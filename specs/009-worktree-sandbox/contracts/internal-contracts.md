# Internal Contracts: Worktree Sandbox

**Feature**: 009-worktree-sandbox  
**Date**: 2026-01-31

## Overview

This feature is internal to the multi-agent IDE system and does not expose external APIs. The contracts below define internal interfaces between components.

## Component Contracts

### 1. RequestDecorator Contract

**Interface**: `RequestDecorator`

```java
public interface RequestDecorator {
    int order();
    <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context);
}
```

**WorktreeContextRequestDecorator Implementation**:
- **Order**: -5000 (runs early in chain)
- **Input**: Any `AgentRequest` subtype
- **Output**: Same request with `worktreeContext` populated
- **Behavior**:
  - OrchestratorRequest: Lookup node → resolve worktree → set context
  - Other requests: Copy worktree from `context.lastRequest()`

**RequestContextRepositoryDecorator Implementation**:
- **Order**: -6000 (runs before WorktreeContextRequestDecorator)
- **Purpose**: Stores request and all context items in `RequestContextRepository`
- **Behavior**: 
  - Listens for all request types, results, contexts
  - Stores them keyed by session ID (ArtifactKey/contextId)
  - Enables lookup by session ID from MCP tools

### 2. RequestContext Record (NEW)

**Record with @With**: `RequestContext`

```java
@With
public record RequestContext(
    ArtifactKey artifactKey,  // The request/session ID - call .value() for string
    AgentModels.AgentRequest currentRequest,
    AgentModels.AgentRequest previousRequest,
    WorktreeContext worktreeContext,
    ToolContext toolContext,
    PromptContext promptContext,
    OperationContext operationContext,
    Instant createdAt,
    Instant lastUpdatedAt
) {
    /**
     * Convenience method to get the string session ID.
     */
    public String sessionId() {
        return artifactKey != null ? artifactKey.value() : null;
    }
    
    public static RequestContext empty(ArtifactKey artifactKey) {
        return new RequestContext(
            artifactKey,
            null,  // currentRequest
            null,  // previousRequest
            null,  // worktreeContext
            null,  // toolContext
            null,  // promptContext
            null,  // operationContext
            Instant.now(),
            Instant.now()
        );
    }
}
```

**RequestContextRepository Interface**:

```java
public interface RequestContextRepository {
    
    /**
     * Get the RequestContext for an artifact key, creating empty if not exists.
     */
    RequestContext getOrCreate(ArtifactKey artifactKey);
    
    /**
     * Get the RequestContext for an artifact key, or null if not exists.
     */
    RequestContext get(ArtifactKey artifactKey);
    
    /**
     * Get the RequestContext by string session ID (convenience method).
     */
    default RequestContext get(String sessionId) {
        return get(new ArtifactKey(sessionId));
    }
    
    /**
     * Update the RequestContext for an artifact key (replaces existing).
     * Decorators use: repo.update(artifactKey, ctx -> ctx.withWorktreeContext(worktree))
     */
    void update(ArtifactKey artifactKey, Function<RequestContext, RequestContext> updater);
    
    /**
     * Clear context for an artifact key (on completion/cleanup).
     */
    void clear(ArtifactKey artifactKey);
}
```

**InMemoryRequestContextRepository Implementation**:
- `ConcurrentHashMap<ArtifactKey, RequestContext>` for thread safety
- Keyed by ArtifactKey (= request.contextId())
- Decorators call `update(artifactKey, ctx -> ctx.withXxx(...))` to set fields
- MCP tools call `get(sessionId)` (string convenience method) and access specific fields
- Each decorator updates one or more fields using `@With` methods

**Injection Points** (Spring @Component):
- **Decorators** - populate context during request processing
- **Controller** - access context for handling requests
- **AcpChatModel** - translate sandbox context into provider-specific options
- **AcpTooling** - validate file operations against sandbox

### 2a. Provider-Specific Sandbox Translation (NEW)

**Purpose**: Each ACP provider (Claude Code, Codex, etc.) has different mechanisms for sandbox enforcement. Translation strategies convert WorktreeContext into provider-specific environment variables and command line arguments.

**Interface**: `SandboxTranslationStrategy`

```java
public interface SandboxTranslationStrategy {
    
    /**
     * Provider name this strategy handles (e.g., "claude-code", "codex", "goose")
     */
    String providerName();
    
    /**
     * Translate worktree context into environment variables for command creation.
     */
    Map<String, String> toEnvironmentVariables(WorktreeContext worktreeContext);
    
    /**
     * Translate worktree context into command line arguments for command creation.
     */
    List<String> toCommandLineArgs(WorktreeContext worktreeContext);
    
    /**
     * Whether this provider supports sandbox enforcement.
     */
    boolean supportsSandbox();
}
```

