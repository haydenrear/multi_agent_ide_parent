package com.hayden.multiagentide.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.StuckHandler;
import com.embabel.agent.api.common.StuckHandlerResult;
import com.embabel.agent.api.common.StuckHandlingResultCode;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.InjectedType;
import com.embabel.agent.core.Operation;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.agent.decorator.prompt.PromptContextDecorator;
import com.hayden.multiagentide.agent.decorator.tools.ToolContextDecorator;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentide.agent.decorator.request.RequestDecorator;
import com.hayden.multiagentide.agent.decorator.request.ResultsRequestDecorator;
import com.hayden.multiagentide.agent.decorator.result.FinalResultDecorator;
import com.hayden.multiagentide.agent.decorator.result.ResultDecorator;
import com.hayden.multiagentide.model.nodes.DiscoveryNode;
import com.hayden.multiagentide.model.nodes.GraphNode;
import com.hayden.multiagentide.model.nodes.PlanningNode;
import com.hayden.multiagentide.model.nodes.TicketNode;
import com.hayden.multiagentide.service.AgentExecutor;
import com.hayden.multiagentide.events.DegenerateLoopException;
import com.hayden.multiagentide.filter.service.FilterExecutionService;
import com.hayden.multiagentide.propagation.service.PropagationExecutionService;
import com.hayden.multiagentide.service.InterruptService;
import com.hayden.multiagentide.service.WorktreeAutoCommitService;
import com.hayden.multiagentide.service.WorktreeMergeConflictService;
import com.hayden.multiagentide.transformation.service.TransformerExecutionService;
import com.hayden.multiagentide.llm.LlmRunner;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContextFactory;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults.*;

/**
 * Embabel @Agent definition for multi-agent IDE.
 * Single agent with all workflow actions.
 */
public interface AgentInterfaces {

    Logger log = LoggerFactory.getLogger(AgentInterfaces.class);

    interface MultiAgentIdeMetadata {


        interface AgentMetadata {

            record AgentActionMetadata<T extends AgentModels.AgentRequest, U extends AgentModels.AgentRouting, V extends AgentModels.AgentResult>(
                    AgentType agentType,
                    String agentName,
                    String actionName,
                    String methodName,
                    String template,
                    Class<T> requestType,
                    Class<U> routingType,
                    Class<V> resultType,
                    ErrorTemplates errorTemplates
            ) {
                public AgentActionMetadata(AgentType agentType, String agentName, String actionName, String methodName, String template,
                                           Class<T> requestType, Class<U> routingType, Class<V> resultType) {
                    this(agentType, agentName, actionName, methodName, template, requestType, routingType, resultType, ErrorTemplates.DEFAULT);
                }
            }

            // ── WorkflowAgent LLM-calling actions ──
            AgentActionMetadata<AgentModels.ContextManagerRequest, AgentModels.ContextManagerResultRouting, AgentModels.AgentResult>
                    CONTEXT_MANAGER_STUCK = new AgentActionMetadata<>(
                    AgentType.CONTEXT_MANAGER, WORKFLOW_AGENT_NAME,
                    ACTION_CONTEXT_MANAGER_STUCK, METHOD_HANDLE_STUCK,
                    TEMPLATE_WORKFLOW_CONTEXT_MANAGER,
                    AgentModels.ContextManagerRequest.class, AgentModels.ContextManagerResultRouting.class, AgentModels.AgentResult.class);

            AgentActionMetadata<AgentModels.ContextManagerRequest, AgentModels.ContextManagerResultRouting, AgentModels.AgentResult>
                    CONTEXT_MANAGER_REQUEST = new AgentActionMetadata<>(
                    AgentType.CONTEXT_MANAGER, WORKFLOW_AGENT_NAME,
                    ACTION_CONTEXT_MANAGER, METHOD_CONTEXT_MANAGER,
                    TEMPLATE_WORKFLOW_CONTEXT_MANAGER,
                    AgentModels.ContextManagerRequest.class, AgentModels.ContextManagerResultRouting.class, AgentModels.AgentResult.class);

            AgentActionMetadata<AgentModels.OrchestratorRequest, AgentModels.OrchestratorRouting, AgentModels.AgentResult>
                    COORDINATE_WORKFLOW = new AgentActionMetadata<>(
                    AgentType.ORCHESTRATOR, WORKFLOW_AGENT_NAME,
                    ACTION_ORCHESTRATOR, METHOD_COORDINATE_WORKFLOW,
                    TEMPLATE_WORKFLOW_ORCHESTRATOR,
                    AgentModels.OrchestratorRequest.class, AgentModels.OrchestratorRouting.class, AgentModels.AgentResult.class);

            AgentActionMetadata<AgentModels.OrchestratorCollectorRequest, AgentModels.OrchestratorCollectorRouting, AgentModels.OrchestratorCollectorResult>
                    CONSOLIDATE_WORKFLOW_OUTPUTS = new AgentActionMetadata<>(
                    AgentType.ORCHESTRATOR_COLLECTOR, WORKFLOW_AGENT_NAME,
                    ACTION_ORCHESTRATOR_COLLECTOR, METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS,
                    TEMPLATE_WORKFLOW_ORCHESTRATOR_COLLECTOR,
                    AgentModels.OrchestratorCollectorRequest.class, AgentModels.OrchestratorCollectorRouting.class, AgentModels.OrchestratorCollectorResult.class);

            AgentActionMetadata<AgentModels.DiscoveryCollectorRequest, AgentModels.DiscoveryCollectorRouting, AgentModels.DiscoveryCollectorResult>
                    CONSOLIDATE_DISCOVERY_FINDINGS = new AgentActionMetadata<>(
                    AgentType.DISCOVERY_COLLECTOR, WORKFLOW_AGENT_NAME,
                    ACTION_DISCOVERY_COLLECTOR, METHOD_CONSOLIDATE_DISCOVERY_FINDINGS,
                    TEMPLATE_WORKFLOW_DISCOVERY_COLLECTOR,
                    AgentModels.DiscoveryCollectorRequest.class, AgentModels.DiscoveryCollectorRouting.class, AgentModels.DiscoveryCollectorResult.class);

            AgentActionMetadata<AgentModels.PlanningCollectorRequest, AgentModels.PlanningCollectorRouting, AgentModels.PlanningCollectorResult>
                    CONSOLIDATE_PLANS_INTO_TICKETS = new AgentActionMetadata<>(
                    AgentType.PLANNING_COLLECTOR, WORKFLOW_AGENT_NAME,
                    ACTION_PLANNING_COLLECTOR, METHOD_CONSOLIDATE_PLANS_INTO_TICKETS,
                    TEMPLATE_WORKFLOW_PLANNING_COLLECTOR,
                    AgentModels.PlanningCollectorRequest.class, AgentModels.PlanningCollectorRouting.class, AgentModels.PlanningCollectorResult.class);

            AgentActionMetadata<AgentModels.TicketCollectorRequest, AgentModels.TicketCollectorRouting, AgentModels.TicketCollectorResult>
                    CONSOLIDATE_TICKET_RESULTS = new AgentActionMetadata<>(
                    AgentType.TICKET_COLLECTOR, WORKFLOW_AGENT_NAME,
                    ACTION_TICKET_COLLECTOR, METHOD_CONSOLIDATE_TICKET_RESULTS,
                    TEMPLATE_WORKFLOW_TICKET_COLLECTOR,
                    AgentModels.TicketCollectorRequest.class, AgentModels.TicketCollectorRouting.class, AgentModels.TicketCollectorResult.class);

            AgentActionMetadata<AgentModels.DiscoveryOrchestratorRequest, AgentModels.DiscoveryOrchestratorRouting, AgentModels.DiscoveryOrchestratorResult>
                    KICK_OFF_DISCOVERY_AGENTS = new AgentActionMetadata<>(
                    AgentType.DISCOVERY_ORCHESTRATOR, WORKFLOW_AGENT_NAME,
                    ACTION_DISCOVERY_ORCHESTRATOR, METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH,
                    TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR,
                    AgentModels.DiscoveryOrchestratorRequest.class, AgentModels.DiscoveryOrchestratorRouting.class, AgentModels.DiscoveryOrchestratorResult.class);

            AgentActionMetadata<AgentModels.PlanningOrchestratorRequest, AgentModels.PlanningOrchestratorRouting, AgentModels.PlanningOrchestratorResult>
                    DECOMPOSE_PLAN = new AgentActionMetadata<>(
                    AgentType.PLANNING_ORCHESTRATOR, WORKFLOW_AGENT_NAME,
                    ACTION_PLANNING_ORCHESTRATOR, METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS,
                    TEMPLATE_WORKFLOW_PLANNING_ORCHESTRATOR,
                    AgentModels.PlanningOrchestratorRequest.class, AgentModels.PlanningOrchestratorRouting.class, AgentModels.PlanningOrchestratorResult.class);

            AgentActionMetadata<AgentModels.TicketOrchestratorRequest, AgentModels.TicketOrchestratorRouting, AgentModels.TicketOrchestratorResult>
                    ORCHESTRATE_TICKET_EXECUTION = new AgentActionMetadata<>(
                    AgentType.TICKET_ORCHESTRATOR, WORKFLOW_AGENT_NAME,
                    ACTION_TICKET_ORCHESTRATOR, METHOD_ORCHESTRATE_TICKET_EXECUTION,
                    TEMPLATE_WORKFLOW_TICKET_ORCHESTRATOR,
                    AgentModels.TicketOrchestratorRequest.class, AgentModels.TicketOrchestratorRouting.class, AgentModels.TicketOrchestratorResult.class);

            // ── Dispatch LLM calls (post-subprocess routing) ──

