package com.yunmu.dto;

import lombok.Data;
import java.time.LocalDateTime;


@Data
public class BehaviorResultDTO {
    private String animalId;
    private String behaviorType;
    private Double confidence;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration;

    // 详细特征
    private Double jawMovementRate;
    private Integer chewingCount;
    private Double soundIntensity;
    private Double activityLevel;

    // 位置信息
    private Double latitude;
    private Double longitude;
}
