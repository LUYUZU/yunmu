// PostureResultRepository.java
package com.yunmu.repository;

import com.yunmu.entity.PostureResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface PostureResultRepository extends JpaRepository<PostureResult, Long> {

    /**
     * 根据动物ID查询姿态记录
     */
    List<PostureResult> findByAnimalIdOrderByTimestampDesc(String animalId);

    /**
     * 根据动物ID和时间范围查询
     */
    List<PostureResult> findByAnimalIdAndTimestampBetweenOrderByTimestampDesc(
            String animalId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询最新的姿态记录
     */
    Optional<PostureResult> findTopByAnimalIdOrderByTimestampDesc(String animalId);

    /**
     * 根据动物ID查询最新的一条姿态识别记录
     * 使用@Query原生SQL方案（MySQL）
     *
     * @param animalId 动物ID
     * @return 最新的一条姿态记录，若不存在返回null
     */
    @Query(value = "SELECT * FROM posture_result WHERE animal_id = :animalId ORDER BY create_time DESC LIMIT 1", 
           nativeQuery = true)
    PostureResult findLatestByAnimalId(@Param("animalId") String animalId);

    /**
     * 统计姿态类型分布
     */
    @Query("SELECT p.postureType, COUNT(p) FROM PostureResult p WHERE p.animalId = :animalId AND p.timestamp BETWEEN :startTime AND :endTime GROUP BY p.postureType")
    List<Object[]> countByPostureType(@Param("animalId") String animalId, 
                                      @Param("startTime") LocalDateTime startTime, 
                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 查询站立时长
     */
    @Query("SELECT SUM(p.standingDuration) FROM PostureResult p WHERE p.animalId = :animalId AND p.timestamp BETWEEN :startTime AND :endTime AND p.postureType = 'standing'")
    Integer sumStandingDuration(@Param("animalId") String animalId, 
                                @Param("startTime") LocalDateTime startTime, 
                                @Param("endTime") LocalDateTime endTime);

    /**
     * 查询躺卧时长
     */
    @Query("SELECT SUM(p.lyingDuration) FROM PostureResult p WHERE p.animalId = :animalId AND p.timestamp BETWEEN :startTime AND :endTime AND p.postureType = 'lying'")
    Integer sumLyingDuration(@Param("animalId") String animalId, 
                             @Param("startTime") LocalDateTime startTime, 
                             @Param("endTime") LocalDateTime endTime);
}
