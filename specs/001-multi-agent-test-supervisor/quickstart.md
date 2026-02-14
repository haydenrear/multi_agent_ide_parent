# Quickstart: LLM Debug UI Skill and Shared UI Abstraction

## Purpose

Deliver debugging-stage capabilities only:

1. Shared UI abstraction with TUI/LLM parity
2. Script-first AI interface in `skills`
3. Deploy/start/poll/inspect/action loop for debugging
4. Embedded routing + prompt architecture references for in-loop prompt edits

## Prerequisites

- Branch: `001-multi-agent-test-supervisor`
- Java 21 + Gradle wrapper
- Python 3 available for skill scripts
- Docker CLI available on PATH for integration tests (`PATH="/Users/hayde/.docker/bin:$PATH"`)
- No existing process expected on `8080` (script will terminate if present)

## Step 1: Create the new skill package

```bash
mkdir -p skills/multi_agent_test_supervisor/{references,scripts,assets}
```

Add `skills/multi_agent_test_supervisor/SKILL.md` based on `skills/skills_template/template/SKILL.md` and document:

- What each script does
- Required args/options
- Output format (JSON)
- Common failure modes and retries
- Prompt file locations and prompt extension architecture references
- Routing/model source anchors (`AgentInterfaces`, `AgentModels`, `BlackboardRoutingPlanner`)
- Context-manager and memory source anchors (`ContextManagerTools`, `BlackboardHistory`)

## Step 2: Add script wrappers (AI interface)

Implement one script per operation (names can vary but should be explicit), e.g.:

- `deploy_restart.py`
- `quick_action.py`
- `ui_state.py`
- `ui_action.py`

Script requirements:

- argument validation
- machine-readable JSON output
- non-zero exit code on failure

## Step 3: Implement shared UI abstraction in app

Add interface-neutral contracts and execution path in:

- `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide`

Ensure TUI and LLM adapters call the same abstraction for UI actions and state mutation.

## Step 4: Add/align LLM debug endpoints

Implement endpoints per `contracts/llm-debug-ui-openapi.yaml` for:

- goal start convenience
- quick actions (`START_GOAL`, `SEND_MESSAGE`)
- node-scoped event polling and event detail expansion
- UI state snapshot and actions
- SSE event stream (optional for live view)

## Step 5: Validate parity and debug loop

1. Execute equivalent action sequences via TUI and LLM endpoints.
2. Confirm resulting state snapshots are semantically equivalent.
3. Execute debug loop end-to-end:
   - deploy/restart
   - start goal
   - poll events
   - inspect event detail
   - inspect UI state
   - apply message/UI action
4. For loop/stall behavior, use skill references to:
   - classify subgraph position (top-level vs Discovery/Planning/Ticket),
   - interpret routing resolution (first non-null route field -> next action input type),
   - inspect context-manager recovery/memory behavior.
5. Use prompt architecture references to locate and edit prompt templates/contributors, then redeploy.

## Step 6: Run tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :multi_agent_ide_java_parent:multi_agent_ide:compileJava :multi_agent_ide_java_parent:multi_agent_ide:compileTestJava
python3 -m unittest skills/multi_agent_test_supervisor/tests/test_scripts.py
```

## Step 7: Smoke-check scripts

Examples:

```bash
python3 skills/multi_agent_test_supervisor/scripts/deploy_restart.py --mode bootrun
python3 skills/multi_agent_test_supervisor/scripts/quick_action.py start-goal --goal "debug graph routing" --repo /path/to/repo
python3 skills/multi_agent_test_supervisor/scripts/quick_action.py poll-events --node-id <node-id> --limit 20
python3 skills/multi_agent_test_supervisor/scripts/quick_action.py event-detail --node-id <node-id> --event-id <event-id>
python3 skills/multi_agent_test_supervisor/scripts/ui_state.py --node-id <node-id>
python3 skills/multi_agent_test_supervisor/scripts/ui_action.py --node-id <node-id> --action CHAT_INPUT_CHANGED --payload '{"text":"continue"}'
python3 skills/multi_agent_test_supervisor/scripts/quick_action.py send-message --node-id <node-id> --message "continue"
```

## Completion Criteria

- Shared UI abstraction drives both TUI and LLM interaction behavior.
- Script catalog is discoverable from `SKILL.md` with clear args/output contracts.
- Debug loop (deploy -> start -> poll -> inspect -> act -> redeploy) is operational.
- Program/routing reference docs are present and linked from `SKILL.md`.
- Prompt file locations and prompt architecture references are present and linked from `SKILL.md`.
