package com.hayden.utilitymodule.concurrent;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class OnceCell<T> {

    AtomicReference<T> t = new AtomicReference<>();

    private final Supplier<T> supplier;

    public T get() {
        t.compareAndSet(null, supplier.get());
        return t.get();
    }

}
