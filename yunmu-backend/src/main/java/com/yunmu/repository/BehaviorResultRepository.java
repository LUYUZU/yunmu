package com.yunmu.repository;

import com.yunmu.entity.BehaviorResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface BehaviorResultRepository extends JpaRepository<BehaviorResult, Long> {

    /**
     * 查询指定动物的行为记录
     */
    List<BehaviorResult> findByAnimalIdOrderByStartTimeDesc(String animalId);

    /**
     * 查询指定时间段内的行为记录
     */
    @Query("SELECT b FROM BehaviorResult b WHERE b.animalId = :animalId AND b.startTime >= :startTime AND b.endTime <= :endTime ORDER BY b.startTime")
    List<BehaviorResult> findByAnimalIdAndTimeRange(
            @Param("animalId") String animalId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计各种行为的持续时间
     */
    @Query("SELECT b.behaviorType, SUM(b.durationSeconds) FROM BehaviorResult b WHERE b.animalId = :animalId AND b.startTime >= :startTime GROUP BY b.behaviorType")
    List<Object[]> sumDurationByBehaviorType(
            @Param("animalId") String animalId,
            @Param("startTime") LocalDateTime startTime);

    /**
     * 获取高置信度的行为记录
     */
    @Query("SELECT b FROM BehaviorResult b WHERE b.confidenceScore >= :minConfidence ORDER BY b.confidenceScore DESC")
    Page<BehaviorResult> findHighConfidenceResults(
            @Param("minConfidence") Double minConfidence,
            Pageable pageable);

    /**
     * 查询最近的行为记录
     */
    @Query("SELECT b FROM BehaviorResult b WHERE b.animalId = :animalId ORDER BY b.endTime DESC")
    List<BehaviorResult> findRecentByAnimalId(@Param("animalId") String animalId, Pageable pageable);

    /**
     * 统计行为频率
     */
    @Query("SELECT b.behaviorType, COUNT(b) FROM BehaviorResult b WHERE b.animalId = :animalId AND b.startTime >= :startTime GROUP BY b.behaviorType")
    List<Object[]> countByBehaviorType(
            @Param("animalId") String animalId,
            @Param("startTime") LocalDateTime startTime);

    /**
     * 批量保存行为记录
     */
    @Override
    <S extends BehaviorResult> List<S> saveAll(Iterable<S> entities);
}