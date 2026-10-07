// MlCallbackController.java
// 接收 Python ML 服务回调，存入数据库并推送至前端
package com.yunmu.controller;

import com.yunmu.entity.PostureResult;
import com.yunmu.entity.BehaviorResult;
import com.yunmu.entity.StepCount;
import com.yunmu.entity.SensorData;
import com.yunmu.repository.PostureResultRepository;
import com.yunmu.repository.BehaviorResultRepository;
import com.yunmu.repository.StepCountRepository;
import com.yunmu.repository.SensorDataRepository;
import com.yunmu.service.websocket.WebSocketPushService;
import com.yunmu.utils.GpsUtils;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ml")
public class MlCallbackController {

    /**
     * Python 回调鉴权令牌。为空时不校验（便于本地联调）；
     * 生产环境应与 Python 端 YUNMU_ML_CALLBACK_TOKEN 保持一致并强制校验。
     */
    @Value("${yunmu.ml.callback.token:}")
    private String callbackToken;

    @Autowired
    private PostureResultRepository postureResultRepository;

    @Autowired
    private BehaviorResultRepository behaviorResultRepository;

    @Autowired
    private StepCountRepository stepCountRepository;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private WebSocketPushService webSocketPushService;

    /**
     * Python ML 处理完毕后回调此接口
     * 流程：存 posture_results → 存 step_counts → 推送 WebSocket
     */
    @PostMapping("/callback")
    public ResponseEntity<?> mlCallback(@RequestBody Map<String, Object> payload,
                                        HttpServletRequest request) {
        // 回调鉴权：仅当配置了令牌时才强制校验（避免开放回调被伪造写入并广播 WebSocket）
        if (callbackToken != null && !callbackToken.isBlank()) {
            String provided = request.getHeader("X-ML-Callback-Token");
            if (!callbackToken.equals(provided)) {
                log.warn("ML 回调鉴权失败: 缺少或错误的 X-ML-Callback-Token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "error", "unauthorized"));
            }
        }

        String deviceId = (String) payload.get("device_id");
        String animalId = (String) payload.get("animal_id");
        if (animalId == null) animalId = deviceId;

        log.debug("收到 Python ML 回调: deviceId={}, animalId={}", deviceId, animalId);

        try {
            // 1. 保存姿态识别结果
            savePostureResult(payload, animalId);

            // 2. 保存步数统计
            saveStepCount(payload, animalId, deviceId);

            // 3. 保存行为识别结果（如果有异常）
            saveBehaviorResult(payload, animalId);

            // 4. 推送 WebSocket 实时通知
            pushToWebSocket(payload, animalId);

            return ResponseEntity.ok(Map.of("success", true, "message", "回调处理成功"));

        } catch (Exception e) {
            log.error("ML 回调处理失败: deviceId={}", deviceId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ========== 私有方法 ==========

    /**
     * 保存姿态识别结果到 posture_results 表
     */
    private void savePostureResult(Map<String, Object> payload, String animalId) {
        String deviceId = (String) payload.get("device_id");
        String postureType = normalizePosture((String) payload.get("posture"));
        Double confidence = parseDouble(payload.get("posture_confidence"));
        Double accelX = parseDouble(payload.get("accel_x"));
        Double accelY = parseDouble(payload.get("accel_y"));
        Double accelZ = parseDouble(payload.get("accel_z"));
        Double gyroX = parseDouble(payload.get("gyro_x"));
        Double gyroY = parseDouble(payload.get("gyro_y"));
        Double gyroZ = parseDouble(payload.get("gyro_z"));

        // 计算加速度模
        Double accelMag = null;
        if (accelX != null && accelY != null && accelZ != null) {
            accelMag = Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
        }

        // 计算倾斜角度（基于 Z 轴加速度）
        Double tiltAngle = null;
        if (accelZ != null && accelMag != null && accelMag > 0) {
            tiltAngle = Math.toDegrees(Math.acos(Math.min(accelZ / accelMag, 1.0)));
        }

        LocalDateTime timestamp = parseTimestamp(payload.get("timestamp"));

        PostureResult result = new PostureResult();
        result.setAnimalId(animalId);
        result.setDeviceId(deviceId);
        result.setTimestamp(timestamp != null ? timestamp : LocalDateTime.now());
        result.setPostureType(postureType);
        result.setConfidenceScore(confidence);
        result.setAccelX(accelX);
        result.setAccelY(accelY);
        result.setAccelZ(accelZ);
        result.setAccelMagnitude(accelMag);
        result.setGyroX(gyroX);
        result.setGyroY(gyroY);
        result.setGyroZ(gyroZ);
        result.setTiltAngle(tiltAngle);
        // 缺陷 B 修复：来源按 Python 回调实际模型名记录（默认 python_ml 兜底）
        Object modelTypeObj = payload.get("model_type");
        result.setModelType(modelTypeObj != null ? modelTypeObj.toString() : "python_ml");

        // 站立/采食持续时间（非累积，每次一条）
        if ("standing".equals(postureType) || "feeding".equals(postureType)) {
            result.setStandingDuration(0);
        }
        if ("lying".equals(postureType)) {
            result.setLyingDuration(0);
        }

        PostureResult saved = postureResultRepository.save(result);
        log.debug("姿态记录已保存: id={}, posture={}", saved.getId(), postureType);
    }

    /**
     * 保存步数统计到 step_counts 表
     */
    private void saveStepCount(Map<String, Object> payload, String animalId, String deviceId) {
        Object stepsObj = payload.get("calculated_steps");
        if (stepsObj == null) return;

        Integer steps = null;
        if (stepsObj instanceof Number) {
            steps = ((Number) stepsObj).intValue();
        } else if (stepsObj instanceof String) {
            try { steps = Integer.parseInt((String) stepsObj); } catch (Exception ignored) {}
        }
        if (steps == null || steps <= 0) return;

        String activityLevel = (String) payload.get("activity_level");
        Double stepFreq = parseDouble(payload.get("step_frequency"));
        LocalDateTime timestamp = parseTimestamp(payload.get("timestamp"));

        StepCount stepCount = new StepCount();
        stepCount.setDeviceId(deviceId);
        stepCount.setAnimalId(animalId);
        stepCount.setStepCount(steps);
        stepCount.setDailySteps(steps);
        stepCount.setActivityLevel(activityLevel != null ? activityLevel : "normal");
        stepCount.setStepFrequency(stepFreq != null ? stepFreq : 0.0);
        stepCount.setTimestamp(timestamp != null ? timestamp : LocalDateTime.now());

        StepCount saved = stepCountRepository.save(stepCount);
        log.debug("步数记录已保存: id={}, steps={}", saved.getId(), steps);
    }

    /**
     * 保存行为识别结果（异常时）
     */
    private void saveBehaviorResult(Map<String, Object> payload, String animalId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> anomaly = (Map<String, Object>) payload.get("anomaly");
        if (anomaly == null || !Boolean.TRUE.equals(anomaly.get("is_anomaly"))) return;

        String behaviorType = normalizePosture((String) payload.get("posture"));
        Double confidence = parseDouble(payload.get("posture_confidence"));

        BehaviorResult result = new BehaviorResult();
        result.setAnimalId(animalId);
        result.setBehaviorType(behaviorType != null ? behaviorType : "unknown");
        result.setConfidenceScore(confidence);
        result.setActivityLevel(parseDouble(payload.get("activity_level")));
        result.setDurationSeconds(0);
        // 缺陷 B 修复：modelType / dataModality 按 Python 回调实际来源动态写入
        Object modelTypeObj = payload.get("model_type");
        Object dataModalityObj = payload.get("data_modality");
        result.setModelType(modelTypeObj != null ? modelTypeObj.toString() : "python_ml");
        result.setDataModality(dataModalityObj != null ? dataModalityObj.toString() : "accel");

        LocalDateTime ts = parseTimestamp(payload.get("timestamp"));
        result.setStartTime(ts != null ? ts : LocalDateTime.now());
        result.setEndTime(ts != null ? ts : LocalDateTime.now());
        result.setCreateTime(LocalDateTime.now());

        BehaviorResult saved = behaviorResultRepository.save(result);
        log.info("行为异常记录已保存: id={}, animalId={}, anomaly={}",
                saved.getId(), animalId, anomaly);
    }

    /**
     * 推送 WebSocket 实时通知
     */
    private void pushToWebSocket(Map<String, Object> payload, String animalId) {
        Long ts = payload.get("timestamp") instanceof Number
                ? ((Number) payload.get("timestamp")).longValue()
                : System.currentTimeMillis() / 1000;

        // 姿态更新（类型与 DataCollectionServiceImpl 保持一致，前端只认识 posture_update）
        Map<String, Object> wsPosture = new HashMap<>();
        wsPosture.put("type", "posture_update");
        wsPosture.put("animalId", animalId);
        wsPosture.put("timestamp", ts);
        wsPosture.put("posture", (String) payload.get("posture"));  // 英文，由前端 translatePosture 翻译
        wsPosture.put("confidence", payload.get("posture_confidence"));
        wsPosture.put("source", "python_ml");
        webSocketPushService.broadcastToAll(wsPosture);

        // 步数更新（合并到 realtime_data 格式，前端只认识 realtime_data）
        @SuppressWarnings("unchecked")
        Map<String, Object> anomaly = (Map<String, Object>) payload.get("anomaly");
        Map<String, Object> wsSteps = new HashMap<>();
        wsSteps.put("type", "realtime_data");
        wsSteps.put("animalId", animalId);
        wsSteps.put("timestamp", ts);
        Map<String, Object> stepsData = new HashMap<>();
        stepsData.put("stepCount", payload.get("calculated_steps"));
        stepsData.put("activityLevel", payload.get("activity_level"));
        wsSteps.put("data", stepsData);
        wsSteps.put("source", "python_ml");
        webSocketPushService.broadcastToAll(wsSteps);

        // 异常告警（使用前端已识别的 health_alert 类型）
        if (anomaly != null && Boolean.TRUE.equals(anomaly.get("is_anomaly"))) {
            Map<String, Object> wsAlert = new HashMap<>();
            wsAlert.put("type", "health_alert");
            wsAlert.put("animalId", animalId);
            wsAlert.put("timestamp", ts);
            wsAlert.put("level", anomaly.get("severity"));
            wsAlert.put("message", "步数异常: " + anomaly.get("severity"));
            webSocketPushService.broadcastToAll(wsAlert);
        }
    }

    // ========== 工具方法 ==========

    /**
     * Python 姿态名 → 统一的英文规范标签（canonical）。
     *
     * 落库统一使用英文规范键（standing/lying/walking/feeding/running/unknown）：
     *   1) 与 PostureRecognitionServiceImpl 的落库口径一致；
     *   2) 保证 PostureResultRepository.sumStandingDuration / sumLyingDuration
     *      以 postureType='standing' / 'lying' 过滤时统计口径不断裂；
     *   3) 中文展示由前端 translatePosture 完成，数据库不再中英混存。
     */
    private String normalizePosture(String pythonPosture) {
        if (pythonPosture == null) return "unknown";
        return switch (pythonPosture.toLowerCase()) {
            case "standing" -> "standing";
            case "walking" -> "walking";
            case "running" -> "running";
            case "lying", "lying_down", "resting" -> "lying";
            case "feeding", "grazing" -> "feeding";
            default -> "unknown";
        };
    }

    private Double parseDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 解析回调中的时间戳。
     *
     * 支持两种形态：
     *   1) 纯数字 —— Unix 时间戳（秒；> 1e12 视为毫秒）；
     *   2) ISO-8601 字符串 —— Python 端实际发送的格式（`datetime.isoformat()`），
     *      如 {@code 2026-09-25T17:54:00}，可能带小数秒，也可能带时区偏移。
     *
     * 历史缺陷：早期实现只认纯数字，ISO 字符串会一路落到 {@code return null}，
     * 三处调用方随即回退 {@code LocalDateTime.now()}，导致历史回放产出的
     * posture_results / step_counts / behavior_results 全部被打上
     * 「回放运行那一刻」的时间戳：
     *   · 步数页的「24 小时趋势」被压缩进一两个小时桶；
     *   · 回放出来的历史步数被算进「今日步数」。
     * 而 sensor_data 走的是 {@code MqttMessageHandler.parseTimestamp}（直接读报文 ts 毫秒值），
     * 它的时间轴一直是对的——这正是排查时「同一批数据、两张表时间轴不一致」的原因。
     */
    private LocalDateTime parseTimestamp(Object value) {
        if (value == null) return null;

        if (value instanceof Number) {
            long epochSeconds = ((Number) value).longValue();
            if (epochSeconds > 1e12) epochSeconds /= 1000;   // 毫秒 → 秒
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
        }

        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty()) return null;

            // 形态 1：纯数字 Unix 时间戳
            if (s.matches("\\d+")) {
                try {
                    long ts = Long.parseLong(s);
                    if (ts > 1e12) ts /= 1000;
                    return LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneId.systemDefault());
                } catch (Exception ignored) { /* 落空则继续尝试 ISO 形态 */ }
            }

            // 形态 2：ISO-8601。先按「本地日期时间」解析（Python isoformat() 的默认形态），
            // 再依次尝试「带偏移量」与「带 Z 的 UTC」两种写法。
            try {
                return LocalDateTime.parse(s);
            } catch (Exception ignored) { }
            try {
                return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception ignored) { }
            try {
                return Instant.parse(s).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception e) {
                log.warn("回调时间戳无法解析，将回退为当前时间: value={}", s);
            }
        }
        return null;
    }
}
