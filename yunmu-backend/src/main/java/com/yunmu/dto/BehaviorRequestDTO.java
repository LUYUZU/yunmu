package com.yunmu.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;


// BehaviorRequestDTO.java
@Data
public class BehaviorRequestDTO {
    private String animalId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String dataType; // ACCEL, ACOUSTIC, COMBINED

    // 原始数据（可选）
    private List<Double> accelData;
    private List<Double> soundData;
}

