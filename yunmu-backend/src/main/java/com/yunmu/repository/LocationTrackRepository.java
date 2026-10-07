package com.yunmu.repository;

import com.yunmu.entity.LocationTrack;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationTrackRepository extends JpaRepository<LocationTrack, Long> {

    /**
     * 查询动物的最新位置（返回 Optional）
     */
    @Query("SELECT l FROM LocationTrack l WHERE l.animalId = :animalId ORDER BY l.timestampEpoch DESC")
    List<LocationTrack> findLatestByAnimalIdList(@Param("animalId") String animalId, Pageable pageable);

    /**
     * 查询动物的最新一条位置（返回 Optional）
     */
    default Optional<LocationTrack> findLatestByAnimalId(String animalId) {
        return findLatestByAnimalIdList(animalId, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst();
    }

    /**
     * 查询动物轨迹
     */
    @Query("SELECT l FROM LocationTrack l WHERE l.animalId = :animalId AND l.timestampEpoch BETWEEN :startTime AND :endTime ORDER BY l.timestampEpoch")
    List<LocationTrack> findTrackByAnimalIdAndTimeRange(
            @Param("animalId") String animalId,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime);

    /**
     * 统计动物位置点数量
     */
    @Query("SELECT COUNT(l) FROM LocationTrack l WHERE l.animalId = :animalId AND l.timestampEpoch >= :startTime")
    Long countByAnimalIdSince(@Param("animalId") String animalId,
                              @Param("startTime") Long startTime);

    /**
     * 查询指定区域内的动物位置
     */
    @Query("SELECT l FROM LocationTrack l WHERE l.latitude BETWEEN :minLat AND :maxLat AND l.longitude BETWEEN :minLng AND :maxLng AND l.timestampEpoch >= :startTime")
    List<LocationTrack> findByAreaAndTime(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            @Param("startTime") Long startTime);

    /**
     * 获取GPS质量统计
     */
    @Query("SELECT l.gpsQuality, COUNT(l) FROM LocationTrack l WHERE l.timestampEpoch >= :startTime GROUP BY l.gpsQuality")
    List<Object[]> countByGpsQuality(@Param("startTime") Long startTime);

    /**
     * 删除无效GPS数据
     */
    @Query("DELETE FROM LocationTrack l WHERE l.isValidGps = false AND l.timestampEpoch < :expireTime")
    void deleteInvalidGpsData(@Param("expireTime") Long expireTime);

    /**
     * 批量保存位置记录
     */
    @Override
    <S extends LocationTrack> List<S> saveAll(Iterable<S> entities);

    /**
     * 计算动物活动范围
     */
    @Query("SELECT MIN(l.latitude) as minLat, MAX(l.latitude) as maxLat, MIN(l.longitude) as minLng, MAX(l.longitude) as maxLng FROM LocationTrack l WHERE l.animalId = :animalId AND l.timestampEpoch BETWEEN :startTime AND :endTime")
    Object[] calculateActivityRange(
            @Param("animalId") String animalId,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime);
}