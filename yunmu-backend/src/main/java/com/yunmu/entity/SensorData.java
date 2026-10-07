// SensorData.java
package com.yunmu.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "sensor_data")
@Data
public class SensorData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String animalId;

    @Column(name = "device_id", nullable = true)
    private String deviceId;

    @Column(name = "timestamp")
    private Long timestampEpoch;  // Unix 秒

    @Transient
    public LocalDateTime getTimestamp() {
        return timestampEpoch != null ? 
            LocalDateTime.ofEpochSecond(timestampEpoch, 0, ZoneOffset.ofHours(8)) : null;
    }

    @Transient
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestampEpoch = timestamp != null ? 
            timestamp.toEpochSecond(ZoneOffset.ofHours(8)) : null;
    }

    // 加速度数据
    private Double accelX;
    private Double accelY;
    private Double accelZ;

    // GPS数据
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double speed;
    private Integer satelliteCount;

    // 环境数据
    private Double temperature;
    private Double humidity;

    // 声学数据
    @Column(name = "sound_level")
    private Double soundLevel;

    @Column(name = "sound_frequency")
    private Double soundFrequency;

    // 健康数据
    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "step_count")
    private Integer stepCount;

    // 姿态数据
    @Column(name = "posture_type")
    private String postureType;

    @Column(name = "posture_confidence")
    private Double postureConfidence;

    @Column(name = "is_valid")
    private Boolean isValid = true;

    @Column(name = "data_source")
    private String dataSource; // collar, gateway, etc.

    // 项圈设备状态（设备列表/在线状态展示用）
    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "signal_strength")
    private Integer signalStrength;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
