package com.yunmu.dto;

import lombok.Data;
import java.time.LocalDateTime;

// HealthAssessmentDTO.java
@Data
public class HealthAssessmentDTO {
    private String animalId;
    private LocalDateTime assessmentTime;
    private String healthStatus;
    private Double healthScore;

    // 各项指标
    private Double temperatureScore;
    private Double heartRateScore;
    private Double behaviorScore;
    private Double activityScore;

    // 预警信息
    private Boolean hasAlert;
    private String alertType;
    private String alertMessage;
    private String alertLevel;
}

