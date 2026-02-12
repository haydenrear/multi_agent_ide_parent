# Quickstart: Repository Onboarding Pipeline

## Prerequisites

- Java 21
- Gradle 8.x
- Git CLI with submodule support
- Access to a real repository fixture for integration tests

## 1) Verify compilation

From repository root:

```bash
cd /Users/hayde/IdeaProjects/multi_agent_ide_parent
./gradlew :commit-diff-context:compileJava :multi_agent_ide_java_parent:multi_agent_ide:compileJava
```

## 2) Run unit tests first (Story 4)

```bash
./gradlew :commit-diff-context:test
./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test
```

Targeted unit test examples:

```bash
./gradlew :commit-diff-context:test --tests "*ContiguousSegmentationServiceTest"
./gradlew :commit-diff-context:test --tests "*SetEmbeddingReusePathTest"
./gradlew :commit-diff-context:test --tests "*AddEpisodicMemoryAgentDelegationTest"
```

## 3) Run full integration tests in `multi_agent_ide` (Story 5)

Integration profile:

```bash
./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test -Pprofile=integration
```

Targeted integration run example:

```bash
./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test -Pprofile=integration --tests "*RepoOnboarding*IT"
```

## 4) Integration test fixture setup expectations

- Use a real git repository fixture that includes submodule-aware behavior.
- Mock only:
  - embedding provider
  - Hindsight provider
  - `EpisodicMemoryAgent`
- Keep ingestion, parser traversal, segmentation, orchestration, run logs, and cleanup real.

## 5) Manual verification checklist

1. Confirm onboarding flow still executes through existing parser operations:
   - `SetEmbedding`
   - `GitParser`
   - evolved `RewriteHistory`
   - evolved `AddEpisodicMemory`
2. Confirm contiguous segment plan:
   - non-overlapping ranges
   - full commit coverage
   - min/max commit constraints respected
3. Confirm episodic sequence per segment:
   - one `runAgent` call per segment
   - memory tooling handled inside configured agent runtime
4. Confirm cleanup behavior:
   - ingestion repo removed by default
   - run metadata remains queryable
5. Confirm agent swap behavior:
   - switch episodic implementation profile
   - onboarding orchestration remains stable
