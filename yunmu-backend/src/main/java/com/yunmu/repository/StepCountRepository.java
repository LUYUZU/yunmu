// StepCountRepository.java
package com.yunmu.repository;

import com.yunmu.entity.StepCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StepCountRepository extends JpaRepository<StepCount, Long> {

    /**
     * 根据动物ID查询步数记录
     */
    List<StepCount> findByAnimalIdOrderByTimestampDesc(String animalId);

    /**
     * 根据动物ID和时间范围查询（epoch 毫秒）
     */
    List<StepCount> findByAnimalIdAndTimestampBetweenOrderByTimestampDesc(
            String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询最新的步数记录（原生 SQL）
     */
    @Query(value = "SELECT TOP 1 * FROM step_counts WHERE animal_id = :animalId ORDER BY timestamp DESC",
           nativeQuery = true)
    Optional<StepCount> findLatestByAnimalId(@Param("animalId") String animalId);

    /**
     * 查询今日步数（epoch 毫秒对比，Long 类型）
     */
    @Query("SELECT COALESCE(SUM(s.stepCount), 0) FROM StepCount s " +
           "WHERE s.animalId = :animalId AND s.timestamp >= :startOfDay")
    Integer getTodaySteps(@Param("animalId") String animalId,
                          @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * 查询时间范围内的总步数（Long epoch）
     */
    @Query("SELECT COALESCE(SUM(s.stepCount), 0) FROM StepCount s " +
           "WHERE s.animalId = :animalId AND s.timestamp BETWEEN :startTime AND :endTime")
    Integer sumSteps(@Param("animalId") String animalId,
                     @Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);

    /**
     * 查询时间范围内的总行走距离（Long epoch）
     */
    @Query("SELECT COALESCE(SUM(s.walkingDistance), 0.0) FROM StepCount s " +
           "WHERE s.animalId = :animalId AND s.timestamp BETWEEN :startTime AND :endTime")
    Double sumWalkingDistance(@Param("animalId") String animalId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    /**
     * 查询时间范围内的活跃时长（Long epoch）
     */
    @Query("SELECT COALESCE(SUM(s.activeDuration), 0) FROM StepCount s " +
           "WHERE s.animalId = :animalId AND s.timestamp BETWEEN :startTime AND :endTime")
    Integer sumActiveDuration(@Param("animalId") String animalId,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

    /**
     * 查询步数异常记录
     */
    List<StepCount> findByAnimalIdAndIsAnomalyTrue(String animalId);

    /**
     * 获取所有动物今日总步数（Long epoch）
     */
    @Query("SELECT s.animalId, SUM(s.stepCount) FROM StepCount s " +
           "WHERE s.timestamp >= :startOfDay GROUP BY s.animalId")
    List<Object[]> getAllAnimalTodaySteps(@Param("startOfDay") LocalDateTime startOfDay);
}
