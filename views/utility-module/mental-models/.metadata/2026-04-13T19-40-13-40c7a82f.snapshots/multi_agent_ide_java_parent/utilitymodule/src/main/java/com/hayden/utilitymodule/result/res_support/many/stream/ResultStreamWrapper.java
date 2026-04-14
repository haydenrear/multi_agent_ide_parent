package com.hayden.utilitymodule.result.res_support.many.stream;

import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachableStream;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachingOperations;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * Stream wrapper for a result type, acting like a result not a stream.
 * @param <C>
 * @param <ST>
 */
@Slf4j
public abstract class ResultStreamWrapper<C extends CachableStream<ST, C>, ST> extends StreamWrapper<C, ST> {

    public ResultStreamWrapper(StreamResultOptions options, Stream<ST> underlying,
                               Class<? extends CachingOperations.StreamCacheOperation> provider,
                               StreamWrapper<C, ST> other,
                               C res) {
        super(options, underlying, provider, other, res);
    }

    public ResultStreamWrapper(StreamResultOptions options,
                               Stream<ST> underlying,
                               Class<? extends CachingOperations.StreamCacheOperation> provider,
                               C c) {
        super(options, underlying, provider, c);
    }

    @Override
    public long count() {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public @NotNull Optional<ST> max(Comparator<? super ST> comparator) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public @NotNull Optional<ST> min(Comparator<? super ST> comparator) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public <R, A> R collect(Collector<? super ST, A, R> collector) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super ST> accumulator, BiConsumer<R, R> combiner) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super ST, U> accumulator, BinaryOperator<U> combiner) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public @NotNull Optional<ST> reduce(BinaryOperator<ST> accumulator) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public ST reduce(ST identity, BinaryOperator<ST> accumulator) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public @NotNull <A> A[] toArray(IntFunction<A[]> generator) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public @NotNull Object[] toArray() {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public @NotNull Spliterator<ST> spliterator() {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public @NotNull Iterator<ST> iterator() {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public boolean noneMatch(Predicate<? super ST> predicate) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public boolean allMatch(Predicate<? super ST> predicate) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public boolean anyMatch(Predicate<? super ST> predicate) {
        throw new RuntimeException("Not implemented yet");
    }

}
