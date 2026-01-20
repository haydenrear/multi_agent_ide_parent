# Research: Agent Prompts, Structured Outputs, and Tool Prompt Contributors

**Feature**: 004-agent-prompts-outputs
**Date**: 2026-01-19

## Research Tasks Completed

### 1. Context ID Design Patterns

**Decision**: Use structured context ID with workflow-scoped uniqueness

**Rationale**: 
- Workflow run ID enables grouping all contexts from a single execution
- Agent type segment enables filtering by agent category
- Sequence number enables ordering within same agent type
- Timestamp provides temporal ordering and debugging

**Alternatives Considered**:
| Alternative | Why Rejected |
|-------------|--------------|
| UUID only | No semantic meaning, harder to debug |
| Hierarchical path (parent/child) | Too deep nesting, complex to generate |
| Hash-based | Not human-readable |

**Format Chosen**:
```
{workflowRunId}/{agentType}/{sequenceNumber}/{timestamp}
```

**Implementation Notes**:
- `workflowRunId`: Generated at workflow start, stored in OrchestratorNode
- `agentType`: Enum value from Events.NodeType
- `sequenceNumber`: Atomic counter per workflow per agent type
- `timestamp`: ISO-8601 format

---

### 2. Upstream Context Typing Strategy

**Decision**: Use sealed interface hierarchy with explicit typed containers

**Rationale**:
- Java 21 sealed interfaces provide compile-time safety
- Explicit typing makes dependencies visible in code
- Enables IDE navigation from downstream to upstream

**Alternatives Considered**:
| Alternative | Why Rejected |
|-------------|--------------|
| Generic Map<String, Object> | No type safety, runtime errors |
| Single base class with optional fields | Null checks everywhere, unclear dependencies |
| Marker interfaces | No compile-time enforcement |

**Upstream Context Hierarchy**:
```java
sealed interface UpstreamContext permits
    OrchestratorUpstreamContext,
    DiscoveryOrchestratorUpstreamContext,
    DiscoveryAgentUpstreamContext,
    DiscoveryCollectorContext,
    PlanningOrchestratorUpstreamContext,
    PlanningAgentUpstreamContext,
    PlanningCollectorContext,
    TicketOrchestratorUpstreamContext,
    TicketAgentUpstreamContext,
    TicketCollectorContext,
    ReviewUpstreamContext,
    MergerUpstreamContext
```

**Key Dependencies**:
| Agent | Typed Upstream Context |
|-------|------------------------|
| Discovery Orchestrator | OrchestratorContext |
| Discovery Agent | DiscoveryOrchestratorContext |
| Discovery Collector | List<DiscoveryAgentResult> |
| Planning Orchestrator | DiscoveryCollectorContext |
| Planning Agent | DiscoveryCollectorContext |
| Planning Collector | List<PlanningAgentResult>, DiscoveryCollectorContext |
| Ticket Orchestrator | DiscoveryCollectorContext, PlanningCollectorContext |
| Ticket Agent | DiscoveryCollectorContext, PlanningCollectorContext, PlanningTicket |
| Ticket Collector | List<TicketAgentResult>, full upstream chain |

---

### 3. Previous Context for Reruns

**Decision**: Nullable PreviousContext mix-in with per-agent implementations and discrete prior contexts

**Rationale**:
- Nullable makes first-run case explicit (no previous context)
- Serialized output enables prompt inclusion without type coupling
- Error info helps agent understand what went wrong

**Alternatives Considered**:
| Alternative | Why Rejected |
|-------------|--------------|
| Optional<T> with generic typing | Type erasure issues, complex generics |
| Boolean flag + separate fields | Scattered data, easy to miss error info |
| Always present with "empty" sentinel | Confusing semantics |

**PreviousContext Structure**:
```java
sealed interface PreviousContext permits
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

record DiscoveryOrchestratorPreviousContext(
    ContextId previousContextId,
    String serializedOutput,
    String errorMessage,        // null if no error
    String errorStackTrace,     // null if no error
    int attemptNumber,
    Instant previousAttemptAt,
    DiscoveryCollectorContext previousDiscoveryCuration,
    PlanningCollectorContext previousPlanningCuration,
    TicketCollectorContext previousTicketCuration
) implements PreviousContext {}
```

