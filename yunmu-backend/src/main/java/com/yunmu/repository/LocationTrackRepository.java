package com.yunmu.repository;

import com.yunmu.entity.LocationTrack;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LocationTrackRepository extends JpaRepository<LocationTrack, Long> {

    /**
     * 查询动物的最新位置
     */
    @Query("SELECT l FROM LocationTrack l WHERE l.animalId = :animalId ORDER BY l.timestamp DESC")
    List<LocationTrack> findLatestByAnimalId(@Param("animalId") String animalId, Pageable pageable);

    /**
     * 查询动物轨迹
     */
    @Query("SELECT l FROM LocationTrack l WHERE l.animalId = :animalId AND l.timestamp BETWEEN :startTime AND :endTime ORDER BY l.timestamp")
    List<LocationTrack> findTrackByAnimalIdAndTimeRange(
            @Param("animalId") String animalId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计动物位置点数量
     */
    @Query("SELECT COUNT(l) FROM LocationTrack l WHERE l.animalId = :animalId AND l.timestamp >= :startTime")
    Long countByAnimalIdSince(@Param("animalId") String animalId,
                              @Param("startTime") LocalDateTime startTime);

    /**
     * 查询指定区域内的动物位置
     */
    @Query("SELECT l FROM LocationTrack l WHERE l.latitude BETWEEN :minLat AND :maxLat AND l.longitude BETWEEN :minLng AND :maxLng AND l.timestamp >= :startTime")
    List<LocationTrack> findByAreaAndTime(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            @Param("startTime") LocalDateTime startTime);

    /**
     * 获取GPS质量统计
     */
    @Query("SELECT l.gpsQuality, COUNT(l) FROM LocationTrack l WHERE l.timestamp >= :startTime GROUP BY l.gpsQuality")
    List<Object[]> countByGpsQuality(@Param("startTime") LocalDateTime startTime);

    /**
     * 删除无效GPS数据
     */
    @Query("DELETE FROM LocationTrack l WHERE l.isValidGps = false AND l.timestamp < :expireTime")
    void deleteInvalidGpsData(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 批量保存位置记录
     */
    @Override
    <S extends LocationTrack> List<S> saveAll(Iterable<S> entities);

    /**
     * 计算动物活动范围
     */
    @Query("SELECT MIN(l.latitude) as minLat, MAX(l.latitude) as maxLat, MIN(l.longitude) as minLng, MAX(l.longitude) as maxLng FROM LocationTrack l WHERE l.animalId = :animalId AND l.timestamp BETWEEN :startTime AND :endTime")
    Object[] calculateActivityRange(
            @Param("animalId") String animalId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}