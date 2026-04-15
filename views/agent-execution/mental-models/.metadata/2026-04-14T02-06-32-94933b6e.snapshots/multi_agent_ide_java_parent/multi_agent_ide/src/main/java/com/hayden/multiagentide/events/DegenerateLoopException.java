package com.hayden.multiagentide.events;

public class DegenerateLoopException extends RuntimeException {
    private final String actionName;
    private final Class<?> inputType;
    private final int repetitionCount;

    public DegenerateLoopException(String message, String actionName, Class<?> inputType, int repetitionCount) {
        super(message);
        this.actionName = actionName;
        this.inputType = inputType;
        this.repetitionCount = repetitionCount;
    }

    public DegenerateLoopException(String actionName, Class<?> inputType, int repetitionCount) {
        this("Degenerate loop detected", actionName, inputType, repetitionCount);
    }

    public String getActionName() {
        return actionName;
    }

    public Class<?> getInputType() {
        return inputType;
    }

    public int getRepetitionCount() {
        return repetitionCount;
    }
}
