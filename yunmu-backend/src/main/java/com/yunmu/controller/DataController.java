package com.yunmu.controller;

import com.yunmu.dto.SensorDataDTO;
import com.yunmu.entity.SensorData;
import com.yunmu.service.DataCollectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class DataController {

    @Autowired
    private DataCollectionService dataCollectionService;

    /**
     * 接收传感器数据（HTTP方式）
     */
    @PostMapping("/sensor")
    public ResponseEntity<Map<String, Object>> receiveSensorData(@RequestBody SensorDataDTO sensorDataDTO) {
        try {
            log.info("收到传感器数据: {}", sensorDataDTO.getAnimalId());

            // 处理数据
            SensorData processedData = dataCollectionService.processSensorData(sensorDataDTO);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "数据接收成功");
            response.put("dataId", processedData.getId());
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("处理传感器数据失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "数据处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量接收数据
     */
    @PostMapping("/sensor/batch")
    public ResponseEntity<Map<String, Object>> receiveBatchData(@RequestBody List<SensorDataDTO> dataList) {
        try {
            log.info("收到批量数据，数量: {}", dataList.size());

            dataCollectionService.batchProcessData(dataList);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "批量数据处理成功",
                    "processedCount", dataList.size(),
                    "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("批量处理数据失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "批量处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 心跳检测
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "yunmu-data-service",
                "timestamp", System.currentTimeMillis(),
                "version", "1.0.0"
        ));
    }
}