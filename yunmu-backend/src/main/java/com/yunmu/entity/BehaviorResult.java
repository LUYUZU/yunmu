// BehaviorResult.java
package com.yunmu.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "behavior_results")
@Data
public class BehaviorResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String animalId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(nullable = false)
    private String behaviorType; // feeding, resting, walking, standing

    @Column(name = "confidence_score")
    private Double confidenceScore;

    // 模型来源
    @Column(name = "model_type")
    private String modelType; // svm, logistic_regression, ensemble

    // 数据来源
    @Column(name = "data_modality")
    private String dataModality; // accelerometer, acoustic, combined

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    // 详细特征
    @Column(name = "chewing_count")
    private Integer chewingCount;

    @Column(name = "jaw_movement_rate")
    private Double jawMovementRate;

    @Column(name = "sound_intensity")
    private Double soundIntensity;

    @Column(name = "activity_level")
    private Double activityLevel;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
