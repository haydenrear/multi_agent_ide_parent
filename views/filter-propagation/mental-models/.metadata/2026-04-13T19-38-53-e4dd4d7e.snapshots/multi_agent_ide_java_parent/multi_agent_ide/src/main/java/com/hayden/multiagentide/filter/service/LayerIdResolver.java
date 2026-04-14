package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentide.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves runtime context metadata to the appropriate filter layer ID from the
 * bootstrapped filter layer catalog.
 */
@Service
@RequiredArgsConstructor
public class LayerIdResolver {

    private final LayerRepository layerRepository;

    /**
     * Resolve layerId for a prompt contributor context.
     * Prefers explicit/decorated metadata, then request/agent-type mappings.
     */
    public Optional<String> resolveForPromptContributor(PromptContext ctx) {
        Map<String, Object> metadata = ctx == null ? null : ctx.metadata();

        Optional<String> explicitLayerId = resolveFromMetadata(metadata, FilterLayerCatalog.METADATA_LAYER_ID);

        if (explicitLayerId.isPresent()) {
            return explicitLayerId;
        }
        Optional<String> metadataLayerId = resolveFromPromptContext(ctx);

        if (metadataLayerId.isPresent()) {
            return metadataLayerId;
        }

        Optional<String> sessionLayerId = resolveForSession(resolveRawMetadata(metadata, "sessionId"));

        if (sessionLayerId.isPresent()) {
            return sessionLayerId;
        }

        if (ctx == null) {
            return Optional.empty();
        }

        return FilterLayerCatalog.resolveActionLayer(ctx.currentRequest(), ctx.agentType())
                .flatMap(this::resolveExistingLayer)
                .or(() -> FilterLayerCatalog.resolveRootLayer(ctx.agentType()).flatMap(this::resolveExistingLayer));
    }

    /**
     * Resolve layerId for a controller context.
     * Any controller that participates in UI event polling maps to the shared controller layer.
     */
    public Optional<String> resolveForController(String controllerId) {
        return resolveExistingLayer(controllerId)
                .or(() -> hasText(controllerId) ? resolveExistingLayer(FilterLayerCatalog.CONTROLLER_UI_EVENT_POLL) : Optional.empty());
    }

    /**
     * Resolve layerId from a session-scoped context.
     */
    public Optional<String> resolveForSession(String sessionId) {
        return hasText(sessionId) ? resolveExistingLayer(FilterLayerCatalog.CONTROLLER_UI_EVENT_POLL) : Optional.empty();
    }

    /**
     * Resolve layerId for a graph event using controller/session/node fallbacks.
     */
    public Optional<String> resolveForGraphEvent(Events.GraphEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        return resolveForGraphEvent(null, event);
    }

    /**
     * Resolve layerId for a graph event with a controller override.
     */
    public Optional<String> resolveForGraphEvent(String controllerId, Events.GraphEvent event) {
        if (event == null) {
            return resolveForController(controllerId);
        }
        return resolveForController(controllerId)
                .or(() -> resolveForSession(extractSessionId(event)))
                .or(() -> resolveForAgentEvent(event))
                .or(() -> resolveForGraphEvent(controllerId, extractSessionId(event), event.nodeId()));
    }

    /**
     * Resolve layerId for a graph event based on provided context pieces.
     */
    public Optional<String> resolveForGraphEvent(String controllerId, String sessionId, String nodeId) {
        return resolveForController(controllerId)
                .or(() -> resolveForSession(sessionId))
                .or(() -> resolveExistingLayer(nodeId));
    }

    private Optional<String> resolveExistingLayer(String layerId) {
        if (!hasText(layerId)) {
            return Optional.empty();
        }
        return layerRepository.findByLayerId(layerId)
                .map(LayerEntity::getLayerId);
    }

    private Optional<String> resolveFromPromptContext(PromptContext ctx) {
        String agentName = ctx.agentName();
        String actionName = ctx.actionName();
        String methodName = ctx.methodName();
        return FilterLayerCatalog.resolveActionLayer(agentName, actionName, methodName)
                .flatMap(this::resolveExistingLayer);
    }

    private Optional<String> resolveFromMetadata(Map<String, Object> metadata, String key) {
        String raw = resolveRawMetadata(metadata, key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return resolveExistingLayer(raw);
    }

    private String resolveRawMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String asString = String.valueOf(value).trim();
        return asString.isEmpty() ? null : asString;
    }

    private String extractSessionId(Events.GraphEvent event) {
        for (String methodName : new String[]{"sessionId", "chatSessionId"}) {
            try {
                Method method = event.getClass().getMethod(methodName);
                Object value = method.invoke(event);
                if (value instanceof String sessionId && !sessionId.isBlank()) {
                    return sessionId;
                }
            } catch (Exception ignored) {
                // best-effort extraction for mixed event types
            }
        }
        return null;
    }

    private Optional<String> resolveForAgentEvent(Events.GraphEvent event) {
        return switch (event) {
            case Events.ActionStartedEvent actionStartedEvent ->
                    FilterLayerCatalog.resolveActionLayer(
                            actionStartedEvent.agentName(),
                            actionStartedEvent.actionName(),
                            null
                    ).flatMap(this::resolveExistingLayer);
            case Events.ActionCompletedEvent actionCompletedEvent ->
                    FilterLayerCatalog.resolveActionLayer(
                            actionCompletedEvent.agentName(),
                            actionCompletedEvent.actionName(),
                            null
                    ).flatMap(this::resolveExistingLayer);
            default -> Optional.empty();
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
