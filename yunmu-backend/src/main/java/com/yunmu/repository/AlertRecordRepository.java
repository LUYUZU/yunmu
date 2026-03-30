package com.yunmu.repository;

import com.yunmu.entity.AlertRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {

    /**
     * 查询活跃预警
     */
    List<AlertRecord> findByAlertStatusOrderByAlertTimeDesc(String alertStatus);

    /**
     * 分页查询预警记录
     */
    Page<AlertRecord> findByAlertStatus(String alertStatus, Pageable pageable);

    /**
     * 查询指定动物的预警记录
     */
    @Query("SELECT a FROM AlertRecord a WHERE a.animalId = :animalId ORDER BY a.alertTime DESC")
    List<AlertRecord> findByAnimalId(@Param("animalId") String animalId, Pageable pageable);

    /**
     * 统计各类预警数量
     */
    @Query("SELECT a.alertType, COUNT(a) FROM AlertRecord a WHERE a.alertTime >= :startTime GROUP BY a.alertType")
    List<Object[]> countByAlertType(@Param("startTime") LocalDateTime startTime);

    /**
     * 查询未通知的预警
     */
    @Query("SELECT a FROM AlertRecord a WHERE a.notificationSent = false AND a.alertLevel IN ('WARNING', 'CRITICAL') ORDER BY a.alertTime")
    List<AlertRecord> findUnnotifiedAlerts();

    /**
     * 批量更新通知状态
     */
    @Query("UPDATE AlertRecord a SET a.notificationSent = :sent WHERE a.id IN :ids")
    void updateNotificationStatus(@Param("ids") List<Long> ids, @Param("sent") Boolean sent);

    /**
     * 查询指定时间段内的预警
     */
    @Query("SELECT a FROM AlertRecord a WHERE a.alertTime BETWEEN :startTime AND :endTime ORDER BY a.alertTime DESC")
    Page<AlertRecord> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * 统计预警级别分布
     */
    @Query("SELECT a.alertLevel, COUNT(a) FROM AlertRecord a WHERE a.alertTime >= :startTime GROUP BY a.alertLevel")
    List<Object[]> countByAlertLevel(@Param("startTime") LocalDateTime startTime);

    /**
     * 根据解决状态查询
     */
    Page<AlertRecord> findByAlertStatusAndAnimalId(String alertStatus, String animalId, Pageable pageable);
}