# Plan: Simplify SessionMode enum and implement ArtifactKey resolution

## Context

The `AiFilterTool.SessionMode` enum has 7 values, several of which are confusing or unused (`SAME_SESSION_FOR_MODEL`, `SAME_SESSION_FOR_MATCH_ON_TYPE`, `SAME_SESSION_FOR_AGENT_TYPE`). The user wants to simplify to 4 clear values and actually implement the session key resolution logic, which currently doesn't exist — `runAiFilter` always creates a fresh child key (PER_INVOCATION behavior) regardless of the configured `sessionMode`.

Since `createChild()` generates a new ULID each time, "same session" modes require **caching** the resolved ArtifactKey. A new `AiFilterSessionResolver` Spring bean will cache keys by scope and listen for lifecycle events (`GoalCompletedEvent`, `ActionCompletedEvent`) to evict stale entries.

## Changes

### 1. Simplify `SessionMode` enum in `AiFilterTool.java` *(DONE)*

**File:** `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/executor/AiFilterTool.java`

```java
public enum SessionMode {
    PER_INVOCATION,
    SAME_SESSION_FOR_ALL,
    SAME_SESSION_FOR_ACTION,
    SAME_SESSION_FOR_AGENT
}
```

### 2. Create `AiFilterSessionResolver` Spring bean

**New file:** `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/AiFilterSessionResolver.java`

A `@Component` that implements `EventListener` (from `com.hayden.acp_cdc_ai.acp.events`).

**Responsibilities:**
- Cache resolved ArtifactKeys keyed by `(policyId, scopeKey)` where `scopeKey` depends on session mode
- On `resolveSessionKey()` call: return cached key if present, otherwise compute + cache
- On `GoalCompletedEvent`: evict all `SAME_SESSION_FOR_ALL` entries whose root matches the goal's nodeId
- On `ActionCompletedEvent`: evict `SAME_SESSION_FOR_ACTION` entries whose action scope matches

```java
@Component
@Slf4j
public class AiFilterSessionResolver implements EventListener {

    // Cache: composite key (policyId + scopeArtifactKey) → resolved session ArtifactKey
    private final ConcurrentHashMap<String, ArtifactKey> sessionCache = new ConcurrentHashMap<>();

    public ArtifactKey resolveSessionKey(String policyId,
                                          AiFilterTool.SessionMode sessionMode,
                                          PromptContext promptContext) {
        ArtifactKey currentKey = promptContext.currentContextId();
        if (currentKey == null) {
            return ArtifactKey.createRoot();
        }

        if (sessionMode == null || sessionMode == AiFilterTool.SessionMode.PER_INVOCATION) {
            return currentKey.createChild();
        }

        String scopeKey = buildScopeKey(policyId, sessionMode, currentKey);
        return sessionCache.computeIfAbsent(scopeKey, k -> currentKey.createChild());
    }

    private String buildScopeKey(String policyId, AiFilterTool.SessionMode mode, ArtifactKey currentKey) {
        return switch (mode) {
            case PER_INVOCATION -> throw new IllegalStateException("PER_INVOCATION handled above");

            case SAME_SESSION_FOR_ALL ->
                // Scope = root key (entire goal/workflow)
                policyId + ":ALL:" + currentKey.root().value();

            case SAME_SESSION_FOR_ACTION -> {
                // Scope = action-level key (depth 2 in hierarchy)
                ArtifactKey actionKey = currentKey;
                while (actionKey.depth() > 2 && actionKey.parent().isPresent()) {
                    actionKey = actionKey.parent().get();
                }
                yield policyId + ":ACTION:" + actionKey.value();
            }

            case SAME_SESSION_FOR_AGENT -> {
                // Scope = agent-level key (parent of current)
                ArtifactKey agentKey = currentKey.parent().orElse(currentKey);
                yield policyId + ":AGENT:" + agentKey.value();
            }
        };
    }

    // --- EventListener implementation ---

    @Override
    public String listenerId() {
        return "AiFilterSessionResolver";
    }

    @Override
    public boolean isInterestedIn(String eventType) {
        return "GOAL_COMPLETED".equals(eventType) || "ACTION_COMPLETED".equals(eventType);
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        switch (event) {
            case Events.GoalCompletedEvent goal -> evictByPrefix(":ALL:" + goal.nodeId());
            case Events.ActionCompletedEvent action -> evictByPrefix(":ACTION:" + action.nodeId());
            default -> {}
        }
    }

    private void evictByPrefix(String suffix) {
        sessionCache.keySet().removeIf(key -> key.contains(suffix));
    }
}
```

