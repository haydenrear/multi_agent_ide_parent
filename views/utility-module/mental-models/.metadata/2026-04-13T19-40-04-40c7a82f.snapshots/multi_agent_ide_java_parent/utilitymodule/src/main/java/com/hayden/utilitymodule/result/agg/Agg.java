package com.hayden.utilitymodule.result.agg;

import java.util.List;

public interface Agg {

    void addAgg(Agg t);

    interface ParameterizedAgg<T> extends Agg {

        void addItem(T t);

        List<T> all();

        default void addItem(ParameterizedAgg<T> t) {
            t.all().forEach(this::addItem);
        }
    }

}
