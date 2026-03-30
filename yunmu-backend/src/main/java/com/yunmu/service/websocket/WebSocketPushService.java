package com.yunmu.service.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class WebSocketPushService {

    private static final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> animalSubscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 添加会话
     */
    public void addSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
    }

    /**
     * 移除会话
     */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        animalSubscriptions.remove(sessionId);
    }

    /**
     * 添加订阅
     */
    public void addSubscription(String sessionId, String animalId) {
        animalSubscriptions.put(sessionId, animalId);
    }

    /**
     * 移除订阅
     */
    public void removeSubscription(String sessionId) {
        animalSubscriptions.remove(sessionId);
    }

    /**
     * 获取订阅的动物ID
     */
    public String getSubscribedAnimal(String sessionId) {
        return animalSubscriptions.get(sessionId);
    }

    /**
     * 发送消息给指定会话
     */
    public void sendMessage(WebSocketSession session, Object message) {
        try {
            if (session != null && session.isOpen()) {
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            }
        } catch (IOException e) {
            log.error("发送WebSocket消息失败", e);
        }
    }

    /**
     * 广播消息给所有连接的客户端
     */
    public void broadcastToAll(Object message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化消息失败", e);
            return;
        }

        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            } catch (IOException e) {
                log.error("发送WebSocket消息失败", e);
            }
        });
    }

    /**
     * 发送消息给订阅特定动物的客户端
     */
    public void sendToSubscribers(String animalId, Object message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化消息失败", e);
            return;
        }

        animalSubscriptions.forEach((sessionId, subscribedAnimalId) -> {
            if (subscribedAnimalId.equals(animalId)) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(jsonMessage));
                    } catch (IOException e) {
                        log.error("发送消息给订阅者失败: session={}", sessionId, e);
                    }
                }
            }
        });
    }

    /**
     * 获取在线会话数
     */
    public int getOnlineCount() {
        return sessions.size();
    }
}