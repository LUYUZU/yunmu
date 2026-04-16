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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ml")
@CrossOrigin(origins = "*")
public class MlCallbackController {

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
    public ResponseEntity<?> mlCallback(@RequestBody Map<String, Object> payload) {
        String deviceId = (String) payload.get("device_id");
        String animalId = (String) payload.get("animal_id");
        if (animalId == null) animalId = deviceId;

        log.info("收到 Python ML 回调: deviceId={}, animalId={}, payload={}", deviceId, animalId, payload);

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
        String postureType = mapPythonPostureToJava((String) payload.get("posture"));
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
        result.setModelType("python_ml");

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
        stepCount.setAnimalId(animalId);
        stepCount.setStepCount(steps);
        stepCount.setDailySteps(steps);
        stepCount.setActivityLevel(activityLevel != null ? activityLevel : "normal");
        stepCount.setStepFrequency(stepFreq != null ? stepFreq : 0.0);
        stepCount.setTimestamp(timestamp != null ? timestamp : LocalDateTime.now());
        // bigint 列转换
        if (timestamp != null) {
            stepCount.setTimestampEpoch(
                timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            );
        } else {
            stepCount.setTimestampEpoch(System.currentTimeMillis());
        }

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

        String behaviorType = mapPythonPostureToJava((String) payload.get("posture"));
        Double confidence = parseDouble(payload.get("posture_confidence"));

        BehaviorResult result = new BehaviorResult();
        result.setAnimalId(animalId);
        result.setBehaviorType(behaviorType != null ? behaviorType : "unknown");
        result.setConfidenceScore(confidence);
        result.setActivityLevel(parseDouble(payload.get("activity_level")));
        result.setDurationSeconds(0);
        result.setModelType("python_ml");
        result.setDataModality("多模态");

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

        // 姿态更新
        Map<String, Object> wsPosture = new HashMap<>();
        wsPosture.put("type", "ml_posture_update");   // 区别于本地分析的 posture_update
        wsPosture.put("animalId", animalId);
        wsPosture.put("timestamp", ts);
        wsPosture.put("posture", mapPythonPostureToJava((String) payload.get("posture")));
        wsPosture.put("confidence", payload.get("posture_confidence"));
        wsPosture.put("source", "python_ml");
        webSocketPushService.broadcastToAll(wsPosture);

        // 步数更新
        @SuppressWarnings("unchecked")
        Map<String, Object> anomaly = (Map<String, Object>) payload.get("anomaly");
        Map<String, Object> wsSteps = new HashMap<>();
        wsSteps.put("type", "ml_step_update");
        wsSteps.put("animalId", animalId);
        wsSteps.put("timestamp", ts);
        wsSteps.put("steps", payload.get("calculated_steps"));
        wsSteps.put("activityLevel", payload.get("activity_level"));
        wsSteps.put("isAnomaly", anomaly != null && Boolean.TRUE.equals(anomaly.get("is_anomaly")));
        wsSteps.put("source", "python_ml");
        webSocketPushService.broadcastToAll(wsSteps);

        // 异常告警
        if (anomaly != null && Boolean.TRUE.equals(anomaly.get("is_anomaly"))) {
            Map<String, Object> wsAlert = new HashMap<>();
            wsAlert.put("type", "ml_anomaly_alert");
            wsAlert.put("animalId", animalId);
            wsAlert.put("timestamp", ts);
            wsAlert.put("severity", anomaly.get("severity"));
            wsAlert.put("message", "步数异常: " + anomaly.get("severity"));
            wsAlert.put("source", "python_ml");
            webSocketPushService.broadcastToAll(wsAlert);
        }
    }

    // ========== 工具方法 ==========

    /**
     * Python 姿态名 → Java 姿态名
     * Python: standing/walking/running/lying  →  Java: 站立/行走/奔跑/躺卧
     */
    private String mapPythonPostureToJava(String pythonPosture) {
        if (pythonPosture == null) return "unknown";
        return switch (pythonPosture.toLowerCase()) {
            case "standing" -> "站立";
            case "walking" -> "行走";
            case "running" -> "奔跑";
            case "lying", "lying_down" -> "躺卧";
            case "feeding", "grazing" -> "采食";
            default -> pythonPosture;
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

    private LocalDateTime parseTimestamp(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            long epochSeconds = ((Number) value).longValue();
            // 秒级时间戳
            if (epochSeconds > 1e12) epochSeconds /= 1000;
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            try {
                if (s.matches("\\d+")) {
                    long ts = Long.parseLong(s);
                    if (ts > 1e12) ts /= 1000;
                    return LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneId.systemDefault());
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
