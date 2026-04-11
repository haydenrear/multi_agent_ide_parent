# Data Model: View Agents

**Branch**: `001-view-agents` | **Date**: 2026-04-10

## Entities

### View

A named directory of symbolic links representing a coherent subset of a repository. Each view has its own `regen.py` script — scripts are per-view, not per-repo.

| Field | Type | Description |
|-------|------|-------------|
| name | string | View identifier (directory name, e.g., `api-module`, `core-lib`) |
| path | path | Absolute path to the view directory under `views/` |
| source_repo | path | Path to the source repository root |
| symlinks | list[path] | Relative paths (from repo root) of files included in this view |
| regen_script | path | Path to this view's `regen.py` Python script (agent-generated, specific to this view) |
| regen_script_hash | string | Git blob hash of this view's `regen.py` — tracked independently for granular staleness detection |
| mental_models_dir | path | Path to the `mental-models/` subdirectory |

**Relationships**: Contains exactly one MentalModel (`mental-models.md`). Referenced by ViewAgent invocations. Each view's `regen.py` is an independent agent-generated Python script that creates/maintains only this view's symlinks. A change to one view's script does not affect other views' staleness.

**Validation**: `name` must be a valid directory name (no slashes, no spaces). `path` must exist and be under `views/`. `regen.py` must exist and be executable.

---

### MentalModel

The single markdown document (`mental-models.md`) describing the architecture, patterns, and key decisions of code in a view. Each view has exactly one mental model — one document, one HEAD, one metadata chain.

| Field | Type | Description |
|-------|------|-------------|
| name | string | Always `mental-models.md` — one per view, one per root |
| path | path | Absolute path to `mental-models/mental-models.md` |
| view_name | string | Name of the owning view (or `"root"` for root-level) |
| head_metadata | path | Symlink (`HEAD`) resolving to the current head MetadataFile |
| sections | list[Section] | Parsed sections with unique heading paths |

**Relationships**: Owned by exactly one View (1:1). Has exactly one head MetadataFile (via HEAD symlink). Sections map to DiffEntries in the MetadataFile.

**Validation**: File must be valid markdown. HEAD symlink must point to an existing MetadataFile. Filename is always `mental-models.md`.

---

### Section

A logical section within a MentalModel, identified by its **section path** — the full heading hierarchy from root to leaf (e.g., `Architecture Overview > Data Flow`). Section paths are the stable identity for lineage tracking. Each DiffEntry covers the content hunk for one section path.

| Field | Type | Description |
|-------|------|-------------|
| section_path | string | Unique heading path: ancestor headings joined by ` > ` (e.g., `Architecture Overview > Data Flow`). This is the section identity — used in DiffEntry and SupersedesRef. |
| heading | string | The leaf heading text (e.g., `Data Flow`) |
| depth | int | Heading depth (1 = `#`, 2 = `##`, etc.) |
| offset | int | Section's ordinal position in the document (0-indexed) — display metadata, not identity |
| status | enum | `CURRENT` or `STALE` — computed from head MetadataFile |

**Relationships**: Belongs to one MentalModel. Mapped to one DiffEntry in the head MetadataFile via `section_path`.

**Identity constraint**: Section paths MUST be unique within a mental model. The `view-model add-section` command validates this. Heading renames are semantically "delete old section chain + create new section" — the old chain is preserved in history.

---

### MetadataFile

An immutable JSON file recording the provenance of a mental model snapshot.

| Field | Type | Description |
|-------|------|-------------|
| id | string | Unique identifier: `<ISO-timestamp>-<content-sha256-prefix-8>` |
| path | path | Absolute path to the JSON file in `mental-models/` |
| created_at | datetime | ISO 8601 timestamp of creation |
| mental_model | string | Always `mental-models.md` — the single mental model per view |
| scope | enum | `view` or `root` |
| owner_name | string | View name for `view` scope, or `root` for `root` scope |
| view_script_hash | string \| null | Git blob hash of the owning view's `regen.py` at time of creation for `view` scope; `null` for `root` scope because the root has no script |
| diff_entries | list[DiffEntry] | One entry per mental model section |
| snapshot_dir | path | Path to the associated `.snapshots/` directory |

**Relationships**: Contains 1..N DiffEntries. Each DiffEntry may reference a previous MetadataFile (linked-list). Has exactly one SnapshotDirectory.

**Validation**: `id` must be unique within the view's `mental-models/` directory. File must be valid JSON conforming to Pydantic schema. Never modified after creation. `scope=view` requires a 40-character hex `view_script_hash`; `scope=root` requires `view_script_hash = null`.

