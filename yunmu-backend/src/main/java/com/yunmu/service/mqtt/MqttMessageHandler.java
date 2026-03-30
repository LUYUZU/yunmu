package com.yunmu.service.mqtt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONException;
import com.yunmu.dto.SensorDataDTO;
import com.yunmu.service.DataCollectionService;
import com.yunmu.utils.GpsUtils;
import com.yunmu.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MqttMessageHandler {

    @Autowired
    private DataCollectionService dataCollectionService;

    // 设备ID到动物ID的映射（可从数据库加载）
    private static final Map<String, String> DEVICE_ANIMAL_MAP = new HashMap<>();

    static {
        // 初始映射，实际应从数据库或配置中心获取
        DEVICE_ANIMAL_MAP.put("7249_001", "cow_001");
        DEVICE_ANIMAL_MAP.put("7249_002", "cow_002");
        DEVICE_ANIMAL_MAP.put("7249_003", "cow_003");
        DEVICE_ANIMAL_MAP.put("7249_004", "sheep_001");
        DEVICE_ANIMAL_MAP.put("7249_005", "sheep_002");
    }

    /**
     * 处理MQTT消息（主入口）
     */
    public void handleMessage(String topic, String payload) {
        log.info("========== 收到MQTT消息 ==========");
        log.info("Topic: {}", topic);
        log.info("Payload长度: {} 字符", payload != null ? payload.length() : 0);

        try {
            // 1. 验证消息有效性
            if (!validateMessage(payload)) {
                log.warn("消息验证失败，跳过处理");
                return;
            }

            // 2. 解析JSON
            JSONObject jsonData;
            try {
                jsonData = JSON.parseObject(payload);
            } catch (JSONException e) {
                log.error("JSON解析失败: {}", e.getMessage());
                log.error("原始消息: {}", payload);
                return;
            }

            // 3. 根据Topic类型分发处理
            if (topic.contains("gps") || topic.contains("location")) {
                handleGpsData(jsonData);
            } else if (topic.contains("sensor") || topic.contains("data")) {
                handleSensorData(jsonData);
            } else if (topic.contains("heartbeat")) {
                handleHeartbeatData(jsonData);
            } else if (topic.equals("7249") || topic.startsWith("7249/")) {
                // 处理默认主题的数据（包含GPS和传感器数据）
                handleMixedData(jsonData);
            } else {
                log.warn("未知的Topic类型: {}, 使用默认处理", topic);
                handleMixedData(jsonData);
            }

        } catch (Exception e) {
            log.error("处理MQTT消息时发生异常", e);
        }
    }

    /**
     * 验证消息有效性
     */
    private boolean validateMessage(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            log.warn("消息内容为空");
            return false;
        }

        if (payload.length() > 10240) {
            log.warn("消息过大: {} 字符", payload.length());
            return false;
        }

        return true;
    }

    /**
     * 处理GPS数据（度分格式）
     * 数据格式示例:
     * {
     *   "deviceId": "7249_001",
     *   "timestamp": "2024-01-01 12:00:00",
     *   "lat": "3110.5682",
     *   "lng": "12112.3456",
     *   "speed": 5.2,
     *   "direction": 120.5,
     *   "satellites": 8,
     *   "battery": 85
     * }
     */
    private void handleGpsData(JSONObject data) {
        try {
            String deviceId = data.getString("deviceId");
            if (deviceId == null) {
                deviceId = data.getString("device_id");
            }

            String animalId = getAnimalId(deviceId);
            if (animalId == null) {
                log.warn("未找到设备对应的动物ID: {}", deviceId);
                return;
            }

            // 解析度分格式坐标
            String latStr = data.getString("lat");
            String lngStr = data.getString("lng");

            if (latStr == null || lngStr == null) {
                log.warn("GPS数据缺少坐标信息");
                return;
            }

            // 度分格式转十进制度数（WGS84）
            Double wgs84Lat = GpsUtils.degreeMinuteToDecimal(latStr);
            Double wgs84Lng = GpsUtils.degreeMinuteToDecimal(lngStr);

            if (wgs84Lat == null || wgs84Lng == null) {
                log.warn("坐标转换失败 - lat: {}, lng: {}", latStr, lngStr);
                return;
            }

            log.debug("坐标转换 - 原始: ({}, {}), WGS84: ({}, {})",
                    latStr, lngStr, wgs84Lat, wgs84Lng);

            // 转换为高德坐标系（GCJ-02）
            double[] gcj = GpsUtils.wgs84ToGcj02(wgs84Lng, wgs84Lat);
            log.debug("GCJ-02坐标: ({}, {})", gcj[0], gcj[1]);

            // 创建SensorDataDTO并更新位置
            SensorDataDTO dto = new SensorDataDTO();
            dto.setAnimalId(animalId);
            dto.setDeviceId(deviceId);
            dto.setLatitude(gcj[1]);  // 纬度
            dto.setLongitude(gcj[0]); // 经度
            dto.setTimestamp(parseTimestamp(data.getString("timestamp")));
            dto.setBatteryLevel(data.getInteger("battery"));
            dto.setSignalStrength(data.getInteger("signal"));

            // 调用数据采集服务处理
            dataCollectionService.processSensorData(dto);

            log.info("GPS数据处理完成 - 动物ID: {}, 坐标: ({}, {})",
                    animalId, gcj[0], gcj[1]);

        } catch (Exception e) {
            log.error("处理GPS数据失败", e);
        }
    }

    /**
     * 处理传感器数据（加速度、心率等）
     * 数据格式示例:
     * {
     *   "deviceId": "7249_001",
     *   "timestamp": "2024-01-01 12:00:00",
     *   "accelX": 0.12,
     *   "accelY": -0.05,
     *   "accelZ": 9.78,
     *   "heartRate": 72,
     *   "temperature": 38.5,
     *   "stepCount": 1250
     * }
     */
    // MqttMessageHandler.java - 修改 handleSensorData 方法中的加速度数据解析部分

    private void handleSensorData(JSONObject data) {
        try {
            String deviceId = data.getString("deviceId");
            if (deviceId == null) {
                deviceId = data.getString("device_id");
            }

            String animalId = getAnimalId(deviceId);
            if (animalId == null) {
                log.warn("未找到设备对应的动物ID: {}", deviceId);
                return;
            }

            SensorDataDTO dto = new SensorDataDTO();
            dto.setAnimalId(animalId);
            dto.setDeviceId(deviceId);
            dto.setTimestamp(parseTimestamp(data.getString("timestamp")));

            // ========== 加速度数据（支持多种字段名） ==========
            dto.setAccelX(getDoubleValue(data, "accelX", "accel_x", "x", "accX"));
            dto.setAccelY(getDoubleValue(data, "accelY", "accel_y", "y", "accY"));
            dto.setAccelZ(getDoubleValue(data, "accelZ", "accel_z", "z", "accZ"));

            // 记录加速度数据缺失情况
            if (dto.getAccelX() == null && dto.getAccelY() == null && dto.getAccelZ() == null) {
                log.debug("设备 {} 未发送加速度数据", deviceId);
            }

            // 健康数据（支持多种字段名）
            dto.setHeartRate(getIntegerValue(data, "heartRate", "heart_rate", "hr", "bpm"));
            dto.setTemperature(getDoubleValue(data, "temperature", "temp", "tmp"));
            dto.setStepCount(getIntegerValue(data, "stepCount", "step_count", "steps"));

            // 声学数据
            dto.setSoundLevel(getDoubleValue(data, "soundLevel", "sound_level", "db"));
            dto.setSoundFrequency(getDoubleValue(data, "soundFrequency", "sound_frequency", "freq"));

            // 设备状态
            dto.setBatteryLevel(getIntegerValue(data, "battery", "battery_level", "bat"));
            dto.setSignalStrength(getIntegerValue(data, "signal", "signal_strength", "rssi"));

            // 调用数据采集服务处理
            dataCollectionService.processSensorData(dto);

            log.info("传感器数据处理完成 - 动物ID: {}, 心率: {}, 温度: {}, 步数: {}",
                    animalId, dto.getHeartRate(), dto.getTemperature(), dto.getStepCount());

        } catch (Exception e) {
            log.error("处理传感器数据失败", e);
        }
    }

    /**
     * 处理心跳数据
     * 数据格式示例:
     * {
     *   "deviceId": "7249_001",
     *   "timestamp": "2024-01-01 12:00:00",
     *   "battery": 85,
     *   "signal": 4,
     *   "firmware": "v1.0.0"
     * }
     */
    private void handleHeartbeatData(JSONObject data) {
        try {
            String deviceId = data.getString("deviceId");
            if (deviceId == null) {
                deviceId = data.getString("device_id");
            }

            Integer battery = data.getInteger("battery");
            Integer signal = data.getInteger("signal");
            String firmware = data.getString("firmware");

            log.info("设备心跳 - DeviceId: {}, 电量: {}%, 信号: {}, 固件: {}",
                    deviceId, battery, signal, firmware);

            // 可以在此处更新设备状态到Redis或数据库
            // TODO: 更新设备在线状态

        } catch (Exception e) {
            log.error("处理心跳数据失败", e);
        }
    }

    /**
     * 处理混合数据（包含GPS和传感器数据）
     * 数据格式示例:
     * {
     *   "deviceId": "7249_001",
     *   "timestamp": "2024-01-01 12:00:00",
     *   "lat": "3110.5682",
     *   "lng": "12112.3456",
     *   "accelX": 0.12,
     *   "accelY": -0.05,
     *   "accelZ": 9.78,
     *   "heartRate": 72,
     *   "temperature": 38.5,
     *   "stepCount": 1250
     * }
     */
    // MqttMessageHandler.java - 修改 handleMixedData 方法中的加速度数据解析部分

    private void handleMixedData(JSONObject data) {
        try {
            String deviceId = data.getString("deviceId");
            if (deviceId == null) {
                deviceId = data.getString("device_id");
            }

            String animalId = getAnimalId(deviceId);
            if (animalId == null) {
                log.warn("未找到设备对应的动物ID: {}", deviceId);
                return;
            }

            SensorDataDTO dto = new SensorDataDTO();
            dto.setAnimalId(animalId);
            dto.setDeviceId(deviceId);
            dto.setTimestamp(parseTimestamp(data.getString("timestamp")));

            // 解析GPS数据（度分格式）
            String latStr = data.getString("lat");
            String lngStr = data.getString("lng");
            if (latStr != null && lngStr != null) {
                Double wgs84Lat = GpsUtils.degreeMinuteToDecimal(latStr);
                Double wgs84Lng = GpsUtils.degreeMinuteToDecimal(lngStr);
                if (wgs84Lat != null && wgs84Lng != null) {
                    double[] gcj = GpsUtils.wgs84ToGcj02(wgs84Lng, wgs84Lat);
                    dto.setLatitude(gcj[1]);
                    dto.setLongitude(gcj[0]);
                }
            }

            // ========== 加速度数据（支持多种字段名） ==========
            dto.setAccelX(getDoubleValue(data, "accelX", "accel_x", "x", "accX"));
            dto.setAccelY(getDoubleValue(data, "accelY", "accel_y", "y", "accY"));
            dto.setAccelZ(getDoubleValue(data, "accelZ", "accel_z", "z", "accZ"));

            // 健康数据（支持多种字段名）
            dto.setHeartRate(getIntegerValue(data, "heartRate", "heart_rate", "hr", "bpm"));
            dto.setTemperature(getDoubleValue(data, "temperature", "temp", "tmp"));
            dto.setStepCount(getIntegerValue(data, "stepCount", "step_count", "steps"));

            // 声学数据
            dto.setSoundLevel(getDoubleValue(data, "soundLevel", "sound_level", "db"));
            dto.setSoundFrequency(getDoubleValue(data, "soundFrequency", "sound_frequency", "freq"));

            // 设备状态
            dto.setBatteryLevel(getIntegerValue(data, "battery", "battery_level", "bat"));
            dto.setSignalStrength(getIntegerValue(data, "signal", "signal_strength", "rssi"));

            // 调用数据采集服务处理
            dataCollectionService.processSensorData(dto);

            log.info("混合数据处理完成 - 动物ID: {}", animalId);

        } catch (Exception e) {
            log.error("处理混合数据失败", e);
        }
    }

    /**
     * 解析时间戳
     */
    private LocalDateTime parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        try {
            // 尝试多种时间格式
            if (timestampStr.contains("T")) {
                return LocalDateTime.parse(timestampStr);
            } else if (timestampStr.contains(" ")) {
                return LocalDateTime.parse(timestampStr,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                return LocalDateTime.now();
            }
        } catch (DateTimeParseException e) {
            log.warn("时间戳解析失败: {}, 使用当前时间", timestampStr);
            return LocalDateTime.now();
        }
    }

    /**
     * 获取动物ID（支持缓存）
     */
    private String getAnimalId(String deviceId) {
        if (deviceId == null) {
            return null;
        }

        // 先从映射表获取
        String animalId = DEVICE_ANIMAL_MAP.get(deviceId);

        if (animalId == null) {
            // TODO: 从数据库查询设备绑定关系
            // animalId = deviceBindingService.getAnimalIdByDeviceId(deviceId);
            log.debug("设备ID {} 未在映射表中找到", deviceId);
        }

        return animalId;
    }

    // MqttMessageHandler.java - 在文件末尾添加以下方法

    /**
     * 从JSON中获取Double值（支持多个字段名）
     */
    private Double getDoubleValue(JSONObject data, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = data.get(fieldName);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                } else if (value instanceof String) {
                    try {
                        return Double.parseDouble((String) value);
                    } catch (NumberFormatException e) {
                        log.warn("无法解析字段 {} 的值: {}", fieldName, value);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 从JSON中获取Integer值（支持多个字段名）
     */
    private Integer getIntegerValue(JSONObject data, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = data.get(fieldName);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                } else if (value instanceof String) {
                    try {
                        return Integer.parseInt((String) value);
                    } catch (NumberFormatException e) {
                        log.warn("无法解析字段 {} 的值: {}", fieldName, value);
                    }
                }
            }
        }
        return null;
    }
}