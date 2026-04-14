package com.hayden.utilitymodule.result.error;

import io.micrometer.common.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public interface SingleError {

    static String parseStackTraceToString(StackTraceElement[] e) {
        return Arrays.stream(e)
                .map(StackTraceElement::toString).collect(Collectors.joining(System.lineSeparator()));
    }

    static String parseStackTraceToString(Throwable e) {
        return parseStackTraceToString(e.getStackTrace());
    }

    default boolean isError() {
        return !StringUtils.isBlank(this.getMessage());
    }

    static SingleError fromMessage(String error) {
        return new StandardError(error);
    }

    static SingleError fromE(Throwable error) {
        return new StandardError("%s\n%s".formatted(error.getMessage(), parseStackTraceToString(error.getStackTrace())));
    }

    static SingleError fromE(Throwable error, String cause) {
        return new StandardError(cause, error);
    }

    String getMessage();

    record StandardError(String error, Throwable throwable) implements SingleError {
        public StandardError(Throwable throwable) {
            this(throwable.getMessage(), throwable);
        }
        public StandardError(String throwable) {
            this(throwable, null);
        }

        @Override
        public String getMessage() {
            return error;
        }
    }

}
