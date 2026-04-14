package com.hayden.multiagentide.transformation.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Request body for registering a new transformer. The executor field contains the transformation logic configuration.")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransformerRegistrationRequest {

    @Schema(description = "Unique name for this transformer registration.", requiredMode = Schema.RequiredMode.REQUIRED, example = "ai-transformer-myEndpoint")
    @NotBlank
    private String name;

    @Schema(description = "Human-readable description of what this transformer does.")
    private String description;

    @Schema(description = "The action/layer path this transformer is associated with.", requiredMode = Schema.RequiredMode.REQUIRED, example = "workflow-agent/coordinateWorkflow")
    @NotBlank
    private String sourcePath;

    @Schema(description = "The kind of transformer. Use AI_TEXT for AI-powered transformers.", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"AI_TEXT", "PASS_THROUGH", "SCRIPT"}, example = "AI_TEXT")
    @NotBlank
    private String transformerKind;

    @Schema(description = "Execution priority. Lower values run first.", example = "100")
    private int priority;

    @Schema(description = "Whether this transformer replaces the full endpoint response.")
    private boolean replaceEndpointResponse;

    @Schema(description = "Whether child nodes inherit this transformer.")
    private boolean isInheritable;

    @Schema(description = "Whether this transformer's output propagates to the parent node.")
    private boolean isPropagatedToParent;

    @Schema(description = "Layer bindings that control which layers and stages trigger this transformer.")
    private List<LayerBindingRequest> layerBindings;

    @Schema(description = "Transformer executor configuration. For AI transformers, provide an object with executorType, registrarPrompt, sessionMode, and configVersion.")
    private Object executor;

    @Schema(description = "Whether to activate the transformer immediately after registration.")
    private boolean activate;

    @Schema(description = "Binds a transformer to a specific layer and stage.")
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

        @Schema(description = "The stage to match on.", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"CONTROLLER_ENDPOINT_RESPONSE"}, example = "CONTROLLER_ENDPOINT_RESPONSE")
        private String matchOn;
    }
}
