package com.yunmu.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SensorDataDTO {
    private String animalId;
    private String deviceId;
    private LocalDateTime timestamp;

    // 加速度数据
    private Double accelX;
    private Double accelY;
    private Double accelZ;

    // GPS数据
    private Double latitude;
    private Double longitude;
    private Double altitude;

    // 声学数据
    private Double soundLevel;
    private Double soundFrequency;

    // 健康数据
    private Integer heartRate;
    private Integer stepCount;

    // 设备信息
    private Integer batteryLevel;
    private Integer signalStrength;
    private Double temperature;
    private Double humidity;


    /**
     * 检查是否有完整的加速度数据
     */
    public boolean hasCompleteAccelData() {
        return accelX != null && accelY != null && accelZ != null;
    }

    /**
     * 检查是否有任何有效数据
     */
    public boolean hasAnyValidData() {
        return accelX != null || heartRate != null || temperature != null ||
                (latitude != null && longitude != null);
    }

    /**
     * 获取加速度模值（安全方法）
     */
    public Double getAccelMagnitude() {
        if (accelX == null || accelY == null || accelZ == null) {
            return null;
        }
        return Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
    }

    /**
     * 检查数据是否有效
     */
    public boolean isValid() {
        // 至少要有一种有效数据
        return hasAnyValidData();
    }
}
