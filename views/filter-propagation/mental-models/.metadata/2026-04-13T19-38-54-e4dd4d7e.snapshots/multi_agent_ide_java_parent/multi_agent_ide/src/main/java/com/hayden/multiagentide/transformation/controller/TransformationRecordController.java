package com.hayden.multiagentide.transformation.controller;

import com.hayden.multiagentide.transformation.controller.dto.ReadTransformationRecordsResponse;
import com.hayden.multiagentide.transformation.service.TransformationRecordQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transformations/records")
@RequiredArgsConstructor
@Tag(name = "Transformation Records", description = "Query recent transformation execution records")
public class TransformationRecordController {

    private final TransformationRecordQueryService queryService;

    @GetMapping
    @Operation(summary = "List recent transformation execution records",
            description = "Returns recent transformation execution records, optionally filtered by layerId. "
                    + "Transformers reshape controller endpoint responses before they are returned to the caller — "
                    + "these records show what was transformed, when, and at which layer. "
                    + "Use this to audit transformer behavior or debug unexpected response shapes. "
                    + "See ControllerEndpointTransformationIntegration for the transformation pipeline.")
    public ResponseEntity<ReadTransformationRecordsResponse> recent(
            @Parameter(description = "Filter records to a specific layer ID (optional)") @RequestParam(value = "layerId", required = false) String layerId,
            @Parameter(description = "Maximum number of records to return (default 50)") @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(queryService.recent(layerId, limit));
    }
}
