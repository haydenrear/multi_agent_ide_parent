package com.hayden.multiagentide.controller;

import com.hayden.commitdiffcontext.git.parser.support.episodic.model.OnboardingRunMetadata;
import com.hayden.commitdiffcontext.git.parser.support.episodic.service.OnboardingOrchestrationService;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for orchestration operations.
 * Provides endpoints for initializing goals, executing nodes, and checking status.
 */
@Slf4j
@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
@Tag(name = "Orchestrator", description = "Goal execution orchestration and onboarding run management")
public class OrchestrationController {

    private final GoalExecutor goalExecutor;
    private final ObjectProvider<OnboardingOrchestrationService> onboardingOrchestrationServiceProvider;

    @PostMapping("/start")
    @Operation(summary = "Start a new goal asynchronously",
            description = "Creates a new root ArtifactKey and asynchronously executes the goal via GoalExecutor. "
                    + "Returns the root nodeId immediately — poll /api/ui/nodes or subscribe to the event stream "
                    + "to track execution progress. Both goal and repositoryUrl are required.")
    public StartGoalResponse startGoal(@RequestBody @Valid StartGoalRequest request) {
        return startGoalAsync(request);
    }

    public StartGoalResponse startGoalAsync(StartGoalRequest request) {
        if (request == null || request.goal() == null || request.goal().isBlank()) {
            throw new IllegalArgumentException("goal is required");
        }
        if (request.repositoryUrl() == null || request.repositoryUrl().isBlank()) {
            throw new IllegalArgumentException("repositoryUrl is required");
        }

        ArtifactKey root = ArtifactKey.createRoot();
        goalExecutor.executeGoal(request, root);
        return new StartGoalResponse(root.value());
    }

    @GetMapping("/onboarding/runs")
    @Operation(summary = "List all onboarding runs",
            description = "Returns metadata for all onboarding runs managed by OnboardingOrchestrationService. "
                    + "Requires the onboarding orchestration bean to be present in the application context — "
                    + "returns 500 if not configured.")
    public java.util.List<OnboardingRunMetadata> onboardingRuns() {
        return onboardingService().findRuns();
    }

    @PostMapping("/onboarding/runs/get")
    @Operation(summary = "Get a single onboarding run by ID",
            description = "Looks up a specific onboarding run by its runId. "
                    + "Throws 400 if the run is not found.")
    public OnboardingRunMetadata onboardingRun(@RequestBody @Valid OnboardingRunRequest request) {
        return onboardingService().findRun(request.runId())
                .orElseThrow(() -> new IllegalArgumentException("Onboarding run not found: " + request.runId()));
    }

    private OnboardingOrchestrationService onboardingService() {
        var service = onboardingOrchestrationServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Onboarding orchestration is not configured in this application context.");
        }
        return service;
    }

    @Schema(description = "Request to start a new goal execution.")
    public record StartGoalRequest(
            @NotBlank @Schema(description = "Natural-language goal description — the instruction given to the agent") String goal,
            @NotBlank @Schema(description = "Git repository URL the agent will operate on - only local paths accepted to local git repository") String repositoryUrl,
            @Schema(description = "Base branch for the goal (optional; defaults to main/master)") String baseBranch,
            @Schema(description = "Human-readable title for this goal run") String title,
            @Schema(description = "Optional tags for filtering/grouping runs") List<String> tags
    ) {
        public StartGoalRequest(String goal, String repositoryUrl, String baseBranch, String title) {
            this(goal, repositoryUrl, baseBranch, title, List.of());
        }

        public StartGoalRequest {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public record StartGoalResponse(String nodeId) {}

    public record OnboardingRunRequest(String runId) {}
}
