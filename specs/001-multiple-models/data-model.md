# Data Model: Runtime ACP Model Selection

## 1. AcpChatOptionsString

Purpose: The structured per-call routing payload carried through the LLM invocation path.

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `version` | string | Yes | Schema version for safe parsing and future evolution. |
| `sessionArtifactKey` | string | Yes | Canonical conversation/artifact identity used for memory and event routing. |
| `requestedModel` | string | No | Model explicitly requested for this call. |
| `requestedProvider` | string | No | ACP provider name explicitly requested for this call. |
| `options` | map<string, object> | No | Open-ended call-scoped options that do not belong in static provider config. |

### Validation Rules

- `version` must be present and supported.
- `sessionArtifactKey` must be non-blank and parseable as the existing session/artifact identity format.
- At least one of `requestedProvider` or configured default provider must resolve successfully.
- `requestedModel` may be omitted only when the selected provider can supply a valid default behavior.
- `options` must be JSON-serializable and must not override reserved top-level fields.

### State Transitions

1. Constructed from prompt/session inputs.
2. Serialized into the Spring AI model transport field.
3. Parsed and validated before ACP session resolution.
4. Resolved into an `AcpResolvedCall` for session creation or reuse.

## 2. AcpProviderDefinition

Purpose: A reusable named ACP backend definition selected at runtime.

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Stable provider key used by callers and configuration lookup. |
| `transport` | string | Yes | Connection transport for the ACP backend. |
| `command` | string | Conditional | Process command for provider-backed session startup when using local process execution. |
| `args` | string | No | Provider-specific startup arguments. |
| `workingDirectory` | string | No | Provider-specific working directory override. |
| `endpoint` | string | Conditional | Remote endpoint when the provider requires network connection instead of local process startup. |
| `authMethod` | string | No | Authentication mode identifier. |
| `apiKeyRef` | string | No | Reference to credential source, if applicable. |
| `env` | map<string, string> | No | Additional environment variables required by the provider. |
| `defaultModel` | string | No | Provider-level default model behavior for calls that omit a model. |

### Validation Rules

- `name` must be unique in the provider catalog.
- Required fields depend on the selected transport.
- Secret-bearing values must come from secure configuration sources and must never be logged in resolved form.
- Definitions are immutable after resolution for a running call.

## 3. AcpProviderCatalog

Purpose: The root configuration object that exposes runtime-selectable ACP backends.

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `defaultProvider` | string | No | Provider name used when the call payload omits `requestedProvider`. |
| `providers` | map<string, AcpProviderDefinition> | Yes | All named ACP backends available to the application. |

### Validation Rules

- `providers` must contain at least one entry.
- `defaultProvider`, when present, must reference an existing provider definition.
- Unknown provider lookups must fail with a validation error.

## 4. AcpResolvedCall

Purpose: The validated execution-time routing object produced from payload + provider catalog.

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sessionArtifactKey` | string | Yes | Canonical session identity for memory, events, and chat routing. |
| `providerName` | string | Yes | Resolved ACP provider used for the call. |
| `effectiveModel` | string | Yes | Final model value after explicit request/default resolution. |
| `providerDefinition` | AcpProviderDefinition | Yes | Provider settings frozen for the lifetime of the call. |
| `options` | map<string, object> | No | Additional call-scoped options carried forward after validation. |

### Validation Rules

- Must exist before ACP session creation begins.
- Must not contain unresolved provider or model choices.
- Must be safe to share across async boundaries.

## 5. AcpSessionRoutingKey

Purpose: The cache/reuse identity for ACP sessions.

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sessionArtifactKey` | string | Yes | Base conversation/artifact identity. |
| `providerName` | string | Yes | Selected ACP provider. |
| `effectiveModel` | string | Yes | Final model used by the provider. |
| `optionsFingerprint` | string | No | Stable digest of provider-affecting options if those options change session behavior. |

### Validation Rules

- Equal routing keys must represent ACP sessions safe to reuse.
- Different providers or effective models must produce different routing keys.
- Provider-affecting options must influence the key; purely observational options must not.

## Relationships

- One `AcpProviderCatalog` contains many `AcpProviderDefinition` entries.
- One `AcpChatOptionsString` resolves to exactly one `AcpResolvedCall`.
- One `AcpResolvedCall` produces exactly one `AcpSessionRoutingKey`.
- Many LLM invocations may reuse the same `AcpSessionRoutingKey` when all routing-defining fields match.
