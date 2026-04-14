package com.hayden.utilitymodule.result;

public interface MutableResult<T, E> extends OneResult<T, E>{

    void set(T value);

}
