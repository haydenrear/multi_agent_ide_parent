# Data Model: ACP Model Type Support

## Entity: ModelSelection

- **Purpose**: Selects the chat model provider for a run.
- **Fields**:
  - `provider`: enum (`acp`, `http`)
  - `useStreamingModel`: boolean
  - `metadata`: key/value map for provider-specific overrides

## Entity: ACP Chat Request

- **Purpose**: Encapsulates a chat request sent to ACP.
- **Fields**:
  - `conversationId`: identifier for the conversation
  - `messages`: ordered list of message entries
  - `requestMetadata`: provider-specific metadata (timeouts, routing hints)

## Entity: ACP Chat Response

- **Purpose**: Captures the ACP response for agent consumption.
- **Fields**:
  - `output`: response text payload
  - `interruptsRequested`: list of interrupt types
  - `responseMetadata`: provider-specific metadata

## Relationships

- `ModelSelection` drives which chat model implementation serves agent requests.
- `ACP Chat Request` and `ACP Chat Response` are used when `ModelSelection.provider = acp`.

## Validation Rules

- `provider` must be one of `acp` or `http`.
- `messages` must be non-empty for chat requests.
- `interruptsRequested` defaults to empty if ACP does not return interrupts.
