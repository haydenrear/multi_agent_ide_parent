package com.hayden.multiagentide.filter.model.interpreter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.acp_cdc_ai.acp.filter.InstructionMatcher;
import com.hayden.utilitymodule.result.Result;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "interpreterType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Interpreter.RegexInterpreter.class, name = "REGEX"),
        @JsonSubTypes.Type(value = Interpreter.MarkdownPathInterpreter.class, name = "MARKDOWN_PATH"),
        @JsonSubTypes.Type(value = Interpreter.JsonPathInterpreter.class, name = "JSON_PATH")
})
public sealed interface Interpreter
        permits Interpreter.RegexInterpreter, Interpreter.MarkdownPathInterpreter, Interpreter.JsonPathInterpreter {

    FilterEnums.InterpreterType interpreterType();

    Result<String, InterpreterError> apply(String input, List<Instruction> instructions);

    private static boolean matcherMatches(InstructionMatcher matcher, String content) {
        return switch (matcher.matcherType()) {
            case EQUALS -> Objects.equals(
                    content != null ? content.strip() : null,
                    matcher.value() != null ? matcher.value().strip() : null);
            case CONTAINS -> content != null && matcher.value() != null && content.contains(matcher.value());
            case REGEX -> Pattern.compile(matcher.value()).matcher(content).find();
        };
    }

    // ── RegexInterpreter ─────────────────────────────────────────────

    @Builder(toBuilder = true)
    record RegexInterpreter() implements Interpreter {
        @Override
        public FilterEnums.InterpreterType interpreterType() {
            return FilterEnums.InterpreterType.REGEX;
        }

        @Override
        public Result<String, InterpreterError> apply(String input, List<Instruction> instructions) {
            String result = input;
            List<Instruction> sorted = instructions.stream()
                    .sorted(Comparator.comparingInt(Instruction::order))
                    .toList();

            for (Instruction instruction : sorted) {
                String expression = instruction.targetPath().expression();
                Pattern pattern;
                try {
                    pattern = Pattern.compile(expression);
                } catch (Exception e) {
                    return Result.err(new InterpreterError(
                            "Invalid regex pattern: " + expression, e));
                }

                result = switch (instruction) {
                    case Instruction.Replace replace ->
                            pattern.matcher(result).replaceAll(
                                    Matcher.quoteReplacement(replace.value().toString()));
                    case Instruction.Set set ->
                            pattern.matcher(result).replaceAll(
                                    Matcher.quoteReplacement(set.value().toString()));
                    case Instruction.Remove ignored ->
                            pattern.matcher(result).replaceAll("");
                    case Instruction.ReplaceIfMatch rim -> {
                        Matcher m = pattern.matcher(result);
                        StringBuilder sb = new StringBuilder();
                        while (m.find()) {
                            String matched = m.group();
                            if (matcherMatches(rim.matcher(), matched)) {
                                m.appendReplacement(sb, Matcher.quoteReplacement(rim.value().toString()));
                            } else {
                                m.appendReplacement(sb, Matcher.quoteReplacement(matched));
                            }
                        }
                        m.appendTail(sb);
                        yield sb.toString();
                    }
                    case Instruction.RemoveIfMatch rim -> {
                        Matcher m = pattern.matcher(result);
                        StringBuilder sb = new StringBuilder();
                        while (m.find()) {
                            String matched = m.group();
                            if (matcherMatches(rim.matcher(), matched)) {
                                m.appendReplacement(sb, "");
                            } else {
                                m.appendReplacement(sb, Matcher.quoteReplacement(matched));
                            }
                        }
                        m.appendTail(sb);
                        yield sb.toString();
                    }
                };
            }
            return Result.ok(result);
        }
    }

    // ── JsonPathInterpreter ──────────────────────────────────────────

    @Builder(toBuilder = true)
    record JsonPathInterpreter() implements Interpreter {
        @Override
        public FilterEnums.InterpreterType interpreterType() {
            return FilterEnums.InterpreterType.JSON_PATH;
        }

        @Override
        public Result<String, InterpreterError> apply(String input, List<Instruction> instructions) {
            DocumentContext doc;
            try {
                doc = JsonPath.parse(input);
            } catch (Exception e) {
                return Result.err(new InterpreterError("Failed to parse JSON input", e));
            }

            List<Instruction> sorted = instructions.stream()
                    .sorted(Comparator.comparingInt(Instruction::order))
                    .toList();

            for (Instruction instruction : sorted) {
                String path = instruction.targetPath().expression();
                try {
                    if (Objects.equals(path, "$") && instruction.op() == FilterEnums.InstructionOp.REMOVE) {
                        return Result.ok("");
                    }

                    switch (instruction) {
                        case Instruction.Replace replace -> doc.set(path, replace.value());
                        case Instruction.Set set -> doc.set(path, set.value());
                        case Instruction.Remove ignored -> doc.delete(path);
                        case Instruction.ReplaceIfMatch rim -> {
                            Object current = doc.read(path);
                            String content = current != null ? current.toString() : "";
                            if (matcherMatches(rim.matcher(), content)) {
                                doc.set(path, rim.value());
                            }
                        }
                        case Instruction.RemoveIfMatch rim -> {
                            Object current = doc.read(path);
                            String content = current != null ? current.toString() : "";
                            if (matcherMatches(rim.matcher(), content)) {
                                doc.delete(path);
                            }
                        }
                    }
                } catch (Exception e) {
                    return Result.err(new InterpreterError(
                            "Failed to apply " + instruction.op() + " at path: " + path, e));
                }
            }
            return Result.ok(doc.jsonString());
        }
    }

    // ── MarkdownPathInterpreter ──────────────────────────────────────

    @Builder(toBuilder = true)
    record MarkdownPathInterpreter() implements Interpreter {
        private static final Pattern HEADING_PATH_PATTERN = Pattern.compile("^(#{1,6})\\s*(.*?)\\s*$");
        private static final Pattern HEADING_LINE_PATTERN = Pattern.compile("^\\s{0,3}(#{1,6})\\s*(.*?)\\s*$", Pattern.MULTILINE);

        @Override
        public FilterEnums.InterpreterType interpreterType() {
            return FilterEnums.InterpreterType.MARKDOWN_PATH;
        }

        @Override
        public Result<String, InterpreterError> apply(String input, List<Instruction> instructions) {
            String result = input;
            List<Instruction> sorted = instructions.stream()
                    .sorted(Comparator.comparingInt(Instruction::order))
                    .toList();

            for (Instruction instruction : sorted) {
                if (instruction.op() == FilterEnums.InstructionOp.REMOVE && isRootPath(instruction.targetPath().expression()))
                    return Result.ok("");

                String pathExpr = instruction.targetPath().expression();
                var parsed = parseHeading(pathExpr);
                if (parsed == null) {
                    return Result.err(new InterpreterError(
                            "Invalid markdown path: " + pathExpr
                                    + " (expected format: '## Section Name')"));
                }
                result = applyToAllMatchingSections(result, parsed.level(), parsed.text(), instruction);
            }
            return Result.ok(result);
        }

        private record ParsedHeading(int level, String text) {}

        private record HeadingMatch(int level, String normalizedText, int start) {}

        private boolean isRootPath(String pathExpression) {
            return pathExpression != null && pathExpression.strip().equals("#");
        }

        private String normalizeHeadingText(String value) {
            if (value == null) {
                return "";
            }
            return value.strip()
                    .replaceAll("\\s+", "")
                    .toLowerCase(Locale.ROOT);
        }

        private ParsedHeading parseHeading(String headingPattern) {
            if (headingPattern == null) {
                return null;
            }
            Matcher hMatch = HEADING_PATH_PATTERN.matcher(headingPattern.strip());
            if (!hMatch.matches()) {
                return null;
            }
            String normalizedHeading = normalizeHeadingText(hMatch.group(2));
            if (normalizedHeading.isEmpty()) {
                return null;
            }
            return new ParsedHeading(hMatch.group(1).length(), normalizedHeading);
        }

        private String applyToAllMatchingSections(String input, int level, String headingText, Instruction instruction) {
            List<int[]> ranges = findAllSectionRanges(input, level, headingText);
            if (ranges.isEmpty()) {
                return input;
            }

            String result = input;
            for (int i = ranges.size() - 1; i >= 0; i--) {
                int[] range = ranges.get(i);
                result = applySingle(result, range, instruction);
            }
            return result;
        }

        private String applySingle(String input, int[] range, Instruction instruction) {
            int headingEnd = input.indexOf('\n', range[0]);
            if (headingEnd == -1 || headingEnd >= range[1]) {
                headingEnd = range[1];
            } else {
                headingEnd++;
            }

            String sectionContent = input.substring(headingEnd, range[1]);

            return switch (instruction) {
                case Instruction.Replace replace -> {
                    String newContent = replace.value().toString();
                    yield input.substring(0, headingEnd)
                            + newContent + (newContent.endsWith("\n") ? "" : "\n")
                            + input.substring(range[1]);
                }
                case Instruction.Set set -> {
                    String newContent = set.value().toString();
                    yield input.substring(0, headingEnd)
                            + newContent + (newContent.endsWith("\n") ? "" : "\n")
                            + input.substring(range[1]);
                }
                case Instruction.Remove ignored ->
                        input.substring(0, range[0]) + input.substring(range[1]);
                case Instruction.ReplaceIfMatch rim -> {
                    if (matcherMatches(rim.matcher(), sectionContent)) {
                        String newContent = rim.value().toString();
                        yield input.substring(0, headingEnd)
                                + newContent + (newContent.endsWith("\n") ? "" : "\n")
                                + input.substring(range[1]);
                    }
                    yield input;
                }
                case Instruction.RemoveIfMatch rim -> {
                    if (matcherMatches(rim.matcher(), sectionContent)) {
                        yield input.substring(0, range[0]) + input.substring(range[1]);
                    }
                    yield input;
                }
            };
        }

        private List<int[]> findAllSectionRanges(String input, int level, String headingText) {
            List<int[]> ranges = new ArrayList<>();
            List<HeadingMatch> headings = new ArrayList<>();
            Matcher m = HEADING_LINE_PATTERN.matcher(input);
            while (m.find()) {
                String normalized = normalizeHeadingText(m.group(2));
                if (normalized.isEmpty()) {
                    continue;
                }
                headings.add(new HeadingMatch(m.group(1).length(), normalized, m.start()));
            }

            for (int i = 0; i < headings.size(); i++) {
                HeadingMatch heading = headings.get(i);
                if (heading.level() != level || !heading.normalizedText().equals(headingText)) {
                    continue;
                }

                int end = input.length();
                for (int j = i + 1; j < headings.size(); j++) {
                    if (headings.get(j).level() <= level) {
                        end = headings.get(j).start();
                        break;
                    }
                }
                ranges.add(new int[]{heading.start(), end});
            }
            return ranges;
        }
    }
}
