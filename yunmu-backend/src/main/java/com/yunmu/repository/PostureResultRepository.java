// PostureResultRepository.java
package com.yunmu.repository;

import com.yunmu.entity.PostureResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface PostureResultRepository extends JpaRepository<PostureResult, Long> {

    /**
     * 根据动物ID查询姿态记录
     */
    List<PostureResult> findByAnimalIdOrderByTimestampEpochDesc(String animalId);

    /**
     * 根据动物ID和时间范围查询
     */
    List<PostureResult> findByAnimalIdAndTimestampEpochBetweenOrderByTimestampEpochDesc(
            String animalId, Long startEpoch, Long endEpoch);

    /**
     * 查询最新的姿态记录
     */
    Optional<PostureResult> findTopByAnimalIdOrderByTimestampEpochDesc(String animalId);

    /**
     * 批量查询多只动物各自最新的一条姿态记录（用于实时监测页一次性取全，避免 N+1 查询）
     */
    @Query("SELECT p FROM PostureResult p WHERE p.animalId IN :animalIds AND p.timestampEpoch = (SELECT MAX(p2.timestampEpoch) FROM PostureResult p2 WHERE p2.animalId = p.animalId)")
    List<PostureResult> findLatestByAnimalIds(@Param("animalIds") List<String> animalIds);

    /**
     * 根据动物ID查询最新的一条姿态识别记录。
     *
     * 注意：目标数据库为 SQL Server，因此采用 TOP 1 语法（而非 MySQL 的 LIMIT 1），
     * 且表名使用实体 @Table 声明的 posture_results（此前误写为 posture_result）。
     * 如无原生 SQL 需求，优先使用 findTopByAnimalIdOrderByTimestampEpochDesc。
     *
     * @param animalId 动物ID
     * @return 最新的一条姿态记录，若不存在返回null
     */
    @Query(value = "SELECT TOP 1 * FROM posture_results WHERE animal_id = :animalId ORDER BY create_time DESC",
           nativeQuery = true)
    PostureResult findLatestByAnimalId(@Param("animalId") String animalId);

    /**
     * 统计姿态类型分布
     */
    @Query("SELECT p.postureType, COUNT(p) FROM PostureResult p WHERE p.animalId = :animalId AND p.timestampEpoch BETWEEN :startEpoch AND :endEpoch GROUP BY p.postureType")
    List<Object[]> countByPostureType(@Param("animalId") String animalId, 
                                      @Param("startEpoch") Long startEpoch, 
                                      @Param("endEpoch") Long endEpoch);

    /**
     * 查询站立时长
     */
    @Query("SELECT SUM(p.standingDuration) FROM PostureResult p WHERE p.animalId = :animalId AND p.timestampEpoch BETWEEN :startEpoch AND :endEpoch AND p.postureType = 'standing'")
    Integer sumStandingDuration(@Param("animalId") String animalId, 
                                @Param("startEpoch") Long startEpoch, 
                                @Param("endEpoch") Long endEpoch);

    /**
     * 查询躺卧时长
     */
    @Query("SELECT SUM(p.lyingDuration) FROM PostureResult p WHERE p.animalId = :animalId AND p.timestampEpoch BETWEEN :startEpoch AND :endEpoch AND p.postureType = 'lying'")
    Integer sumLyingDuration(@Param("animalId") String animalId, 
                             @Param("startEpoch") Long startEpoch, 
                             @Param("endEpoch") Long endEpoch);
}
