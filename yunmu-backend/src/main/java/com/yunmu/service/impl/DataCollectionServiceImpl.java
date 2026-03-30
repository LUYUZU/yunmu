package com.yunmu.service.impl;

import com.yunmu.dto.SensorDataDTO;
import com.yunmu.entity.SensorData;
import com.yunmu.entity.LocationTrack;
import com.yunmu.repository.SensorDataRepository;
import com.yunmu.repository.LocationTrackRepository;
import com.yunmu.service.*;
import com.yunmu.service.websocket.AnimalDataWebSocketHandler;
import com.yunmu.utils.GpsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.yunmu.service.websocket.WebSocketPushService;

@Slf4j
@Service
public class DataCollectionServiceImpl implements DataCollectionService {

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private LocationTrackRepository locationTrackRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private BehaviorAnalysisService behaviorAnalysisService;

    @Autowired
    private HealthMonitoringService healthMonitoringService;

    @Autowired
    private PostureRecognitionService postureRecognitionService;

    @Autowired
    private StepCountService stepCountService;

    @Autowired
    private LocationTrackingService locationTrackingService;

    @Autowired
    private AnimalDataWebSocketHandler animalDataWebSocketHandler;

    private static final String SENSOR_CACHE_PREFIX = "sensor:latest:";
    private static final String LOCATION_CACHE_PREFIX = "location:latest:";

    @Autowired
    private WebSocketPushService webSocketPushService;  // 使用推送服务，而不是直接依赖 WebSocketHandle

    @Override
    @Transactional
    public SensorData processSensorData(SensorDataDTO sensorDataDTO) {
        try {
            log.info("处理传感器数据，动物ID: {}, 设备ID: {}",
                    sensorDataDTO.getAnimalId(), sensorDataDTO.getDeviceId());

            // 1. 验证数据有效性
            if (!filterAbnormalData(sensorDataDTO)) {
                log.warn("数据异常被过滤: {}", sensorDataDTO.getAnimalId());
                return null;
            }

            // 2. 创建传感器数据实体
            SensorData sensorData = convertToEntity(sensorDataDTO);

            // 3. 计算步数
            if (sensorDataDTO.getAccelX() != null) {
                Integer stepCount = calculateStepCount(
                        sensorDataDTO.getAccelX(),
                        sensorDataDTO.getAccelY(),
                        sensorDataDTO.getAccelZ()
                );
                sensorData.setStepCount(stepCount);

                // 异步保存步数统计
                CompletableFuture.runAsync(() -> {
                    try {
                        stepCountService.countSteps(
                                sensorDataDTO.getAnimalId(),
                                stepCount,
                                calculateAccelMagnitude(sensorDataDTO.getAccelX(),
                                        sensorDataDTO.getAccelY(),
                                        sensorDataDTO.getAccelZ())
                        );
                    } catch (Exception e) {
                        log.error("保存步数统计失败", e);
                    }
                });
            }

            // 4. 处理GPS数据（坐标转换）
            if (sensorDataDTO.getLatitude() != null && sensorDataDTO.getLongitude() != null) {
                // 假设接收的是WGS84坐标，转换为GCJ-02
                double[] gcj = GpsUtils.wgs84ToGcj02(
                        sensorDataDTO.getLongitude(),
                        sensorDataDTO.getLatitude()
                );

                sensorData.setLongitude(gcj[0]);
                sensorData.setLatitude(gcj[1]);

                // 更新位置轨迹（使用GCJ-02坐标）
                updateLocationTrack(sensorDataDTO, gcj[0], gcj[1]);
            }

            // 5. 保存传感器数据
            SensorData savedData = sensorDataRepository.save(sensorData);

            // 6. 缓存最新数据
            cacheLatestData(sensorDataDTO.getAnimalId(), savedData);

            // 7. 异步触发行为识别和姿态识别
            triggerAsyncAnalysis(sensorDataDTO, savedData);

            // 8. 实时推送到WebSocket
            pushRealTimeData(sensorDataDTO, savedData);

            log.info("传感器数据处理完成，ID: {}", savedData.getId());
            return savedData;

        } catch (Exception e) {
            log.error("处理传感器数据失败", e);
            throw new RuntimeException("数据处理失败: " + e.getMessage());
        }
    }

