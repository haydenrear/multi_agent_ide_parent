package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentPlatform;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.commitdiffcontext.cdc_utils.SetFromHeader;
import com.hayden.commitdiffcontext.mcp.ToolCarrier;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;

import com.hayden.multiagentide.service.AgentCommunicationService;
import com.hayden.multiagentide.service.SessionKeyResolutionService;

import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentide.llm.AgentLlmExecutor;
import com.hayden.multiagentide.llm.AgentLlmExecutor.DirectExecutorArgs;
import com.hayden.multiagentide.model.nodes.AgentToAgentConversationNode;
import com.hayden.multiagentide.model.nodes.GraphNode;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContextFactory;
import com.hayden.multiagentide.prompt.contributor.NodeMappings;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.hayden.multiagentide.topology.CommunicationTopologyProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hayden.acp_cdc_ai.acp.AcpChatModel.MCP_SESSION_HEADER;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentTopologyTools implements ToolCarrier {

    private final AgentCommunicationService agentCommunicationService;
    private final SessionKeyResolutionService sessionKeyResolutionService;
    private final CommunicationTopologyProvider topologyProvider;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, AtomicInteger> conversationMessageCounts = new ConcurrentHashMap<>();

    private AgentPlatform agentPlatform;
    private DecorateRequestResults decorateRequestResults;
    private AgentLlmExecutor agentLlmExecutor;
    private PromptContextFactory promptContextFactory;
    private EventBus eventBus;
    private com.hayden.multiagentide.service.AgentExecutor agentExecutor;

    @Autowired
    public void setAgentPlatform(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @Autowired
    public void setDecorateRequestResults(DecorateRequestResults decorateRequestResults) {
        this.decorateRequestResults = decorateRequestResults;
    }

    @Autowired
    public void setAgentLlmExecutor(AgentLlmExecutor agentLlmExecutor) {
        this.agentLlmExecutor = agentLlmExecutor;
    }

    @Autowired
    public void setPromptContextFactory(PromptContextFactory promptContextFactory) {
        this.promptContextFactory = promptContextFactory;
    }

    @Autowired
    public void setEventBus(@org.springframework.context.annotation.Lazy EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Autowired
    public void setAgentExecutor(com.hayden.multiagentide.service.AgentExecutor agentExecutor) {
        this.agentExecutor = agentExecutor;
    }

    @SneakyThrows
    @Tool(name = "list_agents", description = "Lists all available agent sessions. Returns two sections: "
            + "(1) agents you CAN call directly, and (2) agents that exist but you CANNOT call due to topology rules, "
            + "along with which agents can bridge you to them. Use this to discover routing paths before calling agents. "
            + "IMPORTANT: Never use list_agents or call_agent as a substitute for returning your structured result. "
            + "Your primary output mechanism is always your structured JSON response. Even if a previous attempt to return "
            + "your structured result failed, you must try again — do NOT use call_agent/list_agents to work around "
            + "the failure by manually delegating or forwarding. The framework handles dispatch from your structured result.")
    @McpTool(name = "list_agents", description = "Lists all available agent sessions. Returns two sections: "
            + "(1) agents you CAN call directly, and (2) agents that exist but you CANNOT call due to topology rules, "
            + "along with which agents can bridge you to them. Use this to discover routing paths before calling agents. "
            + "IMPORTANT: Never use list_agents or call_agent as a substitute for returning your structured result. "
            + "Your primary output mechanism is always your structured JSON response. Even if a previous attempt to return "
            + "your structured result failed, you must try again — do NOT use call_agent/list_agents to work around "
            + "the failure by manually delegating or forwarding. The framework handles dispatch from your structured result.")
    public String list_agents(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId
    ) {
        if (!StringUtils.hasText(sessionId)) {
            return "{\"error\": \"missing session id\"}";
        }
        GraphNode callingNode = sessionKeyResolutionService.resolveNodeBySessionKey(sessionId);
        AgentType callingAgentType = callingNode != null ? NodeMappings.agentTypeFromNode(callingNode) : null;
        var agents = agentCommunicationService.listAvailableAgents(sessionId, callingAgentType);

        List<AgentCommunicationService.AgentAvailabilityEntry> callable = new ArrayList<>();
        List<AgentCommunicationService.AgentAvailabilityEntry> prohibited = new ArrayList<>();
        for (var agent : agents) {
            if (agent.callableByCurrentAgent()) {
                callable.add(agent);
            } else {
                prohibited.add(agent);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Available agents you CAN call directly\n");
        if (callable.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (var a : callable) {
                sb.append("- ").append(a.agentKey()).append(" (").append(a.agentType()).append(")\n");
            }
        }

        sb.append("\n## Available agents you CANNOT call (topology-prohibited)\n");
        if (prohibited.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (var a : prohibited) {
                sb.append("- ").append(a.agentKey()).append(" (").append(a.agentType()).append(")");
                if (!a.reachableVia().isEmpty()) {
                    sb.append(" — reachable via: ").append(String.join(", ", a.reachableVia()));
                }
                sb.append("\n");
            }
            sb.append("\nNOTE: If you need to reach a prohibited agent, contact one of the agents listed in 'reachable via' ");
            sb.append("and ask them to relay your message. Prefer an agent in your sub-graph (discovery, planning, ticket) ");
            sb.append("or at the same level of abstraction (orchestrator, collector, dispatcher, dispatched).");
        }

        return sb.toString();
    }

    @Tool(name = "call_agent", description = "Sends a message to another agent and returns their response. "
            + "Use list_agents first to discover available agents. The target agent must be available and "
            + "communication must be permitted by topology rules. Call chain tracking and loop detection "
            + "are handled automatically. "
            + "IMPORTANT: Never use call_agent as a substitute for returning your structured result. "
            + "Your primary output mechanism is always your structured JSON response. Even if a previous attempt "
            + "to return your structured result failed, you must try again — do NOT use call_agent to work around "
            + "the failure by manually delegating or forwarding work to the next agent. The framework handles "
            + "dispatch from your structured result. call_agent is for peer-to-peer communication only.")
    @McpTool(name = "call_agent", description = "Sends a message to another agent and returns their response. "
            + "Use list_agents first to discover available agents. The target agent must be available and "
            + "communication must be permitted by topology rules. Call chain tracking and loop detection "
            + "are handled automatically. "
            + "IMPORTANT: Never use call_agent as a substitute for returning your structured result. "
            + "Your primary output mechanism is always your structured JSON response. Even if a previous attempt "
            + "to return your structured result failed, you must try again — do NOT use call_agent to work around "
            + "the failure by manually delegating or forwarding work to the next agent. The framework handles "
            + "dispatch from your structured result. call_agent is for peer-to-peer communication only.")
    public String call_agent(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            @ToolParam(description = "The artifact key of the target agent to call (e.g. ak:01KJ...)")
            @McpToolParam(description = "The artifact key of the target agent to call (e.g. ak:01KJ...)")
            String targetAgentKey,
            @ToolParam(description = "The message to send to the target agent")
            @McpToolParam(description = "The message to send to the target agent")
            String message
    ) {
        if (!StringUtils.hasText(sessionId)) {
            String errorMsg = "ERROR: Missing session id.";
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, null,
                    targetAgentKey, null, List.of(), message, null, errorMsg, null);
            return errorMsg;
        }
        if (!StringUtils.hasText(targetAgentKey)) {
            String errorMsg = "ERROR: Missing target agent key.";
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, null,
                    targetAgentKey, null, List.of(), message, null, errorMsg, null);
            return errorMsg;
        }
        if (!StringUtils.hasText(message)) {
            String errorMsg = "ERROR: Message cannot be empty.";
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, null,
                    targetAgentKey, null, List.of(), null, null, errorMsg, null);
            return errorMsg;
        }

        ArtifactKey callingKey;
        ArtifactKey targetKey;
        try {
            callingKey = new ArtifactKey(sessionId);
        } catch (IllegalArgumentException e) {
            String errorMsg = "ERROR: Invalid session ID - not your fault - propagate up the chain from agent topology tools callAgent function.";
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, null,
                    targetAgentKey, null, List.of(), message, null, errorMsg, null);
            return errorMsg;
        }
        try {
            targetKey = new ArtifactKey(targetAgentKey);
        } catch (IllegalArgumentException e) {
            String errorMsg = "ERROR: Invalid target agent key format.";
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, null,
                    targetAgentKey, null, List.of(), message, null, errorMsg, null);
            return errorMsg;
        }

        // Resolve the calling agent's graph node (by nodeId or chatSessionKey)
        GraphNode callingNode = sessionKeyResolutionService.resolveNodeBySessionKey(sessionId);
        AgentType callingType = callingNode != null ? NodeMappings.agentTypeFromNode(callingNode) : null;

        // Resolve the target agent's graph node (by nodeId or chatSessionKey)
        GraphNode targetNode = sessionKeyResolutionService.resolveNodeBySessionKey(targetAgentKey);
        AgentType targetType = targetNode != null ? NodeMappings.agentTypeFromNode(targetNode) : null;
        String targetNodeId = targetNode != null ? targetNode.nodeId() : null;

        // Derive call chain from the workflow graph
        List<AgentModels.CallChainEntry> callChain = sessionKeyResolutionService.buildCallChainFromGraph(sessionId);

        // Validate the call
        AgentCommunicationService.CallValidationResult validation =
                agentCommunicationService.validateCall(callingKey, callingType, targetKey, targetType, callChain);

        if (!validation.valid()) {
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, callingType, targetAgentKey, targetType,
                    callChain, message, null, validation.error(), null);
            return validation.error();
        }

        // Find the originating AgentToAgentConversationNode (first in chain)
        // and the immediate parent (the node that called the current agent)
        String callingNodeId = callingNode != null ? callingNode.nodeId() : null;
        AgentToAgentConversationNode incomingCallNode = sessionKeyResolutionService.findIncomingCallNode(sessionId);
        String originatingAgentToAgentNodeId = null;
        if (incomingCallNode != null) {
            // We're in a chain — use the originating node from the incoming call,
            // or the incoming call itself if it's the first hop
            originatingAgentToAgentNodeId = incomingCallNode.originatingAgentToAgentNodeId() != null
                    ? incomingCallNode.originatingAgentToAgentNodeId()
                    : incomingCallNode.nodeId();
        }

        // Resolve OperationContext from the calling agent's active process
        OperationContext operationContext = resolveOperationContext(callingKey);
        if (operationContext == null) {
            return "ERROR: Could not resolve operation context for calling agent. Agent process not found.";
        }

        try {
            AgentModels.AgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(operationContext, AgentModels.AgentRequest.class);

            // Build the AgentToAgentRequest with all three tracking fields
            ArtifactKey contextId = callingKey.createChild();
            AgentModels.AgentToAgentRequest request = AgentModels.AgentToAgentRequest.builder()
                    .contextId(contextId)
                    .sourceAgentKey(callingKey)
                    .sourceAgentType(callingType)
                    .targetAgentKey(targetKey)
                    .targetAgentType(targetType)
                    .message(message)
                    .callChain(callChain)
                    .callingNodeId(callingNodeId)
                    .originatingAgentToAgentNodeId(originatingAgentToAgentNodeId)
                    .targetNodeId(targetNodeId)
                    .sourceSessionId(sessionId)
                    .chatId(targetNodeId != null && !targetNodeId.isBlank()
                            ? new ArtifactKey(targetNodeId)
                            : targetKey)
                    .build();

            // 1. Decorate request (StartWorkflowRequestDecorator creates the node here)
            AgentModels.AgentToAgentRequest enrichedRequest = decorateRequestResults.decorateRequest(
                    new DecorateRequestResults.DecorateRequestArgs<>(
                            request, operationContext,
                            AgentInterfaces.AGENT_NAME_AGENT_CALL,
                            AgentInterfaces.ACTION_AGENT_CALL,
                            AgentInterfaces.METHOD_CALL_AGENT,
                            lastRequest
                    ));

            // 2. Build and decorate prompt context
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(operationContext);
            DecoratorContext decoratorContext = new DecoratorContext(
                    operationContext,
                    AgentInterfaces.AGENT_NAME_AGENT_CALL,
                    AgentInterfaces.ACTION_AGENT_CALL,
                    AgentInterfaces.METHOD_CALL_AGENT,
                    lastRequest,
                    enrichedRequest
            );

            Map<String, Object> templateModel = new java.util.HashMap<>(Map.of(
                    "sourceAgentKey", callingKey.value(),
                    "message", message
            ));
            if (callingType != null) {
                templateModel.put("sourceAgentType", callingType.wireValue());
            }
            if (!CollectionUtils.isEmpty(callChain)) {
                templateModel.put("callChain", callChain);
            }

            PromptContext promptContext = promptContextFactory.build(
                    AgentType.AGENT_CALL,
                    enrichedRequest,
                    lastRequest,
                    enrichedRequest,
                    history,
                    AgentInterfaces.TEMPLATE_COMMUNICATION_AGENT_CALL,
                    templateModel,
                    operationContext,
                    decoratorContext
            );
            promptContext = decorateRequestResults.decoratePromptContext(
                    new DecorateRequestResults.DecoratePromptContextArgs(promptContext, decoratorContext)
            );

            // 3. Build and decorate tool context
            ToolContext toolContext = decorateRequestResults.decorateToolContext(
                    new DecorateRequestResults.DecorateToolArgs(
                            ToolContext.empty(),
                            enrichedRequest,
                            lastRequest,
                            operationContext,
                            AgentInterfaces.AGENT_NAME_AGENT_CALL,
                            AgentInterfaces.ACTION_AGENT_CALL,
                            AgentInterfaces.METHOD_CALL_AGENT
                    )
            );

            // Emit INITIATED event before LLM call
            emitCallEvent(Events.AgentCallEventType.INITIATED, sessionId, callingType, targetAgentKey, targetType,
                    callChain, message, null, null, null);

            // 4. Call LLM through the target agent's session (routed by PromptContext.chatId())
            AgentModels.AgentCallRouting routing = agentLlmExecutor.runDirect(
                    DirectExecutorArgs.<AgentModels.AgentCallRouting>builder()
                            .responseClazz(AgentModels.AgentCallRouting.class)
                            .agentName(AgentInterfaces.AGENT_NAME_AGENT_CALL)
                            .actionName(AgentInterfaces.ACTION_AGENT_CALL)
                            .methodName(AgentInterfaces.METHOD_CALL_AGENT)
                            .template(AgentInterfaces.TEMPLATE_COMMUNICATION_AGENT_CALL)
                            .promptContext(promptContext)
                            .templateModel(templateModel)
                            .toolContext(toolContext)
                            .operationContext(operationContext)
                            .build());

            // 5. Decorate routing result (WorkflowGraphResultDecorator completes the node here)
            routing = decorateRequestResults.decorateRouting(
                    new DecorateRequestResults.DecorateRoutingArgs<>(
                            routing, operationContext,
                            AgentInterfaces.AGENT_NAME_AGENT_CALL,
                            AgentInterfaces.ACTION_AGENT_CALL,
                            AgentInterfaces.METHOD_CALL_AGENT,
                            lastRequest
                    )
            );

            String responseText = routing != null ? routing.response() : null;
            if (responseText == null || responseText.isBlank()) {
                responseText = "Agent responded but returned no content.";
            }

            // Successful agent call — reset controller message budget counter
            // (agent made forward progress, so consecutive controller calls start fresh)
            var budgetCounter = conversationMessageCounts.get(sessionId);
            if (budgetCounter != null) {
                budgetCounter.set(0);
            }

            // Emit RETURNED event on success
            emitCallEvent(Events.AgentCallEventType.RETURNED, sessionId, callingType, targetAgentKey, targetType,
                    callChain, null, responseText, null, null);

            return responseText;
        } catch (Exception e) {
            log.error("Failed to call agent {}: {}", targetAgentKey, e.getMessage(), e);
            String errorMsg = "ERROR: Agent %s is down. Try an alternative route. Detail: %s"
                    .formatted(targetAgentKey, e.getMessage());
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, callingType, targetAgentKey, targetType,
                    callChain, null, null, errorMsg, null);
            return errorMsg;
        }
    }

    @Tool(name = "call_controller", description = "Sends a structured justification message to the controller "
            + "(human operator) and blocks until they respond. Use this when you need approval, clarification, or "
            + "feedback from the controller before proceeding. The controller will see your justification and can "
            + "approve, reject, or provide guidance.")
    @McpTool(name = "call_controller", description = "Sends a structured justification message to the controller "
            + "(human operator) and blocks until they respond. Use this when you need approval, clarification, or "
            + "feedback from the controller before proceeding. The controller will see your justification and can "
            + "approve, reject, or provide guidance.")
    public String call_controller(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            @ToolParam(description = "The justification message explaining why you need controller approval or feedback")
            @McpToolParam(description = "The justification message explaining why you need controller approval or feedback")
            String justificationMessage
    ) {
        log.info("call_controller START — sessionId={} justification={}",
                sessionId, justificationMessage != null ? justificationMessage.substring(0, Math.min(200, justificationMessage.length())) : "null");
        if (!StringUtils.hasText(sessionId)) {
            String errorMsg = "ERROR: Missing session id.";
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, null,
                    "controller", null, List.of(), justificationMessage, null, errorMsg, null);
            return errorMsg;
        }
        if (!StringUtils.hasText(justificationMessage)) {
            String errorMsg = "ERROR: Justification message cannot be empty.";
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, null,
                    "controller", null, List.of(), null, null, errorMsg, null);
            return errorMsg;
        }

        ArtifactKey callingKey;
        try {
            callingKey = new ArtifactKey(sessionId);
        } catch (IllegalArgumentException e) {
            String errorMsg = "ERROR: Invalid session ID.";
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, null,
                    "controller", null, List.of(), justificationMessage, null, errorMsg, null);
            return errorMsg;
        }

        GraphNode callingNode = sessionKeyResolutionService.resolveNodeBySessionKey(sessionId);
        AgentType callingType = callingNode != null ? NodeMappings.agentTypeFromNode(callingNode) : null;

        // Message budget check (FR-017) — disabled, budget should not limit controller calls
        // int budget = topologyProvider.messageBudget();
        // if (budget > 0) {
        //     int count = conversationMessageCounts
        //             .computeIfAbsent(sessionId, k -> new AtomicInteger(0))
        //             .incrementAndGet();
        //     if (count > budget) {
        //         log.warn("Message budget exceeded for session {}: {} > {}", sessionId, count, budget);
        //         return "ERROR: Message budget exceeded (%d/%d). Escalating to user — please wait for human input."
        //                 .formatted(count, budget);
        //     }
        // }

        OperationContext operationContext = resolveOperationContext(callingKey);
        if (operationContext == null) {
            String errorMsg = "ERROR: Could not resolve operation context for calling agent.";
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, callingType,
                    "controller", null, List.of(), justificationMessage, null, errorMsg, null);
            return errorMsg;
        }

        try {
            AgentModels.AgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(operationContext, AgentModels.AgentRequest.class);

            ArtifactKey contextId = callingKey.createChild();
            AgentModels.AgentToControllerRequest request = new AgentModels.AgentToControllerRequest(
                    contextId, null, callingKey, callingType,
                    justificationMessage, null
            );

            Map<String, Object> templateModel = new java.util.HashMap<>(Map.of(
                    "sourceAgentKey", callingKey.value(),
                    "justificationMessage", justificationMessage
            ));
            if (callingType != null) {
                templateModel.put("sourceAgentType", callingType.wireValue());
            }

            // Emit INITIATED event
            emitCallEvent(Events.AgentCallEventType.INITIATED, sessionId, callingType,
                    "controller", null, List.of(), justificationMessage, null, null, null);

            // Delegate to AgentExecutor: decorates request, builds prompt context,
            // renders template + prompt contributors, publishes interrupt, blocks until resolved.
            String interruptId = contextId.value();
            String responseText = agentExecutor.controllerExecution(
                    com.hayden.multiagentide.service.AgentExecutor.ControllerExecutionArgs.builder()
                            .agentActionMetadata(AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.CALL_CONTROLLER)
                            .previousRequest(lastRequest)
                            .currentRequest(request)
                            .templateModel(templateModel)
                            .operationContext(operationContext)
                            .interruptId(interruptId)
                            .originNodeId(callingNode != null ? callingNode.nodeId() : interruptId)
                            .interruptType(Events.InterruptType.HUMAN_REVIEW)
                            .build()
            );

            // Emit RETURNED event
            emitCallEvent(Events.AgentCallEventType.RETURNED, sessionId, callingType,
                    "controller", null, List.of(), null, responseText, null, null);

            log.info("call_controller END — sessionId={} responseLength={}", sessionId,
                    responseText != null ? responseText.length() : 0);
            return responseText;
        } catch (Exception e) {
            log.error("call_controller ERROR — sessionId={} error={}", sessionId, e.getMessage(), e);
            String errorMsg = "ERROR: Failed to reach controller. Detail: %s".formatted(e.getMessage());
            emitCallEvent(Events.AgentCallEventType.ERROR, sessionId, callingType,
                    "controller", null, List.of(), null, null, errorMsg, null);
            return errorMsg;
        }
    }

    private OperationContext resolveOperationContext(ArtifactKey key) {
        return com.hayden.multiagentide.embabel.EmbabelUtil.resolveOperationContext(
                agentPlatform, key, AgentInterfaces.AGENT_NAME_AGENT_CALL);
    }

    private void emitCallEvent(
            Events.AgentCallEventType callEventType,
            String callerSessionId, AgentType callerType,
            String targetSessionId, AgentType targetType,
            List<AgentModels.CallChainEntry> callChain,
            String message, String response, String errorDetail, String checklistAction
    ) {
        if (eventBus == null) return;
        try {
            List<String> chainKeys = callChain != null
                    ? callChain.stream()
                        .map(e -> e.targetAgentKey() != null
                                ? e.agentKey().value() + "->" + e.targetAgentKey().value()
                                : e.agentKey().value())
                        .toList()
                    : List.of();
            eventBus.publish(new Events.AgentCallEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    callerSessionId,
                    callEventType,
                    callerSessionId,
                    callerType != null ? callerType.wireValue() : null,
                    targetSessionId,
                    targetType != null ? targetType.wireValue() : null,
                    chainKeys,
                    null, // availableAgents — populated lazily to avoid overhead
                    message, response, errorDetail, checklistAction
            ));
        } catch (Exception e) {
            log.warn("Failed to emit AgentCallEvent: {}", e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize to JSON", e);
            return "{\"error\": \"serialization failed\"}";
        }
    }
}
