package com.hayden.multiagentide.adapter;

import com.hayden.multiagentide.infrastructure.EventAdapter;
import com.hayden.acp_cdc_ai.acp.events.AgUiSerdes;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.filter.integration.ControllerEventFilterIntegration;
import com.hayden.multiagentide.filter.integration.PathFilterIntegration;
import com.hayden.multiagentide.filter.service.LayerIdResolver;
import com.hayden.multiagentide.filter.model.FilterSource;
import com.hayden.multiagentide.filter.model.layer.GraphEventObjectContext;
import com.hayden.multiagentide.filter.model.layer.DefaultPathFilterContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket event adapter that streams events to connected clients in real-time.
 */
@Slf4j
@Component
public class WebSocketEventAdapter extends EventAdapter {

    private final List<WebSocketSession> connectedClients = new CopyOnWriteArrayList<>();

    private AgUiSerdes serdes;
    @Autowired
    private LayerIdResolver layerIdResolver;
    @Autowired
    private ControllerEventFilterIntegration controllerEventFilterIntegration;
    @Autowired
    private PathFilterIntegration pathFilterIntegration;

    public WebSocketEventAdapter() {
        super("websocket-adapter");
    }

    @Autowired
    public void setSerdes(AgUiSerdes serdes) {
        this.serdes = serdes;
    }

    /**
     * Register a connected WebSocket client.
     */
    public void registerClient(WebSocketSession session) {
        log.info("Registering websocket client {}.", session.getRemoteAddress());
        connectedClients.add(session);
    }

    /**
     * Unregister a disconnected WebSocket client.
     */
    public void unregisterClient(WebSocketSession session) {
        log.info("Deregistering websocket client {}.", session.getRemoteAddress());
        connectedClients.remove(session);
    }

    /**
     * Get count of connected clients.
     */
    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    @Override
    protected void adaptEvent(Events.GraphEvent event) {
        String eventJson = serializeEvent(event);
        if (eventJson == null) {
            return;
        }

        for (WebSocketSession session : connectedClients) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(eventJson));
                } catch (IOException e) {
                    // If send fails, remove the client
                    unregisterClient(session);
                }
            }
        }
    }

    @Override
    protected void handleAdapterError(Events.GraphEvent event, Exception error) {
        System.err.println("WebSocket adapter error for event " + event.eventType() + ": " + error.getMessage());
    }

    @Override
    public String getAdapterType() {
        return "websocket";
    }

    /**
     * Serialize event to JSON after object and path filters are applied.
     */
    private String serializeEvent(Events.GraphEvent event) {
        if (event == null) {
            return null;
        }
        String layerId = layerIdResolver == null
                ? null
                : layerIdResolver.resolveForGraphEvent(event).orElse(null);

        Events.GraphEvent filteredEvent = event;
        if (layerId != null && controllerEventFilterIntegration != null) {
            filteredEvent = controllerEventFilterIntegration.applyFilters(layerId, event).t();
        }
        if (filteredEvent == null) {
            return null;
        }

        String payload = serdes.serializeEvent(Events.mapToEvent(filteredEvent));
        if (layerId != null && pathFilterIntegration != null) {
            payload = pathFilterIntegration.applyJsonPathFilters(
                    layerId,
                    FilterSource.graphEvent(filteredEvent),
                    payload,
                    new DefaultPathFilterContext(layerId, new GraphEventObjectContext(layerId, event))
            ).t();
        }
        return payload;
    }

}
