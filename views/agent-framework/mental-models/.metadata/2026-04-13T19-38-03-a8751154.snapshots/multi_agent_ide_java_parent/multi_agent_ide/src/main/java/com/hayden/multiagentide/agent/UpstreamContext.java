package com.hayden.multiagentide.agent;

import com.hayden.multiagentide.template.ConsolidationTemplate;
import com.hayden.multiagentide.template.PlanningTicket;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.Builder;
import lombok.With;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public sealed interface UpstreamContext extends AgentContext
        permits
            UpstreamContext.DiscoveryCollectorContext,
            UpstreamContext.PlanningCollectorContext,
            UpstreamContext.TicketCollectorContext {

    ArtifactKey contextId();

    @Override
    default String prettyPrint(AgentSerializationCtx serializationCtx) {
        return switch (serializationCtx) {
            case AgentSerializationCtx.StdReceiverSerialization stdReceiverSerialization ->
                    prettyPrint();
            case AgentSerializationCtx.InterruptSerialization interruptSerialization ->
                    prettyPrintInterruptContinuation();
            case AgentSerializationCtx.GoalResolutionSerialization goalResolutionSerialization ->
                    prettyPrint();
            case AgentSerializationCtx.MergeSummarySerialization mergeSummarySerialization ->
                    prettyPrint();
            case AgentSerializationCtx.ResultsSerialization resultsSerialization ->
                    prettyPrint();
            case AgentSerializationCtx.SkipWorktreeContextSerializationCtx skipWorktreeCtx -> {
                AgentPretty.ACTIVE_SERIALIZATION_CTX.set(skipWorktreeCtx);
                try {
                    yield prettyPrint();
                } finally {
                    AgentPretty.ACTIVE_SERIALIZATION_CTX.remove();
                }
            }
            case AgentSerializationCtx.HistoricalRequestSerializationCtx historicalCtx -> {
                AgentPretty.ACTIVE_SERIALIZATION_CTX.set(historicalCtx);
                try {
                    yield prettyPrint();
                } finally {
                    AgentPretty.ACTIVE_SERIALIZATION_CTX.remove();
                }
            }
            case AgentSerializationCtx.CompactifyingRequestSerializer compactCtx -> {
                AgentPretty.ACTIVE_SERIALIZATION_CTX.set(compactCtx);
                try {
                    yield prettyPrint();
                } finally {
                    AgentPretty.ACTIVE_SERIALIZATION_CTX.remove();
                }
            }
        };
    }

    @Override
    default String prettyPrint() {
        return "";
    }

    private static <T extends Artifact.AgentModel> T firstChildOfType(
            List<Artifact.AgentModel> children,
            Class<T> type,
            T fallback
    ) {
        if (children == null || children.isEmpty()) {
            return fallback;
        }
        for (Artifact.AgentModel child : children) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
        }
        return fallback;
    }

    private static <T extends Artifact.AgentModel> List<T> childrenOfType(
            List<Artifact.AgentModel> children,
            Class<T> type,
            List<T> fallback
    ) {
        if (children == null || children.isEmpty()) {
            return fallback;
        }
        List<T> results = new ArrayList<>();
        for (Artifact.AgentModel child : children) {
            if (type.isInstance(child)) {
                results.add(type.cast(child));
            }
        }
        return results.isEmpty() ? fallback : List.copyOf(results);
    }

    private static void appendKey(StringBuilder builder, String label, ArtifactKey key) {
        builder.append(label).append(": ");
        if (key == null || key.value() == null || key.value().isBlank()) {
            builder.append("(none)\n");
            return;
        }
        builder.append(key.value()).append("\n");
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        builder.append(label).append(": ");
        if (value == null) {
            builder.append("(none)\n");
            return;
        }
        if (value.isBlank()) {
            builder.append("(empty)\n");
            return;
        }
        builder.append(value.trim()).append("\n");
    }

    private static void appendSection(StringBuilder builder, String label, String value) {
        builder.append(label).append(":\n");
        if (value == null) {
            builder.append("\t(none)\n");
            return;
        }
        if (value.isBlank()) {
            builder.append("\t(empty)\n");
            return;
        }
        builder.append("\t").append(value.trim().replace("\n", "\n\t")).append("\n");
    }

    private static void appendContext(StringBuilder builder, String label, AgentPretty context) {
        builder.append(label).append(":\n");
        if (context == null) {
            builder.append("\t(none)\n");
            return;
        }
        String rendered = context.prettyPrint();
        if (rendered == null || rendered.isBlank()) {
            builder.append("\t(empty)\n");
            return;
        }
        builder.append("\t").append(rendered.trim().replace("\n", "\n\t")).append("\n");
    }

    private static void appendStringList(StringBuilder builder, String label, List<String> values, String indent) {
        builder.append(indent).append(label).append(":\n");
        if (values == null || values.isEmpty()) {
            builder.append(indent).append("\t(none)\n");
            return;
        }
        for (String value : values) {
            if (value == null) {
                builder.append(indent).append("\t- (none)\n");
                continue;
            }
            if (value.isBlank()) {
                builder.append(indent).append("\t- (empty)\n");
                continue;
            }
            builder.append(indent).append("\t- ").append(value.trim()).append("\n");
        }
    }

    private static void appendStringMap(StringBuilder builder, String label, Map<String, String> values, String indent) {
        builder.append(indent).append(label).append(":\n");
        if (values == null || values.isEmpty()) {
            builder.append(indent).append("\t(none)\n");
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey() == null || entry.getKey().isBlank() ? "(blank-key)" : entry.getKey().trim();
            String value = entry.getValue();
            if (value == null) {
                builder.append(indent).append("\t- ").append(key).append(": (none)\n");
                continue;
            }
            if (value.isBlank()) {
                builder.append(indent).append("\t- ").append(key).append(": (empty)\n");
                continue;
            }
            builder.append(indent).append("\t- ").append(key).append(": ").append(value.trim()).append("\n");
        }
    }

    private static void appendTicket(StringBuilder builder, String label, PlanningTicket ticket) {
        builder.append(label).append(":\n");
        if (ticket == null) {
            builder.append("\t(none)\n");
            return;
        }
        appendKey(builder, "\tContext Id", ticket.contextId());
        appendLine(builder, "\tSchema Version", ticket.schemaVersion());
        appendKey(builder, "\tResult Id", ticket.resultId());
        appendLine(builder, "\tTicket Id", ticket.ticketId());
        appendLine(builder, "\tTitle", ticket.title());
        appendSection(builder, "\tDescription", ticket.description());
        appendStringList(builder, "Dependencies", ticket.dependencies(), "\t");
        appendStringList(builder, "Acceptance Criteria", ticket.acceptanceCriteria(), "\t");
        appendLine(builder, "\tEffort Estimate", ticket.effortEstimate());
        appendLine(builder, "\tPriority", String.valueOf(ticket.priority()));
        builder.append("\tTasks:\n");
        if (ticket.tasks() == null || ticket.tasks().isEmpty()) {
            builder.append("\t\t(none)\n");
        } else {
            for (PlanningTicket.TicketTask task : ticket.tasks()) {
                if (task == null) {
                    builder.append("\t\t- (none)\n");
                    continue;
                }
                builder.append("\t\t- id=").append(task.taskId())
                        .append(", desc=").append(task.description())
                        .append(", hours=").append(task.estimatedHours())
                        .append("\n");
                appendStringList(builder, "Related Files", task.relatedFiles(), "\t\t");
            }
        }
        builder.append("\tDiscovery Links:\n");
        if (ticket.discoveryLinks() == null || ticket.discoveryLinks().isEmpty()) {
            builder.append("\t\t(none)\n");
        } else {
            for (PlanningTicket.DiscoveryLink link : ticket.discoveryLinks()) {
                if (link == null) {
                    builder.append("\t\t- (none)\n");
                    continue;
                }
                builder.append("\t\t- discoveryResultId=")
                        .append(link.discoveryResultId() == null ? "(none)" : link.discoveryResultId().value())
                        .append(", referenceId=").append(link.referenceId())
                        .append(", rationale=").append(link.linkRationale())
                        .append("\n");
            }
        }
        builder.append("\tMemory References:\n");
        if (ticket.memoryReferences() == null || ticket.memoryReferences().isEmpty()) {
            builder.append("\t\t(none)\n");
        } else {
            for (var memoryReference : ticket.memoryReferences()) {
                builder.append("\t\t- ");
                if (memoryReference == null) {
                    builder.append("(none)\n");
                    continue;
                }
                builder.append("referenceId=").append(memoryReference.referenceId())
                        .append(", type=").append(memoryReference.memoryType())
                        .append(", summary=").append(memoryReference.summary())
                        .append("\n");
                appendStringMap(builder, "Metadata", memoryReference.metadata(), "\t\t");
            }
        }
    }
    @Builder
    @With
    record DiscoveryCollectorContext(
            @SkipPropertyFilter
            ArtifactKey contextId,
            AgentModels.DiscoveryCuration curation,
            String selectionRationale
    ) implements UpstreamContext, ConsolidationTemplate.Curation {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String rationale = selectionRationale == null ? "" : selectionRationale;
            String curationHash = curation == null ? "" : curation.computeHash(hashContext);
            return hashContext.hash(rationale + "|" + curationHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (curation != null) {
                children.add(curation);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.DiscoveryCuration updatedCuration =
                    firstChildOfType(children, AgentModels.DiscoveryCuration.class, curation);
            return (T) new DiscoveryCollectorContext(
                    contextId,
                    updatedCuration,
                    selectionRationale
            );
        }
        
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Discovery Collector Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Selection Rationale", selectionRationale);
            appendContext(builder, "Curation", curation);
            return builder.toString().trim();
        }
    }

    @With
    record PlanningCollectorContext(
            @SkipPropertyFilter
            ArtifactKey contextId,
            AgentModels.PlanningCuration curation,
            String selectionRationale
    ) implements UpstreamContext, ConsolidationTemplate.Curation {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String rationale = selectionRationale == null ? "" : selectionRationale;
            String curationHash = curation == null ? "" : curation.computeHash(hashContext);
            return hashContext.hash(rationale + "|" + curationHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (curation != null) {
                children.add(curation);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.PlanningCuration updatedCuration =
                    firstChildOfType(children, AgentModels.PlanningCuration.class, curation);
            return (T) new PlanningCollectorContext(
                    contextId,
                    updatedCuration,
                    selectionRationale
            );
        }
        
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Planning Collector Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Selection Rationale", selectionRationale);
            appendContext(builder, "Curation", curation);
            return builder.toString().trim();
        }
        
        public String prettyPrintTickets() {
            if (curation == null || curation.finalizedTickets() == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (var ticket : curation.finalizedTickets()) {
                sb.append("## ").append(ticket.ticketId()).append(": ").append(ticket.title()).append("\n");
                if (ticket.description() != null) {
                    sb.append(ticket.description()).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        }
    }

    @With
    record TicketCollectorContext(
            @SkipPropertyFilter
            ArtifactKey contextId,
            AgentModels.TicketCuration curation,
            String selectionRationale
    ) implements UpstreamContext, ConsolidationTemplate.Curation {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String rationale = selectionRationale == null ? "" : selectionRationale;
            String curationHash = curation == null ? "" : curation.computeHash(hashContext);
            return hashContext.hash(rationale + "|" + curationHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (curation != null) {
                children.add(curation);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.TicketCuration updatedCuration =
                    firstChildOfType(children, AgentModels.TicketCuration.class, curation);
            return (T) new TicketCollectorContext(
                    contextId,
                    updatedCuration,
                    selectionRationale
            );
        }
        
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Ticket Collector Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Selection Rationale", selectionRationale);
            appendContext(builder, "Curation", curation);
            return builder.toString().trim();
        }
    }
}
