#!/usr/bin/env python3
"""Regenerate the agent-framework view.

Embabel integration, agent type system, tool system, LLM runner,
and skill system.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
BASE = f"{JAVA}/multi_agent_ide/src/main/java/com/hayden/multiagentide"
ACP = f"{JAVA}/acp-cdc-ai/src/main"
ACP_KT = f"{ACP}/kotlin/com/hayden/acp_cdc_ai/acp"
ACP_JAVA = f"{ACP}/java/com/hayden/acp_cdc_ai/acp"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── Embabel Integration ──────────────────────────────────────────
    ("embabel-integration", f"{BASE}/embabel/EmbabelUtil.java"),
    ("embabel-integration", f"{BASE}/agent/AgentLifecycleHandler.java"),
    ("embabel-integration", f"{BASE}/agent/AgentContext.java"),
    ("embabel-integration", f"{BASE}/config/MultiAgentEmbabelConfig.java"),
    ("embabel-integration", f"{ACP_KT}/AcpChatModel.kt"),
    ("embabel-integration", f"{ACP_KT}/AcpSessionManager.kt"),
    ("embabel-integration", f"{ACP_JAVA}/config/AcpModelProperties.java"),

    # ── Agent Type System ────────────────────────────────────────────
    ("agent-type-system", f"{BASE}/agent/AgentType.java"),
    ("agent-type-system", f"{BASE}/agent/AgentInterfaces.java"),
    ("agent-type-system", f"{BASE}/agent/AgentModels.java"),
    ("agent-type-system", f"{BASE}/agent/AgentPretty.java"),
    ("agent-type-system", f"{BASE}/agent/DecoratorContext.java"),
    ("agent-type-system", f"{BASE}/agent/UpstreamContext.java"),
    ("agent-type-system", f"{BASE}/agent/ErrorDescriptor.java"),
    ("agent-type-system", f"{BASE}/agent/ErrorTemplates.java"),
    ("agent-type-system", f"{BASE}/agent/ResolvedTemplate.java"),
    ("agent-type-system", f"{BASE}/agent/DefaultDegenerateLoopPolicy.java"),
    ("agent-type-system", f"{BASE}/agent/DegenerateLoopPolicy.java"),
    ("agent-type-system", f"{BASE}/agent/SkipPropertyFilter.java"),
    ("agent-type-system", f"{BASE}/agent/GraphNodeFactory.java"),

    # ── Agent Routes ─────────────────────────────────────────────────
    ("agent-routes", f"{BASE}/agent/OrchestratorRoute.java"),
    ("agent-routes", f"{BASE}/agent/OrchestratorCollectorRoute.java"),
    ("agent-routes", f"{BASE}/agent/DiscoveryRoute.java"),
    ("agent-routes", f"{BASE}/agent/DiscoveryDispatchRoute.java"),
    ("agent-routes", f"{BASE}/agent/DiscoveryCollectorRoute.java"),
    ("agent-routes", f"{BASE}/agent/PlanningRoute.java"),
    ("agent-routes", f"{BASE}/agent/PlanningDispatchRoute.java"),
    ("agent-routes", f"{BASE}/agent/PlanningCollectorRoute.java"),
    ("agent-routes", f"{BASE}/agent/TicketRoute.java"),
    ("agent-routes", f"{BASE}/agent/TicketDispatchRoute.java"),
    ("agent-routes", f"{BASE}/agent/TicketCollectorRoute.java"),
    ("agent-routes", f"{BASE}/agent/ContextManagerRoute.java"),

    # ── Tool System ──────────────────────────────────────────────────
    ("tool-system", f"{BASE}/tool/ToolAbstraction.java"),
    ("tool-system", f"{BASE}/tool/ToolContext.java"),
    ("tool-system", f"{BASE}/tool/EmbabelToolObjectProvider.java"),
    ("tool-system", f"{BASE}/tool/EmbabelToolObjectRegistry.java"),
    ("tool-system", f"{BASE}/tool/LazyToolObjectRegistration.java"),
    ("tool-system", f"{BASE}/tool/McpToolObjectRegistrar.java"),
    ("tool-system", f"{BASE}/agent/AgentTools.java"),
    ("tool-system", f"{BASE}/agent/AgentTopologyTools.java"),
    ("tool-system", f"{BASE}/agent/AcpTooling.java"),
    ("tool-system", f"{BASE}/agent/AskUserQuestionTool.java"),
    ("tool-system", f"{BASE}/agent/AskUserQuestionToolAdapter.java"),
    ("tool-system", f"{BASE}/agent/AgentQuestionAnswerFunction.java"),
    ("tool-system", f"{BASE}/agent/ContextManagerTools.java"),

    # ── LLM Runner ───────────────────────────────────────────────────
    ("llm-runner", f"{BASE}/llm/AgentLlmExecutor.java"),
    ("llm-runner", f"{BASE}/llm/LlmRunner.java"),
    ("llm-runner", f"{BASE}/service/DefaultLlmRunner.java"),
    ("llm-runner", f"{BASE}/config/LlmModelSelectionProperties.java"),
    ("llm-runner", f"{BASE}/retry/RetryConfigProperties.java"),

    # ── Skill System ─────────────────────────────────────────────────
    ("skill-system", f"{BASE}/skills/SkillFinder.java"),
    ("skill-system", f"{BASE}/skills/SkillProperties.java"),

    # ── BlackboardHistory ────────────────────────────────────────────
    ("blackboard-history", f"{BASE}/agent/BlackboardHistory.java"),
    ("blackboard-history", f"{BASE}/agent/BlackboardHistoryService.java"),
    ("blackboard-history", f"{BASE}/agent/WorkflowGraphService.java"),
    ("blackboard-history", f"{BASE}/agent/WorkflowGraphState.java"),

    # ── Decorator Chain ──────────────────────────────────────────────
    ("decorator-chain", f"{BASE}/agent/decorator/request/DecorateRequestResults.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/RequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/ResultsRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/EnrichRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/DispatchedAgentRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/DispatchedAgentRequestEnrichmentDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/EmitActionStartedRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/OrchestratorCollectorRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/PropagateActionRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/RegisterBlackboardHistoryInputRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/RequestContextRepositoryDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/SandboxResolver.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/SetGoalRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/StartWorkflowRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/request/WorktreeContextRequestDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/ResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/FinalResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/EnrichResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/DispatchedAgentResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/EmitActionCompletedResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/PropagateActionResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/RegisterAndHideInputResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/ArtifactEnrichmentDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/WorkflowGraphResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/WorkflowGraphServiceFinalResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/result/WorktreeContextResultDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/prompt/PromptContextDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/prompt/DefaultPromptContextDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/prompt/LlmCallDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/prompt/ArtifactEmissionLlmCallDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/prompt/FilterPropertiesDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/prompt/PromptHealthCheckLlmCallDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/tools/ToolContextDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/tools/AddAcpTools.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/tools/AddIntellij.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/tools/RemoveIntellij.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/tools/AddMemoryToolCallDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/tools/AddSkillToolContextDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/tools/AddTopologyTools.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/EmitAgentModelArtifactDecorator.java"),
    ("decorator-chain", f"{BASE}/agent/decorator/InterruptAddMessageComposer.java"),

    # ── Template Types ───────────────────────────────────────────────
    ("template-types", f"{BASE}/template/ConsolidationTemplate.java"),
    ("template-types", f"{BASE}/template/DelegationTemplate.java"),
    ("template-types", f"{BASE}/template/DiscoveryReport.java"),
    ("template-types", f"{BASE}/template/MemoryReference.java"),
    ("template-types", f"{BASE}/template/PlanningTicket.java"),

    # ── Session Cleanup ──────────────────────────────────────────────
    ("session-cleanup", f"{BASE}/agent/AcpSessionCleanupService.java"),
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
