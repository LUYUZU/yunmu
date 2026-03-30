// PostureController.java
package com.yunmu.controller;

import com.yunmu.entity.PostureResult;
import com.yunmu.service.PostureRecognitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/posture")
@CrossOrigin(origins = "*")
public class PostureController {

    @Autowired
    private PostureRecognitionService postureRecognitionService;

    /**
     * 识别姿态
     */
    @PostMapping("/recognize")
    public ResponseEntity<?> recognizePosture(
            @RequestParam String animalId,
            @RequestParam Double accelX,
            @RequestParam Double accelY,
            @RequestParam Double accelZ,
            @RequestParam(required = false) Double gyroX,
            @RequestParam(required = false) Double gyroY,
            @RequestParam(required = false) Double gyroZ) {

        try {
            PostureResult result = postureRecognitionService.recognizePosture(
                    animalId, accelX, accelY, accelZ, gyroX, gyroY, gyroZ);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result
            ));
        } catch (Exception e) {
            log.error("姿态识别失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "姿态识别失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量识别姿态
     */
    @PostMapping("/batch")
    public ResponseEntity<?> batchRecognizePosture(@RequestBody List<Map<String, Object>> sensorDataList) {
        try {
            List<PostureResult> results = postureRecognitionService.batchRecognizePosture(sensorDataList);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", results.size(),
                    "data", results
            ));
        } catch (Exception e) {
            log.error("批量姿态识别失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "批量姿态识别失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取当前姿态
     */
    @GetMapping("/current/{animalId}")
    public ResponseEntity<?> getCurrentPosture(@PathVariable String animalId) {
        try {
            PostureResult result = postureRecognitionService.getCurrentPosture(animalId);
            if (result != null) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取当前姿态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取当前姿态失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取姿态历史
     */
    @GetMapping("/history/{animalId}")
    public ResponseEntity<?> getPostureHistory(
            @PathVariable String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        try {
            List<PostureResult> results = postureRecognitionService.getPostureHistory(animalId, startTime, endTime);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("获取姿态历史失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取姿态历史失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取姿态统计
     */
    @GetMapping("/statistics/{animalId}")
    public ResponseEntity<?> getPostureStatistics(
            @PathVariable String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        try {
            Map<String, Object> statistics = postureRecognitionService.getPostureStatistics(animalId, startTime, endTime);
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("获取姿态统计失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取姿态统计失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 调用Python服务识别姿态
     */
    @PostMapping("/predict")
    public ResponseEntity<?> predictPosture(@RequestBody Map<String, Object> sensorData) {
        try {
            String animalId = (String) sensorData.get("animalId");
            Map<String, Object> result = postureRecognitionService.callPostureRecognitionApi(animalId, sensorData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("调用Python服务失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "调用Python服务失败: " + e.getMessage()
            ));
        }
    }
}
