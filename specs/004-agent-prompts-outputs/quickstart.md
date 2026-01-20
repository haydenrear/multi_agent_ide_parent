# Quickstart: Agent Prompts, Structured Outputs, and Tool Prompt Contributors

**Feature**: 004-agent-prompts-outputs
**Date**: 2026-01-19

## Overview

This guide explains how to:
1. Create and use Context IDs for traceability
2. Add typed upstream context to agent requests
3. Handle previous context for reruns
4. Create custom prompt contributors
5. Use standardized delegation and consolidation templates

## 1. Context IDs

### Creating a Context ID

```java
// In ContextIdService
@Service
public class ContextIdService {
    private final Map<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();
    
    public ContextId generate(String workflowRunId, AgentType agentType) {
        String key = workflowRunId + "/" + agentType.name();
        int sequence = sequenceCounters
            .computeIfAbsent(key, k -> new AtomicInteger(0))
            .incrementAndGet();
        
        return new ContextId(
            workflowRunId,
            agentType,
            sequence,
            Instant.now()
        );
    }
}
```

### Using Context IDs in Requests

```java
// Enhanced request with context ID
var request = new DiscoveryAgentRequest(
    contextIdService.generate(workflowRunId, AgentType.DISCOVERY_AGENT),
    goal,
    subdomainFocus,
    upstreamContext,   // typed upstream context
    previousContext    // nullable previous context
);
```

## 2. Typed Upstream Context

### Defining Upstream Context Types

```java
// Sealed interface for type safety
public sealed interface UpstreamContext permits
    DiscoveryAgentUpstreamContext,
    PlanningAgentUpstreamContext,
    TicketAgentUpstreamContext {
    
    ContextId contextId();
}

// Discovery agent receives orchestrator context
public record DiscoveryAgentUpstreamContext(
    ContextId contextId,
    String orchestratorGoal,
    String subdomainAssignment
) implements UpstreamContext {}

// Planning agent receives discovery collector context
public record PlanningAgentUpstreamContext(
    ContextId contextId,
    DiscoveryCollectorContext discoveryContext
) implements UpstreamContext {}

// Ticket agent receives discovery + planning context
public record TicketAgentUpstreamContext(
    ContextId contextId,
    DiscoveryCollectorContext discoveryContext,
    PlanningCollectorContext planningContext,
    PlanningTicket assignedTicket
) implements UpstreamContext {}
```

### Passing Context Through Workflow

```java
// In Discovery Orchestrator
var discoveryContext = new DiscoveryAgentUpstreamContext(
    contextId,
    orchestratorGoal,
    "service-layer"
);

var request = new DiscoveryAgentRequest(
    newContextId,
    goal,
    "service-layer",
    discoveryContext,
    null  // first run
);
```

## 3. Previous Context for Reruns

### Structure

```java
public sealed interface PreviousContext permits
    DiscoveryOrchestratorPreviousContext,
    PlanningOrchestratorPreviousContext,
    TicketOrchestratorPreviousContext,
    DiscoveryAgentPreviousContext,
    PlanningAgentPreviousContext,
    TicketAgentPreviousContext,
    DiscoveryCollectorPreviousContext,
    PlanningCollectorPreviousContext,
    TicketCollectorPreviousContext,
    ReviewPreviousContext,
    MergerPreviousContext {

    ContextId previousContextId();
    String serializedOutput();
    String errorMessage();
    String errorStackTrace();
    int attemptNumber();
    Instant previousAttemptAt();
}

public record DiscoveryOrchestratorPreviousContext(
    ContextId previousContextId,
    String serializedOutput,
    String errorMessage,
    String errorStackTrace,
    int attemptNumber,
    Instant previousAttemptAt,
    DiscoveryCollectorContext previousDiscoveryCuration,
    PlanningCollectorContext previousPlanningCuration,
    TicketCollectorContext previousTicketCuration
) implements PreviousContext {}
```

### Using in Reruns

When rerouting from collectors back to orchestrators, populate the discrete previous context fields (for example, previous discovery and planning curations).

```java
// Check if this is a rerun
if (isRetry(context, "discoveryAgent")) {
    var prevContext = getPreviousContext(context);
    // Include in request
    request = new DiscoveryAgentRequest(
        newContextId,
        goal,
        subdomain,
        upstreamContext,
        prevContext.withIncrementedAttempt()
    );
}
```

