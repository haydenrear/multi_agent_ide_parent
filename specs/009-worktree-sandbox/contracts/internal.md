# Internal Contracts: Worktree Sandbox

## Header Contract
- **Header**: `X-AG-UI-SESSION` (MCP_SESSION_HEADER)
- **Purpose**: Resolve RequestContext from RequestContextRepository
- **Value**: `ArtifactKey.value()` / request `contextId`

## Tooling Sandbox Contract
- **Applies to**: AcpTooling file operations (read/write/edit)
- **Input**:
  - `sessionId` injected via `@SetFromHeader(MCP_SESSION_HEADER)`
  - `path` supplied by tool invocation
- **Behavior**:
  - Normalize path (absolute + normalized)
  - Allow only if within main worktree or allowed submodule worktrees
- **Failure Response**:
  - JSON serialized `SandboxValidationResult` with `allowed=false` and `reason`
  - No exceptions for sandbox violations

## Provider Translation Contract
- **Applies to**: AcpChatModel command creation
- **Input**: `RequestContext` from repository
- **Output**: Provider-specific env vars + CLI args for sandboxing
- **Registry**: `SandboxTranslationRegistry` maps provider → strategy
