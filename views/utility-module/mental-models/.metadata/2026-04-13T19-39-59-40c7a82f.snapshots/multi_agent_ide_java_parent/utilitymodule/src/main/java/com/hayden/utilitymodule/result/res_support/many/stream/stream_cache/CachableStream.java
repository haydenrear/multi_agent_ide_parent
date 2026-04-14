package com.hayden.utilitymodule.result.res_support.many.stream.stream_cache;

import java.util.stream.Stream;

/**
 * Implodable side effects
 * @param <R>
 * @param <SELF>
 */
public interface CachableStream<R, SELF extends CachableStream<R, SELF>> {

    Stream<R> stream();

    /**
     * free will original sin
     * @param toCache
     * @return
     */
    SELF swap(Stream<R> toCache);

}
