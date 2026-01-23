# Research Plan: Context Manager Agent

## Unknowns & Clarifications

### 1. StuckHandler Interface
**Status**: RESOLVED
**Context**: The spec requires `WorkflowAgent` to implement `StuckHandler` to catch `DegenerateLoopException` and hung states.
**Findings**: `StuckHandler` is an existing interface in `com.embabel.agent.api.common` within the `embabel-agent` library.
**Plan**: Implement `com.embabel.agent.api.common.StuckHandler` in `WorkflowAgent`.

### 2. EventBus Integration
**Status**: RESOLVED
**Context**: `BlackboardHistory` needs to subscribe to `EventBus`.
**Findings**: `EventSubscriber` interface exists in `commit-diff-context`. `EventBus` exists in `utilitymodule`.
**Plan**: Implement `EventSubscriber` in `BlackboardHistory`.

### 3. ContextManagerRequest Structure
**Status**: RESOLVED
**Context**: Need a request model for Context Manager.
**Findings**: `AgentModels` contains `AgentRequest` sealed interface. We need to add `ContextManagerRequest` to `AgentModels` and the permit list.
**Plan**: Add `ContextManagerRequest` record to `AgentModels`.

## Decisions

### Decision 1: StuckHandler Location
**Decision**: Use existing `com.embabel.agent.api.common.StuckHandler`.
**Rationale**: Avoid duplication; leveraging platform capability.

### Decision 2: ContextManagerRequest Design
**Decision**: Implement as a record in `AgentModels` implementing `AgentRequest`.
**Rationale**: Consistent with other agent requests (`OrchestratorRequest`, etc.).

### Decision 3: BlackboardHistory Tools
**Decision**: Implement tools as methods in `BlackboardHistory` or a separate `BlackboardTools` class?
**Rationale**: Spec says "Context Manager... with BlackboardHistory tools". We will implement them as a separate service or utility class `BlackboardTools` that operates on `BlackboardHistory` to keep the data class clean, or extend `BlackboardHistory` functionality. Given `BlackboardHistory` is a record/class mixture, adding methods to `BlackboardHistory` or an inner/sibling class seems appropriate.

## Alternatives Considered
- Modifying `EventBus` to push to `BlackboardHistory` directly? No, subscription model is better.
- Using existing `InterruptRequest` for StuckHandler? No, spec asks for specific escalation.
