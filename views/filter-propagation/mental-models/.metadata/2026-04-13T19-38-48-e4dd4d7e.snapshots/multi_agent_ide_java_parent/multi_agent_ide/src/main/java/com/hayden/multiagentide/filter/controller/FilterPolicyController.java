package com.hayden.multiagentide.filter.controller;

import jakarta.validation.Valid;
import com.hayden.multiagentide.filter.controller.dto.*;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationEntity;
import com.hayden.multiagentide.filter.service.FilterAttachableCatalogService;
import com.hayden.multiagentide.filter.service.LayerService;
import com.hayden.multiagentide.filter.service.PolicyDiscoveryService;
import com.hayden.multiagentide.filter.service.PolicyRegistrationService;
import com.hayden.multiagentide.filter.service.FilterDecisionQueryService;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the layered data policy filtering subsystem.
 * All endpoints accept JSON request bodies to avoid path-encoding issues
 * with hierarchical layer IDs (e.g. "workflow-agent/coordinateWorkflow").
 */
@RestController
@RequestMapping("/api/filters")
@RequiredArgsConstructor
@Tag(name = "Filter Policies", description = "Manage data-layer filter policies, layers, and filter decision records")
public class FilterPolicyController {

    private final PolicyDiscoveryService policyDiscoveryService;
    private final PolicyRegistrationService policyRegistrationService;
    private final FilterDecisionQueryService filterDecisionQueryService;
    private final LayerService layerService;
    private final FilterAttachableCatalogService filterAttachableCatalogService;

    // ── US1: Discovery ───────────────────────────────────────────────

    @PostMapping("/layers/policies")
    @Operation(summary = "List filter policies by layer",
            description = "Returns all active policies bound to the given layerId. Use status='ACTIVE' (default) "
                    + "for policies inherited via the layer hierarchy, or omit for directly-bound only. "
                    + "Layer IDs follow the pattern: 'workflow-agent', 'workflow-agent/actionName', "
                    + "'controller', 'controller-ui-event-poll'. See LayerService for the hierarchy model.")
    public ResponseEntity<ReadPoliciesByLayerResponse> getPoliciesByLayer(
            @RequestBody @Valid ReadPoliciesByLayerRequest request) {

        String layerId = request.layerId();
        String status = request.statusOrDefault();

        List<PolicyRegistrationEntity> policies;
        if ("ACTIVE".equalsIgnoreCase(status)) {
            policies = policyDiscoveryService.getActivePoliciesByLayer(layerId);
        } else {
            policies = policyDiscoveryService.getActivePoliciesDirectlyBound(layerId);
        }

        List<ReadPoliciesByLayerResponse.PolicySummary> summaries = policies.stream()
                .map(p -> ReadPoliciesByLayerResponse.PolicySummary.builder()
                        .policyId(p.getRegistrationId())
                        .name(extractFilterField(p, "name"))
                        .description(extractFilterField(p, "description"))
                        .sourcePath(extractFilterField(p, "sourcePath"))
                        .filterType(p.getFilterKind())
                        .status(p.getStatus())
                        .priority(extractPriority(p))
                        .build())
                .toList();

        return ResponseEntity.ok(ReadPoliciesByLayerResponse.builder()
                .layerId(layerId)
                .policies(summaries)
                .totalCount(summaries.size())
                .build());
    }

    @PostMapping("/layers/children")
    @Operation(summary = "List child layers of a parent layer",
            description = "Returns child layers under the given layerId. Set recursive=true to include "
                    + "the full subtree. Useful for discovering the layer hierarchy before binding policies.")
    public ResponseEntity<ReadLayerChildrenResponse> getLayerChildren(
            @RequestBody @Valid ReadLayerChildrenRequest request) {

        String layerId = request.layerId();
        List<LayerEntity> children = layerService.getChildLayers(layerId, request.recursive());

        List<ReadLayerChildrenResponse.LayerSummary> summaries = children.stream()
                .map(l -> ReadLayerChildrenResponse.LayerSummary.builder()
                        .layerId(l.getLayerId())
                        .layerType(l.getLayerType())
                        .layerKey(l.getLayerKey())
                        .parentLayerId(l.getParentLayerId())
                        .depth(l.getDepth())
                        .build())
                .toList();

        return ResponseEntity.ok(ReadLayerChildrenResponse.builder()
                .layerId(layerId)
                .children(summaries)
                .totalCount(summaries.size())
                .build());
    }

    @GetMapping("/attachables")
    @Operation(summary = "List attachable filter targets",
            description = "Source of truth for valid event type names, contributor names, and layer/contributor "
                    + "combinations. Always check this before registering a filter policy to ensure matchOn/matcherKey values are valid.")
    public ResponseEntity<ReadAttachableTargetsResponse> getAttachableTargets() {
        return ResponseEntity.ok(filterAttachableCatalogService.readAttachableTargets());
    }

    @PostMapping("/json-path-filters/policies")
    @Operation(summary = "Register a JSON path filter policy",
            description = "Registers a filter using JSON_PATH expressions (JsonPath syntax, root is $). "
                    + "The filter produces Instruction objects — see Instruction.java for the sealed interface contract.")
    public ResponseEntity<PolicyRegistrationResponse> registerJsonPathFilter(
            @RequestBody @Valid PolicyRegistrationRequest request) {
        return ResponseEntity.ok(policyRegistrationService.registerPolicy(
                FilterEnums.FilterKind.JSON_PATH, request));
    }

