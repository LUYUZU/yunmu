package com.yunmu.service.impl;

import com.yunmu.dto.HealthAssessmentDTO;
import com.yunmu.dto.SensorDataDTO;  // 确保这行存在
import com.yunmu.entity.HealthStatus;
import com.yunmu.entity.AlertRecord;
import com.yunmu.repository.HealthStatusRepository;
import com.yunmu.repository.AlertRecordRepository;
import com.yunmu.service.HealthMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class HealthMonitoringServiceImpl implements HealthMonitoringService {

    @Autowired
    private HealthStatusRepository healthStatusRepository;

    @Autowired
    private AlertRecordRepository alertRecordRepository;

    // 动物正常生理参数范围（牛）
    private static final Map<String, Map<String, double[]>> ANIMAL_NORMAL_RANGES = new HashMap<>();

    static {
        // 牛的生理参数正常范围
        Map<String, double[]> cowRanges = new HashMap<>();
        cowRanges.put("temperature", new double[]{38.0, 39.5});    // 体温 (°C)
        cowRanges.put("heart_rate", new double[]{60.0, 80.0});     // 心率 (bpm)
        cowRanges.put("respiratory_rate", new double[]{20.0, 40.0}); // 呼吸率 (rpm)
        cowRanges.put("feeding_duration", new double[]{6.0, 10.0});   // 采食时长 (小时)
        cowRanges.put("walking_duration", new double[]{2.0, 6.0});     // 行走时长 (小时)

        ANIMAL_NORMAL_RANGES.put("cow", cowRanges);

        // 羊的生理参数正常范围
        Map<String, double[]> sheepRanges = new HashMap<>();
        sheepRanges.put("temperature", new double[]{38.5, 40.0});
        sheepRanges.put("heart_rate", new double[]{70.0, 90.0});
        sheepRanges.put("respiratory_rate", new double[]{25.0, 50.0});
        sheepRanges.put("feeding_duration", new double[]{5.0, 8.0});
        sheepRanges.put("walking_duration", new double[]{2.0, 5.0});

        ANIMAL_NORMAL_RANGES.put("sheep", sheepRanges);
    }

    @Override
    public HealthAssessmentDTO assessAnimalHealth(String animalId) {
        try {
            log.info("评估动物健康状态，动物ID: {}", animalId);

            // 获取最新健康状态
            HealthStatus latestStatus = healthStatusRepository.findLatestByAnimalId(animalId)
                    .orElseGet(() -> createInitialHealthStatus(animalId));

            // 构建评估结果
            HealthAssessmentDTO assessment = new HealthAssessmentDTO();
            assessment.setAnimalId(animalId);
            assessment.setAssessmentTime(LocalDateTime.now());
            assessment.setHealthStatus(latestStatus.getOverallStatus());

            // 计算健康评分
            double healthScore = calculateHealthScore(latestStatus, animalId);
            assessment.setHealthScore(healthScore);

            // 设置各项指标评分
            assessment.setTemperatureScore(calculateTemperatureScore(latestStatus.getBodyTemperature(), animalId));
            assessment.setHeartRateScore(calculateHeartRateScore(latestStatus.getHeartRate(), animalId));
            assessment.setBehaviorScore(calculateBehaviorScore(latestStatus));
            assessment.setActivityScore(calculateActivityScore(latestStatus));

            // 检查预警
            checkAndGenerateAlerts(latestStatus, animalId);

            assessment.setHasAlert(latestStatus.getAlertLevel() != null);
            assessment.setAlertType(latestStatus.getAlertType());
            assessment.setAlertMessage(latestStatus.getAlertMessage());
            assessment.setAlertLevel(latestStatus.getAlertLevel());

            return assessment;

        } catch (Exception e) {
            log.error("评估动物健康状态失败", e);
            return createDefaultHealthAssessment(animalId);
        }
    }

    @Override
    @Transactional
    public void monitorRealTimeHealth(SensorDataDTO sensorData) {
        try {
            String animalId = sensorData.getAnimalId();

            // 获取或创建健康状态
            HealthStatus healthStatus = healthStatusRepository.findLatestByAnimalId(animalId)
                    .orElseGet(() -> createInitialHealthStatus(animalId));

            // 更新实时数据
            updateHealthStatusWithSensorData(healthStatus, sensorData);

            // 检查异常并生成预警
            checkRealTimeAlerts(healthStatus, sensorData);

            // 保存更新
            healthStatusRepository.save(healthStatus);

            log.debug("实时健康监控完成，动物ID: {}", animalId);

        } catch (Exception e) {
            log.error("实时健康监控失败", e);
        }
    }

    @Override
    @Transactional
    public AlertRecord generateHealthAlert(String animalId, String alertType, String alertMessage) {
        try {
            AlertRecord alert = new AlertRecord();
            alert.setAnimalId(animalId);
            alert.setAlertTime(LocalDateTime.now());
            alert.setAlertType(alertType);
            alert.setAlertMessage(alertMessage);

            // 根据预警类型设置级别
            String alertLevel = determineAlertLevel(alertType);
            alert.setAlertLevel(alertLevel);

            alert.setAlertStatus("ACTIVE");
            alert.setNotificationSent(false);
            alert.setNotificationChannels("WEB"); // 默认只发送到Web

            AlertRecord savedAlert = alertRecordRepository.save(alert);

            log.info("生成健康预警，动物ID: {}, 类型: {}, 级别: {}",
                    animalId, alertType, alertLevel);

            // 触发通知
            triggerAlertNotification(savedAlert);

            return savedAlert;

        } catch (Exception e) {
            log.error("生成健康预警失败", e);
            return null;
        }
    }

    @Override
    public boolean checkTemperatureAbnormal(Double temperature, String animalType) {
        if (temperature == null) {
            return false;
        }

        double[] range = getNormalRange(animalType, "temperature");
        return temperature < range[0] || temperature > range[1];
    }

    @Override
    public boolean checkHeartRateAbnormal(Integer heartRate, String animalType) {
        if (heartRate == null) {
            return false;
        }

        double[] range = getNormalRange(animalType, "heart_rate");
        return heartRate < range[0] || heartRate > range[1];
    }

    @Override
    public boolean checkBehaviorAbnormal(String animalId, LocalDateTime timeWindow) {
        try {
            // 获取指定时间窗口内的行为数据
            LocalDateTime startTime = timeWindow.minusHours(24);

            // 这里需要调用行为分析服务
            // 简化处理：假设总是返回正常
            return false;

        } catch (Exception e) {
            log.error("检查行为异常失败", e);
            return false;
        }
    }

    @Override
    public List<HealthAssessmentDTO> getHealthTrend(String animalId, int days) {
        try {
            List<HealthAssessmentDTO> trend = new ArrayList<>();
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusDays(days);

            // 获取时间段内的健康状态记录
            List<HealthStatus> healthStatusList = healthStatusRepository
                    .findByAnimalIdAndTimeRange(animalId, startTime, endTime);

            // 转换为DTO
            for (HealthStatus status : healthStatusList) {
                HealthAssessmentDTO dto = new HealthAssessmentDTO();
                dto.setAnimalId(animalId);
                dto.setAssessmentTime(status.getTimestamp());
                dto.setHealthStatus(status.getOverallStatus());
                dto.setHealthScore(calculateHealthScore(status, animalId));

                trend.add(dto);
            }

            return trend;

        } catch (Exception e) {
            log.error("获取健康趋势失败", e);
            return Collections.emptyList();
        }
    }

    private HealthStatus createInitialHealthStatus(String animalId) {
        HealthStatus status = new HealthStatus();
        status.setAnimalId(animalId);
        status.setTimestamp(LocalDateTime.now());
        status.setOverallStatus("NORMAL");
        status.setCreateTime(LocalDateTime.now());
        return status;
    }

    private double calculateHealthScore(HealthStatus status, String animalId) {
        double totalScore = 0.0;
        int factorCount = 0;

        // 体温评分
        if (status.getBodyTemperature() != null) {
            double tempScore = calculateTemperatureScore(status.getBodyTemperature(), animalId);
            totalScore += tempScore;
            factorCount++;
        }

        // 心率评分
        if (status.getHeartRate() != null) {
            double hrScore = calculateHeartRateScore(status.getHeartRate(), animalId);
            totalScore += hrScore;
            factorCount++;
        }

        // 行为评分
        double behaviorScore = calculateBehaviorScore(status);
        totalScore += behaviorScore;
        factorCount++;

        // 活动评分
        double activityScore = calculateActivityScore(status);
        totalScore += activityScore;
        factorCount++;

        return factorCount > 0 ? totalScore / factorCount : 0.5; // 默认0.5
    }

    private double calculateTemperatureScore(Double temperature, String animalId) {
        if (temperature == null) {
            return 0.5;
        }

        double[] range = getNormalRange(getAnimalTypeFromId(animalId), "temperature");
        double lower = range[0];
        double upper = range[1];

        if (temperature >= lower && temperature <= upper) {
            return 1.0; // 正常范围
        } else if (temperature >= lower - 0.5 && temperature <= upper + 0.5) {
            return 0.5; // 轻度异常
        } else {
            return 0.0; // 严重异常
        }
    }

    private double calculateHeartRateScore(Integer heartRate, String animalId) {
        if (heartRate == null) {
            return 0.5;
        }

        double[] range = getNormalRange(getAnimalTypeFromId(animalId), "heart_rate");
        double lower = range[0];
        double upper = range[1];

        if (heartRate >= lower && heartRate <= upper) {
            return 1.0;
        } else if (heartRate >= lower - 10 && heartRate <= upper + 10) {
            return 0.5;
        } else {
            return 0.0;
        }
    }

    private double calculateBehaviorScore(HealthStatus status) {
        // 简化处理：根据行为状态评分
        if ("NORMAL".equals(status.getBehaviorStatus())) {
            return 1.0;
        } else if ("WARNING".equals(status.getBehaviorStatus())) {
            return 0.5;
        } else {
            return 0.0;
        }
    }

    private double calculateActivityScore(HealthStatus status) {
        // 简化处理：假设总是正常
        return 1.0;
    }

    private void checkAndGenerateAlerts(HealthStatus status, String animalId) {
        List<String> alerts = new ArrayList<>();

        // 检查体温异常
        if (status.getBodyTemperature() != null) {
            if (checkTemperatureAbnormal(status.getBodyTemperature(), getAnimalTypeFromId(animalId))) {
                alerts.add("体温异常: " + status.getBodyTemperature() + "°C");
                status.setAlertType("TEMPERATURE_ABNORMAL");
            }
        }

        // 检查心率异常
        if (status.getHeartRate() != null) {
            if (checkHeartRateAbnormal(status.getHeartRate(), getAnimalTypeFromId(animalId))) {
                alerts.add("心率异常: " + status.getHeartRate() + " bpm");
                status.setAlertType("HEART_RATE_ABNORMAL");
            }
        }

        // 检查行为异常
        if ("ALERT".equals(status.getBehaviorStatus())) {
            alerts.add("行为异常");
            status.setAlertType("BEHAVIOR_ABNORMAL");
        }

        // 如果有预警，更新状态
        if (!alerts.isEmpty()) {
            status.setAlertMessage(String.join("; ", alerts));
            status.setAlertLevel("WARNING");
            status.setOverallStatus("WARNING");

            // 生成预警记录
            generateHealthAlert(animalId, status.getAlertType(), status.getAlertMessage());
        } else {
            status.setAlertType(null);
            status.setAlertMessage(null);
            status.setAlertLevel(null);
            status.setOverallStatus("NORMAL");
        }
    }

    private void updateHealthStatusWithSensorData(HealthStatus status, SensorDataDTO sensorData) {
        status.setTimestamp(LocalDateTime.now());

        // 更新体温
        if (sensorData.getTemperature() != null) {
            status.setBodyTemperature(sensorData.getTemperature());
            status.setTempStatus(assessParameterStatus(
                    sensorData.getTemperature(),
                    getAnimalTypeFromId(sensorData.getAnimalId()),
                    "temperature"
            ));
        }

        // 更新心率
        if (sensorData.getHeartRate() != null) {
            status.setHeartRate(sensorData.getHeartRate());
            status.setHrStatus(assessParameterStatus(
                    sensorData.getHeartRate().doubleValue(),
                    getAnimalTypeFromId(sensorData.getAnimalId()),
                    "heart_rate"
            ));
        }
    }

    private void checkRealTimeAlerts(HealthStatus status, SensorDataDTO sensorData) {
        // 实时检查预警
        if (sensorData.getTemperature() != null &&
                checkTemperatureAbnormal(sensorData.getTemperature(), getAnimalTypeFromId(sensorData.getAnimalId()))) {

            String alertMessage = String.format("体温异常警报: %.1f°C", sensorData.getTemperature());
            generateHealthAlert(sensorData.getAnimalId(), "TEMPERATURE_ABNORMAL", alertMessage);
        }

        if (sensorData.getHeartRate() != null &&
                checkHeartRateAbnormal(sensorData.getHeartRate(), getAnimalTypeFromId(sensorData.getAnimalId()))) {

            String alertMessage = String.format("心率异常警报: %d bpm", sensorData.getHeartRate());
            generateHealthAlert(sensorData.getAnimalId(), "HEART_RATE_ABNORMAL", alertMessage);
        }
    }

    private String assessParameterStatus(double value, String animalType, String parameter) {
        double[] range = getNormalRange(animalType, parameter);

        if (value >= range[0] && value <= range[1]) {
            return "NORMAL";
        } else if (value >= range[0] - (range[1] - range[0]) * 0.2 &&
                value <= range[1] + (range[1] - range[0]) * 0.2) {
            return "WARNING";
        } else {
            return "ALERT";
        }
    }

    private double[] getNormalRange(String animalType, String parameter) {
        Map<String, double[]> ranges = ANIMAL_NORMAL_RANGES.get(animalType.toLowerCase());
        if (ranges == null) {
            // 默认使用牛的范围
            ranges = ANIMAL_NORMAL_RANGES.get("cow");
        }

        double[] range = ranges.get(parameter);
        if (range == null) {
            // 默认范围
            return new double[]{0.0, 100.0};
        }

        return range;
    }

    private String getAnimalTypeFromId(String animalId) {
        // 根据动物ID判断动物类型
        if (animalId.startsWith("cow")) {
            return "cow";
        } else if (animalId.startsWith("sheep")) {
            return "sheep";
        } else {
            return "cow"; // 默认牛
        }
    }

    private String determineAlertLevel(String alertType) {
        switch (alertType) {
            case "TEMPERATURE_ABNORMAL":
            case "HEART_RATE_ABNORMAL":
                return "CRITICAL";
            case "BEHAVIOR_ABNORMAL":
            case "LOCATION_ABNORMAL":
                return "WARNING";
            default:
                return "INFO";
        }
    }

    private void triggerAlertNotification(AlertRecord alert) {
        try {
            // 这里可以集成各种通知方式：短信、邮件、App推送等
            log.info("触发预警通知: {} - {} - {}",
                    alert.getAnimalId(), alert.getAlertType(), alert.getAlertMessage());

            // 更新通知状态
            alert.setNotificationSent(true);
            alertRecordRepository.save(alert);

        } catch (Exception e) {
            log.error("触发预警通知失败", e);
        }
    }

    private HealthAssessmentDTO createDefaultHealthAssessment(String animalId) {
        HealthAssessmentDTO dto = new HealthAssessmentDTO();
        dto.setAnimalId(animalId);
        dto.setAssessmentTime(LocalDateTime.now());
        dto.setHealthStatus("UNKNOWN");
        dto.setHealthScore(0.5);
        return dto;
    }
}