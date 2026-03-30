package com.yunmu.utils;

public class DistanceUtils {

    private static final double EARTH_RADIUS = 6371000; // 地球半径（米）

    private DistanceUtils() {
        // 工具类，防止实例化
    }

    /**
     * 计算两点间距离（Haversine公式）
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * 计算多个点的总距离
     */
    public static double calculateTotalDistance(double[][] points) {
        if (points == null || points.length < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 1; i < points.length; i++) {
            totalDistance += calculateDistance(
                    points[i-1][0], points[i-1][1],
                    points[i][0], points[i][1]
            );
        }

        return totalDistance;
    }

    /**
     * 计算活动面积（简化多边形面积）
     */
    public static double calculateActivityArea(double[][] points) {
        if (points == null || points.length < 3) {
            return 0.0;
        }

        double area = 0.0;
        int n = points.length;

        for (int i = 0; i < n; i++) {
            double lat1 = points[i][0];
            double lon1 = points[i][1];
            double lat2 = points[(i + 1) % n][0];
            double lon2 = points[(i + 1) % n][1];

            area += Math.toRadians(lon2 - lon1) *
                    (2 + Math.sin(Math.toRadians(lat1)) + Math.sin(Math.toRadians(lat2)));
        }

        area = Math.abs(area * EARTH_RADIUS * EARTH_RADIUS / 2);
        return area;
    }

    /**
     * 计算中心点
     */
    public static double[] calculateCenterPoint(double[][] points) {
        if (points == null || points.length == 0) {
            return new double[]{0.0, 0.0};
        }

        double sumLat = 0.0;
        double sumLon = 0.0;

        for (double[] point : points) {
            sumLat += point[0];
            sumLon += point[1];
        }

        return new double[]{sumLat / points.length, sumLon / points.length};
    }

    /**
     * 判断点是否在区域内
     */
    public static boolean isPointInPolygon(double[] point, double[][] polygon) {
        if (polygon == null || polygon.length < 3) {
            return false;
        }

        boolean inside = false;
        int n = polygon.length;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            if (((polygon[i][1] > point[1]) != (polygon[j][1] > point[1])) &&
                    (point[0] < (polygon[j][0] - polygon[i][0]) * (point[1] - polygon[i][1])
                            / (polygon[j][1] - polygon[i][1]) + polygon[i][0])) {
                inside = !inside;
            }
        }

        return inside;
    }
}