package com.hayden.multiagentide.filter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.filter.model.executor.AiFilterTool;
import com.hayden.multiagentide.filter.model.interpreter.DispatchingInterpreter;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Path-instruction filtering over partial document/object structure.
 * All serialization (including instruction deserialization) is Jackson/JSON.
 */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiPathFilter(
        String id,
        String name,
        String description,
        String sourcePath,
        AiFilterTool<AgentModels.AiFilterRequest, AgentModels.AiFilterResult> executor,
        com.hayden.acp_cdc_ai.acp.filter.FilterEnums.PolicyStatus status,
        int priority,
        Instant createdAt,
        Instant updatedAt
) implements Filter<AgentModels.AiFilterRequest, AiPathFilter.AiPathFilterResult, FilterContext.AiFilterContext> {

    private static final DispatchingInterpreter DISPATCHING_INTERPRETER = new DispatchingInterpreter();

    @Builder(toBuilder = true)
    public record AiPathFilterResult(String r, AgentModels.AiFilterResult res, FilterDescriptor descriptor) {
        public List<Instruction> instructions() {
            return res.output();
        }
    }

    @Override
    public AiPathFilterResult apply(AgentModels.AiFilterRequest s, FilterContext.AiFilterContext ctx) {
        var executionResult = executor.apply(s, ctx);

        FilterDescriptor descriptor = executionResult.descriptor() == null
                ? new FilterDescriptor.NoOpFilterDescriptor()
                : executionResult.descriptor();

        var r = DISPATCHING_INTERPRETER.apply(s.input(), executionResult.t().output());

        if (r.isOk())
            return new AiPathFilterResult(r.unwrap(), executionResult.t(), descriptor);

        return new AiPathFilterResult(s.input(), executionResult.t(), descriptor);
    }

}
