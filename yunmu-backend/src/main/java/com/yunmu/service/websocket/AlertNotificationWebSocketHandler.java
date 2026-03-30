package com.yunmu.service.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AlertNotificationWebSocketHandler implements WebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("预警通知WebSocket连接建立: {}", sessionId);

        sendMessage(session, Map.of(
                "type", "connection_established",
                "message", "预警通知连接成功",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        try {
            String payload = message.getPayload().toString();
            log.debug("预警通知收到消息: {}", payload);

            // 处理确认预警等操作

        } catch (Exception e) {
            log.error("处理预警通知消息失败", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("预警通知WebSocket传输错误", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        sessions.remove(session.getId());
        log.info("预警通知WebSocket连接关闭: {}, 状态: {}", session.getId(), closeStatus);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }

    public void broadcastAlert(Object alert) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(alert);
        } catch (Exception e) {
            log.error("序列化预警数据失败", e);
            return;
        }

        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            } catch (IOException e) {
                log.error("发送预警通知失败", e);
            }
        });
    }
}