    @PostMapping("/markdown-path-filters/policies")
    @Operation(summary = "Register a Markdown path filter policy",
            description = "Registers a filter using MARKDOWN_PATH heading-scope selectors (e.g. '#', '## Section'). "
                    + "Operates on prompt contributor text content. See Path.java for path type semantics.")
    public ResponseEntity<PolicyRegistrationResponse> registerMarkdownPathFilter(
            @RequestBody @Valid PolicyRegistrationRequest request) {
        return ResponseEntity.ok(policyRegistrationService.registerPolicy(
                FilterEnums.FilterKind.MARKDOWN_PATH, request));
    }

    @PostMapping("/regex-path-filters/policies")
    @Operation(summary = "Register a regex path filter policy",
            description = "Registers a filter using Java regex patterns against the raw string payload. "
                    + "See FilterEnums.PathType.REGEX and Path.java for regex path semantics.")
    public ResponseEntity<PolicyRegistrationResponse> registerRegexPathFilter(
            @RequestBody @Valid PolicyRegistrationRequest request) {
        return ResponseEntity.ok(policyRegistrationService.registerPolicy(
                FilterEnums.FilterKind.REGEX_PATH, request));
    }

    @PostMapping("/ai-path-filters/policies")
    @Operation(summary = "Register an AI-powered filter policy",
            description = "Registers a filter that uses an AI agent (AiFilterRequest) to produce Instruction objects. "
                    + "The AI filter runs as a separate agent session with its own ArtifactKey. "
                    + "See AiFilterSessionEvent for the event emitted when the AI filter executes.")
    public ResponseEntity<PolicyRegistrationResponse> registerAiPathFilter(
            @RequestBody @Valid PolicyRegistrationRequest request) {
        return ResponseEntity.ok(policyRegistrationService.registerPolicy(
                FilterEnums.FilterKind.AI_PATH, request));
    }

    // ── US2: Deactivation & Layer Toggle ─────────────────────────────

    @PostMapping("/policies/deactivate")
    @Operation(summary = "Deactivate a filter policy globally",
            description = "Marks the policy as inactive across all layers. The policy can be re-enabled later with the enable endpoint.")
    public ResponseEntity<DeactivatePolicyResponse> deactivatePolicy(
            @RequestBody @Valid DeactivatePolicyRequest request) {
        return ResponseEntity.ok(policyRegistrationService.deactivatePolicy(request.policyId()));
    }

    @PostMapping("/policies/layers/disable")
    @Operation(summary = "Disable a policy at a specific layer",
            description = "Disables the policy binding at the given layer. Set includeDescendants=true to disable "
                    + "at all child layers as well.")
    public ResponseEntity<TogglePolicyLayerResponse> disablePolicyAtLayer(
            @RequestBody @Valid TogglePolicyLayerRequest request) {
        return ResponseEntity.ok(policyRegistrationService.togglePolicyAtLayer(
                request.policyId(), request.layerId(), false, request.includeDescendants()));
    }

    @PostMapping("/policies/layers/enable")
    @Operation(summary = "Enable a policy at a specific layer",
            description = "Enables the policy binding at the given layer. Set includeDescendants=true to enable "
                    + "at all child layers as well.")
    public ResponseEntity<TogglePolicyLayerResponse> enablePolicyAtLayer(
            @RequestBody @Valid TogglePolicyLayerRequest request) {
        return ResponseEntity.ok(policyRegistrationService.togglePolicyAtLayer(
                request.policyId(), request.layerId(), true, request.includeDescendants()));
    }

    @PostMapping("/policies/layers/update")
    @Operation(summary = "Update a policy's layer binding configuration",
            description = "Updates layer binding properties for a policy — priority, matcher settings, etc.")
    public ResponseEntity<PutPolicyLayerResponse> updatePolicyLayerBinding(
            @RequestBody @Valid PutPolicyLayerRequest request) {
        return ResponseEntity.ok(policyRegistrationService.updatePolicyLayerBinding(
                request.policyId(), request));
    }

    // ── US3: Inspection ──────────────────────────────────────────────

    @PostMapping("/policies/records/recent")
    @Operation(summary = "View recent filter decision records",
            description = "Returns recent filter execution records for a given policy and layer — what was filtered, "
                    + "when, and what decision was made. Useful for debugging when events appear to be missing.")
    public ResponseEntity<ReadRecentFilteredRecordsResponse> getRecentFilteredRecords(
            @RequestBody @Valid ReadRecentFilteredRecordsRequest request) {
        return ResponseEntity.ok(filterDecisionQueryService.getRecentRecordsByPolicyAndLayer(
                request.policyId(), request.layerId(), request.limitOrDefault(), request.cursor()));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private String extractFilterField(PolicyRegistrationEntity entity, String field) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(entity.getFilterJson());
            return node.has(field) ? node.get(field).asText("") : "";
        } catch (Exception e) {
            return "";
        }
    }

    private int extractPriority(PolicyRegistrationEntity entity) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(entity.getFilterJson());
            return node.has("priority") ? node.get("priority").asInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
