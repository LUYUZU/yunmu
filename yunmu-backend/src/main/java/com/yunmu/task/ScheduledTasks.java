// ScheduledTasks.java
package com.yunmu.task;

import com.yunmu.dto.HealthAssessmentDTO;
import com.yunmu.entity.AlertRecord;
import com.yunmu.entity.Animal;
import com.yunmu.repository.AlertRecordRepository;
import com.yunmu.repository.AnimalRepository;
import com.yunmu.service.HealthMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务：定期健康巡检、每日统计、预警通知。
 *
 * 使用 Spring @Scheduled 驱动（无需 Quartz），因此 pom 中已移除未使用的 quartz starter。
 */
@Slf4j
@Component
public class ScheduledTasks {

    @Autowired
    private HealthMonitoringService healthMonitoringService;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private AlertRecordRepository alertRecordRepository;

    /**
     * 每小时执行一次健康巡检：遍历全部动物并生成健康评估（异常会自动写入预警）。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void hourlyHealthCheck() {
        log.info("开始执行每小时健康检查");
        long start = System.currentTimeMillis();
        int total = 0;
        int abnormal = 0;
        try {
            List<Animal> animals = animalRepository.findAll();
            total = animals.size();
            List<String> abnormalAnimals = new ArrayList<>();

            for (Animal animal : animals) {
                if (animal == null || animal.getAnimalId() == null) {
                    continue;
                }
                try {
                    HealthAssessmentDTO assessment =
                            healthMonitoringService.assessAnimalHealth(animal.getAnimalId());
                    if (assessment != null && Boolean.TRUE.equals(assessment.getHasAlert())) {
                        abnormal++;
                        abnormalAnimals.add(animal.getAnimalId());
                    }
                } catch (Exception e) {
                    // 单只动物评估失败不影响整体巡检
                    log.warn("健康评估失败 - animalId={}, error={}", animal.getAnimalId(), e.getMessage());
                }
            }

            log.info("每小时健康检查完成 - 动物总数={}, 异常数={}, 耗时={}ms, 异常动物={}",
                    total, abnormal, System.currentTimeMillis() - start, abnormalAnimals);

        } catch (Exception e) {
            log.error("每小时健康检查失败 - 已检查={}", total, e);
        }
    }

    /**
     * 每天凌晨执行数据统计：汇总动物规模与近 24 小时预警分布。
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void dailyStatistics() {
        log.info("开始执行每日数据统计");
        try {
            long animalCount = animalRepository.count();
            List<Object[]> byLevel = alertRecordRepository.countByAlertLevel(LocalDateTime.now().minusDays(1));
            List<Object[]> byType = alertRecordRepository.countByAlertType(LocalDateTime.now().minusDays(1));

            log.info("每日数据统计 - 动物总数={}, 近24h预警按级别={}, 按类型={}",
                    animalCount, formatCounts(byLevel), formatCounts(byType));

        } catch (Exception e) {
            log.error("每日数据统计失败", e);
        }
    }

    /**
     * 每 5 分钟检查并派发未通知的 WARNING/CRITICAL 预警。
     */
    @Scheduled(fixedRate = 300000)
    public void alertMonitoring() {
        log.debug("执行预警监控");
        try {
            List<AlertRecord> pending = alertRecordRepository.findUnnotifiedAlerts();
            if (pending.isEmpty()) {
                return;
            }

            List<Long> ids = new ArrayList<>();
            for (AlertRecord alert : pending) {
                log.warn("待通知预警 - id={}, animalId={}, level={}, message={}",
                        alert.getId(), alert.getAnimalId(), alert.getAlertLevel(), alert.getAlertMessage());
                if (alert.getId() != null) {
                    ids.add(alert.getId());
                }
            }

            // 标记为已通知，避免重复派发（实际短信/邮件/App 推送接入点）
            if (!ids.isEmpty()) {
                alertRecordRepository.updateNotificationStatus(ids, true);
                log.info("预警监控完成 - 已派发 {} 条预警", ids.size());
            }

        } catch (Exception e) {
            log.error("预警监控失败", e);
        }
    }

    private String formatCounts(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(row[0]).append("=").append(row[1]);
        }
        return sb.append("}").toString();
    }
}
