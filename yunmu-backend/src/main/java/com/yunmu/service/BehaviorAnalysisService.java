package com.yunmu.service;

import com.yunmu.dto.BehaviorRequestDTO;
import com.yunmu.dto.BehaviorResultDTO;
import com.yunmu.dto.SensorDataDTO;
import com.yunmu.entity.BehaviorResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface BehaviorAnalysisService {

    /**
     * 分析采食行为（多源传感融合）
     */
    BehaviorResultDTO analyzeFeeding(String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 实时行为识别
     */
    String identifyRealTimeBehavior(SensorDataDTO sensorData);

    /**
     * 批量行为分析
     */
    List<BehaviorResultDTO> batchBehaviorAnalysis(List<BehaviorRequestDTO> requests);

    /**
     * 获取行为统计数据
     */
    Map<String, Object> getBehaviorStatistics(String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 调用Python机器学习服务
     */
    BehaviorResultDTO callPythonMLService(BehaviorRequestDTO request);
}