package com.yunmu.config;

import com.yunmu.service.websocket.AnimalDataWebSocketHandler;
import com.yunmu.service.websocket.RealTimeMonitorWebSocketHandler;
import com.yunmu.service.websocket.AlertNotificationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AnimalDataWebSocketHandler animalDataWebSocketHandler;
    private final RealTimeMonitorWebSocketHandler realTimeMonitorWebSocketHandler;
    private final AlertNotificationWebSocketHandler alertNotificationWebSocketHandler;

    public WebSocketConfig(AnimalDataWebSocketHandler animalDataWebSocketHandler,
                           RealTimeMonitorWebSocketHandler realTimeMonitorWebSocketHandler,
                           AlertNotificationWebSocketHandler alertNotificationWebSocketHandler) {
        this.animalDataWebSocketHandler = animalDataWebSocketHandler;
        this.realTimeMonitorWebSocketHandler = realTimeMonitorWebSocketHandler;
        this.alertNotificationWebSocketHandler = alertNotificationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(animalDataWebSocketHandler, "/ws/animal-data")
                .setAllowedOriginPatterns("*");
        registry.addHandler(realTimeMonitorWebSocketHandler, "/ws/realtime-monitor")
                .setAllowedOriginPatterns("*");
        registry.addHandler(alertNotificationWebSocketHandler, "/ws/alerts")
                .setAllowedOriginPatterns("*");
    }
}