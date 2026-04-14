package com.hayden.multiagentide.filter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hayden.multiagentide.filter.model.executor.ExecutableTool;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentide.filter.model.interpreter.DispatchingInterpreter;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Path-instruction filtering over partial document/object structure.
 * All serialization (including instruction deserialization) is Jackson/JSON.
 */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PathFilter(
        String id,
        String name,
        String description,
        String sourcePath,
        ExecutableTool<String, List<Instruction>, FilterContext.PathFilterContext> executor,
        com.hayden.acp_cdc_ai.acp.filter.FilterEnums.PolicyStatus status,
        int priority,
        Instant createdAt,
        Instant updatedAt
) implements Filter<String, PathFilter.PathFilterResult, FilterContext.PathFilterContext> {

    private static final DispatchingInterpreter DISPATCHING_INTERPRETER = new DispatchingInterpreter();

    public record PathFilterResult(String r, List<Instruction> instructions, FilterDescriptor descriptor) {}

    @Override
    public PathFilterResult apply(String s, FilterContext.PathFilterContext ctx) {
        FilterResult<List<Instruction>> executionResult = executor.apply(s, ctx);
        List<Instruction> instructions = executionResult == null ? null : executionResult.t();
        if (instructions == null) {
            instructions = List.of();
        }
        FilterDescriptor descriptor = executionResult == null || executionResult.descriptor() == null
                ? new FilterDescriptor.NoOpFilterDescriptor()
                : executionResult.descriptor();

        var r = DISPATCHING_INTERPRETER.apply(s, instructions);

        if (r.isOk())
            return new PathFilterResult(r.unwrap(), instructions, descriptor);

        return new PathFilterResult(s, instructions, descriptor);
    }

}
