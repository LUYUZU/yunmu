package com.yunmu.repository;

import com.yunmu.entity.HealthStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
     * 查询动物的最新健康状态（列表 + 分页取首条）。
     *
     * <p><b>不要</b>把它写成返回 {@code Optional<HealthStatus>} 的单条查询：该 JPQL 没有 LIMIT，
     * Hibernate 对单条返回会走 {@code getSingleResult()}，一旦同一动物存在多条记录就抛
     * {@code NonUniqueResultException}，而 {@code monitorRealTimeHealth} 的调用方是异步线程，
     * 异常被吞掉后表现为「健康监控静默失效」——`health_status` 停止更新、告警不再产生。
     *
     * <p>同一动物出现多行的成因：实时监控多线程并发处理同一动物时，
     * 两条线程可能同时查不到记录、各自新建一行。
     *
     * <p>这里改用 {@code List + Pageable} 的写法（与 {@code LocationTrackRepository} 保持一致）。
     */
    @Query("SELECT h FROM HealthStatus h WHERE h.animalId = :animalId ORDER BY h.timestamp DESC")
    List<HealthStatus> findLatestByAnimalIdList(@Param("animalId") String animalId, Pageable pageable);

    /** 查询动物的最新健康状态 */
    default Optional<HealthStatus> findLatestByAnimalId(String animalId) {
        return findLatestByAnimalIdList(animalId, PageRequest.of(0, 1)).stream().findFirst();
    }

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