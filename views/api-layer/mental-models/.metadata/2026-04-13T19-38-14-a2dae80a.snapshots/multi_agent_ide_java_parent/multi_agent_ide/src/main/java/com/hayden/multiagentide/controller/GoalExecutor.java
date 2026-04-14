package com.hayden.multiagentide.controller;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.AgentLifecycleHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoalExecutor {

    private final AgentLifecycleHandler agentLifecycleHandler;

    private EventBus eventBus;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Async
    public void executeGoal(OrchestrationController.StartGoalRequest request, ArtifactKey root) {
        String nodeId = root.value();

        String baseBranch = (request.baseBranch() == null || request.baseBranch().isBlank())
                ? "main"
                : request.baseBranch();

        try {

            String normalizedUrl = normalizeRepositoryUrl(request.repositoryUrl());

            Path repoPath = resolveRepoPath(normalizedUrl);
            if (!repoPath.toFile().exists()) {
                throw new IllegalArgumentException("Repository did not exist: " + repoPath);
            }

            eventBus.publish(new Events.GoalStartedEvent(
                    java.util.UUID.randomUUID().toString(),
                    java.time.Instant.now(),
                    nodeId,
                    request.goal(),
                    normalizedUrl,
                    baseBranch,
                    request.title(),
                    request.tags()
            ));

            agentLifecycleHandler.initializeOrchestrator(
                    normalizedUrl,
                    baseBranch,
                    request.goal(),
                    request.title(),
                    nodeId
            );
        } catch (Exception e) {
            String message = "Error when attempting to start orchestrator - %s.".formatted(e.getMessage());
            log.error(message, e);
            eventBus.publish(Events.NodeErrorEvent.err(message, root));
        }
    }

    static String normalizeRepositoryUrl(String repositoryUrl) {
        String url = repositoryUrl;
        // Strip file:// scheme to get a plain filesystem path
        if (url.startsWith("file://")) {
            url = Path.of(URI.create(url)).toString();
        }
        // If path ends with .git, resolve to the parent directory
        Path p = Path.of(url);
        if (p.getFileName() != null && p.getFileName().toString().equals(".git")) {
            url = p.getParent().toString();
        }
        return url;
    }

    private static Path resolveRepoPath(String normalizedUrl) {
        return Path.of(normalizedUrl);
    }

}
