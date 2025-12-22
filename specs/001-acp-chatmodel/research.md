# Research: ACP Model Type Support

## Decision: Model selection configuration

**Decision**: Introduce a model provider selection configuration that supports `acp` and `http` providers for ChatModel and StreamingChatModel.

**Rationale**: The spec requires ACP as a selectable provider without changing workflow behavior. Centralizing provider selection keeps orchestration unchanged while allowing ACP routing.

**Alternatives considered**:
- Hard-code ACP as the only provider (rejected: must support HTTP baseline)
- Add ACP only for specific agents (rejected: requirement is per-run selection)

## Decision: ACP client integration surface

**Decision**: Implement a ChatModel adapter that uses the ACP Kotlin SDK over the JVM and surface it through the existing LangChain4j configuration beans.

**Rationale**: LangChain4j agents already use `ChatModel` and `StreamingChatModel` beans. Providing ACP-backed implementations preserves agent interfaces and lifecycle handling.

**Alternatives considered**:
- Bypass LangChain4j and invoke ACP directly in agents (rejected: would break existing agent wiring)

## Decision: Error handling and interrupts

**Decision**: Map ACP errors to existing failure handling paths and map ACP interrupt requests into the existing interrupt/interaction result schema.

**Rationale**: The system relies on standard events and AgentModels outputs for review gates and failure transitions. Aligning with current result mapping avoids new event types.

**Alternatives considered**:
- Introduce new ACP-specific events (rejected: spec requires no user-visible workflow changes)
