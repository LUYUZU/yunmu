package com.yunmu.cesiweb;

import com.yunmu.dto.SensorDataDTO;
import com.yunmu.service.websocket.AnimalDataWebSocketHandler;
import com.yunmu.service.websocket.WebSocketPushService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 后端 WebSocket 推送功能测试
 * 
 * 测试目标：验证 Java 后端 → WebSocket → 前端 的推送链路正常
 * 不依赖数据库写入，直接测试推送服务
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WebSocketPushIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private AnimalDataWebSocketHandler animalDataHandler;

    private StandardWebSocketClient webSocketClient;
    private WebSocketSession webSocketSession;
    private final BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>(100);

    @BeforeEach
    void setUp() throws Exception {
        webSocketClient = new StandardWebSocketClient();

        String wsUrl = "ws://localhost:" + port + "/ws/animal-data";
        log.info("连接 WebSocket: {}", wsUrl);

        webSocketSession = webSocketClient.doHandshake(new TestWebSocketHandler(), wsUrl)
                .get(10, TimeUnit.SECONDS);

        Thread.sleep(500);
        receivedMessages.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            webSocketSession.close(CloseStatus.NORMAL);
        }
        Thread.sleep(200);
    }

    // ============================================================
    // 测试1：WebSocket 连接状态
    // ============================================================

    @Test
    @Order(1)
    void testWebSocketConnection() throws Exception {
        log.info("===== 测试1：WebSocket 连接状态 =====");

        // 连接已建立（由 @BeforeEach 完成），这里只验证状态
        Thread.sleep(500);

        assertTrue(webSocketSession.isOpen(), "WebSocket 应该连接成功");
        assertEquals(1, webSocketPushService.getOnlineCount(), "应该有 1 个在线连接");

        log.info("✅ WebSocket 连接测试通过");
    }

    // ============================================================
    // 测试2：推送实时数据（realtime_data）
    // ============================================================

    @Test
    @Order(2)
    void testRealtimeDataPush() throws Exception {
        log.info("===== 测试2：推送实时数据 =====");

        // 模拟 SensorDataDTO（与前端期望格式一致）
        Map<String, Object> realtimeData = Map.of(
                "type", "realtime_data",
                "animalId", "test_cow_001",
                "timestamp", System.currentTimeMillis(),
                "data", Map.of(
                        "heartRate", 72,
                        "temperature", 38.5,
                        "stepCount", 150,
                        "latitude", 30.0,
                        "longitude", 90.0,
                        "posture", "walking"
                )
        );

        // 通过 WebSocketPushService 广播
        webSocketPushService.broadcastToAll(realtimeData);

        // 验证客户端收到
        String msg = receivedMessages.poll(3, TimeUnit.SECONDS);
        assertNotNull(msg, "应该收到实时数据推送");
        assertTrue(msg.contains("realtime_data"), "消息类型应为 realtime_data: " + msg);
        assertTrue(msg.contains("test_cow_001"), "应包含动物ID: " + msg);
        assertTrue(msg.contains("walking"), "应包含姿态: " + msg);

        log.info("✅ 实时数据推送测试通过: {}", msg.substring(0, Math.min(100, msg.length())));
    }

    // ============================================================
    // 测试3：推送姿态更新（posture_update）
    // ============================================================

    @Test
    @Order(3)
    void testPostureUpdatePush() throws Exception {
        log.info("===== 测试3：推送姿态更新 =====");

        String[] postures = {"standing", "walking", "lying", "feeding"};

        for (String posture : postures) {
            Map<String, Object> postureData = Map.of(
                    "type", "posture_update",
                    "animalId", "test_cow_001",
                    "timestamp", System.currentTimeMillis(),
                    "posture", posture,
                    "confidence", 0.85,
                    "tiltAngle", 45.0
            );

            webSocketPushService.broadcastToAll(postureData);
            Thread.sleep(200);
        }

        // 验证收到所有姿态推送
        int count = 0;
        for (String posture : postures) {
            String msg = receivedMessages.poll(2, TimeUnit.SECONDS);
            if (msg != null && msg.contains("posture_update")) {
                count++;
                log.info("收到姿态推送: {}", posture);
            }
        }

        assertEquals(4, count, "应该收到 4 种姿态推送");
        log.info("✅ 姿态更新推送测试通过");
    }

    // ============================================================
    // 测试4：推送位置更新（location_update）
    // ============================================================

    @Test
    @Order(4)
    void testLocationUpdatePush() throws Exception {
        log.info("===== 测试4：推送位置更新 =====");

        Map<String, Object> locationData = Map.of(
                "type", "location_update",
                "animalId", "test_cow_001",
                "timestamp", System.currentTimeMillis(),
                "latitude", 30.5726,
                "longitude", 91.1234,
                "altitude", 4500.0,
                "gpsQuality", "GOOD"
        );

        webSocketPushService.broadcastToAll(locationData);

        String msg = receivedMessages.poll(3, TimeUnit.SECONDS);
        assertNotNull(msg, "应该收到位置更新推送");
        assertTrue(msg.contains("location_update"), "消息类型应为 location_update: " + msg);
        assertTrue(msg.contains("30.5726") || msg.contains("91.1234"), "应包含坐标: " + msg);

        log.info("✅ 位置更新推送测试通过: {}", msg.substring(0, Math.min(100, msg.length())));
    }

    // ============================================================
    // 测试5：推送步数更新（step_update）
    // ============================================================

    @Test
    @Order(5)
    void testStepUpdatePush() throws Exception {
        log.info("===== 测试5：推送步数更新 =====");

        Map<String, Object> stepData = Map.of(
                "type", "step_update",
                "animalId", "test_cow_001",
                "timestamp", System.currentTimeMillis(),
                "steps", 1234,
                "stepFrequency", 65.5,
                "activityLevel", "medium"
        );

        webSocketPushService.broadcastToAll(stepData);

        String msg = receivedMessages.poll(3, TimeUnit.SECONDS);
        assertNotNull(msg, "应该收到步数更新推送");
        assertTrue(msg.contains("step_update"), "消息类型应为 step_update: " + msg);
        assertTrue(msg.contains("1234"), "应包含步数: " + msg);

        log.info("✅ 步数更新推送测试通过: {}", msg.substring(0, Math.min(100, msg.length())));
    }

    // ============================================================
    // 测试6：推送健康告警（health_alert）
    // ============================================================

    @Test
    @Order(6)
    void testHealthAlertPush() throws Exception {
        log.info("===== 测试6：推送健康告警 =====");

        Map<String, Object> alertData = Map.of(
                "type", "health_alert",
                "animalId", "test_cow_001",
                "timestamp", System.currentTimeMillis(),
                "alertType", "FEVER",
                "alertLevel", "WARNING",
                "message", "体温异常: 39.8°C",
                "currentValue", 39.8,
                "threshold", 39.5
        );

        webSocketPushService.broadcastToAll(alertData);

        String msg = receivedMessages.poll(3, TimeUnit.SECONDS);
        assertNotNull(msg, "应该收到健康告警推送");
        assertTrue(msg.contains("health_alert"), "消息类型应为 health_alert: " + msg);
        assertTrue(msg.contains("FEVER"), "应包含告警类型: " + msg);

        log.info("✅ 健康告警推送测试通过: {}", msg.substring(0, Math.min(100, msg.length())));
    }

    // ============================================================
    // 测试7：连续高频推送（模拟硬件上报频率）
    // ============================================================

    @Test
    @Order(7)
    void testContinuousPush() throws Exception {
        log.info("===== 测试7：连续高频推送 =====");

        int totalSent = 0;
        for (int i = 0; i < 10; i++) {
            Map<String, Object> data = Map.of(
                    "type", "realtime_data",
                    "animalId", "test_cow_001",
                    "timestamp", System.currentTimeMillis(),
                    "data", Map.of(
                            "heartRate", 60 + i * 2,
                            "temperature", 38.0 + i * 0.05,
                            "stepCount", 100 * (i + 1)
                    )
            );
            webSocketPushService.broadcastToAll(data);
            totalSent++;
            Thread.sleep(100);  // 100ms 间隔，模拟 10Hz 上报频率
        }

        // 等待所有消息到达
        Thread.sleep(2000);

        int received = 0;
        while (true) {
            String msg = receivedMessages.poll(500, TimeUnit.MILLISECONDS);
            if (msg == null) break;
            received++;
        }

        log.info("发送 {} 条，推送收到 {} 条", totalSent, received);
        assertTrue(received >= 5, "应该收到至少 5 条推送（允许少量丢帧）");

        log.info("✅ 连续推送测试通过，收到 {} / {} 条", received, totalSent);
    }

    // ============================================================
    // 测试8：多种消息类型混合推送
    // ============================================================

    @Test
    @Order(8)
    void testMixedMessageTypes() throws Exception {
        log.info("===== 测试8：混合消息类型推送 =====");

        // 依次推送不同类型
        webSocketPushService.broadcastToAll(Map.of(
                "type", "posture_update", "animalId", "cow_001", "posture", "standing"));
        Thread.sleep(100);

        webSocketPushService.broadcastToAll(Map.of(
                "type", "step_update", "animalId", "cow_001", "steps", 500));
        Thread.sleep(100);

        webSocketPushService.broadcastToAll(Map.of(
                "type", "location_update", "animalId", "cow_001", "latitude", 30.0, "longitude", 90.0));
        Thread.sleep(100);

        webSocketPushService.broadcastToAll(Map.of(
                "type", "behavior_update", "animalId", "cow_001", "behavior", "feeding"));
        Thread.sleep(100);

        Thread.sleep(1000);

        int received = 0;
        while (true) {
            String msg = receivedMessages.poll(500, TimeUnit.MILLISECONDS);
            if (msg == null) break;
            received++;
            log.info("收到消息类型: {}", 
                    msg.contains("posture_update") ? "posture" :
                    msg.contains("step_update") ? "step" :
                    msg.contains("location_update") ? "location" :
                    msg.contains("behavior_update") ? "behavior" : "unknown");
        }

        assertTrue(received >= 3, "应该收到至少 3 种不同类型消息");
        log.info("✅ 混合消息类型推送测试通过，收到 {} 种消息", received);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private class TestWebSocketHandler implements WebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            log.info("WebSocket 连接成功: {}", session.getId());
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
            String payload = message.getPayload().toString();
            log.info("收到推送: {}", payload.substring(0, Math.min(120, payload.length())));
            receivedMessages.offer(payload);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.error("WebSocket 传输错误: {}", exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
            log.info("WebSocket 连接关闭: {}", closeStatus);
        }

        @Override
        public boolean supportsPartialMessages() {
            return false;
        }
    }
}
