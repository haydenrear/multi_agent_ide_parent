# Research: Multi-Agent IDE Skill Ergonomics Refactor

**Feature**: `001-multi-agent-ide-skills` | **Date**: 2026-03-13

## Existing Skill Structure

**Decision**: Migrate content from `multi_agent_test_supervisor/SKILL.md` section-by-section into the four new skills. Do not rewrite.

**Rationale**: The existing skill has been battle-tested in production. Its script catalog, schemas, and polling loop reflect real-world usage. Rewriting risks losing hard-won operational detail. Section mapping is documented in [spec.md](./spec.md#context).

**Alternatives considered**: Rewrite from scratch — rejected because knowledge encoded in the current SKILL.md (especially the Typical Loop, node error triage, and filter instruction set) is too detailed and operationally validated to risk losing in a rewrite.

---

## OpenAPI / Swagger Integration

**Decision**: Use `springdoc-openapi` 2.x with `@Tag`, `@Operation`, `@ApiResponse`, and `@Schema` annotations. Add `springdoc-openapi-starter-webmvc-ui` to the build.

**Rationale**: springdoc-openapi is the de-facto standard for Spring Boot 3.x OpenAPI generation. It auto-generates the JSON at `/v3/api-docs` and the Swagger UI at `/swagger-ui.html`. Annotation-based approach keeps documentation co-located with code.

**Alternatives considered**:
- springfox — deprecated, not compatible with Spring Boot 3.x
- Separate OpenAPI YAML maintained by hand — rejected because it would immediately diverge from actual endpoint signatures

**Endpoint tag groupings** (determined by reviewing all controllers):

| Tag | Controllers |
|-----|-------------|
| `runs` | `LlmDebugRunsController` |
| `ui` | `LlmDebugUiController`, `UiController` |
| `agents` | `AgentControlController` |
| `orchestrator` | `OrchestrationController` |
| `events` | `EventStreamController` |
| `interrupts` | `InterruptController` |
| `permissions` | `PermissionController` |
| `prompts` | `LlmDebugPromptsController` |
| `filters` | `FilterPolicyController` |
| `propagation` | `PropagatorController`, `PropagationController`, `PropagationRecordController` |
| `transformation` | `TransformerController`, `TransformationRecordController` |

**Schema consolidation findings**: Several controllers define local `record` types with identical or near-identical shapes. The following consolidations are required:

| Canonical Type | Duplicate Locations | Action |
|----------------|--------------------|----|
| `NodeIdRequest` | `LlmDebugUiController`, `LlmDebugRunsController` | Move to shared package, reference from both |
| `RunIdRequest` | `LlmDebugRunsController`, `OrchestrationController` area | Move to shared package |
| `StartGoalRequest` | `OrchestrationController` (canonical), referenced in `LlmDebugUiController` + `LlmDebugRunsController` | Already shares `OrchestrationController.StartGoalRequest` — confirm all callers use it |
| `ControlActionRequest/Response` | `AgentControlController` — appears unique | No change needed |

---

## Clone/Sync/Verify Deploy Script

**Decision**: Single Python script `clone_or_pull.py` with three phases: (1) clone or sync, (2) verify, (3) delegate to `deploy_restart.py`. The verification gate is the critical addition — it catches the most common deployment failure modes before they become opaque build errors.

**Phase 1 — Clone or Sync**:
- If no clone exists: `git clone --recurse-submodules <url> <path> --branch main`, then `git submodule foreach --recursive 'git reset --hard || true'`, then `mkdir -p <tmp-repo>/multi_agent_ide_java_parent/multi_agent_ide/bin` (executor cwd required by external filters).
- If clone exists: `git switch main`, `git pull --ff-only origin main`, `git submodule foreach --recursive 'git switch main || true'`, `git submodule foreach --recursive 'git pull --ff-only origin main || true'`, `git submodule foreach --recursive 'git reset --hard || true'`.
- If `--force-reclone`: delete existing clone directory entirely, then run clone path.

**Phase 2 — Verification Gate** (runs after clone OR sync, always, cannot be skipped):
1. `git status --short` + `git submodule foreach --recursive 'git status --short || true'` — detect uncommitted changes
2. Verify all repos/submodules are on `main` (not detached HEAD) — `git symbolic-ref --short HEAD` for each
3. Collect SHAs: `git rev-parse --short HEAD` for parent + each key submodule — return in result so the AI can compare against what was pushed from the source repo

If any check fails, the script returns a structured JSON error:
```json
{
  "ok": false,
  "phase": "verify",
  "failures": [
    {
      "repo": "multi_agent_ide_java_parent",
      "issue": "detached_head",
      "detail": "HEAD is at abc1234, not on any branch",
      "remediation": "cd multi_agent_ide_java_parent && git switch main"
    },
    {
      "repo": ".",
      "issue": "dirty_files",
      "detail": "M CLAUDE.md",
      "remediation": "git checkout -- CLAUDE.md  OR  use --force-reclone for clean slate"
    }
  ]
}
```

This structured error format is designed to be actionable by an AI agent — it can parse the failures and execute the remediation commands directly.

**Phase 3 — Deploy**: Delegate to `deploy_restart.py --project-root <tmp-repo>` for boot + health-check.

**Rationale**: The existing SKILL.md already has a "Pre-deploy verification gate (required, do not skip)" section with manual bash commands. The most common deployment failures are: detached HEAD in a submodule, stale SHAs after a push, local changes blocking `--ff-only` pulls, and missing `bin` directory for external filters. Encoding this in the script removes the possibility of skipping it and provides machine-readable errors.

**Key flags**:
- `--repo-url` — git remote URL (required if cloning)
- `--tmp-dir` — override target directory (default: `/tmp/multi_agent_ide` or `/private/tmp/multi_agent_ide` on macOS)
- `--dry-run` — print actions without executing
- `--force-reclone` — delete existing clone and re-clone from scratch
- `--verify-only` — run only the verification gate (skip clone/sync and deploy)
- `--project-root` — override project root for deploy_restart.py

---

## Progressive API Discovery Script

**Decision**: Python script `api_schema.py` that queries the live OpenAPI endpoint (`/v3/api-docs`) and returns progressively deeper levels of detail. Nothing about the API is hardcoded — the script reads the OpenAPI JSON and uses the hierarchical URL path structure to group and filter.

**Rationale**: Dumping the full OpenAPI schema is overwhelming for both human operators and AI agents working in limited context windows. Progressive disclosure lets the caller start broad ("what controller groups exist?") and drill into only the relevant slice ("show me the full schema for POST /api/runs/start"). The hierarchical URL path structure is what makes this self-documenting — the script doesn't need to know what controllers exist, it just groups by path prefix.

**Design — granularity levels**:

| Level | Flag | Output |
|-------|------|--------|
| 0 — Groups | (default, no args) | Controller group names + one-line descriptions, derived from top-level path segments (e.g. `/api/runs` → "runs") |
| 1 — Endpoints | `--controller <name>` | Endpoint names, HTTP methods, one-line descriptions within that controller group |
| 2 — Detail | `--controller <name> --path <subpath>` | Full request/response schema for one specific endpoint |
| 3 — Full dump | `--all` | Complete schema for all endpoints (escape hatch) |

**Key flags**:
- `--url` — base URL of running app (default: `http://localhost:8080`)
- `--controller` — filter to a specific controller group (derived from path prefix)
- `--path` — drill into a specific endpoint path within the controller
- `--all` — dump everything
- `--format` — `table` (default), `json`, `curl`

**Hierarchical URL path restructuring**: The existing controllers use mixed path patterns (e.g. `/api/llm-debug/ui`, `/api/llm-debug/runs`, `/api/agents`, `/api/orchestrator`). These must be rationalized into a clean hierarchy where the first path segment after `/api/` uniquely identifies the controller domain. The script then groups by this segment.

| Current Path | Proposed Path | Group |
|-------------|---------------|-------|
| `/api/llm-debug/ui/...` | `/api/ui/...` | `ui` |
| `/api/llm-debug/runs/...` | `/api/runs/...` | `runs` |
| `/api/agents/...` | `/api/agents/...` | `agents` |
| `/api/orchestrator/...` | `/api/orchestrator/...` | `orchestrator` |
| `/api/events/...` | `/api/events/...` | `events` |
| (interrupts) | `/api/interrupts/...` | `interrupts` |
| (permissions) | `/api/permissions/...` | `permissions` |
| (prompts) | `/api/prompts/...` | `prompts` |
| (filter policies) | `/api/filters/...` | `filters` |
| (propagation) | `/api/propagation/...` | `propagation` |
| (transformation) | `/api/transformation/...` | `transformation` |

---

## Debug Skill (Instructional — No Python Script)

**Decision**: The `multi_agent_ide_debug` skill is an instructional guide, not a script-based tool. It documents log file paths, triage checklists, references API endpoints (from `multi_agent_ide_api`) for state inspection, and includes a living "Tips & Learnings" section.

**Rationale**: The deploy script already outputs the log and build file paths on completion. A separate log-search Python script would duplicate what `grep`/`rg` already do well. The real value is in structured triage instructions, known error patterns, and a place to accumulate debug tips over time.

**Known log file paths** (documented in the skill, sourced from project memory):
- Build/Gradle: `/tmp/multi_agent_test_supervisor/multi_agent_ide.log`
- Runtime: `<project-root>/multi-agent-ide.log`
- ACP errors: `<project-root>/multi_agent_ide_java_parent/multi_agent_ide/claude-agent-acp-errs.log`

**Key sections in the debug skill**:
- Log file locations and what each log contains
- Triage checklist for common error patterns (node stalls, ACP errors, context manager recovery)
- References to `multi_agent_ide_api` endpoints (events, workflow-graph) for runtime state inspection
- "Tips & Learnings" section — designed to be updated over time with new patterns

---

## Controller Skill (Executable Workflow Document — No Scripts)

**Decision**: The `multi_agent_ide_controller` skill is a living, executable workflow document — not a static reference card. It contains the step-by-step "typical loop" that the AI agent walks through during active ticket work, the testing matrix (unit, integration, full pipeline with durations and timeouts), key caveats about that testing matrix, and workflows that produce results (deploy → start goal → poll → resolve permissions → triage errors → redeploy). It references the other three skills for the mechanics of each step.

**Rationale**: The controller skill is the operational playbook the AI agent actually executes. It mirrors how an operator works — they start a session by loading controller (which gives the workflow), then selectively load the sub-skills as they need to deploy, query the API, or triage errors. The value is not in static intervals or a "tips" section, but in the executable workflow itself: a set of instructions that, when followed, validate, test, and improve the application. Critically, the skill instructs the agent: if you discover a workflow issue or improvement, surface it to the user — they decide whether to update the skill.

**Key sections in the controller skill**:
- Typical loop — step-by-step workflow the AI walks through (push/sync, deploy, start goal, poll, resolve permissions/interrupts, triage errors, redeploy)
- Testing matrix with test suite names, approximate durations, and recommended bash timeouts
- Key features and caveats to remember about the testing matrix
- Polling dynamics — what to check at each interval, how to evaluate status (progressing / stalled / failed), distinguishing real stalls from agents waiting for input
- Program context and Embabel action routing semantics
- Goal tagging policy
- References to other skills: `deploy_multi_agent_ide`, `multi_agent_ide_api`, `multi_agent_ide_debug`
- Self-improvement instruction — the skill explicitly tells the agent to flag workflow issues or improvements to the user

---

## Skill Split Strategy

**Decision**: Four skills with strict content boundaries. `multi_agent_ide_controller` is the entry-point skill that explicitly instructs the agent to load the others. Two skills (`deploy_multi_agent_ide`, `multi_agent_ide_api`) include Python scripts; two skills (`multi_agent_ide_debug`, `multi_agent_ide_controller`) are document-only — `debug` is an instructional guide, `controller` is an executable workflow document.

**Content ownership** (no duplication allowed):

| Content | Owner Skill | Has Scripts? |
|---------|-------------|--------------|
| Clone/pull/deploy script, health checks, `deploy_restart.py` usage, `--dry-run` | `deploy_multi_agent_ide` | Yes |
| Script catalog, quick_action.py, filter_action.py, UI schema, Swagger URL, api_schema.py (progressive disclosure), endpoint quick-reference | `multi_agent_ide_api` | Yes |
| Node error triage, log file locations, Data-Layer Filters, Propagators/Transformers, context manager recovery, session identity, triage checklist, Tips & Learnings | `multi_agent_ide_debug` | No (instructional) |
| Typical loop (executable workflow), testing matrix, polling dynamics, program context, Embabel routing, goal tagging, `workflow-graph` command, self-improvement instruction | `multi_agent_ide_controller` | No (executable workflow) |
