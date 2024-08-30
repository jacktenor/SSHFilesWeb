package com.sativa.sshfilesweb;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class ProgressWebSocketHandler extends TextWebSocketHandler {
    private static WebSocketSession session;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        ProgressWebSocketHandler.session = session;
    }

    public static void sendProgress(int progress) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(String.valueOf(progress)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}