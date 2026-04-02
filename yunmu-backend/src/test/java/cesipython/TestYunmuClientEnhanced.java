// TestYunmuClientEnhanced.java - 修改版，匹配Python服务实际API
package cesipython;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestYunmuClientEnhanced {

    private static final String BASE_URL = "http://127.0.0.1:5000";
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ExecutorService executor = Executors.newFixedThreadPool(5);

    // 测试统计
    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;
    private static List<TestResult> testResults = new ArrayList<>();

    // 测试用的设备ID
    private static final String TEST_DEVICE_ID = "test_device_001";

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║          云牧智感 - Java调用Python机器学习服务测试            ║");
        System.out.println("║          定位 | 步数 | 姿态识别（躺卧/采食/站立/行走）        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 运行所有测试
        runAllTests();

        // 输出测试报告
        printTestReport();

        executor.shutdown();
    }

    private static void runAllTests() {
        // ========== 1. 健康检查 ==========
        testHealthCheck();

        // ========== 2. 设备数据接收测试（核心接口） ==========
        testReceiveStandingData();      // 站立姿态数据
        testReceiveLyingData();         // 躺卧姿态数据
        testReceiveFeedingData();       // 采食姿态数据
        testReceiveWalkingData();       // 行走姿态数据
        testReceiveFullData();          // 完整数据（含加速度历史）
        testReceiveWithGPS();           // 带GPS定位的数据

        // ========== 3. 设备状态查询测试 ==========
        testGetDeviceStatus();          // 获取单个设备状态
        testGetAllDevices();            // 获取所有设备

        // ========== 4. 步数统计测试 ==========
        testDailySteps();               // 每日步数统计
        testStepHistory();              // 步数历史查询

        // ========== 5. 预警功能测试 ==========
        testAlertSummary();             // 预警摘要

        // ========== 6. 综合测试 ==========
        testMultipleDevices();          // 多设备测试
        testConcurrentRequests();       // 并发请求
        testInvalidData();              // 异常数据处理
    }

    // ==================== 1. 健康检查 ====================
    private static void testHealthCheck() {
        System.out.println("\n【测试1】健康检查");
        System.out.println("----------------------------------------");

        runTest("服务健康检查", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                System.out.println("  ✓ 服务状态: " + result.get("status"));
                System.out.println("  ✓ 服务版本: " + result.get("version"));
                System.out.println("  ✓ 已加载算法: " + result.get("algorithms"));
                System.out.println("  ✓ 当前设备数: " + result.get("devices_count"));
                return true;
            }
            return false;
        });
    }

    // ==================== 2. 设备数据接收测试 ====================

    /**
     * 发送设备数据到 /api/device/data
     */
    private static Map<String, Object> sendDeviceData(Map<String, Object> data) throws Exception {
        String jsonBody = mapper.writeValueAsString(data);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/device/data"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return mapper.readValue(response.body(), Map.class);
        }
        return null;
    }

    private static void testReceiveStandingData() {
        System.out.println("\n【测试2】设备数据接收 - 站立姿态");
        System.out.println("----------------------------------------");

        runTest("站立姿态数据接收", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("device_id", TEST_DEVICE_ID);
            data.put("move", 0);                    // 0=静止
            data.put("steps", 0);
            data.put("counter", 1);
            data.put("timestamp", System.currentTimeMillis() / 1000.0);
            // 站立时的加速度数据
            data.put("accel_x", 0.05);
            data.put("accel_y", 0.08);
            data.put("accel_z", 9.81);
            data.put("gyro_x", 0.01);
            data.put("gyro_y", 0.01);
            data.put("gyro_z", 0.01);

            Map<String, Object> result = sendDeviceData(data);

            if (result != null && (boolean) result.get("success")) {
                System.out.println("  ✓ 设备ID: " + result.get("device_id"));
                System.out.println("  ✓ 识别姿态: " + result.get("posture"));
                System.out.println("  ✓ 姿态置信度: " + String.format("%.2f", ((Number) result.get("posture_confidence")).doubleValue()));
                System.out.println("  ✓ 计算步数: " + result.get("calculated_steps"));
                return "standing".equals(result.get("posture"));
            }
            return false;
        });
    }

    private static void testReceiveLyingData() {
        System.out.println("\n【测试3】设备数据接收 - 躺卧姿态");
        System.out.println("----------------------------------------");

        runTest("躺卧姿态数据接收", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("device_id", TEST_DEVICE_ID);
            data.put("move", 0);
            data.put("steps", 0);
            data.put("counter", 2);
            data.put("timestamp", System.currentTimeMillis() / 1000.0);
            // 躺卧时的加速度数据（模长小于9.0）
            // 修改为（正确 - 侧躺时Y轴朝上）
            data.put("accel_x", 0.0);
            data.put("accel_y", 9.5);   // Y轴朝上 ≈ 9.5
            data.put("accel_z", 0.2);  // accel_magnitude ≈ 9.5
            data.put("gyro_x", 0.02);
            data.put("gyro_y", 0.02);
            data.put("gyro_z", 0.02);

            Map<String, Object> result = sendDeviceData(data);

            if (result != null && (boolean) result.get("success")) {
                System.out.println("  ✓ 设备ID: " + result.get("device_id"));
                System.out.println("  ✓ 识别姿态: " + result.get("posture"));
                System.out.println("  ✓ 姿态置信度: " + String.format("%.2f", ((Number) result.get("posture_confidence")).doubleValue()));
                return "lying".equals(result.get("posture"));
            }
            return false;
        });
    }

    private static void testReceiveFeedingData() {
        System.out.println("\n【测试4】设备数据接收 - 采食姿态");
        System.out.println("----------------------------------------");

        runTest("采食姿态数据接收", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("device_id", TEST_DEVICE_ID);
            data.put("move", 1);                    // 1=移动
            data.put("steps", 100);
            data.put("counter", 3);
            data.put("timestamp", System.currentTimeMillis() / 1000.0);
            // 采食时的加速度和角速度数据
            data.put("accel_x", 0.15);
            data.put("accel_y", 0.25);
            data.put("accel_z", 9.75);
            data.put("gyro_x", 0.1);
            data.put("gyro_y", 0.55);              // 采食时Y轴角速度较大
            data.put("gyro_z", 0.08);

            Map<String, Object> result = sendDeviceData(data);

            if (result != null && (boolean) result.get("success")) {
                System.out.println("  ✓ 设备ID: " + result.get("device_id"));
                System.out.println("  ✓ 识别姿态: " + result.get("posture"));
                System.out.println("  ✓ 姿态置信度: " + String.format("%.2f", ((Number) result.get("posture_confidence")).doubleValue()));
                return "feeding".equals(result.get("posture"));
            }
            return false;
        });
    }

    private static void testReceiveWalkingData() {
        System.out.println("\n【测试5】设备数据接收 - 行走姿态");
        System.out.println("----------------------------------------");

        runTest("行走姿态数据接收", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("device_id", TEST_DEVICE_ID);
            data.put("move", 2);                    // 2=跑/走
            data.put("steps", 500);
            data.put("counter", 4);
            data.put("timestamp", System.currentTimeMillis() / 1000.0);
            // 行走时的加速度和角速度数据
            data.put("accel_x", 0.3);
            data.put("accel_y", 0.75);              // 行走时Y轴加速度较大
            data.put("accel_z", 9.65);
            data.put("gyro_x", 0.45);               // 行走时X轴角速度较大
            data.put("gyro_y", 0.2);
            data.put("gyro_z", 0.15);

            Map<String, Object> result = sendDeviceData(data);

            if (result != null && (boolean) result.get("success")) {
                System.out.println("  ✓ 设备ID: " + result.get("device_id"));
                System.out.println("  ✓ 识别姿态: " + result.get("posture"));
                System.out.println("  ✓ 姿态置信度: " + String.format("%.2f", ((Number) result.get("posture_confidence")).doubleValue()));
                System.out.println("  ✓ 计算步数: " + result.get("calculated_steps"));
                return "walking".equals(result.get("posture"));
            }
            return false;
        });
    }

    private static void testReceiveFullData() {
        System.out.println("\n【测试6】设备数据接收 - 完整数据（含加速度历史）");
        System.out.println("----------------------------------------");

        runTest("完整数据步数统计", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("device_id", TEST_DEVICE_ID);
            data.put("move", 2);
            data.put("steps", 0);
            data.put("counter", 5);
            data.put("timestamp", System.currentTimeMillis() / 1000.0);

            // 生成模拟的加速度历史数据（用于步数统计算法）
            List<Double> accelYHistory = new ArrayList<>();
            List<Double> timestamps = new ArrayList<>();

            double stepFrequency = 2.0; // 2步/秒
            for (int i = 0; i < 200; i++) {
                double t = i * 0.02;  // 50Hz采样
                // 行走时的Y轴加速度模式
                double stepPattern = 0.7 * Math.sin(2 * Math.PI * stepFrequency * t);
                accelYHistory.add(stepPattern + 0.08 * Math.random());
                timestamps.add(t);
            }

            data.put("accel_y_history", accelYHistory);
            data.put("timestamps", timestamps);
            data.put("accel_x", 0.3);
            data.put("accel_y", 0.75);
            data.put("accel_z", 9.65);
            data.put("gyro_x", 0.45);
            data.put("gyro_y", 0.2);
            data.put("gyro_z", 0.15);

            Map<String, Object> result = sendDeviceData(data);

            if (result != null && (boolean) result.get("success")) {
                int calculatedSteps = (int) result.get("calculated_steps");
                double stepFrequency_result = ((Number) result.get("step_frequency")).doubleValue();
                String activityLevel = (String) result.get("activity_level");

                System.out.println("  ✓ 算法统计步数: " + calculatedSteps);
                System.out.println("  ✓ 步频: " + String.format("%.2f", stepFrequency_result) + " 步/分钟");
                System.out.println("  ✓ 活动水平: " + activityLevel);
                System.out.println("  ✓ 识别姿态: " + result.get("posture"));

                return calculatedSteps > 0;
            }
            return false;
        });
    }

    private static void testReceiveWithGPS() {
        System.out.println("\n【测试7】设备数据接收 - 带GPS定位");
        System.out.println("----------------------------------------");

        runTest("GPS定位数据接收", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("device_id", TEST_DEVICE_ID);
            data.put("move", 1);
            data.put("steps", 200);
            data.put("counter", 6);
            data.put("timestamp", System.currentTimeMillis() / 1000.0);
            // GPS坐标（成都某地）
            data.put("longitude", 104.06);
            data.put("latitude", 30.57);
            // 姿态数据
            data.put("accel_x", 0.3);
            data.put("accel_y", 0.75);
            data.put("accel_z", 9.65);
            data.put("gyro_x", 0.45);
            data.put("gyro_y", 0.2);
            data.put("gyro_z", 0.15);

            Map<String, Object> result = sendDeviceData(data);

            if (result != null && (boolean) result.get("success")) {
                System.out.println("  ✓ 设备ID: " + result.get("device_id"));
                System.out.println("  ✓ 识别姿态: " + result.get("posture"));
                System.out.println("  ✓ 计算步数: " + result.get("calculated_steps"));

                // 验证GPS已接收（通过查询接口验证）
                Map<String, Object> deviceStatus = getDeviceStatus(TEST_DEVICE_ID);
                if (deviceStatus != null && deviceStatus.containsKey("current")) {
                    Map<String, Object> current = (Map<String, Object>) deviceStatus.get("current");
                    Map<String, Object> location = (Map<String, Object>) current.get("location");
                    System.out.println("  ✓ GPS位置: " + location);
                    return location != null;
                }
                return true;
            }
            return false;
        });
    }

    // ==================== 3. 设备状态查询测试 ====================

    private static Map<String, Object> getDeviceStatus(String deviceId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/device/" + deviceId))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return mapper.readValue(response.body(), Map.class);
        }
        return null;
    }

    private static void testGetDeviceStatus() {
        System.out.println("\n【测试8】获取设备状态");
        System.out.println("----------------------------------------");

        runTest("获取单个设备状态", () -> {
            Map<String, Object> result = getDeviceStatus(TEST_DEVICE_ID);

            if (result != null && (boolean) result.get("success")) {
                Map<String, Object> current = (Map<String, Object>) result.get("current");
                List<Map<String, Object>> history = (List<Map<String, Object>>) result.get("history");
                int historyCount = (int) result.get("history_count");

                System.out.println("  ✓ 设备ID: " + result.get("device_id"));
                System.out.println("  ✓ 当前姿态: " + (current != null ? current.get("posture") : "无"));
                System.out.println("  ✓ 历史记录数: " + historyCount);
                System.out.println("  ✓ 返回历史条数: " + history.size());

                return true;
            }
            return false;
        });
    }

    private static void testGetAllDevices() {
        System.out.println("\n【测试9】获取所有设备");
        System.out.println("----------------------------------------");

        runTest("获取所有设备状态", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/devices"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                int count = (int) result.get("count");
                Map<String, Object> devices = (Map<String, Object>) result.get("devices");

                System.out.println("  ✓ 设备总数: " + count);
                System.out.println("  ✓ 设备列表: " + devices.keySet());

                return true;
            }
            return false;
        });
    }

    // ==================== 4. 步数统计测试 ====================

    private static void testDailySteps() {
        System.out.println("\n【测试10】每日步数统计");
        System.out.println("----------------------------------------");

        runTest("获取每日步数摘要", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/steps/daily"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Map<String, Object> daily = (Map<String, Object>) result.get("daily");
                Map<String, Object> weekly = (Map<String, Object>) result.get("weekly");
                Map<String, Object> alertSummary = (Map<String, Object>) result.get("alert_summary");

                System.out.println("  ✓ 今日日期: " + daily.get("date"));
                System.out.println("  ✓ 今日步数: " + daily.get("steps"));
                System.out.println("  ✓ 今日目标: " + daily.get("goal"));
                System.out.println("  ✓ 完成度: " + String.format("%.1f", ((Number) daily.get("completion")).doubleValue()) + "%");
                System.out.println("  ✓ 本周总步数: " + weekly.get("total_steps"));
                System.out.println("  ✓ 本周日均: " + String.format("%.0f", ((Number) weekly.get("average_daily")).doubleValue()));
                System.out.println("  ✓ 预警总数: " + alertSummary.get("total_alerts"));

                return true;
            }
            return false;
        });
    }

    private static void testStepHistory() {
        System.out.println("\n【测试11】步数历史查询");
        System.out.println("----------------------------------------");

        runTest("获取设备步数历史", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/steps/history/" + TEST_DEVICE_ID))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> history = (List<Map<String, Object>>) result.get("history");
                int count = (int) result.get("count");

                System.out.println("  ✓ 设备ID: " + result.get("device_id"));
                System.out.println("  ✓ 历史记录数: " + count);

                if (!history.isEmpty()) {
                    Map<String, Object> latest = history.get(history.size() - 1);
                    System.out.println("  ✓ 最新记录步数: " + latest.get("steps"));
                    System.out.println("  ✓ 最新记录姿态: " + latest.get("posture"));
                }

                return true;
            }
            return false;
        });
    }

    // ==================== 5. 预警功能测试 ====================

    private static void testAlertSummary() {
        System.out.println("\n【测试12】预警摘要");
        System.out.println("----------------------------------------");

        runTest("获取步数预警摘要", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/alert/summary"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Map<String, Object> alertSummary = (Map<String, Object>) result.get("alert_summary");
                Map<String, Object> frontendData = (Map<String, Object>) result.get("frontend_data");

                System.out.println("  ✓ 总预警数: " + alertSummary.get("total_alerts"));
                System.out.println("  ✓ 严重程度分布: " + alertSummary.get("severity_distribution"));
                System.out.println("  ✓ 正常范围: " + frontendData.get("current_bound"));
                System.out.println("  ✓ 是否存在异常: " + frontendData.get("has_anomaly"));

                return true;
            }
            return false;
        });
    }

    // ==================== 6. 综合测试 ====================

    private static void testMultipleDevices() {
        System.out.println("\n【测试13】多设备测试");
        System.out.println("----------------------------------------");

        String[] deviceIds = {"cow_001", "cow_002", "cow_003", "cow_004", "cow_005"};

        runTest("5个设备同时上报数据", () -> {
            int successCount = 0;

            for (String deviceId : deviceIds) {
                Map<String, Object> data = new HashMap<>();
                data.put("device_id", deviceId);
                data.put("move", 1);
                data.put("steps", (int)(Math.random() * 500));
                data.put("counter", 1);
                data.put("timestamp", System.currentTimeMillis() / 1000.0);
                data.put("accel_x", 0.1 + Math.random() * 0.3);
                data.put("accel_y", 0.2 + Math.random() * 0.6);
                data.put("accel_z", 9.7 + Math.random() * 0.2);
                data.put("longitude", 104.06 + Math.random() * 0.1);
                data.put("latitude", 30.57 + Math.random() * 0.1);

                Map<String, Object> result = sendDeviceData(data);
                if (result != null && (boolean) result.get("success")) {
                    successCount++;
                }
            }

            System.out.println("  ✓ 成功上报: " + successCount + "/" + deviceIds.length);

            // 验证所有设备都已注册
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/devices"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                int count = (int) result.get("count");
                System.out.println("  ✓ 服务端设备数: " + count);
                return successCount == deviceIds.length && count >= deviceIds.length;
            }

            return successCount == deviceIds.length;
        });
    }

    private static void testConcurrentRequests() {
        System.out.println("\n【测试14】并发请求测试");
        System.out.println("----------------------------------------");

        int concurrentCount = 20;

        runTest("并发 " + concurrentCount + " 个数据上报请求", () -> {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < concurrentCount; i++) {
                final int id = i;
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Map<String, Object> data = new HashMap<>();
                        data.put("device_id", "concurrent_device_" + id);
                        data.put("move", id % 3);
                        data.put("steps", id * 100);
                        data.put("counter", id);
                        data.put("timestamp", System.currentTimeMillis() / 1000.0);
                        data.put("accel_x", 0.1 + Math.random() * 0.3);
                        data.put("accel_y", 0.2 + Math.random() * 0.6);
                        data.put("accel_z", 9.7 + Math.random() * 0.2);

                        Map<String, Object> result = sendDeviceData(data);
                        return result != null && (boolean) result.get("success");
                    } catch (Exception e) {
                        return false;
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            allFutures.get(30, TimeUnit.SECONDS);

            long successCount = futures.stream()
                    .filter(f -> f.join())
                    .count();

            System.out.println("  ✓ 成功请求: " + successCount + "/" + concurrentCount);
            System.out.println("  ✓ 成功率: " + String.format("%.1f", (double) successCount / concurrentCount * 100) + "%");
            return successCount == concurrentCount;
        });
    }

    private static void testInvalidData() {
        System.out.println("\n【测试15】异常数据处理");
        System.out.println("----------------------------------------");

        runTest("缺失必填字段处理", () -> {
            Map<String, Object> data = new HashMap<>();
            // 故意不传 device_id
            data.put("move", 1);
            data.put("steps", 100);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/device/data"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("  ✓ 缺失device_id返回状态码: " + response.statusCode());
            // 应该返回400错误
            return response.statusCode() == 400;
        });

        runTest("空JSON请求", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/device/data"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("  ✓ 空JSON请求返回状态码: " + response.statusCode());
            return response.statusCode() == 400;
        });

        runTest("无效设备ID查询", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/device/invalid_device_xyz_123"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("  ✓ 无效设备ID查询返回状态码: " + response.statusCode());
            // 应该返回200但数据为空
            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Map<String, Object> current = (Map<String, Object>) result.get("current");
                System.out.println("  ✓ 返回数据为空: " + (current == null || current.isEmpty()));
                return true;
            }
            return false;
        });
    }

    // ==================== 辅助方法 ====================

    private static void runTest(String testName, TestFunction testFunc) {
        totalTests++;
        boolean passed = false;
        String errorMessage = null;

        try {
            passed = testFunc.execute();
        } catch (Exception e) {
            errorMessage = e.getMessage();
            passed = false;
        }

        if (passed) {
            passedTests++;
            System.out.println("  ✅ " + testName + " - 通过");
        } else {
            failedTests++;
            System.out.println("  ❌ " + testName + " - 失败");
            if (errorMessage != null) {
                System.out.println("     错误: " + errorMessage);
            }
        }

        testResults.add(new TestResult(testName, passed, errorMessage));
    }

    private static void printTestReport() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        测试报告                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  测试分类:");
        System.out.println("  ├── 健康检查: 1项");
        System.out.println("  ├── 设备数据接收: 6项 (站立/躺卧/采食/行走/完整数据/GPS)");
        System.out.println("  ├── 设备状态查询: 2项 (单设备/全部设备)");
        System.out.println("  ├── 步数统计: 2项 (每日统计/历史查询)");
        System.out.println("  ├── 预警功能: 1项");
        System.out.println("  └── 综合测试: 3项 (多设备/并发/异常处理)");
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 总测试数: " + totalTests + "                                          │");
        System.out.println("  │ ✅ 通过: " + passedTests + "                                           │");
        System.out.println("  │ ❌ 失败: " + failedTests + "                                           │");
        System.out.println("  │ 通过率: " + String.format("%.2f%%", (double) passedTests / totalTests * 100) + "                                          │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘");
        System.out.println();

        if (failedTests > 0) {
            System.out.println("失败的测试:");
            System.out.println("----------------------------------------");
            for (TestResult result : testResults) {
                if (!result.passed) {
                    System.out.println("  ❌ " + result.name);
                    if (result.error != null) {
                        System.out.println("     原因: " + result.error);
                    }
                }
            }
        }

        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        测试完成                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
    }

    @FunctionalInterface
    interface TestFunction {
        boolean execute() throws Exception;
    }

    static class TestResult {
        String name;
        boolean passed;
        String error;

        TestResult(String name, boolean passed, String error) {
            this.name = name;
            this.passed = passed;
            this.error = error;
        }
    }
}