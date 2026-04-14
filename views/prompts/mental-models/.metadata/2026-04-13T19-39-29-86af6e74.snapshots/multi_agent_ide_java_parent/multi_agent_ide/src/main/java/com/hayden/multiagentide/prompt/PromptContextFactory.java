package com.hayden.multiagentide.prompt;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory for creating PromptContext instances by extracting upstream prev
 * from typed request objects using pattern matching.
 */

@Component
@RequiredArgsConstructor
public class PromptContextFactory {


    private final PromptContributorService promptContributor;

    /**
     * Build a PromptContext by pattern matching on the input object to extract
     * contextId, upstream prev, and previousContext.
     *
     * @param agentType the type of agent this context is for
     * @param input the request object containing typed curation/upstream context fields
     * @param blackboardHistory optional blackboard history
     * @return a PromptContext with all upstream prev collected
     */
    public PromptContext build(
            AgentType agentType,
            AgentModels.AgentRequest input,
            BlackboardHistory blackboardHistory,
            String templateName,
            Map<String, Object> model,
            String modelName,
            OperationContext operationContext,
            DecoratorContext decoratorContext
    ) {
        return build(agentType, input, null, input, blackboardHistory, templateName, model, modelName,
                operationContext, decoratorContext);
    }

    public PromptContext build(
            AgentType agentType,
            AgentModels.AgentRequest input,
            BlackboardHistory blackboardHistory,
            String templateName,
            Map<String, Object> model,
            OperationContext operationContext,
            DecoratorContext decoratorContext
    ) {
        return build(agentType, input, null, input, blackboardHistory, templateName, model, AcpChatOptionsString.DEFAULT_MODEL_NAME,
                operationContext, decoratorContext);
    }

    public PromptContext build(
            AgentType agentType,
            AgentModels.AgentRequest contextRequest,
            AgentModels.AgentRequest previousRequest,
            AgentModels.AgentRequest currentRequest,
            BlackboardHistory blackboardHistory,
            String templateName,
            Map<String, Object> model,
            OperationContext operationContext,
            DecoratorContext decoratorContext
    ) {
        return build(agentType, contextRequest, previousRequest, currentRequest, blackboardHistory, templateName, model, AcpChatOptionsString.DEFAULT_MODEL_NAME,
                operationContext, decoratorContext);
    }

    /**
     * Build a PromptContext by pattern matching on the request used for context
     * extraction while also carrying previous/current requests explicitly.
     */
    public PromptContext build(
            AgentType agentType,
            AgentModels.AgentRequest contextRequest,
            AgentModels.AgentRequest previousRequest,
            AgentModels.AgentRequest currentRequest,
            BlackboardHistory blackboardHistory,
            String templateName,
            Map<String, Object> model,
            String modelName,
            OperationContext operationContext,
            DecoratorContext decoratorContext
    ) {
        switch (contextRequest) {
            case AgentModels.OrchestratorRequest req -> {
            }
            case AgentModels.OrchestratorCollectorRequest req -> {
            }
            case AgentModels.DiscoveryOrchestratorRequest req -> {
                // Discovery orchestrator is at the start - no upstream prev
            }
            case AgentModels.DiscoveryAgentResults req -> {
                // Discovery agent has no upstream curation
            }
            case AgentModels.DiscoveryAgentRequest req -> {
                // Discovery agent has no upstream curation
            }
            case AgentModels.DiscoveryCollectorRequest req -> {
                // Discovery collector has no upstream curation
            }
            case AgentModels.PlanningOrchestratorRequest req -> {
            }
            case AgentModels.PlanningAgentRequest req -> {
            }
            case AgentModels.PlanningCollectorRequest req -> {
            }
            case AgentModels.TicketOrchestratorRequest req -> {
            }
            case AgentModels.TicketAgentRequest req -> {
            }
            case AgentModels.CommitAgentRequest req -> {
            }
            case AgentModels.MergeConflictRequest req -> {
            }
            case AgentModels.AiFilterRequest aiFilterRequest -> {
            }
            case AgentModels.AiPropagatorRequest aiPropagatorRequest -> {
            }
            case AgentModels.AiTransformerRequest aiTransformerRequest -> {
            }
            case AgentModels.TicketCollectorRequest req -> {
            }
            case AgentModels.ContextManagerRequest contextManagerRequest -> {
            }
            case AgentModels.PlanningAgentResults planningAgentResults -> {
            }
            case AgentModels.TicketAgentResults ticketAgentResults -> {
            }
            case AgentModels.ContextManagerRoutingRequest contextManagerRoutingRequest -> {
            }
            case AgentModels.DiscoveryAgentRequests discoveryAgentRequests -> {
            }
            case AgentModels.PlanningAgentRequests planningAgentRequests -> {
            }
            case AgentModels.TicketAgentRequests ticketAgentRequests -> {
            }
            case AgentModels.AgentToAgentRequest ignored -> {
            }
            case AgentModels.AgentToControllerRequest ignored -> {
            }
            case AgentModels.ControllerToAgentRequest ignored -> {
            }
            case AgentModels.InterruptRequest interruptRequest -> {
                switch(interruptRequest) {
                    case AgentModels.InterruptRequest.ContextManagerInterruptRequest contextManagerInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest discoveryAgentDispatchInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest discoveryAgentInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest discoveryCollectorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest discoveryOrchestratorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest orchestratorCollectorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.OrchestratorInterruptRequest orchestratorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest planningAgentDispatchInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.PlanningAgentInterruptRequest planningAgentInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest planningCollectorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest planningOrchestratorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest questionAnswerInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest ticketAgentDispatchInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.TicketAgentInterruptRequest ticketAgentInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.TicketCollectorInterruptRequest ticketCollectorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest ticketOrchestratorInterruptRequest -> {
                    }
                }
            }
        }

        var pc = new PromptContext(
                agentType,
                resolve(contextRequest != null ? contextRequest.contextId() : null),
                blackboardHistory,
                previousRequest,
                currentRequest,
                Map.of(),
                templateName,
                model,
                modelName,
                operationContext,
                decoratorContext.agentName(),
                decoratorContext.actionName(),
                decoratorContext.methodName()
        );

        // Propagate errorDescriptor from the decorator context (populated by AgentExecutor
        // from BlackboardHistory.errorType()) so prompt contributors can be retry-aware.
        if (decoratorContext.errorDescriptor() != null) {
            pc = pc.withErrorDescriptor(decoratorContext.errorDescriptor());
        }

        return pc.toBuilder().promptContributors(this.promptContributor.getContributors(pc)).build();
    }



    private ArtifactKey resolve(ArtifactKey contextId) {
        return contextId;
    }

}