## 4. Prompt Contributors

### Creating a Custom Contributor

```java
@Component
public class MyToolPromptContributor implements PromptContributor {
    
    @Override
    public String name() {
        return "my-tool";
    }
    
    @Override
    public Set<AgentType> applicableAgents() {
        return Set.of(
            AgentType.DISCOVERY_AGENT,
            AgentType.PLANNING_AGENT,
            AgentType.TICKET_AGENT
        );
    }
    
    @Override
    public String contribute(PromptContext context) {
        return """
            ## My Tool
            
            You have access to My Tool with the following operations:
            - operation1: Does something useful
            - operation2: Does something else
            
            Use these when you encounter [specific situations].
            """;
    }
    
    @Override
    public int priority() {
        return 100;  // After core prompts (0-50), before closing (900+)
    }
}
```

### Episodic Memory Contributor Example

```java
@Component
public class EpisodicMemoryPromptContributor implements PromptContributor {
    
    @Override
    public String name() {
        return "episodic-memory";
    }
    
    @Override
    public Set<AgentType> applicableAgents() {
        return Set.of(AgentType.ALL);  // Applies to all agents
    }
    
    @Override
    public String contribute(PromptContext context) {
        return """
            ## Episodic Memory Tool
            
            You have access to episodic memory with three operations:
            
            ### RETAIN - Store New Episodes
            Use RETAIN when you encounter important patterns, decisions, or successful approaches.
            Include contextual metadata: timestamp, entities involved, action taken.
            
            ### REFLECT - Build Connections (CRITICAL)
            REFLECT is essential for building lasting knowledge. Simply retaining is not enough.
            Use REFLECT:
            - After completing significant actions - solidify what you learned
            - When you see connections to prior work - strengthen those links
            - Before moving to the next phase - consolidate understanding
            
            The reflect operation builds connections in the knowledge graph.
            Without reflection, memories remain isolated and less useful.
            
            ### RECALL - Retrieve Prior Knowledge
            Use RECALL at the start of new tasks to find similar prior analyses,
            retrieve relevant patterns, and understand historical context.
            
            **Recommended Flow**: RECALL → Work → RETAIN key learnings → REFLECT on connections
            """;
    }
    
    @Override
    public int priority() {
        return 50;  // Early, as it's important guidance
    }
}
```

### Registering Contributors

```java
@Configuration
public class PromptContributorConfig {
    
    @Bean
    public PromptContributorRegistry promptContributorRegistry(
            List<PromptContributor> contributors) {
        return new PromptContributorRegistry(contributors);
    }
    
    @Bean
    public PromptAssembly promptAssembly(
            PromptContributorRegistry registry) {
        return new PromptAssembly(registry);
    }
}
```

## 5. Using Prompt Assembly

```java
@Service
public class AgentPromptService {
    private final PromptAssembly promptAssembly;
    
    public String assemblePrompt(
        String basePrompt,
        AgentType agentType,
        ContextId contextId,
        UpstreamContext upstreamContext,
        PreviousContext previousContext,
        BlackboardHistory blackboardHistory
    ) {
        var promptContext = new PromptContext(
            agentType,
            contextId,
            upstreamContext,
            previousContext,
            blackboardHistory,
            Map.of()
        );
        
        return promptAssembly.assemble(basePrompt, promptContext);
    }
}
```

## 6. Delegation Template (Orchestrators)

### Creating Delegation Output

```java
// In an orchestrator action
var delegation = new DelegationTemplate(
    "1.0.0",
    resultId,
    upstreamContextId,
    goal,
    "Dividing discovery into 3 subdomains based on package structure",
    List.of(
        new AgentAssignment(
            "discovery-agent-1",
            AgentType.DISCOVERY_AGENT,
            "Analyze data layer",
            "com.example.data",
            Map.of("focus", "repositories and entities")
        ),
        new AgentAssignment(
            "discovery-agent-2",
            AgentType.DISCOVERY_AGENT,
            "Analyze service layer",
            "com.example.service",
            Map.of("focus", "business logic")
        )
    ),
    List.of(
        new ContextSelection(
            "sel-1",
            orchestratorContextId,
            "Package structure analysis shows clear separation...",
            "Selected to inform subdomain boundaries"
        )
    ),
    Map.of("orchestratorType", "discovery")
);
```