**State**: Immutable. No state transitions — once created, a MetadataFile is permanent.

**Staleness**: For `view` scope, when the current view's `regen.py` hash differs from `view_script_hash`, ALL diff entries in this metadata file are flagged for review — the view's file composition may have changed. Because scripts are per-view, this only affects the specific view whose script changed; other views and their metadata are unaffected. For `root` scope, staleness is computed only from ChildModelRefs: a root diff entry is stale when a referenced child section is stale or the child head metadata ID has advanced. The root has no `regen.py`.

---

### DiffEntry

A single entry within a MetadataFile, mapping a mental model section to its provenance. Identified by `section_path`.

| Field | Type | Description |
|-------|------|-------------|
| section_path | string | Unique heading path (e.g., `Architecture Overview > Data Flow`) — the stable section identity |
| offset | int | Section's ordinal position in the document at time of creation (display metadata, not identity) |
| source_files | list[SourceFileRef] | Source files the LLM selected as inputs (view-level entries only) |
| child_models | list[ChildModelRef] | Child view mental-model sections referenced (root-level entries only) |
| supersedes | SupersedesRef \| null | Pointer to the previous MetadataFile + DiffEntry this one replaces. Null for initial entries. |

**Relationships**: Belongs to one MetadataFile. References either SourceFileRefs (view-level) or ChildModelRefs (root-level), never both. Optionally references one previous DiffEntry in a previous MetadataFile.

**Validation**: Exactly one of `source_files` or `child_models` must be non-empty (not both). The unused collection may be omitted or serialized as an empty list, depending on the concrete schema/model layer. If `supersedes` is present, the referenced MetadataFile must exist.

---

### SourceFileRef

A reference to a source file selected by the LLM as input for a **view-level** mental model section. Used only in view-level DiffEntries.

| Field | Type | Description |
|-------|------|-------------|
| repo_path | path | Relative path from repo root (e.g., `src/main/java/Foo.java`) |
| content_hash | string | Git blob hash of the file's **working tree content** (`git hash-object <file>`) — catches uncommitted changes too |
| snapshot_path | path | Relative path inside the snapshot directory |
| line_span | LineSpan \| null | Optional: specific source-file span using `start_line` + `line_count` (null = entire file) |

**Relationships**: Belongs to one DiffEntry. Has a corresponding file in the SnapshotDirectory.

**Validation**: `repo_path` must be a valid relative path. `content_hash` must be a 40-character hex string. `snapshot_path` must exist in the parent MetadataFile's SnapshotDirectory. If present, `line_span.start_line >= 1` and `line_span.line_count >= 1`. The span is contextual provenance only; staleness is still based on the whole-file `content_hash`.

---

### LineSpan

An optional source-file span used only for contextual provenance in a SourceFileRef.

| Field | Type | Description |
|-------|------|-------------|
| start_line | int | 1-based starting line in the source file |
| line_count | int | Number of lines covered from `start_line` |

**Relationships**: Embedded inside one SourceFileRef.

**Validation**: `start_line >= 1` and `line_count >= 1`.

---

### ChildModelRef

A reference to a child view's mental-model section, used in **root-level** DiffEntries. Separate from SourceFileRef because the semantics differ: invalidation is based on the child's head metadata ID and section staleness, not a file content hash.

| Field | Type | Description |
|-------|------|-------------|
| view_name | string | Name of the child view (e.g., `api-module`) |
| child_head_metadata_id | string | ID of the child view's head MetadataFile at time of root metadata creation |
| child_section_paths | list[string] | Specific child section paths referenced by this root diff entry |
| snapshot_path | path | Relative path inside the snapshot directory (copy of the child `mental-models.md`) |

**Relationships**: Belongs to one root-level DiffEntry. Has a corresponding file in the SnapshotDirectory.

**Validation**: `view_name` must reference an existing view. `child_head_metadata_id` must reference an existing MetadataFile in the child view's `mental-models/` directory.

---

### SupersedesRef

A pointer from a DiffEntry to the previous DiffEntry it replaces.

| Field | Type | Description |
|-------|------|-------------|
| metadata_file_id | string | ID of the previous MetadataFile |
| section_path | string | Section path of the DiffEntry being superseded |

**Relationships**: Points to exactly one DiffEntry in a previous MetadataFile.

**Validation**: `metadata_file_id` must reference an existing MetadataFile in the same `mental-models/` directory.

---

### SnapshotDirectory

