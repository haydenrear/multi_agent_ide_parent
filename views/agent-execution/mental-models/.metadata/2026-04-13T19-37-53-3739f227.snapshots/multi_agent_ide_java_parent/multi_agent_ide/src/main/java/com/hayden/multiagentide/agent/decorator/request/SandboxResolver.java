package com.hayden.multiagentide.agent.decorator.request;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.model.nodes.GraphNode;
import com.hayden.multiagentide.model.nodes.OrchestratorNode;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxResolver {

    private final GraphRepository graphRepository;
    private final WorktreeRepository worktreeRepository;

    public WorktreeSandboxContext resolveSandboxContext(DecoratorContext context) {
        // Single worktree for entire goal — always resolve from orchestrator node.
        return resolveFromOrchestratorNode(context.operationContext());
    }

    public String resolveOrchestratorNode(OperationContext context) {
        var options = context.getAgentProcess();
        String contextId = options.getId();
        return contextId;
    }

    public WorktreeSandboxContext resolveFromOrchestratorNode(OperationContext operationContext) {
        if (operationContext == null) {
            return null;
        }
        String nodeId = resolveOrchestratorNode(operationContext);

        if (StringUtils.isBlank(nodeId)) {
            return null;
        }

        Optional<GraphNode> nodeOpt = graphRepository.findById(nodeId);

        if (nodeOpt.isEmpty()) {
            return null;
        }

        GraphNode node = nodeOpt.get();
        String mainWorktreeId;
        List<String> submoduleIds = List.of();

        if (node instanceof OrchestratorNode orchestratorNode) {
            mainWorktreeId = orchestratorNode.mainWorktreeId();
            return buildSandboxContextFrom(mainWorktreeId, orchestratorNode.submoduleWorktrees());
        }

        return null;
    }

    private WorktreeSandboxContext buildSandboxContextFrom(String mainWorktreeId, List<SubmoduleWorktreeContext> submodules) {
        if (mainWorktreeId == null || mainWorktreeId.isBlank()) {
            return null;
        }
        MainWorktreeContext mainContext = worktreeRepository.findById(mainWorktreeId)
                .filter(MainWorktreeContext.class::isInstance)
                .map(MainWorktreeContext.class::cast)
                .orElse(null);
        if (mainContext == null) {
            return null;
        }
        List<SubmoduleWorktreeContext> resolved = submodules != null ? submodules : List.of();
        return new WorktreeSandboxContext(mainContext, resolved);
    }

    private WorktreeSandboxContext buildSandboxContext(String mainWorktreeId, List<String> submoduleIds) {
        if (mainWorktreeId == null || mainWorktreeId.isBlank()) {
            return null;
        }
        MainWorktreeContext mainContext = worktreeRepository.findById(mainWorktreeId)
                .filter(MainWorktreeContext.class::isInstance)
                .map(MainWorktreeContext.class::cast)
                .orElse(null);
        if (mainContext == null) {
            return null;
        }
        List<SubmoduleWorktreeContext> submodules = new ArrayList<>();
        if (submoduleIds != null) {
            for (String submoduleId : submoduleIds) {
                if (submoduleId == null || submoduleId.isBlank()) {
                    continue;
                }
                WorktreeContext found = worktreeRepository.findById(submoduleId).orElse(null);
                if (found instanceof SubmoduleWorktreeContext submoduleContext) {
                    submodules.add(submoduleContext);
                }
            }
        }
        return new WorktreeSandboxContext(mainContext, submodules);
    }

}