**Provider Implementations**:

```java
// Claude Code sandbox translation
@Component
public class ClaudeCodeSandboxStrategy implements SandboxTranslationStrategy {
    @Override
    public String providerName() { return "claude-code"; }
    
    @Override
    public Map<String, String> toEnvironmentVariables(WorktreeContext ctx) {
        return Map.of(
            "CLAUDE_CODE_ALLOWED_PATHS", ctx.worktreePath().toString(),
            "CLAUDE_CODE_SANDBOX_MODE", "strict"
        );
    }
    
    @Override
    public List<String> toCommandLineArgs(WorktreeContext ctx) {
        return List.of(
            "--allowed-paths", ctx.worktreePath().toString(),
            "--sandbox"
        );
    }
    
    @Override
    public boolean supportsSandbox() { return true; }
}

// Codex sandbox translation
@Component
public class CodexSandboxStrategy implements SandboxTranslationStrategy {
    @Override
    public String providerName() { return "codex"; }
    
    @Override
    public Map<String, String> toEnvironmentVariables(WorktreeContext ctx) {
        return Map.of(
            "CODEX_WORKSPACE", ctx.worktreePath().toString()
        );
    }
    
    @Override
    public List<String> toCommandLineArgs(WorktreeContext ctx) {
        return List.of(
            "--workspace", ctx.worktreePath().toString()
        );
    }
    
    @Override
    public boolean supportsSandbox() { return true; }
}

// Codex sandbox translation
@Component
public class CodexSandboxStrategy implements SandboxTranslationStrategy {
    @Override
    public String providerName() { return "goose"; }
    
    @Override
    public Map<String, String> toEnvironmentVariables(WorktreeContext ctx) {
        return Map.of(
            "GOOSE_ROOT_DIR", ctx.worktreePath().toString()
        );
    }
    
    @Override
    public List<String> toCommandLineArgs(WorktreeContext ctx) {
        return List.of(
            "--root-dir", ctx.worktreePath().toString()
        );
    }
    
    @Override
    public boolean supportsSandbox() { return true; }
}
```

**Registry**: `SandboxTranslationRegistry`

```java
@Component
public class SandboxTranslationRegistry {
    private final Map<String, SandboxTranslationStrategy> strategies;
    
    public SandboxTranslationRegistry(List<SandboxTranslationStrategy> strategies) {
        this.strategies = strategies.stream()
            .collect(Collectors.toMap(
                SandboxTranslationStrategy::providerName,
                Function.identity()
            ));
    }
    
    public Optional<SandboxTranslationStrategy> getStrategy(String providerName) {
        return Optional.ofNullable(strategies.get(providerName));
    }
}
```

**Usage in AcpChatModel** (command creation):

```kotlin
// In createProcessStdioTransport or similar command creation
val worktreeContext = requestContextRepository.get(sessionId)?.worktreeContext()
val strategy = sandboxTranslationRegistry.getStrategy(providerName)

if (worktreeContext != null && strategy?.supportsSandbox() == true) {
    // Add environment variables
    val envVars = strategy.toEnvironmentVariables(worktreeContext)
    processBuilder.environment().putAll(envVars)
    
    // Add command line arguments
    val args = strategy.toCommandLineArgs(worktreeContext)
    command.addAll(args)
}
```

**Decorator Update Pattern**:
```java
// In WorktreeContextRequestDecorator
ArtifactKey artifactKey = request.contextId();
requestContextRepository.update(artifactKey, ctx -> 
    ctx.withWorktreeContext(resolvedWorktree)
       .withLastUpdatedAt(Instant.now())
);

// In ToolContextDecorator  
ArtifactKey artifactKey = request.contextId();
requestContextRepository.update(artifactKey, ctx ->
    ctx.withToolContext(toolContext)
       .withLastUpdatedAt(Instant.now())
);
```

### 3. PromptContributor Contract

**Interface**: `PromptContributor`

```java
public interface PromptContributor {
    String name();
    boolean include(PromptContext promptContext);
    String contribute(PromptContext context);
    int priority();
}
```

**TicketOrchestratorWorktreePromptContributor Implementation**:
- **Name**: "ticket-orchestrator-worktree"
- **Priority**: 60 (after WeAreHere at 90, before generic prompts)
- **Applicability**: Only for `AgentType.TICKET_ORCHESTRATOR`
- **Template output**: Instructions for worktree creation per ticket

