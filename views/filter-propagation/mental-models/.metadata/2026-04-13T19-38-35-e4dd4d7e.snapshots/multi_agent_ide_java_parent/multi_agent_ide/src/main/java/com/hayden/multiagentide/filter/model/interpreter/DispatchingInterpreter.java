package com.hayden.multiagentide.filter.model.interpreter;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.utilitymodule.result.Result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies instructions by dispatching each ordered segment to the interpreter
 * selected by targetPath.pathType.
 */
public final class DispatchingInterpreter {

    private final Interpreter.RegexInterpreter regexInterpreter = new Interpreter.RegexInterpreter();
    private final Interpreter.MarkdownPathInterpreter markdownInterpreter = new Interpreter.MarkdownPathInterpreter();
    private final Interpreter.JsonPathInterpreter jsonPathInterpreter = new Interpreter.JsonPathInterpreter();

    public Result<String, InterpreterError> apply(String input, List<Instruction> instructions) {
        if (instructions == null || instructions.isEmpty()) {
            return Result.ok(input);
        }

        List<Instruction> sorted = instructions.stream()
                .sorted(Comparator.comparingInt(Instruction::order))
                .toList();

        String current = input;
        int index = 0;
        while (index < sorted.size()) {
            Instruction first = sorted.get(index);
            FilterEnums.PathType pathType = resolvePathType(first);
            if (pathType == null) {
                return Result.err(new InterpreterError("Instruction targetPath.pathType is required"));
            }

            List<Instruction> batch = new ArrayList<>();
            int cursor = index;
            while (cursor < sorted.size() && pathType == resolvePathType(sorted.get(cursor))) {
                batch.add(sorted.get(cursor));
                cursor++;
            }

            Result<String, InterpreterError> batchResult = applyBatch(pathType, current, batch);
            if (batchResult.isErr()) {
                return batchResult;
            }
            current = batchResult.unwrap();
            index = cursor;
        }

        return Result.ok(current);
    }

    private Result<String, InterpreterError> applyBatch(FilterEnums.PathType pathType,
                                                        String input,
                                                        List<Instruction> instructions) {
        return switch (pathType) {
            case REGEX -> regexInterpreter.apply(input, instructions);
            case MARKDOWN_PATH -> markdownInterpreter.apply(input, instructions);
            case JSON_PATH -> jsonPathInterpreter.apply(input, instructions);
        };
    }

    private FilterEnums.PathType resolvePathType(Instruction instruction) {
        if (instruction == null || instruction.targetPath() == null) {
            return null;
        }
        return instruction.targetPath().pathType();
    }
}
