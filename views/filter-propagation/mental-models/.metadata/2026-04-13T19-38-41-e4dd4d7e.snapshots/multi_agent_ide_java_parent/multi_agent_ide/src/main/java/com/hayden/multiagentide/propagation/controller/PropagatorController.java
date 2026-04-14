package com.hayden.multiagentide.propagation.controller;

import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.propagation.controller.dto.*;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import com.hayden.multiagentide.propagation.service.PropagatorAttachableCatalogService;
import com.hayden.multiagentide.propagation.service.PropagatorDiscoveryService;
import com.hayden.multiagentide.propagation.service.PropagatorRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/propagators")
@RequiredArgsConstructor
@Tag(name = "Propagators", description = "Manage propagator registrations and layer assignments")
public class PropagatorController {

    private final PropagatorRegistrationService registrationService;
    private final PropagatorDiscoveryService discoveryService;
    private final PropagatorAttachableCatalogService attachableCatalogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/attachables")
    @Operation(summary = "List attachable propagator targets",
            description = "Source of truth for valid attachment targets (event types, layers) for propagator registrations. "
                    + "Propagators are the escalatory mechanism for extracting out-of-domain (OOD) signals from agent execution — "
                    + "check this first to understand what can be propagated.")
    public ResponseEntity<ReadPropagatorAttachableTargetsResponse> attachables() {
        return ResponseEntity.ok(attachableCatalogService.readAttachableTargets());
    }

    @PostMapping("/registrations")
    @Operation(summary = "Register a new propagator",
            description = "Registers a propagator that will fire on matching events at the specified layer. "
                    + "AI propagators (kind=AI) create AiPropagatorRequest sessions. "
                    + "When an AI propagator escalates via AskUserQuestionTool, it creates an interrupt "
                    + "resolvable through the Interrupts API.")
    public ResponseEntity<PropagatorRegistrationResponse> register(@RequestBody @Valid PropagatorRegistrationRequest request) {
        return ResponseEntity.ok(registrationService.register(request));
    }

    @PostMapping("/registrations/by-layer")
    @Operation(summary = "List propagators registered at a layer",
            description = "Returns all active propagator registrations bound to the given layer, "
                    + "with summary information including name, kind, status, and priority. "
                    + "Accepts layerId in request body to support slash-containing layer identifiers (e.g. workflow-agent/coordinateWorkflow).")
    public ResponseEntity<ReadPropagatorsByLayerResponse> byLayer(@RequestBody @Valid ReadPropagatorsByLayerRequest request) {
        String layerId = request.layerId();
        List<ReadPropagatorsByLayerResponse.PropagatorSummary> summaries = discoveryService.getActivePropagatorsByLayer(layerId).stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ReadPropagatorsByLayerResponse.builder().layerId(layerId).propagators(summaries).totalCount(summaries.size()).build());
    }

    @PostMapping("/registrations/{registrationId}/deactivate")
    @Operation(summary = "Deactivate a propagator registration",
            description = "Sets the propagator status to INACTIVE so it no longer fires on matching events. "
                    + "The registration is preserved and can be re-activated later.")
    public ResponseEntity<DeactivatePropagatorResponse> deactivate(@PathVariable String registrationId) {
        return ResponseEntity.ok(registrationService.deactivate(registrationId));
    }

    @PostMapping("/registrations/{registrationId}/activate")
    @Operation(summary = "Activate a propagator registration",
            description = "Sets the propagator status to ACTIVE so it fires on matching events at its bound layers. "
                    + "Auto-bootstrapped propagators start INACTIVE and must be explicitly activated.")
    public ResponseEntity<ActivatePropagatorResponse> activate(@PathVariable String registrationId) {
        return ResponseEntity.ok(registrationService.activate(registrationId));
    }

    @PutMapping("/registrations/{registrationId}/layers")
    @Operation(summary = "Update a propagator's layer binding",
            description = "Updates the layer binding for a propagator registration. "
                    + "The layerId is provided inside the request body layerBinding to support slash-containing layer identifiers.")
    public ResponseEntity<PutPropagatorLayerResponse> updateLayer(@PathVariable String registrationId,
                                                                  @RequestBody @Valid PutPropagatorLayerRequest request) {
        return ResponseEntity.ok(registrationService.updateLayerBinding(registrationId, request));
    }

    @PutMapping("/registrations/{registrationId}/session-mode")
    @Operation(summary = "Update a propagator's session mode",
            description = "Updates the session reuse strategy for the propagator's AI executor. "
                    + "PER_INVOCATION=fresh session each call; SAME_SESSION_FOR_ACTION=reuse within one action pair; "
                    + "SAME_SESSION_FOR_ALL=single shared session; SAME_SESSION_FOR_AGENT=reuse per agent.")
    public ResponseEntity<PropagatorRegistrationResponse> updateSessionMode(
            @PathVariable String registrationId,
            @RequestBody @Valid UpdateSessionModeRequest request) {
        return ResponseEntity.ok(registrationService.updateSessionMode(registrationId, request.sessionMode()));
    }

    @PutMapping("/registrations/session-mode/all")
    @Operation(summary = "Update session mode for all propagators",
            description = "Bulk-updates the session mode for every registered propagator. "
                    + "Useful for switching all propagators to PER_INVOCATION to prevent session bloat.")
    public ResponseEntity<UpdateAllSessionModeResponse> updateAllSessionModes(
            @RequestBody @Valid UpdateSessionModeRequest request) {
        return ResponseEntity.ok(registrationService.updateAllSessionModes(request.sessionMode()));
    }

    private ReadPropagatorsByLayerResponse.PropagatorSummary toSummary(PropagatorRegistrationEntity entity) {
        try {
            var node = objectMapper.readTree(entity.getPropagatorJson());
            return ReadPropagatorsByLayerResponse.PropagatorSummary.builder()
                    .registrationId(entity.getRegistrationId())
                    .name(node.path("name").asText(entity.getRegistrationId()))
                    .description(node.path("description").asText(""))
                    .propagatorKind(entity.getPropagatorKind())
                    .status(entity.getStatus())
                    .priority(node.path("priority").asInt(0))
                    .build();
        } catch (Exception e) {
            return ReadPropagatorsByLayerResponse.PropagatorSummary.builder()
                    .registrationId(entity.getRegistrationId())
                    .name(entity.getRegistrationId())
                    .description("")
                    .propagatorKind(entity.getPropagatorKind())
                    .status(entity.getStatus())
                    .priority(0)
                    .build();
        }
    }
}
