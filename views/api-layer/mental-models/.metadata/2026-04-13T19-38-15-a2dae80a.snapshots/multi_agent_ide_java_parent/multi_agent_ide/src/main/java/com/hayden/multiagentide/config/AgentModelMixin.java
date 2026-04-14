package com.hayden.multiagentide.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.UpstreamContext;

/**
 * Jackson mix-in for Artifact.AgentModel interface to enable polymorphic serialization/deserialization.
 * Uses @class property for full flexibility with all AgentModel implementations.
 */
public interface AgentModelMixin {
    
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@class"
    )
    @JsonSubTypes({
        // Interrupt Requests
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.OrchestratorInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.PlanningAgentInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.PlanningCollectorInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.TicketAgentInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.TicketCollectorInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.ContextManagerInterruptRequest.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.QuestionAnswerInterruptRequest.class),
        
        // Agent Requests
        @JsonSubTypes.Type(value = AgentModels.OrchestratorRequest.class),
        @JsonSubTypes.Type(value = AgentModels.OrchestratorCollectorRequest.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryOrchestratorRequest.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryAgentRequest.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryAgentRequests.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryCollectorRequest.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningOrchestratorRequest.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningAgentRequest.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningAgentRequests.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningCollectorRequest.class),
        @JsonSubTypes.Type(value = AgentModels.TicketOrchestratorRequest.class),
        @JsonSubTypes.Type(value = AgentModels.TicketAgentRequest.class),
        @JsonSubTypes.Type(value = AgentModels.TicketAgentRequests.class),
        @JsonSubTypes.Type(value = AgentModels.CommitAgentRequest.class),
        @JsonSubTypes.Type(value = AgentModels.TicketCollectorRequest.class),
        @JsonSubTypes.Type(value = AgentModels.ContextManagerRequest.class),
        
        // Agent Results
        @JsonSubTypes.Type(value = AgentModels.OrchestratorAgentResult.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryOrchestratorResult.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningOrchestratorResult.class),
        @JsonSubTypes.Type(value = AgentModels.TicketOrchestratorResult.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryAgentResult.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningAgentResult.class),
        @JsonSubTypes.Type(value = AgentModels.TicketAgentResult.class),
        @JsonSubTypes.Type(value = AgentModels.CommitAgentResult.class),
        @JsonSubTypes.Type(value = AgentModels.ReviewAgentResult.class),
        @JsonSubTypes.Type(value = AgentModels.MergerAgentResult.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryCollectorResult.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningCollectorResult.class),
        @JsonSubTypes.Type(value = AgentModels.OrchestratorCollectorResult.class),
        @JsonSubTypes.Type(value = AgentModels.TicketCollectorResult.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningAgentResults.class),
        @JsonSubTypes.Type(value = AgentModels.TicketAgentResults.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryAgentResults.class),
        
        // Routing
        @JsonSubTypes.Type(value = AgentModels.OrchestratorRouting.class),
        @JsonSubTypes.Type(value = AgentModels.OrchestratorCollectorRouting.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryOrchestratorRouting.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryAgentRouting.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryCollectorRouting.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryAgentDispatchRouting.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningOrchestratorRouting.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningAgentRouting.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningCollectorRouting.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningAgentDispatchRouting.class),
        @JsonSubTypes.Type(value = AgentModels.TicketOrchestratorRouting.class),
        @JsonSubTypes.Type(value = AgentModels.TicketAgentRouting.class),
        @JsonSubTypes.Type(value = AgentModels.TicketCollectorRouting.class),
        @JsonSubTypes.Type(value = AgentModels.TicketAgentDispatchRouting.class),
        @JsonSubTypes.Type(value = AgentModels.ContextManagerRoutingRequest.class),
        @JsonSubTypes.Type(value = AgentModels.ContextManagerResultRouting.class),
        
        // Curations
        @JsonSubTypes.Type(value = AgentModels.DiscoveryCuration.class),
        @JsonSubTypes.Type(value = AgentModels.PlanningCuration.class),
        @JsonSubTypes.Type(value = AgentModels.TicketCuration.class),
        
        // Supporting types
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.StructuredChoice.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.ConfirmationItem.class),
        @JsonSubTypes.Type(value = AgentModels.InterruptRequest.InterruptResolution.class),
        @JsonSubTypes.Type(value = AgentModels.AgentInteraction.class),
        @JsonSubTypes.Type(value = AgentModels.DelegationPlan.class),
        @JsonSubTypes.Type(value = AgentModels.ModuleOverview.class),
        @JsonSubTypes.Type(value = AgentModels.CodeMap.class),
        @JsonSubTypes.Type(value = AgentModels.Recommendation.class),
        @JsonSubTypes.Type(value = AgentModels.QueryFindings.class),
        @JsonSubTypes.Type(value = AgentModels.TicketDependency.class),
        @JsonSubTypes.Type(value = AgentModels.DiscoveryOrchestratorRequested.class),
        
        // Upstream Contexts (collector subtypes only)
        @JsonSubTypes.Type(value = UpstreamContext.DiscoveryCollectorContext.class),
        @JsonSubTypes.Type(value = UpstreamContext.PlanningCollectorContext.class),
        @JsonSubTypes.Type(value = UpstreamContext.TicketCollectorContext.class)
    })
    interface WithTypeInfo {}
}
