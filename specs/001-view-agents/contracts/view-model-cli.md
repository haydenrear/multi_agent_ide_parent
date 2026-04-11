# CLI Contract: view-model & view-custody (view_agents_utils package)

The `view-model` and `view-custody` CLIs run inside the `view-agents-utils:latest` Docker container and provide helper commands for interacting with mental models and traversing the chain of custody. No Ollama dependency.

## view-model Commands

### `view-model show`

Render the current mental model with staleness annotations.

```
view-model show [OPTIONS] MENTAL_MODEL_PATH

Arguments:
  MENTAL_MODEL_PATH        Path to the mental model markdown file (required)

Options:
  --repo PATH              Repository root for git hash resolution (default: /repo)
  --format [markdown|json] Output format (default: markdown)
```

**Output** (markdown format, view-level):
```markdown
# Architecture Overview [CURRENT]

References:
- src/main/java/api/UserController.java (current)
- src/main/java/models/User.java (current)

[mental model content for this section...]

# Data Flow [STALE]

References:
- src/main/java/services/DataService.java (stale — hash changed)

[mental model content for this section...]
```

**Output** (json format, view-level):
```json
{
  "mental_model": "mental-models.md",
  "model_scope": "view",
  "view_script_status": "current",
  "sections": [
    {
      "section_path": "Architecture Overview",
      "status": "current",
      "offset": 0,
      "content": "...",
      "references": [
        {"type": "source_file", "path": "src/main/java/api/UserController.java", "status": "current"},
        {"type": "source_file", "path": "src/main/java/models/User.java", "status": "current"}
      ]
    }
  ]
}
```

**Output** (json format, root-level):
```json
{
  "mental_model": "mental-models.md",
  "model_scope": "root",
  "view_script_status": "not_applicable",
  "sections": [
    {
      "section_path": "Cross-View Data Flow",
      "status": "stale",
      "offset": 0,
      "content": "...",
      "references": [
        {
          "type": "child_model",
          "view_name": "api-module",
          "child_section_paths": ["Architecture Overview > Data Flow"],
          "status": "stale"
        }
      ]
    }
  ]
}
```

**Guarantees**: No metadata file paths or internal structure exposed in output. `view_script_status` is `current` or `stale` for view-level models, and `not_applicable` for the root model because the root has no `regen.py`.

---

### `view-model files`

List curated references for the mental model.

```
view-model files [OPTIONS] MENTAL_MODEL_PATH

Arguments:
  MENTAL_MODEL_PATH        Path to the mental model markdown file (required)

Options:
  --section-path TEXT      Filter to a specific section path
  --repo PATH              Repository root for git hash resolution
  --format [text|json]     Output format (default: text)
```

**Behavior**:
- For view-level models, returns source file references.
- For the root model, returns child mental-model references.

**Output** (text format, view-level):
```
CURRENT  src/main/java/api/UserController.java
CURRENT  src/main/java/models/User.java
STALE    src/main/java/services/DataService.java
```

**Output** (text format, root-level):
```
CURRENT  api-module :: Architecture Overview > Data Flow
STALE    core-lib :: Runtime Behavior
```

---

### `view-model update`

Update mental model content and section references.

```
view-model update [OPTIONS] MENTAL_MODEL_PATH

Arguments:
  MENTAL_MODEL_PATH        Path to the mental model markdown file (required)

Options:
  --content FILE           Path to file containing updated markdown content (required)
  --refs FILE              Path to JSON file listing section references (required)
  --repo PATH              Repository root for git hash resolution
```

**Refs file formats** (`refs.json`):

View-level mental model:
```json
[
  {
    "section_path": "Architecture Overview",
    "source_files": [
      {"path": "src/main/java/api/UserController.java"},
      {"path": "src/main/java/models/User.java", "line_span": {"start_line": 1, "line_count": 50}}
    ]
  }
]
```

Root mental model:
```json
[
  {
    "section_path": "Cross-View Data Flow",
    "child_models": [
      {
        "view_name": "api-module",
        "child_section_paths": ["Architecture Overview > Data Flow"]
      }
    ]
  }
]
```

**Validation**:
- Each entry is keyed by `section_path`, not leaf heading text.
- Each entry MUST contain exactly one of `source_files` or `child_models`.
- View-level models may only use `source_files`.
- Root-level models may only use `child_models`.

**Behavior**:
1. Reads current HEAD metadata file.
2. Determines model scope from the metadata chain or model path.
3. For view-level models, reads the current hash of that view's `regen.py`.
4. Creates a staged metadata file with new DiffEntries, supersedes links, hashes, and snapshots.
5. Atomically commits the update under a per-mental-model lock:
   - writes the updated markdown content
   - installs the new metadata file and snapshots
   - flips `HEAD` to the new metadata file
6. On failure, the prior markdown file and `HEAD` remain intact.

**Exit codes**:
- `0`: Success (prints new metadata file ID)
- `1`: General error
- `5`: Referenced file or child model not found
- `7`: Invalid refs payload for the model scope

