package com.yunmu.service;

import com.yunmu.dto.SensorDataDTO;
import com.yunmu.entity.SensorData;
import com.yunmu.entity.LocationTrack;
import com.alibaba.fastjson.JSONObject;
import java.util.List;

public interface DataCollectionService {

    /**
     * 处理传感器数据
     */
    SensorData processSensorData(SensorDataDTO sensorDataDTO);

    /**
     * 处理传感器数据（携带 Python ML 结果，避免重复调用）
     */
    SensorData processSensorDataWithMlResult(SensorDataDTO sensorDataDTO, JSONObject mlResult);

    /**
     * 验证GPS数据质量
     */
    boolean validateGpsData(Double latitude, Double longitude, Double accuracy);

    /**
     * 过滤异常传感器数据
     */
    boolean filterAbnormalData(SensorDataDTO sensorDataDTO);

    /**
     * 计算步数
     */
    Integer calculateStepCount(Double accelX, Double accelY, Double accelZ);

    /**
     * 更新位置轨迹
     */
    LocationTrack updateLocationTrack(SensorDataDTO sensorDataDTO);

    /**
     * 批量处理数据
     */
    void batchProcessData(List<SensorDataDTO> dataList);
}