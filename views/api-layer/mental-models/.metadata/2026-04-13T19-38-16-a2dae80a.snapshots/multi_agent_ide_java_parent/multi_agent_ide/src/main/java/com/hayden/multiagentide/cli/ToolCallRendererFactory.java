package com.hayden.multiagentide.cli;

import com.hayden.acp_cdc_ai.acp.events.Events;

/**
 * Factory that selects the appropriate {@link ToolCallRenderer} based on the
 * tool name ({@link Events.ToolCallEvent#title()}).
 * <p>
 * Renderers are stateless singleton records — no Spring wiring needed.
 */
public final class ToolCallRendererFactory {

    private static final ToolCallRenderer.FileToolRenderer FILE =
            new ToolCallRenderer.FileToolRenderer();
    private static final ToolCallRenderer.ScriptToolRenderer SCRIPT =
            new ToolCallRenderer.ScriptToolRenderer();
    private static final ToolCallRenderer.MessageToolRenderer MESSAGE =
            new ToolCallRenderer.MessageToolRenderer();
    private static final ToolCallRenderer.GenericToolRenderer GENERIC =
            new ToolCallRenderer.GenericToolRenderer();

    private ToolCallRendererFactory() {}

    /**
     * Returns the appropriate renderer for the given ToolCallEvent.
     * Matches on {@code event.title()} which holds the {@code @Tool(name=...)} value.
     */
    public static ToolCallRenderer rendererFor(Events.ToolCallEvent event) {
        String title = event.title();
        if (title == null || title.isBlank()) {
            return GENERIC;
        }
        return switch (title.toLowerCase()) {
            case "read", "write", "edit" -> FILE;
            case "bash", "bashoutput", "killshell" -> SCRIPT;
            case "askuserquestiontool" -> MESSAGE;
            default -> GENERIC;
        };
    }
}