---

### `view-model add-ref`

Add a curated reference to an existing section.

```
view-model add-ref [OPTIONS] MENTAL_MODEL_PATH

Arguments:
  MENTAL_MODEL_PATH        Path to the mental model markdown file (required)

Options:
  --section-path TEXT      Section path to add the reference to (required)
  --file PATH              Source file path (relative to repo root)
  --child-view TEXT        Child view name
  --child-section-path TEXT
                           Child section path to reference (repeatable)
  --line-span TEXT         Optional source span "start_line:line_count" for --file
  --repo PATH              Repository root
```

**Validation**:
- Exactly one reference kind is allowed per invocation: `--file` or `--child-view`.
- `--file` is valid only for view-level models.
- `--child-view` is valid only for the root model.

**Behavior**: Creates a new metadata file with an expanded curated set for the specified section. Mental-model content is not modified.

---

### `view-model add-section`

Add a new section to the mental model with uniqueness validation.

```
view-model add-section [OPTIONS] MENTAL_MODEL_PATH

Arguments:
  MENTAL_MODEL_PATH        Path to the mental model markdown file (required)

Options:
  --heading TEXT           Section heading text (required)
  --parent TEXT            Parent section path to nest under (optional — top-level if omitted)
  --content FILE           Path to file containing section content (required)
  --refs FILE              Path to JSON file listing section references for this section (required)
  --repo PATH              Repository root for git hash resolution
```

**Behavior**:
1. Computes the full section path: `<parent> > <heading>` (or just `<heading>` if top-level).
2. Validates the section path is unique within the mental model — rejects with exit code `6` if duplicate.
3. Appends the section to the mental-model markdown file at the appropriate heading depth.
4. Creates a new metadata file with a DiffEntry for the new section (no supersedes pointer — it is a new chain).
5. Commits the markdown + metadata update atomically.

**Exit codes**:
- `0`: Success (prints new metadata file ID)
- `1`: General error
- `6`: Duplicate section path
- `7`: Invalid refs payload for the model scope

---

### `view-model status`

Report staleness status without rendering full content.

```
view-model status [OPTIONS] MENTAL_MODEL_PATH

Arguments:
  MENTAL_MODEL_PATH        Path to the mental model markdown file (required)

Options:
  --repo PATH              Repository root for git hash resolution
  --format [text|json]     Output format (default: text)
```

**Output** (text format, view-level):
```
MODEL SCOPE: VIEW
VIEW SCRIPT: CURRENT
CURRENT  Architecture Overview (offset 0)
STALE    Architecture Overview > Data Flow (offset 1)
CURRENT  Error Handling (offset 2)
```

**Output** (text format, root-level):
```
MODEL SCOPE: ROOT
VIEW SCRIPT: NOT_APPLICABLE
CURRENT  System Overview (offset 0)
STALE    Cross-View Data Flow (offset 1)
```

---

## view-custody Commands

### `view-custody search`

Traverse and query the metadata file chain.

```
view-custody search [OPTIONS] MENTAL_MODEL_PATH

Arguments:
  MENTAL_MODEL_PATH        Path to the mental model markdown file (required)

Options:
  --section-path TEXT      Filter to a specific section path
  --file PATH              Filter by source file path
  --child-view TEXT        Filter by child view name (root-level chains only)
  --child-section-path TEXT
                           Filter by child section path within a child view's mental model (root-level chains only; narrows ChildModelRef matches)
  --hash-range TEXT        Filter by source-file content-hash range "from..to" (view-level chains only)
  --date-range TEXT        Filter by date range "YYYY-MM-DD..YYYY-MM-DD"
  --max-depth INTEGER      Maximum chain depth to traverse (default: unlimited)
  --format [text|json]     Output format (default: json)
```

**Behavior**: Reads the current `HEAD` metadata file, follows `supersedes` pointers backward through the chain, applies filters, and returns matching metadata-file summaries with snapshot references. For root-level chains, child view and child section filters apply instead of source-file hash filters.

---

## Docker Invocation (via skill scripts)

The `multi_agent_ide_view_agents` skill scripts invoke the container.

**Read-only operations**:
```bash
docker run --rm \
  -v /path/to/repo:/repo:ro \
  view-agents-utils:latest \
  view-model show /repo/views/api-module/mental-models/mental-models.md
```

**Write operations**:
```bash
docker run --rm \
  -v /path/to/repo:/repo:ro \
  -v /path/to/repo/views/api-module/mental-models:/repo/views/api-module/mental-models:rw \
  view-agents-utils:latest \
  view-model update /repo/views/api-module/mental-models/mental-models.md \
    --content /repo/tmp/updated-content.md \
    --refs /repo/tmp/refs.json
```

**Search**:
```bash
docker run --rm \
  -v /path/to/repo:/repo:ro \
  view-agents-utils:latest \
  view-custody search /repo/views/api-module/mental-models/mental-models.md \
    --section-path "Architecture Overview > Data Flow"
```
