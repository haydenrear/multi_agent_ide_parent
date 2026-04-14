package com.hayden.multiagentide.transformation.controller;

import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.transformation.controller.dto.*;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationEntity;
import com.hayden.multiagentide.transformation.service.TransformerAttachableCatalogService;
import com.hayden.multiagentide.transformation.service.TransformerDiscoveryService;
import com.hayden.multiagentide.transformation.service.TransformerRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transformers")
@RequiredArgsConstructor
@Tag(name = "Transformers", description = "Manage transformer registrations and layer assignments")
public class TransformerController {

    private final TransformerRegistrationService registrationService;
    private final TransformerDiscoveryService discoveryService;
    private final TransformerAttachableCatalogService attachableCatalogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/attachables")
    @Operation(summary = "List attachable transformer targets",
            description = "Source of truth for valid controller/endpoint combinations that transformers can bind to. "
                    + "Transformers reshape controller endpoint responses before they are returned to the caller. "
                    + "See ControllerEndpointTransformationIntegration for the transformation pipeline.")
    public ResponseEntity<ReadTransformerAttachableTargetsResponse> attachables() {
        return ResponseEntity.ok(attachableCatalogService.readAttachableTargets());
    }

    @PostMapping("/registrations")
    @Operation(summary = "Register a new transformer",
            description = "Registers a transformer that will process responses from the specified controller endpoint. "
                    + "AI transformers (kind=AI) create AiTransformerRequest sessions.")
    public ResponseEntity<TransformerRegistrationResponse> register(@RequestBody @Valid TransformerRegistrationRequest request) {
        return ResponseEntity.ok(registrationService.register(request));
    }

    @PostMapping("/registrations/by-layer")
    @Operation(summary = "List transformers registered at a layer",
            description = "Returns all active transformer registrations bound to the given layer. "
                    + "Accepts layerId in request body to support slash-containing layer identifiers (e.g. workflow-agent/coordinateWorkflow).")
    public ResponseEntity<ReadTransformersByLayerResponse> byLayer(@RequestBody @Valid ReadTransformersByLayerRequest request) {
        String layerId = request.layerId();
        List<ReadTransformersByLayerResponse.TransformerSummary> summaries = discoveryService.getActiveTransformersByLayer(layerId).stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ReadTransformersByLayerResponse.builder().layerId(layerId).transformers(summaries).totalCount(summaries.size()).build());
    }

    @PostMapping("/registrations/{registrationId}/deactivate")
    @Operation(summary = "Deactivate a transformer registration")
    public ResponseEntity<DeactivateTransformerResponse> deactivate(@PathVariable String registrationId) {
        return ResponseEntity.ok(registrationService.deactivate(registrationId));
    }

    @PutMapping("/registrations/{registrationId}/layers")
    @Operation(summary = "Update a transformer's layer binding",
            description = "Updates the layer binding for a transformer registration. "
                    + "The layerId is provided inside the request body layerBinding to support slash-containing layer identifiers.")
    public ResponseEntity<PutTransformerLayerResponse> updateLayer(@PathVariable String registrationId,
                                                                   @RequestBody @Valid PutTransformerLayerRequest request) {
        return ResponseEntity.ok(registrationService.updateLayerBinding(registrationId, request));
    }

    private ReadTransformersByLayerResponse.TransformerSummary toSummary(TransformerRegistrationEntity entity) {
        try {
            var node = objectMapper.readTree(entity.getTransformerJson());
            return ReadTransformersByLayerResponse.TransformerSummary.builder()
                    .registrationId(entity.getRegistrationId())
                    .name(node.path("name").asText(entity.getRegistrationId()))
                    .description(node.path("description").asText(""))
                    .transformerKind(entity.getTransformerKind())
                    .status(entity.getStatus())
                    .priority(node.path("priority").asInt(0))
                    .build();
        } catch (Exception e) {
            return ReadTransformersByLayerResponse.TransformerSummary.builder()
                    .registrationId(entity.getRegistrationId())
                    .name(entity.getRegistrationId())
                    .description("")
                    .transformerKind(entity.getTransformerKind())
                    .status(entity.getStatus())
                    .priority(0)
                    .build();
        }
    }
}
