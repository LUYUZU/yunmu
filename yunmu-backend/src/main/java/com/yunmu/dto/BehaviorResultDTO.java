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

    // 缺陷 B 修复：模型来源与数据模态（按实际调用来源动态写入，不再固定写死 rule_based/accel）
    private String modelType;
    private String dataModality;

    // 空态标记：查询时间段无数据时为 true（不再用 feeding/0.5 假数据掩盖）
    private Boolean empty;
}
