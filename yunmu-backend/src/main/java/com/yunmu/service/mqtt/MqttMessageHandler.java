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
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = new RestTemplate();

    // Python服务地址
    private static final String PYTHON_API_URL = "http://127.0.0.1:5000/api/device/data";

    // Java 回调地址（Python 处理完毕后回调此地址通知结果）
    private static final String JAVA_CALLBACK_URL = "http://127.0.0.1:8080/api/ml/callback";

    // 设备ID到动物ID的映射（可从数据库加载）
    private static final Map<String, String> DEVICE_ANIMAL_MAP = new HashMap<>();

    static {
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

            // 3. 调用Python API处理数据（同步，等 Python ML 结果）
            JSONObject mlResult = callPythonApi(topic, jsonData);

            // 4. 继续原有的业务逻辑处理
            if (topic.contains("heartbeat")) {
                handleHeartbeatData(jsonData);
            } else {
                // 有 ML 结果：用 processSensorDataWithMlResult（跳过本地姿态识别，避免重复）
                // 无 ML 结果：降级到原有逻辑
                if (mlResult != null) {
                    handleSensorDataWithMlResult(jsonData, mlResult);
                } else {
                    log.warn("Python ML 返回空，降级到原有逻辑处理");
                    handleMixedData(jsonData);
                }
            }

        } catch (Exception e) {
            log.error("处理MQTT消息时发生异常", e);
        }
    }

    /**
     * 调用Python API处理数据（同步等待结果 + 回调通知）
     * @return Python 返回的 ML 结果（JSONObject），失败返回 null
     */
    private JSONObject callPythonApi(String topic, JSONObject data) {
        try {
            Map<String, Object> pythonData = new HashMap<>();
            pythonData.put("topic", topic);
            pythonData.put("device_id", topic);

            // 设备ID → 动物ID（优先从payload取animal_id，否则从topic映射）
            String animalId = data.getString("animal_id");
            if (animalId == null || animalId.isEmpty()) {
                animalId = getAnimalId(topic);
            }
            pythonData.put("animal_id", animalId);
            pythonData.put("device_id", topic);

            // 提取经纬度（WGS84）
            Object jd = data.get("JD");
            Object wd = data.get("WD");
            if (jd != null && wd != null) {
                pythonData.put("longitude", Double.parseDouble(jd.toString()));
                pythonData.put("latitude", Double.parseDouble(wd.toString()));
            }

            // 提取移动状态 (0=静止, 1=移动, 2=跑)
            Object move = data.get("move");
            pythonData.put("move", move != null ? Integer.parseInt(move.toString()) : 0);

            // 提取步数
            Object bushu = data.get("bushu");
            pythonData.put("steps", bushu != null ? Integer.parseInt(bushu.toString()) : 0);

            // 提取计数器
            Object counter = data.get("counter");
            if (counter != null) {
                pythonData.put("counter", Integer.parseInt(counter.toString()));
            }

            // 提取加速度原始数据（Python 需要这些做姿态识别）
            pythonData.put("accel_x", data.get("ax"));
            pythonData.put("accel_y", data.get("ay"));
            pythonData.put("accel_z", data.get("az"));
            pythonData.put("gyro_x", data.get("gx"));
            pythonData.put("gyro_y", data.get("gy"));
            pythonData.put("gyro_z", data.get("gz"));

            // 提取心率、体温、电量、信号强度
            pythonData.put("heart_rate", data.get("heart_rate"));
            pythonData.put("temperature", data.get("temp"));
            pythonData.put("battery", data.get("bat"));
            pythonData.put("signal_strength", data.get("signal"));

            // 时间戳（优先用传感器上报的ts，毫秒；否则用Java系统时间）
            Object tsObj = data.get("ts");
            if (tsObj != null) {
                pythonData.put("timestamp", Long.parseLong(tsObj.toString()) / 1000);
            } else {
                pythonData.put("timestamp", System.currentTimeMillis() / 1000);
            }

            // ★ 关键：告诉 Python 处理完后回调哪个 URL
            pythonData.put("callback_url", JAVA_CALLBACK_URL);

            log.info("调用Python API: {}", pythonData);

            // 同步调用（等待 Python 返回结果，不阻塞则无法使用 Python 的 ML 分析结果）
            String response = restTemplate.postForObject(
                    PYTHON_API_URL,
                    pythonData,
                    String.class
            );
            log.info("Python API响应: {}", response);

            if (response != null && !response.isEmpty()) {
                return JSON.parseObject(response);
            }

        } catch (Exception e) {
            log.error("调用Python API失败: {}", e.getMessage());
        }
        return null;
    }

    // ========== 以下是原有的方法，保持不变 ==========

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

    private void handleGpsData(JSONObject data) {
        // 原有代码保持不变
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
            String latStr = data.getString("lat");
            String lngStr = data.getString("lng");
            if (latStr == null || lngStr == null) {
                log.warn("GPS数据缺少坐标信息");
                return;
            }
            Double wgs84Lat = GpsUtils.degreeMinuteToDecimal(latStr);
            Double wgs84Lng = GpsUtils.degreeMinuteToDecimal(lngStr);
            if (wgs84Lat == null || wgs84Lng == null) {
                log.warn("坐标转换失败 - lat: {}, lng: {}", latStr, lngStr);
                return;
            }
            double[] gcj = GpsUtils.wgs84ToGcj02(wgs84Lng, wgs84Lat);
            SensorDataDTO dto = new SensorDataDTO();
            dto.setAnimalId(animalId);
            dto.setDeviceId(deviceId);
            dto.setLatitude(gcj[1]);
            dto.setLongitude(gcj[0]);
            dto.setTimestamp(parseTimestamp(data.getString("timestamp")));
            dto.setBatteryLevel(data.getInteger("battery"));
            dto.setSignalStrength(data.getInteger("signal"));
            dataCollectionService.processSensorData(dto);
            log.info("GPS数据处理完成 - 动物ID: {}, 坐标: ({}, {})", animalId, gcj[0], gcj[1]);
        } catch (Exception e) {
            log.error("处理GPS数据失败", e);
        }
    }

    private void handleSensorData(JSONObject data) {
        // 原有代码保持不变
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
            dto.setAccelX(getDoubleValue(data, "accelX", "accel_x", "x", "accX"));
            dto.setAccelY(getDoubleValue(data, "accelY", "accel_y", "y", "accY"));
            dto.setAccelZ(getDoubleValue(data, "accelZ", "accel_z", "z", "accZ"));
            dto.setHeartRate(getIntegerValue(data, "heartRate", "heart_rate", "hr", "bpm"));
            dto.setTemperature(getDoubleValue(data, "temperature", "temp", "tmp"));
            dto.setStepCount(getIntegerValue(data, "stepCount", "step_count", "steps"));
            dto.setSoundLevel(getDoubleValue(data, "soundLevel", "sound_level", "db"));
            dto.setSoundFrequency(getDoubleValue(data, "soundFrequency", "sound_frequency", "freq"));
            dto.setBatteryLevel(getIntegerValue(data, "battery", "battery_level", "bat"));
            dto.setSignalStrength(getIntegerValue(data, "signal", "signal_strength", "rssi"));
            dataCollectionService.processSensorData(dto);
            log.info("传感器数据处理完成 - 动物ID: {}, 心率: {}, 温度: {}, 步数: {}",
                    animalId, dto.getHeartRate(), dto.getTemperature(), dto.getStepCount());
        } catch (Exception e) {
            log.error("处理传感器数据失败", e);
        }
    }

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
        } catch (Exception e) {
            log.error("处理心跳数据失败", e);
        }
    }

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
            dto.setAccelX(getDoubleValue(data, "accelX", "accel_x", "x", "accX"));
            dto.setAccelY(getDoubleValue(data, "accelY", "accel_y", "y", "accY"));
            dto.setAccelZ(getDoubleValue(data, "accelZ", "accel_z", "z", "accZ"));
            dto.setHeartRate(getIntegerValue(data, "heartRate", "heart_rate", "hr", "bpm"));
            dto.setTemperature(getDoubleValue(data, "temperature", "temp", "tmp"));
            dto.setStepCount(getIntegerValue(data, "stepCount", "step_count", "steps"));
            dto.setSoundLevel(getDoubleValue(data, "soundLevel", "sound_level", "db"));
            dto.setSoundFrequency(getDoubleValue(data, "soundFrequency", "sound_frequency", "freq"));
            dto.setBatteryLevel(getIntegerValue(data, "battery", "battery_level", "bat"));
            dto.setSignalStrength(getIntegerValue(data, "signal", "signal_strength", "rssi"));
            dataCollectionService.processSensorData(dto);
            log.info("混合数据处理完成 - 动物ID: {}", animalId);
        } catch (Exception e) {
            log.error("处理混合数据失败", e);
        }
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        try {
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

    private String getAnimalId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        String animalId = DEVICE_ANIMAL_MAP.get(deviceId);
        if (animalId == null) {
            log.debug("设备ID {} 未在映射表中找到", deviceId);
        }
        return animalId;
    }

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

    /**
     * 使用 Python ML 结果处理传感器数据
     * 跳过人脸姿态识别（Python 已处理），避免重复
     */
    private void handleSensorDataWithMlResult(JSONObject data, JSONObject mlResult) {
        try {
            String deviceId = data.getString("deviceId");
            if (deviceId == null) deviceId = data.getString("device_id");
            String animalId = getAnimalId(deviceId);
            if (animalId == null) animalId = deviceId;

            SensorDataDTO dto = new SensorDataDTO();
            dto.setAnimalId(animalId);
            dto.setDeviceId(deviceId);
            dto.setTimestamp(parseTimestamp(data.getString("timestamp")));

            // GPS 坐标
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

            // 原始加速度数据
            dto.setAccelX(getDoubleValue(data, "accelX", "accel_x", "x", "accX"));
            dto.setAccelY(getDoubleValue(data, "accelY", "accel_y", "y", "accY"));
            dto.setAccelZ(getDoubleValue(data, "accelZ", "accel_z", "z", "accZ"));
            dto.setHeartRate(getIntegerValue(data, "heartRate", "heart_rate", "hr", "bpm"));
            dto.setTemperature(getDoubleValue(data, "temperature", "temp", "tmp"));
            dto.setStepCount(getIntegerValue(data, "stepCount", "step_count", "steps"));
            dto.setBatteryLevel(getIntegerValue(data, "battery", "battery_level", "bat"));
            dto.setSignalStrength(getIntegerValue(data, "signal", "signal_strength", "rssi"));

            // ★ 关键：传入 Python ML 结果，跳过 Java 本地姿态识别
            dataCollectionService.processSensorDataWithMlResult(dto, mlResult);

            log.info("MQTT数据处理完成（含Python ML） - 动物ID: {}", animalId);

        } catch (Exception e) {
            log.error("处理MQTT数据（含ML）失败", e);
        }
    }
}