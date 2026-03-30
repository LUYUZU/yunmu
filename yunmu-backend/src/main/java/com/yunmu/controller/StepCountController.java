// StepCountController.java
package com.yunmu.controller;

import com.yunmu.entity.StepCount;
import com.yunmu.service.StepCountService;
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
@RequestMapping("/api/steps")
@CrossOrigin(origins = "*")
public class StepCountController {

    @Autowired
    private StepCountService stepCountService;

    /**
     * 统计步数
     */
    @PostMapping("/count")
    public ResponseEntity<?> countSteps(
            @RequestParam String animalId,
            @RequestParam Integer stepCount,
            @RequestParam(required = false) Double accelMagnitude) {

        try {
            StepCount result = stepCountService.countSteps(animalId, stepCount, accelMagnitude);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", result
            ));
        } catch (Exception e) {
            log.error("步数统计失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "步数统计失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量统计步数
     */
    @PostMapping("/batch")
    public ResponseEntity<?> batchCountSteps(@RequestBody List<Map<String, Object>> sensorDataList) {
        try {
            List<StepCount> results = stepCountService.batchCountSteps(sensorDataList);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", results.size(),
                    "data", results
            ));
        } catch (Exception e) {
            log.error("批量步数统计失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "批量步数统计失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取当前步数
     */
    @GetMapping("/current/{animalId}")
    public ResponseEntity<?> getCurrentSteps(@PathVariable String animalId) {
        try {
            StepCount result = stepCountService.getCurrentSteps(animalId);
            if (result != null) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取当前步数失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取当前步数失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取步数历史
     */
    @GetMapping("/history/{animalId}")
    public ResponseEntity<?> getStepHistory(
            @PathVariable String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        try {
            List<StepCount> results = stepCountService.getStepHistory(animalId, startTime, endTime);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("获取步数历史失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取步数历史失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取步数统计
     */
    @GetMapping("/statistics/{animalId}")
    public ResponseEntity<?> getStepStatistics(
            @PathVariable String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        try {
            Map<String, Object> statistics = stepCountService.getStepStatistics(animalId, startTime, endTime);
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取步数统计失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取今日步数
     */
    @GetMapping("/today/{animalId}")
    public ResponseEntity<?> getTodaySteps(@PathVariable String animalId) {
        try {
            Integer todaySteps = stepCountService.getTodaySteps(animalId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "animalId", animalId,
                    "todaySteps", todaySteps != null ? todaySteps : 0
            ));
        } catch (Exception e) {
            log.error("获取今日步数失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取今日步数失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取所有动物今日步数
     */
    @GetMapping("/today/all")
    public ResponseEntity<?> getAllAnimalTodaySteps() {
        try {
            Map<String, Integer> stepsMap = stepCountService.getAllAnimalTodaySteps();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", stepsMap
            ));
        } catch (Exception e) {
            log.error("获取所有动物今日步数失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取所有动物今日步数失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 调用Python服务统计步数
     */
    @PostMapping("/predict")
    public ResponseEntity<?> predictSteps(@RequestBody Map<String, Object> sensorData) {
        try {
            String animalId = (String) sensorData.get("animalId");
            Map<String, Object> result = stepCountService.callStepCountApi(animalId, sensorData);
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
