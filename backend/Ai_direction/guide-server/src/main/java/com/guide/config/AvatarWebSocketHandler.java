package com.guide.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 处理器 — 当后台切换形象时，推送通知给所有连接的客户端（小程序）。
 */
@Slf4j
@Component
public class AvatarWebSocketHandler extends TextWebSocketHandler {

    /** 所有活跃的 WebSocket 连接 */
    private static final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket 已连接: {}, 当前连接数: {}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket 已断开: {}, 当前连接数: {}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端发来的消息，暂不处理
    }

    /**
     * 广播形象变更通知给所有客户端
     * @param filename 新的形象文件名
     */
    public void broadcastAvatarChange(String filename) {
        String payload = "{\"type\":\"avatar_change\",\"filename\":\"" + filename + "\"}";
        TextMessage msg = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(msg);
                } catch (IOException e) {
                    log.warn("WebSocket 发送失败: {}", e.getMessage());
                }
            }
        }
    }
}
