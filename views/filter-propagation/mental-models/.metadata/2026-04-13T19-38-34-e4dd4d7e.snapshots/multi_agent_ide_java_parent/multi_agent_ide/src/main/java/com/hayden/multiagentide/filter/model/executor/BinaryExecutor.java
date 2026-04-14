package com.hayden.multiagentide.filter.model.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import lombok.Builder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executor that runs an external binary command with optional environment overrides.
 */
@Builder(toBuilder = true)
public record BinaryExecutor<I, O, CTX extends FilterContext>(
        List<String> command,
        String workingDirectory,
        Map<String, String> env,
        String outputParserRef,
        int timeoutMs,
        String configVersion
) implements ExecutableTool<I, O, CTX> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        OBJECT_MAPPER.registerModule(module);
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    @Override
    public FilterEnums.ExecutorType executorType() {
        return FilterEnums.ExecutorType.BINARY;
    }

    @Override
    public FilterResult<O> apply(I i, CTX ctx) {
        if (command == null || command.isEmpty()) {
            IllegalStateException error = new IllegalStateException("BINARY executor requires a non-empty command");
            ObjectMapper objectMapper = ExecutableTool.contextObjectMapper(ctx, OBJECT_MAPPER);
            return new FilterResult<>(
                    ExecutableTool.fallbackOutput(i, ctx, objectMapper),
                    buildBinaryFilterDescriptor(List.of(), ctx)
                            .and(ExecutableTool.executorErrorDescriptor(error, FilterEnums.ExecutorType.BINARY, Map.of()))
            );
        }

        List<String> resolvedCommand = List.of();
        ObjectMapper objectMapper = ExecutableTool.contextObjectMapper(ctx, OBJECT_MAPPER);
        try {
            resolvedCommand = resolveCommand(ctx);
            String response = runExternalCommand(resolvedCommand, i, ctx, objectMapper);
            O output = parseResponse(response, i, ctx, objectMapper);
            return new FilterResult<>(output, buildBinaryFilterDescriptor(resolvedCommand, ctx));
        } catch (Exception e) {
            return new FilterResult<>(
                    ExecutableTool.fallbackOutput(i, ctx, objectMapper),
                    buildBinaryFilterDescriptor(resolvedCommand, ctx)
                            .and(ExecutableTool.executorErrorDescriptor(e, FilterEnums.ExecutorType.BINARY, Map.of()))
            );
        }
    }

    private List<String> resolveCommand(CTX ctx) {
        List<String> resolved = new ArrayList<>(command);
        if (resolved.isEmpty()) {
            return resolved;
        }

        String first = resolved.get(0);
        if (first == null || first.isBlank()) {
            return resolved;
        }
        Path firstPath = Paths.get(first);
        if (firstPath.isAbsolute()) {
            return resolved;
        }

        if (ctx != null
                && ctx.filterConfigProperties() != null
                && ctx.filterConfigProperties().getBins() != null) {
            Path candidate = ctx.filterConfigProperties().getBins().resolve(first).normalize();
            resolved.set(0, candidate.toString());
        }
        return resolved;
    }

    private String runExternalCommand(List<String> resolvedCommand, I input, CTX ctx, ObjectMapper objectMapper) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(resolvedCommand);

        if (workingDirectory != null && !workingDirectory.isBlank()) {
            processBuilder.directory(new File(workingDirectory));
        } else if (ctx != null
                && ctx.filterConfigProperties() != null
                && ctx.filterConfigProperties().getBins() != null) {
            processBuilder.directory(ctx.filterConfigProperties().getBins().toFile());
        }

        if (env != null && !env.isEmpty()) {
            processBuilder.environment().putAll(env);
        }

        Process process = processBuilder.start();
        try (var stdin = process.getOutputStream()) {
            stdin.write(buildRequestPayload(input, ctx, objectMapper).getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        boolean finished = process.waitFor(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Executor timed out after " + timeoutMs + "ms");
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Executor exited " + process.exitValue() + ": "
                    + (stderr.isBlank() ? stdout : stderr));
        }

        return stdout;
    }

    private String buildRequestPayload(I input, CTX ctx, ObjectMapper objectMapper) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("input", ExecutableTool.serializeExecutorInput(input, objectMapper));
        payload.put("context", ctx);
        return objectMapper.writeValueAsString(payload);
    }

    private O parseResponse(String response, I input, CTX ctx, ObjectMapper objectMapper) throws Exception {
        return ExecutableTool.parseExecutorResponse(response, input, ctx, objectMapper);
    }

    private FilterDescriptor buildBinaryFilterDescriptor(List<String> resolvedCommand, CTX ctx) {
        Map<String, String> details = new java.util.LinkedHashMap<>();
        if (resolvedCommand != null && !resolvedCommand.isEmpty()) {
            details.put("command", String.join(" ", resolvedCommand));
        }
        putIfPresent(details, "workingDirectory", workingDirectory);
        putIfPresent(details, "outputParserRef", outputParserRef);
        details.put("timeoutMs", String.valueOf(timeoutMs));
        putIfPresent(details, "configVersion", configVersion);
        if (env != null && !env.isEmpty()) {
            details.put("envCount", String.valueOf(env.size()));
        }
        Path binaryPath = resolveBinaryPath(resolvedCommand, ctx);
        if (binaryPath != null) {
            details.put("binaryPath", binaryPath.toString());
        }

        FilterDescriptor.Entry entry = new FilterDescriptor.Entry(
                "EXECUTOR",
                null,
                null,
                null,
                null,
                null,
                "TRANSFORMED",
                FilterEnums.ExecutorType.BINARY.name(),
                details,
                List.of()
        );
        return new FilterDescriptor.SimpleFilterDescriptor(List.of(), entry);
    }

    private Path resolveBinaryPath(List<String> resolvedCommand, CTX ctx) {
        if (resolvedCommand == null || resolvedCommand.isEmpty()) {
            return null;
        }
        String first = resolvedCommand.getFirst();
        if (first == null || first.isBlank()) {
            return null;
        }
        Path raw = Paths.get(first);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        if (ctx != null
                && ctx.filterConfigProperties() != null
                && ctx.filterConfigProperties().getBins() != null) {
            return ctx.filterConfigProperties().getBins().resolve(raw).normalize();
        }
        return raw.normalize();
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value);
    }
}
