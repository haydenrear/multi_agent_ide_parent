# Data Model: Multi-Agent IDE Skill Ergonomics Refactor

**Feature**: `001-multi-agent-ide-skills` | **Date**: 2026-03-13

## Overview

This feature does not introduce new persistent data entities. The primary artifacts are:
- **Skill files** (Markdown, on-disk)
- **Python scripts** (CLI tools, no persistent state)
- **OpenAPI annotations** (compile-time metadata on existing Java controllers)
- **Consolidated request/response types** (Java records, in-memory only)

---

## Consolidated Request/Response Types

These are the canonical Java record types that controllers will share after schema consolidation. Moving or creating these types is a prerequisite for adding OpenAPI annotations without schema duplication.

### `NodeIdRequest`
**Location**: `com.hayden.multiagentide.controller` shared package (or `com.hayden.multiagentide.controller.model`)
**Fields**:
- `nodeId: String` — the ArtifactKey value identifying a workflow node

**Current duplicates to remove**: local record in `LlmDebugUiController`, local record in `LlmDebugRunsController`

---

### `RunIdRequest`
**Location**: shared controller model package
**Fields**:
- `runId: String` — unique run identifier

**Current duplicates to remove**: local record in `LlmDebugRunsController`

---

### `StartGoalRequest` *(already canonical in `OrchestrationController`)*
**Location**: `OrchestrationController.StartGoalRequest` — no move needed
**Fields**:
- `goal: String`
- `repositoryUrl: String`
- `baseBranch: String`
- `title: String`
- `tags: List<String>`

**Validation**: `goal` and `repositoryUrl` must be non-blank.

---

### `ControlActionRequest` *(already unique to `AgentControlController`)*
**Fields**:
- `nodeId: String`
- `message: String` (optional)

**No change needed.**

---

### `ControlActionResponse` *(already unique)*
**Fields**:
- `actionId: String`
- `status: String` — e.g. `"queued"`, `"rejected"`

---

## Script Interface Contracts

These are not Java types — they define the CLI interface for each new Python script.

### `clone_or_pull.py` (Clone / Sync / Verify / Deploy)

| Argument | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `--repo-url` | string | if cloning | — | Git remote URL |
| `--tmp-dir` | string | no | `/tmp/multi_agent_ide` | Target clone/pull directory |
| `--dry-run` | flag | no | false | Print actions, do not execute |
| `--force-reclone` | flag | no | false | Delete existing clone and re-clone from scratch |
| `--verify-only` | flag | no | false | Run only the verification gate (skip clone/sync and deploy) |
| `--project-root` | string | no | derived from `--tmp-dir` | Override for deploy_restart.py |

**Three-phase execution**: (1) clone or sync repo + submodules, (2) verification gate, (3) delegate to `deploy_restart.py`.

**Verification gate checks** (Phase 2, always runs, cannot be skipped):
- No uncommitted changes in parent or any submodule
- All repos/submodules on `main` (not detached HEAD)
- SHA collection for parent + submodules (returned in result for comparison)

**Exit codes**: `0` = success, `1` = clone/sync failed, `2` = verification gate failed (structured errors returned), `3` = health-check failed

---

### `api_schema.py` (Progressive Disclosure)

| Argument | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `--url` | string | no | `http://localhost:8080` | Base URL of running app |
| `--controller` | string | no | — | Filter to a controller group (derived from path prefix, e.g. `runs`, `agents`) |
| `--path` | string | no | — | Drill into a specific endpoint path within the controller |
| `--all` | flag | no | false | Dump full schema for all endpoints |
| `--format` | choice | no | `table` | `table`, `json`, or `curl` |

**Granularity behavior**:
- No args → Level 0: controller group names + descriptions only
- `--controller <name>` → Level 1: endpoint names, methods, descriptions within that group
- `--controller <name> --path <subpath>` → Level 2: full request/response schema for that endpoint
- `--all` → Level 3: everything

**Exit codes**: `0` = success, `1` = app unreachable, `2` = no endpoints matched filter

---

## Skill File Structure

Each skill file follows the same frontmatter + section pattern:

```markdown
---
name: <skill-name>
description: <one-line description>
---

## Scope
## <domain-specific sections>
## Out of scope
```

**Ownership mapping** (no content duplication across skill files):

| Section | Skill |
|---------|-------|
| Clone/pull script, deploy_restart.py, health-check, dry-run, tmp persistence | `deploy_multi_agent_ide` |
| Script catalog, schemas, quick actions, Swagger URL, api_schema.py (progressive disclosure), endpoint quick-reference, prompt locations | `multi_agent_ide_api` |
| Node error triage, log file locations, Data-Layer Filters, Propagators/Transformers, context manager recovery, session identity, Tips & Learnings | `multi_agent_ide_debug` (instructional — no scripts) |
| Typical loop (executable workflow), testing matrix, polling dynamics, program context, Embabel routing, goal tagging, workflow-graph command, self-improvement instruction | `multi_agent_ide_controller` (executable workflow — no scripts) |
