package com.yunmu.repository;

import com.yunmu.entity.HealthStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthStatusRepository extends JpaRepository<HealthStatus, Long> {

    /**
     * 查询动物的最新健康状态
     */
    @Query("SELECT h FROM HealthStatus h WHERE h.animalId = :animalId ORDER BY h.timestamp DESC")
    Optional<HealthStatus> findLatestByAnimalId(@Param("animalId") String animalId);

    /**
     * 查询指定时间段内的健康状态记录
     */
    @Query("SELECT h FROM HealthStatus h WHERE h.animalId = :animalId AND h.timestamp BETWEEN :startTime AND :endTime ORDER BY h.timestamp")
    List<HealthStatus> findByAnimalIdAndTimeRange(
            @Param("animalId") String animalId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询活跃的预警
     */
    @Query("SELECT h FROM HealthStatus h WHERE h.isResolved = false AND h.alertLevel IN ('WARNING', 'CRITICAL') ORDER BY h.timestamp DESC")
    Page<HealthStatus> findActiveAlerts(Pageable pageable);

    /**
     * 统计不同健康状态的数量
     */
    @Query("SELECT h.overallStatus, COUNT(h) FROM HealthStatus h WHERE h.timestamp >= :startTime GROUP BY h.overallStatus")
    List<Object[]> countByHealthStatus(@Param("startTime") LocalDateTime startTime);

    /**
     * 查询预警历史
     */
    @Query("SELECT h FROM HealthStatus h WHERE h.alertLevel IS NOT NULL ORDER BY h.timestamp DESC")
    Page<HealthStatus> findAlertHistory(Pageable pageable);

    /**
     * 根据预警类型查询
     */
    List<HealthStatus> findByAlertTypeAndIsResolved(String alertType, Boolean isResolved);

    /**
     * 批量更新预警解决状态
     */
    @Query("UPDATE HealthStatus h SET h.isResolved = :isResolved, h.resolvedTime = :resolvedTime, h.resolvedBy = :resolvedBy WHERE h.id IN :ids")
    void updateResolveStatus(@Param("ids") List<Long> ids,
                             @Param("isResolved") Boolean isResolved,
                             @Param("resolvedTime") LocalDateTime resolvedTime,
                             @Param("resolvedBy") String resolvedBy);
}