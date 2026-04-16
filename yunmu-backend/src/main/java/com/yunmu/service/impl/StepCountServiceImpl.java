// StepCountServiceImpl.java
package com.yunmu.service.impl;

import com.yunmu.entity.StepCount;
import com.yunmu.repository.StepCountRepository;
import com.yunmu.service.StepCountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class StepCountServiceImpl implements StepCountService {

    @Autowired
    private StepCountRepository stepCountRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${python.service.url:http://localhost:5000}")
    private String pythonServiceUrl;

    @Override
    public StepCount countSteps(String animalId, Integer stepCount, Double accelMagnitude) {
        try {
            // 获取上一步数
            StepCount lastStepCount = stepCountRepository.findLatestByAnimalId(animalId)
                    .orElse(null);
            
            int previousSteps = lastStepCount != null ? lastStepCount.getStepCount() : 0;
            
            // 计算步数增量
            int stepIncrement = stepCount - previousSteps;
            if (stepIncrement < 0) {
                stepIncrement = stepCount; // 计数器重置情况
            }
            
            // 计算行走距离 (假设平均步长0.6米)
            double stepLength = 0.6;
            double walkingDistance = stepIncrement * stepLength;
            
            // 计算活跃时长 (假设每100步活跃1分钟)
            int activeDuration = stepIncrement / 100 * 60;
            
            // 计算步频 (步/分钟)
            double stepFrequency = 0.0;
            if (lastStepCount != null && stepIncrement > 0) {
                long minutesDiff = java.time.Duration.between(
                        lastStepCount.getTimestamp(), LocalDateTime.now()).toMinutes();
                if (minutesDiff > 0) {
                    stepFrequency = (double) stepIncrement / minutesDiff;
                }
            }
            
            // 判断活跃程度
            String activityLevel = determineActivityLevel(stepIncrement);
            
            // 判断是否异常
            boolean isAnomaly = checkAnomaly(stepIncrement, accelMagnitude);
            
            StepCount result = new StepCount();
            result.setAnimalId(animalId);
            result.setTimestamp(LocalDateTime.now());
            result.setStepCount(stepCount);
            result.setDailySteps(stepCount);
            result.setWalkingDistance(walkingDistance);
            result.setActiveDuration(activeDuration);
            result.setStepFrequency(stepFrequency);
            result.setAvgStepLength(stepLength);
            result.setActivityLevel(activityLevel);
            result.setIsAnomaly(isAnomaly);
            
            return stepCountRepository.save(result);
        } catch (Exception e) {
            log.error("步数统计失败: {}", e.getMessage());
            throw new RuntimeException("步数统计失败: " + e.getMessage());
        }
    }

    private String determineActivityLevel(int stepIncrement) {
        if (stepIncrement < 500) {
            return "low";
        } else if (stepIncrement < 2000) {
            return "medium";
        } else {
            return "high";
        }
    }

    private boolean checkAnomaly(int stepIncrement, Double accelMagnitude) {
        // 简单异常检测：步数增量过大或过小
        if (stepIncrement > 10000) {
            return true;
        }
        if (accelMagnitude != null && Math.abs(accelMagnitude - 9.8) > 20) {
            return true;
        }
        return false;
    }

    @Override
    public List<StepCount> batchCountSteps(List<Map<String, Object>> sensorDataList) {
        // 批量处理需要在外部调用Python服务
        return null;
    }

    @Override
    public StepCount getCurrentSteps(String animalId) {
        return stepCountRepository.findLatestByAnimalId(animalId).orElse(null);
    }

    @Override
    public List<StepCount> getStepHistory(String animalId, LocalDateTime startTime, LocalDateTime endTime) {
        Long startMs = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Long endMs = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return stepCountRepository.findByAnimalIdAndTimestampEpochBetweenOrderByTimestampEpochDesc(
                animalId, startMs, endMs);
    }

    @Override
    public Map<String, Object> getStepStatistics(String animalId, LocalDateTime startTime, LocalDateTime endTime) {
        Long startMs = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Long endMs = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Integer totalSteps = stepCountRepository.sumSteps(animalId, startMs, endMs);
        Double totalDistance = stepCountRepository.sumWalkingDistance(animalId, startMs, endMs);
        Integer totalActiveDuration = stepCountRepository.sumActiveDuration(animalId, startMs, endMs);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSteps", totalSteps != null ? totalSteps : 0);
        stats.put("totalDistance", totalDistance != null ? totalDistance : 0.0);
        stats.put("totalActiveDuration", totalActiveDuration != null ? totalActiveDuration : 0);
        stats.put("avgStepsPerHour", calculateAvgStepsPerHour(totalSteps, startTime, endTime));
        
        return stats;
    }

    private double calculateAvgStepsPerHour(Integer totalSteps, LocalDateTime startTime, LocalDateTime endTime) {
        if (totalSteps == null || totalSteps == 0) {
            return 0.0;
        }
        long hours = java.time.Duration.between(startTime, endTime).toHours();
        if (hours <= 0) {
            hours = 1;
        }
        return (double) totalSteps / hours;
    }

    @Override
    public Integer getTodaySteps(String animalId) {
        Long startOfDay = LocalDate.now()
                .atStartOfDay()
                .atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        return stepCountRepository.getTodaySteps(animalId, startOfDay);
    }

    @Override
    public Map<String, Object> callStepCountApi(String animalId, Map<String, Object> sensorData) {
        try {
            String url = pythonServiceUrl + "/api/count/steps";
            Map<String, Object> request = new HashMap<>();
            request.put("animal_id", animalId);
            request.put("accel_data", sensorData.get("accelData"));
            request.put("timestamp", LocalDateTime.now().toString());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            return response;
        } catch (Exception e) {
            log.error("调用Python步数统计服务失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @Override
    public Map<String, Integer> getAllAnimalTodaySteps() {
        Long startOfDay = LocalDate.now()
                .atStartOfDay()
                .atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        List<Object[]> results = stepCountRepository.getAllAnimalTodaySteps(startOfDay);
        
        Map<String, Integer> stepsMap = new HashMap<>();
        for (Object[] row : results) {
            stepsMap.put((String) row[0], ((Number) row[1]).intValue());
        }
        
        return stepsMap;
    }
}
