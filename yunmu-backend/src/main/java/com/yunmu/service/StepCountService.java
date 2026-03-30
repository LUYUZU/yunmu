// StepCountService.java
package com.yunmu.service;

import com.yunmu.entity.StepCount;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface StepCountService {

    /**
     * 统计步数
     */
    StepCount countSteps(String animalId, Integer stepCount, Double accelMagnitude);

    /**
     * 批量统计步数
     */
    List<StepCount> batchCountSteps(List<Map<String, Object>> sensorDataList);

    /**
     * 获取当前步数
     */
    StepCount getCurrentSteps(String animalId);

    /**
     * 获取步数历史
     */
    List<StepCount> getStepHistory(String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取步数统计
     */
    Map<String, Object> getStepStatistics(String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取今日步数
     */
    Integer getTodaySteps(String animalId);

    /**
     * 调用Python服务进行步数统计
     */
    Map<String, Object> callStepCountApi(String animalId, Map<String, Object> sensorData);

    /**
     * 获取所有动物今日步数
     */
    Map<String, Integer> getAllAnimalTodaySteps();
}
