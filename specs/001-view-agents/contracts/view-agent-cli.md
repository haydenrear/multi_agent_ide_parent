# CLI Contract: view-agent (view_agent_exec package)

The `view-agent` CLI runs inside the `view-agent-exec:latest` Docker container and processes queries against a target view path using Ollama.

## Commands

### `view-agent query`

Process a query against a target view.

```
view-agent query [OPTIONS] QUERY

Arguments:
  QUERY                  The query or task to process (required)

Options:
  --view PATH            Path to the target view inside the container (required; `/repo/views/<name>` in view mode, `/repo/views` in root mode)
  --model TEXT           Ollama model name (required, e.g. "llama3.2")
  --ollama-url TEXT      Ollama API URL (default: http://host.docker.internal:11434)
  --mode [view|root]     Operating mode (default: view)
  --timeout INTEGER      Query timeout in seconds (default: 120)
  --skip-refresh         Skip staleness check (for testing only)
```

**Behavior**:
1. If `--mode view`: `--view` points to one repository view under `/repo/views/<name>`.
2. If `--mode root`: `--view` points to `/repo/views`.
3. Staleness check runs first (unless `--skip-refresh`):
   - Reads `mental-models/HEAD`.
   - In `view` mode:
     - compares recorded content hashes against current hashes for curated source files
     - compares recorded `view_script_hash` against the current hash of that view's `regen.py`
     - if the view script hash changed, all sections in that view are flagged for review
   - In `root` mode:
     - compares recorded child head metadata IDs against current child heads
     - checks whether any referenced child sections are stale
     - does not check a root `regen.py`, because the root has no script
   - If stale sections are found, researches changed inputs via Ollama, updates the mental model through the same atomic helper flow used by `view-model`, and creates a new metadata file.
4. Loads the now-current mental model and target view files.
5. Constructs a prompt and queries Ollama.
6. Outputs structured JSON to stdout.

**Exit codes**:
- `0`: Success
- `1`: General error
- `2`: Ollama connection error (model not available or service unreachable)
- `3`: View path invalid or missing

**JSON output schema**: See `view-agent-response.schema.json`

---

## Docker Invocation (via skill scripts)

### Per-View Invocation (Phase 1)

The `multi_agent_ide_view_agent_exec` skill script `query_view.py` invokes:

```bash
docker run --rm \
  -v /path/to/repo:/repo:ro \
  -v /path/to/repo/views/<view-name>/mental-models:/repo/views/<view-name>/mental-models:rw \
  view-agent-exec:latest \
  query --view /repo/views/<view-name> --model llama3.2 "Explain the API architecture"
```

The repository is mounted read-only at `/repo` so relative symlinks inside the view resolve correctly. Only the target `mental-models/` directory is mounted read-write.

### Root Invocation (Phase 2)

The `multi_agent_ide_view_agent_exec` skill script `query_root.py` invokes:

```bash
docker run --rm \
  -v /path/to/repo:/repo:ro \
  -v /path/to/repo/views/mental-models:/repo/views/mental-models:rw \
  view-agent-exec:latest \
  query --view /repo/views --model llama3.2 --mode root "Synthesize findings across all views"
```

### Two-Phase Fan-Out

The `multi_agent_ide_view_agent_exec` skill script `fan_out.py` orchestrates:
1. Detects all view directories under `views/`.
2. Launches one `query_view.py` per view in parallel (subprocess per container).
3. Waits for all to complete (with configurable timeout).
4. Launches `query_root.py` for root synthesis.
5. Returns consolidated JSON output.