---

### 4. Prompt Contributor Framework Design

**Decision**: Interface-based plugin system with priority ordering and agent type filtering

**Rationale**:
- Interface allows Spring injection of all contributors
- Priority ordering ensures consistent prompt structure
- Agent type filtering prevents irrelevant contributions

**Alternatives Considered**:
| Alternative | Why Rejected |
|-------------|--------------|
| Annotation-based contributors | Less flexible, harder to test |
| Event-based contribution | Timing issues, complex lifecycle |
| Template inheritance | Rigid, doesn't support dynamic tools |

**PromptContributor Interface**:
```java
interface PromptContributor {
    String name();
    Set<AgentType> applicableAgents();
    String contribute(PromptContext context);
    int priority();  // lower = earlier in prompt
    
    default boolean isApplicable(AgentType agentType) {
        return applicableAgents().contains(agentType) 
            || applicableAgents().contains(AgentType.ALL);
    }
}
```

**PromptContext**:
```java
record PromptContext(
    AgentType agentType,
    ContextId currentContextId,
    UpstreamContext upstreamContext,
    PreviousContext previousContext,  // nullable
    BlackboardHistory blackboardHistory,  // nullable
    Map<String, Object> metadata
)
```

---

### 5. Episodic Memory Prompt Contributor

**Decision**: Structured guidance with retain/reflect/recall sections emphasizing reflection

**Rationale**:
- Explicit sections make guidance scannable by LLM
- Emphasis on reflect aligns with knowledge graph connection building
- Action triggers help agent know WHEN to use each operation

**Prompt Structure**:
```markdown
## Episodic Memory Tool

You have access to episodic memory with three operations:

### RETAIN - Store New Episodes
Use RETAIN when you encounter:
- Important patterns or decisions
- Key code structures worth remembering
- Successful approaches to problems
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
Use RECALL at the start of new tasks to:
- Find similar prior analyses
- Retrieve relevant patterns
- Understand historical context

**Recommended Flow**: RECALL → Work → RETAIN key learnings → REFLECT on connections
```

---

### 6. Delegation Template Design (Orchestrators)

**Decision**: Versioned structured output with explicit sections for controller model training

**Rationale**:
- Version enables dataset filtering by schema version
- Sections align with controller model training needs
- Context selection section captures orchestrator decision-making

**Delegation Template Structure**:
```java
record DelegationTemplate(
    String schemaVersion,           // "1.0.0"
    ContextId resultId,
    ContextId upstreamContextId,
    
    // Delegation decision
    String goal,
    String delegationRationale,     // Why this decomposition
    List<AgentAssignment> assignments,
    
    // Context selection (what the orchestrator chose to pass)
    List<ContextSelection> contextSelections,
    
    // Metadata for training
    Map<String, String> metadata
)

record AgentAssignment(
    String agentId,
    AgentType agentType,
    String assignedGoal,
    String subdomainFocus,          // nullable
    Map<String, String> contextToPass
)

record ContextSelection(
    String selectionId,
    String sourceContextId,
    String selectedContent,
    String selectionRationale       // Why this was selected
)
```

---

### 7. Consolidation Template Design (Collectors)

**Decision**: Versioned structured output with merge strategy documentation

**Rationale**:
- Captures merging decisions for consolidation model training
- Input references enable traceability
- Decision section captures advancement logic

**Consolidation Template Structure (Mix-in)**:
```java
interface ConsolidationTemplate {
    String schemaVersion();           // "1.0.0"
    ContextId resultId();
    
    // Input references
    List<InputReference> inputs();
    
    // Merge process
    String mergeStrategy();
    List<ConflictResolution> conflictResolutions();
    Map<String, Double> aggregatedMetrics();
    
    // Consolidated output
    String consolidatedOutput();
    
    // Decision
    CollectorDecision decision();
    
    // Upstream context chain
    List<ContextId> upstreamContextChain();
    
    // Metadata for training
    Map<String, String> metadata();
    
    // Derived curated contexts
    List<Curation> curations();
}

record InputReference(
    ContextId inputContextId,
    String inputType,
    String inputSummary
)

record ConflictResolution(
    String conflictDescription,
    String resolutionApproach,
    List<ContextId> conflictingInputs
)
```

