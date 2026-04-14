package com.hayden.multiagentide.cli;

import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.tui.TuiSession;
import com.hayden.utilitymodule.config.EnvConfigProps;
import com.hayden.utilitymodule.git.RepoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@ShellComponent
@Profile("cli")
@Slf4j
public class CliTuiRunner {

    private final OrchestrationController orchestrationController;
    private final EnvConfigProps envConfigProps;
    private final TuiSession tuiSession;

    public CliTuiRunner(
            OrchestrationController orchestrationController,
            EnvConfigProps envConfigProps,
            TuiSession tuiSession
    ) {
        this.orchestrationController = orchestrationController;
        this.envConfigProps = envConfigProps;
        this.tuiSession = tuiSession;
    }

    @ShellMethod(key = "tui", value = "Launch the interactive TUI session")
    public String tui() {
        Path defaultRepo = resolveDefaultRepositoryPath();
        tuiSession.configureSession("session-" + UUID.randomUUID(), defaultRepo, this::startGoal);
        tuiSession.run();
        return "TUI session closed.";
    }

    private String startGoal(String repo, String goal) {
        try (var g = RepoUtil.initGit(Paths.get(repo))) {
            log.info("Starting goal.");
            var branch = g.map(git -> {
                try {
                    return git.getRepository().getBranch();
                } catch (IOException e) {
                    log.error("Error finding branch.", e);
                    return "main";
                }
            }).orElseRes("main");
            OrchestrationController.StartGoalResponse response =
                    orchestrationController.startGoalAsync(new OrchestrationController.StartGoalRequest(
                            goal,
                            repo,
                            branch,
                            resolveTitle(goal)
                    ));
            return response.nodeId();
        } catch (Exception e) {
            log.warn("Failed to start goal from TUI: {}", e.getMessage());
            return null;
        }
    }

    private Path resolveDefaultRepositoryPath() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private String resolveTitle(String goal) {
        if (goal.length() <= 60) {
            return goal;
        }
        return goal.substring(0, 60) + "...";
    }
}