**Registration:** The bean implements `EventListener`, so `DefaultEventBus` will auto-inject it via Spring's `List<EventListener>` autowiring.

### 3. Wire `AiFilterSessionResolver` into `FilterExecutionService.runAiFilter`

**File:** `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/FilterExecutionService.java`

Add `@Autowired` field:
```java
@Autowired
private AiFilterSessionResolver aiFilterSessionResolver;
```

Replace the hardcoded contextId in `runAiFilter` (lines ~281-284):
```java
// Before:
.contextId(promptContext.currentContextId() != null
        ? promptContext.currentContextId().createChild()
        : ArtifactKey.createRoot())

// After:
.contextId(aiFilterSessionResolver.resolveSessionKey(
        policy.getRegistrationId(),
        p.executor().sessionMode(),
        promptContext))
```

### 4. Update `PolicyExecutorValidator.java`

**File:** `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/validation/PolicyExecutorValidator.java`

Update `validateAiExecutor` to accept only the 4 new values:
```java
boolean valid = "PER_INVOCATION".equals(sessionMode)
        || "SAME_SESSION_FOR_ALL".equals(sessionMode)
        || "SAME_SESSION_FOR_ACTION".equals(sessionMode)
        || "SAME_SESSION_FOR_AGENT".equals(sessionMode);
```

### 5. Update JSON schema

**File:** `specs/001-data-layer-policy-filter/contracts/filter-policy.schema.json`

```json
"sessionMode": {
  "type": ["string", "null"],
  "enum": ["PER_INVOCATION", "SAME_SESSION_FOR_ALL", "SAME_SESSION_FOR_ACTION", "SAME_SESSION_FOR_AGENT", null],
  "description": "Controls ACP chat-session reuse scope."
}
```

### 6. Update spec docs

- `specs/001-data-layer-policy-filter/data-model.md` — update Section 8 SessionMode table
- `specs/001-data-layer-policy-filter/spec.md` — update FR-029a requirement text

### 7. Update skill docs and scripts

- `skills/multi_agent_test_supervisor/SKILL.md` — update sessionMode values list
- `skills/multi_agent_test_supervisor/quick-actions/register-ai-filter/skill.md` — update example
- `skills/multi_agent_test_supervisor/references/filter_policy_contracts.md` — update enum values
- `skills/multi_agent_test_supervisor/references/filter_action.schema.md` — update enum values
- `skills/multi_agent_test_supervisor/scripts/filter_action.py` — update argparse choices list

### 8. Update tests

**File:** `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/filter/FilterPolicyInfrastructureIT.java`

- Verify test `sessionMode` values use valid new enum values
- Remove stale graph event serdes code if still present

## Files to modify

1. `AiFilterTool.java` — simplify enum *(DONE)*
2. **NEW** `AiFilterSessionResolver.java` — session key cache + event listener
3. `FilterExecutionService.java` — autowire resolver, use in `runAiFilter`
4. `PolicyExecutorValidator.java` — update validation
5. `filter-policy.schema.json` — update enum
6. `data-model.md` — update table
7. `spec.md` — update FR-029a
8. `SKILL.md` — update enum list
9. `register-ai-filter/skill.md` — update example
10. `filter_policy_contracts.md` — update enum
11. `filter_action.schema.md` — update enum
12. `filter_action.py` — update choices
13. `FilterPolicyInfrastructureIT.java` — verify test values

## Verification

1. `get_file_problems` on FilterExecutionService.java, AiFilterTool.java, AiFilterSessionResolver.java
2. Run `./gradlew multi_agent_ide:test --tests "com.hayden.multiagentide.integration.filter.*" -Pprofile=integration`
3. Grep for remaining references to removed enum values
