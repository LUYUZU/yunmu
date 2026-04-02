package com.yunmu.dto;

import lombok.Data;
import java.time.LocalDateTime;

// RealTimeDataDTO.java
@Data
public class RealTimeDataDTO {
    private String animalId;
    private LocalDateTime timestamp;

    // 实时状态
    private String currentBehavior;
    private Double currentTemperature;
    private Integer currentHeartRate;
    private String healthStatus;

    // 位置信息
    private Double latitude;
    private Double longitude;
    private Integer stepCountToday;

    // 统计信息
    private Integer feedingDurationToday;
    private Integer restingDurationToday;

    // 预警信息
    private Boolean hasActiveAlert;
    private String activeAlertType;
}

