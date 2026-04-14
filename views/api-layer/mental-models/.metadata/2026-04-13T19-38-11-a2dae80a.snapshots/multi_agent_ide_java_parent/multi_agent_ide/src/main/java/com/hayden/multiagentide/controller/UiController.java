package com.hayden.multiagentide.controller;

import jakarta.validation.Valid;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.ui.UiDiffResult;
import com.hayden.multiagentide.service.UiStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/ui")
@RequiredArgsConstructor
@Tag(name = "UI", description = "UI feedback, diff revert, and message submission")
public class UiController {

    private final EventBus eventBus;
    private final UiStateService uiStateService;
    private final ObjectMapper objectMapper;

    @PostMapping("/feedback")
    @Operation(summary = "Submit UI feedback with optional snapshot",
            description = "Publishes an AddMessageEvent and a UiFeedbackEvent for the given node. "
                    + "If snapshot is omitted but nodeId is set, the current UiStateSnapshot is fetched automatically "
                    + "and appended to the message. Feedback is intended for human review annotations — "
                    + "use /message for plain agent messages without snapshot enrichment.")
    public UiFeedbackResponse submitFeedback(@RequestBody @Valid UiFeedbackRequest request) {
        String nodeId = request.nodeId() != null ? request.nodeId() : "unknown";
        Events.UiStateSnapshot snapshot = request.snapshot();
        if (snapshot == null && request.nodeId() != null) {
            snapshot = uiStateService.getSnapshot(request.nodeId());
        }

        String message = request.message();
        String enriched = message;
        if (snapshot != null) {
            try {
                enriched = message + "\n\nUI snapshot:\n" + objectMapper.writeValueAsString(snapshot);
            }
            catch (JsonProcessingException ignored) {
            }
        }

        String eventId = UUID.randomUUID().toString();
        eventBus.publish(new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                enriched
        ));
        eventBus.publish(new Events.UiFeedbackEvent(
                eventId,
                Instant.now(),
                nodeId,
                nodeId,
                request.eventId(),
                message,
                snapshot
        ));
        return new UiFeedbackResponse("received");
    }

    @PostMapping("/diff/revert")
    @Operation(summary = "Revert the latest UI diff for a node",
            description = "Calls UiStateService.revert() for the given nodeId. "
                    + "On success (status == 'reverted'), publishes a UiDiffRevertedEvent with the current revision and renderTree. "
                    + "On failure, publishes a UiDiffRejectedEvent with errorCode and message. "
                    + "Returns the UiDiffResult regardless of outcome.")
    public UiDiffResult revertDiff(@RequestBody @Valid UiRevertRequest request) {
        String sessionId = request.nodeId() != null ? request.nodeId() : "unknown";
        UiDiffResult result = uiStateService.revert(sessionId);
        Events.UiStateSnapshot snapshot = uiStateService.getSnapshot(sessionId);

        if ("reverted".equalsIgnoreCase(result.status()) && snapshot != null) {
            eventBus.publish(new Events.UiDiffRevertedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    sessionId,
                    sessionId,
                    snapshot.revision(),
                    snapshot.renderTree(),
                    request.eventId()
            ));
        } else if (!"reverted".equalsIgnoreCase(result.status())) {
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

    @PostMapping("/message")
    @Operation(summary = "Submit a plain message to an agent node",
            description = "Publishes an AddMessageEvent to the given nodeId. "
                    + "Blank messages are silently ignored (returns status 'ignored'). "
                    + "Use this to inject instructions or context into a running agent node without snapshot enrichment. "
                    + "See /feedback for enriched feedback that attaches the current UiStateSnapshot.")
    public UiFeedbackResponse submitMessage(@RequestBody @Valid UiMessageRequest request) {
        String nodeId = request.nodeId() != null ? request.nodeId() : "unknown";
        String message = request.message() != null ? request.message() : "";
        if (message.isBlank()) {
            return new UiFeedbackResponse("ignored");
        }
        eventBus.publish(new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                message
        ));
        return new UiFeedbackResponse("received");
    }

    @Schema(description = "Request to submit UI feedback, optionally enriched with a UI state snapshot.")
    public record UiFeedbackRequest(
            @Schema(description = "ID of the UI event that triggered this feedback (optional)") String eventId,
            @Schema(description = "ArtifactKey of the target agent node") String nodeId,
            @Schema(description = "Human feedback message to inject into the agent") String message,
            @Schema(description = "Current UI state snapshot — omit to auto-fetch from UiStateService by nodeId") Events.UiStateSnapshot snapshot
    ) {
    }

    public record UiFeedbackResponse(String status) {
    }

    @Schema(description = "Request to revert the latest UI diff for a node.")
    public record UiRevertRequest(
            @Schema(description = "ID of the diff event to revert (optional, used for event correlation)") String eventId,
            @Schema(description = "ArtifactKey of the target agent node") String nodeId
    ) {
    }

    @Schema(description = "Request to send a plain message to an agent node.")
    public record UiMessageRequest(
            @Schema(description = "ArtifactKey of the target agent node") String nodeId,
            @Schema(description = "Message content to inject — blank messages are ignored") String message
    ) {
    }
}
