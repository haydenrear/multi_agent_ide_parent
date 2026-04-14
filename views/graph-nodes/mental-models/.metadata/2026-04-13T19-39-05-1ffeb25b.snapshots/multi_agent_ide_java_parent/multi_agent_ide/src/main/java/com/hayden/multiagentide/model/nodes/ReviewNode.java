package com.hayden.multiagentide.model.nodes;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node for agent-based review of work.
 * Can be Reviewable.
 */
@Builder(toBuilder = true)
public record ReviewNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        
        // Review-specific fields
        String reviewedNodeId,
        String reviewContent,
        boolean approved,
        boolean humanFeedbackRequested,
        String agentFeedback,
        String reviewerAgentType,
        Instant reviewCompletedAt,
        AgentModels.ReviewAgentResult reviewResult,
        InterruptContext interruptContext
) implements GraphNode, Viewable<String>, InterruptRecord {

    public ReviewNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, String reviewedNodeId, String reviewContent, boolean approved, boolean humanFeedbackRequested, String agentFeedback, String reviewerAgentType, Instant reviewCompletedAt) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                reviewedNodeId, reviewContent, approved, humanFeedbackRequested, agentFeedback, reviewerAgentType, reviewCompletedAt, null, null);
    }

    public ReviewNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (reviewedNodeId == null || reviewedNodeId.isEmpty()) throw new IllegalArgumentException("reviewedNodeId required");
        if (reviewerAgentType == null || reviewerAgentType.isEmpty()) throw new IllegalArgumentException("reviewerAgentType required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (interruptContext == null) {
            Events.InterruptType type = "human".equalsIgnoreCase(reviewerAgentType)
                    ? Events.InterruptType.HUMAN_REVIEW
                    : Events.InterruptType.AGENT_REVIEW;
            interruptContext = new InterruptContext(
                    type,
                    InterruptContext.InterruptStatus.REQUESTED,
                    reviewContent,
                    reviewedNodeId,
                    reviewedNodeId,
                    nodeId,
                    null
            );
        }
    }

    @Override
    public Events.NodeType nodeType() {
        return "human".equalsIgnoreCase(reviewerAgentType)
                ? Events.NodeType.HUMAN_REVIEW
                : Events.NodeType.AGENT_REVIEW;
    }

    @Override
    public String getView() {
        return reviewContent;
    }

    /**
     * Create an updated version with new status.
     */
    public ReviewNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Record review decision.
     */
    public ReviewNode withReviewDecision(boolean approvalStatus, String feedback) {
        return toBuilder()
                .approved(approvalStatus)
                .agentFeedback(feedback)
                .reviewCompletedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public ReviewNode withResult(AgentModels.ReviewAgentResult result) {
        return toBuilder()
                .reviewResult(result)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public String contentToReview() {
        return this.reviewContent;
    }

    public String reviewerAgent() {
        return this.reviewerAgentType;
    }
}
