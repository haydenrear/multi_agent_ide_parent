package com.hayden.multiagentide.config;

import com.hayden.multiagentide.adapter.WebSocketEventAdapter;
import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;
import org.springframework.stereotype.Component;

/**
 * WebSocket configuration for real-time event streaming.
 */
@Configuration
//@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketEventAdapter eventAdapter;

    private ComputationGraphOrchestrator orchestrator;
    private OrchestrationController orchestrationController;

    public WebSocketConfig(WebSocketEventAdapter eventAdapter) {
        this.eventAdapter = eventAdapter;
    }

    @Lazy
    @Autowired
    public void setOrchestrator(ComputationGraphOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Autowired
    public void setOrchestrationController(OrchestrationController orchestrationController) {
        this.orchestrationController = orchestrationController;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EventStreamHandler(eventAdapter, orchestrator, orchestrationController), "/ws/events")
                .setAllowedOrigins("*");
    }

    /**
     * WebSocket handler for event streaming.
     */
    @Component
    @RequiredArgsConstructor
    public static class EventStreamHandler extends TextWebSocketHandler {

        private final WebSocketEventAdapter eventAdapter;

        private final ComputationGraphOrchestrator computationGraphOrchestrator;

        private final OrchestrationController orchestrationController;


        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            eventAdapter.registerClient(session);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
            eventAdapter.unregisterClient(session);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            // Echo back for now; can implement command handling here
            session.sendMessage(new TextMessage("Event stream connected"));
        }
    }
}