            AgentActionMetadata<AgentModels.DiscoveryAgentResults, AgentModels.DiscoveryAgentDispatchRouting, AgentModels.AgentResult>
                    DISPATCH_DISCOVERY_AGENTS = new AgentActionMetadata<>(
                    AgentType.DISCOVERY_AGENT_DISPATCH, WORKFLOW_AGENT_NAME,
                    ACTION_DISCOVERY_DISPATCH, METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS,
                    TEMPLATE_WORKFLOW_DISCOVERY_DISPATCH,
                    AgentModels.DiscoveryAgentResults.class, AgentModels.DiscoveryAgentDispatchRouting.class, AgentModels.AgentResult.class);

            AgentActionMetadata<AgentModels.PlanningAgentResults, AgentModels.PlanningAgentDispatchRouting, AgentModels.AgentResult>
                    DISPATCH_PLANNING_AGENTS = new AgentActionMetadata<>(
                    AgentType.PLANNING_AGENT_DISPATCH, WORKFLOW_AGENT_NAME,
                    ACTION_PLANNING_DISPATCH, METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                    TEMPLATE_WORKFLOW_PLANNING_DISPATCH,
                    AgentModels.PlanningAgentResults.class, AgentModels.PlanningAgentDispatchRouting.class, AgentModels.AgentResult.class);

            AgentActionMetadata<AgentModels.TicketAgentResults, AgentModels.TicketAgentDispatchRouting, AgentModels.AgentResult>
                    DISPATCH_TICKET_AGENTS = new AgentActionMetadata<>(
                    AgentType.TICKET_AGENT_DISPATCH, WORKFLOW_AGENT_NAME,
                    ACTION_TICKET_DISPATCH, METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
                    TEMPLATE_WORKFLOW_TICKET_DISPATCH,
                    AgentModels.TicketAgentResults.class, AgentModels.TicketAgentDispatchRouting.class, AgentModels.AgentResult.class);

            // ── Subagent LLM-calling actions ──

            AgentActionMetadata<AgentModels.TicketAgentRequest, AgentModels.TicketAgentRouting, AgentModels.TicketAgentResult>
                    RUN_TICKET_AGENT = new AgentActionMetadata<>(
                    AgentType.TICKET_AGENT, WORKFLOW_TICKET_DISPATCH_SUBAGENT,
                    ACTION_TICKET_AGENT, METHOD_RUN_TICKET_AGENT,
                    TEMPLATE_WORKFLOW_TICKET_AGENT,
                    AgentModels.TicketAgentRequest.class, AgentModels.TicketAgentRouting.class, AgentModels.TicketAgentResult.class);

            AgentActionMetadata<AgentModels.PlanningAgentRequest, AgentModels.PlanningAgentRouting, AgentModels.PlanningAgentResult>
                    RUN_PLANNING_AGENT = new AgentActionMetadata<>(
                    AgentType.PLANNING_AGENT, WORKFLOW_PLANNING_DISPATCH_SUBAGENT,
                    ACTION_PLANNING_AGENT, METHOD_RUN_PLANNING_AGENT,
                    TEMPLATE_WORKFLOW_PLANNING_AGENT,
                    AgentModels.PlanningAgentRequest.class, AgentModels.PlanningAgentRouting.class, AgentModels.PlanningAgentResult.class);

            AgentActionMetadata<AgentModels.DiscoveryAgentRequest, AgentModels.DiscoveryAgentRouting, AgentModels.DiscoveryAgentResult>
                    RUN_DISCOVERY_AGENT = new AgentActionMetadata<>(
                    AgentType.DISCOVERY_AGENT, WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT,
                    ACTION_DISCOVERY_AGENT, METHOD_RUN_DISCOVERY_AGENT,
                    TEMPLATE_WORKFLOW_DISCOVERY_AGENT,
                    AgentModels.DiscoveryAgentRequest.class, AgentModels.DiscoveryAgentRouting.class, AgentModels.DiscoveryAgentResult.class);

            // ── Interrupt handlers ──

            AgentActionMetadata<AgentModels.InterruptRequest, AgentModels.InterruptRouting, AgentModels.AgentResult>
                    HANDLE_UNIFIED_INTERRUPT = new AgentActionMetadata<>(
                    AgentType.CONTEXT_MANAGER, WORKFLOW_AGENT_NAME,
                    ACTION_UNIFIED_INTERRUPT, METHOD_HANDLE_UNIFIED_INTERRUPT,
                    TEMPLATE_WORKFLOW_CONTEXT_MANAGER_INTERRUPT,
                    AgentModels.InterruptRequest.class, AgentModels.InterruptRouting.class, AgentModels.AgentResult.class);

            AgentActionMetadata<AgentModels.InterruptRequest.TicketAgentInterruptRequest, AgentModels.TicketAgentRouting, AgentModels.TicketAgentResult>
                    TICKET_AGENT_INTERRUPT = new AgentActionMetadata<>(
                    AgentType.TICKET_AGENT, WORKFLOW_TICKET_DISPATCH_SUBAGENT,
                    ACTION_TICKET_AGENT_INTERRUPT, METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    TEMPLATE_WORKFLOW_TICKET_AGENT,
                    AgentModels.InterruptRequest.TicketAgentInterruptRequest.class, AgentModels.TicketAgentRouting.class, AgentModels.TicketAgentResult.class);

            AgentActionMetadata<AgentModels.InterruptRequest.PlanningAgentInterruptRequest, AgentModels.PlanningAgentRouting, AgentModels.PlanningAgentResult>
                    PLANNING_AGENT_INTERRUPT = new AgentActionMetadata<>(
                    AgentType.PLANNING_AGENT, WORKFLOW_PLANNING_DISPATCH_SUBAGENT,
                    ACTION_PLANNING_AGENT_INTERRUPT, METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    TEMPLATE_WORKFLOW_PLANNING_AGENT,
                    AgentModels.InterruptRequest.PlanningAgentInterruptRequest.class, AgentModels.PlanningAgentRouting.class, AgentModels.PlanningAgentResult.class);

            AgentActionMetadata<AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest, AgentModels.DiscoveryAgentRouting, AgentModels.DiscoveryAgentResult>
                    DISCOVERY_AGENT_INTERRUPT = new AgentActionMetadata<>(
                    AgentType.DISCOVERY_AGENT, WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT,
                    ACTION_DISCOVERY_AGENT_INTERRUPT, METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    TEMPLATE_WORKFLOW_DISCOVERY_AGENT,
                    AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest.class, AgentModels.DiscoveryAgentRouting.class, AgentModels.DiscoveryAgentResult.class);

            // ── InterruptService LLM calls ──

            AgentActionMetadata<AgentModels.InterruptRequest, AgentModels.InterruptRouting, AgentModels.AgentResult>
                    INTERRUPT_REVIEW_RESOLUTION = new AgentActionMetadata<>(
                    AgentType.REVIEW_RESOLUTION_AGENT, AGENT_NAME_INTERRUPT_SERVICE,
                    ACTION_INTERRUPT_AGENT_REVIEW, METHOD_HANDLE_INTERRUPT,
                    TEMPLATE_REVIEW_RESOLUTION,
                    AgentModels.InterruptRequest.class, AgentModels.InterruptRouting.class, AgentModels.AgentResult.class);

            AgentActionMetadata<AgentModels.InterruptRequest, AgentModels.Routing, AgentModels.ReviewAgentResult>
                    INTERRUPT_AGENT_REVIEW = new AgentActionMetadata<>(
                    AgentType.REVIEW_AGENT, AGENT_NAME_INTERRUPT_SERVICE,
                    ACTION_INTERRUPT_AGENT_REVIEW, METHOD_RUN_INTERRUPT_AGENT_REVIEW,
                    TEMPLATE_WORKFLOW_REVIEW,
                    AgentModels.InterruptRequest.class, AgentModels.Routing.class, AgentModels.ReviewAgentResult.class);

            // ── Worktree service LLM calls ──

            AgentActionMetadata<AgentModels.CommitAgentRequest, AgentModels.Routing, AgentModels.CommitAgentResult>
                    WORKTREE_AUTO_COMMIT = new AgentActionMetadata<>(
                    AgentType.COMMIT_AGENT, AGENT_NAME_WORKTREE_AUTO_COMMIT,
                    ACTION_COMMIT_AGENT, METHOD_RUN_COMMIT_AGENT,
                    TEMPLATE_WORKTREE_COMMIT_AGENT,
                    AgentModels.CommitAgentRequest.class, AgentModels.Routing.class, AgentModels.CommitAgentResult.class);

            AgentActionMetadata<AgentModels.MergeConflictRequest, AgentModels.Routing, AgentModels.MergeConflictResult>
                    WORKTREE_MERGE_CONFLICT = new AgentActionMetadata<>(
                    AgentType.MERGE_CONFLICT_AGENT, AGENT_NAME_WORKTREE_MERGE_CONFLICT,
                    ACTION_MERGE_CONFLICT_AGENT, METHOD_RUN_MERGE_CONFLICT_AGENT,
                    TEMPLATE_WORKTREE_MERGE_CONFLICT_AGENT,
                    AgentModels.MergeConflictRequest.class, AgentModels.Routing.class, AgentModels.MergeConflictResult.class);

            // ── AI tool LLM calls ──

            AgentActionMetadata<AgentModels.AiFilterRequest, AgentModels.Routing, AgentModels.AiFilterResult>
                    AI_FILTER = new AgentActionMetadata<>(
                    AgentType.AI_FILTER, AGENT_NAME_AI_FILTER,
                    ACTION_AI_FILTER, METHOD_AI_FILTER,
                    TEMPLATE_AI_FILTER,
                    AgentModels.AiFilterRequest.class, AgentModels.Routing.class, AgentModels.AiFilterResult.class);

            AgentActionMetadata<AgentModels.AiPropagatorRequest, AgentModels.Routing, AgentModels.AiPropagatorResult>
                    AI_PROPAGATOR = new AgentActionMetadata<>(
                    AgentType.AI_PROPAGATOR, AGENT_NAME_AI_PROPAGATOR,
                    ACTION_AI_PROPAGATOR, METHOD_AI_PROPAGATOR,
                    TEMPLATE_AI_PROPAGATOR,
                    AgentModels.AiPropagatorRequest.class, AgentModels.Routing.class, AgentModels.AiPropagatorResult.class);