---

### 8. Discovery Report Enhancement

**Decision**: Rich structured output with file references, tags, queries, diagrams, and recommendations

**Rationale**:
- File references with relevance enable retrieval training
- Generated queries create query-document pairs for training
- Semantic tags enable filtering and categorization
- Recommendations provide actionable guidance for planning

**Discovery Report Structure**:
```java
record DiscoveryReport(
    String schemaVersion,
    ContextId resultId,
    ContextId upstreamContextId,
    
    // Core findings
    List<FileReference> fileReferences,
    List<CrossLink> crossLinks,
    List<SemanticTag> semanticTags,
    List<GeneratedQuery> generatedQueries,
    
    // Diagrams
    List<DiagramRepresentation> diagrams,
    
    // Analysis
    String architectureOverview,
    List<String> keyPatterns,
    List<String> integrationPoints,
    
    // For retrieval training
    Map<String, Double> relevanceScores
)

record FileReference(
    String filePath,
    int startLine,
    int endLine,
    double relevanceScore,
    String snippet,
    List<String> tags
)

record GeneratedQuery(
    String queryText,
    List<String> expectedResultPaths,
    String queryType   // "code_search", "pattern_find", "dependency_trace"
)
```

---

### 9. Discovery Collector Enhancement

**Decision**: Code map + recommendations + query-specific findings

**Rationale**:
- Code map provides unified view
- Recommendations give planning actionable items
- Query-specific findings enable understanding what queries found what

**Enhanced Discovery Collector Output**:
```java
record DiscoveryCollectorResult(
    // Standard consolidation template fields
    String schemaVersion,
    ContextId resultId,
    List<InputReference> inputs,
    String mergeStrategy,
    List<ConflictResolution> conflictResolutions,
    Map<String, Double> aggregatedMetrics,
    String consolidatedOutput,
    CollectorDecision decision,
    List<ContextId> upstreamContextChain,
    Map<String, String> metadata,
    
    // Unified code map
    CodeMap unifiedCodeMap,
    
    // Recommendations (actionable for planning)
    List<Recommendation> recommendations,
    
    // Query-specific findings
    Map<String, QueryFindings> querySpecificFindings,
    
    // Curated context for downstream
    DiscoveryCollectorContext discoveryCuration
) implements ConsolidationTemplate

record Recommendation(
    String recommendationId,
    String title,
    String description,
    RecommendationPriority priority,
    List<ContextId> supportingDiscoveryIds,
    List<String> relatedFilePaths
)

record QueryFindings(
    String originalQuery,
    List<FileReference> results,
    double confidenceScore,
    String summary
)
```

---

### 10. Integration with Existing BlackboardHistory

**Decision**: Extend existing PromptProvider pattern to use PromptAssembly

**Rationale**:
- Maintains backward compatibility
- Leverages existing prompt augmentation infrastructure
- Single integration point

**Integration Approach**:
```java
// In BlackboardHistory
public PromptProvider generatePromptProvider(Class<?> inputType, PromptAssembly assembly) {
    PromptProvider typeProvider = generatePromptProvider(inputType);
    return basePrompt -> {
        String withHistory = typeProvider.providePrompt(basePrompt);
        return assembly.assemble(withHistory, currentContext);
    };
}
```

---

## Technology Best Practices Applied

### Java 21 Records
- All new data structures as records (immutable, compact)
- Sealed interfaces for type hierarchies
- Pattern matching in switch for routing

### Spring Boot Integration
- @Bean for PromptContributorRegistry
- @Component for individual contributors
- Constructor injection via @RequiredArgsConstructor

### Testing Strategy
- Unit tests for each new record/class
- Integration tests via test_graph Cucumber
- Property-based tests for context ID generation

---

## Resolved Clarifications

All NEEDS CLARIFICATION items from technical context have been resolved through this research:

| Item | Resolution |
|------|------------|
| Context ID format | Structured format with workflow/agent/sequence/timestamp |
| Upstream context typing | Sealed interface hierarchy |
| Previous context handling | Nullable record with serialized output |
| Prompt contributor pattern | Interface with priority ordering |
| Template versioning | Semantic versioning in schemaVersion field |

---

## Next Steps

Proceed to Phase 1: Generate data-model.md and contracts/
