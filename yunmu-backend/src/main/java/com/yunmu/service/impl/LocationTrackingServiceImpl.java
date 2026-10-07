package com.yunmu.service.impl;

import com.yunmu.entity.LocationTrack;
import com.yunmu.repository.LocationTrackRepository;
import com.yunmu.service.LocationTrackingService;
import com.yunmu.utils.GpsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class LocationTrackingServiceImpl implements LocationTrackingService {

    @Autowired
    private LocationTrackRepository locationTrackRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String LOCATION_CACHE_PREFIX = "location:current:";
    private static final String TRACK_HISTORY_PREFIX = "track:history:";

    @Override
    public LocationTrack updateAnimalLocation(String animalId, Double latitude, Double longitude) {
        return updateAnimalLocation(animalId, latitude, longitude, "GCJ02");
    }

    @Override
    public LocationTrack updateAnimalLocation(String animalId, Double latitude, Double longitude, String coordinateType) {
        try {
            log.info("更新动物位置，动物ID: {}, 原始坐标: ({}, {}), 类型: {}",
                    animalId, longitude, latitude, coordinateType);

            Double finalLng = longitude;
            Double finalLat = latitude;

            // 根据坐标类型进行转换
            switch (coordinateType.toUpperCase()) {
                case "WGS84":
                    double[] gcj = GpsUtils.wgs84ToGcj02(longitude, latitude);
                    finalLng = gcj[0];
                    finalLat = gcj[1];
                    log.debug("WGS84转GCJ-02: ({}, {}) -> ({}, {})",
                            longitude, latitude, finalLng, finalLat);
                    break;
                case "BD09":
                    log.debug("BD09坐标转换: ({}, {})", longitude, latitude);
                    break;
                case "GCJ02":
                    break;
                default:
                    log.warn("未知坐标类型: {}, 使用原始坐标", coordinateType);
            }

            // 验证坐标有效性
            if (!GpsUtils.isValidCoordinate(finalLng, finalLat)) {
                log.warn("坐标无效: ({}, {})", finalLng, finalLat);
                return null;
            }

            // 创建位置记录
            LocationTrack locationTrack = new LocationTrack();
            locationTrack.setAnimalId(animalId);
            locationTrack.setTimestamp(LocalDateTime.now());
            locationTrack.setLongitude(finalLng);
            locationTrack.setLatitude(finalLat);
            locationTrack.setIsValidGps(true);
            locationTrack.setGpsQuality("GOOD");
            locationTrack.setCreateTime(LocalDateTime.now());

            LocationTrack savedTrack = locationTrackRepository.save(locationTrack);

            // 缓存最新位置
            cacheCurrentLocation(animalId, savedTrack);

            // 使用副本变量在lambda表达式中
            final String finalAnimalId = animalId;
            final Double finalLatitude = finalLat;
            final Double finalLongitude = finalLng;

            // 异步检查是否越界
            CompletableFuture.runAsync(() -> {
                checkBoundaryViolation(finalAnimalId, finalLatitude, finalLongitude);
            });

            log.info("位置更新成功 - 动物ID: {}, GCJ-02坐标: ({}, {})",
                    animalId, finalLng, finalLat);
            return savedTrack;

        } catch (Exception e) {
            log.error("更新动物位置失败", e);
            return null;
        }
    }

    @Override
    public LocationTrack getCurrentLocation(String animalId) {
        try {
            // 先从缓存获取
            LocationTrack cachedLocation = getCachedLocation(animalId);
            if (cachedLocation != null) {
                return cachedLocation;
            }

            // 从数据库获取最新位置
            Optional<LocationTrack> latestTrack = locationTrackRepository
                    .findLatestByAnimalId(animalId);

            if (latestTrack.isPresent()) {
                LocationTrack location = latestTrack.get();
                cacheCurrentLocation(animalId, location);
                return location;
            }

            return null;

        } catch (Exception e) {
            log.error("获取当前位置失败", e);
            return null;
        }
    }

    @Override
    public List<LocationTrack> getTrackHistory(String animalId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            log.info("获取轨迹历史，动物ID: {}, 时间范围: {} - {}", animalId, startTime, endTime);

            List<LocationTrack> tracks = locationTrackRepository
                    .findTrackByAnimalIdAndTimeRange(animalId,
                            startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

            log.debug("获取到 {} 条轨迹记录", tracks.size());
            return tracks;

        } catch (Exception e) {
            log.error("获取轨迹历史失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> calculateActivityRange(String animalId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            Map<String, Object> result = new HashMap<>();

            List<LocationTrack> tracks = getTrackHistory(animalId, startTime, endTime);

            if (tracks.isEmpty()) {
                result.put("error", "无轨迹数据");
                return result;
            }

            double minLat = Double.MAX_VALUE;
            double maxLat = Double.MIN_VALUE;
            double minLng = Double.MAX_VALUE;
            double maxLng = Double.MIN_VALUE;
            double totalDistance = 0.0;

            LocationTrack prevTrack = null;
            for (LocationTrack track : tracks) {
                minLat = Math.min(minLat, track.getLatitude());
                maxLat = Math.max(maxLat, track.getLatitude());
                minLng = Math.min(minLng, track.getLongitude());
                maxLng = Math.max(maxLng, track.getLongitude());

                if (prevTrack != null) {
                    double distance = GpsUtils.distance(
                            prevTrack.getLongitude(), prevTrack.getLatitude(),
                            track.getLongitude(), track.getLatitude()
                    );
                    totalDistance += distance;
                }
                prevTrack = track;
            }

            double centerLat = (minLat + maxLat) / 2;
            double centerLng = (minLng + maxLng) / 2;
            double latRange = maxLat - minLat;
            double lngRange = maxLng - minLng;
            double area = latRange * lngRange * 111.32 * 111.32;

            result.put("animalId", animalId);
            result.put("startTime", startTime);
            result.put("endTime", endTime);
            result.put("trackCount", tracks.size());
            result.put("boundingBox", Map.of(
                    "minLatitude", minLat,
                    "maxLatitude", maxLat,
                    "minLongitude", minLng,
                    "maxLongitude", maxLng
            ));
            result.put("center", Map.of(
                    "latitude", centerLat,
                    "longitude", centerLng
            ));
            result.put("totalDistance", totalDistance);
            result.put("estimatedArea", area);
            result.put("averageSpeed", calculateAverageSpeed(tracks));

            return result;

        } catch (Exception e) {
            log.error("计算活动范围失败", e);
            return Collections.singletonMap("error", e.getMessage());
        }
    }

    @Override
    public boolean checkBoundaryViolation(String animalId, Double latitude, Double longitude) {
        try {
            // 从数据库获取牧场的边界配置
            // 简化处理：固定边界（示例数据）
            double minLat = 31.0;
            double maxLat = 31.5;
            double minLng = 121.0;
            double maxLng = 121.5;

            boolean isViolation = latitude < minLat || latitude > maxLat ||
                    longitude < minLng || longitude > maxLng;

            if (isViolation) {
                log.warn("动物越界警报: {} - 坐标: ({}, {})", animalId, longitude, latitude);
                // TODO: 生成越界预警
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("检查越界失败", e);
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> generateHeatmapData(String pastureId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<Map<String, Object>> heatmapData = new ArrayList<>();

            // TODO: 根据牧场ID查询所有动物的位置数据
            // 简化处理：生成模拟数据

            for (int i = 0; i < 100; i++) {
                Map<String, Object> point = new HashMap<>();
                point.put("lng", 121.0 + Math.random() * 0.5);
                point.put("lat", 31.0 + Math.random() * 0.5);
                point.put("weight", Math.random());
                heatmapData.add(point);
            }

            return heatmapData;

        } catch (Exception e) {
            log.error("生成热力图数据失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void batchUpdateLocations(List<Map<String, Object>> locationList) {
        try {
            log.info("批量更新位置，数量: {}", locationList.size());

            for (Map<String, Object> location : locationList) {
                String animalId = (String) location.get("animalId");
                Double lng = (Double) location.get("lng");
                Double lat = (Double) location.get("lat");
                String coordType = (String) location.getOrDefault("coordType", "GCJ02");

                if (animalId != null && lng != null && lat != null) {
                    updateAnimalLocation(animalId, lat, lng, coordType);
                }
            }

            log.info("批量位置更新完成");

        } catch (Exception e) {
            log.error("批量更新位置失败", e);
        }
    }

    /**
     * 缓存当前位置
     */
    private void cacheCurrentLocation(String animalId, LocationTrack location) {
        try {
            String cacheKey = LOCATION_CACHE_PREFIX + animalId;
            redisTemplate.opsForValue().set(cacheKey, location, 10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("缓存当前位置失败", e);
        }
    }

    /**
     * 获取缓存的位置
     */
    private LocationTrack getCachedLocation(String animalId) {
        try {
            String cacheKey = LOCATION_CACHE_PREFIX + animalId;
            return (LocationTrack) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.error("获取缓存位置失败", e);
            return null;
        }
    }

    /**
     * 计算平均速度
     */
    private double calculateAverageSpeed(List<LocationTrack> tracks) {
        if (tracks.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        long totalTime = 0;

        for (int i = 1; i < tracks.size(); i++) {
            LocationTrack prev = tracks.get(i - 1);
            LocationTrack curr = tracks.get(i);

            double distance = GpsUtils.distance(
                    prev.getLongitude(), prev.getLatitude(),
                    curr.getLongitude(), curr.getLatitude()
            );

            long timeDiff = java.time.Duration.between(prev.getTimestamp(), curr.getTimestamp()).getSeconds();

            totalDistance += distance;
            totalTime += timeDiff;
        }

        if (totalTime == 0) {
            return 0.0;
        }

        return totalDistance / totalTime;
    }
}