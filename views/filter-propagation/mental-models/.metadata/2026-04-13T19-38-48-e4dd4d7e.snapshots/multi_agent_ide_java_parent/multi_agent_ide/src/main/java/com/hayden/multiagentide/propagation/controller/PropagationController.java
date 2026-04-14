package com.hayden.multiagentide.propagation.controller;

import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.propagation.controller.dto.ReadPropagationItemsResponse;
import com.hayden.multiagentide.propagation.controller.dto.RecentPropagationItemsByNodeRequest;
import com.hayden.multiagentide.propagation.controller.dto.RecentPropagationItemsByNodeResponse;
import com.hayden.multiagentide.propagation.controller.dto.ResolvePropagationItemRequest;
import com.hayden.multiagentide.propagation.controller.dto.ResolvePropagationItemResponse;
import com.hayden.multiagentide.propagation.model.Propagation;
import com.hayden.multiagentide.propagation.service.PropagationItemService;
import com.hayden.multiagentide.model.PropagationResolutionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/propagations/items")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Propagation Items", description = "List, monitor, and resolve propagation items. "
        + "Use /by-node for primary controller monitoring (full request/response payloads). "
        + "Use /records for debugging propagator behavior.")
public class PropagationController {

    private final PropagationItemService propagationItemService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "List all pending propagation items")
    public ResponseEntity<ReadPropagationItemsResponse> items() {
        var items = propagationItemService.findPendingItems().stream()
                .map(item -> ReadPropagationItemsResponse.ItemSummary.builder()
                        .itemId(item.getItemId())
                        .registrationId(item.getRegistrationId())
                        .layerId(item.getLayerId())
                        .sourceNodeId(item.getSourceNodeId())
                        .sourceName(item.getSourceName())
                        .summaryText(item.getSummaryText())
                        .mode(item.getMode())
                        .status(item.getStatus())
                        .createdAt(item.getCreatedAt())
                        .resolvedAt(item.getResolvedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(ReadPropagationItemsResponse.builder().items(items).totalCount(items.size()).build());
    }

    @PostMapping("/by-node")
    @Operation(
            summary = "Recent propagation events by node (primary controller monitoring endpoint)",
            description = "Returns the last N full request/response payloads that passed through propagators for a given node. "
                    + "Call this repeatedly to monitor agent behavior — the propagatedText field contains the complete "
                    + "serialized payload (action request or response) as seen by the propagator. "
                    + "Default limit is 2. For debugging propagator internals use /api/propagations/records instead."
    )
    public ResponseEntity<RecentPropagationItemsByNodeResponse> recentByNode(
            @RequestBody @Valid RecentPropagationItemsByNodeRequest request) {
        int limit = request.limit() > 0 ? request.limit() : 2;
        var items = propagationItemService.recentByNode(request.nodeId(), limit).stream()
                .map(item -> RecentPropagationItemsByNodeResponse.PropagationItemPayload.builder()
                        .itemId(item.getItemId())
                        .registrationId(item.getRegistrationId())
                        .layerId(item.getLayerId())
                        .sourceNodeId(item.getSourceNodeId())
                        .sourceName(item.getSourceName())
                        .stage(item.getStage())
                        .summaryText(item.getSummaryText())
                        .propagatedText(parsePropagation(item.getPropagatedText()))
                        .mode(item.getMode())
                        .status(item.getStatus())
                        .resolutionType(item.getResolutionType())
                        .resolutionNotes(item.getResolutionNotes())
                        .createdAt(item.getCreatedAt())
                        .resolvedAt(item.getResolvedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(RecentPropagationItemsByNodeResponse.builder().items(items).totalCount(items.size()).build());
    }

    @PostMapping("/{itemId}/resolve")
    @Operation(summary = "Resolve a propagation item by ID")
    public ResponseEntity<ResolvePropagationItemResponse> resolve(@PathVariable String itemId,
                                                                  @RequestBody @Valid ResolvePropagationItemRequest request) {
        PropagationResolutionType resolutionType = PropagationResolutionType.valueOf(request.resolutionType());
        return ResponseEntity.ok(propagationItemService.resolve(itemId, resolutionType, request.resolutionNotes()));
    }

    private Propagation parsePropagation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Propagation.class);
        } catch (Exception e) {
            log.debug("propagatedText is not a Propagation record, wrapping as llmOutput: {}", e.getMessage());
            return new Propagation(raw, null);
        }
    }
}
