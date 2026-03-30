// HealthController.java
package com.yunmu.controller;

import com.yunmu.dto.HealthAssessmentDTO;
import com.yunmu.service.HealthMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    @Autowired
    private HealthMonitoringService healthMonitoringService;

    /**
     * 获取健康评估
     */
    @GetMapping("/assessment/{animalId}")
    public ResponseEntity<HealthAssessmentDTO> getHealthAssessment(@PathVariable String animalId) {
        try {
            HealthAssessmentDTO assessment = healthMonitoringService.assessAnimalHealth(animalId);
            return ResponseEntity.ok(assessment);
        } catch (Exception e) {
            log.error("获取健康评估失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取健康趋势
     */
    @GetMapping("/trend/{animalId}")
    public ResponseEntity<List<HealthAssessmentDTO>> getHealthTrend(
            @PathVariable String animalId,
            @RequestParam(defaultValue = "7") int days) {

        try {
            List<HealthAssessmentDTO> trend = healthMonitoringService.getHealthTrend(animalId, days);
            return ResponseEntity.ok(trend);
        } catch (Exception e) {
            log.error("获取健康趋势失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取活跃预警
     */
    @GetMapping("/alerts/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveAlerts() {
        try {
            // 这里需要实现获取活跃预警的逻辑
            return ResponseEntity.ok(List.of());
        } catch (Exception e) {
            log.error("获取活跃预警失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
