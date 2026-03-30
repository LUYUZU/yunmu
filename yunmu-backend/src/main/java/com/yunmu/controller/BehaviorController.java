// BehaviorController.java
package com.yunmu.controller;

import com.yunmu.dto.BehaviorRequestDTO;
import com.yunmu.dto.BehaviorResultDTO;
import com.yunmu.service.BehaviorAnalysisService;
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
@RequestMapping("/api/behavior")
@CrossOrigin(origins = "*")
public class BehaviorController {

    @Autowired
    private BehaviorAnalysisService behaviorAnalysisService;

    /**
     * 分析反刍行为
     */
    @PostMapping("/rumination")
    public ResponseEntity<BehaviorResultDTO> analyzeRumination(
            @RequestParam String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        try {
            BehaviorResultDTO result = behaviorAnalysisService.analyzeRumination(animalId, startTime, endTime);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("分析反刍行为失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 分析采食行为
     */
    @PostMapping("/feeding")
    public ResponseEntity<BehaviorResultDTO> analyzeFeeding(
            @RequestParam String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        try {
            BehaviorResultDTO result = behaviorAnalysisService.analyzeFeeding(animalId, startTime, endTime);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("分析采食行为失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 批量行为分析
     */
    @PostMapping("/batch")
    public ResponseEntity<List<BehaviorResultDTO>> batchAnalysis(@RequestBody List<BehaviorRequestDTO> requests) {
        try {
            List<BehaviorResultDTO> results = behaviorAnalysisService.batchBehaviorAnalysis(requests);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("批量行为分析失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取行为统计数据
     */
    @GetMapping("/statistics/{animalId}")
    public ResponseEntity<Map<String, Object>> getBehaviorStatistics(
            @PathVariable String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        try {
            Map<String, Object> statistics = behaviorAnalysisService.getBehaviorStatistics(animalId, startTime, endTime);
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("获取行为统计数据失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
