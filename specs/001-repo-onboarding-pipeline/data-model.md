# Data Model: Repository Onboarding Pipeline

## Entities

### OnboardingRun
- **Purpose**: Top-level lifecycle record for one onboarding execution.
- **Fields**:
  - `runId` (ULID/String, required, immutable)
  - `repoUrl` (String, required)
  - `requestedRef` (String, optional; default `HEAD`)
  - `resolvedHeadSha` (String, required once run starts)
  - `status` (Enum, required)
  - `startedAt` (Instant, required)
  - `completedAt` (Instant, optional)
  - `keepIngestionRepo` (Boolean, required)
  - `dryRun` (Boolean, required)
  - `errorSummary` (String, optional)
- **Validation rules**:
  - `runId` unique.
  - `status=COMPLETED` requires `completedAt` and no fatal segment failures.
  - `status=FAILED` requires non-empty `errorSummary`.

### IngestionManifest
- **Purpose**: Reproducibility snapshot of imported repository and submodules.
- **Fields**:
  - `runId` (FK -> OnboardingRun, required)
  - `sourceRepo` (String, required)
  - `sourceHeadSha` (String, required)
  - `ingestionRepoPath` (String, required)
  - `rootPrefix` (String, required)
  - `submoduleMappings` (List<SubmoduleMapping>, required)
  - `importedCommitCount` (Integer, required, >= 0)
  - `createdAt` (Instant, required)
- **Validation rules**:
  - Prefixes are stable and unique per mapping.
  - `importedCommitCount` equals sum of imported commit records for the run.

### SubmoduleMapping
- **Purpose**: Mapping from source submodule repo to ingestion prefix.
- **Fields**:
  - `name` (String, required)
  - `sourceUrl` (String, required)
  - `sourceRef` (String, required)
  - `targetPrefix` (String, required)
- **Validation rules**:
  - `targetPrefix` unique within one manifest.

### CommitEmbeddingRecord (stored on existing `CommitDiff`)
- **Purpose**: Embedding output metadata for one eligible commit, stored by extending the existing `commitdiffcontext.git.entity.CommitDiff` object.
- **Fields**:
  - `runId` (FK -> OnboardingRun, required)
  - `commitSha` (String, required)
  - `commitTimestamp` (Instant, required)
  - `embeddingVersion` (String, required)
  - `vectorRef` (String or external ref, optional)
  - `status` (Enum: PRIMARY | FALLBACK | SKIPPED, required)
  - `fallbackReason` (String, optional)
  - `changedPathPrefixes` (List<String>, required)
  - `diffStats` (Object: inserted/deleted/filesChanged, required)
- **Validation rules**:
  - Unique key: (`runId`, `commitSha`, `embeddingVersion`).
  - `status=PRIMARY` requires non-empty `vectorRef`.
  - `status=FALLBACK` or `SKIPPED` requires `fallbackReason`.

### SegmentPlan
- **Purpose**: Deterministic segmentation output for a run.
- **Fields**:
  - `runId` (FK -> OnboardingRun, required, unique)
  - `strategy` (Enum: DRIFT | DP | HDBSCAN_EXPERIMENTAL, required)
  - `requestedK` (Integer, optional)
  - `effectiveK` (Integer, required, > 0)
  - `minCommitsPerSegment` (Integer, required, > 0)
  - `maxCommitsPerSegment` (Integer, required, > 0)
  - `seed` (Long/String, required)
  - `segments` (List<SegmentRange>, required)
  - `createdAt` (Instant, required)
- **Validation rules**:
  - Segments are contiguous and non-overlapping.
  - Segments fully cover the ordered commit list exactly once.
  - Every segment size in `[minCommitsPerSegment, maxCommitsPerSegment]` unless explicitly waived and logged.

### SegmentRange
- **Purpose**: One contiguous slice of commit history.
- **Fields**:
  - `index` (Integer, required, starts at 1)
  - `startCommitSha` (String, required)
  - `endCommitSha` (String, required)
  - `commitCount` (Integer, required, > 0)
  - `startTimestamp` (Instant, required)
  - `endTimestamp` (Instant, required)
- **Validation rules**:
  - `startTimestamp <= endTimestamp`.
  - Segments are ordered by `index`.

### SegmentExecution
- **Purpose**: Execution telemetry for episodic processing of one segment.
- **Fields**:
  - `runId` (FK -> OnboardingRun, required)
  - `segmentIndex` (FK -> SegmentRange.index, required)
  - `status` (Enum: PENDING | RUNNING | RETRYING | COMPLETED | FAILED, required)
  - `attemptCount` (Integer, required, >= 0)
  - `agentRunCalled` (Boolean, required)
  - `episodesRetained` (Integer, required, >= 0)
  - `handoffSummary` (String, optional, bounded length)
  - `startedAt` (Instant, required)
  - `completedAt` (Instant, optional)
  - `lastError` (String, optional)
- **Validation rules**:
  - `status=COMPLETED` requires `agentRunCalled=true`.
  - `attemptCount` <= configured retry max + 1.

### EpisodicMemoryAgentContractModel
- **Purpose**: Contract-level model for app-provided memory delegation.
- **Fields**:
  - `runScope` (`repoId`, `runId`, required)
  - `segmentScope` (`segmentIndex`, commit bounds, required)
  - `provenance` (Map<String,Object>, required)
  - `operation` (`runAgent`, required)
- **Validation rules**:
  - Exactly one `runAgent` invocation occurs per segment attempt.
  - Memory tool usage is implicit within agent runtime configuration (not onboarding orchestration).
  - Temporal marker preservation for unchanged memories is handled by agent/Hindsight runtime behavior.

### RunLogEntry
- **Purpose**: Minimal operational auditing for status and retries.
- **Fields**:
  - `runId` (FK -> OnboardingRun, required)
  - `timestamp` (Instant, required)
  - `level` (Enum: INFO | WARN | ERROR, required)
  - `component` (String, required)
  - `message` (String, required)
  - `segmentIndex` (Integer, optional)
  - `errorCode` (String, optional)

## Relationships

- `OnboardingRun` 1 -> 1 `IngestionManifest`
- `OnboardingRun` 1 -> N `CommitEmbeddingRecord` (materialized on `CommitDiff`)
- `OnboardingRun` 1 -> 1 `SegmentPlan`
- `SegmentPlan` 1 -> N `SegmentRange`
- `OnboardingRun` 1 -> N `SegmentExecution`
- `OnboardingRun` 1 -> N `RunLogEntry`

## State Transitions

### OnboardingRun.status

`CREATED -> PREPARING_INGESTION -> EMBEDDING -> SEGMENTING -> EPISODIC_PROCESSING -> CLEANUP -> COMPLETED`

Failure transitions:
- Any active state -> `FAILED` on hard-stop error.
- `CLEANUP -> CLEANUP_WARNING` when cleanup fails but processing completed.

### SegmentExecution.status

`PENDING -> RUNNING -> COMPLETED`

Retry path:
- `RUNNING -> RETRYING -> RUNNING`
- `RETRYING -> FAILED` when retry budget exhausted.

## Invariants

- Segment execution order is strictly ascending by `segmentIndex`.
- No concurrent `RUNNING` segments within the same `runId`.
- The onboarding flow reuses existing parser operation contracts (`SetEmbedding`, `GitParser`, evolved `AddEpisodicMemory`, evolved `RewriteHistory`) rather than introducing a detached duplicate chain.
