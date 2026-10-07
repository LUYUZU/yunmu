// PostureResult.java
package com.yunmu.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 姿态识别结果实体
 */
@Entity
@Table(name = "posture_results")
@Data
public class PostureResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String animalId;

    @Column(name = "device_id")
    private String deviceId;

    /**
     * 数据库存 bigint（Unix 秒），用 timestampEpoch 对应。
     * Java 代码用 getTimestamp() / setTimestamp() 操作 LocalDateTime，
     * 内部自动与 timestampEpoch 互转。
     */
    @Column(name = "timestamp", nullable = false)
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

    /**
     * 姿态类型: standing(站立), lying(躺卧), walking(行走), feeding(采食), running(奔跑)
     */
    @Column(nullable = false)
    private String postureType;

    /**
     * 姿态置信度
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /**
     * 加速度X轴
     */
    @Column(name = "accel_x")
    private Double accelX;

    /**
     * 加速度Y轴
     */
    @Column(name = "accel_y")
    private Double accelY;

    /**
     * 加速度Z轴
     */
    @Column(name = "accel_z")
    private Double accelZ;

    /**
     * 加速度向量模
     */
    @Column(name = "accel_magnitude")
    private Double accelMagnitude;

    /**
     * 陀螺仪X轴
     */
    @Column(name = "gyro_x")
    private Double gyroX;

    /**
     * 陀螺仪Y轴
     */
    @Column(name = "gyro_y")
    private Double gyroY;

    /**
     * 陀螺仪Z轴
     */
    @Column(name = "gyro_z")
    private Double gyroZ;

    /**
     * 倾斜角度(度)
     */
    @Column(name = "tilt_angle")
    private Double tiltAngle;

    /**
     * 站立持续时间(秒)
     */
    @Column(name = "standing_duration")
    private Integer standingDuration;

    /**
     * 躺卧持续时间(秒)
     */
    @Column(name = "lying_duration")
    private Integer lyingDuration;

    /**
     * 模型类型
     */
    @Column(name = "model_type")
    private String modelType;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
