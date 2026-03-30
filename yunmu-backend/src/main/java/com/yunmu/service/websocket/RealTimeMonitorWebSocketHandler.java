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
public class RealTimeMonitorWebSocketHandler implements WebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("实时监控WebSocket连接建立: {}", sessionId);

        sendMessage(session, Map.of(
                "type", "connection_established",
                "message", "实时监控连接成功",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        try {
            String payload = message.getPayload().toString();
            log.debug("实时监控收到消息: {}", payload);

            // 处理订阅请求等

        } catch (Exception e) {
            log.error("处理实时监控消息失败", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("实时监控WebSocket传输错误", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        sessions.remove(session.getId());
        log.info("实时监控WebSocket连接关闭: {}, 状态: {}", session.getId(), closeStatus);
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

    public void broadcastMonitorData(Object data) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("序列化监控数据失败", e);
            return;
        }

        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            } catch (IOException e) {
                log.error("发送监控数据失败", e);
            }
        });
    }
}