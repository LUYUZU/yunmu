// SensorData.java
package com.yunmu.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_data")
@Data
public class SensorData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String animalId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

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

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
