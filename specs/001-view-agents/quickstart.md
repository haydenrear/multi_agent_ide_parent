# Quickstart: View Agents

**Branch**: `001-view-agents` | **Date**: 2026-04-10

## Prerequisites

- Docker installed and running
- Ollama installed and running locally with at least one model pulled (e.g., `ollama pull llama3.2`)
- Python 3.11+ (for local development; not needed for Docker-only usage)
- Git repository to analyze

## 1. Build the Docker Images

```bash
cd multi_agent_ide_python_parent/packages

# Build the utils image (metadata, custody, helper scripts)
cd view_agents_utils
docker build -t view-agents-utils:latest -f docker/Dockerfile .
cd ..

# Build the exec image (Ollama query execution)
cd view_agent_exec
docker build -t view-agent-exec:latest -f docker/Dockerfile .
cd ..
```

## 2. Generate Views for a Repository

View generation is done by agent-generated Python scripts, not a generic tool. Each view directory gets its own `regen.py` — scripts are per-view, not per-repo. The `multi_agent_ide_view_generation` skill instructs agents to create these per-view scripts.

For a sample repo, each view would have its own `regen.py`:

**`views/api-module/regen.py`**:
```python
#!/usr/bin/env python3
"""Regenerate the api-module view."""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

def regenerate():
    (VIEW_DIR / "mental-models").mkdir(exist_ok=True)
    for pattern in ["src/main/java/api/**/*.java", "src/main/java/models/**/*.java"]:
        for path in REPO_ROOT.glob(pattern):
            link = VIEW_DIR / path.relative_to(REPO_ROOT)
            link.parent.mkdir(parents=True, exist_ok=True)
            if not link.exists():
                # IMPORTANT: Use relative symlinks so views work inside Docker
                # containers where the repo is mounted at a different path.
                rel_target = os.path.relpath(path, link.parent)
                link.symlink_to(rel_target)

if __name__ == "__main__":
    regenerate()
```

**`views/core-lib/regen.py`**:
```python
#!/usr/bin/env python3
"""Regenerate the core-lib view."""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

def regenerate():
    (VIEW_DIR / "mental-models").mkdir(exist_ok=True)
    for pattern in ["src/main/java/util/**/*.java", "src/main/java/core/**/*.java"]:
        for path in REPO_ROOT.glob(pattern):
            link = VIEW_DIR / path.relative_to(REPO_ROOT)
            link.parent.mkdir(parents=True, exist_ok=True)
            if not link.exists():
                # IMPORTANT: Use relative symlinks so views work inside Docker
                # containers where the repo is mounted at a different path.
                rel_target = os.path.relpath(path, link.parent)
                link.symlink_to(rel_target)

if __name__ == "__main__":
    regenerate()
```

Run a single view: `python views/api-module/regen.py`
Run all views: `for d in views/*/; do python "$d/regen.py"; done`

Having per-view scripts means changing one view's script only flags that view's mental models (and root references to it) as stale — other views are unaffected.

## 3. Use Helper Scripts (via skill scripts)

The `multi_agent_ide_view_agents` skill provides Python scripts that invoke the `view-agents-utils` container:

```bash
# Check mental model staleness
python skills/multi_agent_ide_skills/multi_agent_ide_view_agents/scripts/view_model.py \
  status /path/to/repo/views/api-module/mental-models/mental-models.md

# View a mental model with annotations
python skills/multi_agent_ide_skills/multi_agent_ide_view_agents/scripts/view_model.py \
  show /path/to/repo/views/api-module/mental-models/mental-models.md

# List referenced source files
python skills/multi_agent_ide_skills/multi_agent_ide_view_agents/scripts/view_model.py \
  files /path/to/repo/views/api-module/mental-models/mental-models.md

# Update a mental model
python skills/multi_agent_ide_skills/multi_agent_ide_view_agents/scripts/view_model.py \
  update /path/to/repo/views/api-module/mental-models/mental-models.md \
  --content updated-content.md --refs refs.json

# Add a new section
python skills/multi_agent_ide_skills/multi_agent_ide_view_agents/scripts/view_model.py \
  add-section /path/to/repo/views/api-module/mental-models/mental-models.md \
  --heading "Data Flow" \
  --parent "Architecture Overview" \
  --content section-content.md \
  --refs refs.json

# Add a new file reference to a section
python skills/multi_agent_ide_skills/multi_agent_ide_view_agents/scripts/view_model.py \
  add-ref /path/to/repo/views/api-module/mental-models/mental-models.md \
  --section-path "Architecture Overview > Data Flow" \
  --file src/main/java/services/DataService.java

# Search chain of custody
python skills/multi_agent_ide_skills/multi_agent_ide_view_agents/scripts/search_custody.py \
  /path/to/repo/views/api-module/mental-models/mental-models.md \
  --section-path "Architecture Overview > Data Flow"
```

Each of these translates to a `docker run view-agents-utils:latest ...` call under the hood.

## 4. Run a View Agent Query (Single View)

The `multi_agent_ide_view_agent_exec` skill provides Python scripts that invoke the `view-agent-exec` container:

```bash
# Query a single view
python skills/multi_agent_ide_skills/multi_agent_ide_view_agent_exec/scripts/query_view.py \
  --repo /path/to/repo \
  --view api-module \
  --model llama3.2 \
  "Explain the API architecture"
```

The agent will:
1. Check if the mental model is stale (including the view script hash for view-level models) and refresh if needed
2. Query Ollama with the view context
3. Output a JSON response to stdout

## 5. Run the Root Agent (Cross-View Synthesis)

```bash
python skills/multi_agent_ide_skills/multi_agent_ide_view_agent_exec/scripts/query_root.py \
  --repo /path/to/repo \
  --model llama3.2 \
  "Summarize the overall architecture"
```

## 6. Two-Phase Fan-Out (Discovery Agent Pattern)

```bash
python skills/multi_agent_ide_skills/multi_agent_ide_view_agent_exec/scripts/fan_out.py \
  --repo /path/to/repo \
  --model llama3.2 \
  --timeout 120 \
  "Analyze the codebase architecture"
```

This orchestrates:
1. Phase 1: Launches one container per view in parallel
2. Phase 2: Launches a root container for synthesis
3. Returns consolidated JSON output

## Local Development

```bash
cd multi_agent_ide_python_parent/packages

# Install both packages in development mode
cd view_agents_utils && uv pip install -e . && cd ..
cd view_agent_exec && uv pip install -e . && cd ..

# Run utils tests
cd view_agents_utils && pytest tests/ && cd ..

# Run exec tests
cd view_agent_exec && pytest tests/ && cd ..

# Run CLI directly (no Docker)
view-model show /path/to/repo/views/api-module/mental-models/mental-models.md
view-agent query --view /path/to/repo/views/api-module --model llama3.2 "Explain"
```
