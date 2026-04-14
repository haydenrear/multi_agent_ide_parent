package com.hayden.utilitymodule.result.agg;

import com.google.common.collect.Sets;
import com.hayden.utilitymodule.result.error.SingleError;

import java.util.Set;

public interface AggregateError<T extends SingleError> extends SingleError, Agg, AggregateParamError<T> {

    interface StdAggregateError extends AggregateError<SingleError> {

        record SimpleStdAggregateError(Set<SingleError> errors) implements StdAggregateError {

            public SimpleStdAggregateError(SingleError errors) {
                this(Sets.newHashSet(errors));
            }

        }

    }

    default void addItem(T o) {
        this.addError(o);
    }

    default void addItem(ParameterizedAgg t) {
        this.addAgg(t);
    }


    @Override
    default void addAgg(Agg t) {
        AggregateParamError.super.addItem(t);
    }

    record StandardAggregateError(Set<SingleError> messages) implements StdAggregateError {

        public StandardAggregateError(SingleError error) {
            this(Sets.newHashSet(error));
        }

        public StandardAggregateError(String message) {
            this(Sets.newHashSet(SingleError.fromMessage(message)));
        }

        public StandardAggregateError(Throwable message) {
            this(Sets.newHashSet(SingleError.fromE(message)));
        }

        @Override
        public Set<SingleError> errors() {
            return messages;
        }

    }
}