### 4. WorktreeSandbox Contract

**Class**: `WorktreeSandbox`

```java
public class WorktreeSandbox {
    /**
     * Validates if target path is within sandbox boundary.
     * @param targetPath Path to validate (will be normalized)
     * @param sandboxRoot Root of allowed sandbox
     * @return true if path is within sandbox
     */
    public boolean isPathWithinSandbox(Path targetPath, Path sandboxRoot);
    
    /**
     * Validates path and returns result (no exception).
     * @return SandboxValidationResult with success/failure and message
     */
    public SandboxValidationResult validate(Path targetPath, Path sandboxRoot);
    
    /**
     * Normalizes path to canonical form.
     * Resolves symlinks, "..", "." patterns.
     */
    public Path normalizePath(Path path);
}

public record SandboxValidationResult(
    boolean allowed,
    String attemptedPath,
    String sandboxRoot,
    String operation,
    String message
) {
    public static SandboxValidationResult allowed() {
        return new SandboxValidationResult(true, null, null, null, null);
    }
    
    public static SandboxValidationResult denied(String attemptedPath, String sandboxRoot, 
                                                  String operation, String message) {
        return new SandboxValidationResult(false, attemptedPath, sandboxRoot, operation, message);
    }
}
```

### 5. AcpTooling Sandbox Integration

**Session ID Resolution**:
- Uses existing `MCP_SESSION_HEADER` from `AcpChatModel.MCP_SESSION_HEADER`
- Session ID = ArtifactKey.value() = contextId on all requests
- Annotated with `@SetFromHeader(MCP_SESSION_HEADER)` on each tool method

**Modified Methods** (return JSON error instead of throwing):

```java
@Component
@RequiredArgsConstructor
public class AcpTooling implements ToolCarrier {

    private final FileSystemTools fileSystemTools;
    private final ShellTools shellTools;
    private final RequestContextRepository requestContextRepository;
    private final WorktreeSandbox worktreeSandbox;
    private final ObjectMapper objectMapper;

    @Tool(description = "Read file contents")
    public String read(
            @SetFromHeader(MCP_SESSION_HEADER) String sessionId,
            String filePath, 
            Integer offset, 
            Integer limit
    ) {
        // Validate sandbox
        SandboxValidationResult validation = validatePath(sessionId, filePath, "READ");
        if (!validation.allowed()) {
            return serializeError(validation);
        }
        // Proceed with existing logic
        return fileSystemTools.read(filePath, offset, limit, null);
    }

    @Tool(description = "Edit file by replacing text")
    public String edit(
            @SetFromHeader(MCP_SESSION_HEADER) String sessionId,
            String filePath, 
            String old_string, 
            String new_string, 
            Boolean replace_all
    ) {
        SandboxValidationResult validation = validatePath(sessionId, filePath, "EDIT");
        if (!validation.allowed()) {
            return serializeError(validation);
        }
        return fileSystemTools.edit(filePath, old_string, new_string, replace_all, null);
    }

    @Tool(description = "Write file contents")
    public String write(
            @SetFromHeader(MCP_SESSION_HEADER) String sessionId,
            String filePath, 
            String content
    ) {
        SandboxValidationResult validation = validatePath(sessionId, filePath, "WRITE");
        if (!validation.allowed()) {
            return serializeError(validation);
        }
        return fileSystemTools.write(filePath, content, null);
    }
    
    private SandboxValidationResult validatePath(String sessionId, String filePath, String operation) {
        if (sessionId == null || sessionId.isBlank()) {
            // No session = legacy mode, allow
            return SandboxValidationResult.allowed();
        }
        
        RequestContext requestContext = requestContextRepository.get(sessionId);
        if (requestContext == null || requestContext.worktreeContext() == null) {
            // No worktree context = legacy mode, allow
            return SandboxValidationResult.allowed();
        }
        
        WorktreeContext worktree = requestContext.worktreeContext();
        Path targetPath = Path.of(filePath);
        Path sandboxRoot = worktree.worktreePath();
        
        // Check main worktree
        if (worktreeSandbox.isPathWithinSandbox(targetPath, sandboxRoot)) {
            return SandboxValidationResult.allowed();
        }
        
        // Check submodule paths
        for (Path submodulePath : worktree.submodulePaths()) {
            if (worktreeSandbox.isPathWithinSandbox(targetPath, submodulePath)) {
                return SandboxValidationResult.allowed();
            }
        }
        
        return SandboxValidationResult.denied(
            filePath, 
            sandboxRoot.toString(), 
            operation,
            "Access denied: path is outside worktree sandbox"
        );
    }
    
    private String serializeError(SandboxValidationResult validation) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "error", true,
                "errorType", "SANDBOX_VIOLATION",
                "attemptedPath", validation.attemptedPath(),
                "sandboxRoot", validation.sandboxRoot(),
                "operation", validation.operation(),
                "message", validation.message()
            ));
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"Sandbox violation\"}";
        }
    }
}
```

