package com.hayden.multiagentide.filter.model.interpreter;

import com.hayden.utilitymodule.result.error.SingleError;

/**
 * Error type for interpreter failures, used as the error side of Result.
 */
public record InterpreterError(String message, Throwable cause) implements SingleError {

    public InterpreterError(String message) {
        this(message, null);
    }

    @Override
    public String getMessage() {
        return message;
    }
}
