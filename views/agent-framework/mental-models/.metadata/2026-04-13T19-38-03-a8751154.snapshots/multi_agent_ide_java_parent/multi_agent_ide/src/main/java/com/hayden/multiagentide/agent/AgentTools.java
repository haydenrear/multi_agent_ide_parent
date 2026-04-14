package com.hayden.multiagentide.agent;

import com.hayden.commitdiffcontext.cdc_utils.SetFromHeader;
import com.hayden.commitdiffcontext.mcp.ToolCarrier;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.ui.GuiEmissionResult;
import com.hayden.multiagentide.model.ui.GuiEventPayload;
import com.hayden.multiagentide.model.ui.UiDiffRequest;
import com.hayden.multiagentide.model.ui.UiDiffResult;
import com.hayden.multiagentide.model.ui.UiEventFeedback;
import com.hayden.multiagentide.service.UiStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hayden.acp_cdc_ai.acp.AcpChatModel.MCP_SESSION_HEADER;

/**
 * Tool definitions for Embabel agents.
 * These tools are available to all agents via the agent execution framework.
 */
//@Component
@RequiredArgsConstructor
public class AgentTools implements ToolCarrier {


    public static final String SESSION_ID_MISSING_MESSAGE = "Session id is required - let them know that it failed - this is not your fault!";
    private final EventBus eventBus;
    private final UiStateService uiStateService;

    /**
     * Should push an event to the socket to be displayed by the frontend
     * @return
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Emit gui event to user")
    public GuiEmissionResult emitGuiEvent(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            com.hayden.utilitymodule.mcp.ctx.McpRequestContext.McpToolContext requestContext,
            GuiEventPayload payload) {
        if (payload == null) {
            return new GuiEmissionResult("rejected", "invalid_payload", "Payload is required.", true);
        }
        if (!StringUtils.hasText(sessionId)) {
            return new GuiEmissionResult("rejected", "missing_session", SESSION_ID_MISSING_MESSAGE, true);
        }
        Object renderTree = payload.renderTree() != null ? payload.renderTree() : payload.a2uiMessages();
        if (renderTree == null) {
            return new GuiEmissionResult("rejected", "missing_render_tree", "Render tree or a2uiMessages required.", true);
        }
        Events.UiStateSnapshot snapshot = uiStateService.captureSnapshot(sessionId, renderTree);

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("sessionId", sessionId);
        payloadMap.put("renderer", payload.renderer());
        payloadMap.put("title", payload.title());
        payloadMap.put("props", payload.props());
        payloadMap.put("a2uiMessages", payload.a2uiMessages());
        payloadMap.put("renderTree", renderTree);
        payloadMap.put("summary", payload.summary());
        if (snapshot != null) {
            payloadMap.put("revision", snapshot.revision());
        }

        String eventId = UUID.randomUUID().toString();
        eventBus.publish(new Events.GuiRenderEvent(
                eventId,
                Instant.now(),
                sessionId,
                sessionId,
                payloadMap
        ));
        return new GuiEmissionResult("accepted", null, "Gui event emitted.", false);
    }

    @org.springframework.ai.tool.annotation.Tool(description = "Retrieve the current GUI snapshot for a session")
    public Events.UiStateSnapshot retrieveGui(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId
    ) {
        String safeSessionId = StringUtils.hasText(sessionId) ? sessionId : "unknown";
        Events.UiStateSnapshot snapshot = uiStateService.getSnapshot(safeSessionId);
        if (snapshot != null) {
            return snapshot;
        }
        return new Events.UiStateSnapshot(safeSessionId, null, Instant.now(), Map.of());
    }

    @org.springframework.ai.tool.annotation.Tool(description = "Submit feedback for a UI event")
    public GuiEmissionResult submitGuiFeedback(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            UiEventFeedback feedback
    ) {
        if (feedback == null) {
            return new GuiEmissionResult("rejected", "invalid_payload", "Feedback payload is required.", true);
        }
        if (!StringUtils.hasText(sessionId)) {
            return new GuiEmissionResult("rejected", "missing_session", SESSION_ID_MISSING_MESSAGE, true);
        }
        if (!StringUtils.hasText(feedback.eventId())) {
            return new GuiEmissionResult("rejected", "missing_event", "Event id is required.", true);
        }
        if (!StringUtils.hasText(feedback.message())) {
            return new GuiEmissionResult("rejected", "missing_message", "Feedback message is required.", true);
        }

        Events.UiStateSnapshot snapshot = feedback.snapshot() != null
                ? feedback.snapshot()
                : uiStateService.getSnapshot(sessionId);

        eventBus.publish(new Events.UiFeedbackEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                sessionId,
                sessionId,
                feedback.eventId(),
                feedback.message(),
                snapshot
        ));

        return new GuiEmissionResult("accepted", null, "Feedback submitted.", false);
    }

    @org.springframework.ai.tool.annotation.Tool(description = "Apply a UI diff to the current GUI state")
    public UiDiffResult performUiDiff(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            UiDiffRequest request
    ) {
        if (request == null) {
            return new UiDiffResult("rejected", null, "invalid_payload", "Diff payload is required.");
        }
        if (!StringUtils.hasText(sessionId)) {
            return new UiDiffResult("rejected", null, "missing_session", SESSION_ID_MISSING_MESSAGE);
        }
        UiDiffRequest resolved = new UiDiffRequest(
                request.baseRevision(),
                request.diff(),
                request.summary()
        );
        UiDiffResult result = uiStateService.applyDiff(resolved, sessionId);
        Events.UiStateSnapshot snapshot = uiStateService.getSnapshot(sessionId);

        if ("applied".equalsIgnoreCase(result.status()) && snapshot != null) {
            eventBus.publish(new Events.UiDiffAppliedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    sessionId,
                    sessionId,
                    snapshot.revision(),
                    snapshot.renderTree(),
                    request.summary()
            ));
        } else if (!"applied".equalsIgnoreCase(result.status())) {
            eventBus.publish(new Events.UiDiffRejectedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    sessionId,
                    sessionId,
                    result.errorCode(),
                    result.message()
            ));
        }
        return result;
    }


}
