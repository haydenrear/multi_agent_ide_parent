# Quickstart: Multi-Agent IDE Skill Ergonomics Refactor

**Feature**: `001-multi-agent-ide-skills` | **Date**: 2026-03-13

## Prerequisites

- Java 21 + Gradle (for controller annotation changes in `multi_agent_ide_java_parent`)
- Python 3.11+ (for deploy and filter scripts)
- Git (for clone/pull script)
- Running multi_agent_ide application (for Swagger and API testing)

## Implementation Order

### 1. Consolidate shared request/response types (Java)

Move duplicate `NodeIdRequest`, `RunIdRequest` records to `com.hayden.multiagentide.controller.model` package. Update all controller imports.

### 2. Restructure controller paths to be hierarchical + add springdoc-openapi

Rationalize controller `@RequestMapping` paths so the first segment after `/api/` uniquely identifies the domain (e.g. `/api/runs/...`, `/api/ui/...` instead of `/api/llm-debug/runs/...`). Add `springdoc-openapi-starter-webmvc-ui` to `multi_agent_ide/build.gradle.kts`. Add `@Tag`, `@Operation`, `@Schema` annotations to all controllers.

### 3. Create `deploy_multi_agent_ide` skill

- Create `skills/deploy_multi_agent_ide/SKILL.md` — migrate Deploy operations, Tmp repo persistence, Operating mode from source skill
- Create `scripts/clone_or_pull.py` — new clone/pull script
- Migrate `scripts/deploy_restart.py` from `multi_agent_test_supervisor`

### 4. Create `multi_agent_ide_api` skill

- Create `skills/multi_agent_ide_api/SKILL.md` — migrate Script Catalog, schemas, Supported actions, Prompt locations from source skill
- Create `scripts/api_schema.py` — progressive disclosure script (queries live OpenAPI, returns levels of detail)
- Migrate `scripts/quick_action.py` from `multi_agent_test_supervisor`

### 5. Create `multi_agent_ide_debug` skill (instructional)

- Create `skills/multi_agent_ide_debug/SKILL.md` — migrate Node error triage, Data-Layer Filters, Propagators/Transformers, Context manager recovery, Session identity
- Add log file locations section, triage checklist, and empty "Tips & Learnings" section
- Reference `multi_agent_ide_api` endpoints for runtime state inspection

### 6. Create `multi_agent_ide_controller` skill (executable workflow)

- Create `skills/multi_agent_ide_controller/SKILL.md` — migrate Typical loop, Polling pattern, Program context, Embabel routing, Goal tagging
- Structure as executable workflow: step-by-step loop, testing matrix, polling dynamics, status evaluation
- Add references to the other three skills
- Add self-improvement instruction: agent flags workflow issues to user

### 7. Remove `multi_agent_test_supervisor`

- Delete `skills/multi_agent_test_supervisor/` directory
- Verify no other files reference it

## Validation

1. Start the application → verify Swagger UI at `/swagger-ui.html` shows all endpoints with hierarchical paths
2. Run `api_schema.py` (no args) → verify it returns controller group names and descriptions
3. Run `api_schema.py --controller runs` → verify it returns only runs endpoint names/methods/descriptions
4. Run `api_schema.py --controller runs --path /start` → verify full schema for that endpoint
5. Run `clone_or_pull.py --dry-run` → verify no-op output
6. Load each skill independently → verify it provides sufficient context for its domain
7. Load `multi_agent_ide_controller` → verify it references the other three skills by name
