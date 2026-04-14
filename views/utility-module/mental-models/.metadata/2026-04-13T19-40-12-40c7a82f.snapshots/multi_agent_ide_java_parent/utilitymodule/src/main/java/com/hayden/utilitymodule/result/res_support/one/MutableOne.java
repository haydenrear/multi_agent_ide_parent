package com.hayden.utilitymodule.result.res_support.one;

import com.hayden.utilitymodule.result.MutableResult;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.ok.MutableOk;

import java.util.List;

public record MutableOne<T, E>(MutableOk<T> r, Err<E> e)
        implements MutableResult<T, E> {

    @Override
    public void set(T value) {
        this.r.set(value);
    }

    @Override
    public List<T> getAll() {
        return r.stream().toList();
    }
}