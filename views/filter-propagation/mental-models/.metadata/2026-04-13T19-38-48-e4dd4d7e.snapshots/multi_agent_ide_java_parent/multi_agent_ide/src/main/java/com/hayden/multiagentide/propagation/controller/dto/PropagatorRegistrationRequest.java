package com.hayden.multiagentide.propagation.controller.dto;

import com.hayden.multiagentide.model.executor.AiPropagatorTool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Request body for registering a new propagator. The executor field (with registrarPrompt) is the most important part — it contains the AI instructions.")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PropagatorRegistrationRequest {

    @Schema(description = "Unique name for this propagator registration.", requiredMode = Schema.RequiredMode.REQUIRED, example = "ai-prop-myAction-action-request")
    @NotBlank
    private String name;

    @Schema(description = "Human-readable description of what this propagator does.")
    private String description;

    @Schema(description = "The action/layer path this propagator is associated with.", requiredMode = Schema.RequiredMode.REQUIRED, example = "workflow-agent/coordinateWorkflow")
    @NotBlank
    private String sourcePath;

    @Schema(description = "The kind of propagator. Use AI_TEXT for AI-powered propagators.", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"AI_TEXT", "PASS_THROUGH", "SCRIPT"}, example = "AI_TEXT")
    @NotBlank
    private String propagatorKind;

    @Schema(description = "Execution priority. Lower values run first.", example = "100")
    private int priority;

    @Schema(description = "Propagation mode. Null uses the default.")
    private String propagationMode;

    @Schema(description = "Whether child nodes inherit this propagator.")
    private boolean isInheritable;

    @Schema(description = "Whether this propagator's output propagates to the parent node.")
    private boolean isPropagatedToParent;

    @Schema(description = "Layer bindings that control which layers and stages trigger this propagator.")
    private List<LayerBindingRequest> layerBindings;

    @Schema(description = "AI propagator executor configuration. Contains registrarPrompt (the AI instructions), sessionMode, and configVersion. Only AI_PROPAGATOR executors are supported via this API.")
    private AiPropagatorTool executor;

    @Schema(description = "Whether to activate the propagator immediately after registration.")
    private boolean activate;

    @Schema(description = "Binds a propagator to a specific layer and stage.")
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

        @Schema(description = "Optional matcher key for advanced filtering.")
        private String matcherKey;

        @Schema(description = "Optional matcher type for advanced filtering.")
        private String matcherType;

        @Schema(description = "Optional matcher text for advanced filtering.")
        private String matcherText;

        @Schema(description = "The stage to match on: ACTION_REQUEST or ACTION_RESPONSE.", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTION_REQUEST", "ACTION_RESPONSE"}, example = "ACTION_REQUEST")
        private String matchOn;
    }
}
