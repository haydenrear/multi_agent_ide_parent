package com.hayden.utilitymodule.result.res_support.one;

import com.hayden.utilitymodule.result.ClosableResult;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.ok.ClosableOk;

public record ClosableOne<T extends AutoCloseable, E>(ClosableOk<T> r, Err<E> e)
        implements ClosableResult<T, E> {

    public ClosableOne(ClosableOk<T> r, Err<E> e) {
        this.r = r;
        this.e = e;
        onInitialize();
    }

    public void close() {
        this.r().ifPresent(t -> {
                    try {
                        t.close();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }
}