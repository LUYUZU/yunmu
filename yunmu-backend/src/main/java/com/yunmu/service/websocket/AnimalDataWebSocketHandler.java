package com.yunmu.service.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunmu.dto.SensorDataDTO;
import com.yunmu.service.DataCollectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class AnimalDataWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebSocketPushService webSocketPushService;

    // 注意：这里不再直接注入 DataCollectionService，避免循环依赖
    // 如果需要处理消息，可以通过其他方式

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        webSocketPushService.addSession(sessionId, session);
        log.info("WebSocket连接建立: {}", sessionId);

        // 发送连接确认
        webSocketPushService.sendMessage(session, Map.of(
                "type", "connection_established",
                "message", "WebSocket连接成功",
                "timestamp", System.currentTimeMillis(),
                "sessionId", sessionId
        ));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        try {
            String payload = message.getPayload().toString();
            log.debug("收到WebSocket消息: {}", payload);

            Map<String, Object> request = objectMapper.readValue(payload, Map.class);
            String action = (String) request.get("action");

            switch (action) {
                case "subscribe":
                    handleSubscribe(session, request);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session);
                    break;
                case "ping":
                    handlePing(session);
                    break;
                default:
                    // 其他消息处理
                    webSocketPushService.sendMessage(session, Map.of(
                            "type", "response",
                            "message", "收到消息",
                            "timestamp", System.currentTimeMillis()
                    ));
                    break;
            }

        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
            webSocketPushService.sendMessage(session, Map.of(
                    "type", "error",
                    "message", "数据处理失败: " + e.getMessage()
            ));
        }
    }

    private void handleSubscribe(WebSocketSession session, Map<String, Object> request) {
        String animalId = (String) request.get("animalId");
        if (animalId != null && !animalId.isEmpty()) {
            webSocketPushService.addSubscription(session.getId(), animalId);
            log.info("WebSocket订阅: session={}, animalId={}", session.getId(), animalId);

            webSocketPushService.sendMessage(session, Map.of(
                    "type", "subscribed",
                    "animalId", animalId,
                    "message", "订阅成功"
            ));
        }
    }

    private void handleUnsubscribe(WebSocketSession session) {
        webSocketPushService.removeSubscription(session.getId());
        log.info("WebSocket取消订阅: session={}", session.getId());

        webSocketPushService.sendMessage(session, Map.of(
                "type", "unsubscribed",
                "message", "取消订阅成功"
        ));
    }

    private void handlePing(WebSocketSession session) {
        webSocketPushService.sendMessage(session, Map.of(
                "type", "pong",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误", exception);
        webSocketPushService.removeSession(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        webSocketPushService.removeSession(session.getId());
        log.info("WebSocket连接关闭: {}, 状态: {}", session.getId(), closeStatus);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 广播消息（委托给 WebSocketPushService）
     */
    public void broadcastToAll(Object message) {
        webSocketPushService.broadcastToAll(message);
    }

    /**
     * 发送给订阅者（委托给 WebSocketPushService）
     */
    public void sendToSubscribers(String animalId, Object message) {
        webSocketPushService.sendToSubscribers(animalId, message);
    }
}