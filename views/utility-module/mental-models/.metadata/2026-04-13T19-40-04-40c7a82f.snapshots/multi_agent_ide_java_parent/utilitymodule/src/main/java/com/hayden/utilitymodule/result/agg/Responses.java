package com.hayden.utilitymodule.result.agg;

public interface Responses {

    interface AggregateResponse extends Agg {
    }

    interface ParamAggregateResponse<T> extends Agg.ParameterizedAgg<T> {
    }

}