A directory containing eagerly-copied input files for a MetadataFile.

| Field | Type | Description |
|-------|------|-------------|
| path | path | Absolute path: `<metadata-file-id>.snapshots/` |
| files | list[path] | Relative paths of copied files within the directory |

**Relationships**: Belongs to exactly one MetadataFile. Contains copies of all SourceFileRefs (view-level) or ChildModelRefs (root-level) from that MetadataFile's DiffEntries.

**Validation**: Directory name must match `<metadata-file-id>.snapshots/`. Every SourceFileRef or ChildModelRef in the parent MetadataFile must have a corresponding file in this directory.

---

### ViewAgentResponse

The structured JSON output from a view-agent CLI invocation.

| Field | Type | Description |
|-------|------|-------------|
| view_name | string | Name of the view that was processed |
| mode | enum | `view` or `root` |
| query | string | The original query |
| response | string | The agent's answer |
| mental_model_updated | bool | Whether the mental model was modified |
| stale_sections_refreshed | list[string] | Section paths that were refreshed during staleness check |
| metadata_files_created | list[string] | IDs of new MetadataFiles created during this invocation |
| error | string \| null | Error message if the invocation failed |

**Relationships**: References one View. May reference 0..N new MetadataFiles.

---

## Filesystem Layout Example

```text
views/
├── mental-models/                              # Root-level mental model (1:1)
│   ├── mental-models.md                        # THE root mental model (one per root)
│   ├── HEAD -> 2026-04-10T14-30-00-a1b2c3d4.json
│   ├── 2026-04-10T14-30-00-a1b2c3d4.json      # Head metadata file
│   ├── 2026-04-10T14-30-00-a1b2c3d4.snapshots/ # Snapshots for head
│   │   └── api-module/mental-models/mental-models.md  # Copied view mental model
│   ├── 2026-04-09T10-00-00-e5f6a7b8.json      # Previous metadata file
│   └── 2026-04-09T10-00-00-e5f6a7b8.snapshots/
│       └── api-module/mental-models/mental-models.md
│
├── api-module/                                 # A view
│   ├── regen.py
│   ├── src -> ../../src/main/java/api/         # Relative symlink to source
│   ├── models -> ../../src/main/java/models/
│   └── mental-models/
│       ├── mental-models.md                    # THE view mental model (one per view)
│       ├── HEAD -> 2026-04-10T12-00-00-c3d4e5f6.json
│       ├── 2026-04-10T12-00-00-c3d4e5f6.json
│       ├── 2026-04-10T12-00-00-c3d4e5f6.snapshots/
│       │   ├── src/main/java/api/UserController.java
│       │   └── src/main/java/models/User.java
│       ├── 2026-04-09T09-00-00-a1b2c3d4.json
│       └── 2026-04-09T09-00-00-a1b2c3d4.snapshots/
│           ├── src/main/java/api/UserController.java
│           └── src/main/java/models/User.java
│
└── core-lib/                                   # Another view
    ├── regen.py
    ├── util -> ../../src/main/java/util/       # Relative symlink
    └── mental-models/
        ├── mental-models.md                    # THE view mental model (one per view)
        ├── HEAD -> ...
        └── ...
```

## Entity Relationship Diagram (textual)

```
View 1──1 MentalModel 1──1 HEAD(MetadataFile)
                                │
                          1──N DiffEntry (keyed by section_path)
                                │
                    ┌───────────┼───────────┐
             (view-level)  (root-level)  0──1 SupersedesRef
            1──N SourceFileRef  1──N ChildModelRef    │
                    │                 │               └───> previous MetadataFile.DiffEntry
                    └───> SnapshotDir─┘
```

- View → MentalModel: one view has exactly one mental model document (`mental-models.md`)
- MentalModel → MetadataFile: HEAD symlink points to current head
- MetadataFile → DiffEntry: one metadata file contains entries for each section, keyed by `section_path`
- DiffEntry → SourceFileRef: view-level entries reference curated source files
- DiffEntry → ChildModelRef: root-level entries reference child view mental-model sections (view_name + child_section_paths + child_head_metadata_id)
- DiffEntry → SupersedesRef: each entry optionally points back to a previous entry (via section_path)
- MetadataFile → SnapshotDirectory: each metadata file has one snapshot directory
- SourceFileRef/ChildModelRef → SnapshotDirectory: each ref has a file in the snapshot dir
- Root MentalModel: same 1:1 structure (one `mental-models.md`), but uses ChildModelRefs instead of SourceFileRefs