### 6. Session Header Contract

**Header**: `MCP_SESSION_HEADER` (from `AcpChatModel`)

| Aspect | Value |
|--------|-------|
| Name | Value of `AcpChatModel.MCP_SESSION_HEADER` constant |
| Type | String |
| Required | No (backward compatible) |
| Purpose | Identifies session for context lookup |
| Mapping | Session ID = ArtifactKey.value() = request.contextId().value() |

**Resolution Flow**:
```
MCP Tool Call
    │
    ├── @SetFromHeader(MCP_SESSION_HEADER) extracts sessionId
    │
    ▼
RequestContextRepository.getWorktreeContext(sessionId)
    │
    ▼
WorktreeContext (with worktreePath, submodulePaths)
    │
    ▼
WorktreeSandbox.validate(targetPath, worktreePath)
```

### 7. Request Types Requiring WorktreeContext

**All request types that need worktreeContext field added**:

| Request Type | Worktree Source |
|--------------|-----------------|
| `OrchestratorRequest` | Lookup from GraphRepository by contextId |
| `OrchestratorCollectorRequest` | Inherit from parent OrchestratorRequest |
| `DiscoveryOrchestratorRequest` | Inherit from parent |
| `DiscoveryAgentRequest` | Inherit from parent |
| `DiscoveryAgentRequests` | Inherit from parent |
| `DiscoveryCollectorRequest` | Inherit from parent |
| `PlanningOrchestratorRequest` | Inherit from parent |
| `PlanningAgentRequest` | Inherit from parent |
| `PlanningAgentRequests` | Inherit from parent |
| `PlanningCollectorRequest` | Inherit from parent |
| `TicketOrchestratorRequest` | Inherit from parent |
| `TicketAgentRequest` | Created by TicketOrchestrator (new worktree per ticket) |
| `TicketAgentRequests` | Contains individual TicketAgentRequests |
| `TicketCollectorRequest` | Inherit from parent |
| `ReviewRequest` | Inherit from ticket agent worktree |
| `MergerRequest` | Inherit from ticket agent worktree |
| `ContextManagerRequest` | Inherit from parent |
| `ContextManagerRoutingRequest` | Inherit from parent |

**WorktreeContext Record** (added to each request):

```java
public record WorktreeContext(
    String worktreeId,
    Path worktreePath,
    List<Path> submodulePaths,
    String parentWorktreeId
) {}
```

### 8. Decorator Population Flow

```
Request Enters System
    │
    ▼
RequestContextRepositoryDecorator (order: -6000)
    │  - Stores request in RequestContextRepository
    │  - Keyed by request.contextId().value() (= sessionId)
    │
    ▼
WorktreeContextRequestDecorator (order: -5000)
    │  - Resolves worktree for request
    │  - Stores WorktreeContext in RequestContextRepository
    │  - Returns request with worktreeContext field set
    │
    ▼
Other Decorators...
    │
    ▼
ToolContextDecorator (order: varies)
    │  - Stores ToolContext in RequestContextRepository
    │
    ▼
MCP Tool Call (later)
    │  - Receives sessionId via @SetFromHeader
    │  - Queries RequestContextRepository.getWorktreeContext(sessionId)
    │  - Validates path against sandbox
```

## Error Response Format

**JSON Error Response** (instead of exceptions):

```json
{
  "error": true,
  "errorType": "SANDBOX_VIOLATION",
  "attemptedPath": "/other/path/file.txt",
  "sandboxRoot": "/worktrees/agent-1",
  "operation": "READ",
  "message": "Access denied: path is outside worktree sandbox"
}
```

**Success Response**: Original tool return value (unchanged)

## Backward Compatibility Contract

| Scenario | Behavior |
|----------|----------|
| Request without worktreeContext | No sandbox enforcement; legacy behavior |
| Missing sessionId header | No sandbox enforcement; legacy behavior |
| SessionId not in RequestContextRepository | No sandbox enforcement; legacy behavior |
| Null/empty worktree fields | Treated as no sandbox; operations proceed |

---

## Merge Flow Contracts

### 9. DispatchedAgentResultDecorator Contract (Phase 1: Trunk → Child)

