package com.hayden.multiagentide.filter.model.decision;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentide.filter.model.layer.Layer;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Records the outcome of applying a single filter policy to a payload.
 */
@Builder(toBuilder = true)
public record FilterDecisionRecord<I, O>(
        String decisionId,
        String policyId,
        FilterEnums.FilterKind filterType,
        Layer layer,
        FilterEnums.FilterAction action,
        I input,
        O output,
        List<Instruction> appliedInstructions,
        String errorMessage,
        Instant createdAt
) {
}
