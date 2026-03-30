package com.yunmu.service.mqtt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.yunmu.dto.SensorDataDTO;
import com.yunmu.service.DataCollectionService;
import com.yunmu.utils.GpsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SimpleMqttService {

    @Autowired
    private DataCollectionService dataCollectionService;

    // 设备ID到动物ID的映射
    private static final Map<String, String> DEVICE_ANIMAL_MAP = new HashMap<>();

    static {
        DEVICE_ANIMAL_MAP.put("7249_001", "cow_001");
        DEVICE_ANIMAL_MAP.put("7249_002", "cow_002");
        DEVICE_ANIMAL_MAP.put("7249_003", "cow_003");
    }

    /**
     * 处理MQTT消息
     */
    public void handleMessage(String topic, String payload) {
        log.info("处理MQTT消息 - Topic: {}, Payload长度: {}", topic, payload != null ? payload.length() : 0);

        try {
            JSONObject jsonData = JSON.parseObject(payload);

            // 根据设备ID获取动物ID
            String deviceId = jsonData.getString("deviceId");
            if (deviceId == null) {
                deviceId = jsonData.getString("device_id");
            }

            String animalId = getAnimalId(deviceId);
            if (animalId == null) {
                log.warn("未找到设备对应的动物ID: {}", deviceId);
                return;
            }

            // 转换为SensorDataDTO
            SensorDataDTO dto = convertToSensorDataDTO(animalId, deviceId, jsonData);

            // 处理数据
            dataCollectionService.processSensorData(dto);

            log.debug("MQTT数据处理完成 - 动物ID: {}", animalId);

        } catch (Exception e) {
            log.error("处理MQTT消息失败 - Topic: {}, Error: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * 转换为SensorDataDTO
     */
    private SensorDataDTO convertToSensorDataDTO(String animalId, String deviceId, JSONObject data) {
        SensorDataDTO dto = new SensorDataDTO();
        dto.setAnimalId(animalId);
        dto.setDeviceId(deviceId);

        // 时间戳
        String timestampStr = data.getString("timestamp");
        if (timestampStr != null) {
            try {
                dto.setTimestamp(LocalDateTime.parse(timestampStr,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } catch (Exception e) {
                dto.setTimestamp(LocalDateTime.now());
            }
        } else {
            dto.setTimestamp(LocalDateTime.now());
        }

        // 加速度数据
        dto.setAccelX(data.getDouble("accelX"));
        dto.setAccelY(data.getDouble("accelY"));
        dto.setAccelZ(data.getDouble("accelZ"));

        // GPS数据 - 处理度分格式
        String latStr = data.getString("lat");
        String lngStr = data.getString("lng");
        if (latStr != null && lngStr != null) {
            // 度分格式转十进制度数
            Double wgs84Lat = GpsUtils.degreeMinuteToDecimal(latStr);
            Double wgs84Lng = GpsUtils.degreeMinuteToDecimal(lngStr);

            if (wgs84Lat != null && wgs84Lng != null) {
                // 转换为GCJ-02（高德地图坐标）
                double[] gcj = GpsUtils.wgs84ToGcj02(wgs84Lng, wgs84Lat);
                dto.setLatitude(gcj[1]);  // 纬度
                dto.setLongitude(gcj[0]); // 经度
            }
        }

        // 健康数据
        dto.setHeartRate(data.getInteger("heartRate"));
        dto.setTemperature(data.getDouble("temperature"));
        dto.setStepCount(data.getInteger("stepCount"));

        // 设备状态
        dto.setBatteryLevel(data.getInteger("battery"));
        dto.setSignalStrength(data.getInteger("signal"));

        return dto;
    }

    /**
     * 获取动物ID
     */
    private String getAnimalId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        // 先从映射表获取
        String animalId = DEVICE_ANIMAL_MAP.get(deviceId);
        // TODO: 如果映射表没有，可以从数据库查询
        return animalId;
    }
}