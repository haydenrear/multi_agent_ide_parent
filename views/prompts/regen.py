#!/usr/bin/env python3
"""Regenerate the prompts view.

Jinja templates, prompt contributors, assembly process, retry-aware
filtering, AgentPretty serialization, and prompt health check.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
BASE_JAVA = f"{JAVA}/multi_agent_ide/src/main/java/com/hayden/multiagentide"
BASE_RESOURCES = f"{JAVA}/multi_agent_ide/src/main/resources/prompts"
ACP_JAVA = f"{JAVA}/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp"
SKILLS = "skills/multi_agent_ide_skills"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── Prompt Assembly Process ───────────────────────────────────────
    ("prompt-assembly-process", f"{BASE_JAVA}/prompt/PromptAssembly.java"),
    ("prompt-assembly-process", f"{BASE_JAVA}/prompt/PromptContext.java"),
    ("prompt-assembly-process", f"{BASE_JAVA}/prompt/PromptContextFactory.java"),
    ("prompt-assembly-process", f"{BASE_JAVA}/prompt/PromptContributorService.java"),
    ("prompt-assembly-process", f"{BASE_JAVA}/prompt/PromptContributorRegistry.java"),
    ("prompt-assembly-process", f"{BASE_JAVA}/prompt/PromptTemplateLoader.java"),
    ("prompt-assembly-process", f"{BASE_JAVA}/prompt/PromptTemplateStore.java"),
    ("prompt-assembly-process", f"{BASE_JAVA}/prompt/ContextIdService.java"),

    # ── Jinja Template Organization ───────────────────────────────────
    # workflow templates
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/orchestrator.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/orchestrator_collector.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/discovery_orchestrator.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/discovery_agent.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/discovery_dispatch.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/discovery_collector.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/planning_orchestrator.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/planning_agent.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/planning_dispatch.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/planning_collector.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/ticket_orchestrator.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/ticket_agent.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/ticket_dispatch.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/ticket_collector.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/context_manager.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/context_manager_interrupt.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/review.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/review_resolution.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/merger.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/worktree_commit_agent.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/worktree_merge_conflict_agent.jinja"),
    # workflow partials
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/_collector_base.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/_context_manager_body.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/_interrupt_guidance.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/_review_justification.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/_discovery_research_disclaimer.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/workflow/_planning_disclaimer.jinja"),
    # justification templates
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/orchestrator.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/orchestrator_collector.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/discovery.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/discovery_orchestrator.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/discovery_collector.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/discovery_dispatch.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/planning.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/planning_orchestrator.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/planning_collector.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/planning_dispatch.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/ticket.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/ticket_orchestrator.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/ticket_collector.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/ticket_dispatch.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/review.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/justification/merger.jinja"),
    # communication templates
    ("jinja-template-organization", f"{BASE_RESOURCES}/communication/agent_call.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/communication/controller_call.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/communication/controller_response.jinja"),
    # data-layer templates
    ("jinja-template-organization", f"{BASE_RESOURCES}/filter/ai_filter.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/propagation/ai_propagator.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/transformation/ai_transformer.jinja"),
    # retry templates
    ("jinja-template-organization", f"{BASE_RESOURCES}/retry/compaction.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/retry/incomplete_json.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/retry/null_result.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/retry/parse_error.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/retry/timeout.jinja"),
    ("jinja-template-organization", f"{BASE_RESOURCES}/retry/unparsed_tool_call.jinja"),

    # ── Prompt Contributors ───────────────────────────────────────────
    ("prompt-contributors", f"{BASE_JAVA}/prompt/PromptContributor.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/PromptContributorAdapter.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/PromptContributorAdapterFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/PromptContributorDescriptor.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/PromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/PromptContributionListener.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/FilteredPromptContributor.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/SimplePromptContributor.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/WorkflowAgentGraphNode.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/AgentTopologyPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/ContextManagerPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/ContextManagerReturnRoutes.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/CurationHistoryContextContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/FirstOrchestratorPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/GitPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/IntellijPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/JsonOutputFormatPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/JustificationPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/NodeMappings.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/PermissionEscalationPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/RouteToContextManagerPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/SkillPromptContributorFactory.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/WeAreHerePromptContributor.java"),
    ("prompt-contributors", f"{BASE_JAVA}/prompt/contributor/WorktreeSandboxPromptContributorFactory.java"),

    # ── Retry-Aware Filtering ─────────────────────────────────────────
    ("retry-aware-filtering", f"{BASE_JAVA}/prompt/RetryAware.java"),
    ("retry-aware-filtering", f"{BASE_JAVA}/prompt/PromptContributorService.java"),
    ("retry-aware-filtering", f"{BASE_JAVA}/service/ActionRetryListenerImpl.java"),
    ("retry-aware-filtering", f"{BASE_JAVA}/service/AgentExecutor.java"),
    ("retry-aware-filtering", f"{BASE_JAVA}/agent/AgentInterfaces.java"),
    ("retry-aware-filtering", f"{BASE_JAVA}/agent/ErrorDescriptor.java"),
    ("retry-aware-filtering", f"{BASE_JAVA}/agent/ErrorTemplates.java"),
    ("retry-aware-filtering", f"{BASE_JAVA}/agent/ResolvedTemplate.java"),
    ("retry-aware-filtering", f"{ACP_JAVA}/AcpRetryEventListener.java"),
    ("retry-aware-filtering", f"{ACP_JAVA}/AcpSessionRetryContext.java"),

    # ── AgentPretty ───────────────────────────────────────────────────
    ("agent-pretty", f"{BASE_JAVA}/agent/AgentPretty.java"),
    ("agent-pretty", f"{BASE_JAVA}/config/SerdesConfiguration.java"),
    ("agent-pretty", f"{BASE_JAVA}/config/AgentModelMixin.java"),

    # ── Prompt Health Check ───────────────────────────────────────────
    ("prompt-health-check", f"{BASE_JAVA}/agent/decorator/prompt/PromptHealthCheckLlmCallDecorator.java"),

    # ── Skill References ──────────────────────────────────────────────
    ("prompt-assembly-process", f"{SKILLS}/multi_agent_ide_controller/references/prompt_architecture.md"),

    # ── Conversational Topology & Checklists ──────────────────────────
    ("conversational-topology-checklists", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist.md"),
    ("conversational-topology-checklists", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/reference.md"),
    ("conversational-topology-checklists", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-discovery-agent.md"),
    ("conversational-topology-checklists", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-planning-agent.md"),
    ("conversational-topology-checklists", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-ticket-agent.md"),
    ("conversational-topology-checklists", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-discovery-orchestrator.md"),
    ("conversational-topology-checklists", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-planning-orchestrator.md"),
    ("conversational-topology-checklists", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist-ticket-orchestrator.md"),
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
