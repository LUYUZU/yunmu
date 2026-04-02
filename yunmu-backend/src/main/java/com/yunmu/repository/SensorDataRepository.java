package com.yunmu.repository;

import com.yunmu.entity.SensorData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    /**
     * 根据动物ID查询最新数据
     */
    @Query("SELECT s FROM SensorData s WHERE s.animalId = :animalId ORDER BY s.timestampEpoch DESC")
    List<SensorData> findLatestByAnimalId(@Param("animalId") String animalId, Pageable pageable);

    /**
     * 查询指定时间范围内的传感器数据
     * 注意：timestamp 在数据库中是 bigint（Unix 秒）
     */
    @Query("SELECT s FROM SensorData s WHERE s.animalId = :animalId AND s.timestampEpoch BETWEEN :startEpoch AND :endEpoch ORDER BY s.timestampEpoch")
    List<SensorData> findByAnimalIdAndTimeRange(
            @Param("animalId") String animalId,
            @Param("startEpoch") Long startEpoch,
            @Param("endEpoch") Long endEpoch);

    /**
     * 批量查询多个动物的最新数据
     */
    @Query("SELECT s FROM SensorData s WHERE s.animalId IN :animalIds AND s.timestampEpoch = (SELECT MAX(s2.timestampEpoch) FROM SensorData s2 WHERE s2.animalId = s.animalId)")
    List<SensorData> findLatestByAnimalIds(@Param("animalIds") List<String> animalIds);

    /**
     * 统计指定时间段内的数据量
     */
    @Query("SELECT COUNT(s) FROM SensorData s WHERE s.timestampEpoch BETWEEN :startEpoch AND :endEpoch")
    Long countByTimeRange(@Param("startEpoch") Long startEpoch, @Param("endEpoch") Long endEpoch);

    /**
     * 分页查询无效数据
     */
    @Query("SELECT s FROM SensorData s WHERE s.isValid = false ORDER BY s.timestampEpoch DESC")
    Page<SensorData> findInvalidData(Pageable pageable);

    /**
     * 根据设备ID查询数据
     */
    List<SensorData> findByDeviceId(String deviceId);

    /**
     * 删除过期数据
     */
    @Query("DELETE FROM SensorData s WHERE s.timestampEpoch < :expireEpoch")
    void deleteExpiredData(@Param("expireEpoch") Long expireEpoch);

    /**
     * 获取体温异常数据
     */
    @Query("SELECT s FROM SensorData s WHERE s.temperature < 38.0 OR s.temperature > 39.5 ORDER BY s.timestampEpoch DESC")
    List<SensorData> findTemperatureAbnormalData();
}
