# Feature Specification: View Agents — Repository Views & Parallel Local-Model Sub-Agents

**Feature Branch**: `001-view-agents`
**Created**: 2026-04-09
**Status**: Draft
**Input**: User description: "Create a view-agents system: repository views for progressive disclosure, mental models stored hierarchically in views, and a Python/Ollama CLI sub-agent that the discovery agent can parallelize work across views using local models."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Generate Repository Views (Priority: P1)

A developer (or automated pipeline) points the view generation tool at a repository. The tool analyzes the repository structure and produces a set of **views** — each view is a directory containing symbolic links (or copies) to a coherent subset of the repository files, organized for progressive disclosure. Views are stored under a `views/` directory at the repository root. Each view includes a regeneration script so the view can be refreshed when the repository changes.

**Why this priority**: Views are the foundational artifact everything else depends on. Without views, there is nothing for local models to reason over and no structure for mental models.

**Independent Test**: Point the tool at a sample repository. Verify that a `views/` directory is created containing at least one named view subdirectory, that the view contains symbolic links to real files, and that the regeneration script recreates the view when re-run.

**Cucumber Test Tag**: `@view-generation`

**Acceptance Scenarios**:

1. **Given** a repository path, **When** the view generator runs, **Then** a `views/` directory is created at the repository root containing one or more named view subdirectories, each with symbolic links into the source tree and a regeneration script.
2. **Given** a previously generated views directory, **When** the repository contents change and the regeneration script is re-run, **Then** the view is updated to reflect the current repository state.
3. **Given** a repository with no recognizable structure, **When** the view generator runs, **Then** it produces a single flat "all-files" view as a fallback.

---

### User Story 2 — Mental Models with Chain of Custody (Priority: P1)

Each view (and the views root) contains a `mental-models/` subdirectory. Mental models are markdown documents (wiki-style) that describe the purpose, architecture, patterns, and key decisions of the code visible in that view. Mental models replace ad-hoc documentation and agent memory files with a structured, hierarchical knowledge base that evolves with the code.

Critically, mental models only reference a **curated subset** of files — the files the LLM judged important enough to inform the mental model. A view may contain hundreds of files, but the mental model might only reference 10-20 of them. This is by design: by forcing the LLM to choose which files matter, the system creates an emergent curation of what is architecturally significant. The chain of custody only tracks these selected files, not every file in the view.

Every mental model tracks its **chain of custody** via **immutable metadata files**. Each time the mental model is updated, a new metadata file is created (never mutated) containing diff entries — each diff entry covers a line range in the mental model and includes: the curated input file paths (eagerly copied to a local snapshot directory), their git hashes, and a pointer to the previous metadata file whose diff entry this one supersedes. The head metadata file references the live repository paths; older metadata files are fully self-contained snapshots. This forms a flat, hash-map-like structure of immutable files — walking backward is just following pointers, with no log parsing or git operations needed.

When any of these selected files changes (its commit hash advances), the mental model sections derived from that file are marked invalid. This invalidation propagates hierarchically: a view-level mental model's diff entries reference selected source files; the root-level mental model's diff entries reference view-level mental models (those are its "files"). If a selected source file changes, the view mental model diff entries referencing it are invalidated, and the root mental model diff entries referencing that view mental model are also invalidated. A rendering script reads the head metadata file to determine which sections are current vs. stale.

**Why this priority**: Promoted to P1 because mental models that go stale silently are worse than no mental models at all. The chain of custody is essential for the MVP — agents must know which parts of a mental model they can trust and which need regeneration. The curated-subset approach also ensures the chain of custody remains lightweight — it only tracks what matters.

**Independent Test**: Generate views, create a mental model for a view, then modify a source file and commit. Run the staleness checker. Verify the mental model sections referencing the changed file are flagged invalid, and that the root mental model sections referencing that view mental model are also flagged invalid.

**Cucumber Test Tag**: `@view-mental-models`

