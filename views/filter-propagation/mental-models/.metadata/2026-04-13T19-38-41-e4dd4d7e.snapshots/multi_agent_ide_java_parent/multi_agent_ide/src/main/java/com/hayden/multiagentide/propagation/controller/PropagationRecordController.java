package com.hayden.multiagentide.propagation.controller;

import com.hayden.multiagentide.propagation.controller.dto.ReadPropagationRecordsResponse;
import com.hayden.multiagentide.propagation.service.PropagationRecordQueryService;
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
@RequestMapping("/api/propagations/records")
@RequiredArgsConstructor
@Tag(name = "Propagation Records", description = "DEBUG endpoint — audit propagator execution lifecycle. "
        + "NOT for monitoring agent content. Use POST /api/propagations/items/by-node for controller monitoring.")
public class PropagationRecordController {

    private final PropagationRecordQueryService queryService;

    @GetMapping
    @Operation(summary = "List recent propagation execution records (DEBUG only)",
            description = "Returns lightweight audit records of propagator executions (fired/passthrough/transformed/failed). "
                    + "These do NOT contain the full agent request/response payload — they show propagator mechanics only. "
                    + "Use this to debug why a propagator did not fire or to audit propagator firing patterns. "
                    + "For controller monitoring (reading full action payloads by node), use POST /api/propagations/items/by-node instead.")
    public ResponseEntity<ReadPropagationRecordsResponse> recent(
            @Parameter(description = "Filter records to a specific layer ID (optional)") @RequestParam(value = "layerId", required = false) String layerId,
            @Parameter(description = "Maximum number of records to return (default 50)") @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(queryService.recent(layerId, limit));
    }
}
