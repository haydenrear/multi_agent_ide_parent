package com.hayden.utilitymodule.result.map;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.agg.AggregateParamError;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.agg.Responses;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ParameterizedResultCollectors {

    class AggregateParamResultCollector<T extends Responses.ParamAggregateResponse<R>, R, E extends AggregateParamError<InE>, InE extends SingleError>
            extends ResultCollectors<T, E, Result<R, InE>, R, InE> {

        protected final T aggregateResponse;
        protected final E aggregateError;

        public AggregateParamResultCollector(T t, E e) {
            this.aggregateResponse = t;
            this.aggregateError = e;
        }


        public static <T extends Responses.ParamAggregateResponse<R>, R, E extends AggregateParamError<InE>, InE extends SingleError>
        AggregateParamResultCollector<T, R, E, InE> fromValues(T t, E e) {
            return new AggregateParamResultCollector<>(t, e);
        }

        public static <T extends Responses.ParamAggregateResponse<R>, R, E extends AggregateParamError<InE>, InE extends SingleError>
        AggregateParamResultCollector<T, R, E, InE> toResult(Supplier<T> t, Supplier<E> e) {
            return new AggregateParamResultCollector<>(t.get(), e.get());
        }

        public static <
                T extends Responses.AggregateResponse,
                Er extends AggregateError
                >
        ResultCollectors<T, Er, Result<T, Er>, T, Er> from(
                T t, Er e
        ) {
            return AggregateResultCollectors.AggregateResultCollector.fromValues(t, e);
        }

        @Override
        public Supplier<Result<T, E>> supplier() {
            return () -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public BiConsumer<Result<T, E>, Result<R, InE>> accumulator() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::addItem);
                r2.e().ifPresent(aggregateError::addError);
            };
        }

        @Override
        public BinaryOperator<Result<T, E>> combiner() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::addItem);
                r2.e().map(E::errors).ifPresent(aggregateError::addError);
                return Result.from(aggregateResponse, aggregateError);
            };
        }

        @Override
        public Function<Result<T, E>, Result<T, E>> finisher() {
            return r1 -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }

    class AggregateMappingParamResultCollector<T extends Responses.ParamAggregateResponse<U>, U, E extends AggregateParamError<E1>, R1, E1 extends SingleError>
            extends ResultCollectors<T, E, Result<R1, E1>, R1, E1> {

        protected final T aggregateResponse;
        protected final E aggregateError;

        private final Function<Result<R1, E1>, Optional<U>> mapResult;
        private final Function<Result<R1, E1>, Optional<E1>> mapError;

        public AggregateMappingParamResultCollector(T t, E e,
                                                    Function<Result<R1, E1>, Optional<U>> result,
                                                    Function<Result<R1, E1>, Optional<E1>> error) {
            this.aggregateResponse = t;
            this.aggregateError = e;
            this.mapResult = result;
            this.mapError = error;
        }

        @Override
        public Supplier<Result<T, E>> supplier() {
            return () -> Result.from(this.aggregateResponse, this.aggregateError);
        }

        @Override
        public BiConsumer<Result<T, E>, Result<R1, E1>> accumulator() {
            return (r1, r2) -> {
                mapResult.apply(r2)
                        .ifPresent(a -> r1
                                .ifPresent(b -> b.addItem(a)));
                mapError.apply(r2)
                        .ifPresent(a -> r1.e()
                            .ifPresent(b -> b.addItem(a)));
            };
        }

        @Override
        public BinaryOperator<Result<T, E>> combiner() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::addItem);
                r2.e().map(E::errors).ifPresent(aggregateError::addError);
                return Result.from(aggregateResponse, aggregateError);
            };
        }

        @Override
        public Function<Result<T, E>, Result<T, E>> finisher() {
            return r1 -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of();
        }
    }

    class AggregateMappingParamAggResultCollector<T extends Responses.ParamAggregateResponse<U>, U, E extends AggregateParamError<E1>, R1, E1 extends SingleError>
            extends ResultCollectors<T, E, Result<R1, E1>, R1, E1> {

        protected final T aggregateResponse;
        protected final E aggregateError;

        private final Function<Result<R1, E1>, Optional<T>> mapResult;
        private final Function<Result<R1, E1>, Optional<E>> mapError;

        public AggregateMappingParamAggResultCollector(T t, E e,
                                                       Function<Result<R1, E1>, Optional<T>> result,
                                                       Function<Result<R1, E1>, Optional<E>> error) {
            this.aggregateResponse = t;
            this.aggregateError = e;
            this.mapResult = result;
            this.mapError = error;
        }

        @Override
        public Supplier<Result<T, E>> supplier() {
            return () -> Result.from(this.aggregateResponse, this.aggregateError);
        }

        @Override
        public BiConsumer<Result<T, E>, Result<R1, E1>> accumulator() {
            return (r1, r2) -> {
                mapResult.apply(r2)
                        .ifPresent(a -> r1
                                .ifPresent(b -> b.addItem(a)));
                mapError.apply(r2)
                        .ifPresent(a -> r1.e()
                                .ifPresent(b -> b.addItem(a)));
            };
        }

        @Override
        public BinaryOperator<Result<T, E>> combiner() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::addItem);
                r2.e().map(E::errors).ifPresent(aggregateError::addError);
                return Result.from(aggregateResponse, aggregateError);
            };
        }

        @Override
        public Function<Result<T, E>, Result<T, E>> finisher() {
            return r1 -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of();
        }
    }

}