**Interface**: `DispatchedAgentResultDecorator extends ResultDecorator`

```java
public interface DispatchedAgentResultDecorator extends ResultDecorator {
    // Inherits from ResultDecorator:
    // <T extends AgentModels.AgentResult> T decorate(T result, DecoratorContext context);
}
```

**WorktreeMergeResultDecorator Implementation**:
- **Order**: 1000 (runs after core decorators, before emit decorators)
- **Input**: `TicketAgentResult`, `PlanningAgentResult`, `DiscoveryAgentResult`
- **Output**: Same result with `mergeDescriptor` populated
- **Behavior**:
  1. Get child's `WorktreeSandboxContext` from result or context
  2. Get parent's `WorktreeSandboxContext` (trunk) from `context.lastRequest().worktreeContext()`
  3. For each submodule: `gitWorktreeService.mergeWorktrees(trunk.submodule.id, child.submodule.id)`
  4. Merge main: `gitWorktreeService.mergeWorktrees(trunk.main.id, child.main.id)`
  5. Build `MergeDescriptor` with direction=TRUNK_TO_CHILD
  6. Return `result.toBuilder().mergeDescriptor(descriptor).build()`

```java
@Component
@RequiredArgsConstructor
public class WorktreeMergeResultDecorator implements DispatchedAgentResultDecorator {

    private final GitWorktreeService gitWorktreeService;

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T result, DecoratorContext context) {
        if (result == null) {
            return result;
        }
        
        // Only handle dispatch agent results
        if (!(result instanceof AgentModels.TicketAgentResult) &&
            !(result instanceof AgentModels.PlanningAgentResult) &&
            !(result instanceof AgentModels.DiscoveryAgentResult)) {
            return result;
        }
        
        WorktreeSandboxContext childContext = resolveChildWorktreeContext(result, context);
        WorktreeSandboxContext trunkContext = resolveTrunkWorktreeContext(context);
        
        if (childContext == null || trunkContext == null) {
            return result;  // No worktree context, skip merge
        }
        
        MergeDescriptor descriptor = performTrunkToChildMerge(trunkContext, childContext);
        
        return addMergeDescriptor(result, descriptor);
    }
    
    private MergeDescriptor performTrunkToChildMerge(
            WorktreeSandboxContext trunk, 
            WorktreeSandboxContext child) {
        List<SubmoduleMergeResult> submoduleResults = new ArrayList<>();
        List<String> allConflicts = new ArrayList<>();
        boolean allSuccessful = true;
        
        // Merge submodules first
        for (SubmoduleWorktreeContext childSubmodule : child.submoduleWorktrees()) {
            SubmoduleWorktreeContext trunkSubmodule = findMatchingSubmodule(trunk, childSubmodule.submoduleName());
            if (trunkSubmodule != null) {
                MergeResult result = gitWorktreeService.mergeWorktrees(
                        trunkSubmodule.worktreeId(), 
                        childSubmodule.worktreeId()
                );
                submoduleResults.add(new SubmoduleMergeResult(
                        childSubmodule.submoduleName(),
                        result,
                        false  // pointer update not applicable for trunk→child
                ));
                if (!result.successful()) {
                    allSuccessful = false;
                    allConflicts.addAll(result.conflicts().stream()
                            .map(MergeResult.MergeConflict::filePath)
                            .toList());
                }
            }
        }
        
        // Merge main worktree
        MergeResult mainResult = gitWorktreeService.mergeWorktrees(
                trunk.mainWorktree().worktreeId(),
                child.mainWorktree().worktreeId()
        );
        if (!mainResult.successful()) {
            allSuccessful = false;
            allConflicts.addAll(mainResult.conflicts().stream()
                    .map(MergeResult.MergeConflict::filePath)
                    .toList());
        }
        
        return new MergeDescriptor(
                MergeDirection.TRUNK_TO_CHILD,
                allSuccessful,
                allConflicts,
                submoduleResults,
                mainResult,
                allSuccessful ? null : "Merge conflicts detected"
        );
    }
}
```

### 10. ResultsRequestDecorator Contract (Phase 2: Child → Trunk)

**Interface**: `ResultsRequestDecorator` (NEW)

```java
public interface ResultsRequestDecorator {

    /**
     * Ordering for decorator execution. Lower values execute first.
     */
    default int order() {
        return 0;
    }

    /**
     * Decorate a ResultsRequest (TicketAgentResults, PlanningAgentResults, DiscoveryAgentResults).
     */
    <T extends AgentModels.ResultsRequest> T decorate(T resultsRequest, DecoratorContext context);
}
```