            AgentActionMetadata<AgentModels.AiTransformerRequest, AgentModels.Routing, AgentModels.AiTransformerResult>
                    AI_TRANSFORMER = new AgentActionMetadata<>(
                    AgentType.AI_TRANSFORMER, AGENT_NAME_AI_TRANSFORMER,
                    ACTION_AI_TRANSFORMER, METHOD_AI_TRANSFORMER,
                    TEMPLATE_AI_TRANSFORMER,
                    AgentModels.AiTransformerRequest.class, AgentModels.Routing.class, AgentModels.AiTransformerResult.class);

            // ── Inter-agent communication LLM calls ──

            AgentActionMetadata<AgentModels.AgentToAgentRequest, AgentModels.AgentCallRouting, AgentModels.AgentCallResult>
                    CALL_AGENT = new AgentActionMetadata<>(
                    AgentType.AGENT_CALL, AGENT_NAME_AGENT_CALL,
                    ACTION_AGENT_CALL, METHOD_CALL_AGENT,
                    TEMPLATE_COMMUNICATION_AGENT_CALL,
                    AgentModels.AgentToAgentRequest.class, AgentModels.AgentCallRouting.class, AgentModels.AgentCallResult.class);

            AgentActionMetadata<AgentModels.AgentToControllerRequest, AgentModels.ControllerCallRouting, AgentModels.ControllerCallResult>
                    CALL_CONTROLLER = new AgentActionMetadata<>(
                    AgentType.CONTROLLER_CALL, AGENT_NAME_CONTROLLER_CALL,
                    ACTION_CONTROLLER_CALL, METHOD_CALL_CONTROLLER,
                    TEMPLATE_COMMUNICATION_CONTROLLER_CALL,
                    AgentModels.AgentToControllerRequest.class, AgentModels.ControllerCallRouting.class, AgentModels.ControllerCallResult.class);

            AgentActionMetadata<AgentModels.ControllerToAgentRequest, AgentModels.ControllerResponseRouting, AgentModels.ControllerResponseResult>
                    RESPOND_TO_AGENT = new AgentActionMetadata<>(
                    AgentType.CONTROLLER_RESPONSE, AGENT_NAME_CONTROLLER_RESPONSE,
                    ACTION_CONTROLLER_RESPONSE, METHOD_RESPOND_TO_AGENT,
                    TEMPLATE_COMMUNICATION_CONTROLLER_RESPONSE,
                    AgentModels.ControllerToAgentRequest.class, AgentModels.ControllerResponseRouting.class, AgentModels.ControllerResponseResult.class);

            List<AgentActionMetadata<?, ?, ?>> ALL = List.of(
                    CONTEXT_MANAGER_STUCK, CONTEXT_MANAGER_REQUEST,
                    COORDINATE_WORKFLOW, CONSOLIDATE_WORKFLOW_OUTPUTS,
                    CONSOLIDATE_DISCOVERY_FINDINGS, CONSOLIDATE_PLANS_INTO_TICKETS,
                    CONSOLIDATE_TICKET_RESULTS,
                    KICK_OFF_DISCOVERY_AGENTS, DECOMPOSE_PLAN, ORCHESTRATE_TICKET_EXECUTION,
                    DISPATCH_DISCOVERY_AGENTS, DISPATCH_PLANNING_AGENTS, DISPATCH_TICKET_AGENTS,
                    RUN_TICKET_AGENT, RUN_PLANNING_AGENT, RUN_DISCOVERY_AGENT,
                    HANDLE_UNIFIED_INTERRUPT,
                    TICKET_AGENT_INTERRUPT, PLANNING_AGENT_INTERRUPT, DISCOVERY_AGENT_INTERRUPT,
                    INTERRUPT_REVIEW_RESOLUTION, INTERRUPT_AGENT_REVIEW,
                    WORKTREE_AUTO_COMMIT, WORKTREE_MERGE_CONFLICT,
                    AI_FILTER, AI_PROPAGATOR, AI_TRANSFORMER,
                    CALL_AGENT, CALL_CONTROLLER, RESPOND_TO_AGENT
            );


            class AgentMetadataRegistry {
                public static final Map<String, AgentActionMetadata<?, ?, ?>> BY_KEY;
                static {
                    var map = new java.util.HashMap<String, AgentActionMetadata<?, ?, ?>>();
                    for (var m : ALL) {
                        map.put(key(m.agentName(), m.actionName(), m.methodName()), m);
                    }
                    BY_KEY = Map.copyOf(map);
                }
            }


            private static String key(String agentName, String actionName, String methodName) {
                return agentName + "::" + actionName + "::" + methodName;
            }

            /**
             * Look up metadata by agent/action/method triple. Returns null if not found.
             */
            static AgentActionMetadata<?, ?, ?> findByAction(String agentName, String actionName, String methodName) {
                return AgentMetadataRegistry.BY_KEY.get(key(agentName, actionName, methodName));
            }
        }

    }



    String WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT = "WorkflowDiscoveryDispatchSubagent";
    String WORKFLOW_PLANNING_DISPATCH_SUBAGENT = "WorkflowPlanningDispatchSubagent";
    String WORKFLOW_TICKET_DISPATCH_SUBAGENT = "WorkflowTicketDispatchSubagent";
    String WORKFLOW_CONTEXT_DISPATCH_SUBAGENT = "WorkflowContextDispatchSubagent";

    String AGENT_NAME_NONE = "";
    String AGENT_NAME_TICKET_ROUTING = "ticket-routing";
    String AGENT_NAME_PLANNING_AGENT_DISPATCH = "planning-agent-dispatch";

    String ACTION_NONE = "";
    String ACTION_CONTEXT_MANAGER_STUCK = "context-manager-stuck";
    String ACTION_CONTEXT_MANAGER = "context-manager";
    String ACTION_CONTEXT_MANAGER_INTERRUPT = "context-manager-interrupt";
    String ACTION_CONTEXT_MANAGER_ROUTE = "context-manager-route";
    String ACTION_ORCHESTRATOR_COLLECTOR = "orchestrator-collector";
    String ACTION_DISCOVERY_COLLECTOR = "discovery-collector";
    String ACTION_PLANNING_COLLECTOR = "planning-collector";
    String ACTION_TICKET_COLLECTOR = "ticket-collector";
    String ACTION_ORCHESTRATOR = "orchestrator";
    String ACTION_UNIFIED_INTERRUPT = "unified-interrupt";
    String ACTION_ORCHESTRATOR_INTERRUPT = "orchestrator-interrupt";
    String ACTION_DISCOVERY_ORCHESTRATOR = "discovery-orchestrator";
    String ACTION_DISCOVERY_DISPATCH = "discovery-dispatch";
    String ACTION_DISCOVERY_INTERRUPT = "discovery-interrupt";
    String ACTION_PLANNING_ORCHESTRATOR = "planning-orchestrator";
    String ACTION_PLANNING_DISPATCH = "planning-dispatch";
    String ACTION_PLANNING_AGENT_POST_DISPATCH = "planning-agent-post-dispatch";
    String ACTION_PLANNING_INTERRUPT = "planning-interrupt";
    String ACTION_TICKET_ORCHESTRATOR = "ticket-orchestrator";
    String ACTION_TICKET_DISPATCH = "ticket-dispatch";
    String ACTION_TICKET_INTERRUPT = "ticket-interrupt";
    String ACTION_REVIEW_AGENT = "review-agent";
    String ACTION_TICKET_COLLECTOR_ROUTING_BRANCH = "ticket-collector-routing-branch";
    String ACTION_TICKET_AGENT_INTERRUPT = "ticket-agent-interrupt";
    String ACTION_TICKET_AGENT = "ticket-agent";
    String ACTION_PLANNING_AGENT_INTERRUPT = "planning-agent-interrupt";
    String ACTION_PLANNING_AGENT = "planning-agent";
    String ACTION_DISCOVERY_AGENT_INTERRUPT = "discovery-agent-interrupt";
    String ACTION_DISCOVERY_AGENT = "discovery-agent";

    String METHOD_NONE = "";
    String METHOD_HANDLE_STUCK = "handleStuck";
    String METHOD_HANDLE_CONTEXT_MANAGER_INTERRUPT = "handleContextManagerInterrupt";
    String METHOD_ROUTE_TO_CONTEXT_MANAGER = "routeToContextManager";
    String METHOD_CONTEXT_MANAGER = "contextManagerRequest";
    String METHOD_FINAL_COLLECTOR_RESULT = "finalCollectorResult";
    String METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS = "consolidateWorkflowOutputs";
    String METHOD_CONSOLIDATE_DISCOVERY_FINDINGS = "consolidateDiscoveryFindings";
    String METHOD_CONSOLIDATE_PLANS_INTO_TICKETS = "consolidatePlansIntoTickets";
    String METHOD_CONSOLIDATE_TICKET_RESULTS = "consolidateTicketResults";
    String METHOD_COORDINATE_WORKFLOW = "coordinateWorkflow";
    String METHOD_HANDLE_UNIFIED_INTERRUPT = "handleUnifiedInterrupt";
    String METHOD_HANDLE_ORCHESTRATOR_INTERRUPT = "handleOrchestratorInterrupt";
    String METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH = "kickOffAnyNumberOfAgentsForCodeSearch";
    String METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS = "dispatchDiscoveryAgentRequests";
    String METHOD_HANDLE_DISCOVERY_INTERRUPT = "handleDiscoveryInterrupt";
    String METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS = "decomposePlanAndCreateWorkItems";
    String METHOD_DISPATCH_PLANNING_AGENT_REQUESTS = "dispatchPlanningAgentRequests";
    String METHOD_HANDLE_PLANNING_INTERRUPT = "handlePlanningInterrupt";
    String METHOD_FINALIZE_TICKET_ORCHESTRATOR = "finalizeTicketOrchestrator";
    String METHOD_ORCHESTRATE_TICKET_EXECUTION = "orchestrateTicketExecution";
    String METHOD_DISPATCH_TICKET_AGENT_REQUESTS = "dispatchTicketAgentRequests";
    String METHOD_HANDLE_TICKET_INTERRUPT = "handleTicketInterrupt";
    String METHOD_HANDLE_TICKET_COLLECTOR_BRANCH = "handleTicketCollectorBranch";
    String METHOD_HANDLE_DISCOVERY_COLLECTOR_BRANCH = "handleDiscoveryCollectorBranch";
    String METHOD_HANDLE_ORCHESTRATOR_COLLECTOR_BRANCH = "handleOrchestratorCollectorBranch";
    String METHOD_HANDLE_PLANNING_COLLECTOR_BRANCH = "handlePlanningCollectorBranch";
    String METHOD_TRANSITION_TO_INTERRUPT_STATE = "transitionToInterruptState";
    String METHOD_RUN_TICKET_AGENT = "runTicketAgent";
    String METHOD_RUN_PLANNING_AGENT = "runPlanningAgent";
    String METHOD_RUN_DISCOVERY_AGENT = "runDiscoveryAgent";

