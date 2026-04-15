package com.hayden.multiagentide.agent;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.multiagentide.events.DegenerateLoopException;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class DefaultDegenerateLoopPolicy implements DegenerateLoopPolicy {

    private static final int REPETITION_THRESHOLD = 6;

    private EventBus eventBus;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public Optional<DegenerateLoopException> detectLoop(BlackboardHistory history, String actionName, Artifact.AgentModel input) {
        if (history == null) {
            return Optional.empty();
        }

        List<String> nodeSequence = history.copyOfEntries().stream()
                .map(BlackboardHistory.Entry::input)
                .filter(Events.ActionStartedEvent.class::isInstance)
                .map(Events.ActionStartedEvent.class::cast)
                .filter(event -> BlackboardHistory.isContextRequestOrInterrupt(history).test(event.actionName()))
                .map(Events.ActionStartedEvent::nodeId)
                .filter(Objects::nonNull)
                .toList();

        if (!hasRepeatedPattern(nodeSequence, REPETITION_THRESHOLD)) {
            return Optional.empty();
        }

        Class<?> inputType = input != null ? input.getClass() : Object.class;
        String message = "Degenerate loop detected by node repetition policy.";
        eventBus.publish(Events.NodeErrorEvent.err(message, input.key()));
        return Optional.of(new DegenerateLoopException(
                message,
                actionName,
                inputType,
                REPETITION_THRESHOLD
        ));
    }

    private static boolean hasRepeatedPattern(List<String> sequence, int repetitions) {
        if (sequence == null || sequence.isEmpty() || repetitions < 2) {
            return false;
        }
        int total = sequence.size();
        int maxPatternLength = total / repetitions;
        for (int patternLength = 1; patternLength <= maxPatternLength; patternLength++) {
            int window = patternLength * repetitions;
            for (int start = 0; start <= total - window; start++) {
                if (matchesPattern(sequence, start, patternLength, repetitions)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesPattern(List<String> sequence, int start, int patternLength, int repetitions) {
        for (int repetition = 1; repetition < repetitions; repetition++) {
            int offset = repetition * patternLength;
            for (int index = 0; index < patternLength; index++) {
                String first = sequence.get(start + index);
                String next = sequence.get(start + offset + index);
                if (!Objects.equals(first, next)) {
                    return false;
                }
            }
        }
        return true;
    }
}
