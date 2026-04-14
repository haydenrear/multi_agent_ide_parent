package com.hayden.multiagentide.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.cli.CliEventFormatter.CliEventArgs;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface for type-specific rendering of ToolCallEvents.
 * Each permit knows how to extract salient fields from rawInput/rawOutput
 * and format them for both summary (list) and detail (modal) views.
 */
public sealed interface ToolCallRenderer
        permits ToolCallRenderer.FileToolRenderer,
                ToolCallRenderer.ScriptToolRenderer,
                ToolCallRenderer.MessageToolRenderer,
                ToolCallRenderer.GenericToolRenderer {

    ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Produce a single-line summary string for the event list view.
     * Highlights the most important field (file path, command, etc.).
     */
    String formatSummary(CliEventArgs args, Events.ToolCallEvent event);

    /**
     * Produce a multi-line detailed string for the detail modal view.
     * Presents structured, readable output appropriate for the tool type.
     */
    String formatDetail(CliEventArgs args, Events.ToolCallEvent event);

    // ── File tools: read, write, edit ──────────────────────────────────

    record FileToolRenderer() implements ToolCallRenderer {

        @Override
        public String formatSummary(CliEventArgs args, Events.ToolCallEvent event) {
            String filePath = extractField(event.rawInput(), "file_path", "filePath");
            String path = filePath != null ? filePath : "unknown";
            return "tool=" + event.title()
                    + " file=" + summarize(args, path)
                    + " phase=" + nullSafe(event.phase())
                    + " status=" + nullSafe(event.status());
        }

        @Override
        public String formatDetail(CliEventArgs args, Events.ToolCallEvent event) {
            StringBuilder sb = new StringBuilder();
            sb.append("Tool: ").append(event.title()).append("\n");
            sb.append("Phase: ").append(nullSafe(event.phase()))
              .append(" | Status: ").append(nullSafe(event.status())).append("\n");

            String filePath = extractField(event.rawInput(), "file_path", "filePath");
            sb.append("File: ").append(filePath != null ? filePath : "unknown").append("\n");

            appendLocations(sb, event);

            // For edit: show old_string/new_string as diff
            String oldStr = extractField(event.rawInput(), "old_string", "oldText", "old_text");
            String newStr = extractField(event.rawInput(), "new_string", "newText", "new_text");
            if (oldStr != null || newStr != null) {
                sb.append("\n--- Diff ---\n");
                if (oldStr != null) {
                    sb.append("- ").append(oldStr.replace("\n", "\n- ")).append("\n");
                }
                if (newStr != null) {
                    sb.append("+ ").append(newStr.replace("\n", "\n+ ")).append("\n");
                }
            }

            // For write: show content
            String content = extractField(event.rawInput(), "content");
            if (content != null && oldStr == null) {
                sb.append("\n--- Content ---\n").append(content).append("\n");
            }

            appendRawOutput(sb, event);
            return sb.toString();
        }
    }

    // ── Script tools: bash, BashOutput, killShell ──────────────────────

    record ScriptToolRenderer() implements ToolCallRenderer {

        @Override
        public String formatSummary(CliEventArgs args, Events.ToolCallEvent event) {
            String command = extractField(event.rawInput(), "command");
            String bashId = extractField(event.rawInput(), "bash_id", "shell_id", "task_id");
            String highlight;
            if (command != null) {
                highlight = "cmd=" + summarize(args, command);
            } else if (bashId != null) {
                highlight = "shell=" + bashId;
            } else {
                highlight = "input=" + summarize(args, stringify(event.rawInput()));
            }
            return "tool=" + event.title()
                    + " " + highlight
                    + " phase=" + nullSafe(event.phase())
                    + " status=" + nullSafe(event.status());
        }

        @Override
        public String formatDetail(CliEventArgs args, Events.ToolCallEvent event) {
            StringBuilder sb = new StringBuilder();
            sb.append("Tool: ").append(event.title()).append("\n");
            sb.append("Phase: ").append(nullSafe(event.phase()))
              .append(" | Status: ").append(nullSafe(event.status())).append("\n");

            String command = extractField(event.rawInput(), "command");
            if (command != null) {
                sb.append("Command: ").append(command).append("\n");
            }
            String bashId = extractField(event.rawInput(), "bash_id", "shell_id", "task_id");
            if (bashId != null) {
                sb.append("Shell ID: ").append(bashId).append("\n");
            }
            String description = extractField(event.rawInput(), "description");
            if (description != null) {
                sb.append("Description: ").append(description).append("\n");
            }
            String timeout = extractField(event.rawInput(), "timeout");
            if (timeout != null) {
                sb.append("Timeout: ").append(timeout).append("\n");
            }

            appendRawOutput(sb, event);
            return sb.toString();
        }
    }

    // ── Message tools: AskUserQuestionTool ─────────────────────────────

    record MessageToolRenderer() implements ToolCallRenderer {

        @Override
        public String formatSummary(CliEventArgs args, Events.ToolCallEvent event) {
            return "tool=" + event.title()
                    + " phase=" + nullSafe(event.phase())
                    + " message=" + summarize(args, stringify(event.rawInput()));
        }

        @Override
        public String formatDetail(CliEventArgs args, Events.ToolCallEvent event) {
            StringBuilder sb = new StringBuilder();
            sb.append("Tool: ").append(event.title()).append("\n");
            sb.append("Phase: ").append(nullSafe(event.phase()))
              .append(" | Status: ").append(nullSafe(event.status())).append("\n\n");
            sb.append(stringify(event.rawInput()));
            if (event.rawOutput() != null) {
                sb.append("\n\n--- Response ---\n");
                sb.append(stringify(event.rawOutput()));
            }
            return sb.toString();
        }
    }

    // ── Generic fallback: JSON pretty-print ────────────────────────────

    record GenericToolRenderer() implements ToolCallRenderer {

        @Override
        public String formatSummary(CliEventArgs args, Events.ToolCallEvent event) {
            return "tool=" + summarize(args, event.title())
                    + " kind=" + summarize(args, event.kind())
                    + " status=" + summarize(args, event.status())
                    + " phase=" + summarize(args, event.phase())
                    + " input=" + summarize(args, event.rawInput())
                    + " output=" + summarize(args, event.rawOutput());
        }

        @Override
        public String formatDetail(CliEventArgs args, Events.ToolCallEvent event) {
            StringBuilder sb = new StringBuilder();
            sb.append("Tool: ").append(nullSafe(event.title())).append("\n");
            sb.append("Phase: ").append(nullSafe(event.phase()))
              .append(" | Status: ").append(nullSafe(event.status()))
              .append(" | Kind: ").append(nullSafe(event.kind())).append("\n");

            sb.append("\n--- Input ---\n");
            sb.append(prettyPrintJson(event.rawInput()));
            sb.append("\n\n--- Output ---\n");
            sb.append(prettyPrintJson(event.rawOutput()));
            return sb.toString();
        }
    }

    // ── Shared helpers ─────────────────────────────────────────────────

    /**
     * Extract a named field from rawInput, which may be a JSON string or a Map.
     * Tries each candidate key in order, returns null if none found.
     */
    @SuppressWarnings("unchecked")
    private static String extractField(Object raw, String... keys) {
        if (raw == null) return null;

        // Try as Map first
        if (raw instanceof Map<?, ?> map) {
            for (String key : keys) {
                Object val = map.get(key);
                if (val != null) return String.valueOf(val);
            }
            return null;
        }

        // Try parsing as JSON string
        String text = String.valueOf(raw);
        if (text.isBlank() || !text.startsWith("{")) return null;
        try {
            JsonNode node = JSON.readTree(text);
            for (String key : keys) {
                JsonNode val = node.get(key);
                if (val != null && !val.isNull()) {
                    return val.isTextual() ? val.asText() : val.toString();
                }
            }
        } catch (JsonProcessingException ignored) {
            // Not valid JSON — return null
        }
        return null;
    }

    private static String stringify(Object value) {
        if (value == null) return "none";
        return String.valueOf(value);
    }

    private static String nullSafe(Object value) {
        return value != null ? String.valueOf(value) : "none";
    }

    private static String summarize(CliEventArgs args, Object value) {
        if (value == null) return "none";
        String text = String.valueOf(value);
        int max = Math.max(0, args.maxFieldLength());
        if (text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }

    private static String prettyPrintJson(Object raw) {
        if (raw == null) return "none";
        String text = String.valueOf(raw);
        if (text.isBlank()) return "none";
        try {
            Object parsed = JSON.readValue(text, Object.class);
            return JSON.writeValueAsString(parsed);
        } catch (JsonProcessingException e) {
            return text;
        }
    }

    private static void appendLocations(StringBuilder sb, Events.ToolCallEvent event) {
        List<Map<String, Object>> locations = event.locations();
        if (locations == null || locations.isEmpty()) return;
        for (Map<String, Object> loc : locations) {
            Object path = loc.get("path");
            Object line = loc.get("line");
            if (path != null) {
                sb.append("Location: ").append(path);
                if (line != null) sb.append(":").append(line);
                sb.append("\n");
            }
        }
    }

    private static void appendRawOutput(StringBuilder sb, Events.ToolCallEvent event) {
        if (event.rawOutput() == null) return;
        String output = stringify(event.rawOutput());
        if (output.isBlank() || "none".equals(output)) return;
        sb.append("\n--- Output ---\n").append(output).append("\n");
    }
}
