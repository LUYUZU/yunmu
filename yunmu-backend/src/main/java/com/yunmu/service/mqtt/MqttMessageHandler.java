package com.yunmu.service.mqtt;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONException;
import com.yunmu.dto.SensorDataDTO;
import com.yunmu.entity.Animal;
import com.yunmu.repository.AnimalRepository;
import com.yunmu.service.DataCollectionService;
import com.yunmu.utils.GpsUtils;
import com.yunmu.utils.JsonUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class MqttMessageHandler {

    @Autowired
    private DataCollectionService dataCollectionService;

    // 使用全局 RestTemplate（已配置超时与 ML 服务鉴权头），不再 new 出裸 RestTemplate
    @Autowired
    private RestTemplate restTemplate;

    // 动物档案仓库：按项圈号(code)反查当前绑定动物
    @Autowired
    private AnimalRepository animalRepository;

    // Python 服务地址（可配置，默认指向本机 5000 端口）
    @Value("${yunmu.python-service.url:http://localhost:5000}/api/device/data")
    private String pythonApiUrl;

    // Java 回调地址（Python 处理完毕后回调此地址通知结果，可配置）
    @Value("${yunmu.ml.callback-url:http://127.0.0.1:8080/api/ml/callback}")
    private String javaCallbackUrl;

    // 缺陷 D 修复：Python 调用超时（毫秒，可配置，默认 1500ms）。
    // 超时后放弃本次同步结果并降级到本地逻辑，保证 MQTT 吞吐不被慢请求拖死
    @Value("${yunmu.python-service.call-timeout-ms:1500}")
    private long pythonCallTimeoutMs;

    /**
     * 有界处理线程池：将 MQTT 回调线程从“同步等待 Python HTTP 响应”中解放出来。
     * 队列满时 CallerRunsPolicy 把压力回压到 MQTT 回调线程（形成天然背压），
     * 避免消息无限堆积导致 OOM。
     */
    private final ThreadPoolExecutor mqttExecutor;

    /**
     * 缺陷 D 修复：Python 调用专用线程池。
     * MQTT 业务线程不再直接同步等待 Python 返回，而是提交到本线程池异步执行，
     * 业务侧用 CompletableFuture.get(timeout) 做可配置超时降级，
     * 慢 Python 请求只占用独立线程，不再阻塞整条 MQTT 处理链路。
     */
    private final ThreadPoolExecutor pythonCallExecutor;

    public MqttMessageHandler() {
        AtomicInteger seq = new AtomicInteger(1);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "mqtt-handler-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        this.mqttExecutor = new ThreadPoolExecutor(
                4, 8,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy());

        AtomicInteger seq2 = new AtomicInteger(1);
        ThreadFactory factory2 = r -> {
            Thread t = new Thread(r, "python-call-" + seq2.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        this.pythonCallExecutor = new ThreadPoolExecutor(
                2, 4,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                factory2,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PreDestroy
    public void shutdown() {
        mqttExecutor.shutdown();
        pythonCallExecutor.shutdown();
    }

    // ====== 设备编号(code) / 动物ID(device_id) ======
    // code = 设备编号，device_id = 动物ID（设备 payload 直接携带）
    // 不再需要硬编码映射表

    /**
     * 从 payload 提取设备编号(code)和动物ID(animalId)
     * 规则（项圈可换绑）：
     * 1. deviceId = code / deviceId / device_id（项圈号）
     * 2. animalId 优先取 payload 直接携带的 device_id / animal_id（旧协议兼容）
     * 3. 上报只有 code 时，通过 findByDeviceCode 从动物档案反查绑定动物
     * 4. 查不到绑定关系时 animalId 返回 null（调用方仅记录设备在线状态，不写业务表）
     * @return [deviceId, animalId]，animalId 可能为 null
     */
    private String[] extractDeviceAndAnimal(JSONObject data) {
        String deviceId = data.getString("code");
        if (deviceId == null) deviceId = data.getString("deviceId");
        if (deviceId == null) deviceId = data.getString("device_id");
        String animalId = data.getString("device_id");
        if (animalId == null) animalId = data.getString("animal_id");
        // 上报只有 code：按项圈号从动物档案查绑定（禁止再把 code 直接当作 animalId）
        if (animalId == null && deviceId != null && !deviceId.isEmpty()) {
            try {
                Optional<Animal> animal = animalRepository.findByDeviceCode(deviceId);
                if (animal.isPresent()) {
                    animalId = animal.get().getAnimalId();
                }
            } catch (Exception e) {
                log.warn("按项圈号 {} 查询绑定动物失败: {}", deviceId, e.getMessage());
            }
        }
        return new String[]{deviceId, animalId};
    }

    /** @deprecated 兜底兼容旧路径 */
    @Deprecated
    private String getAnimalId(String deviceId) {
        return deviceId;
    }

    /**
     * 处理MQTT消息（主入口）
     */
    public void handleMessage(String topic, String payload) {
        // 快速卸载：MQTT 回调线程只负责入队，实际处理在独立线程池中执行（有界 + 背压）
        mqttExecutor.execute(() -> handleMessageInternal(topic, payload));
    }

    /**
     * 同步处理 MQTT 消息（严格保序）。
     *
     * <p><b>为什么需要这个入口</b>：{@link #handleMessage} 走的是异步线程池，其队列容量为 200
     * 且拒绝策略为 {@link ThreadPoolExecutor.CallerRunsPolicy}。突发注入（典型场景：历史回放
     * 在数十秒内灌入上千个包）会把队列打满，此时「最新的包」会直接在调用线程上执行，
     * 插队到最多 200 个「更早的包」之前。
     *
     * <p>这对<b>有序语义</b>的信号是致命的：协议字段 {@code bushu} 是「通电后累计步数」，
     * 下游 Python 靠「本次累计 − 上次累计」求增量。一旦累计值乱序到达，
     * 先到的第 1 包（累计≈0）会建立基线，随后插队进来的第 220 包（累计≈16000）
     * 就被算成「一个采样间隔走了 16000 步」的幻影值，直接污染 step_counts。
     *
     * <p>因此凡是会突发注入、且数据带累计语义的调用方（历史回放）都必须走本入口，
     * 以保证同一设备内「生成顺序 = 处理顺序」。实时模拟（间隔 5 秒、3 台设备）无突发，
     * 继续走异步入口以保持定时器不被 Python 往返阻塞。
     */
    public void handleMessageSync(String topic, String payload) {
        handleMessageInternal(topic, payload);
    }

    /**
     * 实际的消息处理逻辑（在线程池中执行）
     */
    private void handleMessageInternal(String topic, String payload) {
        log.debug("收到MQTT消息, topic={}, payload长度={}", topic, payload != null ? payload.length() : 0);

        try {
            // 1. 验证消息有效性
            if (!validateMessage(payload)) {
                log.warn("消息验证失败，跳过处理");
                return;
            }

            // 2. 解析JSON
            JSONObject jsonData;
            try {
                jsonData = JSON.parseObject(payload);
            } catch (JSONException e) {
                log.error("JSON解析失败: {}", e.getMessage());
                log.error("原始消息: {}", payload);
                return;
            }

            // 3. 解析设备编号与动物ID（项圈可换绑：上报只有 code 时通过档案反查绑定）
            String[] ids = extractDeviceAndAnimal(jsonData);
            String deviceId = ids[0] != null ? ids[0] : topic;
            String animalId = ids[1];

            // 4. 心跳消息：仅记录设备在线状态（电量/信号），不写业务表
            if (topic.contains("heartbeat")) {
                handleHeartbeatData(jsonData);
                return;
            }

            // 5. 设备未绑定动物：仅记录在线状态（可用已有心跳/电量/信号数据），
            //    不调用 Python、不写入业务表、不抛异常
            if (animalId == null || animalId.isEmpty()) {
                Integer battery = getIntegerValue(jsonData, "bat", "battery", "battery_level");
                Integer signal = getIntegerValue(jsonData, "signal", "signal_strength", "rssi");
                log.info("设备 {} 未绑定动物，仅记录在线状态 - 电量: {}%, 信号: {}", deviceId, battery, signal);
                return;
            }

            // 6. 调用Python API处理数据（同步，等 Python ML 结果）
            JSONObject mlResult = callPythonApi(topic, jsonData);

            // 7. 继续原有的业务逻辑处理
            if (mlResult != null) {
                handleSensorDataWithMlResult(topic, jsonData, mlResult);
            } else {
                log.warn("Python ML 返回空，降级到原有逻辑处理");
                handleMixedData(jsonData);
            }

        } catch (Exception e) {
            log.error("处理MQTT消息时发生异常", e);
        }
    }

    /**
     * 调用Python API处理数据（异步执行 + 可配置超时降级 + 回调通知）
     *
     * 缺陷 D 修复说明：
     * - 构造请求数据（buildPythonData）仍在 MQTT 业务线程完成（纯内存操作，快）；
     * - 真正的 HTTP 调用（doCallPythonApi）提交到 pythonCallExecutor 异步执行，
     *   业务线程通过 CompletableFuture.get(timeout) 等待；
     * - 超过 pythonCallTimeoutMs 未返回时放弃本次结果并降级（返回 null 走本地逻辑），
     *   慢 Python 请求不再长期占住 MQTT 处理线程，吞吐不再被同步阻塞拖死；
     * - 异常/告警链路不受影响：Python 仍通过 callback_url 回调 /api/ml/callback 推送。
     *
     * @return Python 返回的 ML 结果（JSONObject），超时/失败返回 null
     */
    private JSONObject callPythonApi(String topic, JSONObject data) {
        Map<String, Object> pythonData = buildPythonData(topic, data);
        if (pythonData == null) {
            return null;
        }

        CompletableFuture<JSONObject> future = CompletableFuture.supplyAsync(
                () -> doCallPythonApi(pythonData),
                pythonCallExecutor);

        try {
            JSONObject result = future.get(pythonCallTimeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Python API异步调用完成, topic={}", topic);
            return result;
        } catch (TimeoutException e) {
            log.warn("调用Python API超时（{}ms），降级到本地逻辑处理, topic={}", pythonCallTimeoutMs, topic);
            // 必须用 cancel(false) 而不是 cancel(true)：
            // cancel(true) 会中断正在执行 HTTP 请求的线程，把 RestTemplate 复用的 keep-alive
            // 长连接留在「已发出请求但未读完响应」的状态；该连接回到池里后被下一个请求复用，
            // 就会立刻抛出 "Unexpected end of file from server"。
            // 实测这会形成雪崩：一次超时之后所有请求连续失败（曾出现 638 次超时 + 6135 条 ERROR，
            // 回放数据 3/4 丢失）。cancel(false) 只放弃等待结果、不打断 IO，连接保持健康；
            // 对于还没开始执行的任务，cancel(false) 仍会把它从队列里摘掉（不产生无谓请求）。
            future.cancel(false);
            return null;
        } catch (Exception e) {
            log.error("调用Python API异步执行失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构造 Python 请求数据（同步、纯内存操作）
     */
    private Map<String, Object> buildPythonData(String topic, JSONObject data) {
        try {
            Map<String, Object> pythonData = new HashMap<>();
            pythonData.put("topic", topic);

            String[] ids = extractDeviceAndAnimal(data);
            String deviceId = ids[0] != null ? ids[0] : topic;
            String animalId = ids[1];
            pythonData.put("device_id", deviceId);
            pythonData.put("animal_id", animalId);

            // 提取经纬度（WGS84）
            Object jd = data.get("JD");
            Object wd = data.get("WD");
            if (jd != null && wd != null) {
                pythonData.put("longitude", Double.parseDouble(jd.toString()));
                pythonData.put("latitude", Double.parseDouble(wd.toString()));
            }

            // 提取移动状态 (0=静止, 1=移动, 2=跑)
            Object move = data.get("move");
            pythonData.put("move", move != null ? Integer.parseInt(move.toString()) : 0);

            // 提取步数
            Object bushu = data.get("bushu");
            pythonData.put("steps", bushu != null ? Integer.parseInt(bushu.toString()) : 0);

            // 提取计数器
            Object counter = data.get("counter");
            if (counter != null) {
                pythonData.put("counter", Integer.parseInt(counter.toString()));
            }

            // 提取加速度原始数据（Python 需要这些做姿态识别）
            pythonData.put("accel_x", data.get("ax"));
            pythonData.put("accel_y", data.get("ay"));
            pythonData.put("accel_z", data.get("az"));
            pythonData.put("gyro_x", data.get("gx"));
            pythonData.put("gyro_y", data.get("gy"));
            pythonData.put("gyro_z", data.get("gz"));

            // 提取心率、体温、电量、信号强度
            pythonData.put("heart_rate", data.get("heart_rate"));
            pythonData.put("temperature", data.get("temp"));
            pythonData.put("battery", data.get("bat"));
            pythonData.put("signal_strength", data.get("signal"));

            // 时间戳（优先用传感器上报的ts，毫秒；否则用Java系统时间）
            Object tsObj = data.get("ts");
            if (tsObj != null) {
                pythonData.put("timestamp", Long.parseLong(tsObj.toString()) / 1000);
            } else {
                pythonData.put("timestamp", System.currentTimeMillis() / 1000);
            }

            // ★ 关键：告诉 Python 处理完后回调哪个 URL
            pythonData.put("callback_url", javaCallbackUrl);

            log.debug("调用Python API: {}", pythonData);
            return pythonData;
        } catch (Exception e) {
            log.error("构造Python请求数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 实际发起 Python HTTP 调用（在 pythonCallExecutor 线程池中执行）
     */
    private JSONObject doCallPythonApi(Map<String, Object> pythonData) {
        try {
            String response = restTemplate.postForObject(
                    pythonApiUrl,
                    pythonData,
                    String.class
            );
            log.debug("Python API响应: {}", response);

            if (response != null && !response.isEmpty()) {
                return JSON.parseObject(response);
            }
        } catch (Exception e) {
            log.error("调用Python API失败: {}", e.getMessage());
        }
        return null;
    }

    // ========== 以下是原有的方法，保持不变 ==========

    private boolean validateMessage(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            log.warn("消息内容为空");
            return false;
        }
        if (payload.length() > 10240) {
            log.warn("消息过大: {} 字符", payload.length());
            return false;
        }
        return true;
    }

    private void handleGpsData(JSONObject data) {
        try {
            String[] ids = extractDeviceAndAnimal(data);
            String deviceId = ids[0], animalId = ids[1];
            if (animalId == null || animalId.isEmpty()) {
                log.info("设备 {} 未绑定动物，跳过GPS业务写入", deviceId);
                return;
            }
            String latStr = data.getString("WD");
            String lngStr = data.getString("JD");
            if (latStr == null) latStr = data.getString("lat");
            if (lngStr == null) lngStr = data.getString("lng");
            if (latStr == null || lngStr == null) {
                log.warn("GPS数据缺少坐标信息");
                return;
            }
            Double wgs84Lat = GpsUtils.degreeMinuteToDecimal(latStr);
            Double wgs84Lng = GpsUtils.degreeMinuteToDecimal(lngStr);
            if (wgs84Lat == null || wgs84Lng == null) {
                try { wgs84Lat = Double.parseDouble(latStr); wgs84Lng = Double.parseDouble(lngStr); } catch (Exception ignored) {}
            }
            if (wgs84Lat == null || wgs84Lng == null) {
                log.warn("坐标转换失败 - lat: {}, lng: {}", latStr, lngStr);
                return;
            }
            double[] gcj = GpsUtils.wgs84ToGcj02(wgs84Lng, wgs84Lat);
            SensorDataDTO dto = new SensorDataDTO();
            dto.setAnimalId(animalId);
            dto.setDeviceId(deviceId);
            dto.setLatitude(gcj[1]);
            dto.setLongitude(gcj[0]);
            dto.setTimestamp(parseTimestamp(data));
            dto.setBatteryLevel(getIntegerValue(data, "bat", "battery", "battery_level"));
            dto.setSignalStrength(getIntegerValue(data, "signal", "signal_strength", "rssi"));
            dataCollectionService.processSensorData(dto);
            log.info("GPS数据处理完成 - 动物ID: {}, 坐标: ({}, {})", animalId, gcj[0], gcj[1]);
        } catch (Exception e) {
            log.error("处理GPS数据失败", e);
        }
    }

    private void handleSensorData(JSONObject data) {
        try {
            String[] ids = extractDeviceAndAnimal(data);
            String deviceId = ids[0], animalId = ids[1];
            if (animalId == null || animalId.isEmpty()) {
                log.info("设备 {} 未绑定动物，跳过传感器业务写入", deviceId);
                return;
            }
            SensorDataDTO dto = new SensorDataDTO();
            dto.setAnimalId(animalId);
            dto.setDeviceId(deviceId);
            dto.setTimestamp(parseTimestamp(data));
            dto.setAccelX(getDoubleValue(data, "ax", "accelX", "accel_x", "x", "accX"));
            dto.setAccelY(getDoubleValue(data, "ay", "accelY", "accel_y", "y", "accY"));
            dto.setAccelZ(getDoubleValue(data, "az", "accelZ", "accel_z", "z", "accZ"));
            dto.setGyroX(getDoubleValue(data, "gx", "gyroX", "gyro_x"));
            dto.setGyroY(getDoubleValue(data, "gy", "gyroY", "gyro_y"));
            dto.setGyroZ(getDoubleValue(data, "gz", "gyroZ", "gyro_z"));
            dto.setHeartRate(getIntegerValue(data, "heart_rate", "heartRate", "hr", "bpm"));
            dto.setTemperature(getDoubleValue(data, "temp", "temperature", "tmp"));
            dto.setStepCount(getIntegerValue(data, "bushu", "stepCount", "step_count", "steps"));
            dto.setMoveStatus(getIntegerValue(data, "move", "move_state"));
            dto.setSoundLevel(getDoubleValue(data, "soundLevel", "sound_level", "db"));
            dto.setSoundFrequency(getDoubleValue(data, "soundFrequency", "sound_frequency", "freq"));
            dto.setBatteryLevel(getIntegerValue(data, "bat", "battery", "battery_level"));
            dto.setSignalStrength(getIntegerValue(data, "signal", "signal_strength", "rssi"));
            dataCollectionService.processSensorData(dto);
            log.info("传感器数据处理完成 - 动物ID: {}, 心率: {}, 温度: {}, 步数: {}",
                    animalId, dto.getHeartRate(), dto.getTemperature(), dto.getStepCount());
        } catch (Exception e) {
            log.error("处理传感器数据失败", e);
        }
    }

    private void handleHeartbeatData(JSONObject data) {
        try {
            String[] ids = extractDeviceAndAnimal(data);
            String deviceId = ids[0];
            Integer battery = getIntegerValue(data, "bat", "battery", "battery_level");
            Integer signal = getIntegerValue(data, "signal", "signal_strength", "rssi");
            String firmware = data.getString("firmware");
            log.info("设备心跳 - DeviceId: {}, 电量: {}%, 信号: {}, 固件: {}",
                    deviceId, battery, signal, firmware);
        } catch (Exception e) {
            log.error("处理心跳数据失败", e);
        }
    }

    private void handleMixedData(JSONObject data) {
        try {
            String[] ids = extractDeviceAndAnimal(data);
            String deviceId = ids[0], animalId = ids[1];
            if (animalId == null || animalId.isEmpty()) {
                log.info("设备 {} 未绑定动物，跳过混合数据业务写入", deviceId);
                return;
            }
            SensorDataDTO dto = new SensorDataDTO();
            dto.setAnimalId(animalId);
            dto.setDeviceId(deviceId);
            dto.setTimestamp(parseTimestamp(data));
            String latStr = data.getString("WD");
            String lngStr = data.getString("JD");
            if (latStr == null) latStr = data.getString("lat");
            if (lngStr == null) lngStr = data.getString("lng");
            if (latStr != null && lngStr != null) {
                Double wgs84Lat = GpsUtils.degreeMinuteToDecimal(latStr);
                Double wgs84Lng = GpsUtils.degreeMinuteToDecimal(lngStr);
                if (wgs84Lat == null || wgs84Lng == null) {
                    try { wgs84Lat = Double.parseDouble(latStr); wgs84Lng = Double.parseDouble(lngStr); } catch (Exception ignored) {}
                }
                if (wgs84Lat != null && wgs84Lng != null) {
                    double[] gcj = GpsUtils.wgs84ToGcj02(wgs84Lng, wgs84Lat);
                    dto.setLatitude(gcj[1]);
                    dto.setLongitude(gcj[0]);
                }
            }
            dto.setAccelX(getDoubleValue(data, "ax", "accelX", "accel_x", "x", "accX"));
            dto.setAccelY(getDoubleValue(data, "ay", "accelY", "accel_y", "y", "accY"));
            dto.setAccelZ(getDoubleValue(data, "az", "accelZ", "accel_z", "z", "accZ"));
            dto.setGyroX(getDoubleValue(data, "gx", "gyroX", "gyro_x"));
            dto.setGyroY(getDoubleValue(data, "gy", "gyroY", "gyro_y"));
            dto.setGyroZ(getDoubleValue(data, "gz", "gyroZ", "gyro_z"));
            dto.setHeartRate(getIntegerValue(data, "heart_rate", "heartRate", "hr", "bpm"));
            dto.setTemperature(getDoubleValue(data, "temp", "temperature", "tmp"));
            dto.setStepCount(getIntegerValue(data, "bushu", "stepCount", "step_count", "steps"));
            dto.setMoveStatus(getIntegerValue(data, "move", "move_state"));
            dto.setSoundLevel(getDoubleValue(data, "soundLevel", "sound_level", "db"));
            dto.setSoundFrequency(getDoubleValue(data, "soundFrequency", "sound_frequency", "freq"));
            dto.setBatteryLevel(getIntegerValue(data, "bat", "battery", "battery_level"));
            dto.setSignalStrength(getIntegerValue(data, "signal", "signal_strength", "rssi"));
            dataCollectionService.processSensorData(dto);
            log.info("混合数据处理完成 - 动物ID: {}", animalId);
        } catch (Exception e) {
            log.error("处理混合数据失败", e);
        }
    }

    /**
     * 从 JSONObject 中提取时间戳（兼容 ts 毫秒 / timestamp 字符串）
     */
    private LocalDateTime parseTimestamp(JSONObject data) {
        if (data == null) return LocalDateTime.now();
        // 优先读 ts（设备上报的毫秒时间戳）
        Object tsObj = data.get("ts");
        if (tsObj != null) {
            try {
                long ms = Long.parseLong(tsObj.toString());
                return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(ms),
                    ZoneOffset.ofHours(8));
            } catch (Exception ignored) {}
        }
        // 其次读 timestamp（字符串）
        return parseTimestamp(data.getString("timestamp"));
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            if (timestampStr.contains("T")) {
                return LocalDateTime.parse(timestampStr);
            } else if (timestampStr.contains(" ")) {
                return LocalDateTime.parse(timestampStr,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                return LocalDateTime.now();
            }
        } catch (DateTimeParseException e) {
            log.warn("时间戳解析失败: {}, 使用当前时间", timestampStr);
            return LocalDateTime.now();
        }
    }

    private Double getDoubleValue(JSONObject data, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = data.get(fieldName);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                } else if (value instanceof String) {
                    try {
                        return Double.parseDouble((String) value);
                    } catch (NumberFormatException e) {
                        log.warn("无法解析字段 {} 的值: {}", fieldName, value);
                    }
                }
            }
        }
        return null;
    }

    private Integer getIntegerValue(JSONObject data, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = data.get(fieldName);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                } else if (value instanceof String) {
                    try {
                        return Integer.parseInt((String) value);
                    } catch (NumberFormatException e) {
                        log.warn("无法解析字段 {} 的值: {}", fieldName, value);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 使用 Python ML 结果处理传感器数据
     * 跳过人脸姿态识别（Python 已处理），避免重复
     */
    private void handleSensorDataWithMlResult(String topic, JSONObject data, JSONObject mlResult) {
        try {
            String[] ids = extractDeviceAndAnimal(data);
            String deviceId = ids[0], animalId = ids[1];
            if (deviceId == null) deviceId = topic;
            if (animalId == null || animalId.isEmpty()) {
                log.info("设备 {} 未绑定动物，跳过ML业务写入", deviceId);
                return;
            }

            SensorDataDTO dto = new SensorDataDTO();
            dto.setAnimalId(animalId);
            dto.setDeviceId(deviceId);
            dto.setTimestamp(parseTimestamp(data));

            // GPS 坐标（MQTT 原始字段: JD=经度, WD=纬度）
            String latStr = data.getString("WD");
            String lngStr = data.getString("JD");
            if (latStr == null) latStr = data.getString("lat");
            if (lngStr == null) lngStr = data.getString("lng");
            if (latStr != null && lngStr != null) {
                Double wgs84Lat = GpsUtils.degreeMinuteToDecimal(latStr);
                Double wgs84Lng = GpsUtils.degreeMinuteToDecimal(lngStr);
                if (wgs84Lat == null || wgs84Lng == null) {
                    // 如果 degreeMinute 转换失败，尝试直接作为十进制度数
                    try { wgs84Lat = Double.parseDouble(latStr); wgs84Lng = Double.parseDouble(lngStr); } catch (Exception ignored) {}
                }
                if (wgs84Lat != null && wgs84Lng != null) {
                    double[] gcj = GpsUtils.wgs84ToGcj02(wgs84Lng, wgs84Lat);
                    dto.setLatitude(gcj[1]);
                    dto.setLongitude(gcj[0]);
                }
            }

            // 原始加速度数据
            // 加速度（兼容: ax/ay/az, accelX/accel_x, x）
            dto.setAccelX(getDoubleValue(data, "ax", "accelX", "accel_x", "x", "accX"));
            dto.setAccelY(getDoubleValue(data, "ay", "accelY", "accel_y", "y", "accY"));
            dto.setAccelZ(getDoubleValue(data, "az", "accelZ", "accel_z", "z", "accZ"));
            // 陀螺仪（兼容: gx/gy/gz, gyroX/gyro_x）
            dto.setGyroX(getDoubleValue(data, "gx", "gyroX", "gyro_x"));
            dto.setGyroY(getDoubleValue(data, "gy", "gyroY", "gyro_y"));
            dto.setGyroZ(getDoubleValue(data, "gz", "gyroZ", "gyro_z"));
            // 心率、体温（兼容中英文字段名）
            dto.setHeartRate(getIntegerValue(data, "heart_rate", "heartRate", "hr", "bpm"));
            dto.setTemperature(getDoubleValue(data, "temp", "temperature", "tmp"));
            // 步数（兼容: bushu, stepCount, steps）
            dto.setStepCount(getIntegerValue(data, "bushu", "stepCount", "step_count", "steps"));
            // 移动状态
            dto.setMoveStatus(getIntegerValue(data, "move", "move_state"));
            // 电量、信号
            dto.setBatteryLevel(getIntegerValue(data, "bat", "battery", "battery_level"));
            dto.setSignalStrength(getIntegerValue(data, "signal", "signal_strength", "rssi"));

            // ★ 关键：传入 Python ML 结果，跳过 Java 本地姿态识别
            dataCollectionService.processSensorDataWithMlResult(dto, mlResult);

            log.info("MQTT数据处理完成（含Python ML） - 动物ID: {}", animalId);

        } catch (Exception e) {
            log.error("处理MQTT数据（含ML）失败", e);
        }
    }
}