**WorktreeMergeResultsDecorator Implementation**:
- **Order**: 1000
- **Input**: `TicketAgentResults`, `PlanningAgentResults`, `DiscoveryAgentResults`
- **Output**: Same request with `mergeAggregation` populated
- **Behavior**:
  1. Get parent's `WorktreeSandboxContext` from `context.lastRequest().worktreeContext()`
  2. Initialize: `merged=[]`, `pending=[all child results]`, `conflicted=null`
  3. For each child result in order:
     - Get child's worktree context
     - Merge submodules (child→trunk), update pointers
     - If conflict: set `conflicted`, break loop
     - Merge main worktree (child→trunk)
     - If conflict: set `conflicted`, break loop
     - If success: move from pending to merged
  4. Build `MergeAggregation(merged, pending, conflicted)`
  5. Return `resultsRequest.withMergeAggregation(aggregation)`

```java
@Component
@RequiredArgsConstructor
public class WorktreeMergeResultsDecorator implements ResultsRequestDecorator {

    private final GitWorktreeService gitWorktreeService;

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public <T extends AgentModels.ResultsRequest> T decorate(T resultsRequest, DecoratorContext context) {
        if (resultsRequest == null) {
            return resultsRequest;
        }
        
        WorktreeSandboxContext trunkContext = resolveTrunkWorktreeContext(context);
        if (trunkContext == null) {
            return resultsRequest;  // No worktree context, skip merge
        }
        
        List<? extends AgentModels.AgentResult> childResults = getChildResults(resultsRequest);
        
        List<AgentMergeStatus> merged = new ArrayList<>();
        List<AgentMergeStatus> pending = new ArrayList<>(
                childResults.stream()
                        .map(r -> createPendingStatus(r))
                        .toList()
        );
        AgentMergeStatus conflicted = null;
        
        Iterator<AgentMergeStatus> pendingIterator = pending.iterator();
        while (pendingIterator.hasNext()) {
            AgentMergeStatus status = pendingIterator.next();
            WorktreeSandboxContext childContext = status.worktreeContext();
            
            if (childContext == null) {
                // No worktree, treat as merged (no-op)
                pendingIterator.remove();
                merged.add(status);
                continue;
            }
            
            MergeDescriptor descriptor = performChildToTrunkMerge(childContext, trunkContext);
            AgentMergeStatus updatedStatus = status.withMergeDescriptor(descriptor);
            
            if (!descriptor.successful()) {
                pendingIterator.remove();
                conflicted = updatedStatus;
                break;  // Stop on first conflict
            }
            
            pendingIterator.remove();
            merged.add(updatedStatus);
        }
        
        MergeAggregation aggregation = new MergeAggregation(merged, pending, conflicted);
        
        return (T) resultsRequest.withMergeAggregation(aggregation);
    }
    
    private MergeDescriptor performChildToTrunkMerge(
            WorktreeSandboxContext child, 
            WorktreeSandboxContext trunk) {
        List<SubmoduleMergeResult> submoduleResults = new ArrayList<>();
        List<String> allConflicts = new ArrayList<>();
        boolean allSuccessful = true;
        
        // Merge submodules first (child → trunk)
        for (SubmoduleWorktreeContext childSubmodule : child.submoduleWorktrees()) {
            SubmoduleWorktreeContext trunkSubmodule = findMatchingSubmodule(trunk, childSubmodule.submoduleName());
            if (trunkSubmodule != null) {
                MergeResult result = gitWorktreeService.mergeWorktrees(
                        childSubmodule.worktreeId(), 
                        trunkSubmodule.worktreeId()
                );
                
                boolean pointerUpdated = false;
                if (result.successful()) {
                    // Update submodule pointer in trunk's main worktree
                    try {
                        gitWorktreeService.updateSubmodulePointer(
                                trunk.mainWorktree().worktreeId(),
                                childSubmodule.submoduleName()
                        );
                        pointerUpdated = true;
                    } catch (Exception e) {
                        // Pointer update failed, treat as partial success
                    }
                }
                
                submoduleResults.add(new SubmoduleMergeResult(
                        childSubmodule.submoduleName(),
                        result,
                        pointerUpdated
                ));
                
                if (!result.successful()) {
                    allSuccessful = false;
                    allConflicts.addAll(result.conflicts().stream()
                            .map(MergeResult.MergeConflict::filePath)
                            .toList());
                    break;  // Stop on first submodule conflict
                }
            }
        }
        
        // Merge main worktree only if all submodules succeeded
        MergeResult mainResult = null;
        if (allSuccessful) {
            mainResult = gitWorktreeService.mergeWorktrees(
                    child.mainWorktree().worktreeId(),
                    trunk.mainWorktree().worktreeId()
            );
            if (!mainResult.successful()) {
                allSuccessful = false;
                allConflicts.addAll(mainResult.conflicts().stream()
                        .map(MergeResult.MergeConflict::filePath)
                        .toList());
            }
        }
        
        return new MergeDescriptor(
                MergeDirection.CHILD_TO_TRUNK,
                allSuccessful,
                allConflicts,
                submoduleResults,
                mainResult,
                allSuccessful ? null : "Merge conflicts detected"
        );
    }
}
```