**Acceptance Scenarios**:

1. **Given** a generated view, **When** the view-agent produces a mental model, **Then** it creates the mental model in `mental-models/`, an immutable metadata file containing diff entries (each with line range, curated input file paths, and git hashes), and eagerly copies the selected input files to a snapshot directory alongside the metadata file.
2. **Given** a views root, **When** the root agent produces a root mental model, **Then** it creates a root-level `views/mental-models/` directory with a metadata file whose diff entries reference view-level mental model files (not individual source files — the root's inputs are the view mental models), eagerly copied to the snapshot directory.
3. **Given** an existing mental model, **When** a view-agent updates it, **Then** a new immutable metadata file is created (the previous one is never modified). Each diff entry in the new metadata file points back to the diff entry in the previous metadata file that it supersedes. New input files are eagerly copied to a snapshot directory. The head metadata file references live repository paths.
4. **Given** the head metadata file, **When** any of the selected source files' git hash changes (new commit), **Then** the rendering script flags the diff entries (and their corresponding mental model line ranges) referencing that file as stale/invalid.
5. **Given** a view-level mental model flagged as stale, **When** the root-level mental model rendering script runs, **Then** the root diff entries that reference the stale view mental model are also flagged as stale (cascading invalidation).
6. **Given** a mental model with mixed valid and stale diff entries, **When** the rendering script runs, **Then** it produces output that clearly distinguishes current sections from stale sections.
7. **Given** a stale diff entry, **When** the staleness is caused by a small diff to a selected source file, **Then** the system MAY resolve it by creating a new metadata file with updated line references without requiring a full mental model regeneration.
8. **Given** any metadata file, **When** an agent follows the previous-metadata pointers in each diff entry, **Then** it can walk backwards through the full history of that mental model section, with all input files available as local snapshots at each step — no git operations needed.

---

### User Story 3 — Docker-Sandboxed View-Agent CLI (Priority: P1)

A standalone Python CLI tool (`view-agent`) accepts a query/task, a view path, and an Ollama model name. Each invocation operates on a **single view** inside a Docker container — the container only has the specific view directory mounted (read-write for its `mental-models/`, read-only for source symlinks). The agent loads the view's files and mental model, queries the host Ollama instance, and outputs a structured response. The mental model changes made inside the container are persisted back through the mounted volume.

The CLI is designed to be invoked N times in parallel by the discovery agent (one `docker run` per view), plus one additional invocation for the root mental model (with the entire `views/` directory mounted). This Docker-based sandboxing ensures each agent can only see and modify its own view, preventing cross-contamination.

**Why this priority**: This is the execution engine — it is the mechanism by which the discovery agent fans out work across views using local models. Docker sandboxing is essential because agents modify mental models, and without isolation an agent could corrupt another view's state.

**Independent Test**: Create two small test views with a few files each. Run two `docker run` commands in parallel, each mounting one view. Verify that two Ollama calls are made, two per-view responses are returned, and neither container can see the other's files.

**Cucumber Test Tag**: `@view-agent-cli`

**Acceptance Scenarios**:

1. **Given** a view directory and a query, **When** the CLI is invoked via `docker run` with the view mounted, **Then** the agent can only access files within that mounted view and returns a structured response.
2. **Given** a view with a mental model, **When** the CLI agent processes the view, **Then** it includes the mental model content in the context sent to the Ollama model and can write updates back to the mental model through the mounted volume.
3. **Given** mental model updates written by the agent, **When** the container exits, **Then** the updates persist on the host filesystem, a new immutable metadata file is created with diff entries linking back to the previous metadata file, and the curated input files are eagerly copied to the new snapshot directory.
4. **Given** an Ollama model that is not available on the host, **When** the CLI is invoked, **Then** it reports a clear error identifying the missing model and exits gracefully.
5. **Given** a view-agent container, **When** it attempts to access files outside its mounted view directory, **Then** the access fails (sandbox enforced by Docker mount boundaries).

---

### User Story 4 — Discovery Agent Integration (Two-Phase Docker Flow) (Priority: P2)

The discovery agent uses a two-phase flow to parallelize research across views:

**Phase 1 — Per-View Fan-Out**: The discovery agent detects available views, then launches one `docker run` command per view in parallel. Each container gets only its own view directory mounted. All containers run concurrently against the host Ollama instance. The discovery agent waits for all containers to return their per-view responses.

**Phase 2 — Root Synthesis**: The discovery agent launches one more `docker run` command for the root view-agent, this time mounting the entire `views/` directory (including the now-updated per-view mental models). This root agent synthesizes the per-view responses into the root mental model and returns a consolidated answer. The discovery agent then performs any additional targeted research and returns.

**Why this priority**: This is the integration point that makes the system useful end-to-end, but it depends on Stories 1, 2, and 3 being complete.

**Independent Test**: Configure the discovery agent with a repository that has generated views. Issue a discovery request. Verify that the agent launches parallel Docker containers (one per view), waits for all to complete, then launches a root container, and incorporates the final result into its response.

**Cucumber Test Tag**: `@view-agent-discovery-integration`

**Acceptance Scenarios**:

1. **Given** a repository with generated views, **When** the discovery agent receives a research request, **Then** it launches one Docker container per view in parallel, each mounting only its own view directory.
2. **Given** all per-view containers have completed, **When** the discovery agent proceeds to Phase 2, **Then** it launches a root container with the entire `views/` directory mounted, which synthesizes results into the root mental model.
3. **Given** the root container has completed, **When** the discovery agent receives the consolidated response, **Then** it incorporates the result into its final answer, referencing specific views where relevant.
4. **Given** a repository with no views, **When** the discovery agent receives a request, **Then** it falls back to its normal (non-parallelized) behavior.
5. **Given** a per-view container that times out, **When** the discovery agent detects the timeout, **Then** it proceeds with the responses from the containers that completed, noting which views timed out.

---

### User Story 5 — Chain of Custody History, Search & Visualization (Priority: P2)

The chain of custody is not just a point-in-time staleness check — it is a **historical record** of how mental models have evolved alongside the curated set of source files that produced them. The history is captured structurally through the immutable metadata files (Story 2): each metadata file's diff entries point back to the previous metadata file they supersede, and each carries eagerly-copied snapshots of the input files at that point in time. This forms a flat collection of immutable files — like a content-addressed hash map — where walking backward is pointer-following, not log parsing.

A **search/query tool** provides the ability to traverse and reason over this structure. An agent or developer can: walk backwards through the chain for a specific mental model section (seeing each prior version alongside the exact input files that produced it), query which metadata files reference a particular source file, filter by date or git hash range, and see how the curated set of important files evolved over time — all without performing any git operations, because the input files are saved as snapshots in each metadata file's snapshot directory.

The system provides:

1. **A search/query CLI tool** — operates over the flat collection of immutable metadata files per view. It can traverse backward pointers, filter by source file path, mental model section, date range, or git hash range. The tool reads the metadata files and their snapshot directories directly — no database or index needed, since the structure is already a flat map of immutable files.

2. **A static graphical interface** — generated HTML/SVG files that visualize the chain of custody as a graph. Nodes represent mental model diff entries (with line ranges) and the curated source file snapshots that produced them; edges represent "derived from" and "supersedes" relationships. The timeline dimension shows how these relationships evolved — including how the curated set itself changed (files added/removed from consideration). These are saved files in the `views/` directory that can be opened in a browser — not a running server.

This serves three purposes: (1) agents can reason about the *current* mental models by understanding their provenance, (2) agents and developers can see how mental models have evolved over time and what caused each revision, and (3) the curated file selection itself becomes visible — you can see the LLM's emergent judgment of what matters, and how that judgment changes as the codebase evolves.

**Why this priority**: P2 because the basic chain of custody (Story 2) provides the immutable metadata structure, but without search and visualization, agents can't efficiently reason over the history. The search tool makes the linked-list structure queryable; the visualization makes it inspectable.

**Independent Test**: Generate views, create a mental model, update it twice (after two source file changes). Use the search tool to walk backwards from the head metadata file for a specific section — verify it shows three metadata files (initial + two updates) each with their snapshot directories. Generate the visualization — verify it shows the chain of superseding diff entries connected to their source file snapshots.

**Cucumber Test Tag**: `@view-custody-history`

**Acceptance Scenarios**:

1. **Given** a chain of three immutable metadata files (initial, update-1, update-2), **When** an agent uses the search tool to walk backwards from the head for a specific mental model section, **Then** it sees each prior diff entry alongside the exact input file snapshots that produced it — by following pointers, not parsing a log.
2. **Given** a collection of metadata files, **When** a user or agent queries by a specific source file path, **Then** all metadata files whose diff entries reference that file (in their curated input set) are returned, showing how the file's role in the mental model evolved over time.
3. **Given** a collection of metadata files, **When** a user or agent queries filtered by a git hash range, **Then** the matching metadata files are returned.
4. **Given** a collection of metadata files, **When** the visualization generator runs, **Then** it produces static HTML/SVG files showing: diff entries and curated source file snapshots as nodes, "derived from" and "supersedes" edges, and a timeline of how the curated set and mental model content evolved.
5. **Given** a root-level mental model whose diff entries reference view-level mental model files, **When** the visualization generator runs, **Then** the graph includes the cascading relationships (curated source files → view mental model diff entries → root mental model diff entries) across the metadata chain.
6. **Given** a generated visualization, **When** a developer opens the HTML file in a browser, **Then** they can see which diff entries are current vs. stale, follow the "supersedes" chain backwards, and see how the curated file set has changed over time.

---

### User Story 6 — View Update on Repository Change (Priority: P3)

When the repository is modified (new commits, file additions/deletions), the view regeneration scripts are re-run (manually or via a hook) to update the views. The chain of custody system automatically detects which mental model sections are invalidated by comparing current git hashes against the head metadata file's diff entries. Stale sections are not automatically overwritten — agents can refresh them on their next invocation, producing a new immutable metadata file in the chain.

**Why this priority**: Important for long-lived usage but not required for initial validation. The chain-of-custody mechanism (Story 2) handles staleness detection at the per-section level; this story covers the broader view structure updates.

**Independent Test**: Modify a file in the repository, re-run the view regeneration script, verify the view's symbolic links are updated. Run the staleness checker and verify the affected mental model sections are flagged invalid via chain of custody. Check the custody log for the invalidation event.

**Cucumber Test Tag**: `@view-update`

**Acceptance Scenarios**:

1. **Given** a generated view and a subsequent file addition in the repository, **When** the regeneration script runs, **Then** the new file appears in the appropriate view.
2. **Given** a generated view and a subsequent file deletion in the repository, **When** the regeneration script runs, **Then** the deleted file's symbolic link is removed from the view.
3. **Given** a mental model with a head metadata file, **When** a source file has a new commit and the staleness checker runs, **Then** the specific diff entries referencing that file are flagged as stale, the cascading invalidation propagates to the root mental model, and the stale state is detectable from the head metadata file without modifying it.

---

### Edge Cases

- What happens when a repository has thousands of files — does view generation complete in a reasonable time?
- What happens when symbolic links point to files that have been moved or renamed?
- What happens when the Ollama service is not running when the CLI is invoked inside a Docker container?
- What happens when a single view-agent container takes significantly longer than others — does the discovery agent handle timeouts?
- What happens when the views directory already exists from a previous run — is it safely overwritten or merged?
- What happens when two view-agents produce contradictory answers during root synthesis?
- What happens when a mental model's commit-hash manifest references a file that has been deleted from the repository?
- What happens when a view-agent container crashes — are partial mental model updates left in a corrupt state, or is there atomic write behavior?
- What happens when the Docker daemon is not running — does the discovery agent detect this and fall back gracefully?
- What happens when every section of a mental model is stale — is the entire model treated as needing regeneration?
- What happens when the collection of immutable metadata files and snapshot directories grows very large on disk — is there a pruning or archival strategy for old metadata files?
- What happens when the visualization is generated for a chain with dozens of metadata files — does it remain navigable?
- What happens when a file previously in the curated set is removed by the LLM in a subsequent update — the new metadata file simply won't reference it, and the old one remains immutable showing it was once included.
- What happens when snapshot directories contain large files — is there deduplication across metadata files when the same file content appears in consecutive snapshots?

## Requirements *(mandatory)*

### Functional Requirements

#### View Generation

- **FR-001**: The system MUST analyze a repository and produce one or more named views, each containing symbolic links to a coherent subset of repository files.
- **FR-002**: Views MUST be stored under a `views/` directory at the repository root (or a configurable location).
- **FR-003**: Each view MUST include a regeneration script that can recreate the view from the current repository state.
- **FR-004**: The view generator MUST support a tree-based traversal strategy that groups files by directory structure, producing hierarchical views (e.g., per-module, per-package, per-feature-area).
- **FR-005**: The view generator MUST produce a fallback "all-files" view when no meaningful grouping can be determined.

#### Mental Models & Chain of Custody

- **FR-006**: Each view MUST have a `mental-models/` subdirectory for storing wiki-style knowledge documents.
- **FR-007**: A root-level `views/mental-models/` directory MUST exist for cross-view knowledge.
- **FR-008**: Mental models MUST be plain markdown files, organized as a flat or shallow directory structure (like a wiki).
- **FR-009**: Each mental model MUST be accompanied by an **immutable metadata file** — a structured file containing diff entries, where each diff entry maps a mental model line range to the curated source files the LLM selected as inputs, their git commit hashes, and a pointer to the previous metadata file's diff entry that this one supersedes. This is a curated subset of the view's files, not every file in the view.
- **FR-009a**: Each diff entry in the metadata file MUST record: the mental model line range it covers, the curated source file paths that contributed, their git hashes, and (for non-initial entries) a pointer to the previous metadata file and diff entry it supersedes.
- **FR-009a2**: Each metadata file MUST have an associated **snapshot directory** containing eagerly-copied versions of the curated input files at the time of production. The head metadata file's diff entries MUST also reference the live repository paths.
- **FR-009b**: A rendering script MUST compare the head metadata file's recorded hashes against current git hashes and flag diff entries where any contributing file's hash has advanced as **stale/invalid**. Only files in the curated set are checked.
- **FR-009c**: Root-level mental model metadata files MUST have diff entries that reference view-level mental model files (not individual source files) as their inputs, so that when a view-level mental model is invalidated, the root diff entries referencing it are also invalidated (cascading invalidation).
- **FR-009d**: The rendering script MUST produce output that clearly distinguishes current (valid) sections from stale (invalid) sections.
- **FR-009d2**: When staleness is caused by a small diff to a selected source file, the system SHOULD support lightweight resolution (creating a new metadata file with updated line references) without requiring full mental model regeneration.

#### Chain of Custody History, Search & Visualization

- **FR-009e**: The immutable metadata files and their snapshot directories MUST be stored as a flat collection in the view's `mental-models/` directory. Each metadata file is identified by a unique name (e.g., timestamp-based or content-hash-based). No metadata file is ever modified after creation.
- **FR-009f**: The mechanical operations for creating metadata files, copying input file snapshots, and linking diff entries to previous metadata files MUST be implemented as a reusable tool/library, since the same structure is used for both view-level metadata (input = source files) and root-level metadata (input = view mental model files).
- **FR-009g**: A search/query CLI tool MUST support traversing the metadata file chain by: walking backwards from the head following "supersedes" pointers, filtering by source file path, mental model section (line range), date range, and git hash range.
- **FR-009g2**: The search tool MUST support walking backwards through the history of a specific mental model section, resolving the snapshot directory at each step to show the exact input files that produced that version — without any git operations.
- **FR-009h**: A visualization generator MUST produce static HTML/SVG files in the `views/` directory that render the chain of custody as a graph showing diff entries and curated source file snapshots as nodes, "derived from" and "supersedes" edges, and a timeline showing how the curated set and mental model content evolved.
- **FR-009i**: The visualization MUST include the cascading relationship hierarchy (curated source files → view mental model diff entries → root mental model diff entries) so the full provenance chain is visible.
- **FR-009i2**: The visualization MUST show changes in the curated file set over time — when files are added to or dropped from the mental model's inputs across metadata files.
- **FR-009j**: The visualization MUST visually distinguish current (valid) diff entries from stale (invalid) ones.
- **FR-009k**: The view-agent CLI MUST create a new immutable metadata file (with snapshot directory) whenever it creates or modifies a mental model section.

#### Sub-Agent CLI (Docker-Sandboxed)

- **FR-010**: The CLI MUST be a standalone Python tool packaged in a Docker image, invokable via `docker run`.
- **FR-011**: The CLI MUST accept a query, a single view path (the mount point inside the container), and an Ollama model name as inputs.
- **FR-012**: Each CLI invocation operates on exactly one view. Parallelism is achieved by the caller (discovery agent) launching multiple `docker run` commands concurrently.
- **FR-013**: The agent MUST load the mounted view's file listing and mental model content, construct a prompt, and send it to the host's Ollama instance (accessed via `host.docker.internal` or equivalent).
- **FR-014**: The CLI MUST output its response as structured JSON to stdout.
- **FR-015**: The CLI MUST support a `--mode root` flag for the root synthesis invocation, where the entire `views/` directory is mounted and the agent synthesizes per-view mental models into the root mental model.
- **FR-016**: The CLI MUST handle Ollama connection errors gracefully with clear error messages.
- **FR-017**: The CLI MUST support a configurable timeout to prevent indefinite hangs.
- **FR-017a**: The CLI MUST create a new immutable metadata file (with eagerly-copied input file snapshots) for any mental model sections it creates or modifies, linking diff entries back to the previous metadata file.
- **FR-017b**: The Docker image MUST be built from a Dockerfile in the skill directory so it can be versioned and rebuilt as the CLI evolves.

#### Discovery Agent Integration (Two-Phase Docker Flow)

- **FR-018**: The discovery agent MUST detect whether a repository has generated views before attempting to use them.
- **FR-019**: In Phase 1, the discovery agent MUST launch one `docker run` command per view in parallel, each mounting only that view's directory (e.g., `docker run -v views/<name>:/view ...`).
- **FR-019a**: The discovery agent MUST wait for all Phase 1 containers to complete (or time out) before proceeding to Phase 2.
- **FR-020**: In Phase 2, the discovery agent MUST launch a single `docker run` command for the root agent, mounting the entire `views/` directory, using `--mode root`.
- **FR-020a**: The discovery agent MUST incorporate the root agent's consolidated response into its final answer.
- **FR-021**: The discovery agent MUST fall back to standard behavior when no views are available.
- **FR-021a**: The discovery agent MUST handle Docker daemon unavailability gracefully, falling back to standard behavior with a warning.

### Key Entities

- **View**: A named directory of symbolic links into a repository, representing a coherent subset of the codebase. Contains a regeneration script and a `mental-models/` subdirectory.
- **Mental Model**: A markdown document (or set of documents) describing the architecture, patterns, and key decisions of code within a view or across views. Accompanied by a commit-hash manifest.
- **Metadata File**: An immutable structured file containing diff entries. Each diff entry covers a mental model line range and records: the curated source files (or view mental model files for root) the LLM selected as inputs, their git commit hashes, a pointer to the previous metadata file's diff entry it supersedes, and a reference to the associated snapshot directory. Never modified after creation.
- **View Agent**: A single Docker container running the CLI tool on one mounted view — loads files and mental model, queries the host Ollama instance, returns a response, and updates the mental model and manifest.
- **Root Agent**: A special view-agent invocation (`--mode root`) with the entire `views/` directory mounted, responsible for synthesizing per-view mental models into the root mental model.
- **Snapshot Directory**: A directory associated with each metadata file containing eagerly-copied versions of the curated input files at the time of that metadata file's creation. Makes history self-contained — no git needed to see past states.
- **Chain of Custody**: The linked chain of immutable metadata files from head to initial, connected by "supersedes" pointers in diff entries. Forms a flat hash-map-like structure. When a source file's git hash changes, the head metadata file's diff entries referencing it are flagged stale, cascading through root-level metadata.
- **Custody Visualization**: Static HTML/SVG files showing the chain of custody as a graph — diff entries and curated source file snapshots as nodes, "derived from" and "supersedes" as edges, with a timeline showing how both the mental model content and the curated file set evolved.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: View generation completes for a repository of up to 5,000 files in under 60 seconds.
- **SC-002**: The discovery agent successfully launches at least 5 Docker containers in parallel on a standard developer machine.
- **SC-003**: Per-view agent responses are returned within 120 seconds per container (configurable timeout).
- **SC-004**: The discovery agent's end-to-end response time using view-agents is no more than 2x the slowest individual container response (demonstrating effective parallelism).
- **SC-005**: Mental models are structured enough that an agent can load and use them without additional parsing — plain markdown with a consistent header format, accompanied by a machine-readable commit-hash manifest.
- **SC-006**: The CLI Docker image can be built and invoked independently of the multi-agent IDE for standalone testing and iteration.
- **SC-007**: View regeneration after a repository change completes in under 30 seconds for repositories of up to 5,000 files.
- **SC-008**: The staleness checker correctly identifies all invalidated mental model sections within 5 seconds across all views, even when the curated file sets total up to 100 source files.
- **SC-009**: Cascading invalidation from source file → view mental model → root mental model is detected in a single rendering pass (no iterative convergence needed).
- **SC-010**: Search tool queries return results in under 2 seconds when traversing a chain of up to 100 metadata files.
- **SC-011**: The visualization generator produces navigable HTML files that load in a standard browser within 5 seconds for chains of up to 50 metadata files with up to 50 curated source files.
- **SC-012**: An agent can use the search tool to determine the full provenance of any mental model section — which source files, which commits, which prior versions, and the input file snapshots at each step — in a single CLI call.

## Assumptions

- Ollama is installed and running locally on the developer's machine, with at least one model pulled. The Docker containers access the host Ollama via `host.docker.internal` (macOS/Windows) or `--network host` (Linux).
- Docker is installed and the Docker daemon is running on the developer's machine.
- The repository being viewed is a local git repository (not a bare or remote-only repo).
- Symbolic links are supported by the filesystem (standard on macOS and Linux). Inside Docker containers, the mounted view directory contains the resolved files (Docker follows symlinks on mount).
- The Python CLI targets Python 3.10+ and runs inside a Docker container.
- The initial view generation strategy uses directory-structure-based grouping; more sophisticated strategies (semantic, dependency-graph) are deferred to later iterations.
- The `view-agent` CLI and Dockerfile will be developed as a new skill under `skills/multi_agent_ide_skills/`. View generation scripts will also live there.
- The `multi_agent_ide_java_parent/view_agents/` directory will contain any Java-side integration code if needed, but the core CLI is Python-only.
- Discovery agent integration (Story 4) will be implemented as instructional prompts to the discovery agent, invoking `docker run` commands rather than requiring Java code changes.
- Git must be available inside the Docker container (or the manifest comparison can be done host-side before launching containers). The initial approach will do manifest comparison host-side.
