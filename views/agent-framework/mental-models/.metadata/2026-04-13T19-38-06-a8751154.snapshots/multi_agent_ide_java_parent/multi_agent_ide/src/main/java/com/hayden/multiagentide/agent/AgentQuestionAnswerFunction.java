package com.hayden.multiagentide.agent;

import com.embabel.agent.core.AgentPlatform;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.service.InterruptService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class AgentQuestionAnswerFunction implements Function<AskUserQuestionTool.QuestionContext, Map<String, String>> {

    private final AgentPlatform agentPlatform;

    private final GraphRepository graphRepository;

    private InterruptService interruptService;

    @Autowired @Lazy
    public void setInterruptService(InterruptService interruptService) {
        this.interruptService = interruptService;
    }

    @Override
    public Map<String, String> apply(AskUserQuestionTool.QuestionContext questionContext) {
        if (questionContext == null) {
            return Map.of();
        }

        String nodeId = questionContext.sessionId();
        var process = agentPlatform.getAgentProcess(nodeId);
        if (process == null) {
            return Map.of();
        }

        BlackboardHistory history = process.last(BlackboardHistory.class);
        String historySummary = history != null ? Objects.toString(history.summary(), "") : "";

        AgentModels.InterruptRequest.QuestionAnswerInterruptRequest interruptRequest =
                buildInterruptRequest(questionContext.questions(), historySummary);

        PermissionGate.InterruptResolution resolution =
                interruptService.awaitHumanReview(interruptRequest, graphRepository.findById(nodeId).orElse(null));

        return parseAnswers(
                questionContext.questions(),
                interruptRequest.choices(),
                resolution != null ? resolution.getResolutionNotes() : null
        );
    }

    private AgentModels.InterruptRequest.QuestionAnswerInterruptRequest buildInterruptRequest(
            List<AskUserQuestionTool.Question> questions,
            String historySummary
    ) {
        return AgentModels.InterruptRequest.QuestionAnswerInterruptRequest.builder()
                .type(com.hayden.acp_cdc_ai.acp.events.Events.InterruptType.HUMAN_REVIEW)
                .reason("User input required for agent decision.")
                .choices(toStructuredChoices(questions))
                .confirmationItems(List.of())
                .contextForDecision(historySummary)
                .build();
    }

    private List<AgentModels.InterruptRequest.StructuredChoice> toStructuredChoices(
            List<AskUserQuestionTool.Question> questions
    ) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        List<AgentModels.InterruptRequest.StructuredChoice> choices = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            AskUserQuestionTool.Question question = questions.get(i);
            if (question == null) {
                continue;
            }
            Map<String, String> options = toChoiceOptions(question.options());
            String choiceId = "question-" + (i + 1);
            String context = question.header();
            if (Boolean.TRUE.equals(question.multiSelect())) {
                context = context == null || context.isBlank()
                        ? "Multiple selections allowed"
                        : context + " (multi-select)";
            }
            choices.add(AgentModels.InterruptRequest.StructuredChoice.builder()
                    .choiceId(choiceId)
                    .question(question.question())
                    .context(context)
                    .options(options)
                    .recommended(null)
                    .build());
        }
        return choices;
    }

    private Map<String, String> toChoiceOptions(
            List<AskUserQuestionTool.Question.Option> options
    ) {
        Map<String, String> mapped = new LinkedHashMap<>();
        if (options == null || options.isEmpty()) {
            mapped.put("CUSTOM", "Other");
            return mapped;
        }
        String[] keys = new String[] { "A", "B", "C" };
        int idx = 0;
        for (AskUserQuestionTool.Question.Option option : options) {
            if (option == null) {
                continue;
            }
            if (idx < keys.length) {
                mapped.put(keys[idx], option.label());
            } else if (!mapped.containsKey("CUSTOM")) {
                mapped.put("CUSTOM", option.label());
            }
            idx++;
        }
        if (!mapped.containsKey("CUSTOM")) {
            mapped.put("CUSTOM", "Other");
        }
        return mapped;
    }

    private Map<String, String> parseAnswers(
            List<AskUserQuestionTool.Question> questions,
            List<AgentModels.InterruptRequest.StructuredChoice> choices,
            String resolutionNotes
    ) {
        if (resolutionNotes == null || resolutionNotes.isBlank()) {
            return Map.of();
        }

        AgentModels.InterruptRequest.InterruptResolution interruptResolution = tryParseInterruptResolution(resolutionNotes);
        if (interruptResolution != null) {
            Map<String, String> mapped = mapStructuredAnswers(choices, interruptResolution);
            if (!mapped.isEmpty()) {
                return mapped;
            }
        }

        Map<String, String> parsed = tryParseJson(resolutionNotes);
        if (!parsed.isEmpty()) {
            return parsed;
        }

        Map<String, String> fallback = new LinkedHashMap<>();
        if (questions == null || questions.isEmpty()) {
            fallback.put("answer", resolutionNotes);
            return fallback;
        }
        if (questions.size() == 1 && questions.getFirst() != null) {
            fallback.put(questions.getFirst().question(), resolutionNotes);
            return fallback;
        }
        for (AskUserQuestionTool.Question question : questions) {
            if (question != null) {
                fallback.put(question.question(), resolutionNotes);
            }
        }
        return fallback;
    }

    private AgentModels.InterruptRequest.InterruptResolution tryParseInterruptResolution(String value) {
        try {
            return agentPlatform.getPlatformServices()
                    .getObjectMapper()
                    .readValue(value, AgentModels.InterruptRequest.InterruptResolution.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, String> mapStructuredAnswers(
            List<AgentModels.InterruptRequest.StructuredChoice> choices,
            AgentModels.InterruptRequest.InterruptResolution resolution
    ) {
        if (choices == null || choices.isEmpty() || resolution == null) {
            return Map.of();
        }
        Map<String, String> answers = new LinkedHashMap<>();
        Map<String, String> selected = resolution.selectedChoices();
        Map<String, String> customInputs = resolution.customInputs();
        for (AgentModels.InterruptRequest.StructuredChoice choice : choices) {
            if (choice == null) {
                continue;
            }
            String key = choice.choiceId();
            String selectedKey = selected != null ? selected.get(key) : null;
            if (selectedKey == null || selectedKey.isBlank()) {
                continue;
            }
            String answer;
            if ("CUSTOM".equalsIgnoreCase(selectedKey)) {
                answer = customInputs != null ? customInputs.get(key) : null;
            } else {
                answer = choice.options() != null ? choice.options().get(selectedKey) : null;
            }
            if (answer == null || answer.isBlank()) {
                answer = selectedKey;
            }
            answers.put(choice.question(), answer);
        }
        return answers;
    }

    private Map<String, String> tryParseJson(String value) {
        try {
            var mapper = agentPlatform.getPlatformServices().getObjectMapper();
            Map<String, Object> raw = mapper.readValue(value, new TypeReference<Map<String, Object>>() {});
            Map<String, String> parsed = new LinkedHashMap<>();
            for (var entry : raw.entrySet()) {
                parsed.put(entry.getKey(), Objects.toString(entry.getValue(), ""));
            }
            return parsed;
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