### 11. ResultsRequest Interface Contract

**Interface**: `ResultsRequest` (NEW in AgentModels)

```java
public interface ResultsRequest extends AgentRequest {
    
    /**
     * The worktree context for this dispatch agent.
     */
    WorktreeSandboxContext worktreeContext();
    
    /**
     * The merge aggregation after child→trunk merges.
     */
    MergeAggregation mergeAggregation();
    
    /**
     * Return a copy with merge aggregation set.
     */
    ResultsRequest withMergeAggregation(MergeAggregation aggregation);
    
    /**
     * Get the list of child agent results.
     */
    List<? extends AgentResult> childResults();
}
```

**Implementations**:

```java
// In AgentModels.java
@Builder(toBuilder=true)
record TicketAgentResults(
        ArtifactKey contextId,
        ArtifactKey artifactKey,
        WorktreeSandboxContext worktreeContext,
        List<TicketAgentResult> ticketAgentResults,
        MergeAggregation mergeAggregation  // NEW field
) implements ResultsRequest {
    
    @Override
    public List<? extends AgentResult> childResults() {
        return ticketAgentResults != null ? ticketAgentResults : List.of();
    }
    
    @Override
    public ResultsRequest withMergeAggregation(MergeAggregation aggregation) {
        return this.toBuilder().mergeAggregation(aggregation).build();
    }
    
    // ... existing methods
}

// Similarly for PlanningAgentResults and DiscoveryAgentResults
```

### 12. AgentInterfaces Helper Method Contract

**Static Method**: `AgentInterfaces.decorateResultsRequest()`

```java
public static <T extends AgentModels.ResultsRequest> T decorateResultsRequest(
        T resultsRequest,
        ActionContext context,
        List<ResultsRequestDecorator> decorators,
        String agentName,
        String action,
        String method,
        AgentModels.AgentRequest lastRequest
) {
    if (resultsRequest == null || decorators == null || decorators.isEmpty()) {
        return resultsRequest;
    }
    
    DecoratorContext decoratorContext = new DecoratorContext(
            context.operationContext(),
            lastRequest,
            agentName,
            action,
            method
    );
    
    // Sort by order and apply
    List<ResultsRequestDecorator> sorted = decorators.stream()
            .sorted(Comparator.comparingInt(ResultsRequestDecorator::order))
            .toList();
    
    T decorated = resultsRequest;
    for (ResultsRequestDecorator decorator : sorted) {
        decorated = decorator.decorate(decorated, decoratorContext);
    }
    
    return decorated;
}
```

**Usage in dispatchTicketAgentRequests()**:

```java
@Action(canRerun = true)
public AgentModels.TicketAgentDispatchRouting dispatchTicketAgentRequests(
        @NotNull AgentModels.TicketAgentRequests input,
        ActionContext context
) {
    // ... existing code to collect results ...
    
    var ticketAgentResults = AgentModels.TicketAgentResults.builder()
            .ticketAgentResults(ticketResults)
            .worktreeContext(input.worktreeContext())  // Inherit from input
            .build();
    
    // NEW: Decorate with ResultsRequestDecorator chain
    ticketAgentResults = AgentInterfaces.decorateResultsRequest(
            ticketAgentResults,
            context,
            resultsRequestDecorators,  // Injected List<ResultsRequestDecorator>
            multiAgentAgentName(),
            ACTION_TICKET_DISPATCH,
            METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
            lastRequest
    );
    
    // Now ticketAgentResults has mergeAggregation populated
    // Pass to routing LLM...
}
```

### 13. MergeAggregationPromptContributorFactory Contract

**Factory**: `MergeAggregationPromptContributorFactory implements PromptContributorFactory`

Provides merge status context to routing LLMs via the standard prompt contributor pattern.

