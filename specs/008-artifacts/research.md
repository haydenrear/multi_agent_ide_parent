# Research: Execution Artifacts, Templates, and Semantics

**Feature**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/008-artifacts/spec.md`  
**Plan**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/008-artifacts/plan.md`  
**Date**: 2026-01-24

## Decisions

### 1) ArtifactKey Strategy (Execution-Scoped + Shared)

**Decision**: Use two ArtifactKey namespaces:

- Execution-scoped keys for per-run artifacts (execution tree)
- Shared keys for versioned templates (PromptTemplate versions reused across runs)

**Rationale**:

- Execution reconstructability needs stable, hierarchical keys inside a run.
- PromptTemplate versions must be reused across many executions; their ArtifactKey should change only when template text changes.

**Alternatives considered**:

- Single global key namespace for all artifacts: rejected because it blurs run-local provenance vs shared template provenance.
- Derive template ArtifactKey directly from content hash only: rejected because the key must also embed last-updated time for evolution analysis.

### 2) Time-Sortable Ordering Within an Execution

**Decision**: Ensure `ArtifactKey` includes a time component and provides deterministic ordering for execution-scoped artifacts, while avoiding late insertion of children under older keys.

Practical rule: emit artifacts as immutable subtrees at the time the event occurs (render prompt, tool call, graph event). If later information is produced, emit a new artifact (or semantic representation) referencing earlier artifacts rather than mutating existing ones.

**Rationale**:

- Prefix-based hierarchy and global time ordering are hard to combine if children are added much later.
- Emitting immutable subtrees aligns with the spec invariant (source artifacts are immutable) and preserves ordering.

**Alternatives considered**:

- Allow “append children later” under long-lived parent scopes: rejected due to ordering ambiguities and increased complexity in reconstruction.

### 3) Content Hashing (SHA-256 on Canonical Bytes)

**Decision**: Compute `contentHash` using SHA-256 on canonical bytes, encoded as lowercase hexadecimal (64 characters):

- PromptTemplate: `sha256(templateText.getBytes(UTF_8)).toHex()`
- PromptArgs: `sha256(canonicalJson.getBytes(UTF_8)).toHex()`
- GraphEvent/EventArtifact payload: `sha256(canonicalJson.getBytes(UTF_8)).toHex()`

**Canonical JSON rules**:
- Object keys sorted lexicographically (Unicode code point order)
- No insignificant whitespace (compact form)
- Numbers: no trailing zeros, no leading zeros except `0.x`, no positive sign
- Strings: UTF-8 encoded, escape only required characters
- Explicit `null` values included, missing keys omitted
- Array ordering preserved

**Rationale**:

- SHA-256 is widely available, fast, and cryptographically secure against collisions.
- Lowercase hex encoding is human-readable and consistent across platforms.
- Canonical JSON ensures identical logical content produces identical hashes.
- Hashes provide stable “what changed” identity, enabling deduplication and semantic caching.
- For templates, the content hash directly defines version identity within a template family.

**Alternatives considered**:

- SHA-1: rejected; deprecated due to collision vulnerabilities.
- SHA-512: rejected; longer output with no benefit for this use case.
- Base64 encoding: rejected; less readable than hex for debugging.
- Hashing parsed AST / normalized structures: rejected; increases coupling and complexity.

### 4) Template Version Storage + Deduplication

**Decision**: Deduplicate PromptTemplate versions by `(templateStaticId, contentHash)`. Create a new stored PromptTemplate version only when a new content hash appears for the same static template ID. Assign that stored version a stable PromptTemplate `ArtifactKey` that is reused across executions.

**Rationale**:

- Enables longitudinal analysis of how template changes affect outcomes without exploding storage.
- Stable keys enable references from many execution trees without copying the template artifact.

**Alternatives considered**:

- Store template text inside each execution: rejected; loses cross-run deduplication and makes evolution analysis harder.

### 5) “Last Updated” Time for PromptTemplate Versions

**Decision**: Treat the PromptTemplate version “last-updated” time as the moment the system first persists the new `(staticId, contentHash)` version. Use that time as the time component of the PromptTemplate `ArtifactKey`.

**Rationale**:

- Works in all environments (packaged apps may not have git metadata available).
- Produces a consistent “when this version entered the system” timeline, which is sufficient for evolution monitoring.

**Alternatives considered**:

- Derive time from git commit timestamps: rejected for initial rollout; requires git availability in runtime environments and introduces additional failure modes.

### 6) Event-Driven Artifact Emission

**Decision**: Emit artifacts as events and process them via existing listener/subscriber infrastructure:

- Add `ArtifactEvent` to `Events.java` (carrying the artifact interface only)
- Implement an `EventListener` that subscribes to ArtifactEvents and incrementally persists/builds the execution artifact tree using ArtifactKey semantics
- Capture existing `Events.GraphEvent` stream as `EventArtifact` nodes inside the execution tree

**Rationale**:

- Matches the constitution’s event-driven node lifecycle and artifact propagation principles.
- Keeps runtime logic decoupled from persistence and downstream analysis.

**Alternatives considered**:

- Direct repository writes from agents: rejected; increases coupling and makes testing harder.

### 7) Test Storage via Docker Compose

**Decision**: Use Postgres via Docker Compose for test environments instead of H2.

**Rationale**:

- Keeps test behavior aligned with production persistence semantics.
- Matches existing project patterns that already rely on Docker Compose for Postgres.

**Alternatives considered**:

- H2: rejected because it can mask production-specific behaviors and schema differences.

## Open Questions (Deferred)

None. All key decisions have been resolved:
- Hash algorithm: SHA-256 with lowercase hex encoding
- Canonical JSON rules: defined in spec.md Technical Specifications
- ArtifactKey format: ULID-based hierarchical keys
- TemplateStaticId format: dot-separated hierarchical identifiers
