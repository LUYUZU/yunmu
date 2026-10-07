package com.yunmu.service.impl;

import com.alibaba.fastjson2.JSON;
import com.yunmu.dto.BehaviorRequestDTO;
import com.yunmu.dto.BehaviorResultDTO;
import com.yunmu.dto.SensorDataDTO;
import com.yunmu.entity.BehaviorResult;
import com.yunmu.entity.SensorData;
import com.yunmu.repository.BehaviorResultRepository;
import com.yunmu.repository.SensorDataRepository;
import com.yunmu.service.BehaviorAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BehaviorAnalysisServiceImpl implements BehaviorAnalysisService {

    @Autowired
    private BehaviorResultRepository behaviorResultRepository;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${yunmu.python-service.url}")
    private String pythonServiceUrl;

    @Value("${yunmu.python-service.timeout}")
    private int pythonServiceTimeout;

    @Override
    public BehaviorResultDTO analyzeFeeding(String animalId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            log.info("分析采食行为，动物 ID: {}, 时间范围：{} - {}", animalId, startTime, endTime);

            // 数据库 timestamp 是 bigint（Unix 秒），需转换
            long startEpoch = startTime.toEpochSecond(java.time.ZoneOffset.ofHours(8));
            long endEpoch = endTime.toEpochSecond(java.time.ZoneOffset.ofHours(8));
            List<SensorData> sensorDataList = sensorDataRepository.findByAnimalIdAndTimeRange(animalId, startEpoch, endEpoch);
            
            if (sensorDataList.isEmpty()) {
                log.warn("未找到传感器数据，动物 ID: {}", animalId);
                // 缺陷 C 修复：不再静默返回 feeding/0.5 假数据，改为显式空态结果（empty=true），
                // 由调用方/前端识别为"该时间段无数据"，避免污染统计口径
                return createEmptyBehaviorResult(animalId);
            }

            BehaviorRequestDTO request = new BehaviorRequestDTO();
            request.setAnimalId(animalId);
            request.setStartTime(startTime);
            request.setEndTime(endTime);
            request.setDataType("COMBINED");
            request.setAccelData(extractAccelData(sensorDataList));
            request.setSoundData(extractSoundData(sensorDataList));

            BehaviorResultDTO result = callPythonMLService(request);

            if (result != null) {
                saveBehaviorResult(result);
            }

            return result;

        } catch (Exception e) {
            log.error("分析采食行为失败", e);
            // 返回 null 而非假数据，由调用方判断并给出明确的"分析服务不可用"提示
            return null;
        }
    }

    @Override
    public String identifyRealTimeBehavior(SensorDataDTO sensorData) {
        try {
            Double accelX = sensorData.getAccelX();
            Double soundLevel = sensorData.getSoundLevel();

            if (accelX == null) {
                return "unknown";
            }

            double activityLevel = Math.abs(accelX);

            // 统一为 ML 5 类姿态标签：standing / lying / walking / feeding / running
            // running 对应 gyro_magnitude > 2.0 的高速运动状态
            // resting 并入 lying（牛羊静卧即为 lying）；
            //   保留 resting 作为 behaviorType 存入数据库（历史数据兼容），
            //   但实时识别结果输出用 ML 5 类标准标签
            if (activityLevel < 0.05) {
                return "lying";   // 静卧
            } else if (activityLevel < 0.2) {
                return "standing";
            } else if (soundLevel != null && soundLevel > 0.5) {
                return "feeding";
            } else {
                return "walking";
            }

        } catch (Exception e) {
            log.error("实时行为识别失败", e);
            return "unknown";
        }
    }

    @Override
    public List<BehaviorResultDTO> batchBehaviorAnalysis(List<BehaviorRequestDTO> requests) {
        try {
            log.info("批量行为分析，请求数量：{}", requests.size());

            List<BehaviorResultDTO> results = new ArrayList<>();

            for (BehaviorRequestDTO request : requests) {
                try {
                    BehaviorResultDTO result = callPythonMLService(request);
                    if (result != null) {
                        results.add(result);
                        saveBehaviorResult(result);
                    }
                } catch (Exception e) {
                    log.error("单个请求分析失败：{}", request.getAnimalId(), e);
                }
            }

            return results;

        } catch (Exception e) {
            log.error("批量行为分析失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getBehaviorStatistics(String animalId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            Map<String, Object> statistics = new HashMap<>();

            List<BehaviorResult> behaviorResults = behaviorResultRepository
                    .findByAnimalIdAndTimeRange(animalId, startTime, endTime);

            Map<String, Long> durationByBehavior = new HashMap<>();
            Map<String, Integer> countByBehavior = new HashMap<>();

            for (BehaviorResult result : behaviorResults) {
                String behaviorType = result.getBehaviorType();
                long duration = result.getDurationSeconds() != null ? result.getDurationSeconds() : 0;

                durationByBehavior.merge(behaviorType, duration, Long::sum);
                countByBehavior.merge(behaviorType, 1, Integer::sum);
            }

            long totalDuration = durationByBehavior.values().stream().mapToLong(Long::longValue).sum();

            Map<String, Double> proportionByBehavior = new HashMap<>();
            for (Map.Entry<String, Long> entry : durationByBehavior.entrySet()) {
                double proportion = totalDuration > 0 ? (double) entry.getValue() / totalDuration : 0.0;
                proportionByBehavior.put(entry.getKey(), proportion);
            }

            statistics.put("animalId", animalId);
            statistics.put("startTime", startTime);
            statistics.put("endTime", endTime);
            statistics.put("totalDuration", totalDuration);
            statistics.put("totalRecords", behaviorResults.size());
            statistics.put("durationByBehavior", durationByBehavior);
            statistics.put("countByBehavior", countByBehavior);
            statistics.put("proportionByBehavior", proportionByBehavior);
            statistics.put("averageConfidence", calculateAverageConfidence(behaviorResults));
            statistics.put("behaviorTransitions", analyzeBehaviorTransitions(behaviorResults));

            return statistics;

        } catch (Exception e) {
            log.error("获取行为统计数据失败", e);
            return Collections.emptyMap();
        }
    }

    @Override
    public BehaviorResultDTO callPythonMLService(BehaviorRequestDTO request) {
        try {
            String url = pythonServiceUrl + "/api/predict/behavior";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("animal_id", request.getAnimalId());
            requestBody.put("start_time", request.getStartTime().toString());
            requestBody.put("end_time", request.getEndTime().toString());
            requestBody.put("data_type", request.getDataType());

            if (request.getAccelData() != null) {
                requestBody.put("accel_data", request.getAccelData());
            }

            if (request.getSoundData() != null) {
                requestBody.put("sound_data", request.getSoundData());
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = JSON.parseObject(response.getBody(), Map.class);

                if (Boolean.TRUE.equals(responseBody.get("success"))) {
                    return convertToBehaviorResultDTO(responseBody);
                } else {
                    log.error("Python 服务返回失败：{}", responseBody.get("error"));
                    return null;
                }
            } else {
                log.error("调用 Python 服务失败，状态码：{}", response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("调用 Python 机器学习服务失败", e);
            return null;
        }
    }

    private List<Double> extractAccelData(List<SensorData> sensorDataList) {
        return sensorDataList.stream()
                .map(SensorData::getAccelX)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Double> extractSoundData(List<SensorData> sensorDataList) {
        return sensorDataList.stream()
                .map(SensorData::getSoundLevel)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private BehaviorResultDTO convertToBehaviorResultDTO(Map<String, Object> response) {
        BehaviorResultDTO dto = new BehaviorResultDTO();

        dto.setAnimalId((String) response.get("animal_id"));
        dto.setBehaviorType((String) response.get("behavior"));

        Object confidenceObj = response.get("confidence");
        if (confidenceObj instanceof Number) {
            dto.setConfidence(((Number) confidenceObj).doubleValue());
        } else {
            dto.setConfidence(0.0);
        }

        String timestampStr = (String) response.get("timestamp");
        if (timestampStr != null) {
            try {
                dto.setStartTime(LocalDateTime.parse(timestampStr));
                dto.setEndTime(LocalDateTime.now());
            } catch (Exception e) {
                log.warn("时间解析失败：{}", timestampStr);
                dto.setStartTime(LocalDateTime.now());
                dto.setEndTime(LocalDateTime.now());
            }
        } else {
            dto.setStartTime(LocalDateTime.now());
            dto.setEndTime(LocalDateTime.now());
        }

        dto.setDuration(60);

        // 缺陷 B 修复：透传 Python 返回的模型来源与数据模态（ML / rule_based，accel / sound / combined）
        Object modelTypeObj = response.get("model_type");
        dto.setModelType(modelTypeObj != null ? modelTypeObj.toString() : null);
        Object dataModalityObj = response.get("data_modality");
        dto.setDataModality(dataModalityObj != null ? dataModalityObj.toString() : null);

        Object featuresObj = response.get("features");
        if (featuresObj instanceof Map) {
            Map<String, Object> features = (Map<String, Object>) featuresObj;

            setIfNumber(dto, "jawMovementRate", features.get("jaw_movement_rate"));
            setIfNumber(dto, "chewingCount", features.get("chewing_count"));
            setIfNumber(dto, "soundIntensity", features.get("sound_intensity"));
            setIfNumber(dto, "activityLevel", features.get("activity_level"));
        }

        return dto;
    }

    private void setIfNumber(BehaviorResultDTO dto, String field, Object value) {
        if (value instanceof Number) {
            if (field.equals("chewingCount")) {
                dto.setChewingCount(((Number) value).intValue());
            } else if (field.equals("jawMovementRate")) {
                dto.setJawMovementRate(((Number) value).doubleValue());
            } else if (field.equals("soundIntensity")) {
                dto.setSoundIntensity(((Number) value).doubleValue());
            } else if (field.equals("activityLevel")) {
                dto.setActivityLevel(((Number) value).doubleValue());
            }
        }
    }

    private void saveBehaviorResult(BehaviorResultDTO dto) {
        if (dto == null) return;
        // 空态结果不落库（缺陷 C 配套：空数据显式空态，不入库污染统计）
        if (Boolean.TRUE.equals(dto.getEmpty())) {
            log.info("空态分析结果不入库，动物 ID: {}", dto.getAnimalId());
            return;
        }
        try {
            BehaviorResult result = new BehaviorResult();
            result.setAnimalId(dto.getAnimalId());
            result.setBehaviorType(dto.getBehaviorType());
            result.setConfidenceScore(dto.getConfidence());
            result.setStartTime(dto.getStartTime() != null ? dto.getStartTime() : LocalDateTime.now());
            result.setEndTime(dto.getEndTime() != null ? dto.getEndTime() : LocalDateTime.now().plusMinutes(1));
            result.setDurationSeconds(dto.getDuration());
            result.setJawMovementRate(dto.getJawMovementRate());
            result.setChewingCount(dto.getChewingCount());
            result.setSoundIntensity(dto.getSoundIntensity());
            result.setActivityLevel(dto.getActivityLevel());
            // 缺陷 B 修复：modelType / dataModality 按实际调用来源动态写入。
            // 优先取 Python 返回的真实来源（ML 模型名 / rule_based），
            // 缺失时按本次结果特征形态推断，不再固定写死 rule_based/accel
            String modelType = dto.getModelType();
            if (modelType == null || modelType.isBlank()) {
                modelType = "rule_based";
            }
            String dataModality = dto.getDataModality();
            if (dataModality == null || dataModality.isBlank()) {
                dataModality = inferDataModality(dto);
            }
            result.setModelType(modelType);
            result.setDataModality(dataModality);
            result.setCreateTime(LocalDateTime.now());

            behaviorResultRepository.save(result);

            log.info("行为分析结果已保存，动物 ID: {}, 行为：{}, modelType: {}, dataModality: {}",
                    dto.getAnimalId(), dto.getBehaviorType(), modelType, dataModality);

        } catch (Exception e) {
            log.error("保存行为分析结果失败", e);
        }
    }

    /**
     * 缺陷 B 配套：Python 未返回 data_modality 时，按结果特征推断数据模态。
     * 仅有活动量 → accel；仅有声音类特征 → sound；两者都有 → combined
     */
    private String inferDataModality(BehaviorResultDTO dto) {
        boolean hasAccel = dto.getActivityLevel() != null && dto.getActivityLevel() > 0;
        boolean hasSound = (dto.getSoundIntensity() != null && dto.getSoundIntensity() > 0)
                || (dto.getChewingCount() != null && dto.getChewingCount() > 0);
        if (hasAccel && hasSound) {
            return "combined";
        }
        if (hasSound) {
            return "sound";
        }
        return "accel";
    }

    /**
     * 缺陷 C 修复：无数据时的显式空态结果（empty=true，无默认行为、无假置信度）
     */
    private BehaviorResultDTO createEmptyBehaviorResult(String animalId) {
        BehaviorResultDTO dto = new BehaviorResultDTO();
        dto.setAnimalId(animalId);
        dto.setEmpty(true);
        return dto;
    }

    private double calculateAverageConfidence(List<BehaviorResult> results) {
        if (results.isEmpty()) {
            return 0.0;
        }

        return results.stream()
                .mapToDouble(r -> r.getConfidenceScore() != null ? r.getConfidenceScore() : 0.0)
                .average()
                .orElse(0.0);
    }

    private List<Map<String, Object>> analyzeBehaviorTransitions(List<BehaviorResult> results) {
        List<Map<String, Object>> transitions = new ArrayList<>();

        if (results.size() < 2) {
            return transitions;
        }

        results.sort(Comparator.comparing(BehaviorResult::getStartTime));

        for (int i = 1; i < results.size(); i++) {
            BehaviorResult prev = results.get(i - 1);
            BehaviorResult curr = results.get(i);

            long timeGap = ChronoUnit.SECONDS.between(prev.getEndTime(), curr.getStartTime());

            if (timeGap < 300) {
                Map<String, Object> transition = new HashMap<>();
                transition.put("from", prev.getBehaviorType());
                transition.put("to", curr.getBehaviorType());
                transition.put("timeGap", timeGap);
                transition.put("transitionTime", curr.getStartTime());

                transitions.add(transition);
            }
        }

        return transitions;
    }
}
