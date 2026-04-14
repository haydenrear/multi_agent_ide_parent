package com.hayden.utilitymodule.result.agg;

import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.string.StringUtil;
import io.micrometer.common.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface AggregateParamError<T extends SingleError>
        extends Agg.ParameterizedAgg<T>, SingleError {

    Set<T> errors();

    default boolean isError() {
        return !errors().isEmpty() && !StringUtils.isBlank(this.getMessage());
    }

    default Set<String> getMessages() {
        return errors().stream().map(SingleError::getMessage)
                .collect(Collectors.toSet());
    }

    default String getMessage() {
        return String.join(", ", getMessages());
    }

    default void addItem(T toAdd) {
        this.addError(toAdd);
    }

    default List<T> all() {
        return errors().stream().toList();
    }

    default void addItem(Agg agg) {
        if (agg instanceof AggregateParamError e) {
            this.errors().addAll(e.errors());
            this.getMessages().addAll(e.getMessages());
        } else {
            throw new UnsupportedOperationException("Cannot add Aggregate type %s to %s."
                    .formatted(agg.getClass().getName(), this.getClass().getName()));
        }
    }

    default void addError(T error) {
        errors().add(error);
    }

    default void addError(Set<T> error) {
        errors().addAll(error);
    }

    default String prettyPrint() {
        return """
                   Aggregate Error:
                   %s
                """.formatted(errors().stream().map(SingleError::toString).collect(Collectors.joining("\n\r")));
    }

}
