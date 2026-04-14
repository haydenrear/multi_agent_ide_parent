#!/usr/bin/env python3
"""Regenerate the utility-module view.

Shared abstractions: the Result type hierarchy, EventBus contract,
concurrency primitives, and other notable abstractions.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
UTIL = f"{JAVA}/utilitymodule/src/main/java/com/hayden/utilitymodule"
SKILLS = "skills/multi_agent_ide_skills"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── The Result Type Hierarchy ─────────────────────────────────────
    ("result-type-hierarchy", f"{UTIL}/result/Result.java"),
    ("result-type-hierarchy", f"{UTIL}/result/AsyncResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/ClosableResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/ManyResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/MutableResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/OneResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/agg/Agg.java"),
    ("result-type-hierarchy", f"{UTIL}/result/agg/AggregateError.java"),
    ("result-type-hierarchy", f"{UTIL}/result/agg/AggregateParamError.java"),
    ("result-type-hierarchy", f"{UTIL}/result/agg/Responses.java"),
    ("result-type-hierarchy", f"{UTIL}/result/async/CompletableFutureResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/async/FluxResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/async/IAsyncManyResultItem.java"),
    ("result-type-hierarchy", f"{UTIL}/result/async/IAsyncResultItem.java"),
    ("result-type-hierarchy", f"{UTIL}/result/async/MonoResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/closable/ClosableMonitor.java"),
    ("result-type-hierarchy", f"{UTIL}/result/error/BindingResultErr.java"),
    ("result-type-hierarchy", f"{UTIL}/result/error/Err.java"),
    ("result-type-hierarchy", f"{UTIL}/result/error/SingleError.java"),
    ("result-type-hierarchy", f"{UTIL}/result/error/StdErr.java"),
    ("result-type-hierarchy", f"{UTIL}/result/map/AggregateResultCollectors.java"),
    ("result-type-hierarchy", f"{UTIL}/result/map/ParameterizedResultCollectors.java"),
    ("result-type-hierarchy", f"{UTIL}/result/map/ResultCollectors.java"),
    ("result-type-hierarchy", f"{UTIL}/result/map/StreamResultCollector.java"),
    ("result-type-hierarchy", f"{UTIL}/result/ok/ClosableOk.java"),
    ("result-type-hierarchy", f"{UTIL}/result/ok/MutableOk.java"),
    ("result-type-hierarchy", f"{UTIL}/result/ok/Ok.java"),
    ("result-type-hierarchy", f"{UTIL}/result/ok/ResponseEntityOk.java"),
    ("result-type-hierarchy", f"{UTIL}/result/ok/StdOk.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_many/IManyResultItem.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_many/IStreamResultItem.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_many/ListResultItem.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_many/StreamResultItem.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_single/ISingleResultItem.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/many/stream/ResultStreamWrapper.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/many/stream/StreamCache.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/many/stream/StreamResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/many/stream/StreamResultOptions.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/many/stream/StreamWrapper.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/many/stream/stream_cache/CachableStream.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/many/stream/stream_cache/CachingOperations.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/one/ClosableOne.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/one/MutableOne.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/one/One.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/one/ResponseEntityOne.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_support/one/ResultTy.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_ty/CachedCollectedResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_ty/ClosableResult.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_ty/IResultItem.java"),
    ("result-type-hierarchy", f"{UTIL}/result/res_ty/ResultTyResult.java"),
    ("result-type-hierarchy", f"{UTIL}/Either.java"),

    # ── EventBus Contract ─────────────────────────────────────────────
    ("eventbus-contract", f"{UTIL}/Cdc.java"),

    # ── Concurrency Primitives ────────────────────────────────────────
    ("concurrency-primitives", f"{UTIL}/concurrent/OnceCell.java"),
    ("concurrency-primitives", f"{UTIL}/concurrent/striped/StripedLock.java"),
    ("concurrency-primitives", f"{UTIL}/concurrent/striped/StripedLockAspect.java"),

    # ── Other Notable Abstractions ────────────────────────────────────
    ("other-abstractions", f"{UTIL}/ArrayUtilUtilities.java"),
    ("other-abstractions", f"{UTIL}/ByteUtility.java"),
    ("other-abstractions", f"{UTIL}/CollectionFunctions.java"),
    ("other-abstractions", f"{UTIL}/CombinationUtilities.java"),
    ("other-abstractions", f"{UTIL}/DateUtilities.java"),
    ("other-abstractions", f"{UTIL}/MapFunctions.java"),
    ("other-abstractions", f"{UTIL}/NdUtilities.java"),
    ("other-abstractions", f"{UTIL}/RandomUtils.java"),
    ("other-abstractions", f"{UTIL}/TestAnnotation.java"),
    ("other-abstractions", f"{UTIL}/TimeUnitEnum.java"),
    ("other-abstractions", f"{UTIL}/TriFunction.java"),
    ("other-abstractions", f"{UTIL}/WritableUtils.java"),
    ("other-abstractions", f"{UTIL}/assert_util/AssertUtil.java"),
    ("other-abstractions", f"{UTIL}/cast/UtilityClassUtils.java"),
    ("other-abstractions", f"{UTIL}/config/EnvConfigProps.java"),
    ("other-abstractions", f"{UTIL}/ctx/PreSetConfigurationProperties.java"),
    ("other-abstractions", f"{UTIL}/ctx/PresetProperties.java"),
    ("other-abstractions", f"{UTIL}/ctx/PrototypeBean.java"),
    ("other-abstractions", f"{UTIL}/ctx/PrototypeScope.java"),
    ("other-abstractions", f"{UTIL}/db/DbDataSourceTrigger.java"),
    ("other-abstractions", f"{UTIL}/db/WithDb.java"),
    ("other-abstractions", f"{UTIL}/db/WithDbAspect.java"),
    ("other-abstractions", f"{UTIL}/fn/Reducer.java"),
    ("other-abstractions", f"{UTIL}/free/Effect.java"),
    ("other-abstractions", f"{UTIL}/free/Free.java"),
    ("other-abstractions", f"{UTIL}/free/Interpreter.java"),
    ("other-abstractions", f"{UTIL}/gather/DistinctBy.java"),
    ("other-abstractions", f"{UTIL}/git/RepoUtil.java"),
    ("other-abstractions", f"{UTIL}/git/WildcardPathFilter.java"),
    ("other-abstractions", f"{UTIL}/io/ArchiveUtils.java"),
    ("other-abstractions", f"{UTIL}/io/FileUtils.java"),
    ("other-abstractions", f"{UTIL}/iter/BreadthFirstLazyDelegatingIterator.java"),
    ("other-abstractions", f"{UTIL}/iter/DepthFirstLazyDelegatingIterator.java"),
    ("other-abstractions", f"{UTIL}/iter/LazyIterator.java"),
    ("other-abstractions", f"{UTIL}/iter/ZigZagIterator.java"),
    ("other-abstractions", f"{UTIL}/kafka/KafkaProperties.java"),
    ("other-abstractions", f"{UTIL}/matchers/JwtMatcher.java"),
    ("other-abstractions", f"{UTIL}/mcp/ctx/McpRequestContext.java"),
    ("other-abstractions", f"{UTIL}/otel/DisableOtelConfiguration.java"),
    ("other-abstractions", f"{UTIL}/proxies/ProxyUtil.java"),
    ("other-abstractions", f"{UTIL}/reflection/Defaults.java"),
    ("other-abstractions", f"{UTIL}/reflection/NullHasMeaning.java"),
    ("other-abstractions", f"{UTIL}/reflection/ParameterAnnotationUtils.java"),
    ("other-abstractions", f"{UTIL}/reflection/PathUtil.java"),
    ("other-abstractions", f"{UTIL}/reflection/TypeReferenceDelegate.java"),
    ("other-abstractions", f"{UTIL}/schema/DelegatingSchemaReplacer.java"),
    ("other-abstractions", f"{UTIL}/schema/SchemaReplacer.java"),
    ("other-abstractions", f"{UTIL}/schema/SpecialJsonSchemaGenerator.java"),
    ("other-abstractions", f"{UTIL}/schema/SpecialMethodToolCallbackProvider.java"),
    ("other-abstractions", f"{UTIL}/schema/SpecialMethodToolCallbackProviderFactory.java"),
    ("other-abstractions", f"{UTIL}/security/KeyConfigProperties.java"),
    ("other-abstractions", f"{UTIL}/security/KeyFiles.java"),
    ("other-abstractions", f"{UTIL}/security/SecurityUtils.java"),
    ("other-abstractions", f"{UTIL}/security/SignatureUtil.java"),
    ("other-abstractions", f"{UTIL}/sort/GraphSort.java"),
    ("other-abstractions", f"{UTIL}/stream/StreamUtil.java"),
    ("other-abstractions", f"{UTIL}/string/StringUtil.java"),
    ("other-abstractions", f"{UTIL}/telemetry/TelemetryAttributesProvider.java"),
    ("other-abstractions", f"{UTIL}/telemetry/log/AttributeProvider.java"),
    ("other-abstractions", f"{UTIL}/telemetry/log/FluentDRestTemplateSender.java"),
    ("other-abstractions", f"{UTIL}/telemetry/log/LoggingConfig.java"),
    ("other-abstractions", f"{UTIL}/telemetry/log/TelemetryAttributes.java"),
    ("other-abstractions", f"{UTIL}/telemetry/prelog/PreTelemetryAttributes.java"),

    # ── Skill References ──────────────────────────────────────────────
    ("other-abstractions", f"{SKILLS}/multi_agent_ide_deploy/scripts/clone_or_pull.py"),
    ("other-abstractions", f"{SKILLS}/multi_agent_ide_deploy/scripts/deploy_restart.py"),
]


def _cleanup_stale_symlinks(view_dir: Path):
    """Remove all symlinks under the view directory (except in mental-models/)."""
    for item in sorted(view_dir.rglob("*")):
        if item.is_symlink() and "mental-models" not in item.parts:
            item.unlink()
    for item in sorted(view_dir.rglob("*"), reverse=True):
        if item.is_dir() and "mental-models" not in str(item.relative_to(view_dir)):
            try:
                item.rmdir()
            except OSError:
                pass


def regenerate():
    (VIEW_DIR / "mental-models").mkdir(exist_ok=True)
    _cleanup_stale_symlinks(VIEW_DIR)

    for section_dir, rel_path in SECTION_FILES:
        source = REPO_ROOT / rel_path
        if not source.exists():
            print(f"  WARN: {rel_path} does not exist, skipping")
            continue
        link_dir = VIEW_DIR / section_dir
        link = link_dir / source.name
        link_dir.mkdir(parents=True, exist_ok=True)
        if not link.exists():
            rel_target = os.path.relpath(source, link.parent)
            link.symlink_to(rel_target)


if __name__ == "__main__":
    regenerate()
