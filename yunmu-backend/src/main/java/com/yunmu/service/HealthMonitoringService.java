package com.yunmu.service;

import com.yunmu.dto.HealthAssessmentDTO;
import com.yunmu.dto.SensorDataDTO;
import com.yunmu.entity.HealthStatus;
import com.yunmu.entity.AlertRecord;
import java.time.LocalDateTime;
import java.util.List;

public interface HealthMonitoringService {

    /**
     * 综合健康评估
     */
    HealthAssessmentDTO assessAnimalHealth(String animalId);

    /**
     * 实时健康监控
     */
    void monitorRealTimeHealth(SensorDataDTO sensorData);

    /**
     * 生成健康预警
     */
    AlertRecord generateHealthAlert(String animalId, String alertType, String alertMessage);

    /**
     * 检查体温异常
     */
    boolean checkTemperatureAbnormal(Double temperature, String animalType);

    /**
     * 检查心率异常
     */
    boolean checkHeartRateAbnormal(Integer heartRate, String animalType);

    /**
     * 检查行为异常
     */
    boolean checkBehaviorAbnormal(String animalId, LocalDateTime timeWindow);

    /**
     * 获取健康趋势
     */
    List<HealthAssessmentDTO> getHealthTrend(String animalId, int days);
}