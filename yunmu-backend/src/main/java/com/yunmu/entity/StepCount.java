// StepCount.java
package com.yunmu.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
    private String animalId;

    /**
     * 数据库存 bigint（Unix 毫秒），用 timestampEpoch 对应。
     * Java 代码用 getTimestamp() / setTimestamp() 操作 LocalDateTime，
     * 内部自动与 timestampEpoch 互转。
     */
    @Column(name = "timestamp")
    private Long timestampEpoch;  // ← 唯一的时间戳字段（对应数据库 bigint 列）

    @Transient
    public LocalDateTime getTimestamp() {
        if (timestampEpoch == null) return null;
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestampEpoch), ZoneId.systemDefault());
    }

    @Transient
    public void setTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            this.timestampEpoch = null;
        } else {
            this.timestampEpoch = timestamp.atZone(ZoneId.systemDefault())
                                          .toInstant().toEpochMilli();
        }
    }

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
        if (timestampEpoch == null) {
            timestampEpoch = System.currentTimeMillis();
        }
    }
}
