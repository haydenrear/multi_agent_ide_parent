#!/usr/bin/env python3
"""Regenerate the filter-propagation view.

Three sibling data-layer pipelines — filter, propagation, and
transformation — plus the shared layer hierarchy and AI execution pattern.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
BASE = f"{JAVA}/multi_agent_ide/src/main/java/com/hayden/multiagentide"
SKILLS = "skills/multi_agent_ide_skills"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── Filter Evaluation End-to-End ──────────────────────────────────
    ("filter-evaluation", f"{BASE}/filter/FilterFn.java"),
    ("filter-evaluation", f"{BASE}/filter/integration/ControllerEventFilterIntegration.java"),
    ("filter-evaluation", f"{BASE}/filter/integration/PathFilterIntegration.java"),
    ("filter-evaluation", f"{BASE}/filter/model/AiPathFilter.java"),
    ("filter-evaluation", f"{BASE}/filter/model/Filter.java"),
    ("filter-evaluation", f"{BASE}/filter/model/FilterSource.java"),
    ("filter-evaluation", f"{BASE}/filter/model/PathFilter.java"),
    ("filter-evaluation", f"{BASE}/filter/model/decision/FilterDecisionRecord.java"),
    ("filter-evaluation", f"{BASE}/filter/model/executor/AiFilterTool.java"),
    ("filter-evaluation", f"{BASE}/filter/model/executor/BinaryExecutor.java"),
    ("filter-evaluation", f"{BASE}/filter/model/executor/ExecutableTool.java"),
    ("filter-evaluation", f"{BASE}/filter/model/executor/JavaFunctionExecutor.java"),
    ("filter-evaluation", f"{BASE}/filter/model/executor/PythonExecutor.java"),
    ("filter-evaluation", f"{BASE}/filter/model/interpreter/DispatchingInterpreter.java"),
    ("filter-evaluation", f"{BASE}/filter/model/interpreter/Interpreter.java"),
    ("filter-evaluation", f"{BASE}/filter/model/interpreter/InterpreterError.java"),
    ("filter-evaluation", f"{BASE}/filter/service/AiFilterSessionResolver.java"),
    ("filter-evaluation", f"{BASE}/filter/service/AiFilterToolHydration.java"),
    ("filter-evaluation", f"{BASE}/filter/service/FilterAttachableCatalogService.java"),
    ("filter-evaluation", f"{BASE}/filter/service/FilterDecisionQueryService.java"),
    ("filter-evaluation", f"{BASE}/filter/service/FilterDescriptor.java"),
    ("filter-evaluation", f"{BASE}/filter/service/FilterExecutionService.java"),
    ("filter-evaluation", f"{BASE}/filter/service/FilterResult.java"),
    ("filter-evaluation", f"{BASE}/filter/service/PolicyDiscoveryService.java"),
    ("filter-evaluation", f"{BASE}/filter/service/PolicyRegistrationService.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/FilterPolicyController.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/DeactivatePolicyRequest.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/DeactivatePolicyResponse.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/PolicyRegistrationRequest.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/PolicyRegistrationResponse.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/PutPolicyLayerRequest.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/PutPolicyLayerResponse.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/ReadAttachableTargetsResponse.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/ReadLayerChildrenRequest.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/ReadLayerChildrenResponse.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/ReadPoliciesByLayerRequest.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/ReadPoliciesByLayerResponse.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/ReadRecentFilteredRecordsRequest.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/ReadRecentFilteredRecordsResponse.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/TogglePolicyLayerRequest.java"),
    ("filter-evaluation", f"{BASE}/filter/controller/dto/TogglePolicyLayerResponse.java"),
    ("filter-evaluation", f"{BASE}/filter/prompt/ActiveFiltersPromptContributorFactory.java"),
    ("filter-evaluation", f"{BASE}/filter/prompt/FilteredPromptContributorAdapter.java"),
    ("filter-evaluation", f"{BASE}/filter/prompt/FilteredPromptContributorAdapterFactory.java"),
    ("filter-evaluation", f"{BASE}/filter/repository/FilterDecisionRecordEntity.java"),
    ("filter-evaluation", f"{BASE}/filter/repository/FilterDecisionRecordRepository.java"),
    ("filter-evaluation", f"{BASE}/filter/repository/LayerEntity.java"),
    ("filter-evaluation", f"{BASE}/filter/repository/LayerRepository.java"),
    ("filter-evaluation", f"{BASE}/filter/repository/PolicyRegistrationEntity.java"),
    ("filter-evaluation", f"{BASE}/filter/repository/PolicyRegistrationRepository.java"),

    # ── Propagation: Observer Pattern on Agent I/O ────────────────────
    ("propagation-observer", f"{BASE}/propagation/integration/ActionRequestPropagationIntegration.java"),
    ("propagation-observer", f"{BASE}/propagation/integration/ActionResponsePropagationIntegration.java"),
    ("propagation-observer", f"{BASE}/propagation/model/Propagation.java"),
    ("propagation-observer", f"{BASE}/propagation/service/AiPropagatorToolHydration.java"),
    ("propagation-observer", f"{BASE}/propagation/service/AutoAiPropagatorBootstrap.java"),
    ("propagation-observer", f"{BASE}/propagation/service/PropagationExecutionService.java"),
    ("propagation-observer", f"{BASE}/propagation/service/PropagationItemService.java"),
    ("propagation-observer", f"{BASE}/propagation/service/PropagationRecordQueryService.java"),
    ("propagation-observer", f"{BASE}/propagation/service/PropagatorAttachableCatalogService.java"),
    ("propagation-observer", f"{BASE}/propagation/service/PropagatorDiscoveryService.java"),
    ("propagation-observer", f"{BASE}/propagation/service/PropagatorRegistrationService.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/PropagationController.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/PropagationRecordController.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/PropagatorController.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/ActivatePropagatorResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/DeactivatePropagatorResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/PropagatorRegistrationRequest.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/PropagatorRegistrationResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/PutPropagatorLayerRequest.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/PutPropagatorLayerResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/ReadPropagationItemsResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/ReadPropagationRecordsResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/ReadPropagatorAttachableTargetsResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/ReadPropagatorsByLayerRequest.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/ReadPropagatorsByLayerResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/RecentPropagationItemsByNodeRequest.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/RecentPropagationItemsByNodeResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/ResolvePropagationItemRequest.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/ResolvePropagationItemResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/UpdateAllSessionModeResponse.java"),
    ("propagation-observer", f"{BASE}/propagation/controller/dto/UpdateSessionModeRequest.java"),
    ("propagation-observer", f"{BASE}/propagation/repository/PropagationItemEntity.java"),
    ("propagation-observer", f"{BASE}/propagation/repository/PropagationItemRepository.java"),
    ("propagation-observer", f"{BASE}/propagation/repository/PropagationRecordEntity.java"),
    ("propagation-observer", f"{BASE}/propagation/repository/PropagationRecordRepository.java"),
    ("propagation-observer", f"{BASE}/propagation/repository/PropagatorRegistrationEntity.java"),
    ("propagation-observer", f"{BASE}/propagation/repository/PropagatorRegistrationRepository.java"),

    # ── Transformation: Response Body Rewriting ───────────────────────
    ("transformation-rewriting", f"{BASE}/transformation/integration/ControllerEndpointTransformationIntegration.java"),
    ("transformation-rewriting", f"{BASE}/transformation/integration/ControllerResponseTransformationAdvice.java"),
    ("transformation-rewriting", f"{BASE}/transformation/service/AiTransformerToolHydration.java"),
    ("transformation-rewriting", f"{BASE}/transformation/service/TransformationRecordQueryService.java"),
    ("transformation-rewriting", f"{BASE}/transformation/service/TransformerAttachableCatalogService.java"),
    ("transformation-rewriting", f"{BASE}/transformation/service/TransformerDiscoveryService.java"),
    ("transformation-rewriting", f"{BASE}/transformation/service/TransformerExecutionService.java"),
    ("transformation-rewriting", f"{BASE}/transformation/service/TransformerRegistrationService.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/TransformationRecordController.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/TransformerController.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/dto/DeactivateTransformerResponse.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/dto/PutTransformerLayerRequest.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/dto/PutTransformerLayerResponse.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/dto/ReadTransformationRecordsResponse.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/dto/ReadTransformerAttachableTargetsResponse.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/dto/ReadTransformersByLayerRequest.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/dto/ReadTransformersByLayerResponse.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/dto/TransformerRegistrationRequest.java"),
    ("transformation-rewriting", f"{BASE}/transformation/controller/dto/TransformerRegistrationResponse.java"),
    ("transformation-rewriting", f"{BASE}/transformation/repository/TransformationRecordEntity.java"),
    ("transformation-rewriting", f"{BASE}/transformation/repository/TransformationRecordRepository.java"),
    ("transformation-rewriting", f"{BASE}/transformation/repository/TransformerRegistrationEntity.java"),
    ("transformation-rewriting", f"{BASE}/transformation/repository/TransformerRegistrationRepository.java"),

    # ── The Layer Hierarchy ───────────────────────────────────────────
    ("layer-hierarchy", f"{BASE}/filter/service/FilterLayerCatalog.java"),
    ("layer-hierarchy", f"{BASE}/filter/service/LayerHierarchyBootstrap.java"),
    ("layer-hierarchy", f"{BASE}/filter/service/LayerIdResolver.java"),
    ("layer-hierarchy", f"{BASE}/filter/service/LayerService.java"),
    ("layer-hierarchy", f"{BASE}/filter/model/layer/DefaultPathFilterContext.java"),
    ("layer-hierarchy", f"{BASE}/filter/model/layer/FilterContext.java"),
    ("layer-hierarchy", f"{BASE}/filter/model/layer/GraphEventObjectContext.java"),
    ("layer-hierarchy", f"{BASE}/filter/model/layer/Layer.java"),
    ("layer-hierarchy", f"{BASE}/filter/model/layer/LayerCtx.java"),
    ("layer-hierarchy", f"{BASE}/filter/model/layer/LayerEntity.java"),
    ("layer-hierarchy", f"{BASE}/filter/model/layer/PromptContributorContext.java"),
    ("layer-hierarchy", f"{BASE}/filter/model/policy/PolicyLayerBinding.java"),

    # ── The AI Execution Pattern ──────────────────────────────────────
    ("ai-execution-pattern", f"{BASE}/filter/config/ContextObjectMapperProvider.java"),
    ("ai-execution-pattern", f"{BASE}/filter/config/FilterConfig.java"),
    ("ai-execution-pattern", f"{BASE}/filter/config/FilterConfigProperties.java"),
    ("ai-execution-pattern", f"{BASE}/filter/config/FilterContextFactory.java"),
    ("ai-execution-pattern", f"{BASE}/filter/config/FilterModelModule.java"),
    ("ai-execution-pattern", f"{BASE}/propagation/config/PropagationModelModule.java"),
    ("ai-execution-pattern", f"{BASE}/transformation/config/TransformationModelModule.java"),

    # ── Skill References ──────────────────────────────────────────────
    ("filter-evaluation",     f"{SKILLS}/multi_agent_ide_controller/references/filter_policy_contracts.md"),
    ("filter-evaluation",     f"{SKILLS}/multi_agent_ide_contracts/references/filter_instruction_contract.schema.md"),
    ("propagation-observer",  f"{SKILLS}/multi_agent_ide_controller/references/propagator_contracts.md"),
    ("propagation-observer",  f"{SKILLS}/multi_agent_ide_contracts/references/resolution_types.schema.md"),
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
