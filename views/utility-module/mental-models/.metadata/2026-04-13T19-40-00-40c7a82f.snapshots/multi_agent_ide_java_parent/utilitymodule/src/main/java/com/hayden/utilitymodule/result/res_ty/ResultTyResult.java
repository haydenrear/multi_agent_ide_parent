package com.hayden.utilitymodule.result.res_ty;

import com.hayden.utilitymodule.result.res_single.ISingleResultItem;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
public record ResultTyResult<R>(Optional<R> r) implements ISingleResultItem<R> {

    public <T> IResultItem<T> flatMap(Function<R, IResultItem<T>> toMap) {
        if (r().isEmpty())
            return from(Optional.empty());
        return toMap.apply(r().get());
    }

    public <T> IResultItem<T> map(Function<R, T> toMap) {
        if (r().isEmpty())
            return from(Optional.empty());
        return from(r().map(toMap));
    }

    @Override
    public boolean isZeroOrOneAbstraction() {
        return true;
    }

    @Override
    public Optional<R> optional() {
        return r;
    }

    @Override
    public R orElse(R r) {
        return this.r.orElse(r);
    }

    @Override
    public R orElseGet(Supplier<R> r) {
        return this.r.orElseGet(r);
    }

    @Override
    public void ifPresent(Consumer<? super R> consumer) {
        this.r.ifPresent(consumer);
    }

    @Override
    public IResultItem<R> peek(Consumer<? super R> consumer) {
        this.ifPresent(consumer);
        return this;
    }

    @Override
    public boolean isMany() {
        return false;
    }

    @Override
    public boolean isOne() {
        return true;
    }

    @Override
    public Stream<R> stream() {
        return this.r.stream();
    }

    @Override
    public Flux<R> flux() {
        return Flux.fromStream(this.r.stream());
    }

    @Override
    public Mono<R> firstMono() {
        return Mono.justOrEmpty(this.r);
    }

    @Override
    public ISingleResultItem<R> single() {
        return this;
    }

    @Override
    public Optional<R> firstOptional() {
        return r;
    }

    @Override
    public <T> IResultItem<T> from(T r) {
        return new ResultTyResult<>(Optional.ofNullable(r));
    }

    @Override
    public <T> IResultItem<T> from(Optional<T> r) {
        return new ResultTyResult<>(r);
    }

    @Override
    public IResultItem<R> filter(Predicate<R> p) {
        return from(this.r.filter(p));
    }

    @Override
    public R get() {
        return this.r.orElse(null);
    }
}
