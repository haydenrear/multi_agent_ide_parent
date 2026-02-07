# Contract: CLI event stream rendering (internal)

## Scope

Internal event rendering contract for CLI mode. No new external HTTP or GraphQL API is introduced by this feature. The CLI consumes in-process events emitted by the existing event system and renders them to the terminal.

## Event Envelope (common fields)

All events rendered in the CLI include the following common fields when available:

- `eventId`
- `eventType`
- `timestamp`
- `nodeId`

## Event Categories (rendered in CLI)

The CLI must render events across these categories:

- Node lifecycle (add/update/delete/status changes, errors, review requests, branching/pruning)
- Action lifecycle (action started, action completed)
- Interrupts and resumes
- Permissions (requested/resolved)
- Tool calls
- Worktree events (created/branched/merged/discarded)
- Plan updates and current mode updates
- UI/render events (render, diff applied/rejected/reverted, feedback, state snapshot)
- User message chunks
- Goal completion

## Action Lifecycle Rendering

For action lifecycle events, the CLI must also render a hierarchy context derived from the artifact key semantics of `nodeId`:

- Root identifier
- Depth
- Parent chain (if present)

## Tool Call Rendering

For tool call events, the CLI should render:

- Tool title
- Kind
- Status
- Phase
- Input/output summary (if present)

## Unknown Event Types

If an event type is not recognized, the CLI must render a fallback line that includes:

- `eventType`
- `nodeId`
- Any available metadata fields