    String STUCK_HANDLER = "stuck-handler";
    String TEMPLATE_WORKFLOW_CONTEXT_MANAGER = "workflow/context_manager";
    String TEMPLATE_WORKFLOW_CONTEXT_MANAGER_INTERRUPT = "workflow/context_manager_interrupt";
    String TEMPLATE_WORKFLOW_ORCHESTRATOR_COLLECTOR = "workflow/orchestrator_collector";
    String TEMPLATE_WORKFLOW_DISCOVERY_COLLECTOR = "workflow/discovery_collector";
    String TEMPLATE_WORKFLOW_PLANNING_COLLECTOR = "workflow/planning_collector";
    String TEMPLATE_WORKFLOW_TICKET_COLLECTOR = "workflow/ticket_collector";
    String TEMPLATE_WORKFLOW_ORCHESTRATOR = "workflow/orchestrator";
    String TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR = "workflow/discovery_orchestrator";
    String TEMPLATE_WORKFLOW_DISCOVERY_DISPATCH = "workflow/discovery_dispatch";
    String TEMPLATE_WORKFLOW_PLANNING_ORCHESTRATOR = "workflow/planning_orchestrator";
    String TEMPLATE_WORKFLOW_PLANNING_DISPATCH = "workflow/planning_dispatch";
    String TEMPLATE_WORKFLOW_TICKET_ORCHESTRATOR = "workflow/ticket_orchestrator";
    String TEMPLATE_WORKFLOW_TICKET_DISPATCH = "workflow/ticket_dispatch";
    String TEMPLATE_WORKFLOW_REVIEW = "workflow/review";
    String TEMPLATE_WORKFLOW_TICKET_AGENT = "workflow/ticket_agent";
    String TEMPLATE_WORKFLOW_PLANNING_AGENT = "workflow/planning_agent";
    String TEMPLATE_WORKFLOW_DISCOVERY_AGENT = "workflow/discovery_agent";
    // InterruptService constants (sourced from InterruptService)
    String AGENT_NAME_INTERRUPT_SERVICE = InterruptService.AGENT_NAME;
    String ACTION_INTERRUPT_AGENT_REVIEW = InterruptService.ACTION_AGENT_REVIEW;
    String METHOD_HANDLE_INTERRUPT = InterruptService.METHOD_HANDLE_INTERRUPT;
    String METHOD_RUN_INTERRUPT_AGENT_REVIEW = InterruptService.METHOD_RUN_INTERRUPT_AGENT_REVIEW;
    String TEMPLATE_REVIEW_RESOLUTION = InterruptService.TEMPLATE_REVIEW_RESOLUTION;

    // WorktreeAutoCommitService constants (sourced from WorktreeAutoCommitService)
    String AGENT_NAME_WORKTREE_AUTO_COMMIT = WorktreeAutoCommitService.AGENT_NAME;
    String ACTION_COMMIT_AGENT = WorktreeAutoCommitService.ACTION_NAME;
    String METHOD_RUN_COMMIT_AGENT = WorktreeAutoCommitService.METHOD_NAME;
    String TEMPLATE_WORKTREE_COMMIT_AGENT = WorktreeAutoCommitService.TEMPLATE_WORKTREE_COMMIT_AGENT;

    // WorktreeMergeConflictService constants (sourced from WorktreeMergeConflictService)
    String AGENT_NAME_WORKTREE_MERGE_CONFLICT = WorktreeMergeConflictService.AGENT_NAME;
    String ACTION_MERGE_CONFLICT_AGENT = WorktreeMergeConflictService.ACTION_NAME;
    String METHOD_RUN_MERGE_CONFLICT_AGENT = WorktreeMergeConflictService.METHOD_NAME;
    String TEMPLATE_WORKTREE_MERGE_CONFLICT_AGENT = WorktreeMergeConflictService.TEMPLATE;

    // AI filter constants (sourced from FilterExecutionService)
    String AGENT_NAME_AI_FILTER = FilterExecutionService.AI_FILTER_AGENT_NAME;
    String ACTION_AI_FILTER = FilterExecutionService.AI_FILTER_ACTION_NAME;
    String METHOD_AI_FILTER = FilterExecutionService.AI_FILTER_METHOD_NAME;
    String TEMPLATE_AI_FILTER = FilterExecutionService.AI_FILTER_TEMPLATE_NAME;

    // AI propagator constants (sourced from PropagationExecutionService)
    String AGENT_NAME_AI_PROPAGATOR = PropagationExecutionService.AI_PROPAGATOR_AGENT_NAME;
    String ACTION_AI_PROPAGATOR = PropagationExecutionService.AI_PROPAGATOR_ACTION_NAME;
    String METHOD_AI_PROPAGATOR = PropagationExecutionService.AI_PROPAGATOR_METHOD_NAME;
    String TEMPLATE_AI_PROPAGATOR = PropagationExecutionService.AI_PROPAGATOR_TEMPLATE_NAME;

    // AI transformer constants (sourced from TransformerExecutionService)
    String AGENT_NAME_AI_TRANSFORMER = TransformerExecutionService.AI_TRANSFORMER_AGENT_NAME;
    String ACTION_AI_TRANSFORMER = TransformerExecutionService.AI_TRANSFORMER_ACTION_NAME;
    String METHOD_AI_TRANSFORMER = TransformerExecutionService.AI_TRANSFORMER_METHOD_NAME;
    String TEMPLATE_AI_TRANSFORMER = TransformerExecutionService.AI_TRANSFORMER_TEMPLATE_NAME;

    // Agent call (inter-agent communication) constants
    String AGENT_NAME_AGENT_CALL = "agent-call";
    String ACTION_AGENT_CALL = "agent-call";
    String METHOD_CALL_AGENT = "callAgent";
    String TEMPLATE_COMMUNICATION_AGENT_CALL = "communication/agent_call";

    // Controller call (agent-to-controller communication) constants
    String AGENT_NAME_CONTROLLER_CALL = "controller-call";
    String ACTION_CONTROLLER_CALL = "controller-call";
    String METHOD_CALL_CONTROLLER = "callController";
    String TEMPLATE_COMMUNICATION_CONTROLLER_CALL = "communication/controller_call";

    // Controller response (controller-to-agent communication) constants
    String AGENT_NAME_CONTROLLER_RESPONSE = "controller-response";
    String ACTION_CONTROLLER_RESPONSE = "controller-response";
    String METHOD_RESPOND_TO_AGENT = "respondToAgent";
    String TEMPLATE_COMMUNICATION_CONTROLLER_RESPONSE = "communication/controller_response";

    String UNKNOWN_VALUE = "unknown";
    String RETURN_ROUTE_NONE = "none";

    static void publishDegenerateLoopError(
            EventBus eventBus,
            OperationContext context,
            String reason,
            String methodName,
            Class<?> requestType,
            int loopInvocation
    ) {
        if (eventBus == null || context == null) {
            return;
        }
        String processId = Optional.of(context.getAgentProcess())
                .map(AgentProcess::getId)
                .orElse(null);
        if (processId == null) {
            return;
        }
        String loopType = StringUtils.isNotBlank(methodName) ? methodName : UNKNOWN_VALUE;
        String requestTypeName = requestType != null ? requestType.getSimpleName() : UNKNOWN_VALUE;
        String detail = "Degenerate loop (%s) for %s at invocation %d: %s"
                .formatted(loopType, requestTypeName, loopInvocation, reason);
        eventBus.publish(Events.NodeErrorEvent.err(detail, new ArtifactKey(processId)));
    }

    static DegenerateLoopException degenerateLoop(
            EventBus eventBus,
            OperationContext context,
            String reason,
            String methodName,
            Class<?> requestType,
            int loopInvocation
    ) {
        publishDegenerateLoopError(eventBus, context, reason, methodName, requestType, loopInvocation);
        return new DegenerateLoopException(reason, methodName, requestType, loopInvocation);
    }

    static OperationContext buildOpContext(AgentProcess agentProcess, String name) {
        if (agentProcess == null) {
            return null;
        }
        Operation operation = InjectedType.Companion.named(name);
        return OperationContext.Companion.invoke(
                agentProcess.getProcessContext(),
                operation,
                Set.of()
        );
    }

    String multiAgentAgentName();

    String WORKFLOW_AGENT_NAME = WorkflowAgent.class.getName();

    AgentInterfaces WORKFLOW_AGENT = () -> WORKFLOW_AGENT_NAME;
    AgentInterfaces ORCHESTRATOR_AGENT = WORKFLOW_AGENT;
    AgentInterfaces DISCOVERY_ORCHESTRATOR_AGENT = WORKFLOW_AGENT;
    AgentInterfaces PLANNING_ORCHESTRATOR_AGENT = WORKFLOW_AGENT;
    AgentInterfaces TICKET_ORCHESTRATOR_AGENT = WORKFLOW_AGENT;
    AgentInterfaces REVIEW_AGENT = WORKFLOW_AGENT;
    AgentInterfaces MERGER_AGENT = WORKFLOW_AGENT;
    AgentInterfaces CONTEXT_ORCHESTRATOR_AGENT = WORKFLOW_AGENT;


