package com.yunmu.repository;

import com.yunmu.entity.Animal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnimalRepository extends JpaRepository<Animal, String> {

    /**
     * 根据动物类型查询
     */
    List<Animal> findByAnimalType(String animalType);

    /**
     * 根据项圈设备编号查询绑定动物（一个项圈同时只绑定一只动物，唯一约束保证至多一条）
     */
    Optional<Animal> findByDeviceCode(String deviceCode);

    /**
     * 查询已绑定项圈的动物（device_code 非空）
     */
    @Query("SELECT a FROM Animal a WHERE a.deviceCode IS NOT NULL AND a.deviceCode <> ''")
    List<Animal> findBoundAnimals();

    /**
     * 根据牧场ID查询
     */
    List<Animal> findByPastureId(String pastureId);

    /**
     * 根据所有者查询
     */
    List<Animal> findByOwnerId(String ownerId);

    /**
     * 查询年龄范围内的动物
     */
    @Query("SELECT a FROM Animal a WHERE a.age BETWEEN :minAge AND :maxAge")
    List<Animal> findByAgeRange(@Param("minAge") Integer minAge,
                                @Param("maxAge") Integer maxAge);

    /**
     * 批量查询动物信息
     */
    List<Animal> findByAnimalIdIn(List<String> animalIds);

    /**
     * 统计各类动物数量
     */
    @Query("SELECT a.animalType, COUNT(a) FROM Animal a GROUP BY a.animalType")
    List<Object[]> countByAnimalType();

    /**
     * 查询最近注册的动物
     */
    @Query("SELECT a FROM Animal a ORDER BY a.registrationDate DESC")
    List<Animal> findRecentRegistered(org.springframework.data.domain.Pageable pageable);

    /**
     * 根据品种查询
     */
    List<Animal> findByBreed(String breed);

    /**
     * 搜索动物（ID或类型）
     */
    @Query("SELECT a FROM Animal a WHERE a.animalId LIKE %:keyword% OR a.breed LIKE %:keyword%")
    List<Animal> searchAnimals(@Param("keyword") String keyword);
}