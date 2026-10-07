package com.yunmu.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * GPS坐标转换工具类
 * 支持：
 * 1. 度分格式转WGS84（十进制度数）
 * 2. WGS84转GCJ-02（高德地图坐标）
 */
@Slf4j
public class GpsUtils {

    private static final double PI = 3.1415926535897932384626;
    private static final double A = 6378245.0;      // 长半轴
    private static final double EE = 0.00669342162296594323; // 偏心率平方

    private GpsUtils() {
        // 工具类，防止实例化
    }

    // ==================== 度分格式转换 ====================

    /**
     * 度分格式转十进制度数（WGS84）
     * 格式说明：ddmm.mmmm (度分格式)
     * 例如：3110.5682 表示 31°10.5682' = 31 + 10.5682/60 = 31.1761367
     *
     * @param degreeMinute 度分格式坐标，如 3110.5682
     * @return 十进制度数
     */
    public static Double degreeMinuteToDecimal(Double degreeMinute) {
        if (degreeMinute == null) {
            return null;
        }
        return degreeMinuteToDecimal(degreeMinute.toString());
    }

    /**
     * 度分格式转十进制度数（WGS84）
     *
     * @param degreeMinuteStr 度分格式字符串，如 "3110.5682"
     * @return 十进制度数
     */
    public static Double degreeMinuteToDecimal(String degreeMinuteStr) {
        if (degreeMinuteStr == null || degreeMinuteStr.trim().isEmpty()) {
            return null;
        }

        try {
            // 解析度分格式
            double value = Double.parseDouble(degreeMinuteStr);
            int degrees = (int) (value / 100);
            double minutes = value % 100;
            double decimal = degrees + minutes / 60.0;

            log.debug("度分转换: {} -> {}", degreeMinuteStr, decimal);
            return decimal;
        } catch (NumberFormatException e) {
            log.error("度分格式解析失败: {}", degreeMinuteStr, e);
            return null;
        }
    }

    /**
     * 十进制度数转度分格式（DDMM.MMMM）
     *
     * <p>与 {@link #degreeMinuteToDecimal(String)} 严格互逆。度部分至少补齐 2 位，
     * 保证「分」始终占据末两位整数（例如纬度 9.5° 应输出 {@code 0930.000} 而非 {@code 9030.000}，
     * 后者会被回解析成 90.5°）。经度为 3 位度时自然占用 5 位整数部分。
     *
     * @param decimal 十进制度数
     * @return 度分格式字符串，如 "3110.568"
     */
    public static String decimalToDegreeMinute(Double decimal) {
        if (decimal == null) {
            return null;
        }

        int degrees = (int) Math.floor(decimal);
        double minutes = (decimal - degrees) * 60;
        // 固定 Locale，避免小数点被本地化成逗号导致回解析失败
        return String.format(Locale.ROOT, "%02d%06.3f", degrees, minutes);
    }

    // ==================== WGS84 转 GCJ-02 ====================

    /**
     * WGS84坐标转GCJ-02坐标（高德地图坐标系）
     * 适用于中国境内坐标加密
     *
     * @param lng WGS84经度
     * @param lat WGS84纬度
     * @return GCJ-02坐标数组 [lng, lat]
     */
    public static double[] wgs84ToGcj02(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }

        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);

        double mgLat = lat + dLat;
        double mgLng = lng + dLng;

        return new double[]{mgLng, mgLat};
    }

    /**
     * 批量转换WGS84坐标到GCJ-02
     *
     * @param coordinates 坐标数组，每个元素为 [lng, lat]
     * @return GCJ-02坐标数组
     */
    public static double[][] wgs84ToGcj02(double[][] coordinates) {
        if (coordinates == null) {
            return null;
        }

        double[][] result = new double[coordinates.length][2];
        for (int i = 0; i < coordinates.length; i++) {
            result[i] = wgs84ToGcj02(coordinates[i][0], coordinates[i][1]);
        }
        return result;
    }

    // ==================== GCJ-02 转 WGS84 ====================

    /**
     * GCJ-02坐标转WGS84坐标（近似逆转换）
     *
     * @param lng GCJ-02经度
     * @param lat GCJ-02纬度
     * @return WGS84坐标数组 [lng, lat]
     */
    public static double[] gcj02ToWgs84(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }

        double[] gcj = wgs84ToGcj02(lng, lat);
        double dLng = gcj[0] - lng;
        double dLat = gcj[1] - lat;

        return new double[]{lng - dLng, lat - dLat};
    }

    // ==================== WGS84 转 BD-09 ====================

    /**
     * WGS84转BD-09（百度地图坐标系）
     *
     * @param lng WGS84经度
     * @param lat WGS84纬度
     * @return BD-09坐标数组 [lng, lat]
     */
    public static double[] wgs84ToBd09(double lng, double lat) {
        double[] gcj = wgs84ToGcj02(lng, lat);
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    /**
     * GCJ-02转BD-09
     *
     * @param lng GCJ-02经度
     * @param lat GCJ-02纬度
     * @return BD-09坐标数组 [lng, lat]
     */
    public static double[] gcj02ToBd09(double lng, double lat) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * PI);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * PI);
        double bdLng = z * Math.cos(theta) + 0.0065;
        double bdLat = z * Math.sin(theta) + 0.006;
        return new double[]{bdLng, bdLat};
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断是否在中国境外
     */
    private static boolean outOfChina(double lng, double lat) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    /**
     * 纬度转换
     */
    private static double transformLat(double lng, double lat) {
        double ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat +
                0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lat * PI) + 40.0 * Math.sin(lat / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(lat / 12.0 * PI) + 320 * Math.sin(lat * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * 经度转换
     */
    private static double transformLng(double lng, double lat) {
        double ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng +
                0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lng * PI) + 40.0 * Math.sin(lng / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(lng / 12.0 * PI) + 300.0 * Math.sin(lng / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * 计算两点间距离（Haversine公式）
     *
     * @param lng1 点1经度
     * @param lat1 点1纬度
     * @param lng2 点2经度
     * @param lat2 点2纬度
     * @return 距离（米）
     */
    public static double distance(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = lat1 * PI / 180.0;
        double radLat2 = lat2 * PI / 180.0;
        double a = radLat1 - radLat2;
        double b = (lng1 - lng2) * PI / 180.0;

        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
                Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * 6378137.0; // 地球半径（米）
        return Math.round(s * 10000) / 10000.0;
    }

    /**
     * 验证坐标是否有效
     */
    public static boolean isValidCoordinate(Double lng, Double lat) {
        if (lng == null || lat == null) {
            return false;
        }
        // 中国境内大致范围
        return lng >= 73.0 && lng <= 135.0 && lat >= 3.0 && lat <= 53.0;
    }
}