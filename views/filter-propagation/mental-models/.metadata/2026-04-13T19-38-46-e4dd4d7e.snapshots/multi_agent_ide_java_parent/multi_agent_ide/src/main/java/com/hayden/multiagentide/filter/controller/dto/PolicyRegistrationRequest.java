package com.hayden.multiagentide.filter.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Request body for registering a new filter policy. The executor field contains the filter logic configuration (AI, script, etc.).")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRegistrationRequest {

    @Schema(description = "Unique name for this policy registration.", requiredMode = Schema.RequiredMode.REQUIRED, example = "ai-filter-myAction")
    private String name;

    @Schema(description = "Human-readable description of what this policy does.")
    private String description;

    @Schema(description = "The action/layer path this policy is associated with.", requiredMode = Schema.RequiredMode.REQUIRED, example = "workflow-agent/coordinateWorkflow")
    private String sourcePath;

    @Schema(description = "Execution priority. Lower values run first.", example = "100")
    private int priority;

    @Schema(description = "Whether child nodes inherit this policy.")
    private boolean isInheritable;

    @Schema(description = "Whether this policy's output propagates to the parent node.")
    private boolean isPropagatedToParent;

    @Schema(description = "Layer bindings that control which layers and stages trigger this policy.")
    private List<LayerBindingRequest> layerBindings;

    @Schema(description = "Filter executor configuration. For AI filters, provide an object with executorType, registrarPrompt, sessionMode, and configVersion.")
    private Object executor;

    @Schema(description = "Whether to activate the policy immediately after registration.")
    private boolean activate;

    @Schema(description = "Binds a policy to a specific layer and stage.")
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayerBindingRequest {

        @Schema(description = "The layer ID to bind to. May contain slashes.", requiredMode = Schema.RequiredMode.REQUIRED, example = "workflow-agent/coordinateWorkflow")
        private String layerId;

        @Schema(description = "Layer type filter. Null matches all types.")
        private String layerType;

        @Schema(description = "Layer key filter. Null matches all keys.")
        private String layerKey;

        @Schema(description = "Whether this binding is active.")
        private boolean enabled;

        @Schema(description = "Whether to also match descendant layers.")
        private boolean includeDescendants;

        @Schema(description = "Whether child nodes inherit this binding.")
        private boolean isInheritable;

        @Schema(description = "Whether this binding propagates to parent.")
        private boolean isPropagatedToParent;

        @Schema(description = "Optional matcher key for advanced filtering.", example = "TEXT")
        private String matcherKey;

        @Schema(description = "Optional matcher type for advanced filtering.", example = "EQUALS")
        private String matcherType;

        @Schema(description = "Optional matcher text for advanced filtering.")
        private String matcherText;

        @Schema(description = "The stage to match on.", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"PROMPT_CONTRIBUTOR", "GRAPH_EVENT"}, example = "PROMPT_CONTRIBUTOR")
        private String matchOn;
    }
}
