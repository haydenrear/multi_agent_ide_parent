package com.hayden.multiagentide.filter.model.layer;

import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.filter.FilterFn;
import com.hayden.multiagentide.filter.config.FilterConfigProperties;
import com.hayden.multiagentide.llm.AgentLlmExecutor;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.Builder;
import lombok.experimental.Delegate;

import java.util.Map;
import java.util.Optional;

/**
 * Runtime context supplied to filter execution.
 * Extends LayerCtx so filters have access to the layer context they're executing in.
 */
public non-sealed interface FilterContext extends LayerCtx {

    interface PathFilterContext extends FilterContext {
        FilterContext filterContext();
    }

    @Builder(toBuilder = true)
    record AiFilterContext(
            @Delegate FilterContext filterContext,
            String templateName,
            PromptContext promptContext,
            Map<String, Object> model,
            ToolContext toolContext,
            Class<AgentModels.AiFilterResult> responseClass,
            OperationContext context,
            AgentLlmExecutor.DirectExecutorArgs<AgentModels.AiFilterResult> directExecutorArgs) implements PathFilterContext {
    }

    String layerId();

    ArtifactKey key();

    FilterConfigProperties filterConfigProperties();

    void setFilterConfigProperties(FilterConfigProperties filterConfigProperties);

    ObjectMapper objectMapper();

    void setObjectMapper(ObjectMapper objectMapper);

    default Optional<FilterFn> fn()  {
        return Optional.empty();
    }

}
