package com.hayden.utilitymodule;

@FunctionalInterface
public interface TriFunction<T, T1, T2, T3> {
    T3 apply(T t, T1 t1, T2 t2);
}
