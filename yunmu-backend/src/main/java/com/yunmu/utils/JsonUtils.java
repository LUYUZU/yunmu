package com.yunmu.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.yunmu.dto.SensorDataDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class JsonUtils {

    private JsonUtils() {
        // 工具类，防止实例化
    }

    /**
     * 对象转JSON字符串
     */
    public static String toJson(Object obj) {
        try {
            return JSON.toJSONString(obj, SerializerFeature.WriteMapNullValue,
                    SerializerFeature.WriteDateUseDateFormat);
        } catch (Exception e) {
            log.error("对象转JSON失败", e);
            return null;
        }
    }

    /**
     * JSON字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return JSON.parseObject(json, clazz);
        } catch (Exception e) {
            log.error("JSON转对象失败: {}", e.getMessage());
            log.debug("原始JSON: {}", json);
            return null;
        }
    }

    /**
     * JSON字符串转SensorDataDTO（专用方法）
     */
    public static SensorDataDTO fromJsonToSensorData(String json) {
        try {
            return JSON.parseObject(json, SensorDataDTO.class);
        } catch (Exception e) {
            log.error("JSON转SensorDataDTO失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JSON字符串转List
     */
    public static <T> List<T> fromJsonList(String json, Class<T> clazz) {
        try {
            return JSON.parseArray(json, clazz);
        } catch (Exception e) {
            log.error("JSON转List失败", e);
            return null;
        }
    }

    /**
     * JSON字符串转Map
     */
    public static Map<String, Object> fromJsonToMap(String json) {
        try {
            return JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("JSON转Map失败", e);
            return null;
        }
    }

    /**
     * 对象转JSONObject
     */
    public static JSONObject toJsonObject(Object obj) {
        try {
            return (JSONObject) JSON.toJSON(obj);
        } catch (Exception e) {
            log.error("对象转JSONObject失败", e);
            return null;
        }
    }

    /**
     * 格式化JSON字符串
     */
    public static String formatJson(String json) {
        try {
            return JSON.toJSONString(JSON.parse(json), SerializerFeature.PrettyFormat);
        } catch (Exception e) {
            log.error("格式化JSON失败", e);
            return json;
        }
    }

    /**
     * 验证JSON字符串是否有效
     */
    public static boolean isValidJson(String json) {
        try {
            JSON.parse(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从JSON中提取字段
     */
    public static String extractField(String json, String fieldName) {
        try {
            JSONObject obj = JSON.parseObject(json);
            return obj.getString(fieldName);
        } catch (Exception e) {
            log.error("提取字段失败: {}", fieldName, e);
            return null;
        }
    }

    /**
     * 从JSON中提取字段（带默认值）
     */
    public static String extractField(String json, String fieldName, String defaultValue) {
        String value = extractField(json, fieldName);
        return value != null ? value : defaultValue;
    }
}