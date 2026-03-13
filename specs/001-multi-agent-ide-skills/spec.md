# Feature Specification: Multi-Agent IDE Skill Ergonomics Refactor

**Feature Branch**: `001-multi-agent-ide-skills`
**Created**: 2026-03-13
**Status**: Draft

## Context

The existing `multi_agent_test_supervisor` skill (`skills/multi_agent_test_supervisor/SKILL.md`) is the single source of truth for deploying, operating, debugging, and controlling multi_agent_ide workflows. It has grown large and covers too many concerns in one file. This feature refactors it into four focused skills that replace it entirely.

The existing skill contains the following reusable content that will be migrated (not rewritten from scratch) into the new skills:

- **Deploy operations**, `Tmp repo persistence`, and `Operating mode** → `deploy_multi_agent_ide`
- **Script Catalog** (quick-agent ops, filter ops, propagation ops, transformation ops, UI ops), **Script schemas**, **UI request/response schema**, **Supported actions**, and **Prompt locations** → `multi_agent_ide_api`
- **Node error triage**, **Data-Layer Filters**, **Propagators and Transformers**, **Context manager recovery model**, and **Session identity model** → `multi_agent_ide_debug`
- **Typical loop**, **Polling pattern**, **Program context**, **Embabel action routing semantics**, and **Goal tagging policy** → `multi_agent_ide_controller`

All four new skill files will live in the root `skills/` directory (e.g. `skills/deploy_multi_agent_ide/SKILL.md`), alongside the existing skills. Once the four new skills are complete and validated, `skills/multi_agent_test_supervisor/` will be removed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Clone, Sync, Validate, and Deploy multi_agent_ide to Tmp (Priority: P1)

An operator uses a script to either clone a fresh copy or sync an existing copy of the repo in `/tmp`, with full submodule handling. After clone or sync, the script runs a **verification gate** that validates the repo is in a clean, deployable state — all submodules on `main`, no detached HEADs, no uncommitted changes, SHAs matching what was pushed from the source repo. If verification fails, the script returns a structured error telling the AI agent exactly what is wrong and what actions to take. Only after verification passes does it proceed to deploy.

**Why this priority**: Deployment is the prerequisite for every other workflow. The most common failure mode is a dirty or desynced repo state (detached HEAD, stale submodule SHAs, local changes that conflict with pull). The verification gate catches these before they become opaque build failures.

**Independent Test**: Run the script against a clean `/tmp` directory for clone, then intentionally desync a submodule and run the sync path — confirm the verification gate catches it and returns an actionable error.

**Acceptance Scenarios**:

1. **Given** no existing clone exists in `/tmp`, **When** the script is run, **Then** it clones the repo with `--recurse-submodules`, runs `git submodule foreach --recursive 'git reset --hard || true'` to ensure clean submodule working trees, creates the executor `bin` directory, runs the verification gate, and returns a clean-state result.
2. **Given** an existing clone exists in `/tmp`, **When** the script is run to sync, **Then** it switches all repos/submodules to `main`, pulls with `--ff-only`, resets submodules, and runs the verification gate before proceeding to deploy.
3. **Given** the existing clone has local uncommitted changes that would cause pull to fail, **When** the script is run to sync, **Then** the verification gate detects the dirty state and returns a structured error listing which repos/submodules have uncommitted changes, with suggested remediation actions (e.g. "run `git checkout -- .` in submodule X" or "use `--force-reclone` for a clean slate").
4. **Given** a submodule is in detached HEAD state or not on `main`, **When** the verification gate runs, **Then** it reports which submodule is detached and suggests `git switch main`.
5. **Given** the verification gate passes, **When** deployment proceeds, **Then** it starts the application and confirms health, returning the log file paths, build log path, PID, and active filter policies in the result.
6. **Given** the application is already running, **When** the script is run with `--dry-run`, **Then** it reports what would be done without making changes.

---

### User Story 2 - Progressive API Discovery via Swagger (Priority: P2)

An operator uses a Python script that queries the live Swagger/OpenAPI endpoint and returns progressively deeper levels of detail about the API. At the shallowest level, the operator sees only controller group names and descriptions. They can then drill into a specific controller to see its endpoint names and descriptions, and further into a specific endpoint to see full request/response schemas. Nothing about the endpoints is hardcoded in the script or the skill — the script reads the OpenAPI JSON and the hierarchical URL path structure provides the filtering. The controller endpoints themselves are restructured to use hierarchical paths that make progressive discovery natural.

**Why this priority**: With many controllers and endpoints, dumping the full schema is overwhelming. Progressive disclosure lets the operator (or an AI agent) start broad and drill down only into what's relevant, keeping context windows small and focused.

**Independent Test**: Start the application, run the script at each granularity level, confirm the output narrows correctly at each step.

**Acceptance Scenarios**:

1. **Given** the application is running, **When** the script is run with no filter arguments, **Then** it returns only the top-level controller group names and their one-line descriptions (e.g. "runs — Debug run lifecycle", "agents — Agent control actions").
2. **Given** the application is running, **When** the script is run with a controller group name (e.g. `--controller runs`), **Then** it returns only the endpoint names, HTTP methods, and one-line descriptions within that controller — no request/response schemas.
3. **Given** the application is running, **When** the script is run with a controller and endpoint path (e.g. `--controller runs --path /start`), **Then** it returns the full request/response schema for that specific endpoint.
4. **Given** the application is running, **When** the script is run with `--all`, **Then** it returns the complete schema for all endpoints (escape hatch for full dump).
5. **Given** the controllers are annotated, **When** the OpenAPI JSON is generated, **Then** the URL path structure is hierarchical (e.g. `/api/runs/...`, `/api/agents/...`) so that path-prefix filtering naturally groups endpoints by controller domain.
6. **Given** multiple controllers expose overlapping request types, **When** the Swagger schema is generated, **Then** consolidated canonical types are used so no duplicate shapes exist.

---

### User Story 3 - Debug Using Best-Practice Instructions (Priority: P3)

An operator loads the `multi_agent_ide_debug` skill and follows its instructions to triage errors — using the log and build file paths provided by the deploy script output, referencing API endpoints from the api skill for event/state inspection, and following a documented triage checklist. The skill is an instructional guide, not a script-heavy tool.

**Why this priority**: Debugging is the most time-consuming activity. Clear, structured instructions with known log file locations and triage patterns reduce context-switching and speed up resolution.

**Independent Test**: Load the debug skill, follow the triage checklist for a simulated node error, and confirm the instructions correctly point to log file locations, relevant API endpoints, and common resolution patterns.

**Acceptance Scenarios**:

1. **Given** the debug skill is loaded, **When** an operator encounters a node error, **Then** the skill provides the exact log file paths (runtime log, build log, ACP errors log) and instructions for searching them.
2. **Given** the deploy script has run successfully, **When** the operator needs to debug, **Then** the deploy script output includes the log and build file paths that the debug skill references.
3. **Given** the debug skill is loaded, **When** the operator needs to inspect runtime state, **Then** the skill references the relevant API endpoints (events, workflow-graph) from `multi_agent_ide_api` rather than duplicating endpoint details.
4. **Given** a new debug pattern is discovered during use, **When** the operator wants to record it, **Then** the skill includes a "Tips & Learnings" section designed to be updated over time with new triage patterns.

---

### User Story 4 - Controller Skill as Executable Workflow (Priority: P2)

An AI agent loads the `multi_agent_ide_controller` skill and follows its workflow instructions to validate, test, and improve the application. The skill is a living, executable document — not a static reference card. It contains the "typical loop" (a step-by-step workflow the agent walks through), the testing matrix (unit, integration, full pipeline with durations and timeouts), key features and caveats to remember about that testing matrix, and workflows that produce results (deploy → start goal → poll → resolve permissions → triage errors → redeploy). The skill references the other three sub-skills for the mechanics of each step. Critically, the skill instructs the AI agent: if you discover a problem with the workflow or a way to improve it, surface it to the user — they decide whether to update the skill.

**Why this priority**: The controller skill is what the AI agent actually executes during active ticket work. It's the operational playbook. If the workflow is wrong or incomplete, everything downstream suffers.

**Independent Test**: Load the controller skill, walk through its workflow end-to-end on a real deployment, and confirm each step produces the expected result and references the correct sub-skill.

**Acceptance Scenarios**:

1. **Given** the controller skill is loaded, **When** an AI agent begins a session, **Then** the skill provides a step-by-step workflow (the "typical loop") that the agent can follow — including push/sync, deploy, start goal, poll, resolve permissions/interrupts, triage errors, and redeploy.
2. **Given** the controller skill is loaded, **When** the agent needs to run tests before deploying, **Then** the skill includes the testing matrix with test suite names, approximate durations, and recommended bash timeouts.
3. **Given** the controller skill is loaded, **When** the agent needs to deploy, query endpoints, or debug, **Then** the skill references `deploy_multi_agent_ide`, `multi_agent_ide_api`, and `multi_agent_ide_debug` respectively for the mechanics.
4. **Given** the agent discovers a workflow issue (e.g. a missing step, a step that doesn't work, or a better ordering), **When** the agent encounters it during execution, **Then** the skill instructs the agent to flag it to the user with a concrete suggestion, and the user decides whether to update the skill.
5. **Given** the controller skill is loaded, **When** the agent needs to understand polling dynamics, **Then** the skill explains what to check at each polling interval, how to evaluate status (progressing / stalled / failed), and how to distinguish between a real stall and an agent that's waiting for input.

---

### Edge Cases

- What happens when the `/tmp` clone directory is corrupt or has conflicts? The verification gate must detect this and return a structured error with remediation steps; `--force-reclone` must be available as a nuclear option.
- What happens when a submodule pull fails because of local changes? The verification gate must detect the dirty submodule and list the specific files/repos affected, suggesting `git checkout -- .` or `--force-reclone`.
- What happens when a submodule is in detached HEAD state? The verification gate must detect this and suggest `git switch main` for the specific submodule.
- What happens when SHAs in the tmp clone don't match what was pushed from the source repo? The verification gate must report the mismatch for parent + each submodule, with the expected vs actual SHAs.
- What happens when the application fails to start within the health-check timeout? The deploy script must exit with a non-zero code and print the last 50 log lines.
- What happens when the OpenAPI JSON is unavailable (application not started)? The filter script must return a clear error, not a Python traceback.
- What happens when a log file does not exist yet? The debug skill instructions should note that missing log files mean the application hasn't started or the path is wrong, and point back to the deploy skill.
- What happens when multiple controllers define duplicate request record types? The merged schema must deduplicate and use a single canonical type.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A script MUST clone the multi_agent_ide repo to `/tmp` or `/private/tmp` when no local copy exists, using `--recurse-submodules` and then `git submodule foreach --recursive 'git reset --hard || true'` to ensure clean submodule working trees. It MUST also create the executor `bin` directory (`<tmp-repo>/multi_agent_ide_java_parent/multi_agent_ide/bin`).
- **FR-002**: A script MUST sync an existing clone by switching all repos/submodules to `main`, pulling with `--ff-only`, and resetting submodules. If the pull fails due to local changes, it MUST NOT silently proceed — it MUST return a structured error.
- **FR-003**: After clone or sync, a **verification gate** MUST run that checks: (a) no uncommitted changes in parent or any submodule, (b) all repos/submodules are on `main` (not detached HEAD), (c) SHAs are consistent. The gate MUST return a structured result with per-repo status. If any check fails, it MUST return actionable error messages telling the AI agent what is wrong and how to fix it.
- **FR-004**: The deploy script MUST start the application and confirm health only after the verification gate passes. It MUST support a `--dry-run` flag and a `--force-reclone` flag that deletes the existing clone and starts fresh.
- **FR-005**: All controller endpoints MUST be annotated for OpenAPI/Swagger documentation, including request/response schema descriptions.
- **FR-006**: Controller endpoints MUST use hierarchical URL paths (e.g. `/api/runs/...`, `/api/agents/...`, `/api/filters/...`) so that path-prefix grouping naturally maps to controller domains. Existing endpoints MUST be restructured where needed to achieve this hierarchy.
- **FR-007**: A Python script MUST query the live OpenAPI endpoint and support progressive disclosure at multiple granularity levels: (1) controller group names + descriptions only, (2) endpoint names + methods + descriptions within a controller, (3) full request/response schema for a specific endpoint, (4) full dump of all endpoints. The script MUST NOT hardcode any endpoint or controller names — all information comes from the live OpenAPI JSON.
- **FR-008**: Overlapping request/response record types across controllers MUST be consolidated so the OpenAPI schema uses a single canonical shape per concept.
- **FR-009**: The `multi_agent_ide_debug` skill MUST document the exact log file paths (runtime log, build log, ACP errors log) and best-practice instructions for searching them.
- **FR-010**: The `multi_agent_ide_debug` skill MUST reference API endpoints from `multi_agent_ide_api` (events, workflow-graph) for runtime state inspection rather than duplicating endpoint details.
- **FR-011**: The `multi_agent_test_supervisor` skill MUST be split into four standalone skills: `deploy_multi_agent_ide`, `multi_agent_ide_api`, `multi_agent_ide_debug`, `multi_agent_ide_controller`.
- **FR-012**: The `multi_agent_ide_controller` skill MUST reference the other three skills by name rather than duplicating their content.
- **FR-013**: The `multi_agent_ide_api` skill MUST include the Swagger URL, the filter script usage, quick-action command examples, and best practices for interacting with the API.
- **FR-014**: The `multi_agent_ide_debug` skill MUST include log file locations, a triage checklist for common error patterns, and a "Tips & Learnings" section for accumulating new debug patterns over time.
- **FR-015**: The `deploy_multi_agent_ide` skill MUST include clone/pull script usage, health-check instructions, and the deploy script `--dry-run` workflow.
- **FR-016**: Each new skill MUST be placed at `skills/<skill-name>/SKILL.md` in the root skills directory, consistent with the layout of existing skills.
- **FR-017**: Content from `multi_agent_test_supervisor/SKILL.md` MUST be migrated into the appropriate new skill rather than rewritten from scratch, preserving existing script references, schemas, and instructions.
- **FR-018**: Once all four new skills are complete, `skills/multi_agent_test_supervisor/` MUST be removed so it no longer exists as an active skill.

### Key Entities

- **Deploy Script**: Python script that clones or syncs the repo to `/tmp` (with full submodule handling), runs a verification gate to ensure clean/deployable state, then starts the application and validates health. Returns structured errors with remediation actions when verification fails.
- **Debug Skill**: Instructional guide with log file locations, triage checklists, references to API endpoints for state inspection, and a living "Tips & Learnings" section.
- **OpenAPI Progressive Disclosure Script**: Python script that queries the live OpenAPI endpoint and returns information at configurable granularity levels — from controller group summaries down to full endpoint schemas. Hardcodes nothing about the API; all structure comes from the OpenAPI JSON and hierarchical URL paths.
- **Skill File**: Markdown file (`SKILL.md`) loaded by the AI agent to scope its available actions and instructions for a given domain. Lives at `skills/<name>/SKILL.md`.
- **Controller Endpoint**: A Spring REST endpoint exposed by one of the multi_agent_ide controllers, documented via OpenAPI annotations.
- **Source Skill**: The existing `multi_agent_test_supervisor` skill — the origin of all migrated content. Removed at the end of this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can deploy the application to `/tmp` from scratch in under 3 minutes using a single script command.
- **SC-002**: An operator can go from "what controllers exist?" to "what's the full schema for this endpoint?" in three script invocations or fewer, each completing in under 5 seconds.
- **SC-003**: An operator can locate the correct log file and find relevant error lines for a given node ID within 60 seconds by following the debug skill instructions.
- **SC-004**: Loading the `multi_agent_ide_controller` skill gives an operator a complete polling loop workflow that references sub-skills rather than duplicating content — verified by the absence of duplicated instruction blocks across skill files.
- **SC-005**: The OpenAPI schema has zero duplicate request/response type definitions for equivalent concepts (e.g., a single `NodeIdRequest` shape used across all endpoints that accept a node ID).
- **SC-006**: Each of the four split skills can be loaded independently and provides sufficient context to complete its specific task domain without loading the others.
