package com.hayden.multiagentide.service;

import com.agentclientprotocol.model.PermissionOptionKind;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.repository.EventStreamRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Centralized service for ArtifactKey-scoped permission and interrupt lookups.
 * <p>
 * Both {@code PermissionController} and {@code InterruptController} previously duplicated
 * {@code isArtifactKey}, {@code matchesNodeScope}, event-search helpers, and ToolCallInfo
 * construction. This service is the single source of truth for all of that logic.
 * <p>
 * Resolution strategy (applied uniformly for both permissions and interrupts):
 * <ol>
 *   <li>Try the supplied id as a direct exact match (UUID requestId / interruptId).</li>
 *   <li>If unresolved and the id is a valid ArtifactKey, scan the event stream for matching
 *       events whose nodeId is a descendant of the supplied scope key (most-recent first).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PermissionGateService {

    private final PermissionGate permissionGate;
    private final EventStreamRepository eventStreamRepository;

    // ─── Canonical ToolCallInfo ──────────────────────────────────────────────

    @Schema(description = "Summary of a tool call event associated with a permission or interrupt request.")
    public record ToolCallInfo(
            String eventId,
            Instant timestamp,
            String nodeId,
            String toolCallId,
            String title,
            String kind,
            String status,
            String phase,
            List<Map<String, Object>> content,
            List<Map<String, Object>> locations,
            Object rawInput,
            Object rawOutput
    ) {
    }

    // ─── ArtifactKey utilities ────────────────────────────────────────────────

    public boolean isArtifactKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            new ArtifactKey(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean matchesNodeScope(String scopeNodeId, String eventNodeId) {
        if (scopeNodeId == null || scopeNodeId.isBlank() || eventNodeId == null || eventNodeId.isBlank()) {
            return false;
        }
        if (scopeNodeId.equals(eventNodeId)) {
            return true;
        }
        try {
            ArtifactKey candidate = new ArtifactKey(eventNodeId);
            ArtifactKey scope = new ArtifactKey(scopeNodeId);
            return candidate.isDescendantOf(scope);
        } catch (Exception ignored) {
            return eventNodeId.startsWith(scopeNodeId + "/");
        }
    }

    // ─── Permission helpers ───────────────────────────────────────────────────

    public Optional<Events.PermissionRequestedEvent> findPermissionRequestEvent(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return eventStreamRepository.list().stream()
                .filter(Events.PermissionRequestedEvent.class::isInstance)
                .map(Events.PermissionRequestedEvent.class::cast)
                .filter(event -> matchesPermissionIdentifier(id, event))
                .sorted(Comparator.comparing(Events.PermissionRequestedEvent::timestamp).reversed())
                .findFirst();
    }

    /**
     * Resolve a permission by ArtifactKey scope: scans all PermissionRequestedEvents whose
     * nodeId is a descendant of {@code scopeNodeId}, attempting resolution most-recent first.
     *
     * @return the resolved requestId, or {@code null} if nothing matched.
     */
    public String resolvePermissionFromScope(String scopeNodeId, PermissionOptionKind optionType, String note) {
        List<Events.PermissionRequestedEvent> candidates = eventStreamRepository.list().stream()
                .filter(Events.PermissionRequestedEvent.class::isInstance)
                .map(Events.PermissionRequestedEvent.class::cast)
                .filter(event -> matchesNodeScope(scopeNodeId, event.nodeId()))
                .sorted(Comparator.comparing(Events.PermissionRequestedEvent::timestamp).reversed())
                .toList();

        for (Events.PermissionRequestedEvent candidate : candidates) {
            if (performPermissionResolution(candidate.requestId(), optionType, note)) {
                return candidate.requestId();
            }
        }
        return null;
    }

    public boolean performPermissionResolution(String requestId, PermissionOptionKind optionType, String note) {
        if (optionType == null) {
            return permissionGate.resolveCancelled(requestId);
        }
        var option = switch (optionType) {
            case ALLOW_ONCE -> IPermissionGate.Companion.allowOnce();
            case ALLOW_ALWAYS -> IPermissionGate.Companion.allowAlways();
            case REJECT_ONCE -> IPermissionGate.Companion.rejectOnce();
            case REJECT_ALWAYS -> IPermissionGate.Companion.rejectAlways();
        };
        return permissionGate.resolveSelected(requestId, option, note != null ? note : "");
    }

    public List<ToolCallInfo> findToolCallsForPermission(Events.PermissionRequestedEvent permissionEvent) {
        String toolCallId = permissionEvent.toolCallId();
        if (toolCallId != null && !toolCallId.isBlank()) {
            List<ToolCallInfo> byToolCallId = eventStreamRepository.list().stream()
                    .filter(Events.ToolCallEvent.class::isInstance)
                    .map(Events.ToolCallEvent.class::cast)
                    .filter(tc -> Objects.equals(tc.toolCallId(), toolCallId))
                    .sorted(Comparator.comparing(Events.ToolCallEvent::timestamp))
                    .map(this::toToolCallInfo)
                    .toList();
            if (!byToolCallId.isEmpty()) {
                return byToolCallId;
            }
        }
        // Fall back to node-scope matching when toolCallId is absent or unmatched.
        String scopeId = permissionEvent.nodeId() != null ? permissionEvent.nodeId() : permissionEvent.originNodeId();
        return findToolCallsInScope(scopeId, 40);
    }

    // ─── Interrupt helpers ────────────────────────────────────────────────────

    public Optional<Events.InterruptRequestEvent> findInterruptRequestEvent(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return eventStreamRepository.list().stream()
                .filter(Events.InterruptRequestEvent.class::isInstance)
                .map(Events.InterruptRequestEvent.class::cast)
                .filter(event -> matchesInterruptIdentifier(id, event))
                .sorted(Comparator.comparing(Events.InterruptRequestEvent::timestamp).reversed())
                .findFirst();
    }

    /**
     * Collect all candidate interrupt/review IDs within the given ArtifactKey scope,
     * ordered most-recent first. Used when direct ID resolution fails and the caller
     * passes an ArtifactKey that should cover descendant nodes.
     */
    public List<String> findInterruptIdsInScope(String scopeNodeId) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        eventStreamRepository.list().stream()
                .sorted(Comparator.comparing(Events.GraphEvent::timestamp).reversed())
                .forEach(event -> {
                    if (event instanceof Events.InterruptRequestEvent interruptEvent
                            && matchesNodeScope(scopeNodeId, interruptEvent.nodeId())) {
                        Stream.of(interruptEvent.requestId(), interruptEvent.nodeId())
                                .filter(Objects::nonNull)
                                .filter(s -> !s.isBlank())
                                .forEach(candidates::add);
                    }
                    if (event instanceof Events.NodeReviewRequestedEvent reviewRequested
                            && matchesNodeScope(scopeNodeId, reviewRequested.nodeId())
                            && reviewRequested.reviewNodeId() != null
                            && !reviewRequested.reviewNodeId().isBlank()) {
                        candidates.add(reviewRequested.reviewNodeId());
                    }
                });
        return candidates.stream().toList();
    }

    // ─── Shared tool-call lookup ──────────────────────────────────────────────

    public List<ToolCallInfo> findToolCallsInScope(String nodeId, int limit) {
        if (nodeId == null || nodeId.isBlank()) {
            return List.of();
        }
        return eventStreamRepository.list().stream()
                .filter(Events.ToolCallEvent.class::isInstance)
                .map(Events.ToolCallEvent.class::cast)
                .filter(tc -> matchesNodeScope(nodeId, tc.nodeId()))
                .sorted(Comparator.comparing(Events.ToolCallEvent::timestamp).reversed())
                .limit(limit)
                .map(this::toToolCallInfo)
                .toList();
    }

    public ToolCallInfo toToolCallInfo(Events.ToolCallEvent event) {
        return new ToolCallInfo(
                event.eventId(),
                event.timestamp(),
                event.nodeId(),
                event.toolCallId(),
                event.title(),
                event.kind(),
                event.status(),
                event.phase(),
                event.content(),
                event.locations(),
                event.rawInput(),
                event.rawOutput()
        );
    }

    // ─── Private matching helpers ─────────────────────────────────────────────

    private boolean matchesPermissionIdentifier(String id, Events.PermissionRequestedEvent event) {
        if (isArtifactKey(id)) {
            return matchesNodeScope(id, event.nodeId())
                    || matchesNodeScope(id, event.originNodeId());
        }
        return id.equals(event.requestId())
                || id.equals(event.toolCallId())
                || id.equals(event.nodeId());
    }

    private boolean matchesInterruptIdentifier(String id, Events.InterruptRequestEvent event) {
        if (isArtifactKey(id)) {
            return matchesNodeScope(id, event.nodeId());
        }
        return id.equals(event.requestId()) || id.equals(event.nodeId());
    }
}
