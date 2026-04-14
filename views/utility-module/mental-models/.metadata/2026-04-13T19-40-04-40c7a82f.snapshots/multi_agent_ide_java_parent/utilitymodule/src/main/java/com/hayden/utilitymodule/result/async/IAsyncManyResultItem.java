package com.hayden.utilitymodule.result.async;

import com.hayden.utilitymodule.result.res_many.IManyResultItem;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

public interface IAsyncManyResultItem<T> extends IAsyncResultItem<T>, IManyResultItem<T> {


    default List<T> blockAll() throws ExecutionException, InterruptedException {
        return List.of(block());
    }

    default List<T> blockAll(Duration duration) throws ExecutionException, InterruptedException {
        return blockAll();
    }

    default T blockFirst() throws ExecutionException, InterruptedException {
        return block();
    }

    default T blockLast() throws ExecutionException, InterruptedException {
        return block();
    }

    default boolean has(Predicate<T> e) {
        throw new RuntimeException("Async many result needs to add BlockingMany.");
    }
}