    @EmbabelComponent(scan = false)
    @RequiredArgsConstructor
    @Slf4j
    class WorkflowAgent implements AgentInterfaces, StuckHandler {

        private final WorkflowGraphService workflowGraphService;
        private final InterruptService interruptService;
        private final PromptContextFactory promptContextFactory;
        private final LlmRunner llmRunner;
        private final ContextManagerTools contextManagerTools;
        private final BlackboardHistoryService blackboardHistoryService;
        private final AgentExecutor agentExecutor;

        @Autowired(required = false)
        private List<ResultDecorator> resultDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<RequestDecorator> requestDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<ResultsRequestDecorator> resultsRequestDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<FinalResultDecorator> finalResultDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<PromptContextDecorator> promptContextDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<ToolContextDecorator> toolContextDecorators = new ArrayList<>();

        @Autowired
        private EventBus eventBus;

        @Override
        public String multiAgentAgentName() {
            return WORKFLOW_AGENT_NAME;
        }

        private static final int MAX_STUCK_HANDLER_INVOCATIONS = 3;

        @Override
        public StuckHandlerResult handleStuck(AgentProcess agentProcess) {
            OperationContext context = buildOpContext(agentProcess, STUCK_HANDLER);
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);

            if (history == null)
                return new StuckHandlerResult(
                        "Stuck handler called with no blackboard history. Systemic failure!",
                        this,
                        StuckHandlingResultCode.NO_RESOLUTION,
                        agentProcess
                );

            // Guard against infinite stuck handler re-entry.
            var lastSize = history.fromHistory(s -> {
                List<AgentModels.ContextManagerRequest> cmr = s.getLast(AgentModels.ContextManagerRequest.class);
                if (cmr.size() >= MAX_STUCK_HANDLER_INVOCATIONS
                        && cmr.stream().allMatch(c -> STUCK_HANDLER.equals(c.reason()))) {
                    return cmr.size();
                }

                return -1;
            });

            if (lastSize != -1)
                return new StuckHandlerResult(
                        "Stuck handler exhausted after " + lastSize + " attempts",
                        this,
                        StuckHandlingResultCode.NO_RESOLUTION,
                        agentProcess
                );

            String loopSummary = Optional.of(history)
                    .map(BlackboardHistory::summary)
                    .filter(StringUtils::isNotBlank)
                    .orElse("No history available");

            AgentModels.AgentRequest anyLastRequest = BlackboardHistory.findLastRequest(
                    history,
                    a -> a instanceof AgentModels.AgentRequest);

            if (anyLastRequest instanceof AgentModels.ContextManagerRequest c) {
//                TODO: this should probably route for human/agent review - in that case.
            }

            AgentModels.AgentRequest lastRequest = BlackboardHistory.findLastNonContextRequest(history);

            AgentModels.ContextManagerRequest request = AgentModels.ContextManagerRequest.builder()
                    .reason(STUCK_HANDLER)
                    .build()
                    .addRequest(lastRequest);

            AgentModels.ContextManagerResultRouting routing = agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.ContextManagerRequest, AgentModels.ContextManagerResultRouting, AgentModels.AgentResult, AgentModels.ContextManagerResultRouting>builder()
                            .responseClazz(AgentModels.ContextManagerResultRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.CONTEXT_MANAGER_STUCK)
                            .previousRequest(lastRequest)
                            .currentRequest(request)
                            .templateModelProducer(a -> {
                                var m = new HashMap<String, Object>(Map.of("reason", loopSummary));
                                Optional.ofNullable(a.enrichedRequest().goal()).ifPresent(g -> m.put("goal", g));
                                return m;
                            })
                            .operationContext(context)
                            .baseToolContext(ToolContext.of(ToolAbstraction.fromToolCarrier(contextManagerTools)))
                            .build());

            if (agentProcess != null) {
                agentProcess.addObject(routing);
            }

