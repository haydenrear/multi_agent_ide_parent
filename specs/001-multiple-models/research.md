# Phase 0 Research: Runtime ACP Model Selection

## Decision 1: Use a versioned JSON envelope for per-call ACP routing

- **Decision**: Represent runtime routing as a versioned `AcpChatOptionsString` payload containing the session/artifact key, requested model, requested ACP provider, and an open-ended options map.
- **Rationale**: A versioned JSON envelope cleanly carries the current routing fields plus future per-call overrides without changing the call signature again. It also gives asynchronous code a self-contained payload instead of relying on thread-local context.
- **Alternatives considered**:
  - Continue passing separate model and chat/session strings: rejected because it cannot express provider choice or future per-call options cleanly.
  - Add more delimiter-packed string segments: rejected because it becomes fragile, hard to validate, and hard to extend safely.
  - Use thread-local routing state: rejected because the chat flow already crosses async boundaries.

## Decision 2: Preserve the current transport contract by using `sessionKey___jsonPayload`

- **Decision**: Carry the structured routing payload through the current Spring AI model string as `sessionIdentity___serializedJson`, with the JSON repeating the session identity for validation.
- **Rationale**: The current ACP chat model already recovers memory identity by splitting on `___`. Preserving that prefix avoids breaking existing session extraction while allowing the second half to carry richer routing details in one opaque string.
- **Alternatives considered**:
  - Pure JSON only in the model field: rejected because it would require broader changes to all current memory/session parsing paths at once.
  - Keep the current `key___model` layout and store provider elsewhere: rejected because provider resolution must stay attached to the same async-safe routing payload.

## Decision 3: Replace the single flat ACP property set with a provider catalog and default provider

- **Decision**: Refactor ACP configuration to a record-based root configuration that exposes `defaultProvider` plus `providers: Map<String, AcpProviderDefinition>`.
- **Rationale**: Runtime selection only works if the application can resolve more than one named ACP backend at once. A provider catalog makes those choices explicit and allows one default to preserve simple callers.
- **Alternatives considered**:
  - Keep one active ACP property set per Spring profile: rejected because it still requires profile switches or restarts to compare providers.
  - Create one top-level property block per provider family without a map: rejected because it scales poorly and makes dynamic lookup harder.

## Decision 4: Key ACP sessions by resolved routing identity, not only by session/artifact key

- **Decision**: Use a composite session cache key derived from session identity, selected provider, effective model, and any provider-affecting options.
- **Rationale**: Reusing sessions by session key alone would incorrectly share one ACP session across different providers or models, which violates the feature's concurrency and isolation requirements.
- **Alternatives considered**:
  - Keep the current cache key equal to memory/session ID only: rejected because provider/model changes in the same conversation would leak state across ACP backends.
  - Never cache sessions: rejected because it would discard existing continuity behavior and increase session startup cost unnecessarily.

## Decision 5: Resolve sandbox translation and process startup from the selected provider definition

- **Decision**: Derive command, args, working directory, authentication, environment, and sandbox translation lookup from the resolved provider definition for the call rather than from one global ACP command.
- **Rationale**: Different ACP backends may need different startup commands and sandbox behavior. The selected provider must be the source of truth for session creation.
- **Alternatives considered**:
  - Infer provider only from the configured command executable: rejected because the explicit provider name is now part of the call contract.
  - Keep one global sandbox translation and override only model: rejected because backend-specific command and environment differences are central to this feature.

## Decision 6: Keep backward compatibility by translating existing inputs into the new payload

- **Decision**: Build the new routing payload centrally in the LLM integration layer so existing callers that currently provide only session identity and model name can still work through the new contract.
- **Rationale**: This allows incremental migration of upstream callers while concentrating the routing change inside the ACP integration boundary.
- **Alternatives considered**:
  - Require every caller to construct the new payload immediately: rejected because it would widen the change surface and raise rollout risk.
  - Keep separate legacy and new execution paths indefinitely: rejected because dual routing logic would increase maintenance burden and drift.

## Decision 7: Validate and log runtime routing explicitly, but never log secrets

- **Decision**: Add explicit validation for unknown providers, malformed payloads, and missing defaults, and log the resolved session identity, provider, and model without logging credentials or raw secret-bearing environment values.
- **Rationale**: Operators need to verify runtime routing decisions, while the provider catalog may carry sensitive configuration.
- **Alternatives considered**:
  - Minimal logging only on failure: rejected because successful runtime provider switching must also be diagnosable.
  - Log the entire resolved provider definition: rejected because it risks exposing secrets.
