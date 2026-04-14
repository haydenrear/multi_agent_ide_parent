package com.hayden.utilitymodule.result.map;

import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.agg.AggregateParamError;
import com.hayden.utilitymodule.result.agg.Responses;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;

@RequiredArgsConstructor
public abstract class ResultCollectors<
        OUT_R,
        OUT_E,
        IN_RES extends Result<IN_R, IN_E>,
        IN_R,
        IN_E extends SingleError
        >
        implements Collector<IN_RES, Result<OUT_R, OUT_E>, Result<OUT_R, OUT_E>> {

    public interface ResultMapper<ResultTypeT, ErrorTypeT extends SingleError, ToCreateAggT>
            extends Function<Result<ResultTypeT, ErrorTypeT>, Optional<ToCreateAggT>> {
    }

    public interface ErrorMapper<ResultTypeT, ErrorTypeT extends SingleError, ToCreateAggT extends SingleError>
            extends Function<Result<ResultTypeT, ErrorTypeT>, Optional<ToCreateAggT>> {
    }

    public interface AggResultMapper<ResultTypeT, ErrorTypeT extends SingleError, ToCreateAggT extends Responses.AggregateResponse>
            extends Function<Result<ResultTypeT, ErrorTypeT>, Optional<ToCreateAggT>> {
    }

    public interface AggErrorMapper<ResultTypeT, ErrorTypeT extends SingleError, ToCreateAggT extends AggregateError>
            extends Function<Result<ResultTypeT, ErrorTypeT>, Optional<ToCreateAggT>> {
    }

    public static <
            T extends Responses.ParamAggregateResponse<R>,
            R,
            E extends AggregateParamError<E1>,
            R1, E1 extends SingleError
            >
    ResultCollectors<T, E, Result<R1, E1>, R1, E1> from(
            T t,
            E e,
            ResultMapper<R1, E1, R> result,
            ErrorMapper<R1, E1, E1> error
    ) {
        return new ParameterizedResultCollectors.AggregateMappingParamResultCollector<>(t, e, result, error);
    }

    public static <
            T extends Responses.ParamAggregateResponse<R>,
            R,
            E extends AggregateParamError<E1>,
            R1,
            E1 extends SingleError
            >
    ResultCollectors<T, E, Result<R1, E1>, R1, E1> aggParamFrom(
            T t,
            E e,
            ResultMapper<R1, E1, T> result,
            ErrorMapper<R1, E1, E> error
    ) {
        return new ParameterizedResultCollectors.AggregateMappingParamAggResultCollector<>(t, e, result, error);
    }

    public static <
            T extends Responses.AggregateResponse,
            E extends AggregateError,
            R1, E1 extends SingleError
            >
    ResultCollectors<T, E, Result<R1, E1>, R1, E1> from(
            T t,
            E e,
            AggResultMapper<R1, E1, T> result,
            AggErrorMapper<R1, E1, E> error
    ) {
        return new AggregateResultCollectors.AggregateMappingResultCollector<>(t, e, result, error);
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

    public static <
            T extends Responses.ParamAggregateResponse<R>,
            R,
            Er extends AggregateParamError<InE>,
            InE extends SingleError
            >
    ResultCollectors<T, Er, Result<R, InE>, R, InE> from(
            T t, Er e
    ) {
        return ParameterizedResultCollectors.AggregateParamResultCollector.fromValues(t, e);
    }
}
