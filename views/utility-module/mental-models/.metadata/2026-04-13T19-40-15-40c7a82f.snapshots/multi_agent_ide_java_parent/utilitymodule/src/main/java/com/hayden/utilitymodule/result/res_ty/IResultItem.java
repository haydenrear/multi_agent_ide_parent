package com.hayden.utilitymodule.result.res_ty;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.res_many.IManyResultItem;
import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_many.StreamResultItem;
import com.hayden.utilitymodule.result.res_single.ISingleResultItem;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamWrapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface IResultItem<R> extends Result.Monadic<R> {

    static <V> IResultItem<V> toRes(V v) {
        if (v instanceof AutoCloseable a) {
            return (IResultItem<V>) new ClosableResult<>(Optional.of(a));
        }

        return new ResultTyResult<>(Optional.ofNullable(v));
    }

    static <V> IResultItem<V> toRes(Stream<V> v) {
        return new StreamResultItem<>(v);
    }

    static <R> IResultItem<R> empty() {
        return new ResultTyResult<>(Optional.empty());
    }

    default boolean isClosable() {
        return false;
    }

    <V> IResultItem<V> from(V r);

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    <V> IResultItem<V> from(Optional<V> r);

    Stream<R> stream();

    Flux<R> flux();

    Mono<R> firstMono();

    ISingleResultItem<R> single();

    IResultItem<R> filter(Predicate<R> p);

    R get();

    <V> IResultItem<V> flatMap(Function<R, IResultItem<V>> toMap);

    <V> IResultItem<V> map(Function<R, V> toMap);

    void ifPresent(Consumer<? super R> consumer);

    IResultItem<R> peek(Consumer<? super R> consumer);

    boolean isMany();

    boolean isOne();

    boolean isPresent();

    default IManyResultItem<R> many() {
        if (this instanceof IManyResultItem<R> t) {
            return t;
        }

        return new ListResultItem<>(Lists.newArrayList(this.get()));
    }


    default boolean isAsyncSub() {
        return false;
    }

    default boolean isZeroOrOneAbstraction() {
        return false;
    }

    default boolean isStream() {
        return false;
    }

    default void forEach(Consumer<? super R> consumer) {
        this.stream().forEach(consumer);
    }


    default boolean isEmpty() {
        return !isPresent();
    }

    default List<R> toList() {
        return this.stream().toList();
    }

    default List<R> toMutableList() {
        return this.stream().collect(Collectors.toCollection(ArrayList::new));
    }
}
