package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.SomeOf;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentide.model.PropagationOutput;
import com.hayden.multiagentide.template.ConsolidationTemplate;
import com.hayden.multiagentide.template.DelegationTemplate;
import com.hayden.multiagentide.template.DiscoveryReport;
import com.hayden.multiagentide.template.MemoryReference;
import com.hayden.multiagentide.template.PlanningTicket;
import com.hayden.multiagentide.model.merge.WorktreeCommitMetadata;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.TransformationOutput;
import io.micrometer.common.util.StringUtils;
import lombok.Builder;
import lombok.With;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface AgentModels {

    enum InteractionType {
        AGENT_MESSAGE,
        USER_MESSAGE,
        INTERRUPT_REQUEST,
        RESULT_HANDOFF
    }

    enum CurationPhase {
        ORCHESTRATION,
        DISCOVERY_CURATION,
        DISCOVERY_AGENT,
        PLANNING_CURATION,
        PLANNING_AGENT,
        TICKET_CURATION,
        TICKET_AGENT,
        INTERRUPT,
        OTHER
    }

    sealed interface AgentResult extends AgentContext
            permits AgentCallResult, ControllerCallResult, ControllerResponseResult, AiFilterResult, AiPropagatorResult, AiTransformerResult, CommitAgentResult, DiscoveryAgentResult, DiscoveryCollectorResult, DiscoveryOrchestratorResult, MergeConflictResult, MergerAgentResult, OrchestratorAgentResult, OrchestratorCollectorResult, PlanningAgentResult, PlanningCollectorResult, PlanningOrchestratorResult, ReviewAgentResult, TicketAgentResult, TicketCollectorResult, TicketOrchestratorResult

    {
        WorktreeSandboxContext worktreeContext();

        @Override
        default String computeHash(Artifact.HashContext hashContext) {
            return hashContext.hash(prettyPrint());
        }
    }

    sealed interface DispatchedRequest
    permits TicketAgentRequest, PlanningAgentRequest, DiscoveryAgentRequest{

    }


    sealed interface AgentRequest extends AgentContext
            permits
//          Here for demonstration purposes - do not delete this comment or reformat this permits clause
//          Here is the happy path ordering for the requests
            OrchestratorRequest,
            DiscoveryOrchestratorRequest,
            DiscoveryAgentRequests,
            DiscoveryAgentRequest,
//            DiscoveryAgentResults,
            DiscoveryCollectorRequest,
            PlanningOrchestratorRequest,
            PlanningAgentRequests,
            PlanningAgentRequest,
//            PlanningAgentResults,
            PlanningCollectorRequest,
            TicketOrchestratorRequest,
            TicketAgentRequests,
            TicketAgentRequest,
            CommitAgentRequest,
            MergeConflictRequest,
//            TicketAgentResults,
            TicketCollectorRequest,
            OrchestratorCollectorRequest,
//          gets routed to by agents to refine context or after
//            merger/review to reroute to requesting agent,
//            or when in invalid status or degenerate loop
            ContextManagerRequest,
            ContextManagerRoutingRequest,
//          There exist various interrupt request types for each
//            of above agents associated with the requests, can reroute,
//            then get rerouted back
            InterruptRequest,
            AiFilterRequest,
            AiPropagatorRequest,
            AiTransformerRequest,
            ResultsRequest,
//          Communication request types for inter-agent and controller topology
            AgentToAgentRequest,
            AgentToControllerRequest,
            ControllerToAgentRequest
    {

        @JsonIgnore
        default String goalExtraction() {
            return switch (this) {
                case OrchestratorRequest r -> r.goal();
                case OrchestratorCollectorRequest r -> r.goal();
                case DiscoveryOrchestratorRequest r -> r.goal();
                case DiscoveryAgentRequest r -> r.goal();
                case DiscoveryAgentRequests r -> r.goal();
                case DiscoveryCollectorRequest r -> r.goal();
                case PlanningOrchestratorRequest r -> r.goal();
                case PlanningAgentRequest r -> r.goal();
                case PlanningAgentRequests r -> r.goal();
                case PlanningCollectorRequest r -> r.goal();
                case TicketOrchestratorRequest r -> r.goal();
                case TicketAgentRequest r -> r.ticketDetails();
                case CommitAgentRequest r -> r.goal();
                case MergeConflictRequest r -> r.goal();
                case TicketAgentRequests r -> r.goal();
                case TicketCollectorRequest r -> r.goal();
                case ContextManagerRequest r -> r.goal();
                case ContextManagerRoutingRequest r -> r.reason();
                case InterruptRequest.OrchestratorInterruptRequest r -> r.reason();
                case InterruptRequest.OrchestratorCollectorInterruptRequest r -> r.reason();
                case InterruptRequest.DiscoveryOrchestratorInterruptRequest r -> r.reason();
                case InterruptRequest.DiscoveryAgentInterruptRequest r -> r.reason();
                case InterruptRequest.DiscoveryCollectorInterruptRequest r -> r.reason();
                case InterruptRequest.DiscoveryAgentDispatchInterruptRequest r -> r.reason();
                case InterruptRequest.PlanningOrchestratorInterruptRequest r -> r.reason();
                case InterruptRequest.PlanningAgentInterruptRequest r -> r.reason();
                case InterruptRequest.PlanningCollectorInterruptRequest r -> r.reason();
                case InterruptRequest.PlanningAgentDispatchInterruptRequest r -> r.reason();
                case InterruptRequest.TicketOrchestratorInterruptRequest r -> r.reason();
                case InterruptRequest.TicketAgentInterruptRequest r -> r.reason();
                case InterruptRequest.TicketCollectorInterruptRequest r -> r.reason();
                case InterruptRequest.TicketAgentDispatchInterruptRequest r -> r.reason();
                case InterruptRequest.ContextManagerInterruptRequest r -> r.reason();
                case InterruptRequest.QuestionAnswerInterruptRequest r -> r.reason();
                case DiscoveryAgentResults ignored -> null;
                case PlanningAgentResults ignored -> null;
                case TicketAgentResults ignored -> null;
                case AiFilterRequest aiFilterRequest -> aiFilterRequest.goal;
                case AiPropagatorRequest aiPropagatorRequest -> aiPropagatorRequest.goal();
                case AiTransformerRequest aiTransformerRequest -> aiTransformerRequest.goal();
                case AgentToAgentRequest r -> r.goal();
                case AgentToControllerRequest r -> r.goal();
                case ControllerToAgentRequest r -> r.goal();
            };
        }

        @JsonIgnore
        default String phaseExtraction() {
            return switch (this) {
                case OrchestratorRequest r -> phaseOrFallback(r.phase(), "ORCHESTRATOR");
                case OrchestratorCollectorRequest r -> phaseOrFallback(r.phase(), "ORCHESTRATOR_COLLECTOR");
                case DiscoveryOrchestratorRequest ignored -> "DISCOVERY_ORCHESTRATOR";
                case DiscoveryAgentRequests ignored -> "DISCOVERY_DISPATCH";
                case DiscoveryAgentRequest ignored -> "DISCOVERY_AGENT";
                case DiscoveryCollectorRequest ignored -> "DISCOVERY_COLLECTOR";
                case PlanningOrchestratorRequest ignored -> "PLANNING_ORCHESTRATOR";
                case PlanningAgentRequests ignored -> "PLANNING_DISPATCH";
                case PlanningAgentRequest ignored -> "PLANNING_AGENT";
                case PlanningCollectorRequest ignored -> "PLANNING_COLLECTOR";
                case TicketOrchestratorRequest ignored -> "TICKET_ORCHESTRATOR";
                case TicketAgentRequests ignored -> "TICKET_DISPATCH";
                case TicketAgentRequest ignored -> "TICKET_AGENT";
                case CommitAgentRequest ignored -> "COMMIT_AGENT";
                case MergeConflictRequest ignored -> "MERGE_CONFLICT_AGENT";
                case TicketCollectorRequest ignored -> "TICKET_COLLECTOR";
                case ContextManagerRequest ignored -> "CONTEXT_MANAGER";
                case ContextManagerRoutingRequest ignored -> "CONTEXT_MANAGER_ROUTING";
                case InterruptRequest.OrchestratorInterruptRequest ignored -> "ORCHESTRATOR_INTERRUPT";
                case InterruptRequest.OrchestratorCollectorInterruptRequest ignored -> "ORCHESTRATOR_COLLECTOR_INTERRUPT";
                case InterruptRequest.DiscoveryOrchestratorInterruptRequest ignored -> "DISCOVERY_ORCHESTRATOR_INTERRUPT";
                case InterruptRequest.DiscoveryAgentInterruptRequest ignored -> "DISCOVERY_AGENT_INTERRUPT";
                case InterruptRequest.DiscoveryCollectorInterruptRequest ignored -> "DISCOVERY_COLLECTOR_INTERRUPT";
                case InterruptRequest.DiscoveryAgentDispatchInterruptRequest ignored -> "DISCOVERY_DISPATCH_INTERRUPT";
                case InterruptRequest.PlanningOrchestratorInterruptRequest ignored -> "PLANNING_ORCHESTRATOR_INTERRUPT";
                case InterruptRequest.PlanningAgentInterruptRequest ignored -> "PLANNING_AGENT_INTERRUPT";
                case InterruptRequest.PlanningCollectorInterruptRequest ignored -> "PLANNING_COLLECTOR_INTERRUPT";
                case InterruptRequest.PlanningAgentDispatchInterruptRequest ignored -> "PLANNING_DISPATCH_INTERRUPT";
                case InterruptRequest.TicketOrchestratorInterruptRequest ignored -> "TICKET_ORCHESTRATOR_INTERRUPT";
                case InterruptRequest.TicketAgentInterruptRequest ignored -> "TICKET_AGENT_INTERRUPT";
                case InterruptRequest.TicketCollectorInterruptRequest ignored -> "TICKET_COLLECTOR_INTERRUPT";
                case InterruptRequest.TicketAgentDispatchInterruptRequest ignored -> "TICKET_DISPATCH_INTERRUPT";
                case InterruptRequest.ContextManagerInterruptRequest ignored -> "CONTEXT_MANAGER_INTERRUPT";
                case InterruptRequest.QuestionAnswerInterruptRequest ignored -> "QUESTION_ANSWER_INTERRUPT";
                case DiscoveryAgentResults ignored -> "DISCOVERY_RESULTS";
                case PlanningAgentResults ignored -> "PLANNING_RESULTS";
                case TicketAgentResults ignored -> "TICKET_RESULTS";
                case AiFilterRequest ignored -> "AI_FILTER";
                case AiPropagatorRequest ignored -> "AI_PROPAGATOR";
                case AiTransformerRequest ignored -> "AI_TRANSFORMER";
                case AgentToAgentRequest ignored -> "AGENT_TO_AGENT_CONVERSATION";
                case AgentToControllerRequest ignored -> "AGENT_TO_CONTROLLER_CONVERSATION";
                case ControllerToAgentRequest ignored -> "CONTROLLER_TO_AGENT_CONVERSATION";
            };
        }

        @JsonIgnore
        default CurationPhase curationPhaseExtraction() {
            return switch (this) {
                case DiscoveryCollectorRequest ignored -> CurationPhase.DISCOVERY_CURATION;
                case PlanningCollectorRequest ignored -> CurationPhase.PLANNING_CURATION;
                case TicketCollectorRequest ignored -> CurationPhase.TICKET_CURATION;
                case DiscoveryOrchestratorRequest ignored -> CurationPhase.DISCOVERY_AGENT;
                case DiscoveryAgentRequest ignored -> CurationPhase.DISCOVERY_AGENT;
                case DiscoveryAgentRequests ignored -> CurationPhase.DISCOVERY_AGENT;
                case DiscoveryAgentResults ignored -> CurationPhase.DISCOVERY_AGENT;
                case PlanningOrchestratorRequest ignored -> CurationPhase.PLANNING_AGENT;
                case PlanningAgentRequest ignored -> CurationPhase.PLANNING_AGENT;
                case PlanningAgentRequests ignored -> CurationPhase.PLANNING_AGENT;
                case PlanningAgentResults ignored -> CurationPhase.PLANNING_AGENT;
                case TicketOrchestratorRequest ignored -> CurationPhase.TICKET_AGENT;
                case TicketAgentRequest ignored -> CurationPhase.TICKET_AGENT;
                case TicketAgentRequests ignored -> CurationPhase.TICKET_AGENT;
                case CommitAgentRequest ignored -> CurationPhase.OTHER;
                case MergeConflictRequest ignored -> CurationPhase.OTHER;
                case TicketAgentResults ignored -> CurationPhase.TICKET_AGENT;
                case InterruptRequest ignored -> CurationPhase.INTERRUPT;
                case OrchestratorRequest ignored -> CurationPhase.OTHER;
                case OrchestratorCollectorRequest ignored -> CurationPhase.OTHER;
                case ContextManagerRequest ignored -> CurationPhase.OTHER;
                case ContextManagerRoutingRequest ignored -> CurationPhase.OTHER;
                case AiFilterRequest aiFilterRequest ->
                        CurationPhase.OTHER;
                case AiPropagatorRequest aiPropagatorRequest ->
                        CurationPhase.OTHER;
                case AiTransformerRequest aiTransformerRequest ->
                        CurationPhase.OTHER;
                case AgentToAgentRequest ignored -> CurationPhase.OTHER;
                case AgentToControllerRequest ignored -> CurationPhase.OTHER;
                case ControllerToAgentRequest ignored -> CurationPhase.OTHER;
            };
        }

        @JsonIgnore
        default String routeGuardrailsExtraction() {
            return switch (this) {
                case DiscoveryCollectorRequest ignored -> "Consolidate discovery results. Collectors always advance forward. Use contextManagerRequest only for missing cross-agent context.";
                case PlanningCollectorRequest ignored -> "Consolidate planning results. Collectors always advance forward. Use contextManagerRequest only for missing cross-agent context.";
                case TicketCollectorRequest ignored -> "Consolidate ticket results. Collectors always advance forward. Use contextManagerRequest only for missing cross-agent context.";
                case OrchestratorCollectorRequest ignored -> "Consolidate workflow results. Collectors always advance forward. Use contextManagerRequest only for missing cross-agent context.";
                case ContextManagerRequest ignored -> "Return exactly one non-null returnTo* route based on recovered context.";
                case ContextManagerRoutingRequest ignored -> "Set context manager reason/type and route to context manager request.";
                default -> "Set exactly one non-null route field that matches the next intended node.";
            };
        }

        @JsonIgnore
        private static String phaseOrFallback(String phase, String fallback) {
            if (phase == null || phase.isBlank()) {
                return fallback;
            }
            return phase.trim();
        }

        @JsonIgnore
        private static CurationPhase curationPhaseFromReturnTo(
                TicketCollectorRequest returnToTicketCollector,
                PlanningCollectorRequest returnToPlanningCollector,
                DiscoveryCollectorRequest returnToDiscoveryCollector,
                OrchestratorCollectorRequest returnToOrchestratorCollector
        ) {
            if (returnToTicketCollector != null) {
                return CurationPhase.TICKET_AGENT;
            }
            if (returnToPlanningCollector != null) {
                return CurationPhase.PLANNING_AGENT;
            }
            if (returnToDiscoveryCollector != null) {
                return CurationPhase.DISCOVERY_AGENT;
            }
            if (returnToOrchestratorCollector != null) {
                return CurationPhase.ORCHESTRATION;
            }
            return CurationPhase.OTHER;
        }

        WorktreeSandboxContext worktreeContext();

        AgentRequest withGoal(String goal);

        default AgentRequest withPhase(String phase) {
            return this;
        }

        @Override
        default String computeHash(Artifact.HashContext hashContext) {
            return hashContext.hash(prettyPrintInterruptContinuation());
        }

        @JsonIgnore
        String prettyPrintInterruptContinuation();

        @Override
        @JsonIgnore
        default String prettyPrint() {
            return prettyPrintInterruptContinuation();
        }
    }

    // HasRouteBack and HasOrchestratorRequestRouteBack removed — route-back is handled
    // by the conversational topology controller conference (Story 7).

    /**
     * Interface for agent results containers.
     * Implemented by TicketAgentResults, PlanningAgentResults, and DiscoveryAgentResults.
     */
    sealed interface ResultsRequest extends AgentRequest
        permits
            DiscoveryAgentResults,
            PlanningAgentResults,
            TicketAgentResults {

        /**
         * Get the list of child agent results.
         */
        List<? extends AgentResult> childResults();

    }

    sealed interface InterruptRequest extends AgentRequest
            permits
            InterruptRequest.OrchestratorInterruptRequest,
            InterruptRequest.OrchestratorCollectorInterruptRequest,
            InterruptRequest.DiscoveryOrchestratorInterruptRequest,
            InterruptRequest.DiscoveryAgentInterruptRequest,
            InterruptRequest.DiscoveryCollectorInterruptRequest,
            InterruptRequest.DiscoveryAgentDispatchInterruptRequest,
            InterruptRequest.PlanningOrchestratorInterruptRequest,
            InterruptRequest.PlanningAgentInterruptRequest,
            InterruptRequest.PlanningCollectorInterruptRequest,
            InterruptRequest.PlanningAgentDispatchInterruptRequest,
            InterruptRequest.TicketOrchestratorInterruptRequest,
            InterruptRequest.TicketAgentInterruptRequest,
            InterruptRequest.TicketCollectorInterruptRequest,
            InterruptRequest.TicketAgentDispatchInterruptRequest,
            InterruptRequest.ContextManagerInterruptRequest,
            InterruptRequest.QuestionAnswerInterruptRequest {

        Events.InterruptType type();

        String reason();

        List<StructuredChoice> choices();

        List<ConfirmationItem> confirmationItems();

        String contextForDecision();

        default InterruptRequest withGoal(String goal) {
            return this;
        }

        default String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            builder.append("Interrupt received: ");
            builder.append(type());
            if (reason() != null && !reason().isBlank()) {
                builder.append(" with reason (").append(reason().trim()).append(")");
            }
            builder.append("\n");
            if (contextForDecision() != null && !contextForDecision().isBlank()) {
                builder.append("Context:\n");
                builder.append(contextForDecision().trim()).append("\n");
            }
            appendStructuredChoices(builder, choices());
            appendConfirmationItems(builder, confirmationItems());
            return builder.toString();
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for orchestrator-level workflow steering.")
        @With
        record OrchestratorInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Current workflow phase (DISCOVERY, PLANNING, TICKETS, COMPLETE).")
                String phase,
                @JsonPropertyDescription("Workflow goal statement.")
                String goal,
                @JsonPropertyDescription("High-level workflow context or state summary.")
                String workflowContext
        ) implements InterruptRequest {
            @Override
            public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> c) {
                return (T) this;
            }
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for orchestrator collector phase decisions.")
        @With
        record OrchestratorCollectorInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Summary of collector results driving the decision.")
                String collectorResults,
                @JsonPropertyDescription("Rationale for advancing or routing back.")
                String advancementRationale,
                @JsonPropertyDescription("Available phase options for routing.")
                List<String> phaseOptions
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for discovery orchestration scope and partitioning.")
        @With
        record DiscoveryOrchestratorInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Proposed partitioning of discovery subdomains.")
                List<String> subdomainPartitioning,
                @JsonPropertyDescription("Coverage goals for the discovery phase.")
                String coverageGoals,
                @JsonPropertyDescription("Discovery strategy or approach summary.")
                String discoveryStrategy,
                @JsonPropertyDescription("Scope boundaries for discovery.")
                String scope
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for discovery agent code finding clarification.")
        @With
        record DiscoveryAgentInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Summary of key code findings and questions.")
                String codeFindings,
                @JsonPropertyDescription("Relevant file paths or references.")
                List<String> fileReferences,
                @JsonPropertyDescription("Decisions or questions about boundaries/ownership.")
                List<String> boundaryDecisions,
                @JsonPropertyDescription("Observed architectural patterns or conventions.")
                List<String> patternObservations
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for discovery collector consolidation decisions.")
        @With
        record DiscoveryCollectorInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Consolidation decisions or tradeoffs under consideration.")
                String consolidationDecisions,
                @JsonPropertyDescription("Recommendations derived from discovery results.")
                List<String> recommendations,
                @JsonPropertyDescription("Key code references supporting the consolidation.")
                List<String> codeReferences,
                @JsonPropertyDescription("Subdomain boundaries used in the consolidation.")
                List<String> subdomainBoundaries
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for discovery dispatch routing decisions.")
        @With
        record DiscoveryAgentDispatchInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Summary of dispatch decisions under consideration.")
                String dispatchDecisions,
                @JsonPropertyDescription("Proposed agent assignments.")
                List<String> agentAssignments,
                @JsonPropertyDescription("Workload distribution notes or constraints.")
                List<String> workloadDistribution,
                @JsonPropertyDescription("Rationale for the routing decision.")
                String routingRationale
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for planning orchestration and decomposition decisions.")
        @With
        record PlanningOrchestratorInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Planned ticket decomposition summary.")
                String ticketDecomposition,
                @JsonPropertyDescription("Strategy for breaking down tickets.")
                String ticketBreakdownStrategy,
                @JsonPropertyDescription("How discovery context informed planning.")
                String discoveryContextUsage,
                @JsonPropertyDescription("Scope boundaries for planning.")
                String planningScope
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for planning agent ticket design decisions.")
        @With
        record PlanningAgentInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Ticket design summary or open questions.")
                String ticketDesign,
                @JsonPropertyDescription("Architecture decisions or tradeoffs being considered.")
                List<String> architectureDecisions,
                @JsonPropertyDescription("Proposed planning tickets.")
                List<PlanningTicket> proposedTickets,
                @JsonPropertyDescription("Discovery references informing planning.")
                List<MemoryReference> discoveryReferences
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for planning collector consolidation decisions.")
        @With
        record PlanningCollectorInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Summary of ticket consolidation decisions.")
                String ticketConsolidation,
                @JsonPropertyDescription("Dependency resolution strategy or conflicts.")
                String dependencyResolution,
                @JsonPropertyDescription("Consolidated tickets under consideration.")
                List<PlanningTicket> consolidatedTickets,
                @JsonPropertyDescription("Conflicting dependencies needing resolution.")
                List<String> dependencyConflicts
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for planning dispatch routing decisions.")
        @With
        record PlanningAgentDispatchInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Proposed agent assignments for tickets.")
                List<String> agentAssignments,
                @JsonPropertyDescription("Ticket distribution or batching notes.")
                List<String> ticketDistribution,
                @JsonPropertyDescription("Routing notes about discovery context usage.")
                String discoveryContextRouting,
                @JsonPropertyDescription("Rationale for the routing decision.")
                String routingRationale
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for ticket orchestration and execution scope decisions.")
        @With
        record TicketOrchestratorInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Implementation scope under consideration.")
                String implementationScope,
                @JsonPropertyDescription("How planning context informed execution.")
                String planningContextUsage,
                @JsonPropertyDescription("Execution strategy or sequencing notes.")
                String executionStrategy
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for ticket agent implementation decisions.")
        @With
        record TicketAgentInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Implementation approach under consideration.")
                String implementationApproach,
                @JsonPropertyDescription("Summary of expected file changes.")
                List<String> fileChanges,
                @JsonPropertyDescription("Specific files to modify or create.")
                List<String> filesToModify,
                @JsonPropertyDescription("Planned test strategies or verification steps.")
                List<String> testStrategies
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for ticket collector completion decisions.")
        @With
        record TicketCollectorInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Completion status summary or open questions.")
                String completionStatus,
                @JsonPropertyDescription("Follow-up items or remaining work.")
                List<String> followUps,
                @JsonPropertyDescription("Verification or test result summaries.")
                List<String> verificationResults
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for ticket dispatch routing decisions.")
        @With
        record TicketAgentDispatchInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Proposed agent assignments for tickets.")
                List<String> agentAssignments,
                @JsonPropertyDescription("Ticket distribution or batching notes.")
                List<String> ticketDistribution,
                @JsonPropertyDescription("Context routing notes for execution.")
                String contextRouting,
                @JsonPropertyDescription("Rationale for the routing decision.")
                String routingRationale
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for context manager routing decisions.")
        @With
        record ContextManagerInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision,
                @JsonPropertyDescription("Summary of context reconstruction findings.")
                String contextFindings,
                @JsonPropertyDescription("Relevance assessments for sources.")
                List<String> relevanceAssessments,
                @JsonPropertyDescription("Source references used during reconstruction.")
                List<String> sourceReferences
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Interrupt request for user question/answer flow.")
        @With
        record QuestionAnswerInterruptRequest(
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @SkipPropertyFilter
                ArtifactKey contextId,
                @JsonPropertyDescription("Worktree sandbox context for this request.")
                @SkipPropertyFilter
                WorktreeSandboxContext worktreeContext,
                @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
                Events.InterruptType type,
                @JsonPropertyDescription("Natural language explanation of the uncertainty.")
                String reason,
                @JsonPropertyDescription("Structured decision choices for the controller.")
                List<StructuredChoice> choices,
                @JsonPropertyDescription("Yes/no confirmations required for continuation.")
                List<ConfirmationItem> confirmationItems,
                @JsonPropertyDescription("Concise context needed to make the decision.")
                String contextForDecision
        ) implements InterruptRequest {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Multiple-choice decision with optional write-in.")
        @With
        record StructuredChoice(
                @JsonPropertyDescription("Stable identifier for this choice.")
                String choiceId,
                @JsonPropertyDescription("Decision question posed to the controller.")
                String question,
                @JsonPropertyDescription("Additional context for the decision.")
                String context,
                @JsonPropertyDescription("Options map with keys A/B/C/CUSTOM.")
                Map<String, String> options,
                @JsonPropertyDescription("Recommended option key if applicable.")
                String recommended
        ) implements AgentPretty{


            @Override
            public String prettyPrint() {
                StringBuilder sb = new StringBuilder();
                return "";
            }
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Yes/no confirmation item for interrupt decisions.")
        @With
        record ConfirmationItem(
                @JsonPropertyDescription("Stable identifier for this confirmation.")
                String confirmationId,
                @JsonPropertyDescription("Confirmation statement to approve or deny.")
                String statement,
                @JsonPropertyDescription("Additional context for the confirmation.")
                String context,
                @JsonPropertyDescription("Default value when unspecified.")
                Boolean defaultValue,
                @JsonPropertyDescription("Impact if the confirmation is denied.")
                String impactIfNo
        ) {
        }

        @Builder(toBuilder=true)
        @JsonClassDescription("Controller response resolving an interrupt request.")
        @With
        record InterruptResolution(
                @JsonPropertyDescription("Selected option per choiceId (A/B/C/CUSTOM).")
                Map<String, String> selectedChoices,
                @JsonPropertyDescription("Custom inputs keyed by choiceId when CUSTOM is selected.")
                Map<String, String> customInputs,
                @JsonPropertyDescription("Confirmation decisions keyed by confirmationId.")
                Map<String, Boolean> confirmations
        ) {
        }
    }

    // CollectorDecision removed — collectors always route forward. Route-back is handled
    // by the conversational topology controller conference (Story 7).

    /**
     * Shared data models describing agent interactions, results, and interrupts.
     * These are intentionally transport-agnostic and can be serialized as needed.
     */
    @Builder(toBuilder=true)
    @With
    record AgentInteraction(
            InteractionType interactionType,
            String message
    ) {
    }

    /**
     * Defines how to kick off sub-agents for orchestrated work.
     */
    @Builder(toBuilder=true)
    @With
    record DelegationPlan(
            String summary,
            Map<String, String> subAgentGoals
    ) {
    }

    /**
     * Orchestrator-specific results.
     * Note: Results do NOT implement DelegationTemplate - delegation templates are the
     * *AgentRequests types that contain multiple sub-agent requests.
     */
    @Builder(toBuilder=true)
    @With
    record OrchestratorAgentResult(
            @SkipPropertyFilter
            ArtifactKey contextId,
            String output,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult{
        public OrchestratorAgentResult(ArtifactKey contextId, String output) {
            this(contextId, output, null);
        }

        public OrchestratorAgentResult(String output) {
            this(null, output, null);
        }

        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    @Builder(toBuilder=true)
    @With
    record DiscoveryOrchestratorResult(
            @SkipPropertyFilter
            ArtifactKey contextId,
            String output,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        public DiscoveryOrchestratorResult(ArtifactKey contextId, String output) {
            this(contextId, output, null);
        }

        public DiscoveryOrchestratorResult(String output) {
            this(null, output, null);
        }

        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    @Builder(toBuilder=true)
    @With
    record PlanningOrchestratorResult(
            @SkipPropertyFilter
            ArtifactKey contextId,
            String output,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        public PlanningOrchestratorResult(ArtifactKey contextId, String output) {
            this(contextId, output, null);
        }

        public PlanningOrchestratorResult(String output) {
            this(null, output, null);
        }

        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from the ticket orchestrator.")
    @With
    record TicketOrchestratorResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Human-readable summary output.")
            String output,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        public TicketOrchestratorResult(ArtifactKey contextId, String output) {
            this(contextId, output, null);
        }

        public TicketOrchestratorResult(String output) {
            this(null, output, null);
        }

        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    /**
     * Results for non-orchestrator agents.
     */
//    TODO: this should be a code-map-like report result -
//        something cool about this
    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from a discovery agent.")
    @With
    record DiscoveryAgentResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Structured discovery report.")
            DiscoveryReport report,
            @JsonPropertyDescription("Human-readable summary output.")
            String output,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (report != null) {
                children.add(report);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            DiscoveryReport updatedReport = firstChildOfType(children, DiscoveryReport.class, report);
            return (T) this.toBuilder()
                    .report(updatedReport)
                    .build();
        }

        public DiscoveryAgentResult(String output) {
            this(null, null, output, null);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (output != null && !output.isBlank()) {
                builder.append(output.trim());
            }
            if (report != null) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append(report.prettyPrint());
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from a planning agent.")
    @With
    record PlanningAgentResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Proposed planning tickets.")
            List<PlanningTicket> tickets,
            @JsonPropertyDescription("Human-readable summary output.")
            String output,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (tickets != null) {
                for (PlanningTicket ticket : tickets) {
                    if (ticket != null) {
                        children.add(ticket);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<PlanningTicket> updatedTickets = childrenOfType(children, PlanningTicket.class, tickets);
            return (T) this.toBuilder()
                    .tickets(updatedTickets)
                    .build();
        }

        public PlanningAgentResult(String output) {
            this(null, null, output, null);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (output != null && !output.isBlank()) {
                builder.append(output.trim());
            }
            if (tickets != null && !tickets.isEmpty()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Tickets:\n");
                for (PlanningTicket ticket : tickets) {
                    if (ticket == null) {
                        continue;
                    }
                    builder.append("- ").append(ticket.prettyPrint()).append("\n");
                }
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from a ticket execution agent.")
    @With
    record TicketAgentResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Ticket identifier being implemented.")
            String ticketId,
            @JsonPropertyDescription("Summary of implementation completed.")
            String implementationSummary,
            @JsonPropertyDescription("Files modified during execution.")
            List<String> filesModified,
            @JsonPropertyDescription("Test results or verification output.")
            List<String> testResults,
            @JsonPropertyDescription("Commit references or hashes.")
            List<String> commits,
            @JsonPropertyDescription("Verification status summary.")
            String verificationStatus,
            @JsonPropertyDescription("Memory references used during execution.")
            List<MemoryReference> memoryReferences,
            @JsonPropertyDescription("Human-readable summary output.")
            String output,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {

        public TicketAgentResult(String output) {
            this(null, null, null, null, null, null, null, null, output, null);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (ticketId != null && !ticketId.isBlank()) {
                builder.append("Ticket Id: ").append(ticketId.trim()).append("\n");
            }
            if (verificationStatus != null && !verificationStatus.isBlank()) {
                builder.append("Verification Status: ").append(verificationStatus.trim()).append("\n");
            }
            if (implementationSummary != null && !implementationSummary.isBlank()) {
                builder.append("Implementation Summary:\n").append(implementationSummary.trim()).append("\n");
            }
            appendList(builder, "Files Modified", filesModified);
            appendList(builder, "Test Results", testResults);
            appendList(builder, "Commits", commits);
            if (output != null && !output.isBlank()) {
                builder.append("Output:\n").append(output.trim()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from AI filter execution containing filtered instructions.")
    @With
    record AiFilterResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Whether the AI filter executed successfully.")
            boolean successful,
            @JsonPropertyDescription("The instructions produced by the AI filter.")
            List<Instruction> output,
            @JsonPropertyDescription("Error message if the AI filter execution failed.")
            String errorMessage,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            builder.append("Successful: ").append(successful).append("\n");
            if (errorMessage != null && !errorMessage.isBlank()) {
                builder.append("Error: ").append(errorMessage.trim()).append("\n");
            }
            if (output != null && !output.isEmpty()) {
                builder.append("Instructions: ").append(output.size()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from commit execution before merge.")
    @With
    record CommitAgentResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Whether commit execution completed successfully.")
            boolean successful,
            @JsonPropertyDescription("Human-readable summary output.")
            String output,
            @JsonPropertyDescription("Error message when commit execution fails.")
            String errorMessage,
            @JsonPropertyDescription("Commit metadata emitted by commit execution.")
            List<WorktreeCommitMetadata> commitMetadata,
            @JsonPropertyDescription("Optional short notes about notable file-specific decisions.")
            List<String> notes,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        public CommitAgentResult(ArtifactKey contextId, boolean successful, String output, String errorMessage, List<WorktreeCommitMetadata> commitMetadata) {
            this(contextId, successful, output, errorMessage, commitMetadata, List.of(), null);
        }

        public CommitAgentResult(String output) {
            this(null, true, output, null, List.of(), List.of(), null);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            builder.append("Successful: ").append(successful).append("\n");
            if (output != null && !output.isBlank()) {
                builder.append("Output:\n").append(output.trim()).append("\n");
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                builder.append("Error: ").append(errorMessage.trim()).append("\n");
            }
            builder.append("Commit Metadata:\n");
            if (commitMetadata == null || commitMetadata.isEmpty()) {
                builder.append("(none)\n");
            } else {
                for (WorktreeCommitMetadata metadata : commitMetadata) {
                    if (metadata == null) {
                        continue;
                    }
                    builder.append("- worktreeId=").append(metadata.worktreeId())
                            .append(", commit=").append(metadata.commitHash())
                            .append(", message=").append(metadata.commitMessage())
                            .append("\n");
                }
            }
            appendList(builder, "Notes", notes);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from merge conflict resolution execution.")
    @With
    record MergeConflictResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Whether merge conflict resolution completed successfully.")
            boolean successful,
            @JsonPropertyDescription("Human-readable summary output.")
            String output,
            @JsonPropertyDescription("Error message when merge conflict resolution fails.")
            String errorMessage,
            @JsonPropertyDescription("Files resolved during merge conflict handling.")
            List<String> resolvedConflictFiles,
            @JsonPropertyDescription("Optional short notes about conflict decisions.")
            List<String> notes,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        public MergeConflictResult(String output) {
            this(null, true, output, null, List.of(), List.of(), null);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            builder.append("Successful: ").append(successful).append("\n");
            if (output != null && !output.isBlank()) {
                builder.append("Output:\n").append(output.trim()).append("\n");
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                builder.append("Error: ").append(errorMessage.trim()).append("\n");
            }
            appendList(builder, "Resolved Conflict Files", resolvedConflictFiles);
            appendList(builder, "Notes", notes);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @With
    record ReviewAgentResult(
            @SkipPropertyFilter
            ArtifactKey contextId,
            String assessmentStatus,
            String feedback,
            List<String> suggestions,
            List<String> contentLinks,
            String output,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        public ReviewAgentResult(ArtifactKey contextId, String assessmentStatus, String feedback, List<String> suggestions, List<String> contentLinks, String output) {
            this(contextId, assessmentStatus, feedback, suggestions, contentLinks, output, null);
        }

        public ReviewAgentResult(String output) {
            this(null, null, output, null, null, output, null);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (assessmentStatus != null && !assessmentStatus.isBlank()) {
                builder.append("Assessment Status: ").append(assessmentStatus.trim()).append("\n");
            }
            if (feedback != null && !feedback.isBlank()) {
                builder.append("Feedback:\n").append(feedback.trim()).append("\n");
            }
            if (suggestions != null && !suggestions.isEmpty()) {
                builder.append("Suggestions:\n");
                for (String suggestion : suggestions) {
                    if (suggestion == null || suggestion.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(suggestion.trim()).append("\n");
                }
            }
            if (contentLinks != null && !contentLinks.isEmpty()) {
                builder.append("Content Links:\n");
                for (String link : contentLinks) {
                    if (link == null || link.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(link.trim()).append("\n");
                }
            }
            if (output != null && !output.isBlank()) {
                builder.append("Output:\n").append(output.trim()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from a merger agent.")
    @With
    record MergerAgentResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Acceptability assessment of the merge.")
            String acceptability,
            @JsonPropertyDescription("Conflict details identified during review.")
            List<String> conflictDetails,
            @JsonPropertyDescription("Guidance for resolving conflicts.")
            List<String> resolutionGuidance,
            @JsonPropertyDescription("Human-readable summary output.")
            String output,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        public MergerAgentResult(ArtifactKey contextId, String acceptability, List<String> conflictDetails, List<String> resolutionGuidance, String output) {
            this(contextId, acceptability, conflictDetails, resolutionGuidance, output, null);
        }

        public MergerAgentResult(String output) {
            this(null, null, null, null, output, null);
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.MergeSummarySerialization mergeSummarySerialization ->
                        output == null ? "" : output.trim();
                default -> AgentResult.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (acceptability != null && !acceptability.isBlank()) {
                builder.append("Acceptability: ").append(acceptability.trim()).append("\n");
            }
            if (conflictDetails != null && !conflictDetails.isEmpty()) {
                builder.append("Conflict Details:\n");
                for (String conflict : conflictDetails) {
                    if (conflict == null || conflict.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(conflict.trim()).append("\n");
                }
            }
            if (resolutionGuidance != null && !resolutionGuidance.isEmpty()) {
                builder.append("Resolution Guidance:\n");
                for (String guidance : resolutionGuidance) {
                    if (guidance == null || guidance.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(guidance.trim()).append("\n");
                }
            }
            if (output != null && !output.isBlank()) {
                builder.append("Output:\n").append(output.trim()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    /**
     * Results for collector agents.
     */
//    TODO: this should be a code-map-like report returned
    @Builder(toBuilder=true)
    @JsonClassDescription("Consolidated discovery results and routing decision.")
    @With
    record DiscoveryCollectorResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Unified consolidated output summary.")
            String consolidatedOutput,
            @JsonPropertyDescription("Additional metadata for the result.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Unified code map derived from discovery.")
            CodeMap unifiedCodeMap,
            @JsonPropertyDescription("Recommendations derived from discovery.")
            List<Recommendation> recommendations,
            @JsonPropertyDescription("Query-specific findings keyed by query name.")
            Map<String, QueryFindings> querySpecificFindings,
            @JsonPropertyDescription("Curated discovery context for downstream agents.")
            UpstreamContext.DiscoveryCollectorContext discoveryCollectorContext,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements ConsolidationTemplate, AgentResult {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCollectorContext != null) {
                children.add(discoveryCollectorContext);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedCuration =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, discoveryCollectorContext);
            return (T) this.toBuilder()
                    .discoveryCollectorContext(updatedCuration)
                    .build();
        }

        public DiscoveryCollectorResult(
                ArtifactKey contextId,
                String consolidatedOutput,
                Map<String, String> metadata,
                CodeMap unifiedCodeMap,
                List<Recommendation> recommendations,
                Map<String, QueryFindings> querySpecificFindings,
                UpstreamContext.DiscoveryCollectorContext discoveryCollectorContext
        ) {
            this(
                    contextId,
                    consolidatedOutput,
                    metadata,
                    unifiedCodeMap,
                    recommendations,
                    querySpecificFindings,
                    discoveryCollectorContext,
                    null
            );
        }

        public DiscoveryCollectorResult(String consolidatedOutput) {
            this(null, consolidatedOutput, Map.of(), null, List.of(), Map.of(), null, null);
        }

        @Override
        public List<Curation> curations() {
            return discoveryCollectorContext == null ? List.of() : List.of(discoveryCollectorContext);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidatedOutput != null && !consolidatedOutput.isBlank()) {
                builder.append(consolidatedOutput.trim()).append("\n");
            }
            if (discoveryCollectorContext != null) {
                builder.append("Curation:\n").append(discoveryCollectorContext.prettyPrint()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Consolidated planning results and routing decision.")
    @With
    record PlanningCollectorResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Unified consolidated output summary.")
            String consolidatedOutput,
            @JsonPropertyDescription("Additional metadata for the result.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Finalized planning tickets.")
            List<PlanningTicket> finalizedTickets,
            @JsonPropertyDescription("Dependency graph between tickets.")
            List<TicketDependency> dependencyGraph,
            @JsonPropertyDescription("Curated planning context for downstream agents.")
            UpstreamContext.PlanningCollectorContext planningCuration,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements ConsolidationTemplate, AgentResult {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (planningCuration != null) {
                children.add(planningCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.PlanningCollectorContext updatedCuration =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, planningCuration);
            return (T) this.toBuilder()
                    .planningCuration(updatedCuration)
                    .build();
        }

        public PlanningCollectorResult(
                ArtifactKey contextId,
                String consolidatedOutput,
                Map<String, String> metadata,
                List<PlanningTicket> finalizedTickets,
                List<TicketDependency> dependencyGraph,
                UpstreamContext.PlanningCollectorContext planningCuration
        ) {
            this(
                    contextId,
                    consolidatedOutput,
                    metadata,
                    finalizedTickets,
                    dependencyGraph,
                    planningCuration,
                    null
            );
        }

        public PlanningCollectorResult(String consolidatedOutput) {
            this(null, consolidatedOutput, Map.of(), List.of(), List.of(), null, null);
        }

        @Override
        public List<Curation> curations() {
            return planningCuration == null ? List.of() : List.of(planningCuration);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidatedOutput != null && !consolidatedOutput.isBlank()) {
                builder.append(consolidatedOutput.trim()).append("\n");
            }
            if (finalizedTickets != null && !finalizedTickets.isEmpty()) {
                builder.append("Tickets:\n");
                for (PlanningTicket ticket : finalizedTickets) {
                    if (ticket == null) {
                        continue;
                    }
                    builder.append("- ").append(ticket.prettyPrint()).append("\n");
                }
            }
            if (planningCuration != null) {
                builder.append("Curation:\n").append(planningCuration.prettyPrint()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Final consolidated workflow result and routing decision.")
    @With
    record OrchestratorCollectorResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Unified consolidated output summary.")
            String consolidatedOutput,
            @JsonPropertyDescription("Additional metadata for the result.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Discovery collector result included in consolidation.")
            DiscoveryCollectorResult discoveryCollectorResult,
            @JsonPropertyDescription("Planning collector result included in consolidation.")
            PlanningCollectorResult planningCollectorResult,
            @JsonPropertyDescription("Ticket collector result included in consolidation.")
            TicketCollectorResult ticketCollectorResult,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements ConsolidationTemplate, AgentResult {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCollectorResult != null) {
                children.add(discoveryCollectorResult);
            }
            if (planningCollectorResult != null) {
                children.add(planningCollectorResult);
            }
            if (ticketCollectorResult != null) {
                children.add(ticketCollectorResult);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            DiscoveryCollectorResult updatedDiscovery =
                    firstChildOfType(children, DiscoveryCollectorResult.class, discoveryCollectorResult);
            PlanningCollectorResult updatedPlanning =
                    firstChildOfType(children, PlanningCollectorResult.class, planningCollectorResult);
            TicketCollectorResult updatedTicket =
                    firstChildOfType(children, TicketCollectorResult.class, ticketCollectorResult);
            return (T) this.toBuilder()
                    .discoveryCollectorResult(updatedDiscovery)
                    .planningCollectorResult(updatedPlanning)
                    .ticketCollectorResult(updatedTicket)
                    .build();
        }

        public OrchestratorCollectorResult(
                ArtifactKey contextId,
                String consolidatedOutput,
                Map<String, String> metadata,
                DiscoveryCollectorResult discoveryCollectorResult,
                PlanningCollectorResult planningCollectorResult,
                TicketCollectorResult ticketCollectorResult
        ) {
            this(
                    contextId,
                    consolidatedOutput,
                    metadata,
                    discoveryCollectorResult,
                    planningCollectorResult,
                    ticketCollectorResult,
                    null
            );
        }

        public OrchestratorCollectorResult(String consolidatedOutput) {
            this(null, consolidatedOutput, Map.of(), null, null, null, null);
        }

        @Override
        public List<Curation> curations() {
            return List.of();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidatedOutput != null && !consolidatedOutput.isBlank()) {
                builder.append(consolidatedOutput.trim()).append("\n");
            }
            if (discoveryCollectorResult != null) {
                builder.append("Discovery Result:\n").append(discoveryCollectorResult.prettyPrint()).append("\n");
            }
            if (planningCollectorResult != null) {
                builder.append("Planning Result:\n").append(planningCollectorResult.prettyPrint()).append("\n");
            }
            if (ticketCollectorResult != null) {
                builder.append("Ticket Result:\n").append(ticketCollectorResult.prettyPrint()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Consolidated ticket execution results and routing decision.")
    @With
    record TicketCollectorResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Unified consolidated output summary.")
            String consolidatedOutput,
            @JsonPropertyDescription("Additional metadata for the result.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Completion status summary.")
            String completionStatus,
            @JsonPropertyDescription("Follow-up items or remaining work.")
            List<String> followUps,
            @JsonPropertyDescription("Curated ticket context for downstream agents.")
            @SkipPropertyFilter
            UpstreamContext.TicketCollectorContext ticketCuration,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements ConsolidationTemplate, AgentResult {

        public TicketCollectorResult(
                ArtifactKey contextId,
                String consolidatedOutput,
                Map<String, String> metadata,
                String completionStatus,
                List<String> followUps,
                UpstreamContext.TicketCollectorContext ticketCuration
        ) {
            this(
                    contextId,
                    consolidatedOutput,
                    metadata,
                    completionStatus,
                    followUps,
                    ticketCuration,
                    null
            );
        }

        public TicketCollectorResult(String consolidatedOutput) {
            this(null, consolidatedOutput, Map.of(), "", List.of(), null, null);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (ticketCuration != null) {
                children.add(ticketCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.TicketCollectorContext updatedCuration =
                    firstChildOfType(children, UpstreamContext.TicketCollectorContext.class, ticketCuration);
            return (T) this.toBuilder()
                    .ticketCuration(updatedCuration)
                    .build();
        }

        @Override
        public List<Curation> curations() {
            return ticketCuration == null ? List.of() : List.of(ticketCuration);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidatedOutput != null && !consolidatedOutput.isBlank()) {
                builder.append(consolidatedOutput.trim()).append("\n");
            }
            if (completionStatus != null && !completionStatus.isBlank()) {
                builder.append("Completion Status: ").append(completionStatus.trim()).append("\n");
            }
            appendList(builder, "Follow Ups", followUps);
            if (ticketCuration != null) {
                builder.append("Curation:\n").append(ticketCuration.prettyPrint()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    private static <T extends Artifact.AgentModel> T firstChildOfType(
            List<Artifact.AgentModel> children,
            Class<T> type,
            T fallback
    ) {
        if (children == null || children.isEmpty()) {
            return fallback;
        }
        for (Artifact.AgentModel child : children) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
        }
        return fallback;
    }

    private static <T extends Artifact.AgentModel> List<T> childrenOfType(
            List<Artifact.AgentModel> children,
            Class<T> type,
            List<T> fallback
    ) {
        if (children == null || children.isEmpty()) {
            return fallback;
        }
        List<T> results = new ArrayList<>();
        for (Artifact.AgentModel child : children) {
            if (type.isInstance(child)) {
                results.add(type.cast(child));
            }
        }
        return results.isEmpty() ? fallback : List.copyOf(results);
    }

    private static void appendList(StringBuilder builder, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(label).append(":\n");
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            builder.append("- ").append(value.trim()).append("\n");
        }
    }

    private static void appendStructuredChoices(StringBuilder builder, List<InterruptRequest.StructuredChoice> choices) {
        if (choices == null || choices.isEmpty()) {
            return;
        }
        builder.append("Choices:\n");
        for (InterruptRequest.StructuredChoice choice : choices) {
            if (choice == null) {
                continue;
            }
            String choiceId = choice.choiceId();
            String question = choice.question();
            builder.append("- ");
            if (choiceId != null && !choiceId.isBlank()) {
                builder.append(choiceId.trim()).append(": ");
            }
            builder.append(question == null ? "" : question.trim()).append("\n");
            if (choice.context() != null && !choice.context().isBlank()) {
                builder.append("  Context: ").append(choice.context().trim()).append("\n");
            }
            appendChoiceOptions(builder, choice.options());
            if (choice.recommended() != null && !choice.recommended().isBlank()) {
                builder.append("  Recommended: ").append(choice.recommended().trim()).append("\n");
            }
        }
    }

    private static void appendChoiceOptions(StringBuilder builder, Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        builder.append("  Options:\n");
        for (String key : List.of("A", "B", "C", "CUSTOM")) {
            String option = options.get(key);
            if (option == null || option.isBlank()) {
                continue;
            }
            builder.append("  - ").append(key).append(": ").append(option.trim()).append("\n");
        }
    }

    private static void appendConfirmationItems(StringBuilder builder, List<InterruptRequest.ConfirmationItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        builder.append("Confirmations:\n");
        for (InterruptRequest.ConfirmationItem item : items) {
            if (item == null) {
                continue;
            }
            builder.append("- ");
            if (item.confirmationId() != null && !item.confirmationId().isBlank()) {
                builder.append(item.confirmationId().trim()).append(": ");
            }
            builder.append(item.statement() == null ? "" : item.statement().trim()).append("\n");
            if (item.context() != null && !item.context().isBlank()) {
                builder.append("  Context: ").append(item.context().trim()).append("\n");
            }
            if (item.defaultValue() != null) {
                builder.append("  Default: ").append(item.defaultValue()).append("\n");
            }
            if (item.impactIfNo() != null && !item.impactIfNo().isBlank()) {
                builder.append("  Impact if no: ").append(item.impactIfNo().trim()).append("\n");
            }
        }
    }

    private static void appendPrettyLine(StringBuilder builder, String label, String value) {
        builder.append(label).append(": ");
        if (value == null) {
            builder.append("(none)\n");
            return;
        }
        builder.append(value.isBlank() ? "(empty)" : value.trim()).append("\n");
    }

    private static void appendPrettyLine(StringBuilder builder, String label, ArtifactKey value) {
        appendPrettyArtifactKey(builder, label, value);
    }

    private static void appendPrettyLine(StringBuilder builder, String label, WorktreeSandboxContext value) {
        appendPrettyWorktreeContext(builder, label, value);
    }

    private static void appendPrettyLine(StringBuilder builder, String label, Map<String, String> value) {
        appendPrettyMap(builder, label, value);
    }

    private static void appendPrettyLine(StringBuilder builder, String label, ContextManagerRequestType value) {
        builder.append(label).append(": ");
        if (value == null) {
            builder.append("(none)\n");
            return;
        }
        builder.append(value.name()).append("\n");
    }

    private static void appendPrettyText(StringBuilder builder, String label, String value) {
        builder.append(label).append(":\n");
        if (value == null) {
            builder.append("\t(none)\n");
            return;
        }
        if (value.isBlank()) {
            builder.append("\t(empty)\n");
            return;
        }
        String normalized = value.trim().replace("\n", "\n\t");
        builder.append("\t").append(normalized).append("\n");
    }

    private static void appendPrettyContext(StringBuilder builder, String label, AgentPretty context) {
        builder.append(label).append(":\n");
        if (context == null) {
            builder.append("\t(none)\n");
            return;
        }
        String rendered = context.prettyPrint();
        if (rendered == null || rendered.isBlank()) {
            builder.append("\t(empty)\n");
            return;
        }
        builder.append("\t")
                .append(rendered.trim().replace("\n", "\n\t"))
                .append("\n");
    }

    private static void appendPrettyArtifactKey(StringBuilder builder, String label, ArtifactKey key) {
        builder.append(label).append(": ");
        if (key == null || key.value() == null || key.value().isBlank()) {
            builder.append("(none)\n");
            return;
        }
        builder.append(key.value()).append("\n");
    }

    private static void appendPrettyMap(StringBuilder builder, String label, Map<String, String> values) {
        builder.append(label).append(":\n");
        if (values == null || values.isEmpty()) {
            builder.append("\t(none)\n");
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey() == null || entry.getKey().isBlank() ? "(blank-key)" : entry.getKey().trim();
            String value = entry.getValue();
            if (value == null) {
                builder.append("\t- ").append(key).append(": (none)\n");
                continue;
            }
            if (value.isBlank()) {
                builder.append("\t- ").append(key).append(": (empty)\n");
                continue;
            }
            builder.append("\t- ")
                    .append(key)
                    .append(": ")
                    .append(value.trim().replace("\n", "\n\t  "))
                    .append("\n");
        }
    }

    private static void appendPrettyWorktreeContext(StringBuilder builder, String label, WorktreeSandboxContext context) {
        var serCtx = AgentPretty.ACTIVE_SERIALIZATION_CTX.get();
        // SkipWorktreeContext and HistoricalRequest both suppress worktree entirely —
        // worktree info is provided authoritatively by WorktreeSandboxPromptContributorFactory.
        if (serCtx instanceof AgentPretty.AgentSerializationCtx.SkipWorktreeContextSerializationCtx
                || serCtx instanceof AgentPretty.AgentSerializationCtx.HistoricalRequestSerializationCtx) {
            return;
        }
        if (serCtx instanceof AgentPretty.AgentSerializationCtx.CompactifyingRequestSerializer) {
            // Compact: only the parent worktree path
            String path = context != null && context.mainWorktree() != null
                    ? String.valueOf(context.mainWorktree().worktreePath())
                    : "(none)";
            var submodules = context != null ? context.submoduleWorktrees() : null;
            boolean hasSubmodules = submodules != null && !submodules.isEmpty();
            builder.append(label).append(": ").append(path);
            if (hasSubmodules) {
                builder.append(" (and git submodules)");
            }
            builder.append("\n");
            return;
        }

        builder.append("\tSubmodule Worktrees:\n");
        var submodules = context.submoduleWorktrees();
        if (submodules == null || submodules.isEmpty()) {
            builder.append("\t\t(none)\n");
            return;
        }
        int idx = 1;
        for (var submodule : submodules) {
            builder.append("\t\t").append(idx++).append(".\n");
            if (submodule == null) {
                builder.append("\t\t\t(none)\n");
                continue;
            }
            builder.append("\t\t\tId: ").append(submodule.worktreeId()).append("\n");
            builder.append("\t\t\tPath: ").append(submodule.worktreePath()).append("\n");
            builder.append("\t\t\tBase Branch: ").append(submodule.baseBranch()).append("\n");
            builder.append("\t\t\tStatus: ").append(submodule.status()).append("\n");
            builder.append("\t\t\tParent Worktree Id: ").append(submodule.parentWorktreeId()).append("\n");
            builder.append("\t\t\tAssociated Node Id: ").append(submodule.associatedNodeId()).append("\n");
            builder.append("\t\t\tCreated At: ").append(submodule.createdAt()).append("\n");
            builder.append("\t\t\tLast Commit Hash: ").append(submodule.lastCommitHash()).append("\n");
            builder.append("\t\t\tSubmodule Name: ").append(submodule.submoduleName()).append("\n");
            builder.append("\t\t\tSubmodule Url: ").append(submodule.submoduleUrl()).append("\n");
            builder.append("\t\t\tMain Worktree Id: ").append(submodule.mainWorktreeId()).append("\n");
            appendPrettyMap(builder, "\t\t\tMetadata", submodule.metadata());
        }
    }

    static String serializeResults(List<? extends AgentResult> results) {
        if (results == null || results.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (AgentResult result : results) {
            if (result == null) {
                continue;
            }
            String summary = result.prettyPrint(new AgentPretty.AgentSerializationCtx.ResultsSerialization());
            if (summary == null || summary.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("- ").append(summary.replace("\n", "\n  ").trim());
        }
        return builder.isEmpty() ? "(none)" : builder.toString();
    }

    @Builder(toBuilder=true)
    record ModuleOverview(
            String moduleName,
            String summary,
            List<String> relatedFiles,
            List<String> dependencies
    ) {
    }

    @Builder(toBuilder=true)
    record CodeMap(
            List<ModuleOverview> modules,
            List<DiscoveryReport.FileReference> deduplicatedReferences,
            DiscoveryReport.DiagramRepresentation unifiedDiagram,
            List<DiscoveryReport.SemanticTag> mergedTags
    ) {
    }

    enum RecommendationPriority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    @Builder(toBuilder=true)
    record Recommendation(
            String recommendationId,
            String title,
            String description,
            RecommendationPriority priority,
            List<String> relatedFilePaths,
            String estimatedImpact
    ) {
    }

    @Builder(toBuilder=true)
    record QueryFindings(
            String originalQuery,
            List<DiscoveryReport.FileReference> results,
            double confidenceScore,
            String summary
    ) {
    }

    @Builder(toBuilder=true)
    record TicketDependency(
            String fromTicketId,
            String toTicketId,
            String dependencyType
    ) {
    }

    @Builder(toBuilder=true)
    @With
    record DiscoveryCuration(
            @SkipPropertyFilter
            ArtifactKey contextId,
            List<DiscoveryReport> discoveryReports,
            CodeMap unifiedCodeMap,
            List<Recommendation> recommendations,
            Map<String, QueryFindings> querySpecificFindings,
            List<MemoryReference> memoryReferences,
            ConsolidationTemplate.ConsolidationSummary consolidationSummary
    ) implements AgentContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            return hashContext.hash(prettyPrint());
        }

        @Override
        public ArtifactKey contextId() {
            return contextId;
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryReports != null) {
                for (DiscoveryReport report : discoveryReports) {
                    if (report != null) {
                        children.add(report);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<DiscoveryReport> updatedReports = childrenOfType(children, DiscoveryReport.class, discoveryReports);
            return (T) this.toBuilder()
                    .discoveryReports(updatedReports)
                    .build();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidationSummary != null && consolidationSummary.consolidatedOutput() != null) {
                builder.append(consolidationSummary.consolidatedOutput().trim()).append("\n");
            }
            if (recommendations != null && !recommendations.isEmpty()) {
                builder.append("Recommendations:\n");
                for (Recommendation recommendation : recommendations) {
                    if (recommendation == null) {
                        continue;
                    }
                    builder.append("- ").append(recommendation.title()).append(": ")
                            .append(recommendation.description()).append("\n");
                }
            }
            if (discoveryReports != null && !discoveryReports.isEmpty()) {
                builder.append("Discovery Reports:\n");
                for (DiscoveryReport report : discoveryReports) {
                    if (report == null) {
                        continue;
                    }
                    builder.append("- ").append(report.prettyPrint()).append("\n");
                }
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @With
    record PlanningCuration(
            @SkipPropertyFilter
            ArtifactKey contextId,
            List<PlanningAgentResult> planningAgentResults,
            List<PlanningTicket> finalizedTickets,
            List<TicketDependency> dependencyGraph,
            List<MemoryReference> memoryReferences,
            ConsolidationTemplate.ConsolidationSummary consolidationSummary
    ) implements AgentContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            return hashContext.hash(prettyPrint());
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (planningAgentResults != null) {
                for (PlanningAgentResult result : planningAgentResults) {
                    if (result != null) {
                        children.add(result);
                    }
                }
            }
            if (finalizedTickets != null) {
                for (PlanningTicket ticket : finalizedTickets) {
                    if (ticket != null) {
                        children.add(ticket);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<PlanningAgentResult> updatedResults =
                    childrenOfType(children, PlanningAgentResult.class, planningAgentResults);
            List<PlanningTicket> updatedTickets =
                    childrenOfType(children, PlanningTicket.class, finalizedTickets);
            return (T) this.toBuilder()
                    .planningAgentResults(updatedResults)
                    .finalizedTickets(updatedTickets)
                    .build();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidationSummary != null && consolidationSummary.consolidatedOutput() != null) {
                builder.append(consolidationSummary.consolidatedOutput().trim()).append("\n");
            }
            if (finalizedTickets != null && !finalizedTickets.isEmpty()) {
                builder.append("Tickets:\n");
                for (PlanningTicket ticket : finalizedTickets) {
                    if (ticket == null) {
                        continue;
                    }
                    builder.append("- ").append(ticket.prettyPrint()).append("\n");
                }
            }
            if (planningAgentResults != null && !planningAgentResults.isEmpty()) {
                builder.append("Planning Results:\n");
                for (PlanningAgentResult result : planningAgentResults) {
                    if (result == null) {
                        continue;
                    }
                    builder.append("- ").append(result.prettyPrint()).append("\n");
                }
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @With
    record TicketCuration(
            @SkipPropertyFilter
            ArtifactKey contextId,
            List<TicketAgentResult> ticketAgentResults,
            String completionStatus,
            List<String> followUps,
            List<MemoryReference> memoryReferences,
            ConsolidationTemplate.ConsolidationSummary consolidationSummary
    ) implements AgentContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            return hashContext.hash(prettyPrint());
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (ticketAgentResults != null) {
                for (TicketAgentResult result : ticketAgentResults) {
                    if (result != null) {
                        children.add(result);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<TicketAgentResult> updatedResults =
                    childrenOfType(children, TicketAgentResult.class, ticketAgentResults);
            return (T) this.toBuilder()
                    .ticketAgentResults(updatedResults)
                    .build();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (completionStatus != null && !completionStatus.isBlank()) {
                builder.append("Status: ").append(completionStatus.trim()).append("\n");
            }
            if (consolidationSummary != null && consolidationSummary.consolidatedOutput() != null) {
                builder.append(consolidationSummary.consolidatedOutput().trim()).append("\n");
            }
            if (followUps != null && !followUps.isEmpty()) {
                builder.append("Follow Ups:\n");
                for (String followUp : followUps) {
                    if (followUp == null || followUp.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(followUp.trim()).append("\n");
                }
            }
            if (ticketAgentResults != null && !ticketAgentResults.isEmpty()) {
                builder.append("Ticket Results:\n");
                for (TicketAgentResult result : ticketAgentResults) {
                    if (result == null) {
                        continue;
                    }
                    builder.append("- ").append(result.prettyPrint()).append("\n");
                }
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Top-level orchestrator request to coordinate workflow phases.")
    @With
    record OrchestratorRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Current workflow phase.")
            String phase,
            @JsonPropertyDescription("Provide any feedback from interrupt requests.")
            String feedback,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            @SkipPropertyFilter
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            @SkipPropertyFilter
            UpstreamContext.PlanningCollectorContext planningCuration,
            @JsonPropertyDescription("Curated ticket context from ticket collector.")
            @SkipPropertyFilter
            UpstreamContext.TicketCollectorContext ticketCuration
    ) implements AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCuration != null) {
                children.add(discoveryCuration);
            }
            if (planningCuration != null) {
                children.add(planningCuration);
            }
            if (ticketCuration != null) {
                children.add(ticketCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, discoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, planningCuration);
            UpstreamContext.TicketCollectorContext updatedTicket =
                    firstChildOfType(children, UpstreamContext.TicketCollectorContext.class, ticketCuration);
            return (T) this.toBuilder()
                    .discoveryCuration(updatedDiscovery)
                    .planningCuration(updatedPlanning)
                    .ticketCuration(updatedTicket)
                    .build();
        }

        public OrchestratorRequest(ArtifactKey contextId, String goal, String phase) {
            this(contextId, null, goal, phase, null, null, null, null);
        }

        public OrchestratorRequest(String goal, String phase) {
            this(null, null, goal, phase, null, null, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (phase != null && !phase.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Phase: ").append(phase.trim());
            }
            if (StringUtils.isNotBlank(feedback)) {
                builder.append("Feedback resolved from interrupt: ").append(feedback.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Orchestrator Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Phase", phase);
            appendPrettyLine(builder, "Interrupt Feedback Resolutions", feedback);
            appendPrettyContext(builder, "Discovery Curation", discoveryCuration);
            appendPrettyContext(builder, "Planning Curation", planningCuration);
            appendPrettyContext(builder, "Ticket Curation", ticketCuration);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Orchestrator collector request to finalize workflow outputs.")
    @With
    record OrchestratorCollectorRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Current workflow phase.")
            String phase,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            @SkipPropertyFilter
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            @SkipPropertyFilter
            UpstreamContext.PlanningCollectorContext planningCuration,
            @JsonPropertyDescription("Curated ticket context from ticket collector.")
            @SkipPropertyFilter
            UpstreamContext.TicketCollectorContext ticketCuration
    ) implements AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCuration != null) {
                children.add(discoveryCuration);
            }
            if (planningCuration != null) {
                children.add(planningCuration);
            }
            if (ticketCuration != null) {
                children.add(ticketCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, discoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, planningCuration);
            UpstreamContext.TicketCollectorContext updatedTicket =
                    firstChildOfType(children, UpstreamContext.TicketCollectorContext.class, ticketCuration);
            return (T) this.toBuilder()
                    .discoveryCuration(updatedDiscovery)
                    .planningCuration(updatedPlanning)
                    .ticketCuration(updatedTicket)
                    .build();
        }

        public OrchestratorCollectorRequest(String goal, String phase) {
            this(null, null, goal, phase, null, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (phase != null && !phase.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Phase: ").append(phase.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Orchestrator Collector Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Phase", phase);
            appendPrettyContext(builder, "Discovery Curation", discoveryCuration);
            appendPrettyContext(builder, "Planning Curation", planningCuration);
            appendPrettyContext(builder, "Ticket Curation", ticketCuration);
            return builder.toString().trim();
        }
    }

    sealed interface AgentRouting permits AgentCallRouting, ControllerCallRouting, ControllerResponseRouting, DispatchedAgentRouting, Routing {}

    sealed interface DispatchedAgentRouting extends AgentRouting permits
            DiscoveryAgentRouting,
            PlanningAgentRouting,
            TicketAgentRouting { }

    /**
     * Embabel uses SomeOf to determine which fields are requests to be added to the blackboard.
     * SomeOf contains in it's fields the requests that are added to the blackboard.
     * I've had quite a few issues with inability to interpolate Embabel for this.
     */
    sealed interface Routing extends AgentRouting, SomeOf permits
            OrchestratorRouting,
            OrchestratorCollectorRouting,
            DiscoveryOrchestratorRouting,
            DiscoveryCollectorRouting,
            DiscoveryAgentDispatchRouting,
            PlanningOrchestratorRouting,
            PlanningCollectorRouting,
            PlanningAgentDispatchRouting,
            TicketOrchestratorRouting,
            TicketCollectorRouting,
            TicketAgentDispatchRouting,
            ContextManagerResultRouting,
            InterruptRouting {}

    @Builder(toBuilder=true)
    @JsonClassDescription("Unified interrupt routing result. Exactly one field should be non-null.")
    record InterruptRouting(
            @OrchestratorRoute
            @JsonPropertyDescription("Return to orchestrator.")
            OrchestratorRequest orchestratorRequest,
            @DiscoveryRoute
            @JsonPropertyDescription("Return to discovery orchestrator.")
            DiscoveryOrchestratorRequest discoveryOrchestratorRequest,
            @PlanningRoute
            @JsonPropertyDescription("Return to planning orchestrator.")
            PlanningOrchestratorRequest planningOrchestratorRequest,
            @TicketRoute
            @JsonPropertyDescription("Return to ticket orchestrator.")
            TicketOrchestratorRequest ticketOrchestratorRequest,
            @OrchestratorCollectorRoute
            @JsonPropertyDescription("Return to orchestrator collector.")
            OrchestratorCollectorRequest orchestratorCollectorRequest,
            @DiscoveryCollectorRoute
            @JsonPropertyDescription("Return to discovery collector.")
            DiscoveryCollectorRequest discoveryCollectorRequest,
            @PlanningCollectorRoute
            @JsonPropertyDescription("Return to planning collector.")
            PlanningCollectorRequest planningCollectorRequest,
            @TicketCollectorRoute
            @JsonPropertyDescription("Return to ticket collector.")
            TicketCollectorRequest ticketCollectorRequest,
            @DiscoveryDispatchRoute
            @JsonPropertyDescription("Return to discovery dispatch node.")
            DiscoveryAgentRequests discoveryAgentRequests,
            @PlanningDispatchRoute
            @JsonPropertyDescription("Return to planning dispatch node.")
            PlanningAgentRequests planningAgentRequests,
            @TicketDispatchRoute
            @JsonPropertyDescription("Return to ticket dispatch node.")
            TicketAgentRequests ticketAgentRequests,
            @ContextManagerRoute
            @JsonPropertyDescription("Route to context manager for context refinement - will look in request log to identify next step.")
            ContextManagerRoutingRequest contextManagerRoutingRequest
    ) implements Routing {}

    /**
     * Routing type for orchestrator - routes to interrupt, collector, or discovery orchestrator.
     * Note: Routing types do NOT implement DelegationTemplate - they are routing containers.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the orchestrator.")
    record OrchestratorRouting(
            @JsonPropertyDescription("Interrupt request for orchestration decisions.")
            @SkipPropertyFilter
            InterruptRequest.OrchestratorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Route to orchestrator collector for finalization.")
            OrchestratorCollectorRequest collectorRequest,
            @JsonPropertyDescription("Route to discovery orchestrator.")
            DiscoveryOrchestratorRequest discoveryOrchestratorRequest
    ) implements Routing {
        public OrchestratorRouting(InterruptRequest.OrchestratorInterruptRequest interruptRequest, OrchestratorCollectorRequest collectorRequest) {
            this(interruptRequest, collectorRequest, null);
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the orchestrator collector.")
    record OrchestratorCollectorRouting(
            @JsonPropertyDescription("Interrupt request for collector decisions.")
            @SkipPropertyFilter
            InterruptRequest.OrchestratorCollectorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Final consolidation decision. Use this to complete the workflow (ADVANCE_PHASE with requestedPhase=\"COMPLETE\") or intentionally route back via collector decision semantics.")
            OrchestratorCollectorResult collectorResult,
            @JsonPropertyDescription("Route back to orchestrator.")
            @SkipPropertyFilter
            OrchestratorRequest orchestratorRequest
    ) implements Routing {
        public OrchestratorCollectorRouting(OrchestratorCollectorResult collectorResult) {
            this(null, collectorResult, null);
        }
    }

    @Builder(toBuilder=true)
    record DiscoveryOrchestratorRequested(DiscoveryOrchestratorRequest request) {

    }

    /**
     * Request for discovery orchestrator. Uses typed curation fields from upstream collectors.
     * The model does not set upstream context - it receives curated context from the framework.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the discovery orchestrator to partition and dispatch discovery work.")
    @With
    record DiscoveryOrchestratorRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Provide any feedback from interrupt requests.")
            String feedback
    ) implements AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            return (T) this.toBuilder()
                    .build();
        }

        public DiscoveryOrchestratorRequest(String goal) {
            this(null, null, goal, null);
        }

        public DiscoveryOrchestratorRequested to() {
            return new DiscoveryOrchestratorRequested(this);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isNotBlank(feedback)) {
                sb.append("Feedback resolved from interrupt: ").append(feedback.trim());
            }
            sb.append(goal == null || goal.isBlank() ? "Goal: (none)" : "Goal: " + goal.trim());
            return sb.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Discovery Orchestrator Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Interrupt Feedback Resolutions", feedback);
            return builder.toString().trim();
        }

    }

    /**
     * Request for discovery agent. No upstream context field - discovery agents are at the start of the pipeline.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for a discovery agent to inspect a subdomain.")
    @With
    record DiscoveryAgentRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @SkipPropertyFilter
            @JsonPropertyDescription("Unique context id for this request.")
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Subdomain focus for this discovery task.")
            String subdomainFocus
    ) implements AgentRequest, DispatchedRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            return (T) this.toBuilder()
                    .build();
        }

        public DiscoveryAgentRequest(String goal, String subdomainFocus) {
            this(null, null, goal, subdomainFocus);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (subdomainFocus != null && !subdomainFocus.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Subdomain Focus: ").append(subdomainFocus.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Discovery Agent Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Subdomain Focus", subdomainFocus);
            return builder.toString().trim();
        }
    }

    /**
     * DelegationTemplate for discovery orchestrator - contains multiple sub-agent requests.
     * The model returns this to delegate work to discovery agents.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Delegation template carrying multiple discovery agent requests.")
    @With
    record DiscoveryAgentRequests(
            @JsonPropertyDescription("List of discovery agent requests.")
            List<DiscoveryAgentRequest> requests,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this delegation result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Rationale for delegation and assignments.")
            String delegationRationale,
            @JsonPropertyDescription("Additional metadata for delegation.")
            Map<String, String> metadata
    ) implements DelegationTemplate, AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (requests != null) {
                for (DiscoveryAgentRequest request : requests) {
                    if (request != null) {
                        children.add(request);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<DiscoveryAgentRequest> updatedRequests =
                    childrenOfType(children, DiscoveryAgentRequest.class, requests);
            return (T) this.toBuilder()
                    .requests(updatedRequests)
                    .build();
        }

        public DiscoveryAgentRequests(List<DiscoveryAgentRequest> requests) {
            this(requests, null, null, null, null, null);
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.GoalResolutionSerialization goalResolutionSerialization ->
                        resolveGoal();
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        @JsonIgnore
        public ArtifactKey contextId() {
            return contextId;
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim()).append("\n");
            }
            if (requests == null || requests.isEmpty()) {
                builder.append("Requests: (none)");
                return builder.toString().trim();
            }
            builder.append("Requests:\n");
            int index = 0;
            for (DiscoveryAgentRequest request : requests) {
                if (request == null) {
                    continue;
                }
                index++;
                builder.append(index).append(". ");
                String summary = request.prettyPrintInterruptContinuation();
                builder.append(summary.replace("\n", " | "));
                builder.append("\n");
            }
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Discovery Agent Requests\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Delegation Rationale", delegationRationale);
            appendPrettyLine(builder, "Metadata", metadata);
            builder.append("Requests:\n");
            if (requests == null || requests.isEmpty()) {
                builder.append("\t(none)\n");
            } else {
                int idx = 1;
                for (DiscoveryAgentRequest request : requests) {
                    builder.append("\t").append(idx++).append(". ");
                    if (request == null) {
                        builder.append("(none)\n");
                        continue;
                    }
                    builder.append(request.prettyPrintInterruptContinuation().replace("\n", " | ")).append("\n");
                }
            }
            return builder.toString().trim();
        }

        private String resolveGoal() {
            if (goal != null && !goal.isBlank()) {
                return goal.trim();
            }
            if (requests != null) {
                for (DiscoveryAgentRequest request : requests) {
                    if (request != null && request.goal() != null && !request.goal().isBlank()) {
                        return request.goal().trim();
                    }
                }
            }
            return "";
        }
    }

    /**
     * Request for discovery collector. No upstream context field - receives discovery agent results directly.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the discovery collector to consolidate agent findings.")
    @With
    record DiscoveryCollectorRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Serialized discovery results to consolidate.")
            String discoveryResults
    ) implements AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            return (T) this.toBuilder()
                    .build();
        }

        public DiscoveryCollectorRequest(String goal, String discoveryResults) {
            this(null, null, goal, discoveryResults);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (discoveryResults != null && !discoveryResults.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Discovery Results:\n").append(discoveryResults.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Discovery Collector Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyText(builder, "Discovery Results", discoveryResults);
            return builder.toString().trim();
        }
    }

    /**
     * Routing type for discovery orchestrator - routes to interrupt, agent requests, or collector.
     * Note: Routing types do NOT implement DelegationTemplate - the agentRequests field IS the DelegationTemplate.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the discovery orchestrator.")
    record DiscoveryOrchestratorRouting(
            @JsonPropertyDescription("Interrupt request for discovery orchestration decisions.")
            @SkipPropertyFilter
            InterruptRequest.DiscoveryOrchestratorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Delegation payload for discovery agents.")
            DiscoveryAgentRequests agentRequests,
            @JsonPropertyDescription("Route to discovery collector.")
            DiscoveryCollectorRequest collectorRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for a discovery agent.")
    record DiscoveryAgentRouting(
            @JsonPropertyDescription("Interrupt request for discovery agent decisions.")
            @SkipPropertyFilter
            InterruptRequest.DiscoveryAgentInterruptRequest interruptRequest,
            @JsonPropertyDescription("Discovery agent result payload.")
            DiscoveryAgentResult agentResult
    ) implements DispatchedAgentRouting { }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the discovery collector.")
    record DiscoveryCollectorRouting(
            @JsonPropertyDescription("Interrupt request for discovery collector decisions.")
            @SkipPropertyFilter
            InterruptRequest.DiscoveryCollectorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Collector result to drive branching decisions.")
            DiscoveryCollectorResult collectorResult,
            @JsonPropertyDescription("Route to orchestrator.")
            @SkipPropertyFilter
            OrchestratorRequest orchestratorRequest,
            @JsonPropertyDescription("Route back to discovery orchestrator.")
            @SkipPropertyFilter
            DiscoveryOrchestratorRequest discoveryRequest,
            @JsonPropertyDescription("Route to planning orchestrator.")
            @SkipPropertyFilter
            PlanningOrchestratorRequest planningRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for discovery agent dispatch.")
    record DiscoveryAgentDispatchRouting(
            @JsonPropertyDescription("Interrupt request for dispatch decisions.")
            @SkipPropertyFilter
            InterruptRequest.DiscoveryAgentDispatchInterruptRequest interruptRequest,
            @SkipPropertyFilter
            InterruptRequest.DiscoveryAgentInterruptRequest agentInterruptRequest,
            @JsonPropertyDescription("Route to discovery collector with aggregated results.")
            DiscoveryCollectorRequest collectorRequest
    ) implements Routing { }

    /**
     * Request for planning orchestrator. Uses typed curation field from discovery collector.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the planning orchestrator to decompose work.")
    @With
    record PlanningOrchestratorRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Provide any feedback from interrupt requests.")
            String feedback,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            @SkipPropertyFilter
            UpstreamContext.DiscoveryCollectorContext discoveryCuration
    ) implements AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCuration != null) {
                children.add(discoveryCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, discoveryCuration);
            return (T) this.toBuilder()
                    .discoveryCuration(updatedDiscovery)
                    .build();
        }

        public PlanningOrchestratorRequest(String goal) {
            this(null, null, goal, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isNotBlank(feedback)) {
                sb.append("Feedback resolved from interrupt: ").append(feedback.trim());
            }
            sb.append(goal == null || goal.isBlank() ? "Goal: (none)" : "Goal: " + goal.trim());
            return sb.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Planning Orchestrator Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Interrupt Feedback Resolutions", feedback);
            appendPrettyContext(builder, "Discovery Curation", discoveryCuration);
            return builder.toString().trim();
        }
    }

    /**
     * Request for planning agent. Uses typed curation field from discovery collector.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for a planning agent to produce tickets.")
    @With
    record PlanningAgentRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            @SkipPropertyFilter
            UpstreamContext.DiscoveryCollectorContext discoveryCuration
    ) implements AgentRequest, DispatchedRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCuration != null) {
                children.add(discoveryCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, discoveryCuration);
            return (T) this.toBuilder()
                    .discoveryCuration(updatedDiscovery)
                    .build();
        }

        public PlanningAgentRequest(String goal) {
            this(null, null, goal, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder sb = new StringBuilder();
            sb.append(goal == null || goal.isBlank() ? "Goal: (none)" : "Goal: " + goal.trim());
            return sb.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Planning Agent Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyContext(builder, "Discovery Curation", discoveryCuration);
            return builder.toString().trim();
        }
    }

    /**
     * DelegationTemplate for planning orchestrator - contains multiple sub-agent requests.
     * The model returns this to delegate work to planning agents.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Delegation template carrying multiple planning agent requests.")
    @With
    record PlanningAgentRequests(
            @JsonPropertyDescription("List of planning agent requests.")
            List<PlanningAgentRequest> requests,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this delegation result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Rationale for delegation and assignments.")
            String delegationRationale,
            @JsonPropertyDescription("Additional metadata for delegation.")
            Map<String, String> metadata
    ) implements DelegationTemplate, AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (requests != null) {
                for (PlanningAgentRequest request : requests) {
                    if (request != null) {
                        children.add(request);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<PlanningAgentRequest> updatedRequests =
                    childrenOfType(children, PlanningAgentRequest.class, requests);
            return (T) this.toBuilder()
                    .requests(updatedRequests)
                    .build();
        }

        public PlanningAgentRequests(List<PlanningAgentRequest> requests) {
            this(requests, null, null, null, null, null);
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.GoalResolutionSerialization goalResolutionSerialization ->
                        resolveGoal();
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim()).append("\n");
            }
            if (requests == null || requests.isEmpty()) {
                builder.append("Requests: (none)");
                return builder.toString().trim();
            }
            builder.append("Requests:\n");
            int index = 0;
            for (PlanningAgentRequest request : requests) {
                if (request == null) {
                    continue;
                }
                index++;
                builder.append(index).append(". ");
                String summary = request.prettyPrintInterruptContinuation();
                builder.append(summary.replace("\n", " | "));
                builder.append("\n");
            }
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Planning Agent Requests\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Delegation Rationale", delegationRationale);
            appendPrettyLine(builder, "Metadata", metadata);
            builder.append("Requests:\n");
            if (requests == null || requests.isEmpty()) {
                builder.append("\t(none)\n");
            } else {
                int idx = 1;
                for (PlanningAgentRequest request : requests) {
                    builder.append("\t").append(idx++).append(". ");
                    if (request == null) {
                        builder.append("(none)\n");
                        continue;
                    }
                    builder.append(request.prettyPrintInterruptContinuation().replace("\n", " | ")).append("\n");
                }
            }
            return builder.toString().trim();
        }

        private String resolveGoal() {
            if (goal != null && !goal.isBlank()) {
                return goal.trim();
            }
            if (requests != null) {
                for (PlanningAgentRequest request : requests) {
                    if (request != null && request.goal() != null && !request.goal().isBlank()) {
                        return request.goal().trim();
                    }
                }
            }
            return "";
        }
    }

    /**
     * Request for planning collector. Uses typed curation field from discovery collector.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the planning collector to consolidate tickets.")
    @With
    record PlanningCollectorRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Serialized planning results to consolidate.")
            String planningResults,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            @SkipPropertyFilter
            UpstreamContext.DiscoveryCollectorContext discoveryCuration
    ) implements AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCuration != null) {
                children.add(discoveryCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, discoveryCuration);
            return (T) this.toBuilder()
                    .discoveryCuration(updatedDiscovery)
                    .build();
        }

        public PlanningCollectorRequest(String goal, String planningResults) {
            this(null, null, goal, planningResults, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (planningResults != null && !planningResults.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Planning Results:\n").append(planningResults.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Planning Collector Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyText(builder, "Planning Results", planningResults);
            appendPrettyContext(builder, "Discovery Curation", discoveryCuration);
            return builder.toString().trim();
        }
    }

    /**
     * Routing type for planning orchestrator - routes to interrupt, agent requests, or collector.
     * Note: Routing types do NOT implement DelegationTemplate - the agentRequests field IS the DelegationTemplate.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the planning orchestrator.")
    record PlanningOrchestratorRouting(
            @JsonPropertyDescription("Interrupt request for planning orchestration decisions.")
            @SkipPropertyFilter
            InterruptRequest.PlanningOrchestratorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Delegation payload for planning agents.")
            PlanningAgentRequests agentRequests,
            @JsonPropertyDescription("Route to planning collector.")
            PlanningCollectorRequest collectorRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for a planning agent.")
    record PlanningAgentRouting(
            @JsonPropertyDescription("Interrupt request for planning agent decisions.")
            @SkipPropertyFilter
            InterruptRequest.PlanningAgentInterruptRequest interruptRequest,
            @JsonPropertyDescription("Planning agent result payload.")
            PlanningAgentResult agentResult
    ) implements DispatchedAgentRouting {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the planning collector.")
    record PlanningCollectorRouting(
            @JsonPropertyDescription("Interrupt request for planning collector decisions.")
            @SkipPropertyFilter
            InterruptRequest.PlanningCollectorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Collector result to drive branching decisions.")
            PlanningCollectorResult collectorResult,
            @JsonPropertyDescription("Route back to planning orchestrator.")
            @SkipPropertyFilter
            PlanningOrchestratorRequest planningRequest,
            @JsonPropertyDescription("Route to ticket orchestrator.")
            @SkipPropertyFilter
            TicketOrchestratorRequest ticketOrchestratorRequest,
            @JsonPropertyDescription("Route to orchestrator collector.")
            @SkipPropertyFilter
            OrchestratorCollectorRequest orchestratorCollectorRequest,
            @JsonPropertyDescription("Route to orchestrator for non-standard workflow coordination.")
            @SkipPropertyFilter
            OrchestratorRequest orchestratorRequest
    ) implements Routing {
        public PlanningCollectorRouting(InterruptRequest.PlanningCollectorInterruptRequest interruptRequest,
                                        PlanningCollectorResult collectorResult,
                                        PlanningOrchestratorRequest planningRequest,
                                        TicketOrchestratorRequest ticketOrchestratorRequest,
                                        OrchestratorRequest orchestratorRequest) {
            this(interruptRequest, collectorResult, planningRequest, ticketOrchestratorRequest,
                    null, orchestratorRequest);
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for planning agent dispatch.")
    record PlanningAgentDispatchRouting(
            @JsonPropertyDescription("Interrupt request for dispatch decisions.")
            @SkipPropertyFilter
            InterruptRequest.PlanningAgentDispatchInterruptRequest interruptRequest,
            @SkipPropertyFilter
            InterruptRequest.PlanningAgentInterruptRequest agentInterruptRequest,
            @JsonPropertyDescription("Route to planning collector with aggregated results.")
            PlanningCollectorRequest planningCollectorRequest
    ) implements Routing {
    }

    /**
     * Request for ticket orchestrator. Uses typed curation fields from discovery and planning collectors.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the ticket orchestrator to execute planned work.")
    @With
    record TicketOrchestratorRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Provide any feedback from interrupt requests.")
            String feedback,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            @SkipPropertyFilter
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            @SkipPropertyFilter
            UpstreamContext.PlanningCollectorContext planningCuration
    ) implements AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCuration != null) {
                children.add(discoveryCuration);
            }
            if (planningCuration != null) {
                children.add(planningCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, discoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, planningCuration);
            return (T) this.toBuilder()
                    .discoveryCuration(updatedDiscovery)
                    .planningCuration(updatedPlanning)
                    .build();
        }

        public TicketOrchestratorRequest(String goal) {
            this(null, null, goal, null, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            var g = goal == null || goal.isBlank() ? "Goal: (none)" : "Goal: " + goal.trim();
            var f = StringUtils.isBlank(feedback)
                ? "Interrupt Feedback Resolutions (none)"
                : "Interrupt Feedback Resolutions: " + feedback.trim();
            return g + "\n" + f;
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Ticket Orchestrator Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Interrupt Feedback Resolutions", feedback);
            appendPrettyContext(builder, "Discovery Curation", discoveryCuration);
            appendPrettyContext(builder, "Planning Curation", planningCuration);
            return builder.toString().trim();
        }
    }

    /**
     * Request for ticket agent. Uses typed curation fields from discovery and planning collectors.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for a ticket agent to implement a ticket.")
    @With
    record TicketAgentRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Ticket details or instructions.")
            String ticketDetails,
            @JsonPropertyDescription("File path containing ticket details.")
            String ticketDetailsFilePath,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            @SkipPropertyFilter
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            @SkipPropertyFilter
            UpstreamContext.PlanningCollectorContext planningCuration
    ) implements AgentRequest, DispatchedRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCuration != null) {
                children.add(discoveryCuration);
            }
            if (planningCuration != null) {
                children.add(planningCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, discoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, planningCuration);
            return (T) this.toBuilder()
                    .discoveryCuration(updatedDiscovery)
                    .planningCuration(updatedPlanning)
                    .build();
        }

        public TicketAgentRequest(String ticketDetails, String ticketDetailsFilePath) {
            this(null, null, ticketDetails, ticketDetailsFilePath, null, null);
        }

        @Override
        public AgentRequest withGoal(String goal) {
            return this;
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (ticketDetails != null && !ticketDetails.isBlank()) {
                builder.append("Ticket Details: ").append(ticketDetails.trim());
            }
            if (ticketDetailsFilePath != null && !ticketDetailsFilePath.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Ticket File Path: ").append(ticketDetailsFilePath.trim());
            }
            return builder.isEmpty() ? "Ticket Details: (none)" : builder.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Ticket Agent Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyText(builder, "Ticket Details", ticketDetails);
            appendPrettyLine(builder, "Ticket Details File Path", ticketDetailsFilePath);
            appendPrettyContext(builder, "Discovery Curation", discoveryCuration);
            appendPrettyContext(builder, "Planning Curation", planningCuration);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Request to execute an AI-backed filter against input content.")
    @With
    record AiFilterRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Chat session key for LLM communication, may differ from contextId.")
            @SkipPropertyFilter
            ArtifactKey chatKey,
            @JsonPropertyDescription("Goal context for AI filter execution.")
            String goal,
            @JsonPropertyDescription("The serialized input content to be filtered.")
            String input
    ) implements AgentRequest {
        @Override
        public AgentRequest withGoal(String goal) {
            return this.toBuilder().goal(goal).build();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            appendPrettyLine(builder, "Goal", goal);
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("AI Filter Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Input", input != null && input.length() > 200 ? input.substring(0, 200) + "..." : input);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Internal request to commit dirty worktree changes before merge.")
    @With
    record CommitAgentRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Chat session key for LLM session routing (may differ from contextId).")
            @SkipPropertyFilter
            ArtifactKey chatKey,
            @JsonPropertyDescription("Original request that routed into this commit request.")
            @SkipPropertyFilter
            AgentRequest routedFromRequest,
            @JsonPropertyDescription("Goal context for commit execution.")
            String goal,
            @JsonPropertyDescription("Source agent type that produced pending changes.")
            AgentType sourceAgentType,
            @JsonPropertyDescription("Source request type for commit execution context.")
            String sourceRequestType,
            @JsonPropertyDescription("Explicit commit execution instructions.")
            String commitInstructions,
            @JsonPropertyDescription("Summary of source result output.")
            String sourceResultSummary
    ) implements AgentRequest {
        @Override
        public AgentRequest withGoal(String goal) {
            return this.toBuilder().goal(goal).build();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Source Agent Type", sourceAgentType != null ? sourceAgentType.name() : null);
            appendPrettyLine(builder, "Source Request Type", sourceRequestType);
            appendPrettyText(builder, "Commit Instructions", commitInstructions);
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Commit Agent Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Routed From Request Type",
                    routedFromRequest != null ? routedFromRequest.getClass().getSimpleName() : null);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Source Agent Type", sourceAgentType != null ? sourceAgentType.name() : null);
            appendPrettyLine(builder, "Source Request Type", sourceRequestType);
            appendPrettyText(builder, "Commit Instructions", commitInstructions);
            appendPrettyText(builder, "Source Result Summary", sourceResultSummary);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Internal request to resolve merge conflicts after merge execution.")
    @With
    record MergeConflictRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Chat session key for LLM session routing (may differ from contextId).")
            @SkipPropertyFilter
            ArtifactKey chatKey,
            @JsonPropertyDescription("Original request that routed into this merge conflict request.")
            @SkipPropertyFilter
            AgentRequest routedFromRequest,
            @JsonPropertyDescription("Goal context for conflict resolution.")
            String goal,
            @JsonPropertyDescription("Source agent type that produced merge state.")
            AgentType sourceAgentType,
            @JsonPropertyDescription("Source request type for conflict resolution context.")
            String sourceRequestType,
            @JsonPropertyDescription("Merge direction being resolved.")
            String mergeDirection,
            @JsonPropertyDescription("Current merge conflict files to validate and repair.")
            List<String> conflictFiles,
            @JsonPropertyDescription("Current merge error message.")
            String mergeError
    ) implements AgentRequest {
        @Override
        public AgentRequest withGoal(String goal) {
            return this.toBuilder().goal(goal).build();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Source Agent Type", sourceAgentType != null ? sourceAgentType.name() : null);
            appendPrettyLine(builder, "Source Request Type", sourceRequestType);
            appendPrettyLine(builder, "Merge Direction", mergeDirection);
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Merge Conflict Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Routed From Request Type",
                    routedFromRequest != null ? routedFromRequest.getClass().getSimpleName() : null);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Source Agent Type", sourceAgentType != null ? sourceAgentType.name() : null);
            appendPrettyLine(builder, "Source Request Type", sourceRequestType);
            appendPrettyLine(builder, "Merge Direction", mergeDirection);
            appendList(builder, "Conflict Files", conflictFiles);
            appendPrettyLine(builder, "Merge Error", mergeError);
            return builder.toString().trim();
        }
    }

    /**
     * DelegationTemplate for ticket orchestrator - contains multiple sub-agent requests.
     * The model returns this to delegate work to ticket agents.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Delegation template carrying multiple ticket agent requests.")
    @With
    record TicketAgentRequests(
            @JsonPropertyDescription("List of ticket agent requests.")
            List<TicketAgentRequest> requests,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this delegation result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Rationale for delegation and assignments.")
            String delegationRationale,
            @JsonPropertyDescription("Additional metadata for delegation.")
            Map<String, String> metadata
    ) implements DelegationTemplate, AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (requests != null) {
                for (TicketAgentRequest request : requests) {
                    if (request != null) {
                        children.add(request);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<TicketAgentRequest> updatedRequests =
                    childrenOfType(children, TicketAgentRequest.class, requests);
            return (T) this.toBuilder()
                    .requests(updatedRequests)
                    .build();
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.GoalResolutionSerialization goalResolutionSerialization ->
                        resolveGoal();
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        @JsonIgnore
        public ArtifactKey contextId() {
            return this.contextId;
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim()).append("\n");
            }
            if (requests == null || requests.isEmpty()) {
                builder.append("Requests: (none)");
                return builder.toString().trim();
            }
            builder.append("Requests:\n");
            int index = 0;
            for (TicketAgentRequest request : requests) {
                if (request == null) {
                    continue;
                }
                index++;
                builder.append(index).append(". ");
                String summary = request.prettyPrintInterruptContinuation();
                builder.append(summary.replace("\n", " | "));
                builder.append("\n");
            }
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Ticket Agent Requests\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Delegation Rationale", delegationRationale);
            appendPrettyLine(builder, "Metadata", metadata);
            builder.append("Requests:\n");
            if (requests == null || requests.isEmpty()) {
                builder.append("\t(none)\n");
            } else {
                int idx = 1;
                for (TicketAgentRequest request : requests) {
                    builder.append("\t").append(idx++).append(". ");
                    if (request == null) {
                        builder.append("(none)\n");
                        continue;
                    }
                    builder.append(request.prettyPrintInterruptContinuation().replace("\n", " | ")).append("\n");
                }
            }
            return builder.toString().trim();
        }

        private String resolveGoal() {
            if (goal != null && !goal.isBlank()) {
                return goal.trim();
            }
            return "";
        }

    }

    /**
     * Request for ticket collector. Uses typed curation fields from discovery and planning collectors.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the ticket collector to consolidate execution results.")
    @With
    record TicketCollectorRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Serialized ticket results to consolidate.")
            String ticketResults,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            @SkipPropertyFilter
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            @SkipPropertyFilter
            UpstreamContext.PlanningCollectorContext planningCuration
    ) implements AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCuration != null) {
                children.add(discoveryCuration);
            }
            if (planningCuration != null) {
                children.add(planningCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, discoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, planningCuration);
            return (T) this.toBuilder()
                    .discoveryCuration(updatedDiscovery)
                    .planningCuration(updatedPlanning)
                    .build();
        }

        public TicketCollectorRequest(String goal, String ticketResults) {
            this(null, null, goal, ticketResults, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (ticketResults != null && !ticketResults.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Ticket Results:\n").append(ticketResults.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Ticket Collector Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyText(builder, "Ticket Results", ticketResults);
            appendPrettyContext(builder, "Discovery Curation", discoveryCuration);
            appendPrettyContext(builder, "Planning Curation", planningCuration);
            return builder.toString().trim();
        }
    }

    /**
     * Routing type for ticket orchestrator - routes to interrupt, agent requests, or collector.
     * Note: Routing types do NOT implement DelegationTemplate - the agentRequests field IS the DelegationTemplate.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the ticket orchestrator.")
    record TicketOrchestratorRouting(
            @JsonPropertyDescription("Interrupt request for ticket orchestration decisions.")
            @SkipPropertyFilter
            InterruptRequest.TicketOrchestratorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Delegation payload for ticket agents.")
            TicketAgentRequests agentRequests,
            @JsonPropertyDescription("Route to ticket collector.")
            TicketCollectorRequest collectorRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for a ticket agent.")
    record TicketAgentRouting(
            @JsonPropertyDescription("Interrupt request for ticket agent decisions.")
            @SkipPropertyFilter
            InterruptRequest.TicketAgentInterruptRequest interruptRequest,
            @JsonPropertyDescription("Ticket agent result payload.")
            TicketAgentResult agentResult
    ) implements DispatchedAgentRouting {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the ticket collector.")
    record TicketCollectorRouting(
            @JsonPropertyDescription("Interrupt request for ticket collector decisions.")
            @SkipPropertyFilter
            InterruptRequest.TicketCollectorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Collector result to drive branching decisions.")
            TicketCollectorResult collectorResult,
            @JsonPropertyDescription("Route back to ticket orchestrator.")
            @SkipPropertyFilter
            TicketOrchestratorRequest ticketRequest,
            @JsonPropertyDescription("Route to orchestrator collector.")
            @SkipPropertyFilter
            OrchestratorCollectorRequest orchestratorCollectorRequest,
            @JsonPropertyDescription("Route to orchestrator for non-standard workflow coordination.")
            @SkipPropertyFilter
            OrchestratorRequest orchestratorRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for ticket agent dispatch.")
    record TicketAgentDispatchRouting(
            @JsonPropertyDescription("Interrupt request for dispatch decisions.")
            @SkipPropertyFilter
            InterruptRequest.TicketAgentDispatchInterruptRequest interruptRequest,
            @SkipPropertyFilter
            InterruptRequest.TicketAgentInterruptRequest agentInterruptRequest,
            @JsonPropertyDescription("Route to ticket collector with aggregated results.")
            TicketCollectorRequest ticketCollectorRequest
    ) implements Routing {
    }

    enum ContextManagerRequestType {
        INTROSPECT_AGENT_CONTEXT,
        PROCEED
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Lightweight request to route to the context manager.")
    @With
    record ContextManagerRoutingRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Clear explanation of what context you need blackboard intra-agent context.")
            String reason,
            @JsonPropertyDescription("Specific description of what information is missing.")
            String missingContextDescription,
            @JsonPropertyDescription("Type of context reconstruction to request.")
            ContextManagerRequestType type,
            @JsonPropertyDescription("Optional hints about which agent or phase might have the needed context.")
            String suggestedSources
    ) implements AgentRequest {

        @Override
        public AgentRequest withGoal(String goal) {
            return this;
        }

        public ContextManagerRoutingRequest(ArtifactKey contextId, String reason, ContextManagerRequestType type) {
            this(contextId, null, reason, "", type, "");
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            if (reason == null || reason.isBlank()) {
                return "Context Manager Routing: (no reason)";
            }
            return "Context Manager Routing: " + reason.trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Context Manager Routing Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Type", type);
            appendPrettyText(builder, "Reason", reason);
            appendPrettyText(builder, "Missing Context Description", missingContextDescription);
            appendPrettyText(builder, "Suggested Sources", suggestedSources);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Request for Context Manager to reconstruct context.")
    @With
    record ContextManagerRequest(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Type of context reconstruction.")
            ContextManagerRequestType type,
            @JsonPropertyDescription("Reason for context reconstruction.")
            String reason,
            @JsonPropertyDescription("Goal of the reconstruction.")
            String goal,
            @JsonPropertyDescription("Additional context to guide reconstruction.")
            String additionalContext,
            @JsonPropertyDescription("Route back to orchestrator.")
            OrchestratorRequest returnToOrchestrator,
            @JsonPropertyDescription("Route back to orchestrator collector.")
            OrchestratorCollectorRequest returnToOrchestratorCollector,
            @JsonPropertyDescription("Route back to discovery orchestrator.")
            DiscoveryOrchestratorRequest returnToDiscoveryOrchestrator,
            @JsonPropertyDescription("Route back to discovery collector.")
            DiscoveryCollectorRequest returnToDiscoveryCollector,
            @JsonPropertyDescription("Route back to planning orchestrator.")
            PlanningOrchestratorRequest returnToPlanningOrchestrator,
            @JsonPropertyDescription("Route back to planning collector.")
            PlanningCollectorRequest returnToPlanningCollector,
            @JsonPropertyDescription("Route back to ticket orchestrator.")
            TicketOrchestratorRequest returnToTicketOrchestrator,
            @JsonPropertyDescription("Route back to ticket collector.")
            TicketCollectorRequest returnToTicketCollector,
            @JsonPropertyDescription("Route back to planning agent.")
            PlanningAgentRequest returnToPlanningAgent,
            @JsonPropertyDescription("Route back to planning agent requests.")
            PlanningAgentRequests returnToPlanningAgentRequests,
            @JsonPropertyDescription("Route back to planning agent results.")
            PlanningAgentResults returnToPlanningAgentResults,
            @JsonPropertyDescription("Route back to ticket agent.")
            TicketAgentRequest returnToTicketAgent,
            @JsonPropertyDescription("Route back to ticket agent requests.")
            TicketAgentRequests returnToTicketAgentRequests,
            @JsonPropertyDescription("Route back to ticket agent results.")
            TicketAgentResults returnToTicketAgentResults,
            @JsonPropertyDescription("Route back to discovery agent.")
            DiscoveryAgentRequest returnToDiscoveryAgent,
            @JsonPropertyDescription("Route back to discovery agent requests.")
            DiscoveryAgentRequests returnToDiscoveryAgentRequests,
            @JsonPropertyDescription("Route back to discovery agent results.")
            DiscoveryAgentResults returnToDiscoveryAgentResults,
            @JsonPropertyDescription("Route back to context orchestrator.")
            ContextManagerRequest returnToContextOrchestrator
    ) implements AgentRequest {
        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (returnToOrchestrator != null) {
                children.add(returnToOrchestrator);
            }
            if (returnToOrchestratorCollector != null) {
                children.add(returnToOrchestratorCollector);
            }
            if (returnToDiscoveryOrchestrator != null) {
                children.add(returnToDiscoveryOrchestrator);
            }
            if (returnToDiscoveryCollector != null) {
                children.add(returnToDiscoveryCollector);
            }
            if (returnToPlanningOrchestrator != null) {
                children.add(returnToPlanningOrchestrator);
            }
            if (returnToPlanningCollector != null) {
                children.add(returnToPlanningCollector);
            }
            if (returnToTicketOrchestrator != null) {
                children.add(returnToTicketOrchestrator);
            }
            if (returnToTicketCollector != null) {
                children.add(returnToTicketCollector);
            }
            if (returnToPlanningAgent != null) {
                children.add(returnToPlanningAgent);
            }
            if (returnToPlanningAgentRequests != null) {
                children.add(returnToPlanningAgentRequests);
            }
            if (returnToPlanningAgentResults != null) {
                children.add(returnToPlanningAgentResults);
            }
            if (returnToTicketAgent != null) {
                children.add(returnToTicketAgent);
            }
            if (returnToTicketAgentRequests != null) {
                children.add(returnToTicketAgentRequests);
            }
            if (returnToTicketAgentResults != null) {
                children.add(returnToTicketAgentResults);
            }
            if (returnToDiscoveryAgent != null) {
                children.add(returnToDiscoveryAgent);
            }
            if (returnToDiscoveryAgentRequests != null) {
                children.add(returnToDiscoveryAgentRequests);
            }
            if (returnToDiscoveryAgentResults != null) {
                children.add(returnToDiscoveryAgentResults);
            }
            if (returnToContextOrchestrator != null) {
                children.add(returnToContextOrchestrator);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            OrchestratorRequest updatedOrchestrator =
                    firstChildOfType(children, OrchestratorRequest.class, returnToOrchestrator);
            OrchestratorCollectorRequest updatedOrchestratorCollector =
                    firstChildOfType(children, OrchestratorCollectorRequest.class, returnToOrchestratorCollector);
            DiscoveryOrchestratorRequest updatedDiscoveryOrchestrator =
                    firstChildOfType(children, DiscoveryOrchestratorRequest.class, returnToDiscoveryOrchestrator);
            DiscoveryCollectorRequest updatedDiscoveryCollector =
                    firstChildOfType(children, DiscoveryCollectorRequest.class, returnToDiscoveryCollector);
            PlanningOrchestratorRequest updatedPlanningOrchestrator =
                    firstChildOfType(children, PlanningOrchestratorRequest.class, returnToPlanningOrchestrator);
            PlanningCollectorRequest updatedPlanningCollector =
                    firstChildOfType(children, PlanningCollectorRequest.class, returnToPlanningCollector);
            TicketOrchestratorRequest updatedTicketOrchestrator =
                    firstChildOfType(children, TicketOrchestratorRequest.class, returnToTicketOrchestrator);
            TicketCollectorRequest updatedTicketCollector =
                    firstChildOfType(children, TicketCollectorRequest.class, returnToTicketCollector);
            PlanningAgentRequest updatedPlanningAgent =
                    firstChildOfType(children, PlanningAgentRequest.class, returnToPlanningAgent);
            PlanningAgentRequests updatedPlanningAgentRequests =
                    firstChildOfType(children, PlanningAgentRequests.class, returnToPlanningAgentRequests);
            PlanningAgentResults updatedPlanningAgentResults =
                    firstChildOfType(children, PlanningAgentResults.class, returnToPlanningAgentResults);
            TicketAgentRequest updatedTicketAgent =
                    firstChildOfType(children, TicketAgentRequest.class, returnToTicketAgent);
            TicketAgentRequests updatedTicketAgentRequests =
                    firstChildOfType(children, TicketAgentRequests.class, returnToTicketAgentRequests);
            TicketAgentResults updatedTicketAgentResults =
                    firstChildOfType(children, TicketAgentResults.class, returnToTicketAgentResults);
            DiscoveryAgentRequest updatedDiscoveryAgent =
                    firstChildOfType(children, DiscoveryAgentRequest.class, returnToDiscoveryAgent);
            DiscoveryAgentRequests updatedDiscoveryAgentRequests =
                    firstChildOfType(children, DiscoveryAgentRequests.class, returnToDiscoveryAgentRequests);
            DiscoveryAgentResults updatedDiscoveryAgentResults =
                    firstChildOfType(children, DiscoveryAgentResults.class, returnToDiscoveryAgentResults);
            return (T) this.toBuilder()
                    .returnToOrchestrator(updatedOrchestrator)
                    .returnToOrchestratorCollector(updatedOrchestratorCollector)
                    .returnToDiscoveryOrchestrator(updatedDiscoveryOrchestrator)
                    .returnToDiscoveryCollector(updatedDiscoveryCollector)
                    .returnToPlanningOrchestrator(updatedPlanningOrchestrator)
                    .returnToPlanningCollector(updatedPlanningCollector)
                    .returnToTicketOrchestrator(updatedTicketOrchestrator)
                    .returnToTicketCollector(updatedTicketCollector)
                    .returnToPlanningAgent(updatedPlanningAgent)
                    .returnToPlanningAgentRequests(updatedPlanningAgentRequests)
                    .returnToPlanningAgentResults(updatedPlanningAgentResults)
                    .returnToTicketAgent(updatedTicketAgent)
                    .returnToTicketAgentRequests(updatedTicketAgentRequests)
                    .returnToTicketAgentResults(updatedTicketAgentResults)
                    .returnToDiscoveryAgent(updatedDiscoveryAgent)
                    .returnToDiscoveryAgentRequests(updatedDiscoveryAgentRequests)
                    .returnToDiscoveryAgentResults(updatedDiscoveryAgentResults)
                    .build();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (type != null) {
                builder.append("Type: ").append(type);
            }
            if (reason != null && !reason.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Reason: ").append(reason.trim());
            }
            if (goal != null && !goal.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Goal: ").append(goal.trim());
            }
            if (additionalContext != null && !additionalContext.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Additional Context:\n").append(additionalContext.trim());
            }
            return builder.isEmpty() ? "Context Manager Request: (none)" : builder.toString();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Context Manager Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Type", type);
            appendPrettyText(builder, "Reason", reason);
            appendPrettyText(builder, "Goal", goal);
            appendPrettyText(builder, "Additional Context", additionalContext);
            appendPrettyContext(builder, "Return To Orchestrator", returnToOrchestrator);
            appendPrettyContext(builder, "Return To Orchestrator Collector", returnToOrchestratorCollector);
            appendPrettyContext(builder, "Return To Discovery Orchestrator", returnToDiscoveryOrchestrator);
            appendPrettyContext(builder, "Return To Discovery Collector", returnToDiscoveryCollector);
            appendPrettyContext(builder, "Return To Planning Orchestrator", returnToPlanningOrchestrator);
            appendPrettyContext(builder, "Return To Planning Collector", returnToPlanningCollector);
            appendPrettyContext(builder, "Return To Ticket Orchestrator", returnToTicketOrchestrator);
            appendPrettyContext(builder, "Return To Ticket Collector", returnToTicketCollector);
            appendPrettyContext(builder, "Return To Planning Agent", returnToPlanningAgent);
            appendPrettyContext(builder, "Return To Planning Agent Requests", returnToPlanningAgentRequests);
            appendPrettyContext(builder, "Return To Planning Agent Results", returnToPlanningAgentResults);
            appendPrettyContext(builder, "Return To Ticket Agent", returnToTicketAgent);
            appendPrettyContext(builder, "Return To Ticket Agent Requests", returnToTicketAgentRequests);
            appendPrettyContext(builder, "Return To Ticket Agent Results", returnToTicketAgentResults);
            appendPrettyContext(builder, "Return To Discovery Agent", returnToDiscoveryAgent);
            appendPrettyContext(builder, "Return To Discovery Agent Requests", returnToDiscoveryAgentRequests);
            appendPrettyContext(builder, "Return To Discovery Agent Results", returnToDiscoveryAgentResults);
            appendPrettyContext(builder, "Return To Context Orchestrator", returnToContextOrchestrator);
            return builder.toString().trim();
        }

        public ContextManagerRequest addRequest(AgentRequest lastOfType) {
            if (lastOfType == null) {
                return this;
            }

            ContextManagerRequestBuilder builder = this.toBuilder();
            switch (lastOfType) {
                case ContextManagerRequest contextManagerRequest -> {
                }
                case DiscoveryAgentRequest discoveryAgentRequest ->
                        builder = builder.returnToDiscoveryAgent(discoveryAgentRequest);
                case DiscoveryAgentRequests discoveryAgentRequests ->
                        builder = builder.returnToDiscoveryAgentRequests(discoveryAgentRequests);
                case DiscoveryAgentResults discoveryAgentResults ->
                        builder = builder.returnToDiscoveryAgentResults(discoveryAgentResults);
                case DiscoveryCollectorRequest discoveryCollectorRequest ->
                        builder = builder.returnToDiscoveryCollector(discoveryCollectorRequest);
                case DiscoveryOrchestratorRequest discoveryOrchestratorRequest ->
                        builder = builder.returnToDiscoveryOrchestrator(discoveryOrchestratorRequest);
                case InterruptRequest interruptRequest -> {
                }
                case OrchestratorCollectorRequest orchestratorCollectorRequest ->
                        builder = builder.returnToOrchestratorCollector(orchestratorCollectorRequest);
                case OrchestratorRequest orchestratorRequest ->
                        builder = builder.returnToOrchestrator(orchestratorRequest);
                case PlanningAgentRequest planningAgentRequest ->
                        builder = builder.returnToPlanningAgent(planningAgentRequest);
                case PlanningAgentRequests planningAgentRequests ->
                        builder = builder.returnToPlanningAgentRequests(planningAgentRequests);
                case PlanningAgentResults planningAgentResults ->
                        builder = builder.returnToPlanningAgentResults(planningAgentResults);
                case PlanningCollectorRequest planningCollectorRequest ->
                        builder = builder.returnToPlanningCollector(planningCollectorRequest);
                case PlanningOrchestratorRequest planningOrchestratorRequest ->
                        builder = builder.returnToPlanningOrchestrator(planningOrchestratorRequest);
                case TicketAgentRequest ticketAgentRequest ->
                        builder = builder.returnToTicketAgent(ticketAgentRequest);
                case TicketAgentRequests ticketAgentRequests ->
                        builder = builder.returnToTicketAgentRequests(ticketAgentRequests);
                case TicketAgentResults ticketAgentResults ->
                        builder = builder.returnToTicketAgentResults(ticketAgentResults);
                case TicketCollectorRequest ticketCollectorRequest ->
                        builder = builder.returnToTicketCollector(ticketCollectorRequest);
                case TicketOrchestratorRequest ticketOrchestratorRequest ->
                        builder = builder.returnToTicketOrchestrator(ticketOrchestratorRequest);
                case CommitAgentRequest commitAgentRequest -> {
                }
                case MergeConflictRequest mergeConflictRequest -> {
                }
                case ContextManagerRoutingRequest contextManagerRoutingRequest -> {
                }
                case AiFilterRequest ignored -> {
                }
                case AiPropagatorRequest ignored -> {
                }
                case AiTransformerRequest ignored -> {
                }
                case AgentToAgentRequest ignored -> {
                }
                case AgentToControllerRequest ignored -> {
                }
                case ControllerToAgentRequest ignored -> {
                }
            }

            return builder.build();
        }
    }


    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the context manager agent.")
    record ContextManagerResultRouting(
            @JsonPropertyDescription("Interrupt request for context manager decisions.")
            @SkipPropertyFilter
            InterruptRequest.ContextManagerInterruptRequest interruptRequest,
            @JsonPropertyDescription("Route to orchestrator.")
            OrchestratorRequest orchestratorRequest,
            @JsonPropertyDescription("Route to orchestrator collector.")
            OrchestratorCollectorRequest orchestratorCollectorRequest,
            @JsonPropertyDescription("Route to discovery orchestrator.")
            DiscoveryOrchestratorRequest discoveryOrchestratorRequest,
            @JsonPropertyDescription("Route to discovery collector.")
            DiscoveryCollectorRequest discoveryCollectorRequest,
            @JsonPropertyDescription("Route to planning orchestrator.")
            PlanningOrchestratorRequest planningOrchestratorRequest,
            @JsonPropertyDescription("Route to planning collector.")
            PlanningCollectorRequest planningCollectorRequest,
            @JsonPropertyDescription("Route to ticket orchestrator.")
            TicketOrchestratorRequest ticketOrchestratorRequest,
            @JsonPropertyDescription("Route to ticket collector.")
            TicketCollectorRequest ticketCollectorRequest,
            @JsonPropertyDescription("Route to planning agent.")
            PlanningAgentRequest planningAgentRequest,
            @JsonPropertyDescription("Route to planning agent requests.")
            PlanningAgentRequests planningAgentRequests,
            @JsonPropertyDescription("Route to planning agent results.")
            PlanningAgentResults planningAgentResults,
            @JsonPropertyDescription("Route to ticket agent.")
            TicketAgentRequest ticketAgentRequest,
            @JsonPropertyDescription("Route to ticket agent requests.")
            TicketAgentRequests ticketAgentRequests,
            @JsonPropertyDescription("Route to ticket agent results.")
            TicketAgentResults ticketAgentResults,
            @JsonPropertyDescription("Route to discovery agent.")
            DiscoveryAgentRequest discoveryAgentRequest,
            @JsonPropertyDescription("Route to discovery agent requests.")
            DiscoveryAgentRequests discoveryAgentRequests,
            @JsonPropertyDescription("Route to discovery agent results.")
            DiscoveryAgentResults discoveryAgentResults
    ) implements Routing {
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Wrapper for planning agent results used in dispatch.")
    @With
    record PlanningAgentResults(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Planning agent results to consolidate.")
            List<PlanningAgentResult> planningAgentResults
    ) implements ResultsRequest {

        @Override
        public AgentRequest withGoal(String goal) {
            return this;
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (planningAgentResults != null) {
                for (PlanningAgentResult result : planningAgentResults) {
                    if (result != null) {
                        children.add(result);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<PlanningAgentResult> updatedResults =
                    childrenOfType(children, PlanningAgentResult.class, planningAgentResults);
            return (T) this.toBuilder()
                    .planningAgentResults(updatedResults)
                    .build();
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.ResultsSerialization resultsSerialization ->
                        AgentModels.serializeResults(planningAgentResults);
                default -> ResultsRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return "Planning Agent Results Dispatch";
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Planning Agent Results\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyText(builder, "Planning Agent Results", AgentModels.serializeResults(planningAgentResults));
            return builder.toString().trim();
        }

        @Override
        public List<? extends AgentResult> childResults() {
            return planningAgentResults != null ? planningAgentResults : List.of();
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Wrapper for ticket agent results used in dispatch.")
    @With
    record TicketAgentResults(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Ticket agent results to consolidate.")
            List<TicketAgentResult> ticketAgentResults
    ) implements ResultsRequest {

        @Override
        public AgentRequest withGoal(String goal) {
            return this;
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (ticketAgentResults != null) {
                for (TicketAgentResult result : ticketAgentResults) {
                    if (result != null) {
                        children.add(result);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<TicketAgentResult> updatedResults =
                    childrenOfType(children, TicketAgentResult.class, ticketAgentResults);
            return (T) this.toBuilder()
                    .ticketAgentResults(updatedResults)
                    .build();
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.ResultsSerialization resultsSerialization ->
                        AgentModels.serializeResults(ticketAgentResults);
                default -> ResultsRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return "Ticket Agent Results Dispatch";
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Ticket Agent Results\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyText(builder, "Ticket Agent Results", AgentModels.serializeResults(ticketAgentResults));
            return builder.toString().trim();
        }

        @Override
        public List<? extends AgentResult> childResults() {
            return ticketAgentResults != null ? ticketAgentResults : List.of();
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Wrapper for discovery agent results used in dispatch.")
    @With
    record DiscoveryAgentResults(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Discovery agent results to consolidate.")
            List<DiscoveryAgentResult> result
    ) implements ResultsRequest {

        @Override
        public AgentRequest withGoal(String goal) {
            return this;
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (result != null) {
                for (DiscoveryAgentResult discoveryResult : result) {
                    if (discoveryResult != null) {
                        children.add(discoveryResult);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<DiscoveryAgentResult> updatedResults =
                    childrenOfType(children, DiscoveryAgentResult.class, result);
            return (T) this.toBuilder()
                    .result(updatedResults)
                    .build();
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.ResultsSerialization resultsSerialization ->
                        AgentModels.serializeResults(result);
                default -> ResultsRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return "Discovery Agent Results Dispatch";
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Discovery Agent Results\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyText(builder, "Discovery Agent Results", AgentModels.serializeResults(result));
            return builder.toString().trim();
        }

        @Override
        public List<? extends AgentResult> childResults() {
            return result != null ? result : List.of();
        }
    }

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Request to execute an AI-backed transformer against controller payloads.")
    record AiTransformerRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Chat session key for LLM communication, may differ from contextId.")
            @SkipPropertyFilter
            ArtifactKey chatKey,
            @JsonPropertyDescription("Goal context for AI transformer execution.")
            String goal,
            @JsonPropertyDescription("The serialized input content to be transformed.")
            String input,
            @JsonPropertyDescription("Controller id associated with the payload.")
            String controllerId,
            @JsonPropertyDescription("Endpoint id associated with the payload.")
            String endpointId,
            @JsonPropertyDescription("Additional metadata for the transformer.")
            Map<String, String> metadata
    ) implements AgentRequest {

        @Override
        public AgentRequest withGoal(String goal) {
            return this.toBuilder().goal(goal).build();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim()).append("\n");
            }
            if (controllerId != null && !controllerId.isBlank()) {
                builder.append("Controller Id: ").append(controllerId.trim()).append("\n");
            }
            if (endpointId != null && !endpointId.isBlank()) {
                builder.append("Endpoint Id: ").append(endpointId.trim()).append("\n");
            }
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("AI Transformer Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Controller Id", controllerId);
            appendPrettyLine(builder, "Endpoint Id", endpointId);
            appendPrettyLine(builder, "Input", input != null && input.length() > 200 ? input.substring(0, 200) + "..." : input);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Result payload from AI transformer execution for controller responses.")
    record AiTransformerResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Whether the AI transformer executed successfully.")
            boolean successful,
            @JsonPropertyDescription("Transformed text for the controller or human.")
            String transformedText,
            @JsonPropertyDescription("Additional metadata emitted by the transformer.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Error message if the AI transformer execution failed.")
            String errorMessage,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {

        public TransformationOutput toOutput() {
            return TransformationOutput.builder()
                    .transformedText(transformedText)
                    .metadata(metadata)
                    .errorMessage(errorMessage)
                    .build();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            builder.append("Successful: ").append(successful).append("\n");
            if (transformedText != null && !transformedText.isBlank()) {
                builder.append("Transformed Text: ")
                        .append(transformedText.length() > 200 ? transformedText.substring(0, 200) + "..." : transformedText)
                        .append("\n");
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                builder.append("Error: ").append(errorMessage.trim()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Request to execute an AI-backed propagator against action payloads.")
    record AiPropagatorRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Chat session key for LLM communication, may differ from contextId.")
            @SkipPropertyFilter
            ArtifactKey chatKey,
            @JsonPropertyDescription("Goal context for AI propagator execution.")
            String goal,
            @JsonPropertyDescription("The serialized input content to be propagated.")
            String input,
            @JsonPropertyDescription("Human-readable action or source name for the payload.")
            String sourceName,
            @JsonPropertyDescription("Node id of the source payload.")
            String sourceNodeId,
            @JsonPropertyDescription("Additional metadata for the propagator.")
            Map<String, String> metadata
    ) implements AgentRequest {

        @Override
        public AgentRequest withGoal(String goal) {
            return this.toBuilder().goal(goal).build();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim()).append("\n");
            }
            if (sourceName != null && !sourceName.isBlank()) {
                builder.append("Source Name: ").append(sourceName.trim()).append("\n");
            }
            if (sourceNodeId != null && !sourceNodeId.isBlank()) {
                builder.append("Source Node Id: ").append(sourceNodeId.trim()).append("\n");
            }
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("AI Propagator Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Worktree Context", worktreeContext);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Source Name", sourceName);
            appendPrettyLine(builder, "Source Node Id", sourceNodeId);
            appendPrettyLine(builder, "Input", input != null && input.length() > 200 ? input.substring(0, 200) + "..." : input);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Result payload from AI propagator execution containing escalation guidance.")
    record AiPropagatorResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Whether the AI propagator executed successfully.")
            boolean successful,
            @JsonPropertyDescription("Text propagated to the controller or human.")
            String propagatedText,
            @JsonPropertyDescription("Short summary of why you escalated.")
            String summaryText,
            @JsonPropertyDescription("Additional metadata emitted by the propagator.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Error message if the AI propagator execution failed.")
            String errorMessage,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {

        public PropagationOutput toOutput() {
            return PropagationOutput.builder()
                    .propagatedText(propagatedText)
                    .summaryText(summaryText)
                    .metadata(metadata)
                    .errorMessage(errorMessage)
                    .build();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            builder.append("Successful: ").append(successful).append("\n");
            if (summaryText != null && !summaryText.isBlank()) {
                builder.append("Summary: ").append(summaryText.trim()).append("\n");
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                builder.append("Error: ").append(errorMessage.trim()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    // ========== Communication Topology Types ==========

    record CallChainEntry(
            ArtifactKey agentKey,
            AgentType agentType,
            ArtifactKey targetAgentKey,
            AgentType targetAgentType,
            java.time.Instant timestamp
    ) {}

    record CallChain(
            List<CallChainEntry> entries,
            ArtifactKey initiatorKey
    ) {
        public boolean containsAgent(ArtifactKey agentKey) {
            return entries != null && entries.stream().anyMatch(e -> e.agentKey().equals(agentKey));
        }

        public int depth() {
            return entries != null ? entries.size() : 0;
        }
    }

    record ChecklistAction(
            @JsonPropertyDescription("ACTION identifier from checklist row (e.g. VERIFY_REQUIREMENTS_MAPPING, CHALLENGE_ASSUMPTIONS)")
            String actionType,
            @JsonPropertyDescription("Reference to the checklist step (e.g. checklist-discovery-agent.md#step-3)")
            String completedStep,
            @JsonPropertyDescription("Human-readable description of the completed step")
            String stepDescription
    ) {}

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Request from one agent to another agent for inter-agent communication.")
    record AgentToAgentRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("The calling agent's ArtifactKey.")
            @SkipPropertyFilter
            ArtifactKey sourceAgentKey,
            @JsonPropertyDescription("Role of the calling agent.")
            @SkipPropertyFilter
            AgentType sourceAgentType,
            @JsonPropertyDescription("The target agent's ArtifactKey.")
            @SkipPropertyFilter
            ArtifactKey targetAgentKey,
            @JsonPropertyDescription("Role of the target agent.")
            @SkipPropertyFilter
            AgentType targetAgentType,
            @JsonPropertyDescription("Message content from the calling agent.")
            String message,
            @JsonPropertyDescription("Current call chain for loop detection.")
            @SkipPropertyFilter
            List<CallChainEntry> callChain,
            @JsonPropertyDescription("Node ID of the calling agent's workflow graph node (the parent for the new AgentToAgentConversationNode).")
            @SkipPropertyFilter
            String callingNodeId,
            @JsonPropertyDescription("Node ID of the first AgentToAgentConversationNode in this call chain, null if this is the first hop.")
            @SkipPropertyFilter
            String originatingAgentToAgentNodeId,
            @JsonPropertyDescription("Graph node ID of the target agent's workflow node.")
            @SkipPropertyFilter
            String targetNodeId,
            @JsonPropertyDescription("The source agent's ACP session header value — passed in from the calling agent's request context.")
            @SkipPropertyFilter
            String sourceSessionId,
            @JsonPropertyDescription("Chat ID for the target agent's ACP session (where the LLM call will be routed).")
            @SkipPropertyFilter
            ArtifactKey chatId,
            @JsonPropertyDescription("Current goal context.")
            String goal
    ) implements AgentRequest {
        @Override
        public AgentRequest withGoal(String goal) {
            return this.toBuilder().goal(goal).build();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Source Agent", sourceAgentType != null ? sourceAgentType.name() : null);
            appendPrettyLine(builder, "Target Agent", targetAgentType != null ? targetAgentType.name() : null);
            appendPrettyText(builder, "Message", message);
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Agent-to-Agent Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Source Agent Key", sourceAgentKey);
            appendPrettyLine(builder, "Source Agent Type", sourceAgentType != null ? sourceAgentType.name() : null);
            appendPrettyLine(builder, "Target Agent Key", targetAgentKey);
            appendPrettyLine(builder, "Target Agent Type", targetAgentType != null ? targetAgentType.name() : null);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyText(builder, "Message", message);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Request from an agent to the controller for justification conversations.")
    record AgentToControllerRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("The calling agent's ArtifactKey.")
            @SkipPropertyFilter
            ArtifactKey sourceAgentKey,
            @JsonPropertyDescription("Role of the calling agent.")
            @SkipPropertyFilter
            AgentType sourceAgentType,
            @JsonPropertyDescription("Structured justification content.")
            String justificationMessage,
            @JsonPropertyDescription("Current goal context.")
            String goal
    ) implements AgentRequest {
        @Override
        public AgentRequest withGoal(String goal) {
            return this.toBuilder().goal(goal).build();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Source Agent", sourceAgentType != null ? sourceAgentType.name() : null);
            appendPrettyText(builder, "Justification", justificationMessage);
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Agent-to-Controller Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Source Agent Key", sourceAgentKey);
            appendPrettyLine(builder, "Source Agent Type", sourceAgentType != null ? sourceAgentType.name() : null);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyText(builder, "Justification", justificationMessage);
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Request from the controller to an agent as part of a conversation.")
    record ControllerToAgentRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("Worktree sandbox context for this request.")
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext,
            @JsonPropertyDescription("Controller's per-target conversation key.")
            @SkipPropertyFilter
            ArtifactKey sourceKey,
            @JsonPropertyDescription("The target agent's ArtifactKey.")
            @SkipPropertyFilter
            ArtifactKey targetAgentKey,
            @JsonPropertyDescription("Role of the target agent.")
            @SkipPropertyFilter
            AgentType targetAgentType,
            @JsonPropertyDescription("Controller's response content.")
            String message,
            @JsonPropertyDescription("Optional checklist action when responding as part of a structured checklist review.")
            @SkipPropertyFilter
            ChecklistAction checklistAction,
            @JsonPropertyDescription("The source agent's ACP session header value — passed in from the controller's request context.")
            @SkipPropertyFilter
            String sourceSessionId,
            @JsonPropertyDescription("Chat ID for the target agent's ACP session (where the LLM call will be routed).")
            @SkipPropertyFilter
            ArtifactKey chatId,
            @JsonPropertyDescription("Current goal context.")
            String goal
    ) implements AgentRequest {
        @Override
        public AgentRequest withGoal(String goal) {
            return this.toBuilder().goal(goal).build();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyLine(builder, "Target Agent", targetAgentType != null ? targetAgentType.name() : null);
            appendPrettyText(builder, "Message", message);
            return builder.toString().trim();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Controller-to-Agent Request\n");
            appendPrettyLine(builder, "Context Id", contextId);
            appendPrettyLine(builder, "Source Key", sourceKey);
            appendPrettyLine(builder, "Target Agent Key", targetAgentKey);
            appendPrettyLine(builder, "Target Agent Type", targetAgentType != null ? targetAgentType.name() : null);
            appendPrettyLine(builder, "Goal", goal);
            appendPrettyText(builder, "Message", message);
            if (checklistAction != null) {
                appendPrettyLine(builder, "Checklist Action", checklistAction.actionType());
                appendPrettyLine(builder, "Completed Step", checklistAction.completedStep());
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Routing result from an inter-agent call. Contains the target agent's response.")
    record AgentCallRouting(
            @JsonPropertyDescription("The target agent's response text.")
            String response
    ) implements AgentRouting {}

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Result of an inter-agent call.")
    record AgentCallResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("The target agent's response text.")
            String response,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Agent Call Result\n");
            appendPrettyText(builder, "Response", response);
            return builder.toString().trim();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return prettyPrint();
        }
    }

    // ========== Controller Call Communication Types ==========

    @JsonClassDescription("Routing result from an agent-to-controller call.")
    record ControllerCallRouting(
            @JsonPropertyDescription("The controller's response text.")
            String response,
            @JsonPropertyDescription("The controller conversation key for follow-up messages.")
            String controllerConversationKey
    ) implements AgentRouting {}

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Result of an agent-to-controller call.")
    record ControllerCallResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("The controller's response text.")
            String response,
            @JsonPropertyDescription("The controller conversation key.")
            String controllerConversationKey,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Controller Call Result\n");
            appendPrettyText(builder, "Response", response);
            appendPrettyLine(builder, "Conversation Key", controllerConversationKey);
            return builder.toString().trim();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return prettyPrint();
        }
    }

    // ========== Controller Response Communication Types ==========

    @JsonClassDescription("Routing result from a controller-to-agent response.")
    record ControllerResponseRouting(
            @JsonPropertyDescription("The enriched response text after decorator pipeline processing.")
            String response,
            @JsonPropertyDescription("The controller conversation key.")
            String controllerConversationKey
    ) implements AgentRouting {}

    @Builder(toBuilder = true)
    @With
    @JsonClassDescription("Result of a controller-to-agent response.")
    record ControllerResponseResult(
            @JsonPropertyDescription("Unique context id for this result.")
            @SkipPropertyFilter
            ArtifactKey contextId,
            @JsonPropertyDescription("The response text delivered to the agent.")
            String response,
            @JsonPropertyDescription("The controller conversation key.")
            String controllerConversationKey,
            @SkipPropertyFilter
            WorktreeSandboxContext worktreeContext
    ) implements AgentResult {
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Controller Response Result\n");
            appendPrettyText(builder, "Response", response);
            appendPrettyLine(builder, "Conversation Key", controllerConversationKey);
            return builder.toString().trim();
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return prettyPrint();
        }
    }
}
