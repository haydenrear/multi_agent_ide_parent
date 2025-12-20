# Research

## Decision: Use existing Spring Boot test harness and event bus wiring

**Rationale**: The module already provides AgentTestBase, TestEventListener, and in-memory repositories that align with event-driven orchestration. Reusing these avoids introducing new test infrastructure and keeps tests aligned with production lifecycle behavior.

**Alternatives considered**:
- Standalone unit tests without Spring context: rejected because lifecycle wiring depends on EventBus subscriptions and AgentEventListener behavior.
- Full integration tests with real WorktreeService: rejected because test scope is event flow and node lifecycle, not Git IO.

## Decision: Mock agent outputs to drive orchestration phases

**Rationale**: AgentRunner transitions rely on agent outputs; mocking preserves deterministic test flows while validating event sequencing and node status updates.

**Alternatives considered**:
- Real LLM calls: rejected due to nondeterminism and external dependency requirements.
- Custom fake agents: rejected since Mockito stubs already standard in the module.
