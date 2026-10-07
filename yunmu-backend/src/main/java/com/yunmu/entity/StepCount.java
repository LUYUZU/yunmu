// StepCount.java
package com.yunmu.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 步数统计实体
 */
@Entity
@Table(name = "step_counts")
@Data
public class StepCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String animalId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /**
     * 累计步数
     */
    @Column(name = "steps", nullable = false)
    private Integer stepCount;

    /**
     * 今日步数
     */
    @Column(name = "daily_steps")
    private Integer dailySteps;

    /**
     * 行走距离(米)
     */
    @Column(name = "walking_distance")
    private Double walkingDistance;

    /**
     * 活动时长(秒)
     */
    @Column(name = "active_duration")
    private Integer activeDuration;

    /**
     * 步频(步/分钟)
     */
    @Column(name = "step_frequency")
    private Double stepFrequency;

    /**
     * 平均步长(米)
     */
    @Column(name = "avg_step_length")
    private Double avgStepLength;

    /**
     * 活跃程度: low(低), medium(中), high(高)
     */
    @Column(name = "activity_level")
    private String activityLevel;

    /**
     * 步数异常标记
     */
    @Column(name = "is_anomaly")
    private Boolean isAnomaly;

    /**
     * 备注
     */
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
