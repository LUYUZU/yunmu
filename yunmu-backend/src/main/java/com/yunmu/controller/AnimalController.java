package com.yunmu.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/animals")
@CrossOrigin(origins = "*")
public class AnimalController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取所有动物列表
     */
    @GetMapping
    public List<Map<String, Object>> getAllAnimals() {
        log.info("获取所有动物列表");

        try {
            // 直接使用 SQL 查询数据库
            String sql = "SELECT animal_id, device_id, name, type, breed, weight, status, created_at FROM animals";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

            if (results.isEmpty()) {
                log.info("数据库无动物数据，返回模拟数据");
                return generateMockAnimals();
            }

            // 转换为前端期望的格式
            List<Map<String, Object>> animals = new ArrayList<>();
            for (Map<String, Object> row : results) {
                Map<String, Object> animal = new HashMap<>();
                animal.put("id", row.get("animal_id"));
                animal.put("type", row.get("type"));
                animal.put("breed", row.get("breed"));
                animal.put("weight", row.get("weight"));
                animal.put("status", row.get("status"));
                animal.put("name", row.get("name"));
                animal.put("deviceId", row.get("device_id"));

                // 添加模拟的实时数据（实际应从其他表获取）
                animal.put("temperature", String.format("%.1f", 38 + Math.random() * 1.5));
                animal.put("heartRate", 60 + (int)(Math.random() * 30));
                animal.put("steps", (int)(Math.random() * 5000));
                animal.put("behavior", getRandomBehavior());
                animal.put("posture", getRandomPosture());

                animals.add(animal);
            }

            log.info("从数据库获取到 {} 只动物", animals.size());
            return animals;

        } catch (Exception e) {
            log.error("获取动物列表失败: {}", e.getMessage(), e);
            return generateMockAnimals();
        }
    }

    /**
     * 获取单个动物信息
     */
    @GetMapping("/{animalId}")
    public Map<String, Object> getAnimal(@PathVariable String animalId) {
        log.info("获取动物信息: {}", animalId);

        try {
            String sql = "SELECT animal_id, device_id, name, type, breed, weight, status FROM animals WHERE animal_id = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, animalId);

            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                Map<String, Object> animal = new HashMap<>();
                animal.put("id", row.get("animal_id"));
                animal.put("type", row.get("type"));
                animal.put("breed", row.get("breed"));
                animal.put("weight", row.get("weight"));
                animal.put("status", row.get("status"));
                animal.put("name", row.get("name"));
                animal.put("deviceId", row.get("device_id"));
                animal.put("temperature", String.format("%.1f", 38 + Math.random() * 1.5));
                animal.put("heartRate", 60 + (int)(Math.random() * 30));
                return animal;
            }

            return null;
        } catch (Exception e) {
            log.error("获取动物信息失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getRandomBehavior() {
        String[] behaviors = {"采食", "反刍", "站立", "行走", "躺卧"};
        return behaviors[(int)(Math.random() * behaviors.length)];
    }

    private String getRandomPosture() {
        String[] postures = {"standing", "lying", "feeding", "walking"};
        return postures[(int)(Math.random() * postures.length)];
    }

    private List<Map<String, Object>> generateMockAnimals() {
        List<Map<String, Object>> animals = new ArrayList<>();
        String[] types = {"cow", "sheep"};
        String[] breeds = {"荷斯坦", "西门塔尔", "小尾寒羊", "藏绵羊"};

        for (int i = 1; i <= 8; i++) {
            Map<String, Object> animal = new HashMap<>();
            animal.put("id", String.format("NO.%03d", i));
            animal.put("type", types[i % 2]);
            animal.put("breed", breeds[i % 4]);
            animal.put("weight", 200 + (int)(Math.random() * 300));
            animal.put("status", Math.random() > 0.85 ? "alert" : "normal");
            animal.put("temperature", String.format("%.1f", 38 + Math.random() * 1.5));
            animal.put("heartRate", 60 + (int)(Math.random() * 30));
            animal.put("steps", (int)(Math.random() * 5000));
            animal.put("behavior", getRandomBehavior());
            animal.put("posture", getRandomPosture());
            animals.add(animal);
        }
        return animals;
    }
}