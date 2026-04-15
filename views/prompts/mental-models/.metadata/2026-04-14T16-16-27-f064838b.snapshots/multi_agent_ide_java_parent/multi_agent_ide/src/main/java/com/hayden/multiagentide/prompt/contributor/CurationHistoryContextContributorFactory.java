package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentPretty;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.agent.UpstreamContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.*;

@Component
public class CurationHistoryContextContributorFactory implements PromptContributorFactory {

    private static final int BASE_PRIORITY = -1100;

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(
                PromptContributorDescriptor.of(
                        "curation-binder-<from>-to-<to>",
                        NarrativeBinderContributor.class,
                        "FACTORY",
                        BASE_PRIORITY,
                        "curation-binder-<from>-to-<to>"),
                PromptContributorDescriptor.of(
                        "curation-request-context-<request-type>",
                        RequestContextContributor.class,
                        "FACTORY",
                        BASE_PRIORITY,
                        "curation-request-context-<request-type>"),
                PromptContributorDescriptor.of(
                        "curation-workflow-footer",
                        CurationWorkflowFooterContributor.class,
                        "FACTORY",
                        BASE_PRIORITY,
                        "curation-workflow-footer"),
                PromptContributorDescriptor.of(
                        "curation-workflow-instructions",
                        CurationWorkflowInstructionsContributor.class,
                        "FACTORY",
                        BASE_PRIORITY,
                        "curation-workflow-instructions"),
                PromptContributorDescriptor.of(
                        "curation-data-<phase>",
                        DataCurationContributor.class,
                        "FACTORY",
                        BASE_PRIORITY,
                        "curation-data-<phase>"),
                PromptContributorDescriptor.of(
                        "curation-interrupt-resolution",
                        InterruptResolutionContributor.class,
                        "FACTORY",
                        BASE_PRIORITY,
                        "curation-interrupt-resolution"));
    }

    static final String DISCOVERY_CURATION_HEADER = """
            ## Discovery Curation
            The consolidated curation from the discovery collector, summarizing code analysis,
            architecture findings, and recommendations from discovery agents.
            """;

    static final String DISCOVERY_AGENT_REPORT_HEADER = """
            ## Discovery Agent Report
            Individual report from a discovery agent with detailed code findings, file references,
            and subdomain analysis.
            """;

    static final String PLANNING_CURATION_HEADER = """
            ## Planning Curation
            The consolidated curation from the planning collector, including finalized tickets,
            dependency graphs, and planning summaries.
            """;

    static final String PLANNING_AGENT_RESULT_HEADER = """
            ## Planning Agent Result
            Individual result from a planning agent with proposed tickets, architecture decisions,
            and implementation strategies.
            """;

    static final String TICKET_CURATION_HEADER = """
            ## Ticket Curation
            The consolidated curation from the ticket collector, including completion status,
            follow-up items, and execution summaries.
            """;

    static final String TICKET_AGENT_RESULT_HEADER = """
            ## Ticket Agent Result
            Individual result from a ticket execution agent with implementation summary,
            files modified, test results, and verification status.
            """;

    static final String DISCOVERY_ORCHESTRATOR_RESULT_HEADER = """
            ## Discovery Orchestrator Result
            Orchestrator output for discovery-phase delegation and synthesis.
            """;

    static final String PLANNING_ORCHESTRATOR_RESULT_HEADER = """
            ## Planning Orchestrator Result
            Orchestrator output for planning-phase delegation and synthesis.
            """;

    static final String TICKET_ORCHESTRATOR_RESULT_HEADER = """
            ## Ticket Orchestrator Result
            Orchestrator output for ticket-phase execution coordination.
            """;

    static final String ORCHESTRATOR_AGENT_RESULT_HEADER = """
            ## Orchestrator Agent Result
            Top-level orchestrator output coordinating multi-phase workflow progress.
            """;

    static final String ORCHESTRATOR_COLLECTOR_RESULT_HEADER = """
            ## Orchestrator Collector Result
            Consolidated workflow-level collector output spanning discovery, planning, and ticket phases.
            """;

    static final String REVIEW_AGENT_RESULT_HEADER = """
            ## Review Agent Result
            Review agent assessment and feedback captured during workflow execution.
            """;

    static final String MERGER_AGENT_RESULT_HEADER = """
            ## Merger Agent Result
            Merge assessment and conflict-resolution guidance from the merger agent.
            """;

    static final String COMMIT_AGENT_RESULT_HEADER = """
            ## Commit Agent Result
            Pre-merge commit execution summary, including commit linkage metadata and notable decisions.
            """;

    static final String CURRENT_PHASE_HEADER = """
            ## Request Context
            Current request context in this step. Route using exactly one non-null routing field.
            """;

    static final String HISTORICAL_REQUEST_CONTEXT_HEADER = """
            ## Prior Workflow Context
            Historical request from an earlier agent in this workflow run. Provided for context only — do not treat as your current task or routing instruction.
            """;

    private enum AllowedHistoryType {
        DISCOVERY_COLLECTOR_RESULT,
        DISCOVERY_AGENT_RESULT,
        DISCOVERY_ORCHESTRATOR_RESULT,
        PLANNING_COLLECTOR_RESULT,
        PLANNING_AGENT_RESULT,
        PLANNING_ORCHESTRATOR_RESULT,
        TICKET_COLLECTOR_RESULT,
        TICKET_AGENT_RESULT,
        TICKET_ORCHESTRATOR_RESULT,
        ORCHESTRATOR_AGENT_RESULT,
        ORCHESTRATOR_COLLECTOR_RESULT,
        REVIEW_AGENT_RESULT,
        MERGER_AGENT_RESULT,
        COMMIT_AGENT_RESULT,
        INTERRUPT_REQUEST
    }

    private enum CurationType {
        DISCOVERY,
        PLANNING,
        TICKET
    }

    private static final class CurationOverrides {
        private UpstreamContext.DiscoveryCollectorContext discovery;
        private UpstreamContext.PlanningCollectorContext planning;
        private UpstreamContext.TicketCollectorContext ticket;

        private void setDiscovery(UpstreamContext.DiscoveryCollectorContext discovery) {
            if (discovery != null) {
                this.discovery = discovery;
            }
        }

        private void setPlanning(UpstreamContext.PlanningCollectorContext planning) {
            if (planning != null) {
                this.planning = planning;
            }
        }

        private void setTicket(UpstreamContext.TicketCollectorContext ticket) {
            if (ticket != null) {
                this.ticket = ticket;
            }
        }

        private boolean hasDiscovery() {
            return discovery != null;
        }

        private boolean hasPlanning() {
            return planning != null;
        }

        private boolean hasTicket() {
            return ticket != null;
        }

        private UpstreamContext.DiscoveryCollectorContext discovery() {
            return discovery;
        }

        private UpstreamContext.PlanningCollectorContext planning() {
            return planning;
        }

        private UpstreamContext.TicketCollectorContext ticket() {
            return ticket;
        }

        private UpstreamContext.DiscoveryCollectorContext discoveryOr(UpstreamContext.DiscoveryCollectorContext fromHistory) {
            return discovery != null ? discovery : fromHistory;
        }

        private UpstreamContext.PlanningCollectorContext planningOr(UpstreamContext.PlanningCollectorContext fromHistory) {
            return planning != null ? planning : fromHistory;
        }

        private UpstreamContext.TicketCollectorContext ticketOr(UpstreamContext.TicketCollectorContext fromHistory) {
            return ticket != null ? ticket : fromHistory;
        }
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (BlackboardHistory.isNonWorkflowRequest(context.currentRequest()))
            return List.of();

        var bh = context.blackboardHistory();
        if (bh == null || CollectionUtils.isEmpty(bh.copyOfEntries())) {
            return List.of();
        }

        // Step 1: Determine which history payloads are relevant for this request,
        // and collect any curation overrides from the request itself.
        EnumSet<AllowedHistoryType> allowedTypes = EnumSet.noneOf(AllowedHistoryType.class);
        CurationOverrides curationOverrides = new CurationOverrides();
        populateAllowedTypes(context.currentRequest(), allowedTypes, curationOverrides);

        if (allowedTypes.isEmpty()) {
            return List.of();
        }

        // Step 2: Walk history chronologically to build temporally-ordered contributors.
        // Collapse consecutive runs of the same action+request-class (retries) into a single
        // entry so agents don't see the same request repeated multiple times in their context.
        List<CollapsedEntry> entries = collapseConsecutiveRequests(bh.copyOfEntries());
        List<PromptContributor> contributors = new ArrayList<>();
        int seq = 0;
        AgentModels.CurationPhase lastPhase = null;
        int interruptIndex = 0;

        // Track which request-level curation overrides have already been emitted.
        EnumSet<CurationType> emittedOverrides = EnumSet.noneOf(CurationType.class);

        // Track which curation phases have already been emitted from history.
        EnumSet<CurationType> emittedCurationTypes = EnumSet.noneOf(CurationType.class);

        for (int i = 0; i < entries.size(); i++) {
            CollapsedEntry collapsed = entries.get(i);
            BlackboardHistory.DefaultEntry de = collapsed.entry();
            int retryCount = collapsed.retryCount();
            Object input = de.input();
            if (input == null) {
                continue;
            }

            if (input instanceof AgentModels.AgentResult result) {
                switch (result) {
                    case AgentModels.CommitAgentResult commitAgentResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.COMMIT_AGENT_RESULT)
                                && hasRenderableOutput(commitAgentResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.OTHER;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-commit-agent-result",
                                    COMMIT_AGENT_RESULT_HEADER, commitAgentResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.MergeConflictResult ignored -> {
                    }
                    case AgentModels.DiscoveryCollectorResult discoveryCollectorResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.DISCOVERY_COLLECTOR_RESULT)
                                && !emittedCurationTypes.contains(CurationType.DISCOVERY)
                                && discoveryCollectorResult.discoveryCollectorContext() != null) {
                            var toEmit = curationOverrides.discoveryOr(discoveryCollectorResult.discoveryCollectorContext());
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.DISCOVERY_CURATION;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-discovery-curation",
                                    DISCOVERY_CURATION_HEADER, toEmit, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                            emittedCurationTypes.add(CurationType.DISCOVERY);
                            if (curationOverrides.hasDiscovery()) {
                                emittedOverrides.add(CurationType.DISCOVERY);
                            }
                        }
                    }
                    case AgentModels.PlanningCollectorResult planningCollectorResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.PLANNING_COLLECTOR_RESULT)
                                && !emittedCurationTypes.contains(CurationType.PLANNING)
                                && planningCollectorResult.planningCuration() != null) {
                            var toEmit = curationOverrides.planningOr(planningCollectorResult.planningCuration());
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.PLANNING_CURATION;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-planning-curation",
                                    PLANNING_CURATION_HEADER, toEmit, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                            emittedCurationTypes.add(CurationType.PLANNING);
                            if (curationOverrides.hasPlanning()) {
                                emittedOverrides.add(CurationType.PLANNING);
                            }
                        }
                    }
                    case AgentModels.TicketCollectorResult ticketCollectorResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.TICKET_COLLECTOR_RESULT)
                                && !emittedCurationTypes.contains(CurationType.TICKET)
                                && ticketCollectorResult.ticketCuration() != null) {
                            var toEmit = curationOverrides.ticketOr(ticketCollectorResult.ticketCuration());
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.TICKET_CURATION;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-ticket-curation",
                                    TICKET_CURATION_HEADER, toEmit, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                            emittedCurationTypes.add(CurationType.TICKET);
                            if (curationOverrides.hasTicket()) {
                                emittedOverrides.add(CurationType.TICKET);
                            }
                        }
                    }
                    case AgentModels.DiscoveryAgentResult discoveryAgentResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.DISCOVERY_AGENT_RESULT)
                                && hasRenderableOutput(discoveryAgentResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.DISCOVERY_AGENT;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-discovery-report",
                                    DISCOVERY_AGENT_REPORT_HEADER, discoveryAgentResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.PlanningAgentResult planningAgentResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.PLANNING_AGENT_RESULT)
                                && hasRenderableOutput(planningAgentResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.PLANNING_AGENT;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-planning-result",
                                    PLANNING_AGENT_RESULT_HEADER, planningAgentResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.TicketAgentResult ticketAgentResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.TICKET_AGENT_RESULT)
                                && hasRenderableOutput(ticketAgentResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.TICKET_AGENT;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-ticket-result",
                                    TICKET_AGENT_RESULT_HEADER, ticketAgentResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.DiscoveryOrchestratorResult discoveryOrchestratorResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.DISCOVERY_ORCHESTRATOR_RESULT)
                                && hasRenderableOutput(discoveryOrchestratorResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.DISCOVERY_AGENT;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-discovery-orchestrator-result",
                                    DISCOVERY_ORCHESTRATOR_RESULT_HEADER, discoveryOrchestratorResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.PlanningOrchestratorResult planningOrchestratorResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.PLANNING_ORCHESTRATOR_RESULT)
                                && hasRenderableOutput(planningOrchestratorResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.PLANNING_AGENT;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-planning-orchestrator-result",
                                    PLANNING_ORCHESTRATOR_RESULT_HEADER, planningOrchestratorResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.TicketOrchestratorResult ticketOrchestratorResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.TICKET_ORCHESTRATOR_RESULT)
                                && hasRenderableOutput(ticketOrchestratorResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.TICKET_AGENT;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-ticket-orchestrator-result",
                                    TICKET_ORCHESTRATOR_RESULT_HEADER, ticketOrchestratorResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.OrchestratorAgentResult orchestratorAgentResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.ORCHESTRATOR_AGENT_RESULT)
                                && hasRenderableOutput(orchestratorAgentResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.OTHER;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-orchestrator-agent-result",
                                    ORCHESTRATOR_AGENT_RESULT_HEADER, orchestratorAgentResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.OrchestratorCollectorResult orchestratorCollectorResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.ORCHESTRATOR_COLLECTOR_RESULT)
                                && hasRenderableOutput(orchestratorCollectorResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.OTHER;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-orchestrator-collector-result",
                                    ORCHESTRATOR_COLLECTOR_RESULT_HEADER, orchestratorCollectorResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.ReviewAgentResult reviewAgentResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.REVIEW_AGENT_RESULT)
                                && hasRenderableOutput(reviewAgentResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.OTHER;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-review-agent-result",
                                    REVIEW_AGENT_RESULT_HEADER, reviewAgentResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.MergerAgentResult mergerAgentResult -> {
                        if (allowedTypes.contains(AllowedHistoryType.MERGER_AGENT_RESULT)
                                && hasRenderableOutput(mergerAgentResult)) {
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.OTHER;
                            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
                            contributors.add(new DataCurationContributor("curation-merger-agent-result",
                                    MERGER_AGENT_RESULT_HEADER, mergerAgentResult, BASE_PRIORITY + seq++));
                            lastPhase = newPhase;
                        }
                    }
                    case AgentModels.AiFilterResult ignored -> {
                    }
                    case AgentModels.AiPropagatorResult ignored -> {
                    }
                    case AgentModels.AiTransformerResult ignored -> {
                    }
                    case AgentModels.AgentCallResult ignored -> {
                    }
                    case AgentModels.ControllerCallResult ignored -> {
                    }
                    case AgentModels.ControllerResponseResult ignored -> {
                    }
                }
            }

            Bind b = new Bind(seq, lastPhase);

            if (input instanceof AgentModels.AgentRequest req) {
                switch (req) {
                    case AgentModels.InterruptRequest interrupt -> {
                        if (allowedTypes.contains(AllowedHistoryType.INTERRUPT_REQUEST)) {
                            String resolution = findResolutionAfter(entries, i);
                            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.INTERRUPT;
                            seq = maybeAddBinder(contributors, b.phase, newPhase, seq);
                            interruptIndex++;
                            contributors.add(new InterruptResolutionContributor(
                                    new InterruptResolutionEntry(interrupt, de.timestamp(), resolution, null),
                                    interruptIndex, BASE_PRIORITY + seq++));
                            b = new Bind(seq, newPhase);
                        }
                    }
                    case AgentModels.AiFilterRequest aiFilterRequest -> {}
                    case AgentModels.AiPropagatorRequest aiPropagatorRequest -> {}
                    case AgentModels.AiTransformerRequest aiTransformerRequest -> {}
                    case AgentModels.CommitAgentRequest commitAgentRequest -> {}
                    case AgentModels.MergeConflictRequest mergeConflictRequest -> {}
                    case AgentModels.ContextManagerRequest contextManagerRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.ContextManagerRoutingRequest contextManagerRoutingRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.DiscoveryAgentRequest discoveryAgentRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.DiscoveryAgentRequests discoveryAgentRequests ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.DiscoveryCollectorRequest discoveryCollectorRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.DiscoveryOrchestratorRequest discoveryOrchestratorRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.OrchestratorCollectorRequest orchestratorCollectorRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.OrchestratorRequest orchestratorRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.PlanningAgentRequest planningAgentRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.PlanningAgentRequests planningAgentRequests ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.PlanningCollectorRequest planningCollectorRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.PlanningOrchestratorRequest planningOrchestratorRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.ResultsRequest resultsRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.TicketAgentRequest ticketAgentRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.TicketAgentRequests ticketAgentRequests ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.TicketCollectorRequest ticketCollectorRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.TicketOrchestratorRequest ticketOrchestratorRequest ->
                            b = addPromptContributor(req, contributors, b.phase, b.seq, de, retryCount);
                    case AgentModels.AgentToAgentRequest ignored -> {}
                    case AgentModels.AgentToControllerRequest ignored -> {}
                    case AgentModels.ControllerToAgentRequest ignored -> {}
                }

                lastPhase = b.phase;
                seq = b.seq;
            }
        }

        // Emit any curation overrides that weren't found in history (e.g., passed on the request
        // but no corresponding collector result in history yet).
        if (!emittedOverrides.contains(CurationType.DISCOVERY) && curationOverrides.hasDiscovery()) {
            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.DISCOVERY_CURATION;
            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
            contributors.add(new DataCurationContributor("curation-discovery-curation",
                    DISCOVERY_CURATION_HEADER, curationOverrides.discovery(), BASE_PRIORITY + seq++));
            lastPhase = newPhase;
        }

        if (!emittedOverrides.contains(CurationType.PLANNING) && curationOverrides.hasPlanning()) {
            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.PLANNING_CURATION;
            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
            contributors.add(new DataCurationContributor("curation-planning-curation",
                    PLANNING_CURATION_HEADER, curationOverrides.planning(), BASE_PRIORITY + seq++));
            lastPhase = newPhase;
        }

        if (!emittedOverrides.contains(CurationType.TICKET) && curationOverrides.hasTicket()) {
            AgentModels.CurationPhase newPhase = AgentModels.CurationPhase.TICKET_CURATION;
            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
            contributors.add(new DataCurationContributor("curation-ticket-curation",
                    TICKET_CURATION_HEADER, curationOverrides.ticket(), BASE_PRIORITY + seq++));
            lastPhase = newPhase;
        }

        if (contributors.isEmpty()) {
            return List.of();
        }

        // Prepend instructions preamble
        contributors.addFirst(new CurationWorkflowInstructionsContributor(BASE_PRIORITY - 1));

        contributors.add(new CurationWorkflowFooterContributor(BASE_PRIORITY + contributors.size() + 1));

        return contributors;
    }

    Bind addPromptContributor(AgentModels.AgentRequest req, List<PromptContributor> contributors,
                              AgentModels.CurationPhase lastPhase, int seq,
                              BlackboardHistory.DefaultEntry de, int retryCount) {
        if (hasRenderableRequest(req)) {
            AgentModels.CurationPhase newPhase = req.curationPhaseExtraction();
            seq = maybeAddBinder(contributors, lastPhase, newPhase, seq);
            contributors.add(new RequestContextContributor(
                    requestContributorName(req),
                    req,
                    de.actionName(),
                    BASE_PRIORITY + seq++,
                    true,
                    retryCount));
            lastPhase = newPhase;
        }

        return new Bind(seq, lastPhase);
    }

    record Bind(int seq, AgentModels.CurationPhase phase) {}

    /** A history entry with consecutive-duplicate collapse metadata. */
    record CollapsedEntry(BlackboardHistory.DefaultEntry entry, int retryCount) {}

    /**
     * Collapses consecutive runs of entries with the same action name and input class
     * (i.e., retries of the same action) into a single entry carrying the last (most recent)
     * attempt. Non-request entries are never collapsed.
     */
    private List<CollapsedEntry> collapseConsecutiveRequests(List<BlackboardHistory.Entry> raw) {
        List<CollapsedEntry> result = new ArrayList<>();
        int i = 0;
        while (i < raw.size()) {
            if (!(raw.get(i) instanceof BlackboardHistory.DefaultEntry de)) {
                i++;
                continue;
            }
            Object input = de.input();
            // Only collapse AgentRequest runs; leave results and other entries as-is.
            if (!(input instanceof AgentModels.AgentRequest)) {
                result.add(new CollapsedEntry(de, 1));
                i++;
                continue;
            }
            String actionName = de.actionName();
            Class<?> inputClass = input.getClass();
            int count = 1;
            BlackboardHistory.DefaultEntry last = de;
            while (i + count < raw.size()) {
                BlackboardHistory.Entry next = raw.get(i + count);
                if (!(next instanceof BlackboardHistory.DefaultEntry nextDe)) break;
                Object nextInput = nextDe.input();
                if (nextInput == null
                        || nextInput.getClass() != inputClass
                        || !Objects.equals(nextDe.actionName(), actionName)) break;
                last = nextDe;
                count++;
            }
            result.add(new CollapsedEntry(last, count));
            i += count;
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Allowed-types population (preserves the original switch semantics)
    // -----------------------------------------------------------------------

    private void populateAllowedTypes(Object requestInput,
                                      EnumSet<AllowedHistoryType> allowed,
                                      CurationOverrides overrides) {
        if (!(requestInput instanceof AgentModels.AgentRequest request)) {
            return;
        }

        switch (request) {
            case AgentModels.DiscoveryAgentRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.DiscoveryOrchestratorRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.DiscoveryAgentRequests ignored ->
                    addAllTypes(allowed);
            case AgentModels.PlanningAgentRequests ignored ->
                    addAllTypes(allowed);
            case AgentModels.TicketAgentRequests ignored ->
                    addAllTypes(allowed);
            case AgentModels.CommitAgentRequest ignored -> {
//                TODO: validate - this already uses previous session - no reason to rewrite these.
            }
            case AgentModels.AiFilterRequest aiFilterRequest -> {
            }
            case AgentModels.AiPropagatorRequest aiPropagatorRequest -> {
            }
            case AgentModels.AiTransformerRequest aiTransformerRequest -> {
            }
            case AgentModels.MergeConflictRequest ignored -> {
            }
            case AgentModels.ContextManagerRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.ContextManagerRoutingRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.InterruptRequest ignored ->
                    addAllTypes(allowed);

            case AgentModels.DiscoveryCollectorRequest ignored -> {
                allow(allowed,
                        AllowedHistoryType.DISCOVERY_AGENT_RESULT,
                        AllowedHistoryType.PLANNING_COLLECTOR_RESULT,
                        AllowedHistoryType.PLANNING_AGENT_RESULT,
                        AllowedHistoryType.TICKET_COLLECTOR_RESULT,
                        AllowedHistoryType.TICKET_AGENT_RESULT,
                        AllowedHistoryType.INTERRUPT_REQUEST);
            }

            case AgentModels.PlanningOrchestratorRequest req -> {
                addAllTypes(allowed);
                overrides.setDiscovery(req.discoveryCuration());
            }

            case AgentModels.PlanningAgentRequest req -> {
                addAllTypes(allowed);
                overrides.setDiscovery(req.discoveryCuration());
            }

            case AgentModels.PlanningCollectorRequest req -> {
                allow(allowed,
                        AllowedHistoryType.DISCOVERY_COLLECTOR_RESULT,
                        AllowedHistoryType.DISCOVERY_AGENT_RESULT,
                        AllowedHistoryType.PLANNING_AGENT_RESULT,
                        AllowedHistoryType.TICKET_COLLECTOR_RESULT,
                        AllowedHistoryType.TICKET_AGENT_RESULT,
                        AllowedHistoryType.INTERRUPT_REQUEST);
                overrides.setDiscovery(req.discoveryCuration());
            }

            case AgentModels.TicketOrchestratorRequest req -> {
                addAllTypes(allowed);
                overrides.setDiscovery(req.discoveryCuration());
                overrides.setPlanning(req.planningCuration());
            }

            case AgentModels.TicketAgentRequest req -> {
                addAllTypes(allowed);
                overrides.setDiscovery(req.discoveryCuration());
                overrides.setPlanning(req.planningCuration());
            }

            case AgentModels.TicketCollectorRequest req -> {
                addAllTypes(allowed);
                overrides.setDiscovery(req.discoveryCuration());
                overrides.setPlanning(req.planningCuration());
            }

            case AgentModels.OrchestratorCollectorRequest req -> {
                addAllTypes(allowed);
                overrides.setDiscovery(req.discoveryCuration());
                overrides.setPlanning(req.planningCuration());
                overrides.setTicket(req.ticketCuration());
            }

            case AgentModels.OrchestratorRequest req -> {
                addAllTypes(allowed);
                overrides.setDiscovery(req.discoveryCuration());
                overrides.setPlanning(req.planningCuration());
                overrides.setTicket(req.ticketCuration());
            }

            case AgentModels.DiscoveryAgentResults ignored -> {
                allow(allowed,
                        AllowedHistoryType.DISCOVERY_AGENT_RESULT,
                        AllowedHistoryType.INTERRUPT_REQUEST);
            }

            case AgentModels.PlanningAgentResults ignored -> {
                allow(allowed,
                        AllowedHistoryType.DISCOVERY_COLLECTOR_RESULT,
                        AllowedHistoryType.DISCOVERY_AGENT_RESULT,
                        AllowedHistoryType.PLANNING_AGENT_RESULT,
                        AllowedHistoryType.INTERRUPT_REQUEST);
            }

            case AgentModels.TicketAgentResults ignored -> {
                allow(allowed,
                        AllowedHistoryType.DISCOVERY_COLLECTOR_RESULT,
                        AllowedHistoryType.DISCOVERY_AGENT_RESULT,
                        AllowedHistoryType.PLANNING_COLLECTOR_RESULT,
                        AllowedHistoryType.PLANNING_AGENT_RESULT,
                        AllowedHistoryType.TICKET_AGENT_RESULT,
                        AllowedHistoryType.INTERRUPT_REQUEST);
            }

            case AgentModels.AgentToAgentRequest ignored -> {}
            case AgentModels.AgentToControllerRequest ignored -> {}
            case AgentModels.ControllerToAgentRequest ignored -> {}
        }

        // Include all result variants so AgentResult matching stays exhaustive.
        addSupplementaryResultTypes(allowed);
    }

    private static boolean hasRenderableOutput(AgentPretty data) {
        if (data == null) {
            return false;
        }
        String rendered = data.prettyPrint();
        return rendered != null && !rendered.isBlank();
    }

    private int maybeAddBinder(List<PromptContributor> contributors,
                               AgentModels.CurationPhase lastPhase,
                               AgentModels.CurationPhase newPhase,
                               int seq) {
        String binder = binderText(lastPhase, newPhase);
        if (binder == null) {
            return seq;
        }
        contributors.add(new NarrativeBinderContributor(
                binderNameFor(lastPhase, newPhase),
                binder,
                BASE_PRIORITY + seq++));
        return seq;
    }

    private static String binderNameFor(AgentModels.CurationPhase from, AgentModels.CurationPhase to) {
        return "curation-binder-%s-to-%s".formatted(
                from == null ? "start" : from.name().toLowerCase(Locale.ROOT),
                to == null ? "none" : to.name().toLowerCase(Locale.ROOT));
    }

    private static String requestContributorName(AgentModels.AgentRequest request) {
        return "curation-request-context-%s".formatted(
                request.getClass().getSimpleName().toLowerCase(Locale.ROOT));
    }

    private void addAllTypes(EnumSet<AllowedHistoryType> allowed) {
        allowed.addAll(EnumSet.allOf(AllowedHistoryType.class));
    }

    private static void allow(EnumSet<AllowedHistoryType> allowed, AllowedHistoryType... types) {
        Collections.addAll(allowed, types);
    }

    private void addSupplementaryResultTypes(EnumSet<AllowedHistoryType> allowed) {
        allow(allowed,
                AllowedHistoryType.DISCOVERY_ORCHESTRATOR_RESULT,
                AllowedHistoryType.PLANNING_ORCHESTRATOR_RESULT,
                AllowedHistoryType.TICKET_ORCHESTRATOR_RESULT,
                AllowedHistoryType.ORCHESTRATOR_AGENT_RESULT,
                AllowedHistoryType.ORCHESTRATOR_COLLECTOR_RESULT,
                AllowedHistoryType.REVIEW_AGENT_RESULT,
                AllowedHistoryType.MERGER_AGENT_RESULT,
                AllowedHistoryType.COMMIT_AGENT_RESULT);
    }

    private static boolean hasRenderableRequest(AgentModels.AgentRequest request) {
        if (request == null) {
            return false;
        }
        String rendered = request.prettyPrintInterruptContinuation();
        if (rendered == null || rendered.isBlank()) {
            rendered = request.prettyPrint();
        }
        return rendered != null && !rendered.isBlank();
    }

    // -----------------------------------------------------------------------
    // Resolution pairing for interrupts
    // -----------------------------------------------------------------------

    private String findResolutionAfter(List<CollapsedEntry> entries, int startIndex) {
        for (int i = startIndex + 1; i < entries.size(); i++) {
            BlackboardHistory.DefaultEntry de = entries.get(i).entry();
            if (de.input() instanceof Events.ResolveInterruptEvent re) {
                return re.toAddMessage();
            }
            if (de.input() instanceof AgentModels.ReviewAgentResult rar) {
                return rar.output();
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Binder text based on phase transitions
    // -----------------------------------------------------------------------

    private static String binderText(AgentModels.CurationPhase from, AgentModels.CurationPhase to) {
        if (from == null) {
            return null;
        }
        if (from == to) {
            return null;
        }

        // Interrupt transitions
        if (to == AgentModels.CurationPhase.INTERRUPT) {
            return "At this point in the workflow, clarification or review was needed:";
        }
        if (from == AgentModels.CurationPhase.INTERRUPT) {
            return "With the feedback incorporated, the workflow continued:";
        }

        // Discovery → Discovery agent reports
        if (from == AgentModels.CurationPhase.DISCOVERY_CURATION && to == AgentModels.CurationPhase.DISCOVERY_AGENT) {
            return """
                    Following the discovery curation above, individual discovery agents each produced \
                    detailed reports with their specific findings. These reports informed the consolidated \
                    curation and provide additional granular detail:""";
        }

        // Discovery → Planning
        if ((from == AgentModels.CurationPhase.DISCOVERY_CURATION || from == AgentModels.CurationPhase.DISCOVERY_AGENT) &&
                (to == AgentModels.CurationPhase.PLANNING_CURATION || to == AgentModels.CurationPhase.PLANNING_AGENT)) {
            return """
                    With discovery complete, the workflow advanced to the planning phase. The planning \
                    process used the discovery findings above to formulate implementation strategies, \
                    tickets, and dependency graphs:""";
        }

        // Planning → Planning agent results
        if (from == AgentModels.CurationPhase.PLANNING_CURATION && to == AgentModels.CurationPhase.PLANNING_AGENT) {
            return """
                    The following individual planning agent results contain the detailed tickets, \
                    architecture decisions, and implementation strategies that were synthesized into \
                    the planning curation above:""";
        }

        // Planning → Ticket
        if ((from == AgentModels.CurationPhase.PLANNING_CURATION || from == AgentModels.CurationPhase.PLANNING_AGENT) &&
                (to == AgentModels.CurationPhase.TICKET_CURATION || to == AgentModels.CurationPhase.TICKET_AGENT)) {
            return """
                    After planning was finalized, ticket execution began. Ticket agents worked on \
                    implementing the planned changes, running tests, and verifying results:""";
        }

        // Ticket curation → Ticket agent results
        if (from == AgentModels.CurationPhase.TICKET_CURATION && to == AgentModels.CurationPhase.TICKET_AGENT) {
            return """
                    The individual ticket execution results below detail what each ticket agent \
                    accomplished, including files modified, test outcomes, and verification status:""";
        }

        // Re-run: going back to an earlier phase
        if ((from == AgentModels.CurationPhase.TICKET_CURATION || from == AgentModels.CurationPhase.TICKET_AGENT) &&
                (to == AgentModels.CurationPhase.DISCOVERY_CURATION || to == AgentModels.CurationPhase.DISCOVERY_AGENT)) {
            return """
                    The workflow looped back to re-run discovery with the accumulated context from \
                    the previous iteration:""";
        }

        if ((from == AgentModels.CurationPhase.TICKET_CURATION || from == AgentModels.CurationPhase.TICKET_AGENT) &&
                (to == AgentModels.CurationPhase.PLANNING_CURATION || to == AgentModels.CurationPhase.PLANNING_AGENT)) {
            return """
                    The workflow looped back to re-run planning with the accumulated context from \
                    ticket execution:""";
        }

        if ((from == AgentModels.CurationPhase.PLANNING_CURATION || from == AgentModels.CurationPhase.PLANNING_AGENT) &&
                (to == AgentModels.CurationPhase.DISCOVERY_CURATION || to == AgentModels.CurationPhase.DISCOVERY_AGENT)) {
            return """
                    The workflow looped back to re-run discovery with the accumulated context from \
                    the previous planning iteration:""";
        }

        // Generic fallback for other transitions
        return "The workflow then progressed to the next phase:";
    }

    // -----------------------------------------------------------------------
    // Inner records: data holders
    // -----------------------------------------------------------------------

    record InterruptResolutionEntry(
            AgentModels.InterruptRequest request,
            Instant requestedAt,
            String resolution,
            Instant resolvedAt
    ) {
    }

    // -----------------------------------------------------------------------
    // Inner records: prompt contributors
    // -----------------------------------------------------------------------

    record NarrativeBinderContributor(
            String binderName,
            String narrative,
            int binderPriority
    ) implements PromptContributor {
        @Override
        public String name() {
            return binderName;
        }

        @Override
        public boolean include(PromptContext ctx) {
            return true;
        }

        @Override
        public String contribute(PromptContext ctx) {
            return narrative;
        }

        @Override
        public String template() {
            return narrative;
        }

        @Override
        public int priority() {
            return binderPriority;
        }
    }

    record CurationWorkflowFooterContributor(
            int contributorPriority) implements PromptContributor {

        private static final String TEMPLATE = """
                --- End Curated Workflow Context ---
                """;

        @Override
        public String name() {
            return "curation-workflow-footer";
        }

        @Override
        public boolean include(PromptContext ctx) {
            return true;
        }

        @Override
        public String contribute(PromptContext ctx) {
            return TEMPLATE;
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return contributorPriority;
        }
    }

    record CurationWorkflowInstructionsContributor(
            int contributorPriority) implements PromptContributor {

        private static final String TEMPLATE = """
                --- Curated Workflow Context ---
                
                The following context has been curated from prior workflow phases. This represents
                the accumulated knowledge from discovery, planning, and/or ticket execution that
                has been completed so far. Use this context to inform your current task. Each section
                is labeled by its source phase and type, presented in the order they occurred.
                """;

        @Override
        public String name() {
            return "curation-workflow-instructions";
        }

        @Override
        public boolean include(PromptContext ctx) {
            return true;
        }

        @Override
        public String contribute(PromptContext ctx) {
            return TEMPLATE;
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return contributorPriority;
        }
    }

    record RequestContextContributor(
            String contributorName,
            AgentModels.AgentRequest request,
            String actionName,
            int contributorPriority,
            boolean isHistorical,
            int retryCount
    ) implements PromptContributor {

        @Override
        public String name() {
            return contributorName;
        }

        @Override
        public boolean include(PromptContext ctx) {
            return request != null;
        }

        @Override
        public String contribute(PromptContext ctx) {
            StringBuilder sb = new StringBuilder();
            if (isHistorical) {
                sb.append(HISTORICAL_REQUEST_CONTEXT_HEADER.trim()).append("\n");
            } else {
                sb.append(CURRENT_PHASE_HEADER.trim()).append("\n");
            }
            if (retryCount > 1) {
                sb.append("Note: This action was attempted ").append(retryCount)
                        .append(" times consecutively; showing the most recent attempt.\n");
            }
            if (actionName != null && !actionName.isBlank()) {
                sb.append("Action: ").append(actionName).append("\n");
            }
            sb.append("Phase: ").append(request.phaseExtraction()).append("\n");
            String extractedGoal = request.goalExtraction();
            if (extractedGoal != null && !extractedGoal.isBlank()) {
                sb.append("Goal: ").append(extractedGoal.trim()).append("\n");
            }
            sb.append("Request type: ").append(request.getClass().getSimpleName()).append("\n");
            if (!isHistorical) {
                sb.append("Routing guardrails: ").append(request.routeGuardrailsExtraction()).append("\n");
            }
            AgentPretty.AgentSerializationCtx serCtx = isHistorical
                    ? new AgentPretty.AgentSerializationCtx.HistoricalRequestSerializationCtx()
                    : new AgentPretty.AgentSerializationCtx.SkipWorktreeContextSerializationCtx();
            String summary = request.prettyPrint(serCtx);
            if (summary != null && !summary.isBlank()) {
                sb.append("Details: ").append(summary.replace("\n", " | ")).append("\n");
            }
            return sb.toString().trim();
        }

        @Override
        public String template() {
            String header = isHistorical ? HISTORICAL_REQUEST_CONTEXT_HEADER.trim() : CURRENT_PHASE_HEADER.trim();
            return header + "\nRequest type: " + request.getClass().getSimpleName();
        }

        @Override
        public int priority() {
            return contributorPriority;
        }
    }

    record DataCurationContributor(
            String contributorName,
            String header,
            AgentPretty data,
            int contributorPriority
    ) implements PromptContributor {

        @Override
        public String name() {
            return contributorName;
        }

        @Override
        public boolean include(PromptContext ctx) {
            return true;
        }

        @Override
        public String contribute(PromptContext ctx) {
            String rendered = data != null ? data.prettyPrint(new AgentPretty.AgentSerializationCtx.SkipWorktreeContextSerializationCtx()) : null;
            return header + (rendered != null && !rendered.isBlank() ? rendered.trim() : "(none)");
        }

        @Override
        public String template() {
            return header;
        }

        @Override
        public int priority() {
            return contributorPriority;
        }
    }

    record InterruptResolutionContributor(InterruptResolutionEntry entry,
                                          int index,
                                          int contributorPriority) implements PromptContributor {

        @Override
        public String name() {
            return "curation-interrupt-resolution";
        }

        @Override
        public boolean include(PromptContext ctx) {
            return true;
        }

        @Override
        public String contribute(PromptContext ctx) {
            var req = entry.request();
            StringBuilder sb = new StringBuilder();
            sb.append("### Interrupt %d - %s".formatted(index, req.type()));
            if (entry.requestedAt() != null) {
                sb.append(" (at %s)".formatted(entry.requestedAt()));
            }
            sb.append("\n");

            if (req.reason() != null && !req.reason().isBlank()) {
                sb.append("We asked: \"%s\"\n".formatted(req.reason().trim()));
            }

            if (req.contextForDecision() != null && !req.contextForDecision().isBlank()) {
                sb.append("Context for decision: %s\n".formatted(req.contextForDecision().trim()));
            }

            if (req.choices() != null && !req.choices().isEmpty()) {
                sb.append("Options presented:");
                for (var choice : req.choices()) {
                    if (choice.options() != null) {
                        for (var opt : choice.options().entrySet()) {
                            sb.append(" (%s) %s".formatted(opt.getKey(), opt.getValue()));
                        }
                    }
                }
                sb.append("\n");
            }

            if (entry.resolution() != null && !entry.resolution().isBlank()) {
                String responder = req.type() == Events.InterruptType.AGENT_REVIEW
                        ? "The agent" : "The user";
                sb.append("%s responded: \"%s\"\n".formatted(responder, entry.resolution().trim()));
            }

            return sb.toString().trim();
        }

        @Override
        public String template() {
            return "### Interrupt %d".formatted(index);
        }

        @Override
        public int priority() {
            return contributorPriority;
        }
    }

}
