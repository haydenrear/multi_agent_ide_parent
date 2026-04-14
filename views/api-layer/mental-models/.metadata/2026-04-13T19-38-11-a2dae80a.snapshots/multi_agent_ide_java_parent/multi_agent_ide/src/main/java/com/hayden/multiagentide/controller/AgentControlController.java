package com.hayden.multiagentide.controller;

import jakarta.validation.Valid;
import com.hayden.multiagentide.service.AgentControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Tag(name = "Agent Control", description = "Pause, stop, resume, prune, branch, delete, and review agent nodes")
public class AgentControlController {

    private final AgentControlService agentControlService;

    @Schema(description = "Request targeting an agent node for a control action.")
    public record ControlActionRequest(
            @Schema(description = "ArtifactKey of the target agent node") String nodeId,
            @Schema(description = "Optional message or reason for the action") String message
    ) {
    }

    public record ControlActionResponse(String actionId, String status) {
    }

    @PostMapping("/pause")
    @Operation(summary = "Request pause for a node",
            description = "Queues a pause signal for the target node via AgentControlService. "
                    + "The node will suspend at its next safe checkpoint. Resume via /resume.")
    public ControlActionResponse pause(@RequestBody @Valid ControlActionRequest request) {
        String actionId = agentControlService.requestPause(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/stop")
    @Operation(summary = "Request stop for a node",
            description = "Terminates the target node. Unlike pause, this is not resumable. "
                    + "Use /delete to also remove the node and its subtree from the graph.")
    public ControlActionResponse stop(@RequestBody @Valid ControlActionRequest request) {
        String actionId = agentControlService.requestStop(request.nodeId());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/resume")
    @Operation(summary = "Request resume for a paused node",
            description = "Resumes a node that was previously paused. "
                    + "The optional message is passed to AgentControlService for context.")
    public ControlActionResponse resume(@RequestBody @Valid ControlActionRequest request) {
        String actionId = agentControlService.requestResume(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/prune")
    @Operation(summary = "Request prune (trim subtree) for a node",
            description = "Removes the node's subtree while keeping the node itself. "
                    + "Useful for discarding an unproductive branch without stopping the parent.")
    public ControlActionResponse prune(@RequestBody @Valid ControlActionRequest request) {
        String actionId = agentControlService.requestPrune(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/branch")
    @Operation(summary = "Request branch creation from a node",
            description = "Forks the current node's execution context into a new branch, "
                    + "allowing parallel exploration from the same point.")
    public ControlActionResponse branch(@RequestBody @Valid ControlActionRequest request) {
        String actionId = agentControlService.requestBranch(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

//   should delete all nodes in tree below also. So if you delete orchestrator node,
//    should delete anything below that node. Also, should delete all events in EventStreamRepository
    @PostMapping("/delete")
    @Operation(summary = "Delete a node and its entire subtree",
            description = "Deletes the target node and all descendant nodes from the graph, "
                    + "including all associated events in EventStreamRepository. "
                    + "This is a destructive operation — use /stop if you only want to halt execution.")
    public ControlActionResponse delete(@RequestBody @Valid ControlActionRequest request) {
//        TODO:
        String actionId = agentControlService.delete(request.nodeId());
        return new ControlActionResponse(actionId, "queued");
    }

    @PostMapping("/review-request")
    @Operation(summary = "Request a review for a node",
            description = "Triggers a HUMAN_REVIEW gate on the target node, suspending execution until "
                    + "explicitly resolved via POST /api/interrupts/resolve. "
                    + "See InterruptController for the resolution contract.")
    public ControlActionResponse reviewRequest(@RequestBody @Valid ControlActionRequest request) {
        String actionId = agentControlService.requestReview(request.nodeId(), request.message());
        return new ControlActionResponse(actionId, "queued");
    }

}
