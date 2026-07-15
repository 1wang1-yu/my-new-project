package com.guide.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置 — 注册 /ws/avatar 端点供小程序实时接收形象切换通知
 */
@Configuration
@EnableWebSocket
public class AvatarWebSocketConfig implements WebSocketConfigurer {

    private final AvatarWebSocketHandler avatarWebSocketHandler;

    public AvatarWebSocketConfig(AvatarWebSocketHandler avatarWebSocketHandler) {
        this.avatarWebSocketHandler = avatarWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(avatarWebSocketHandler, "/ws/avatar")
                .setAllowedOrigins("*");
    }
}
