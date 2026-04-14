package com.hayden.utilitymodule.fn;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@UtilityClass
public class Reducer {

    public static <T, U, B extends BiFunction<U, T, T>> Optional<BiFunction<U, T, T>> chainReducers(List<B> reducers) {
        return reducers.stream()
                .map(b -> (BiFunction<U, T, T>) b)
                .reduce((reducedFn, nextFn) -> (reducedVal, nextVal) -> nextFn.apply(reducedVal, reducedFn.apply(reducedVal, nextVal)));
    }

    public static <T, U, V, B extends BiFunction<U, T, V>> Optional<BiFunction<U, T, V>> chainReducers(List<B> reducers, Function<V, T> ap) {
        return reducers.stream()
                .map(b -> (BiFunction<U, T, V>) b)
                .reduce((reducedFn, nextFn) -> (reducedVal, nextVal) -> nextFn.apply(reducedVal, ap.apply(reducedFn.apply(reducedVal, nextVal))));
    }
}
