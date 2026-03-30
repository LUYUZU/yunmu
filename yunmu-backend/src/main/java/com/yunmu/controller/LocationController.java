package com.yunmu.controller;

import com.yunmu.service.LocationTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/location")
@CrossOrigin(origins = "*")
public class LocationController {

    @Autowired
    private LocationTrackingService locationTrackingService;

    /**
     * 获取动物当前位置
     */
    @GetMapping("/current/{animalId}")
    public ResponseEntity<?> getCurrentLocation(@PathVariable String animalId) {
        try {
            var location = locationTrackingService.getCurrentLocation(animalId);
            if (location != null) {
                return ResponseEntity.ok(location);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取当前位置失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取位置失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取轨迹历史
     */
    @GetMapping("/history/{animalId}")
    public ResponseEntity<?> getTrackHistory(
            @PathVariable String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        try {
            var tracks = locationTrackingService.getTrackHistory(animalId, startTime, endTime);
            return ResponseEntity.ok(tracks);
        } catch (Exception e) {
            log.error("获取轨迹历史失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取轨迹失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 计算活动范围
     */
    @GetMapping("/activity-range/{animalId}")
    public ResponseEntity<?> calculateActivityRange(
            @PathVariable String animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        try {
            var range = locationTrackingService.calculateActivityRange(animalId, startTime, endTime);
            return ResponseEntity.ok(range);
        } catch (Exception e) {
            log.error("计算活动范围失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "计算活动范围失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 更新动物位置
     */
    @PostMapping("/update")
    public ResponseEntity<?> updateLocation(
            @RequestParam String animalId,
            @RequestParam Double latitude,
            @RequestParam Double longitude) {

        try {
            var location = locationTrackingService.updateAnimalLocation(animalId, latitude, longitude);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "位置更新成功",
                    "location", location
            ));
        } catch (Exception e) {
            log.error("更新位置失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "更新位置失败: " + e.getMessage()
            ));
        }
    }
}