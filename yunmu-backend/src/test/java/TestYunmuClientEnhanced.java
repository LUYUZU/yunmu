// TestYunmuClientEnhanced.java
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

        // ========== 2. 姿态识别测试 ==========
        testStandingPosture();      // 站立
        testLyingPosture();         // 躺卧
        testFeedingPosture();       // 采食
        testWalkingPosture();       // 行走
        testBatchPostureRecognition(); // 批量姿态识别

        // ========== 3. 步数统计测试 ==========
        testStepCountWalking();     // 行走步数统计
        testStepCountWithPose();    // 姿态联动步数
        testStepCountLyingPause();  // 躺卧时暂停计步
        testDailyStepSummary();     // 每日步数统计
        testStepAnomalyAlert();     // 步数异常预警

        // ========== 4. GPS定位测试 ==========
        testGpsParse();             // GPS数据解析
        testGpsCurrentPosition();   // 获取当前位置
        testWsnLocalization();      // WSN辅助定位
        testWsnCalibration();       // WSN路径损耗校准

        // ========== 5. 综合测试 ==========
        testAnimalStatus();         // 动物综合状态
        testConcurrentRequests();   // 并发请求
        testInvalidData();          // 异常数据处理
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
                System.out.println("  ✓ 服务版本: " + result.get("status"));
                System.out.println("  ✓ 可用模块: " + result.get("modules"));
                return true;
            }
            return false;
        });
    }

    // ==================== 2. 姿态识别测试 ====================

    private static void testStandingPosture() {
        System.out.println("\n【测试2】姿态识别 - 站立");
        System.out.println("----------------------------------------");

        runTest("站立姿态识别", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_001");
            data.put("accel_x", 0.05);
            data.put("accel_y", 0.08);
            data.put("accel_z", 9.81);  // 重力加速度
            data.put("gyro_x", 0.01);
            data.put("gyro_y", 0.01);
            data.put("gyro_z", 0.01);
            data.put("timestamp", System.currentTimeMillis() / 1000);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/posture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                String posture = (String) result.get("posture_type");
                double confidence = (double) result.get("confidence");

                System.out.println("  ✓ 姿态类型: " + posture);
                System.out.println("  ✓ 置信度: " + String.format("%.2f", confidence * 100) + "%");

                boolean isCorrect = "standing".equals(posture);
                System.out.println("  ✓ 识别结果: " + (isCorrect ? "正确" : "期望standing，实际" + posture));
                return isCorrect;
            }
            return false;
        });
    }

    private static void testLyingPosture() {
        System.out.println("\n【测试3】姿态识别 - 躺卧");
        System.out.println("----------------------------------------");

        runTest("躺卧姿态识别", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_002");
            data.put("accel_x", 0.02);
            data.put("accel_y", 0.03);
            data.put("accel_z", 8.5);   // 躺卧时加速度模长小于9.0
            data.put("gyro_x", 0.02);
            data.put("gyro_y", 0.02);
            data.put("gyro_z", 0.02);
            data.put("timestamp", System.currentTimeMillis() / 1000);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/posture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                String posture = (String) result.get("posture_type");
                double confidence = (double) result.get("confidence");

                System.out.println("  ✓ 姿态类型: " + posture);
                System.out.println("  ✓ 置信度: " + String.format("%.2f", confidence * 100) + "%");

                boolean isCorrect = "lying".equals(posture);
                System.out.println("  ✓ 识别结果: " + (isCorrect ? "正确" : "期望lying，实际" + posture));
                return isCorrect;
            }
            return false;
        });
    }

    private static void testFeedingPosture() {
        System.out.println("\n【测试4】姿态识别 - 采食");
        System.out.println("----------------------------------------");

        runTest("采食姿态识别", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_003");
            data.put("accel_x", 0.15);
            data.put("accel_y", 0.25);
            data.put("accel_z", 9.75);
            data.put("gyro_x", 0.1);
            data.put("gyro_y", 0.55);   // 采食时Y轴角速度较大
            data.put("gyro_z", 0.08);
            data.put("timestamp", System.currentTimeMillis() / 1000);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/posture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                String posture = (String) result.get("posture_type");
                double confidence = (double) result.get("confidence");

                System.out.println("  ✓ 姿态类型: " + posture);
                System.out.println("  ✓ 置信度: " + String.format("%.2f", confidence * 100) + "%");

                boolean isCorrect = "feeding".equals(posture);
                System.out.println("  ✓ 识别结果: " + (isCorrect ? "正确" : "期望feeding，实际" + posture));
                return isCorrect;
            }
            return false;
        });
    }

    private static void testWalkingPosture() {
        System.out.println("\n【测试5】姿态识别 - 行走");
        System.out.println("----------------------------------------");

        runTest("行走姿态识别", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_004");
            data.put("accel_x", 0.3);
            data.put("accel_y", 0.75);   // 行走时Y轴加速度较大
            data.put("accel_z", 9.65);
            data.put("gyro_x", 0.45);    // 行走时X轴角速度较大
            data.put("gyro_y", 0.2);
            data.put("gyro_z", 0.15);
            data.put("timestamp", System.currentTimeMillis() / 1000);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/posture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                String posture = (String) result.get("posture_type");
                double confidence = (double) result.get("confidence");

                System.out.println("  ✓ 姿态类型: " + posture);
                System.out.println("  ✓ 置信度: " + String.format("%.2f", confidence * 100) + "%");

                boolean isCorrect = "walking".equals(posture);
                System.out.println("  ✓ 识别结果: " + (isCorrect ? "正确" : "期望walking，实际" + posture));
                return isCorrect;
            }
            return false;
        });
    }

    private static void testBatchPostureRecognition() {
        System.out.println("\n【测试6】批量姿态识别");
        System.out.println("----------------------------------------");

        runTest("批量识别10个动物姿态", () -> {
            List<Map<String, Object>> animals = new ArrayList<>();

            // 测试不同姿态
            String[] postures = {"standing", "lying", "feeding", "walking", "standing"};
            double[][] accelData = {
                    {0.05, 0.08, 9.81},    // 站立
                    {0.02, 0.03, 8.5},     // 躺卧
                    {0.15, 0.25, 9.75},    // 采食
                    {0.30, 0.75, 9.65},    // 行走
                    {0.05, 0.08, 9.81}     // 站立
            };

            for (int i = 1; i <= 10; i++) {
                Map<String, Object> animal = new HashMap<>();
                animal.put("animal_id", "cow_" + String.format("%03d", i));
                int idx = (i - 1) % accelData.length;
                animal.put("accel_x", accelData[idx][0]);
                animal.put("accel_y", accelData[idx][1]);
                animal.put("accel_z", accelData[idx][2]);
                animal.put("gyro_x", 0.1);
                animal.put("gyro_y", 0.2);
                animal.put("gyro_z", 0.05);
                animals.add(animal);
            }

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("data", animals);

            String jsonBody = mapper.writeValueAsString(requestMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/batch/posture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            long endTime = System.currentTimeMillis();

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                int count = (int) result.get("count");
                System.out.println("  ✓ 处理数量: " + count);
                System.out.println("  ✓ 耗时: " + (endTime - startTime) + " ms");
                return count == 10;
            }
            return false;
        });
    }

    // ==================== 3. 步数统计测试 ====================

    private static void testStepCountWalking() {
        System.out.println("\n【测试7】步数统计 - 行走");
        System.out.println("----------------------------------------");

        runTest("10秒行走数据步数统计", () -> {
            List<Double> accelX = new ArrayList<>();
            List<Double> accelY = new ArrayList<>();
            List<Double> accelZ = new ArrayList<>();
            List<Double> timestamps = new ArrayList<>();

            // 生成10秒行走数据，50Hz采样
            double stepFrequency = 2.0; // 2步/秒
            for (int i = 0; i < 500; i++) {
                double t = i * 0.02;
                // 行走时的Y轴加速度模式
                double stepPattern = 0.7 * Math.sin(2 * Math.PI * stepFrequency * t);

                accelX.add(0.1 * Math.sin(2 * Math.PI * 1 * t) + 0.03 * Math.random());
                accelY.add(stepPattern + 0.08 * Math.random());
                accelZ.add(9.8 + 0.15 * Math.sin(2 * Math.PI * 2 * t) + 0.05 * Math.random());
                timestamps.add(t);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_001");
            data.put("accel_x", accelX);
            data.put("accel_y", accelY);
            data.put("accel_z", accelZ);
            data.put("timestamps", timestamps);
            data.put("posture", "walking");
            data.put("timestamp", System.currentTimeMillis() / 1000);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/count/steps"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                int steps = (int) result.get("steps");
                double stepFreq = (double) result.get("step_frequency");
                double distance = (double) result.get("walking_distance");
                String activityLevel = (String) result.get("activity_level");

                System.out.println("  ✓ 检测步数: " + steps);
                System.out.println("  ✓ 步频: " + String.format("%.2f", stepFreq) + " 步/分钟");
                System.out.println("  ✓ 行走距离: " + String.format("%.2f", distance) + " 米");
                System.out.println("  ✓ 活动水平: " + activityLevel);
                System.out.println("  ✓ 当前姿态: " + result.get("current_posture"));

                return steps > 0;
            }
            return false;
        });
    }

    private static void testStepCountWithPose() {
        System.out.println("\n【测试8】步数统计 - 姿态联动");
        System.out.println("----------------------------------------");

        String[] postures = {"standing", "walking", "feeding", "walking"};

        for (String posture : postures) {
            runTest("姿态: " + posture, () -> {
                List<Double> accelY = new ArrayList<>();
                List<Double> timestamps = new ArrayList<>();

                // 生成模拟步数数据
                for (int i = 0; i < 200; i++) {
                    double t = i * 0.02;
                    accelY.add(0.6 * Math.sin(2 * Math.PI * 2 * t) + 0.05 * Math.random());
                    timestamps.add(t);
                }

                Map<String, Object> data = new HashMap<>();
                data.put("animal_id", "cow_001");
                data.put("accel_y", accelY);
                data.put("timestamps", timestamps);
                data.put("posture", posture);

                String jsonBody = mapper.writeValueAsString(data);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/count/steps"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                    int steps = (int) result.get("steps");
                    System.out.println("    → 姿态: " + posture + ", 步数: " + steps);
                    return true;
                }
                return false;
            });
        }
    }

    private static void testStepCountLyingPause() {
        System.out.println("\n【测试9】步数统计 - 躺卧时暂停计步");
        System.out.println("----------------------------------------");

        runTest("躺卧姿态下不计步", () -> {
            List<Double> accelY = new ArrayList<>();
            List<Double> timestamps = new ArrayList<>();

            // 生成模拟步数数据
            for (int i = 0; i < 200; i++) {
                double t = i * 0.02;
                accelY.add(0.6 * Math.sin(2 * Math.PI * 2 * t) + 0.05 * Math.random());
                timestamps.add(t);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_001");
            data.put("accel_y", accelY);
            data.put("timestamps", timestamps);
            data.put("posture", "lying");  // 躺卧姿态

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/count/steps"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                int steps = (int) result.get("steps");
                String posture = (String) result.get("current_posture");

                System.out.println("  ✓ 当前姿态: " + posture);
                System.out.println("  ✓ 检测步数: " + steps + " (躺卧时不应计步)");

                // 躺卧时步数应该为0
                return steps == 0;
            }
            return false;
        });
    }

    private static void testDailyStepSummary() {
        System.out.println("\n【测试10】每日步数统计");
        System.out.println("----------------------------------------");

        runTest("获取每日步数摘要", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/step/daily"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Map<String, Object> daily = (Map<String, Object>) result.get("daily");
                Map<String, Object> weekly = (Map<String, Object>) result.get("weekly");

                System.out.println("  ✓ 今日步数: " + daily.get("steps"));
                System.out.println("  ✓ 今日目标: " + daily.get("goal"));
                System.out.println("  ✓ 完成度: " + String.format("%.1f", (double) daily.get("completion")) + "%");
                System.out.println("  ✓ 本周总步数: " + weekly.get("total_steps"));
                System.out.println("  ✓ 日均步数: " + String.format("%.0f", (double) weekly.get("average_daily")));
                return true;
            }
            return false;
        });
    }

    private static void testStepAnomalyAlert() {
        System.out.println("\n【测试11】步数异常预警");
        System.out.println("----------------------------------------");

        runTest("步数异常检测", () -> {
            // 先添加一些历史步数数据建立基线
            for (int i = 0; i < 20; i++) {
                Map<String, Object> stepData = new HashMap<>();
                List<Double> accelY = new ArrayList<>();
                List<Double> timestamps = new ArrayList<>();

                int baseSteps = 5000 + (int)(Math.random() * 1000);
                for (int j = 0; j < 200; j++) {
                    accelY.add(0.6 * Math.sin(2 * Math.PI * 2 * j * 0.02) + 0.05 * Math.random());
                    timestamps.add(j * 0.02);
                }
                stepData.put("accel_y", accelY);
                stepData.put("timestamps", timestamps);
                stepData.put("posture", "walking");

                String jsonBody = mapper.writeValueAsString(stepData);
                HttpRequest postRequest = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/count/steps"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                client.send(postRequest, HttpResponse.BodyHandlers.ofString());
            }

            // 获取预警信息
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/step/alert"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Map<String, Object> summary = (Map<String, Object>) result.get("summary");
                Map<String, Object> frontendData = (Map<String, Object>) result.get("frontend_data");

                System.out.println("  ✓ 总预警数: " + summary.get("total_alerts"));
                System.out.println("  ✓ 正常范围: " + frontendData.get("current_bound"));
                System.out.println("  ✓ 有异常: " + frontendData.get("has_anomaly"));
                return true;
            }
            return false;
        });
    }

    // ==================== 4. GPS定位测试 ====================

    private static void testGpsParse() {
        System.out.println("\n【测试12】GPS数据解析");
        System.out.println("----------------------------------------");

        runTest("解析NMEA-0183 GGA语句", () -> {
            // 标准GGA语句
            String nmeaSentence = "$GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47";

            Map<String, Object> data = new HashMap<>();
            data.put("nmea_sentence", nmeaSentence);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/gps/parse"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Map<String, Object> parsedData = (Map<String, Object>) result.get("parsed_data");
                Map<String, Object> currentPos = (Map<String, Object>) result.get("current_position");

                System.out.println("  ✓ 解析数据: " + (parsedData != null ? "成功" : "失败"));
                if (parsedData != null) {
                    System.out.println("  ✓ 纬度: " + parsedData.get("latitude"));
                    System.out.println("  ✓ 经度: " + parsedData.get("longitude"));
                    System.out.println("  ✓ 海拔: " + parsedData.get("altitude"));
                    System.out.println("  ✓ 卫星数: " + parsedData.get("satellites"));
                    System.out.println("  ✓ 定位质量: " + parsedData.get("fix_quality"));
                }
                return parsedData != null;
            }
            return false;
        });
    }

    private static void testGpsCurrentPosition() {
        System.out.println("\n【测试13】获取当前位置");
        System.out.println("----------------------------------------");

        runTest("获取当前GPS位置", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/gps/position"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Map<String, Object> position = (Map<String, Object>) result.get("position");

                System.out.println("  ✓ 定位源: " + position.get("source"));
                System.out.println("  ✓ 纬度: " + position.get("latitude"));
                System.out.println("  ✓ 经度: " + position.get("longitude"));
                System.out.println("  ✓ 海拔: " + position.get("altitude"));
                System.out.println("  ✓ 卫星数: " + position.get("satellites"));
                System.out.println("  ✓ WSN辅助: " + position.get("wsn_active"));
                return true;
            }
            return false;
        });
    }

    private static void testWsnLocalization() {
        System.out.println("\n【测试14】WSN辅助定位");
        System.out.println("----------------------------------------");

        runTest("基于RSSI的WSN定位", () -> {
            Map<String, Object> data = new HashMap<>();

            // 锚节点位置
            Map<String, double[]> anchors = new HashMap<>();
            anchors.put("anchor1", new double[]{0, 0});
            anchors.put("anchor2", new double[]{100, 0});
            anchors.put("anchor3", new double[]{50, 100});
            anchors.put("anchor4", new double[]{0, 100});
            data.put("anchors", anchors);

            // RSSI读数
            Map<String, Double> rssiReadings = new HashMap<>();
            rssiReadings.put("anchor1", -65.0);
            rssiReadings.put("anchor2", -70.0);
            rssiReadings.put("anchor3", -68.0);
            rssiReadings.put("anchor4", -72.0);
            data.put("rssi_readings", rssiReadings);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/gps/wsn"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Map<String, Object> position = (Map<String, Object>) result.get("position");

                System.out.println("  ✓ 定位方法: " + position.get("method"));
                System.out.println("  ✓ 估算纬度: " + position.get("latitude"));
                System.out.println("  ✓ 估算经度: " + position.get("longitude"));
                System.out.println("  ✓ 路径损耗因子: " + result.get("path_loss_exponent"));
                System.out.println("  ✓ 已校准: " + result.get("calibrated"));
                return true;
            }
            return false;
        });
    }

    private static void testWsnCalibration() {
        System.out.println("\n【测试15】WSN路径损耗校准");
        System.out.println("----------------------------------------");

        runTest("校准路径损耗因子", () -> {
            Map<String, Object> data = new HashMap<>();

            // 校准参数
            Map<String, Object> calibration = new HashMap<>();
            calibration.put("distance", 50.0);  // 已知距离50米
            calibration.put("rssi", -68.0);     // 测得的RSSI值
            data.put("calibration", calibration);

            // RSSI读数
            Map<String, Double> rssiReadings = new HashMap<>();
            rssiReadings.put("anchor1", -65.0);
            rssiReadings.put("anchor2", -70.0);
            rssiReadings.put("anchor3", -68.0);
            data.put("rssi_readings", rssiReadings);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/gps/wsn"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                double pathLoss = (double) result.get("path_loss_exponent");
                boolean calibrated = (boolean) result.get("calibrated");

                System.out.println("  ✓ 校准后路径损耗因子: " + String.format("%.2f", pathLoss));
                System.out.println("  ✓ 校准状态: " + calibrated);
                return true;
            }
            return false;
        });
    }

    // ==================== 5. 综合测试 ====================

    private static void testAnimalStatus() {
        System.out.println("\n【测试16】动物综合状态");
        System.out.println("----------------------------------------");

        runTest("获取动物综合状态", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_001");
            data.put("accel_x", 0.3);
            data.put("accel_y", 0.75);
            data.put("accel_z", 9.65);
            data.put("gyro_x", 0.45);
            data.put("gyro_y", 0.2);
            data.put("gyro_z", 0.15);

            // 步数历史数据
            List<Double> accelYHistory = new ArrayList<>();
            List<Double> timestamps = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                double t = i * 0.02;
                accelYHistory.add(0.6 * Math.sin(2 * Math.PI * 2 * t) + 0.05 * Math.random());
                timestamps.add(t);
            }
            data.put("accel_y_history", accelYHistory);
            data.put("timestamps", timestamps);
            data.put("timestamp", System.currentTimeMillis() / 1000);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/animal/status"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            long endTime = System.currentTimeMillis();

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Map<String, Object> posture = (Map<String, Object>) result.get("posture");
                Map<String, Object> steps = (Map<String, Object>) result.get("steps");
                Map<String, Object> location = (Map<String, Object>) result.get("location");

                System.out.println("  ✓ 动物ID: " + result.get("animal_id"));
                System.out.println("  ✓ 姿态: " + posture.get("posture_type"));
                System.out.println("  ✓ 步数: " + steps.get("total_steps"));
                System.out.println("  ✓ 定位源: " + location.get("source"));
                System.out.println("  ✓ 耗时: " + (endTime - startTime) + " ms");
                return true;
            }
            return false;
        });
    }

    private static void testConcurrentRequests() {
        System.out.println("\n【测试17】并发请求测试");
        System.out.println("----------------------------------------");

        int concurrentCount = 20;

        runTest("并发 " + concurrentCount + " 个姿态识别请求", () -> {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < concurrentCount; i++) {
                final int id = i;
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Map<String, Object> data = new HashMap<>();
                        data.put("animal_id", "cow_" + id);
                        data.put("accel_x", 0.1 + Math.random() * 0.3);
                        data.put("accel_y", 0.2 + Math.random() * 0.6);
                        data.put("accel_z", 9.7 + Math.random() * 0.2);

                        String jsonBody = mapper.writeValueAsString(data);

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/api/predict/posture"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                                .timeout(Duration.ofSeconds(5))
                                .build();

                        HttpResponse<String> response = client.send(request,
                                HttpResponse.BodyHandlers.ofString());

                        return response.statusCode() == 200;
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
        System.out.println("\n【测试18】异常数据处理");
        System.out.println("----------------------------------------");

        runTest("无效加速度值处理", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_001");
            data.put("accel_x", Double.NaN);  // 无效值
            data.put("accel_y", Double.POSITIVE_INFINITY);
            data.put("accel_z", 9.8);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/posture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("  ✓ 服务未崩溃，返回状态码: " + response.statusCode());
            return response.statusCode() == 200 || response.statusCode() == 500;
        });

        runTest("空数据请求", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/posture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("  ✓ 空数据请求返回状态码: " + response.statusCode());
            return response.statusCode() == 200 || response.statusCode() == 400;
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
        System.out.println("  ├── 姿态识别: 5项 (站立/躺卧/采食/行走/批量)");
        System.out.println("  ├── 步数统计: 5项 (计步/姿态联动/躺卧暂停/每日统计/异常预警)");
        System.out.println("  ├── GPS定位: 4项 (解析/位置/WSN定位/校准)");
        System.out.println("  └── 综合测试: 3项 (综合状态/并发/异常)");
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