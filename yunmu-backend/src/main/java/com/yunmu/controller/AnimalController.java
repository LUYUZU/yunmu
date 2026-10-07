package com.yunmu.controller;

import com.yunmu.entity.Animal;
import com.yunmu.repository.AnimalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动物档案查询接口
 *
 * 两点约定：
 *
 * 1. 统一走 AnimalRepository（JPA），不再使用裸 SQL。
 *    此前 SQL 写的是 name / type / device_id / status / created_at，
 *    而实体 Animal 实际映射的是 animal_id / animal_type / device_code / create_time，
 *    animals 表并没有 name / status / created_at 这几列，该查询会抛异常走兜底分支。
 *
 * 2. 本系统不对动物做个别命名，个体只用编号（即 animal_id）标识，
 *    因此接口不返回 name 字段，前端也不再渲染任何动物名称。
 *
 * 本接口只返回静态档案。实时指标（体温 / 心率 / 步数 / 行为 / 姿态 / 健康分）
 * 请查 /api/realtime/summary（全量）或 /api/realtime/latest/{animalId}（单只）。
 */
@Slf4j
@RestController
@RequestMapping("/api/animals")
@CrossOrigin(origins = "*")
public class AnimalController {

    @Autowired
    private AnimalRepository animalRepository;

    /**
     * 获取所有动物档案
     */
    @GetMapping
    public List<Map<String, Object>> getAllAnimals() {
        log.info("获取所有动物列表");

        List<Map<String, Object>> animals = new ArrayList<>();
        try {
            for (Animal animal : animalRepository.findAll()) {
                animals.add(toArchiveMap(animal));
            }
            log.info("从数据库获取到 {} 只动物", animals.size());
        } catch (Exception e) {
            log.error("获取动物列表失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
        return animals;
    }

    /**
     * 获取单个动物档案
     */
    @GetMapping("/{animalId}")
    public Map<String, Object> getAnimal(@PathVariable String animalId) {
        log.info("获取动物信息: {}", animalId);

        try {
            return animalRepository.findById(animalId)
                    .map(this::toArchiveMap)
                    .orElse(null);
        } catch (Exception e) {
            log.error("获取动物信息失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 档案字段映射——只输出 animals 表真实存在的列。
     *
     * 注意：这里既不返回 animal 的 name（项目不对动物命名），
     * 也不返回 status，也不生成任何体温 / 心率 / 步数 / 行为 / 姿态等实时值——
     * 这些必须来自真实链路（MQTT → Python 推理 → 回调入库），
     * 早期版本曾用 Math.random() 在此处生成，已移除。
     */
    private Map<String, Object> toArchiveMap(Animal animal) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", animal.getAnimalId());
        m.put("type", animal.getAnimalType());
        m.put("breed", animal.getBreed());
        m.put("age", animal.getAge());
        m.put("weight", animal.getWeight());
        m.put("deviceId", animal.getDeviceCode());
        m.put("registrationDate", animal.getRegistrationDate() != null
                ? animal.getRegistrationDate().toString() : null);
        m.put("ownerId", animal.getOwnerId());
        m.put("pastureId", animal.getPastureId());
        return m;
    }
}
