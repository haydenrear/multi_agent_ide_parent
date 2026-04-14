package com.hayden.multiagentide.filter.model.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hayden.acp_cdc_ai.acp.events.ArtifactHashing;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import lombok.Builder;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executor that runs a Python script subprocess.
 */
@Builder(toBuilder = true)
@Slf4j
public record PythonExecutor<I, O, CTX extends FilterContext>(
        String scriptPath,
        String entryFunction,
        Object runtimeArgsSchema,
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
        return FilterEnums.ExecutorType.PYTHON;
    }

    @Override
    public FilterResult<O> apply(I i, CTX filterContext) {
        ObjectMapper objectMapper = ExecutableTool.contextObjectMapper(filterContext, OBJECT_MAPPER);
        try {
            String uvExecutable = resolveUvExecutable(filterContext);
            String resolvedScriptPath = resolveScriptPath(filterContext);
            List<String> command = new ArrayList<>();
            command.add(uvExecutable);
            command.add("run");
            command.add(resolvedScriptPath);
            if (entryFunction != null && !entryFunction.isBlank()) {
                command.add(entryFunction);
            }

            String response = runExternalCommand(command, i, filterContext, objectMapper);
            O output = parseResponse(response, i, filterContext, objectMapper);
            return new FilterResult<>(output, buildPythonFilterDescriptor(filterContext, objectMapper));
        } catch (Exception e) {
            log.error("Error when attempting to filter {}.", i, e);
            return new FilterResult<>(
                    ExecutableTool.fallbackOutput(i, filterContext, objectMapper),
                    new FilterDescriptor.NoOpFilterDescriptor());
        }
    }

    private String resolveUvExecutable(CTX filterContext) {
        if (filterContext != null
                && filterContext.filterConfigProperties() != null
                && filterContext.filterConfigProperties().getUv() != null) {
            return filterContext.filterConfigProperties().getUv().toString();
        }
        return "uv";
    }

    private String resolveScriptPath(CTX filterContext) {
        if (scriptPath == null || scriptPath.isBlank()) {
            throw new IllegalStateException("PYTHON executor requires scriptPath");
        }
        Path script = Paths.get(scriptPath);
        if (script.isAbsolute()) {
            return script.toString();
        }
        if (filterContext != null
                && filterContext.filterConfigProperties() != null
                && filterContext.filterConfigProperties().getBins() != null) {
            return filterContext.filterConfigProperties().getBins().resolve(script).normalize().toString();
        }
        return scriptPath;
    }

    private String runExternalCommand(List<String> command, I input, CTX filterContext, ObjectMapper objectMapper) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (filterContext != null
                && filterContext.filterConfigProperties() != null
                && filterContext.filterConfigProperties().getBins() != null) {
            processBuilder.directory(filterContext.filterConfigProperties().getBins().toFile());
        }

        Process process = processBuilder.start();
        CompletableFuture<String> stdoutFuture = readStreamAsync(process.getInputStream());
        CompletableFuture<String> stderrFuture = readStreamAsync(process.getErrorStream());
        try (var stdin = process.getOutputStream()) {
            stdin.write(buildRequestPayload(input, filterContext, objectMapper).getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        boolean finished = process.waitFor(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Executor timed out after " + timeoutMs + "ms");
        }

        String stdout = stdoutFuture.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS).trim();
        String stderr = stderrFuture.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS).trim();
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Executor exited " + process.exitValue() + ": "
                    + (stderr.isBlank() ? stdout : stderr));
        }
        return stdout;
    }

    private CompletableFuture<String> readStreamAsync(java.io.InputStream stream) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (stream) {
                future.complete(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private String buildRequestPayload(I input, CTX ctx, ObjectMapper objectMapper) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", ExecutableTool.serializeExecutorInput(input, objectMapper));
        payload.put("entryFunction", entryFunction);
        payload.put("runtimeArgsSchema", runtimeArgsSchema);
        return objectMapper.writeValueAsString(payload);
    }

    private O parseResponse(String response, I input, CTX filterContext, ObjectMapper objectMapper) throws Exception {
        return ExecutableTool.parseExecutorResponse(response, input, filterContext, objectMapper);
    }

    private FilterDescriptor buildPythonFilterDescriptor(CTX filterContext, ObjectMapper objectMapper) {
        Map<String, String> details = new LinkedHashMap<>();
        String resolvedScriptPath = safeResolveScriptPath(filterContext);
        putIfPresent(details, "scriptPath", resolvedScriptPath);
        details.put("timeoutMs", String.valueOf(timeoutMs));
        putIfPresent(details, "entryFunction", entryFunction);
        putIfPresent(details, "configVersion", configVersion);
        putIfPresent(details, "uvExecutable", resolveUvExecutable(filterContext));
        if (runtimeArgsSchema != null) {
            details.put("runtimeArgsSchemaJson", toJson(runtimeArgsSchema, objectMapper));
        }

        if (resolvedScriptPath != null && !resolvedScriptPath.isBlank()) {
            Path script = Paths.get(resolvedScriptPath);
            if (Files.exists(script) && Files.isRegularFile(script)) {
                putIfPresent(details, "scriptHash", hashScript(script));
                putIfPresent(details, "scriptText", readScriptText(script));
            }
        }

        FilterDescriptor.Entry entry = new FilterDescriptor.Entry(
                "EXECUTOR",
                null,
                null,
                null,
                null,
                scriptPath,
                "TRANSFORMED",
                FilterEnums.ExecutorType.PYTHON.name(),
                details,
                List.of()
        );
        return new FilterDescriptor.SimpleFilterDescriptor(List.of(), entry);
    }

    private String safeResolveScriptPath(CTX filterContext) {
        try {
            return resolveScriptPath(filterContext);
        } catch (Exception e) {
            return scriptPath;
        }
    }

    private String toJson(Object value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String hashScript(Path script) {
        try {
            return ArtifactHashing.hashBytes(Files.readAllBytes(script));
        } catch (Exception e) {
            return null;
        }
    }

    private String readScriptText(Path script) {
        try {
            return Files.readString(script, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value);
    }

}
