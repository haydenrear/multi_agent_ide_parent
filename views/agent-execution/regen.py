#!/usr/bin/env python3
"""Regenerate the agent-execution view.

Everything involved in executing an agent: the decorator pipeline
(request, result, prompt, tool decorators), agent routes, agent
interfaces/models/contexts, lifecycle handling, blackboard history,
the AgentExecutor, and the retry listener.

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
SKILLS = "skills/multi_agent_ide_skills"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── The Execution Pipeline ────────────────────────────────────────
    ("execution-pipeline", f"{BASE}/service/AgentExecutor.java"),
    ("execution-pipeline", f"{BASE}/service/RequestEnrichment.java"),
    ("execution-pipeline", f"{BASE}/llm/AgentLlmExecutor.java"),
    ("execution-pipeline", f"{BASE}/llm/LlmRunner.java"),
    ("execution-pipeline", f"{BASE}/service/DefaultLlmRunner.java"),
    ("execution-pipeline", f"{BASE}/config/LlmModelSelectionProperties.java"),
    ("execution-pipeline", f"{BASE}/infrastructure/AgentRunner.java"),
    ("execution-pipeline", f"{BASE}/agent/AgentLifecycleHandler.java"),
    ("execution-pipeline", f"{ACP_KT}/AcpChatModel.kt"),
    ("execution-pipeline", f"{ACP_JAVA}/config/AcpChatOptionsString.java"),

    # ── Request/Result Decorator Symmetry ─────────────────────────────
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/RequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/DecorateRequestResults.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/ResultsRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/EnrichRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/DispatchedAgentRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/DispatchedAgentRequestEnrichmentDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/EmitActionStartedRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/PropagateActionRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/RegisterBlackboardHistoryInputRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/RequestContextRepositoryDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/SandboxResolver.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/SetGoalRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/StartWorkflowRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/WorktreeContextRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/request/OrchestratorCollectorRequestDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/ResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/FinalResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/EnrichResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/DispatchedAgentResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/EmitActionCompletedResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/PropagateActionResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/RegisterAndHideInputResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/ArtifactEnrichmentDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/WorkflowGraphResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/WorkflowGraphServiceFinalResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/result/WorktreeContextResultDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/prompt/PromptContextDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/prompt/DefaultPromptContextDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/prompt/LlmCallDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/prompt/ArtifactEmissionLlmCallDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/prompt/FilterPropertiesDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/prompt/PromptHealthCheckLlmCallDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/tools/ToolContextDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/tools/AddAcpTools.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/tools/AddIntellij.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/tools/RemoveIntellij.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/tools/AddMemoryToolCallDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/tools/AddSkillToolContextDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/tools/AddTopologyTools.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/EmitAgentModelArtifactDecorator.java"),
    ("request-result-decorator-symmetry", f"{BASE}/agent/decorator/InterruptAddMessageComposer.java"),

    # ── The Fan-Out Dispatch Process ──────────────────────────────────
    ("fan-out-dispatch", f"{BASE}/agent/DiscoveryRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/DiscoveryDispatchRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/DiscoveryCollectorRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/PlanningRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/PlanningDispatchRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/PlanningCollectorRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/TicketRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/TicketDispatchRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/TicketCollectorRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/OrchestratorRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/OrchestratorCollectorRoute.java"),
    ("fan-out-dispatch", f"{BASE}/agent/ContextManagerRoute.java"),

    # ── Blackboard Planner ──────────────────────────────────────────
    ("blackboard-planner", f"{BASE}/config/BlackboardRoutingPlanner.java"),
    ("blackboard-planner", f"{BASE}/config/BlackboardRoutingPlannerFactory.java"),

    # ── BlackboardHistory: Session State and Loop Detection ───────────
    ("blackboard-history", f"{BASE}/agent/BlackboardHistory.java"),
    ("blackboard-history", f"{BASE}/agent/BlackboardHistoryService.java"),
    ("blackboard-history", f"{BASE}/agent/WorkflowGraphService.java"),
    ("blackboard-history", f"{BASE}/agent/WorkflowGraphState.java"),

    # ── Controller Conversation Pipeline ──────────────────────────────
    ("controller-conversation-pipeline", f"{BASE}/agent/AgentContext.java"),
    ("controller-conversation-pipeline", f"{BASE}/agent/AgentModels.java"),
    ("controller-conversation-pipeline", f"{BASE}/agent/DecoratorContext.java"),
    ("controller-conversation-pipeline", f"{BASE}/agent/UpstreamContext.java"),
    ("controller-conversation-pipeline", f"{BASE}/agent/AgentTopologyTools.java"),
    ("controller-conversation-pipeline", f"{BASE}/service/AgentExecutor.java"),

    # ── Communication Topology ────────────────────────────────────────
    ("communication-topology", f"{BASE}/topology/CommunicationTopologyConfig.java"),
    ("communication-topology", f"{BASE}/topology/CommunicationTopologyProvider.java"),
    ("communication-topology", f"{BASE}/service/AgentCommunicationService.java"),
    ("communication-topology", f"{BASE}/agent/AgentTopologyTools.java"),
    ("communication-topology", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist.md"),
    ("communication-topology", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-orchestrator.md"),
    ("communication-topology", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-discovery-agent.md"),
    ("communication-topology", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-ticket-agent.md"),
    ("communication-topology", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-planning-agent.md"),

    # ── Error Recovery and Retry ──────────────────────────────────────
    ("error-recovery-retry", f"{BASE}/service/ActionRetryListenerImpl.java"),
    ("error-recovery-retry", f"{BASE}/agent/ErrorDescriptor.java"),
    ("error-recovery-retry", f"{BASE}/agent/ErrorTemplates.java"),
    ("error-recovery-retry", f"{BASE}/agent/ResolvedTemplate.java"),
    ("error-recovery-retry", f"{BASE}/agent/DefaultDegenerateLoopPolicy.java"),
    ("error-recovery-retry", f"{BASE}/agent/DegenerateLoopPolicy.java"),
    ("error-recovery-retry", f"{BASE}/events/DegenerateLoopException.java"),
    ("error-recovery-retry", f"{ACP_JAVA}/AcpRetryEventListener.java"),
    ("error-recovery-retry", f"{ACP_JAVA}/AcpSessionRetryContext.java"),

    # ── AgentInterfaces and Metadata Registry ─────────────────────────
    ("agent-interfaces-metadata", f"{BASE}/agent/AgentInterfaces.java"),
    ("agent-interfaces-metadata", f"{BASE}/agent/AgentModels.java"),
    ("agent-interfaces-metadata", f"{BASE}/agent/AgentType.java"),
    ("agent-interfaces-metadata", f"{BASE}/agent/AgentPretty.java"),
    ("agent-interfaces-metadata", f"{BASE}/agent/GraphNodeFactory.java"),
    ("agent-interfaces-metadata", f"{BASE}/agent/SkipPropertyFilter.java"),

    # ── Skill References ──────────────────────────────────────────────
    ("execution-pipeline",    f"{SKILLS}/multi_agent_ide_controller/references/prompt_architecture.md"),
    ("execution-pipeline",    f"{SKILLS}/multi_agent_ide_controller/references/we_are_here_prompt.md"),
    ("error-recovery-retry",  f"{SKILLS}/multi_agent_ide_validation/workflows/validation_workflow.md"),
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
