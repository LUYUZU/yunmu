// PostureResult.java
package com.yunmu.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * 姿态类型: standing(站立), lying(躺卧), walking(行走), feeding(采食), ruminating(反刍)
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