    @Override
    public boolean validateGpsData(Double latitude, Double longitude, Double accuracy) {
        if (latitude == null || longitude == null) {
            return false;
        }

        // 检查坐标范围
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return false;
        }

        // 检查是否为0,0（无效坐标）
        if (Math.abs(latitude) < 0.0001 && Math.abs(longitude) < 0.0001) {
            return false;
        }

        // 检查精度
        if (accuracy != null && accuracy > 50.0) {
            return false;
        }

        return true;
    }

    @Override
    public boolean filterAbnormalData(SensorDataDTO sensorDataDTO) {
        try {
            // 检查加速度数据异常
            if (sensorDataDTO.getAccelX() != null) {
                double accelMagnitude = Math.sqrt(
                        Math.pow(sensorDataDTO.getAccelX(), 2) +
                                Math.pow(sensorDataDTO.getAccelY(), 2) +
                                Math.pow(sensorDataDTO.getAccelZ(), 2)
                );

                // 加速度幅度异常（正常范围：0.8g-1.2g）
                if (accelMagnitude < 0.8 || accelMagnitude > 1.2) {
                    log.warn("加速度数据异常: {}", accelMagnitude);
                    return false;
                }
            }

            // 检查心率异常
            if (sensorDataDTO.getHeartRate() != null) {
                int heartRate = sensorDataDTO.getHeartRate();
                if (heartRate < 40 || heartRate > 120) {
                    log.warn("心率数据异常: {}", heartRate);
                    return false;
                }
            }

            // 检查体温异常
            if (sensorDataDTO.getTemperature() != null) {
                double temperature = sensorDataDTO.getTemperature();
                if (temperature < 35.0 || temperature > 42.0) {
                    log.warn("体温数据异常: {}", temperature);
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            log.error("数据过滤检查失败", e);
            return false;
        }
    }

    @Override
    public Integer calculateStepCount(Double accelX, Double accelY, Double accelZ) {
        try {
            // 简单的步数统计算法
            double magnitude = Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);

            // 使用阈值检测步数
            double threshold = 1.1;
            if (magnitude > threshold) {
                return 1;
            }

            return 0;
        } catch (Exception e) {
            log.error("步数计算失败", e);
            return 0;
        }
    }

    @Override
    @Transactional
    public LocationTrack updateLocationTrack(SensorDataDTO sensorDataDTO) {
        // 如果已经有GCJ-02坐标，直接使用
        if (sensorDataDTO.getLatitude() != null && sensorDataDTO.getLongitude() != null) {
            return updateLocationTrack(sensorDataDTO, sensorDataDTO.getLongitude(), sensorDataDTO.getLatitude());
        }
        return null;
    }

    @Override
    @Transactional
    public void batchProcessData(List<SensorDataDTO> dataList) {
        try {
            log.info("开始批量处理数据，数量: {}", dataList.size());

            for (SensorDataDTO data : dataList) {
                processSensorData(data);
            }

            log.info("批量数据处理完成");

        } catch (Exception e) {
            log.error("批量数据处理失败", e);
            throw new RuntimeException("批量处理失败: " + e.getMessage());
        }
    }

    /**
     * 更新位置轨迹（带GCJ-02坐标）
     */
    private LocationTrack updateLocationTrack(SensorDataDTO dto, Double gcjLng, Double gcjLat) {
        try {
            LocationTrack locationTrack = new LocationTrack();
            locationTrack.setAnimalId(dto.getAnimalId());
            locationTrack.setDeviceId(dto.getDeviceId());
            locationTrack.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now());
            locationTrack.setLongitude(gcjLng);
            locationTrack.setLatitude(gcjLat);
            locationTrack.setAltitude(dto.getAltitude());
            locationTrack.setIsValidGps(true);
            locationTrack.setGpsQuality("GOOD");
            locationTrack.setBatteryLevel(dto.getBatteryLevel());
            locationTrack.setSignalStrength(dto.getSignalStrength());
            locationTrack.setCreateTime(LocalDateTime.now());

            LocationTrack savedTrack = locationTrackRepository.save(locationTrack);

            // 缓存最新位置
            cacheLatestLocation(dto.getAnimalId(), savedTrack);

            // 推送位置更新
            pushLocationUpdate(dto.getAnimalId(), gcjLng, gcjLat);

            return savedTrack;

        } catch (Exception e) {
            log.error("更新位置轨迹失败", e);
            return null;
        }
    }

    /**
     * 触发异步分析任务
     */
    // DataCollectionServiceImpl.java - 找到 triggerAsyncAnalysis 方法并替换

    /**
     * 触发异步分析任务
     */
    private void triggerAsyncAnalysis(SensorDataDTO dto, SensorData savedData) {
        // 异步触发姿态识别
        CompletableFuture.runAsync(() -> {
            try {
                // ========== 添加空值检查 ==========
                if (dto.getAccelX() == null || dto.getAccelY() == null || dto.getAccelZ() == null) {
                    log.debug("跳过姿态识别：加速度数据缺失 - animalId={}, accelX={}, accelY={}, accelZ={}",
                            dto.getAnimalId(), dto.getAccelX(), dto.getAccelY(), dto.getAccelZ());
                    return;
                }

                // 检查数据是否有效（非零或异常值）
                if (Math.abs(dto.getAccelX()) < 0.01 && Math.abs(dto.getAccelY()) < 0.01 && Math.abs(dto.getAccelZ()) < 0.01) {
                    log.debug("跳过姿态识别：加速度数据全零 - animalId={}", dto.getAnimalId());
                    return;
                }

                log.debug("开始姿态识别 - animalId={}, accel=({:.3f}, {:.3f}, {:.3f})",
                        dto.getAnimalId(), dto.getAccelX(), dto.getAccelY(), dto.getAccelZ());

                var postureResult = postureRecognitionService.recognizePosture(
                        dto.getAnimalId(),
                        dto.getAccelX(),
                        dto.getAccelY(),
                        dto.getAccelZ(),
                        null, null, null
                );

                if (postureResult != null && postureResult.getPostureType() != null) {
                    log.debug("姿态识别完成 - animalId={}, posture={}, confidence={:.2f}",
                            dto.getAnimalId(),
                            postureResult.getPostureType(),
                            postureResult.getConfidenceScore());

                    pushPostureResult(dto.getAnimalId(), postureResult);
                }
            } catch (Exception e) {
                log.error("姿态识别失败 - animalId={}, error={}", dto.getAnimalId(), e.getMessage(), e);
            }
        });

        // 异步触发行为分析
        CompletableFuture.runAsync(() -> {
            try {
                // 添加空值检查
                if (dto.getAccelX() == null && dto.getHeartRate() == null && dto.getTemperature() == null) {
                    log.debug("跳过行为分析：无有效数据 - animalId={}", dto.getAnimalId());
                    return;
                }

                log.debug("开始行为分析 - 动物ID: {}", dto.getAnimalId());
                String behavior = behaviorAnalysisService.identifyRealTimeBehavior(dto);

                if (behavior != null && !"unknown".equals(behavior)) {
                    log.debug("行为分析完成 - 动物ID: {}, 行为: {}", dto.getAnimalId(), behavior);
                    pushBehaviorResult(dto.getAnimalId(), behavior);
                }
            } catch (Exception e) {
                log.error("行为分析失败 - animalId={}", dto.getAnimalId(), e);
            }
        });

        // 异步触发健康监控
        CompletableFuture.runAsync(() -> {
            try {
                // 健康监控需要心率或体温数据
                if (dto.getHeartRate() == null && dto.getTemperature() == null) {
                    log.debug("跳过健康监控：无健康数据 - animalId={}", dto.getAnimalId());
                    return;
                }

                log.debug("开始健康监控 - 动物ID: {}", dto.getAnimalId());
                healthMonitoringService.monitorRealTimeHealth(dto);
            } catch (Exception e) {
                log.error("健康监控失败 - animalId={}", dto.getAnimalId(), e);
            }
        });
    }

    /**
     * 实时推送到WebSocket
     */
    private void pushRealTimeData(SensorDataDTO dto, SensorData savedData) {
        try {
            Map<String, Object> realTimeData = new HashMap<>();
            realTimeData.put("type", "realtime_data");
            realTimeData.put("animalId", dto.getAnimalId());
            realTimeData.put("timestamp", System.currentTimeMillis());

            Map<String, Object> data = new HashMap<>();
            data.put("heartRate", dto.getHeartRate());
            data.put("temperature", dto.getTemperature());
            data.put("stepCount", dto.getStepCount());
            data.put("latitude", savedData.getLatitude());
            data.put("longitude", savedData.getLongitude());

            if (dto.getAccelX() != null) {
                double activityLevel = Math.sqrt(
                        dto.getAccelX() * dto.getAccelX() +
                                dto.getAccelY() * dto.getAccelY() +
                                dto.getAccelZ() * dto.getAccelZ()
                );
                data.put("activityLevel", activityLevel);
            }

            realTimeData.put("data", data);
            webSocketPushService.broadcastToAll(realTimeData);

        } catch (Exception e) {
            log.error("推送实时数据失败", e);
        }
    }

    /**
     * 推送姿态识别结果
     */
    private void pushPostureResult(String animalId, com.yunmu.entity.PostureResult posture) {
        try {
            Map<String, Object> postureData = new HashMap<>();
            postureData.put("type", "posture_update");
            postureData.put("animalId", animalId);
            postureData.put("timestamp", System.currentTimeMillis());
            postureData.put("posture", posture.getPostureType());
            postureData.put("confidence", posture.getConfidenceScore());
            postureData.put("tiltAngle", posture.getTiltAngle());

            webSocketPushService.broadcastToAll(postureData);

        } catch (Exception e) {
            log.error("推送姿态结果失败", e);
        }
    }

    /**
     * 推送行为分析结果
     */
    private void pushBehaviorResult(String animalId, String behavior) {
        try {
            Map<String, Object> behaviorData = new HashMap<>();
            behaviorData.put("type", "behavior_update");
            behaviorData.put("animalId", animalId);
            behaviorData.put("timestamp", System.currentTimeMillis());
            behaviorData.put("behavior", behavior);

            webSocketPushService.broadcastToAll(behaviorData);

        } catch (Exception e) {
            log.error("推送行为结果失败", e);
        }
    }

    /**
     * 推送位置更新
     */
    private void pushLocationUpdate(String animalId, Double lng, Double lat) {
        try {
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("type", "location_update");
            locationData.put("animalId", animalId);
            locationData.put("timestamp", System.currentTimeMillis());
            locationData.put("lng", lng);
            locationData.put("lat", lat);

            webSocketPushService.broadcastToAll(locationData);

        } catch (Exception e) {
            log.error("推送位置更新失败", e);
        }
    }

    private SensorData convertToEntity(SensorDataDTO dto) {
        SensorData entity = new SensorData();
        entity.setAnimalId(dto.getAnimalId());
        entity.setDeviceId(dto.getDeviceId());
        entity.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now());

        entity.setAccelX(dto.getAccelX());
        entity.setAccelY(dto.getAccelY());
        entity.setAccelZ(dto.getAccelZ());

        entity.setLatitude(dto.getLatitude());
        entity.setLongitude(dto.getLongitude());
        entity.setAltitude(dto.getAltitude());

        entity.setHeartRate(dto.getHeartRate());
        entity.setTemperature(dto.getTemperature());
        entity.setStepCount(dto.getStepCount());

        entity.setSoundLevel(dto.getSoundLevel());
        entity.setSoundFrequency(dto.getSoundFrequency());

        entity.setIsValid(true);
        entity.setDataSource("mqtt");
        entity.setCreateTime(LocalDateTime.now());

        return entity;
    }

    private double calculateAccelMagnitude(Double accelX, Double accelY, Double accelZ) {
        if (accelX == null || accelY == null || accelZ == null) {
            return 0.0;
        }
        return Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
    }

    private void cacheLatestData(String animalId, SensorData data) {
        try {
            String cacheKey = SENSOR_CACHE_PREFIX + animalId;
            redisTemplate.opsForValue().set(cacheKey, data);
            redisTemplate.expire(cacheKey, 300, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("缓存最新数据失败", e);
        }
    }

    private void cacheLatestLocation(String animalId, LocationTrack location) {
        try {
            String cacheKey = LOCATION_CACHE_PREFIX + animalId;
            redisTemplate.opsForValue().set(cacheKey, location);
            redisTemplate.expire(cacheKey, 300, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("缓存最新位置失败", e);
        }
    }
}