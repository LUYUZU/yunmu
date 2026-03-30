// PostureRecognitionService.java
package com.yunmu.service;

import com.yunmu.entity.PostureResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface PostureRecognitionService {

    /**
     * 识别姿态
     */
    PostureResult recognizePosture(String animalId, Double accelX, Double accelY, Double accelZ,
                                   Double gyroX, Double gyroY, Double gyroZ);

    /**
     * 批量识别姿态
     */
    List<PostureResult> batchRecognizePosture(List<Map<String, Object>> sensorDataList);

    /**
     * 获取动物当前姿态
     */
    PostureResult getCurrentPosture(String animalId);

    /**
     * 获取姿态历史
     */
    List<PostureResult> getPostureHistory(String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取姿态统计
     */
    Map<String, Object> getPostureStatistics(String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 调用Python服务进行姿态识别
     */
    Map<String, Object> callPostureRecognitionApi(String animalId, Map<String, Object> sensorData);
}
