// PostureRecognitionServiceImpl.java
package com.yunmu.service.impl;

import com.yunmu.entity.PostureResult;
import com.yunmu.repository.PostureResultRepository;
import com.yunmu.service.PostureRecognitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PostureRecognitionServiceImpl implements PostureRecognitionService {

    @Autowired
    private PostureResultRepository postureResultRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${python.service.url:http://localhost:5000}")
    private String pythonServiceUrl;

    // PostureRecognitionServiceImpl.java - 修改 recognizePosture 方法
    @Override
    public PostureResult recognizePosture(String animalId, Double accelX, Double accelY, Double accelZ,
                                          Double gyroX, Double gyroY, Double gyroZ) {
        try {
            // ========== 数据完整性检查 ==========
            if (accelX == null || accelY == null || accelZ == null) {
                log.warn("跳过姿态识别：加速度数据不完整 - animalId={}, accelX={}, accelY={}, accelZ={}",
                        animalId, accelX, accelY, accelZ);
                return createDefaultPostureResult(animalId, "unknown", 0.0);
            }

            // ========== 数据有效性检查 ==========
            if (Math.abs(accelX) > 20 || Math.abs(accelY) > 20 || Math.abs(accelZ) > 20) {
                log.warn("加速度数据异常 - animalId={}, accelX={}, accelY={}, accelZ={}",
                        animalId, accelX, accelY, accelZ);
                return createDefaultPostureResult(animalId, "unknown", 0.3);
            }

            // 计算加速度向量模
            double magnitude = Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);

            // 计算倾斜角度（使用 safe acos）
            double tiltAngle = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, accelZ / (magnitude + 0.001)))));

            // 基于阈值判断姿态
            String postureType = determinePosture(magnitude, tiltAngle, gyroX, gyroY, gyroZ);
            double confidence = calculateConfidence(magnitude, tiltAngle, accelX, accelY, accelZ);

            PostureResult result = new PostureResult();
            result.setAnimalId(animalId);
            result.setTimestamp(LocalDateTime.now());
            result.setPostureType(postureType);
            result.setConfidenceScore(confidence);
            result.setAccelX(accelX);
            result.setAccelY(accelY);
            result.setAccelZ(accelZ);
            result.setAccelMagnitude(magnitude);
            result.setGyroX(gyroX);
            result.setGyroY(gyroY);
            result.setGyroZ(gyroZ);
            result.setTiltAngle(tiltAngle);
            result.setModelType("threshold-based");

            log.debug("姿态识别完成 - animalId={}, posture={}, confidence={}, magnitude={}, tiltAngle={}",
                    animalId, postureType, confidence, magnitude, tiltAngle);

            return postureResultRepository.save(result);

        } catch (Exception e) {
            log.error("姿态识别异常 - animalId={}, error={}", animalId, e.getMessage(), e);
            return createDefaultPostureResult(animalId, "unknown", 0.0);
        }
    }

    /**
     * 创建默认姿态结果
     */
    private PostureResult createDefaultPostureResult(String animalId, String postureType, double confidence) {
        PostureResult result = new PostureResult();
        result.setAnimalId(animalId);
        result.setTimestamp(LocalDateTime.now());
        result.setPostureType(postureType);
        result.setConfidenceScore(confidence);
        result.setModelType("fallback");
        return result;
    }

    /**
     * 确定姿态（调用 Python ML 服务）
     * 姿态类型：standing, lying, walking, feeding, running
     */
    private String determinePosture(double magnitude, double tiltAngle, Double gyroX, Double gyroY, Double gyroZ) {
        // 计算陀螺仪模值（如果存在）
        double gyroMagnitude = 0.0;
        if (gyroX != null && gyroY != null && gyroZ != null) {
            gyroMagnitude = Math.sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ);
        }

        // 基于阈值的姿态判断（备用方案，当 Python 服务不可用时使用）
        if (gyroMagnitude > 2.0) {
            return "running";
        } else if (gyroMagnitude > 1.0) {
            return "walking";
        } else if (magnitude < 8.0 || magnitude > 11.0) {
            // 加速度异常，可能是设备脱落或剧烈运动
            return "abnormal";
        } else if (tiltAngle > 60 && tiltAngle < 120) {
            return "standing";
        } else if (tiltAngle < 30 || tiltAngle > 150) {
            return "lying";
        } else {
            // 默认返回站立
            return "standing";
        }
    }

    /**
     * 计算置信度（改进版）
     */
    private double calculateConfidence(double magnitude, double tiltAngle, Double accelX, Double accelY, Double accelZ) {
        double confidence = 0.7; // 基础置信度

        // 基于加速度模值的置信度（正常重力加速度约为9.8）
        double magnitudeDiff = Math.abs(magnitude - 9.8);
        if (magnitudeDiff < 0.5) {
            confidence += 0.2;
        } else if (magnitudeDiff > 2.0) {
            confidence -= 0.3;
        }

        // 基于倾斜角度的置信度
        if (tiltAngle < 10 || tiltAngle > 170) {
            confidence += 0.1; // 躺卧姿态置信度更高
        }

        // 确保置信度在 [0, 1] 范围内
        return Math.min(1.0, Math.max(0.0, confidence));
    }

    @Override
    public List<PostureResult> batchRecognizePosture(List<Map<String, Object>> sensorDataList) {
        // 批量处理需要在外部调用Python服务
        return null;
    }

    @Override
    public PostureResult getCurrentPosture(String animalId) {
        return postureResultRepository.findLatestByAnimalId(animalId);
    }

    @Override
    public List<PostureResult> getPostureHistory(String animalId, LocalDateTime startTime, LocalDateTime endTime) {
        return postureResultRepository.findByAnimalIdAndTimestampBetweenOrderByTimestampDesc(
                animalId, startTime, endTime);
    }

    @Override
    public Map<String, Object> getPostureStatistics(String animalId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> postureCounts = postureResultRepository.countByPostureType(animalId, startTime, endTime);
        Integer standingDuration = postureResultRepository.sumStandingDuration(animalId, startTime, endTime);
        Integer lyingDuration = postureResultRepository.sumLyingDuration(animalId, startTime, endTime);
        
        Map<String, Object> stats = new HashMap<>();
        Map<String, Long> distribution = new HashMap<>();
        
        for (Object[] row : postureCounts) {
            distribution.put((String) row[0], (Long) row[1]);
        }
        
        stats.put("distribution", distribution);
        stats.put("standingDuration", standingDuration != null ? standingDuration : 0);
        stats.put("lyingDuration", lyingDuration != null ? lyingDuration : 0);
        
        return stats;
    }

    @Override
    public Map<String, Object> callPostureRecognitionApi(String animalId, Map<String, Object> sensorData) {
        try {
            // 调用 Python ML 服务的姿态识别接口
            String url = pythonServiceUrl + "/api/device/data";
            Map<String, Object> request = new HashMap<>();
            request.put("device_id", animalId);
            request.put("accel_x", sensorData.get("accelX"));
            request.put("accel_y", sensorData.get("accelY"));
            request.put("accel_z", sensorData.get("accelZ"));
            request.put("gyro_x", sensorData.get("gyroX"));
            request.put("gyro_y", sensorData.get("gyroY"));
            request.put("gyro_z", sensorData.get("gyroZ"));
            request.put("timestamp", System.currentTimeMillis() / 1000);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                // 返回 Python ML 服务的姿态识别结果
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("posture", response.get("posture"));
                result.put("confidence", response.get("posture_confidence"));
                result.put("model_type", "ml_" + (response.containsKey("ml_confidence") ? "model" : "rule"));
                return result;
            }
            
            return Map.of("success", false, "error", "Python服务返回失败");
        } catch (Exception e) {
            log.error("调用Python姿态识别服务失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
