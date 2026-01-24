# Quickstart: Using the Context Manager

## Overview
The Context Manager helps recover from stuck workflows and manages context across complex agent transitions.

## 1. Handling Stuck Workflows
The `WorkflowAgent` class (which implements `com.embabel.agent.api.common.StuckHandler`) uses the `llmRunner` to invoke the Context Manager when a stuck state is detected.

```java
// Inside WorkflowAgent.java

@Override
public AgentModels.ContextManagerResultRouting handleStuck(AgentProcess agentProcess) {
    // 1. Detect loop details from history
    var history = BlackboardHistory.getBlackboardHistory(context);
    // ... analysis logic ...

    // 2. Invoke Context Manager via LLM Template
    // This generates the ContextManagerRequest with the appropriate routing
    AgentModels.ContextManagerResultRouting routing = llmRunner.runWithTemplate(
        "workflow/context_manager_recovery",
        promptContext,
        Map.of(
            "reason", "Degenerate loop detected: " + loopDetails
        ),
        AgentModels.ContextManagerResultRouting.class,
        context
    );

    return routing;
}
```

## 2. Requesting Context Explicitly
Agents request context reconstruction naturally through their prompt templates and routing models. There is no manual `if (missing)` check; instead, the agent's LLM prompt includes instructions on when to seek context, and the model selects the `ContextManagerRequest` option in its routing response if it deems necessary.

```java
// Inside an agent action (e.g., PlanningDispatchSubagent)
// The LLM decides whether to proceed or request context based on the "planning/agent_dispatch" template.
AgentModels.PlanningAgentRouting routing = llmRunner.runWithTemplate(
    "planning/agent_dispatch",
    promptContext,
    Map.of(
        "goal", input.goal(),
        // ... inputs ...
    ),
    AgentModels.PlanningAgentRouting.class,
    context
);

// If the model chose to route to Context Manager, 'routing' will contain that request, the agent sets that field, where PlanningAgentRouting is a SomeOf with ContextAgentRequest.
return routing;
```

## 3. Using Blackboard Tools
The Context Manager uses `BlackboardTools` to inspect history.

```java
var history = BlackboardHistory.getBlackboardHistory(context);
var tools = new BlackboardTools(history);

// Trace history
var trace = tools.trace("kickOffDiscoveryAgents");

// Search
var results = tools.search("error");

```
