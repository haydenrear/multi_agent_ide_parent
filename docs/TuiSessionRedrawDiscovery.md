# Unified Discovery: Tui Session Redraw Metering

## Architecture overview
- `TuiSession` is the CLI controller owned by the Spring Shell `cli` profile. It wires `TerminalUI` once, registers itself with the shared `EventBus`, and drives `TuiTerminalView` through a snapshot-based `TuiState` feed. The `TuiStateReducer` keeps the session/event history and focus information in-memory, while `ViewController` relays terminal key events back into Graph events (session selection, chat input, focus toggles).
- The redraw goal is to stop calling `requestRedraw()` on every incoming service (`GraphEvent`) dispatch, but keep modal/focus-driven updates (modals in `setModalView`, detail scrolls in `ViewController.moveSelection`, etc.) instantaneous.

## Key modules and components
- `TuiSession` (`multi_agent_ide_java_parent/.../tui/TuiSession.java`) owns the lifecycle: terminal setup, GraphEvent listener registration, state reduction, modal coordination, and redraw requests via `terminalUi.redraw()`.
- `TuiTerminalView` (`.../tui/TuiTerminalView.java`) composes Spring Shell grid views (`GridView`, `MenuView`, `DialogView`) and delegates user input to `Controller`. It also captures dynamic layout hints (event list height, modal configuration) via callbacks from `TuiSession`.
- `ViewController` nested in `TuiSession` maps keyboard/controller actions to Graph events and is currently one of the places where `requestRedraw()` is invoked after detail scrolls or modal focus changes.
- `TuiStateReducer` maintains the CLI model, including `TuiSessionState` per node, chat history, focus, detail views, and event selection indexes.
- `CliEventFormatter` and `EventStreamRepository` provide formatted event rows and persistence for Graph events and are injected into `TuiTerminalView` so it can display chat/event columns.
- Service infrastructure: `EventBus`/`Events.GraphEvent` cycle, `IPermissionGate` for permission/interrupt requests, and `GoalStarter` for new sessions.

## Data flow and dependencies
1. External services publish `Events.GraphEvent` through `EventBus`, which passes them to `TuiSession` because it implements `EventListener` (`listenerId()` = `cli-tui-session`).
2. On each Graph event (`onEvent`), `TuiSession` synchronizes on `stateLock`, reduces the new event via `TuiStateReducer`, resolves the current session, and optionally queues interaction replies (permissions, interrupts, chat input). The `eventListHeight` consumer, session ordering, and focus are updated before releasing the lock.
3. After event reduction, `TuiSession` currently calls `requestRedraw()` unconditionally (target for metering) and forwards `Events.AddMessageEvent` back into the `EventBus` if needed. This path should be rate-limited to avoid repaint storms while keeping modal/focus redraws instant.
4. UI interactions go through `TuiTerminalView`: it reads `TuiState` snapshots, builds menu/session cells, and exposes modal/detail views. Interaction callbacks (`height -> eventListHeight = ...`, `setModalView`, `configureDynamicView`) tightly link the view layout to `TuiSession` state.
5. The modal/focus path (`setModalView`, `ViewController.moveSelection` detail scroll, `publishInteraction`, etc.) bypasses the GraphEvent throttling; these should continue to call `requestRedraw()` immediately.
6. `CliEventFormatter`/`EventStreamRepository` add dependency on formatted rows and persisted event history so that `TuiTerminalView` can display both live and historical entries.

## Integration points
- `EventBus` subscribers include `TuiSession` as an `EventListener` and the repository-backed `TestEventBus` (used in CLI tests).
- `TerminalUI` (Spring Shell) renders `TuiTerminalView`, handles focus, and displays modal dialogs via `setModalView`.
- `ViewController` and `TuiTerminalView` share callbacks for event navigation, session creation, focus toggling, and detail scrolling, all of which ultimately publish Graph events back through `EventBus`.
- `IPermissionGate` hooks into `TuiSession` to resolve pending permissions/interrupts using CLI text input.
- Goal management (`GoalStarter`) and session creation manipulate `startedSessions` and `sessionOrder` within `TuiSession` before feeding the next Graph event.

## Technology stack summary
- **Language/Platform**: Java 21, Spring Boot 3.x, Lombok.
- **CLI/Terminal**: Spring Shell `TerminalUI`, `GridView`, `DialogView`, JLine `Terminal` for sizing.
- **Event/Agent infrastructure**: Custom `EventBus`/`Events.GraphEvent`, Embabel agent scaffolding (implied by imports), LangChain4j agents (per AGENTS.md), and permission gating via `IPermissionGate`.
- **Persistence/Repo**: `EventStreamRepository`/`InMemoryEventStreamRepository` store Graph events; `CliEventFormatter` shapes rows.
- **Testing**: `@ShellTest` integration harness with mock `OrchestrationController`, `EnvConfigProps`, and `EventStreamRepository` plus `ShellTestClient` for UI assertions (see `CliTuiShellLargeTerminalTest`, `CliTuiShellNarrowTerminalTest`, `CliTuiShellFlowTest`).

## Test patterns and conventions
- `@ShellTest` with custom `terminalWidth/Height` parameterizes layout coverage for large/narrow terminals.
- Each test replaces production dependencies with mocks (`OrchestrationController`, `EnvConfigProps`, `EventStreamRepository`, `IPermissionGate`) via `@TestConfiguration` and `@Primary` beans.
- `TestEventBus` reuses `EventStreamRepository` and proxies publish/subscriptions while saving events for assertions.
- Tests use `ShellTestClient.NonInteractiveShellSession` to drive the CLI and `Awaitility` loops to wait for `TuiSession.snapshotForTests()` states.
- Context cleanup uses `@DirtiesContext` per test to reset the Spring context.

## Critical files and entry points
- `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/tui/TuiSession.java` (main CLI controller, event listener, redraw gate).
- `.../tui/TuiTerminalView.java` (view layout, key bindings, session menu, modal support).
- `.../tui/TuiSessionView.java` and `TuiSessionMenuView.java` (session-specific grid rendering).
- `.../tui/TuiStateReducer.java`, `TuiState.java`, and `TuiSessionState.java` (CQRS-like model reduction and immutable state).
- `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliModeConfig.java` (Spring config that wires CLI beans, potentially the entrypoint for `TuiSession`).
- `com.hayden.acp_cdc_ai.acp.events.EventBus` and `Events` (event primitives used across service + CLI boundaries).
- `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/cli/CliTuiShellLargeTerminalTest.java` (shell-based regression coverage and `requestRedraw()` expectations).

Metering `requestRedraw()` should target the GraphEvent listener path inside `TuiSession.onEvent(Events.GraphEvent)` while leaving `setModalView` and controller-driven redraws immediate to keep modal transitions and focus-based scrolls snappy.
