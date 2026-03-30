package com.yunmu.service;

import com.yunmu.entity.LocationTrack;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface LocationTrackingService {

    /**
     * 更新动物位置（自动进行坐标转换）
     */
    LocationTrack updateAnimalLocation(String animalId, Double latitude, Double longitude);

    /**
     * 更新动物位置（带坐标类型参数）
     * @param coordinateType 坐标类型：WGS84, GCJ02, BD09
     */
    LocationTrack updateAnimalLocation(String animalId, Double latitude, Double longitude, String coordinateType);

    /**
     * 获取实时位置（已转换为GCJ-02）
     */
    LocationTrack getCurrentLocation(String animalId);

    /**
     * 获取轨迹历史（已转换为GCJ-02）
     */
    List<LocationTrack> getTrackHistory(String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 计算活动范围
     */
    Map<String, Object> calculateActivityRange(String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 检测越界行为
     */
    boolean checkBoundaryViolation(String animalId, Double latitude, Double longitude);

    /**
     * 生成热力图数据
     */
    List<Map<String, Object>> generateHeatmapData(String pastureId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 批量更新位置
     */
    void batchUpdateLocations(List<Map<String, Object>> locationList);
}