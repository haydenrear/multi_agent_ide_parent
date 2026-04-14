# CLI Contract: view-agent (view_agent_exec package)

The `view-agent` CLI runs inside the `view-agent-exec:latest` Docker container and processes queries against a target view path using Ollama. The container is built on `python-with-docker`, which includes the Docker CLI so the agent can invoke `view-agents-utils` containers via `docker run` to use `view-model` and `view-custody` skill scripts as tools.

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
  --repo PATH            Repository root (default: /repo)
  --skill-dir PATH       Path to skill directory with SKILL.md and scripts
```

**Behavior**:
1. If `--mode view`: `--view` points to one repository view under `/repo/views/<name>`.
2. If `--mode root`: `--view` points to `/repo/views`.
3. Constructs a prompt that provides the agent with:
   - The view directory and repository root paths
   - The skill directory (if `--skill-dir` provided) so the agent can reference `view-model` and `view-custody` CLI scripts as tools
   - Instructions to use `view-model status` to detect staleness, `view-model show` to read the mental model, and `view-model update` to write changes
   - The user's query
4. Queries Ollama with the constructed prompt.
5. The agent uses the skill scripts as tools to inspect, refresh, and update mental models as part of its natural reasoning — there is no separate hardcoded refresh step.
6. Outputs structured JSON to stdout.

**Exit codes**:
- `0`: Success
- `1`: General error
- `2`: Ollama connection error (model not available or service unreachable)
- `3`: View path invalid or missing

**JSON output schema**: See `view-agent-response.schema.json`

---

## Docker Image

The `view-agent-exec` image is built on `python-with-docker` (from `runner_code`), which extends the `python` base image with the Docker CLI. This allows the agent to invoke `view-agents-utils` containers via `docker run` for `view-model` and `view-custody` operations.

**Image chain**: `ubuntu → jdk → python → python-with-docker → view-agent-exec`

At runtime, the host Docker socket must be mounted so the container can launch sibling containers:

```bash
-v /var/run/docker.sock:/var/run/docker.sock
```

---

## Docker Invocation (via skill scripts)

### Per-View Invocation (Phase 1)

The `multi_agent_ide_view_agent_exec` skill script `query_view.py` invokes:

```bash
docker run --rm \
  -v /path/to/repo:/repo:ro \
  -v /path/to/repo/views/<view-name>/mental-models:/repo/views/<view-name>/mental-models:rw \
  -v /var/run/docker.sock:/var/run/docker.sock \
  view-agent-exec:latest \
  query --view /repo/views/<view-name> --model llama3.2 --repo /repo "Explain the API architecture"
```

The repository is mounted read-only at `/repo` so relative symlinks inside the view resolve correctly. Only the target `mental-models/` directory is mounted read-write. The Docker socket is mounted so the agent can invoke `view-agents-utils` containers as tools.

### Root Invocation (Phase 2)

The `multi_agent_ide_view_agent_exec` skill script `query_root.py` invokes:

```bash
docker run --rm \
  -v /path/to/repo:/repo:ro \
  -v /path/to/repo/views/mental-models:/repo/views/mental-models:rw \
  -v /var/run/docker.sock:/var/run/docker.sock \
  view-agent-exec:latest \
  query --view /repo/views --model llama3.2 --mode root --repo /repo "Synthesize findings across all views"
```

### Two-Phase Fan-Out

The `multi_agent_ide_view_agent_exec` skill script `fan_out.py` orchestrates:
1. Detects all view directories under `views/`.
2. Launches one `query_view.py` per view in parallel (subprocess per container).
3. Waits for all to complete (with configurable timeout).
4. Launches `query_root.py` for root synthesis.
5. Returns consolidated JSON output.