            return new StuckHandlerResult(
                    "Context manager recovery invoked",
                    this,
                    StuckHandlingResultCode.REPLAN,
                    agentProcess
            );
        }

        @Action(canRerun = true, cost = 2)
        public AgentModels.ContextManagerRequest routeToContextManager(
                AgentModels.ContextManagerRoutingRequest request,
                OperationContext context
        ) {
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
            AgentModels.AgentRequest lastRequest = BlackboardHistory.findLastNonContextRequest(history);

            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context,
                        "Upstream request not found - cannot route to context manager.",
                        METHOD_ROUTE_TO_CONTEXT_MANAGER,
                        AgentModels.ContextManagerRoutingRequest.class,
                        1
                );
            }

            request = decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER_ROUTE,
                    METHOD_ROUTE_TO_CONTEXT_MANAGER,
                    lastRequest
            );

            AgentModels.ContextManagerRequest contextManagerRequest = AgentModels.ContextManagerRequest.builder()
                    .reason(request != null ? request.reason() : "%s routed to context manager, but did not provide reason.".formatted(Optional.ofNullable(lastRequest).map(a -> a.getClass().getName()).orElse("Unknown agent")))
                    .type(Optional.ofNullable(request)
                            .flatMap(r -> Optional.ofNullable(r.type()))
                            .orElse(AgentModels.ContextManagerRequestType.INTROSPECT_AGENT_CONTEXT))
                    .build()
                    .addRequest(lastRequest);

            contextManagerRequest = decorateRequest(
                    contextManagerRequest,
                    context,
                    requestDecorators,
                    AGENT_NAME_NONE,
                    ACTION_CONTEXT_MANAGER_ROUTE,
                    METHOD_ROUTE_TO_CONTEXT_MANAGER,
                    lastRequest
            );

            return DecorateRequestResults.decorateRequestResult(
                    contextManagerRequest,
                    context,
                    resultDecorators.stream().filter(r -> !(r instanceof RequestDecorator))
                            .toList(),
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER_ROUTE,
                    METHOD_ROUTE_TO_CONTEXT_MANAGER,
                    lastRequest
            );
        }


        @Action(canRerun = true, cost = 1)
        public AgentModels.ContextManagerResultRouting contextManagerRequest(
                AgentModels.ContextManagerRequest request,
                OperationContext context
        ) {

            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);

            String loopSummary = Optional.ofNullable(history)
                    .map(BlackboardHistory::summary)
                    .orElse("No history available");

            AgentModels.AgentRequest anyLastRequest = BlackboardHistory.findLastRequest(
                    history,
                    a -> a instanceof AgentModels.AgentRequest);

            if (anyLastRequest instanceof AgentModels.ContextManagerRequest c) {
//                TODO: this should probably route for human/agent review - in that case.
            }

            AgentModels.AgentRequest lastRequest = BlackboardHistory.findLastNonContextRequest(history);

            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.ContextManagerRequest, AgentModels.ContextManagerResultRouting, AgentModels.AgentResult, AgentModels.ContextManagerResultRouting>builder()
                            .responseClazz(AgentModels.ContextManagerResultRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.CONTEXT_MANAGER_REQUEST)
                            .previousRequest(lastRequest)
                            .currentRequest(request)
                            .templateModelProducer(a -> {
                                var m = new HashMap<String, Object>(Map.of("reason", loopSummary));
                                Optional.ofNullable(a.enrichedRequest().goal()).ifPresent(g -> m.put("goal", g));
                                return m;
                            })
                            .operationContext(context)
                            .baseToolContext(ToolContext.of(ToolAbstraction.fromToolCarrier(contextManagerTools)))
                            .build());
        }

        @Action(canRerun = true)
        public AgentModels.OrchestratorRouting coordinateWorkflow(
                AgentModels.OrchestratorRequest input,
                OperationContext context
        ) {
            AgentModels.AgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.AgentRequest.class);

            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.OrchestratorRequest, AgentModels.OrchestratorRouting, AgentModels.AgentResult, AgentModels.OrchestratorRouting>builder()
                            .responseClazz(AgentModels.OrchestratorRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.COORDINATE_WORKFLOW)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> {
                                var m = new HashMap<String, Object>();
                                Optional.ofNullable(a.enrichedRequest().goal()).ifPresent(g -> m.put("goal", g));
                                Optional.ofNullable(a.enrichedRequest().phase()).ifPresent(g -> m.put("phase", g));
                                return m;
                            })
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }

        /**
         * Build prompt context by extracting upstream prev from typed curation fields on the input.
         * Delegates to PromptContextFactory for pattern matching logic.
         */
        private PromptContext buildPromptContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName,
                Map<String, Object> model
        ) {
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
            DecoratorContext decoratorContext = new DecoratorContext(
                    context, multiAgentAgentName(), actionName, methodName, previousRequest, currentRequest
            );
            PromptContext promptContext = promptContextFactory.build(
                    agentType,
                    contextRequest,
                    previousRequest,
                    currentRequest,
                    history,
                    templateName,
                    model,
                    context,
                    decoratorContext
            );
            return decoratePromptContext(
                    promptContext,
                    promptContextDecorators,
                    decoratorContext
            );
        }

        private ToolContext buildToolContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName,
                ToolContext toolContext
        ) {
            return DecorateRequestResults.decorateToolContext(
                    toolContext,
                    currentRequest,
                    previousRequest,
                    context,
                    toolContextDecorators,
                    multiAgentAgentName(),
                    actionName,
                    methodName
            );
        }

        @Action(canRerun = true, cost = 1)
        public AgentModels.InterruptRouting handleUnifiedInterrupt(
                AgentModels.InterruptRequest request,
                OperationContext context
        ) {
            InterruptRouteConfig routeConfig = resolveInterruptRouteConfig(request, context);
            if (routeConfig == null || routeConfig.lastRequest() == null) {
                throw AgentInterfaces.degenerateLoop(
                        eventBus,
                        context,
                        "Unable to resolve unified interrupt route.",
                        METHOD_HANDLE_UNIFIED_INTERRUPT,
                        request != null ? request.getClass() : AgentModels.InterruptRequest.class,
                        1
                );
            }

            AgentModels.InterruptRequest decoratedRequest = decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_UNIFIED_INTERRUPT,
                    METHOD_HANDLE_UNIFIED_INTERRUPT,
                    routeConfig.lastRequest()
            );

            PromptContext promptContext = buildPromptContext(
                    routeConfig.agentType(),
                    routeConfig.lastRequest(),
                    routeConfig.lastRequest(),
                    decoratedRequest,
                    context,
                    ACTION_UNIFIED_INTERRUPT,
                    METHOD_HANDLE_UNIFIED_INTERRUPT,
                    routeConfig.templateName(),
                    routeConfig.model()
            );

            AgentModels.InterruptRouting routing = interruptService.handleInterrupt(
                    context,
                    decoratedRequest,
                    routeConfig.originNode(),
                    routeConfig.templateName(),
                    promptContext,
                    routeConfig.model(),
                    routeConfig.toolContext(),
                    AgentModels.InterruptRouting.class
            );

            return decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_UNIFIED_INTERRUPT,
                    METHOD_HANDLE_UNIFIED_INTERRUPT,
                    routeConfig.lastRequest()
            );
        }

        private record InterruptRouteConfig(
                AgentType agentType,
                AgentModels.AgentRequest lastRequest,
                GraphNode originNode,
                String templateName,
                Map<String, Object> model,
                ToolContext toolContext
        ) {}

        private InterruptRouteConfig resolveInterruptRouteConfig(
                AgentModels.InterruptRequest request,
                OperationContext context
        ) {
            return switch (request) {
                case AgentModels.InterruptRequest.OrchestratorInterruptRequest ignored -> {
                    AgentModels.OrchestratorRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
                    yield new InterruptRouteConfig(
                            AgentType.ORCHESTRATOR,
                            lastRequest,
                            workflowGraphService.requireOrchestrator(context),
                            TEMPLATE_WORKFLOW_ORCHESTRATOR,
                            mapGoalAndPhase(lastRequest != null ? lastRequest.goal() : null, lastRequest != null ? lastRequest.phase() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest ignored -> {
                    AgentModels.OrchestratorCollectorRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorCollectorRequest.class);
                    yield new InterruptRouteConfig(
                            AgentType.ORCHESTRATOR_COLLECTOR,
                            lastRequest,
                            workflowGraphService.requireOrchestratorCollector(context),
                            TEMPLATE_WORKFLOW_ORCHESTRATOR_COLLECTOR,
                            mapGoalAndPhase(lastRequest != null ? lastRequest.goal() : null, lastRequest != null ? lastRequest.phase() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest ignored -> {
                    AgentModels.DiscoveryOrchestratorRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
                    yield new InterruptRouteConfig(
                            AgentType.DISCOVERY_ORCHESTRATOR,
                            lastRequest,
                            workflowGraphService.requireDiscoveryOrchestrator(context),
                            TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest ignored -> {
                    AgentModels.DiscoveryCollectorRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryCollectorRequest.class);
                    yield new InterruptRouteConfig(
                            AgentType.DISCOVERY_COLLECTOR,
                            lastRequest,
                            workflowGraphService.requireDiscoveryCollector(context),
                            TEMPLATE_WORKFLOW_DISCOVERY_COLLECTOR,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest ignored -> {
                    AgentModels.PlanningOrchestratorRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
                    yield new InterruptRouteConfig(
                            AgentType.PLANNING_ORCHESTRATOR,
                            lastRequest,
                            workflowGraphService.requirePlanningOrchestrator(context),
                            TEMPLATE_WORKFLOW_PLANNING_ORCHESTRATOR,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest ignored -> {
                    AgentModels.PlanningCollectorRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningCollectorRequest.class);
                    yield new InterruptRouteConfig(
                            AgentType.PLANNING_COLLECTOR,
                            lastRequest,
                            workflowGraphService.requirePlanningCollector(context),
                            TEMPLATE_WORKFLOW_PLANNING_COLLECTOR,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest ignored -> {
                    AgentModels.TicketOrchestratorRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
                    yield new InterruptRouteConfig(
                            AgentType.TICKET_ORCHESTRATOR,
                            lastRequest,
                            workflowGraphService.requireTicketOrchestrator(context),
                            TEMPLATE_WORKFLOW_TICKET_ORCHESTRATOR,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.TicketCollectorInterruptRequest ignored -> {
                    AgentModels.TicketCollectorRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.TicketCollectorRequest.class);
                    yield new InterruptRouteConfig(
                            AgentType.TICKET_COLLECTOR,
                            lastRequest,
                            workflowGraphService.requireTicketCollector(context),
                            TEMPLATE_WORKFLOW_TICKET_COLLECTOR,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.ContextManagerInterruptRequest cmRequest -> {
                    AgentModels.ContextManagerRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.ContextManagerRequest.class);
                    Map<String, Object> model = Map.of(
                            "reason",
                            firstNonBlank(cmRequest.reason(), cmRequest.contextForDecision(), cmRequest.contextFindings())
                    );
                    yield new InterruptRouteConfig(
                            AgentType.CONTEXT_MANAGER,
                            lastRequest,
                            workflowGraphService.requireOrchestrator(context),
                            TEMPLATE_WORKFLOW_CONTEXT_MANAGER_INTERRUPT,
                            model,
                            ToolContext.of(ToolAbstraction.fromToolCarrier(contextManagerTools))
                    );
                }
                case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest ignored -> {
                    AgentModels.DiscoveryAgentRequests lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryAgentRequests.class);
                    yield new InterruptRouteConfig(
                            AgentType.DISCOVERY_AGENT_DISPATCH,
                            lastRequest,
                            workflowGraphService.requireDiscoveryOrchestrator(context),
                            TEMPLATE_WORKFLOW_DISCOVERY_DISPATCH,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest ignored -> {
                    AgentModels.PlanningAgentRequests lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningAgentRequests.class);
                    yield new InterruptRouteConfig(
                            AgentType.PLANNING_AGENT_DISPATCH,
                            lastRequest,
                            workflowGraphService.requirePlanningOrchestrator(context),
                            TEMPLATE_WORKFLOW_PLANNING_DISPATCH,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest ignored -> {
                    AgentModels.TicketAgentRequests lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.TicketAgentRequests.class);
                    yield new InterruptRouteConfig(
                            AgentType.TICKET_AGENT_DISPATCH,
                            lastRequest,
                            workflowGraphService.requireTicketOrchestrator(context),
                            TEMPLATE_WORKFLOW_TICKET_DISPATCH,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest ignored -> {
                    AgentModels.DiscoveryAgentRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryAgentRequest.class);
                    GraphNode originNode = workflowGraphService.findNodeForContext(context)
                            .filter(n -> n instanceof DiscoveryNode dn && dn.interruptibleContext() != null)
                            .orElseGet(() -> workflowGraphService.requireDiscoveryDispatch(context));
                    yield new InterruptRouteConfig(
                            AgentType.DISCOVERY_AGENT,
                            lastRequest,
                            originNode,
                            TEMPLATE_WORKFLOW_DISCOVERY_AGENT,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.PlanningAgentInterruptRequest ignored -> {
                    AgentModels.PlanningAgentRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningAgentRequest.class);
                    GraphNode originNode = workflowGraphService.findNodeForContext(context)
                            .filter(n -> n instanceof PlanningNode pn && pn.interruptibleContext() != null)
                            .orElseGet(() -> workflowGraphService.requirePlanningDispatch(context));
                    yield new InterruptRouteConfig(
                            AgentType.PLANNING_AGENT,
                            lastRequest,
                            originNode,
                            TEMPLATE_WORKFLOW_PLANNING_AGENT,
                            mapGoal(lastRequest != null ? lastRequest.goal() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.TicketAgentInterruptRequest ignored -> {
                    AgentModels.TicketAgentRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.TicketAgentRequest.class);
                    GraphNode originNode = workflowGraphService.findNodeForContext(context)
                            .filter(n -> n instanceof TicketNode tn && tn.interruptibleContext() != null)
                            .orElseGet(() -> workflowGraphService.requireTicketDispatch(context));
                    yield new InterruptRouteConfig(
                            AgentType.TICKET_AGENT,
                            lastRequest,
                            originNode,
                            TEMPLATE_WORKFLOW_TICKET_AGENT,
                            mapGoal(lastRequest != null ? lastRequest.ticketDetails() : null),
                            ToolContext.empty()
                    );
                }
                case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest ignored -> {
                    AgentModels.OrchestratorRequest lastRequest =
                            BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
                    yield new InterruptRouteConfig(
                            AgentType.ORCHESTRATOR,
                            lastRequest,
                            workflowGraphService.requireOrchestrator(context),
                            TEMPLATE_WORKFLOW_ORCHESTRATOR,
                            mapGoalAndPhase(lastRequest != null ? lastRequest.goal() : null, lastRequest != null ? lastRequest.phase() : null),
                            ToolContext.empty()
                    );
                }
            };
        }

        private static Map<String, Object> mapGoalAndPhase(String goal, String phase) {
            Map<String, Object> model = new HashMap<>();
            Optional.ofNullable(goal).ifPresent(g -> model.put("goal", g));
            Optional.ofNullable(phase).ifPresent(p -> model.put("phase", p));
            return model;
        }

        private static Map<String, Object> mapGoal(String goal) {
            Map<String, Object> model = new HashMap<>();
            Optional.ofNullable(goal).ifPresent(g -> model.put("goal", g));
            return model;
        }

        @Action
        @AchievesGoal(description = "Finished orchestrator collector")
        public AgentModels.OrchestratorCollectorResult finalCollectorResult(
                AgentModels.OrchestratorCollectorResult input,
                OperationContext context
        ) {
            AgentModels.OrchestratorCollectorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorCollectorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Orchestrator collector request not found - cannot finalize collector result.",
                        METHOD_FINAL_COLLECTOR_RESULT,
                        AgentModels.OrchestratorCollectorResult.class,
                        1
                );
            }

            return DecorateRequestResults.decorateFinalResult(
                    input,
                    lastRequest,
                    lastRequest,
                    context,
                    finalResultDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR_COLLECTOR,
                    METHOD_FINAL_COLLECTOR_RESULT
            );
        }
        


        @Action(canRerun = true)
        public AgentModels.OrchestratorCollectorRouting consolidateWorkflowOutputs(
                AgentModels.OrchestratorCollectorRequest input,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Orchestrator request not found - cannot consolidate workflow outputs.",
                        METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS,
                        AgentModels.OrchestratorCollectorRequest.class,
                        1
                );
            }
            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.OrchestratorCollectorRequest, AgentModels.OrchestratorCollectorRouting, AgentModels.OrchestratorCollectorResult, AgentModels.OrchestratorCollectorRouting>builder()
                            .responseClazz(AgentModels.OrchestratorCollectorRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.CONSOLIDATE_WORKFLOW_OUTPUTS)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> {
                                var m = new HashMap<String, Object>();
                                Optional.ofNullable(a.enrichedRequest().goal()).ifPresent(g -> m.put("goal", g));
                                Optional.ofNullable(a.enrichedRequest().phase()).ifPresent(g -> m.put("phase", g));
                                return m;
                            })
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }

        @Action(canRerun = true)
        public AgentModels.DiscoveryCollectorRouting consolidateDiscoveryFindings(
                AgentModels.DiscoveryCollectorRequest input,
                OperationContext context
        ) {
            AgentModels.DiscoveryOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Discovery orchestrator request not found - cannot consolidate discovery findings.",
                        METHOD_CONSOLIDATE_DISCOVERY_FINDINGS,
                        AgentModels.DiscoveryCollectorRequest.class,
                        1
                );
            }
            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.DiscoveryCollectorRequest, AgentModels.DiscoveryCollectorRouting, AgentModels.DiscoveryCollectorResult, AgentModels.DiscoveryCollectorRouting>builder()
                            .responseClazz(AgentModels.DiscoveryCollectorRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.CONSOLIDATE_DISCOVERY_FINDINGS)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> Map.of(
                                    "goal", Optional.ofNullable(a.enrichedRequest().goal()).orElse(""),
                                    "discoveryResults", Optional.ofNullable(a.enrichedRequest().discoveryResults()).orElse("")))
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }

        @Action(canRerun = true)
        public AgentModels.PlanningCollectorRouting consolidatePlansIntoTickets(
                AgentModels.PlanningCollectorRequest input,
                OperationContext context
        ) {
            AgentModels.PlanningOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Planning request not found - cannot consolidate plans into tickets.",
                        METHOD_CONSOLIDATE_PLANS_INTO_TICKETS,
                        AgentModels.PlanningCollectorRequest.class,
                        1
                );
            }
            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.PlanningCollectorRequest, AgentModels.PlanningCollectorRouting, AgentModels.PlanningCollectorResult, AgentModels.PlanningCollectorRouting>builder()
                            .responseClazz(AgentModels.PlanningCollectorRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.CONSOLIDATE_PLANS_INTO_TICKETS)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> Map.of(
                                    "goal", Optional.ofNullable(a.enrichedRequest().goal()).orElse(""),
                                    "planningResults", Optional.ofNullable(a.enrichedRequest().planningResults()).orElse("")))
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }

        @Action(canRerun = true)
        public AgentModels.TicketCollectorRouting consolidateTicketResults(
                AgentModels.TicketCollectorRequest input,
                OperationContext context
        ) {
            AgentModels.TicketOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Ticket request not found - cannot consolidate ticket results.",
                        METHOD_CONSOLIDATE_TICKET_RESULTS,
                        AgentModels.TicketCollectorRequest.class,
                        1
                );
            }
            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.TicketCollectorRequest, AgentModels.TicketCollectorRouting, AgentModels.TicketCollectorResult, AgentModels.TicketCollectorRouting>builder()
                            .responseClazz(AgentModels.TicketCollectorRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.CONSOLIDATE_TICKET_RESULTS)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> Map.of(
                                    "goal", Optional.ofNullable(a.enrichedRequest().goal()).orElse(""),
                                    "ticketResults", a.enrichedRequest().ticketResults()))
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }


        @Action(canRerun = true)
        public AgentModels.DiscoveryOrchestratorRouting kickOffAnyNumberOfAgentsForCodeSearch(
                AgentModels.DiscoveryOrchestratorRequest input,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Discovery orchestrator request not found - cannot kick off discovery agents.",
                        METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH,
                        AgentModels.DiscoveryOrchestratorRequest.class,
                        1
                );
            }
            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.DiscoveryOrchestratorRequest, AgentModels.DiscoveryOrchestratorRouting, AgentModels.DiscoveryOrchestratorResult, AgentModels.DiscoveryOrchestratorRouting>builder()
                            .responseClazz(AgentModels.DiscoveryOrchestratorRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.KICK_OFF_DISCOVERY_AGENTS)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> Map.of("goal", a.enrichedRequest().goal()))
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }

        @Action(canRerun = true)
        public AgentModels.DiscoveryAgentDispatchRouting dispatchDiscoveryAgentRequests(
                @NotNull AgentModels.DiscoveryAgentRequests input,
                ActionContext context
        ) {
            AgentModels.DiscoveryOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context,
                        "Discovery dispatch request not found - cannot dispatch discovery agents.",
                        METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS,
                        AgentModels.DiscoveryAgentRequests.class,
                        1
                );
            }
            return agentExecutor.runDiscoveryDispatch(input, context, lastRequest);
        }

        @Action(canRerun = true)
        public AgentModels.PlanningOrchestratorRouting decomposePlanAndCreateWorkItems(
                AgentModels.PlanningOrchestratorRequest input,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Planning orchestrator request not found - cannot decompose plan.",
                        METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS,
                        AgentModels.PlanningOrchestratorRequest.class,
                        1
                );
            }
            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.PlanningOrchestratorRequest, AgentModels.PlanningOrchestratorRouting, AgentModels.PlanningOrchestratorResult, AgentModels.PlanningOrchestratorRouting>builder()
                            .responseClazz(AgentModels.PlanningOrchestratorRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.DECOMPOSE_PLAN)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> {
                                var m = new HashMap<String, Object>();
                                Optional.ofNullable(a.enrichedRequest().goal()).ifPresent(p -> m.put("goal", p));
                                m.put("phase", "PLANNING");
                                return m;
                            })
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }

        @Action(canRerun = true)
        public AgentModels.PlanningAgentDispatchRouting dispatchPlanningAgentRequests(
                @NotNull AgentModels.PlanningAgentRequests input,
                ActionContext context
        ) {
            AgentModels.PlanningOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context,
                        "Planning dispatch request not found - cannot dispatch planning agents.",
                        METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                        AgentModels.PlanningAgentRequests.class,
                        1
                );
            }
            return agentExecutor.runPlanningDispatch(input, context, lastRequest);
        }

        @Action(canRerun = true)
        public AgentModels.OrchestratorCollectorResult finalizeTicketOrchestrator(
                AgentModels.TicketOrchestratorResult input,
                OperationContext context
        ) {
            AgentModels.TicketOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Ticket orchestrator request not found - cannot finalize ticket orchestrator.",
                        METHOD_FINALIZE_TICKET_ORCHESTRATOR,
                        AgentModels.TicketOrchestratorResult.class,
                        1
                );
            }
            blackboardHistoryService.register(context, METHOD_FINALIZE_TICKET_ORCHESTRATOR, input);
            AgentModels.OrchestratorCollectorResult result = new AgentModels.OrchestratorCollectorResult(
                    input.output());

            return DecorateRequestResults.decorateFinalResult(
                    result,
                    lastRequest,
                    lastRequest,
                    context,
                    finalResultDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR_COLLECTOR,
                    METHOD_FINALIZE_TICKET_ORCHESTRATOR
            );
        }

        @Action(canRerun = true)
        public AgentModels.TicketOrchestratorRouting orchestrateTicketExecution(
                AgentModels.TicketOrchestratorRequest input,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Ticket orchestrator request not found - cannot orchestrate ticket execution.",
                        METHOD_ORCHESTRATE_TICKET_EXECUTION,
                        AgentModels.TicketOrchestratorRequest.class,
                        1
                );
            }
            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.TicketOrchestratorRequest, AgentModels.TicketOrchestratorRouting, AgentModels.TicketOrchestratorResult, AgentModels.TicketOrchestratorRouting>builder()
                            .responseClazz(AgentModels.TicketOrchestratorRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.ORCHESTRATE_TICKET_EXECUTION)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> Map.of("goal", a.enrichedRequest().goal()))
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }

        @Action(canRerun = true)
        public AgentModels.TicketAgentDispatchRouting dispatchTicketAgentRequests(
                @NotNull AgentModels.TicketAgentRequests input,
                ActionContext context
        ) {
            AgentModels.TicketOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context,
                        "Ticket dispatch request not found - cannot dispatch ticket agents.",
                        METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
                        AgentModels.TicketAgentRequests.class,
                        1
                );
            }
            return agentExecutor.runTicketDispatch(input, context, lastRequest);
        }

        @Action(canRerun = true)
        public AgentModels.TicketCollectorRouting handleTicketCollectorBranch(
                AgentModels.TicketCollectorResult request,
                OperationContext context
        ) {
            AgentModels.TicketOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Ticket collector request not found - cannot handle ticket collector branch.",
                        METHOD_HANDLE_TICKET_COLLECTOR_BRANCH,
                        AgentModels.TicketCollectorResult.class,
                        1
                );
            }
            blackboardHistoryService.register(context, METHOD_HANDLE_TICKET_COLLECTOR_BRANCH, request);

            // Get upstream curations from context for routing back
            AgentModels.TicketOrchestratorRequest lastTicketOrchestratorRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            UpstreamContext.DiscoveryCollectorContext discoveryCuration = lastTicketOrchestratorRequest != null
                    ? lastTicketOrchestratorRequest.discoveryCuration()
                    : null;
            UpstreamContext.PlanningCollectorContext planningCuration = lastTicketOrchestratorRequest != null
                    ? lastTicketOrchestratorRequest.planningCuration()
                    : null;

            // Collectors always advance forward — route-back handled by conversational topology
            AgentModels.OrchestratorCollectorRequest orchestratorCollectorRequest = AgentModels.OrchestratorCollectorRequest.builder()
                    .goal(request.consolidatedOutput())
                    .discoveryCuration(discoveryCuration)
                    .planningCuration(planningCuration)
                    .ticketCuration(request.ticketCuration())
                    .build();

            AgentModels.TicketCollectorRouting routing = AgentModels.TicketCollectorRouting.builder()
                    .orchestratorCollectorRequest(orchestratorCollectorRequest)
                    .build();

            return decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    AGENT_NAME_TICKET_ROUTING,
                    ACTION_TICKET_COLLECTOR_ROUTING_BRANCH,
                    METHOD_HANDLE_TICKET_COLLECTOR_BRANCH,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.DiscoveryCollectorRouting handleDiscoveryCollectorBranch(
                AgentModels.DiscoveryCollectorResult request,
                OperationContext context
        ) {
            AgentModels.DiscoveryOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Discovery collector request not found - cannot handle discovery collector branch.",
                        METHOD_HANDLE_DISCOVERY_COLLECTOR_BRANCH,
                        AgentModels.DiscoveryCollectorResult.class,
                        1
                );
            }
            blackboardHistoryService.register(context, METHOD_HANDLE_DISCOVERY_COLLECTOR_BRANCH, request);
            // Collectors always advance forward — route-back handled by conversational topology
            AgentModels.PlanningOrchestratorRequest planningRequest = AgentModels.PlanningOrchestratorRequest.builder()
                    .goal(request.consolidatedOutput())
                    .discoveryCuration(request.discoveryCollectorContext())
                    .build();
            AgentModels.DiscoveryCollectorRouting routing = AgentModels.DiscoveryCollectorRouting.builder()
                    .planningRequest(planningRequest)
                    .build();
            return decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    AGENT_NAME_NONE,
                    ACTION_NONE,
                    METHOD_NONE,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.OrchestratorCollectorRouting handleOrchestratorCollectorBranch(
                AgentModels.OrchestratorCollectorResult request,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Orchestrator collector request not found - cannot handle orchestrator collector branch.",
                        METHOD_HANDLE_ORCHESTRATOR_COLLECTOR_BRANCH,
                        AgentModels.OrchestratorCollectorResult.class,
                        1
                );
            }
            blackboardHistoryService.register(context, METHOD_HANDLE_ORCHESTRATOR_COLLECTOR_BRANCH, request);
            // Collectors always advance forward — route-back handled by conversational topology
            AgentModels.OrchestratorCollectorResult collectorResult
                    = new AgentModels.OrchestratorCollectorResult(request.consolidatedOutput());
            AgentModels.OrchestratorCollectorRouting routing = AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(collectorResult)
                    .build();
            return decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    AGENT_NAME_NONE,
                    ACTION_NONE,
                    METHOD_NONE,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.PlanningCollectorRouting handlePlanningCollectorBranch(
                AgentModels.PlanningCollectorResult request,
                OperationContext context
        ) {
            AgentModels.PlanningOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Planning collector request not found - cannot handle planning collector branch.",
                        METHOD_HANDLE_PLANNING_COLLECTOR_BRANCH,
                        AgentModels.PlanningCollectorResult.class,
                        1
                );
            }
            blackboardHistoryService.register(context, METHOD_HANDLE_PLANNING_COLLECTOR_BRANCH, request);
            // Get discovery curation from prior planning orchestrator request
            AgentModels.PlanningOrchestratorRequest lastPlanningOrchestratorRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
            UpstreamContext.DiscoveryCollectorContext discoveryCuration = lastPlanningOrchestratorRequest != null
                    ? lastPlanningOrchestratorRequest.discoveryCuration()
                    : null;

            // Collectors always advance forward — route-back handled by conversational topology
            AgentModels.TicketOrchestratorRequest ticketRequest = AgentModels.TicketOrchestratorRequest.builder()
                    .goal(request.consolidatedOutput())
                    .discoveryCuration(discoveryCuration)
                    .planningCuration(request.planningCuration())
                    .build();

            AgentModels.PlanningCollectorRouting routing = AgentModels.PlanningCollectorRouting.builder()
                    .ticketOrchestratorRequest(ticketRequest)
                    .build();
            return decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    AGENT_NAME_NONE,
                    ACTION_NONE,
                    METHOD_NONE,
                    lastRequest
            );
        }

        private static String resolveRootGoal(ActionContext context) {
            AgentModels.OrchestratorRequest rootRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            return rootRequest != null ? rootRequest.goal() : "Continue workflow";
        }

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return "";
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return "";
        }


    }

    @Agent(
            name = WORKFLOW_TICKET_DISPATCH_SUBAGENT,
            description = "Runs ticket agent request in a subprocess"
    )
    @RequiredArgsConstructor
    class TicketDispatchSubagent implements AgentInterfaces {
        private final EventBus eventBus;
        private final AgentExecutor agentExecutor;


        @Override
        public String multiAgentAgentName() {
            return WORKFLOW_TICKET_DISPATCH_SUBAGENT;
        }


        @Action
        @AchievesGoal(description = "Handle context agent request")
        public AgentModels.TicketAgentRouting runTicketAgent(
                AgentModels.TicketAgentRequest input,
                OperationContext context
        ) {
            AgentModels.TicketAgentRequests lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketAgentRequests.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Ticket agent request not found - cannot run ticket agent.",
                        METHOD_RUN_TICKET_AGENT,
                        AgentModels.TicketAgentRequest.class,
                        1
                );
            }

            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.TicketAgentRequest, AgentModels.TicketAgentRouting, AgentModels.TicketAgentResult, AgentModels.TicketAgentRouting>builder()
                            .responseClazz(AgentModels.TicketAgentRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.RUN_TICKET_AGENT)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> Map.of(
                                    "ticketDetails", a.enrichedRequest().ticketDetails() != null ? a.enrichedRequest().ticketDetails() : "",
                                    "ticketDetailsFilePath", a.enrichedRequest().ticketDetailsFilePath() != null ? a.enrichedRequest().ticketDetailsFilePath() : ""))
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }
    }

    @Agent(
            name = WORKFLOW_PLANNING_DISPATCH_SUBAGENT,
            description = "Runs planning agent request in a subprocess"
    )
    @RequiredArgsConstructor
    class PlanningDispatchSubagent implements AgentInterfaces {

        private final EventBus eventBus;
        private final AgentExecutor agentExecutor;

        @Override
        public String multiAgentAgentName() {
            return WORKFLOW_PLANNING_DISPATCH_SUBAGENT;
        }

        @Action(canRerun = true)
        @AchievesGoal(description = "Handle context agent request")
        public AgentModels.PlanningAgentRouting runPlanningAgent(
                AgentModels.PlanningAgentRequest input,
                OperationContext context
        ) {
            AgentModels.PlanningAgentRequests lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningAgentRequests.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context, 
                        "Planning agent request not found - cannot run planning agent.",
                        METHOD_RUN_PLANNING_AGENT,
                        AgentModels.PlanningAgentRequest.class,
                        1
                );
            }
            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.PlanningAgentRequest, AgentModels.PlanningAgentRouting, AgentModels.PlanningAgentResult, AgentModels.PlanningAgentRouting>builder()
                            .responseClazz(AgentModels.PlanningAgentRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.RUN_PLANNING_AGENT)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> Map.of("goal", a.enrichedRequest().goal()))
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }
    }

    @Agent(
            name = WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT,
            description = "Runs discovery agent request in a subprocess"
    )
    @RequiredArgsConstructor
    class DiscoveryDispatchSubagent implements AgentInterfaces {

        private final EventBus eventBus;
        private final AgentExecutor agentExecutor;

        @Override
        public String multiAgentAgentName() {
            return WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT;
        }


        @Action
        @AchievesGoal(description = "Handle context agent request")
        public AgentModels.DiscoveryAgentRouting runDiscoveryAgent(
                AgentModels.DiscoveryAgentRequest input,
                OperationContext context
        ) {
            AgentModels.DiscoveryAgentRequests lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryAgentRequests.class);
            if (lastRequest == null) {
                throw AgentInterfaces.degenerateLoop(eventBus, context,
                        "Discovery agent request not found - cannot run discovery agent.",
                        METHOD_RUN_DISCOVERY_AGENT,
                        AgentModels.DiscoveryAgentRequest.class,
                        1
                );
            }

            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscoveryDispatchSubagent.class);
            log.info("runDiscoveryAgent: input contextId={} | lastRequest(container) contextId={} | subdomain={}",
                    input.contextId() != null ? input.contextId().value() : "null",
                    lastRequest.contextId() != null ? lastRequest.contextId().value() : "null",
                    input.subdomainFocus());

            return agentExecutor.run(
                    AgentExecutor.AgentExecutorArgs.<AgentModels.DiscoveryAgentRequest, AgentModels.DiscoveryAgentRouting, AgentModels.DiscoveryAgentResult, AgentModels.DiscoveryAgentRouting>builder()
                            .responseClazz(AgentModels.DiscoveryAgentRouting.class)
                            .agentActionMetadata(MultiAgentIdeMetadata.AgentMetadata.RUN_DISCOVERY_AGENT)
                            .previousRequest(lastRequest)
                            .currentRequest(input)
                            .templateModelProducer(a -> Map.of(
                                    "goal", a.enrichedRequest().goal(),
                                    "subdomainFocus", a.enrichedRequest().subdomainFocus()))
                            .operationContext(context)
                            .baseToolContext(ToolContext.empty())
                            .build());
        }

    }

}