```java
@Component
public class MergeAggregationPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }
        
        // Only applicable for dispatch agent types that have ResultsRequest
        if (!isDispatchAgentType(context.agentType())) {
            return List.of();
        }
        
        // Extract MergeAggregation from the current request if it's a ResultsRequest
        MergeAggregation aggregation = extractMergeAggregation(context);
        if (aggregation == null) {
            return List.of();
        }
        
        return List.of(new MergeAggregationPromptContributor(aggregation));
    }
    
    private boolean isDispatchAgentType(AgentType agentType) {
        return agentType == AgentType.TICKET_AGENT_DISPATCH
            || agentType == AgentType.PLANNING_AGENT_DISPATCH
            || agentType == AgentType.DISCOVERY_AGENT_DISPATCH;
    }
    
    private MergeAggregation extractMergeAggregation(PromptContext context) {
        AgentModels.AgentRequest request = context.currentRequest();
        if (request instanceof AgentModels.ResultsRequest resultsRequest) {
            return resultsRequest.mergeAggregation();
        }
        // Also check metadata in case it was stored there
        Object fromMetadata = context.metadata().get("mergeAggregation");
        if (fromMetadata instanceof MergeAggregation agg) {
            return agg;
        }
        return null;
    }
}
```

**PromptContributor**: `MergeAggregationPromptContributor`

```java
public record MergeAggregationPromptContributor(
        MergeAggregation aggregation
) implements PromptContributor {

    @Override
    public String name() {
        return "MergeAggregationPromptContributor";
    }

    @Override
    public boolean include(PromptContext promptContext) {
        return aggregation != null;
    }

    @Override
    public String contribute(PromptContext context) {
        return template();
    }

    @Override
    public String template() {
        if (aggregation == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("""
                ## Worktree Merge Status
                
                The child agent worktrees have been merged back to the parent worktree.
                Review the merge status below to determine next steps.
                
                """);
        
        sb.append("**Overall Status**: ");
        sb.append(aggregation.allSuccessful() ? "ALL MERGED SUCCESSFULLY" : "MERGE CONFLICTS DETECTED");
        sb.append("\n\n");
        
        if (aggregation.merged() != null && !aggregation.merged().isEmpty()) {
            sb.append("### Successfully Merged (").append(aggregation.merged().size()).append(")\n");
            for (AgentMergeStatus status : aggregation.merged()) {
                sb.append("- ✓ `").append(status.agentResultId()).append("`\n");
            }
            sb.append("\n");
        }
        
        if (aggregation.conflicted() != null) {
            sb.append("### Conflicted\n");
            AgentMergeStatus conflicted = aggregation.conflicted();
            sb.append("- ✗ `").append(conflicted.agentResultId()).append("`\n");
            if (conflicted.mergeDescriptor() != null && 
                conflicted.mergeDescriptor().conflictFiles() != null) {
                sb.append("  - Conflict files:\n");
                for (String file : conflicted.mergeDescriptor().conflictFiles()) {
                    sb.append("    - `").append(file).append("`\n");
                }
            }
            sb.append("\n");
            sb.append("""
                    **Action Required**: You must decide how to handle this merge conflict.
                    Options include:
                    - Emit a `MergerInterruptRequest` to pause and request human intervention
                    - Emit a `MergerRequest` to trigger conflict resolution
                    - Skip this agent's changes and proceed with others
                    
                    """);
        }
        
        if (aggregation.pending() != null && !aggregation.pending().isEmpty()) {
            sb.append("### Pending (").append(aggregation.pending().size()).append(")\n");
            sb.append("These agents have not been merged yet (merge stopped at first conflict):\n");
            for (AgentMergeStatus status : aggregation.pending()) {
                sb.append("- ○ `").append(status.agentResultId()).append("`\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }

    @Override
    public int priority() {
        // Run after core context contributors but before decision prompts
        return 500;
    }
}
```

**MergeAggregation Record** (with helper methods):

```java
public record MergeAggregation(
        List<AgentMergeStatus> merged,
        List<AgentMergeStatus> pending,
        AgentMergeStatus conflicted
) {
    public MergeAggregation {
        if (merged == null) merged = List.of();
        if (pending == null) pending = List.of();
    }
    
    public boolean allSuccessful() {
        return conflicted == null && pending.isEmpty();
    }
    
    public boolean hasConflict() {
        return conflicted != null;
    }
    
    public int totalCount() {
        return merged.size() + pending.size() + (conflicted != null ? 1 : 0);
    }
}
```

**Integration**: The factory is auto-discovered via Spring's component scanning and automatically included in the prompt contributor chain when building prompts for dispatch agents.