## 7. Consolidation Template (Collectors, Mix-in)

### Creating Consolidation Output

```java
// In a collector action (collector results implement ConsolidationTemplate)
var discoveryResult = new DiscoveryCollectorResult(
    "1.0.0",
    resultId,
    inputs,
    "MERGE_BY_RELEVANCE",
    List.of(),  // no conflicts
    Map.of("totalReferences", 35.0, "avgRelevance", 0.75),
    mergedOutput,
    new CollectorDecision(
        CollectorDecisionType.ADVANCE_PHASE,
        "All subdomains analyzed successfully",
        "PLANNING"
    ),
    List.of(orchestratorContextId, agent1ContextId, agent2ContextId),
    Map.of("collectorType", "discovery"),
    unifiedCodeMap,
    recommendations,
    querySpecificFindings,
    discoveryCuration
);

// Curations are exposed via a derived list:
// discoveryResult.curations() -> List.of(discoveryCuration)
```

## 8. Discovery Report

### Creating Enhanced Discovery Output

```java
var report = new DiscoveryReport(
    "1.0.0",
    resultId,
    upstreamContextId,
    List.of(
        new FileReference(
            "src/main/java/com/example/UserService.java",
            10, 50, 0.95,
            "public class UserService { ... }",
            List.of("service", "user-management")
        )
    ),
    List.of(
        new CrossLink("link-1", "ref-1", "ref-2", LinkType.CALLS, "UserService calls UserRepository")
    ),
    List.of(
        new SemanticTag("tag-1", "service-layer", TagCategory.ARCHITECTURE, 0.9, List.of("ref-1"))
    ),
    List.of(
        new GeneratedQuery(
            "q-1",
            "user service implementation",
            List.of("src/main/java/com/example/UserService.java"),
            QueryType.CODE_SEARCH,
            Map.of("UserService.java", 1.0)
        )
    ),
    List.of(),  // diagrams
    "Service layer follows hexagonal architecture...",
    List.of("Dependency Injection", "Repository Pattern"),
    List.of("REST API", "Database"),
    Map.of("UserService.java", 0.95)
);
```

## Testing

### Unit Test Example

```java
@Test
void testPromptAssembly() {
    var registry = new PromptContributorRegistry(List.of(
        new EpisodicMemoryPromptContributor()
    ));
    var assembly = new PromptAssembly(registry);
    
    var context = new PromptContext(
        AgentType.DISCOVERY_AGENT,
        testContextId,
        null,
        null,
        null,
        Map.of()
    );
    
    String result = assembly.assemble("Base prompt", context);
    
    assertThat(result)
        .contains("Base prompt")
        .contains("Episodic Memory Tool")
        .contains("REFLECT");
}
```

### Integration Test (Cucumber)

```gherkin
@typed-context-flow
Scenario: Discovery context flows to planning
  Given a workflow with ID "wf-test-123"
  And discovery collector produces result with ID "wf-test-123/discovery-collector/001"
  When planning orchestrator receives the discovery result
  Then the planning context includes discovery collector ID "wf-test-123/discovery-collector/001"
  And planning agents receive typed DiscoveryCollectorContext
```

## Common Patterns

### Pattern 1: Context Chain Reconstruction

```java
public List<ContextId> reconstructContextChain(ContextId current) {
    List<ContextId> chain = new ArrayList<>();
    chain.add(current);
    
    // Walk up through upstream contexts
    UpstreamContext upstream = getUpstreamContext(current);
    while (upstream != null) {
        chain.add(0, upstream.contextId());
        upstream = getUpstreamContext(upstream.contextId());
    }
    
    return chain;
}
```

### Pattern 2: Extracting Training Data

```java
public ControllerTrainingExample extractFromDelegation(DelegationTemplate delegation) {
    return new ControllerTrainingExample(
        delegation.goal(),
        delegation.delegationRationale(),
        delegation.assignments().stream()
            .map(a -> a.assignedGoal())
            .toList(),
        delegation.contextSelections().stream()
            .map(c -> c.selectedContent())
            .toList()
    );
}
```
