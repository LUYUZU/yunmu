// HealthStatus.java
package com.yunmu.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "health_status")
@Data
public class HealthStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String animalId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "overall_status")
    private String overallStatus; // NORMAL, WARNING, ALERT

    // 体温相关
    @Column(name = "body_temperature")
    private Double bodyTemperature;

    @Column(name = "temp_status")
    private String tempStatus;

    // 心率相关
    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "hr_status")
    private String hrStatus;

    // 呼吸相关
    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "rr_status")
    private String rrStatus;

    // 行为相关
    @Column(name = "feeding_duration")
    private Integer feedingDuration;

    @Column(name = "resting_duration")
    private Integer restingDuration;

    @Column(name = "walking_duration")
    private Integer walkingDuration;

    @Column(name = "behavior_status")
    private String behaviorStatus;

    // 位置相关
    @Column(name = "location_status")
    private String locationStatus;

    // 预警信息
    @Column(name = "alert_type")
    private String alertType;

    @Column(name = "alert_message")
    private String alertMessage;

    @Column(name = "alert_level")
    private String alertLevel; // INFO, WARNING, CRITICAL

    @Column(name = "is_resolved")
    private Boolean isResolved = false;

    @Column(name = "resolved_time")
    private LocalDateTime resolvedTime;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
