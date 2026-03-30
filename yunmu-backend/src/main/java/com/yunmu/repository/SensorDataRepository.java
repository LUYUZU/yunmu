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
    @Query("SELECT s FROM SensorData s WHERE s.animalId = :animalId ORDER BY s.timestamp DESC")
    List<SensorData> findLatestByAnimalId(@Param("animalId") String animalId, Pageable pageable);

    /**
     * 查询指定时间范围内的传感器数据
     */
    @Query("SELECT s FROM SensorData s WHERE s.animalId = :animalId AND s.timestamp BETWEEN :startTime AND :endTime ORDER BY s.timestamp")
    List<SensorData> findByAnimalIdAndTimeRange(
            @Param("animalId") String animalId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 批量查询多个动物的最新数据
     */
    @Query("SELECT s FROM SensorData s WHERE s.animalId IN :animalIds AND s.timestamp = (SELECT MAX(s2.timestamp) FROM SensorData s2 WHERE s2.animalId = s.animalId)")
    List<SensorData> findLatestByAnimalIds(@Param("animalIds") List<String> animalIds);

    /**
     * 统计指定时间段内的数据量
     */
    @Query("SELECT COUNT(s) FROM SensorData s WHERE s.timestamp BETWEEN :startTime AND :endTime")
    Long countByTimeRange(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /**
     * 分页查询无效数据
     */
    @Query("SELECT s FROM SensorData s WHERE s.isValid = false ORDER BY s.timestamp DESC")
    Page<SensorData> findInvalidData(Pageable pageable);

    /**
     * 根据设备ID查询数据
     */
    List<SensorData> findByDeviceId(String deviceId);

    /**
     * 删除过期数据
     */
    @Query("DELETE FROM SensorData s WHERE s.timestamp < :expireTime")
    void deleteExpiredData(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 获取温度异常数据
     */
    @Query("SELECT s FROM SensorData s WHERE s.temperature < 38.0 OR s.temperature > 39.5 ORDER BY s.timestamp DESC")
    List<SensorData> findTemperatureAbnormalData();
}