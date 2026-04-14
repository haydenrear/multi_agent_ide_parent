package com.hayden.acp_cdc_ai.acp.filter;

/**
 * Shared enums for the layered data policy filtering subsystem.
 */
public interface FilterEnums {

    enum PolicyStatus {
        ACTIVE,
        INACTIVE
    }

    enum ExecutorType {
        BINARY,
        JAVA_FUNCTION,
        PYTHON,
        AI,
        AI_PROPAGATOR,
        AI_TRANSFORMER
    }

    enum FilterKind {
        JSON_PATH,
        MARKDOWN_PATH,
        REGEX_PATH,
        AI_PATH;

        public String type() {
            return switch (this) {
                case REGEX_PATH, MARKDOWN_PATH, JSON_PATH:
                    yield "PATH";
                case AI_PATH:
                    yield "AI";
            };
        }
    }

    enum InstructionLanguage {
        REGEX,
        MARKDOWN_PATH,
        JSON_PATH
    }

    enum InstructionOp {
        REPLACE,
        SET,
        REMOVE,
        REPLACE_IF_MATCH,
        REMOVE_IF_MATCH
    }

    enum FilterAction {
        DROPPED,
        TRANSFORMED,
        PASSTHROUGH,
        ERROR
    }

    enum LayerType {
        WORKFLOW_AGENT,
        WORKFLOW_AGENT_ACTION,
        CONTROLLER,
        CONTROLLER_UI_EVENT_POLL
    }

    enum InterpreterType {
        REGEX,
        MARKDOWN_PATH,
        JSON_PATH
    }

    enum PathType {
        REGEX,
        MARKDOWN_PATH,
        JSON_PATH
    }

    // Matcher enums for PolicyLayerBinding (T005)

    enum MatcherKey {
        NAME,
        TEXT
    }

    enum MatcherType {
        REGEX,
        EQUALS,
        CONTAINS
    }

    enum MatchOn {
        PROMPT_CONTRIBUTOR,
        GRAPH_EVENT,
        ACTION_REQUEST,
        ACTION_RESPONSE,
        CONTROLLER_ENDPOINT_RESPONSE
    }

}
