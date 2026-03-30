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
import java.util.stream.Collectors;

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
        System.out.println("║                    完整功能测试 v2.0                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 运行所有测试
        runAllTests();

        // 输出测试报告
        printTestReport();

        executor.shutdown();
    }

    private static void runAllTests() {
        // 基础功能测试
        testHealthCheck();
        testPostureRecognition();
        testPostureWithGyro();
        testBatchPostureRecognition();

        // 步数统计测试
        testStepCount();
        testStepCountWithDifferentPaces();
        testStepCountValidation();

        // 行为预测测试
        testBehaviorPrediction();
        testRuminationPrediction();
        testBehaviorWithDifferentData();

        // 性能测试
        testConcurrentRequests();
        testResponseTime();

        // 异常测试
        testInvalidData();
        testMissingFields();
    }

    // ==================== 基础功能测试 ====================

    private static void testHealthCheck() {
        System.out.println("【测试1】健康检查");
        System.out.println("----------------------------------------");

        runTest("健康检查", () -> {
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
                return true;
            }
            return false;
        });
    }

    private static void testPostureRecognition() {
        System.out.println("\n【测试2】姿态识别 - 单点预测");
        System.out.println("----------------------------------------");

        runTest("站立姿态识别", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_001");
            data.put("accel_x", 0.1);
            data.put("accel_y", 0.2);
            data.put("accel_z", 9.8);
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

                System.out.println("  ✓ 动物ID: " + result.get("animal_id"));
                System.out.println("  ✓ 姿态类型: " + posture);
                System.out.println("  ✓ 置信度: " + String.format("%.2f", confidence * 100) + "%");

                return posture != null && confidence > 0;
            }
            return false;
        });
    }

    private static void testPostureWithGyro() {
        System.out.println("\n【测试3】姿态识别 - 含陀螺仪数据");
        System.out.println("----------------------------------------");

        runTest("行走姿态识别", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_002");
            data.put("accel_x", 0.3);
            data.put("accel_y", 0.8);
            data.put("accel_z", 9.7);
            data.put("gyro_x", 0.5);
            data.put("gyro_y", 1.2);
            data.put("gyro_z", 0.3);
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
                System.out.println("  ✓ 姿态类型: " + posture);
                return true;
            }
            return false;
        });
    }

    private static void testBatchPostureRecognition() {
        System.out.println("\n【测试4】批量姿态识别");
        System.out.println("----------------------------------------");

        runTest("批量识别10个动物", () -> {
            List<Map<String, Object>> animals = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                Map<String, Object> animal = new HashMap<>();
                animal.put("animal_id", "cow_" + String.format("%03d", i));
                animal.put("accel_x", 0.1 + Math.random() * 0.2);
                animal.put("accel_y", 0.2 + Math.random() * 0.3);
                animal.put("accel_z", 9.8 + Math.random() * 0.2);
                animals.add(animal);
            }

            Map<String, Object> request = new HashMap<>();
            request.put("data", animals);

            String jsonBody = mapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/batch/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = client.send(httpRequest,
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

    // ==================== 步数统计测试 ====================

    private static void testStepCount() {
        System.out.println("\n【测试5】步数统计 - 基础测试");
        System.out.println("----------------------------------------");

        runTest("10秒行走数据统计", () -> {
            List<Double> accelX = new ArrayList<>();
            List<Double> accelY = new ArrayList<>();
            List<Double> accelZ = new ArrayList<>();
            List<Double> timestamps = new ArrayList<>();

            // 生成10秒数据，500个点
            for (int i = 0; i < 500; i++) {
                double t = i * 0.02;
                double stepPattern = 0.8 * Math.sin(2 * Math.PI * 2 * t);

                accelX.add(0.1 * Math.sin(2 * Math.PI * 1 * t) + 0.05 * Math.random());
                accelY.add(stepPattern + 0.1 * Math.random());
                accelZ.add(9.8 + 0.2 * Math.sin(2 * Math.PI * 2 * t) + 0.05 * Math.random());
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

                System.out.println("  ✓ 检测步数: " + steps);
                System.out.println("  ✓ 步频: " + String.format("%.2f", stepFreq) + " 步/分钟");
                System.out.println("  ✓ 行走距离: " + String.format("%.2f", distance) + " 米");
                System.out.println("  ✓ 活动水平: " + result.get("activity_level"));

                return steps > 0;
            }
            return false;
        });
    }

    private static void testStepCountWithDifferentPaces() {
        System.out.println("\n【测试6】步数统计 - 不同速度测试");
        System.out.println("----------------------------------------");

        // 测试慢走、正常走、快走
        double[] paces = {1.5, 2.0, 2.5}; // Hz
        String[] paceNames = {"慢走", "正常走", "快走"};

        for (int p = 0; p < paces.length; p++) {
            final double pace = paces[p];
            final String paceName = paceNames[p];

            runTest(paceName + " (" + pace + "Hz)", () -> {
                List<Double> accelY = new ArrayList<>();
                List<Double> timestamps = new ArrayList<>();

                for (int i = 0; i < 500; i++) {
                    double t = i * 0.02;
                    double stepPattern = 0.8 * Math.sin(2 * Math.PI * pace * t);
                    accelY.add(stepPattern + 0.1 * Math.random());
                    timestamps.add(t);
                }

                Map<String, Object> data = new HashMap<>();
                data.put("animal_id", "cow_001");
                data.put("accel_x", new ArrayList<>(Collections.nCopies(500, 0.1)));
                data.put("accel_y", accelY);
                data.put("accel_z", new ArrayList<>(Collections.nCopies(500, 9.8)));
                data.put("timestamps", timestamps);
                data.put("posture", "walking");

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

                    System.out.println("    → 步数: " + steps + ", 步频: " +
                            String.format("%.2f", stepFreq) + " 步/分钟");
                    return true;
                }
                return false;
            });
        }
    }

    private static void testStepCountValidation() {
        System.out.println("\n【测试7】步数统计 - 数据有效性验证");
        System.out.println("----------------------------------------");

        // 测试空数据
        runTest("空数据验证", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_001");
            data.put("accel_x", new ArrayList<>());
            data.put("accel_y", new ArrayList<>());
            data.put("accel_z", new ArrayList<>());
            data.put("timestamps", new ArrayList<>());

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/count/steps"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // 应该返回错误但不是异常
            return response.statusCode() == 400 || response.statusCode() == 500;
        });
    }

    // ==================== 行为预测测试 ====================

    private static void testBehaviorPrediction() {
        System.out.println("\n【测试8】行为预测");
        System.out.println("----------------------------------------");

        runTest("采食行为预测", () -> {
            // 生成模拟采食数据
            List<Double> accelData = new ArrayList<>();
            List<Double> soundData = new ArrayList<>();

            for (int i = 0; i < 100; i++) {
                double t = i * 0.02;
                accelData.add(0.4 * Math.sin(2 * Math.PI * 3 * t) + 0.08 * Math.random());
            }

            for (int i = 0; i < 2000; i++) {
                double t = i * 0.001;
                soundData.add(0.5 * Math.sin(2 * Math.PI * 4 * t) + 0.1 * Math.random());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_001");
            data.put("accel_data", accelData);
            data.put("sound_data", soundData);
            data.put("accel_sampling_rate", 50);
            data.put("sound_frequency", 1000);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/behavior"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            long endTime = System.currentTimeMillis();

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                String behavior = (String) result.get("behavior");
                double confidence = (double) result.get("confidence");

                System.out.println("  ✓ 预测行为: " + behavior);
                System.out.println("  ✓ 置信度: " + String.format("%.2f", confidence * 100) + "%");
                System.out.println("  ✓ 耗时: " + (endTime - startTime) + " ms");

                // 注意：行为可能为unknown，这是正常的
                return true;
            }
            return false;
        });
    }

    private static void testRuminationPrediction() {
        System.out.println("\n【测试9】反刍预测");
        System.out.println("----------------------------------------");

        runTest("反刍行为检测", () -> {
            // 生成模拟反刍声音（周期性）
            List<Double> audioData = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                double t = i * 0.001;
                double sound = 0.5 * Math.sin(2 * Math.PI * 2.5 * t) +
                        0.3 * Math.sin(2 * Math.PI * 5 * t) +
                        0.1 * Math.random();
                audioData.add(sound);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("animal_id", "cow_001");
            data.put("audio_data", audioData);
            data.put("timestamp", System.currentTimeMillis() / 1000);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/rumination"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(), Map.class);
                Boolean isRuminating = (Boolean) result.get("is_ruminating");
                Double confidence = (Double) result.get("confidence");

                System.out.println("  ✓ 是否反刍: " + (isRuminating != null ? isRuminating : "unknown"));
                System.out.println("  ✓ 置信度: " + (confidence != null ? String.format("%.2f", confidence * 100) + "%" : "N/A"));
                return true;
            }
            return false;
        });
    }

    private static void testBehaviorWithDifferentData() {
        System.out.println("\n【测试10】行为预测 - 不同数据量测试");
        System.out.println("----------------------------------------");

        int[] dataSizes = {50, 100, 200, 500};

        for (int size : dataSizes) {
            runTest("数据量: " + size + "个点", () -> {
                List<Double> accelData = new ArrayList<>();
                List<Double> soundData = new ArrayList<>();

                for (int i = 0; i < size; i++) {
                    accelData.add(Math.sin(i * 0.1) + 0.1 * Math.random());
                }

                int soundSize = Math.min(size * 20, 2000);
                for (int i = 0; i < soundSize; i++) {
                    soundData.add(Math.sin(i * 0.05) + 0.05 * Math.random());
                }

                Map<String, Object> data = new HashMap<>();
                data.put("animal_id", "cow_001");
                data.put("accel_data", accelData);
                data.put("sound_data", soundData);

                String jsonBody = mapper.writeValueAsString(data);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/predict/behavior"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                long startTime = System.currentTimeMillis();
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                long endTime = System.currentTimeMillis();

                if (response.statusCode() == 200) {
                    System.out.println("    → 数据量: " + size + " 加速度点, " + soundSize + " 声音点");
                    System.out.println("    → 耗时: " + (endTime - startTime) + " ms");
                    return true;
                }
                return false;
            });
        }
    }

    // ==================== 性能测试 ====================

    private static void testConcurrentRequests() {
        System.out.println("\n【测试11】并发请求测试");
        System.out.println("----------------------------------------");

        int concurrentCount = 10;

        runTest("并发 " + concurrentCount + " 个请求", () -> {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < concurrentCount; i++) {
                final int id = i;
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Map<String, Object> data = new HashMap<>();
                        data.put("animal_id", "cow_" + id);
                        data.put("accel_x", 0.1 + Math.random() * 0.2);
                        data.put("accel_y", 0.2 + Math.random() * 0.3);
                        data.put("accel_z", 9.8);

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
            return successCount == concurrentCount;
        });
    }

    private static void testResponseTime() {
        System.out.println("\n【测试12】响应时间测试");
        System.out.println("----------------------------------------");

        int testCount = 20;
        List<Long> responseTimes = new ArrayList<>();

        for (int i = 0; i < testCount; i++) {
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("animal_id", "cow_001");
                data.put("accel_x", 0.1);
                data.put("accel_y", 0.2);
                data.put("accel_z", 9.8);

                String jsonBody = mapper.writeValueAsString(data);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/predict/posture"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                long startTime = System.nanoTime();
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                long endTime = System.nanoTime();

                if (response.statusCode() == 200) {
                    responseTimes.add((endTime - startTime) / 1_000_000); // 毫秒
                }
            } catch (Exception e) {
                // 忽略单个失败
            }
        }

        if (!responseTimes.isEmpty()) {
            double avg = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long min = responseTimes.stream().min(Long::compare).orElse(0L);
            long max = responseTimes.stream().max(Long::compare).orElse(0L);

            System.out.println("  ✓ 测试次数: " + responseTimes.size());
            System.out.println("  ✓ 平均响应时间: " + String.format("%.2f", avg) + " ms");
            System.out.println("  ✓ 最小响应时间: " + min + " ms");
            System.out.println("  ✓ 最大响应时间: " + max + " ms");

            runTest("响应时间 < 500ms", () -> avg < 500);
        } else {
            System.out.println("  ✗ 没有成功的请求");
            runTest("响应时间测试", () -> false);
        }
    }

    // ==================== 异常测试 ====================

    private static void testInvalidData() {
        System.out.println("\n【测试13】异常数据处理测试");
        System.out.println("----------------------------------------");

        // 测试无效的加速度值
        runTest("无效加速度值", () -> {
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

            // 应该能处理异常，不崩溃
            return response.statusCode() == 200 || response.statusCode() == 500;
        });
    }

    private static void testMissingFields() {
        System.out.println("\n【测试14】缺失字段测试");
        System.out.println("----------------------------------------");

        runTest("缺少animal_id", () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("accel_x", 0.1);
            data.put("accel_y", 0.2);
            data.put("accel_z", 9.8);

            String jsonBody = mapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/posture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // 应该能处理缺失字段
            return response.statusCode() == 200 || response.statusCode() == 400;
        });

        runTest("完全空数据", () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/predict/posture"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

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
        System.out.println("  总测试数: " + totalTests);
        System.out.println("  ✅ 通过: " + passedTests);
        System.out.println("  ❌ 失败: " + failedTests);
        System.out.println("  通过率: " + String.format("%.2f%%", (double) passedTests / totalTests * 100));
        System.out.println();

        // 输出失败的测试
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

    // 函数式接口
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