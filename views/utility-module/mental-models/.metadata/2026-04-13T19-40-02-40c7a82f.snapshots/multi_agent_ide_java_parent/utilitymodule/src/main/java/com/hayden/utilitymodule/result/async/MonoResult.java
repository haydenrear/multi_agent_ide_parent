package com.hayden.utilitymodule.result.async;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.res_single.ISingleResultItem;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResultOptions;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hayden.utilitymodule.result.Result.logAsync;
import static com.hayden.utilitymodule.result.Result.logThreadStarvation;

@Slf4j
@AllArgsConstructor
public class MonoResult<R> implements IAsyncResultItem<R>, ISingleResultItem<R> {

    Mono<R> r;
    AtomicBoolean finished = new AtomicBoolean(false);

    public MonoResult(Mono<R> r) {
        this.r = r;
    }

    @Override
    public boolean isZeroOrOneAbstraction() {
        return true;
    }

    @Override
    public Optional<R> firstOptional() {
        logThreadStarvation();
        var l = Lists.newArrayList(r.flux().toIterable());
        if (l.size() > 1) {
            log.error("Called optional on stream result with more than one value. Returning first.");
        }

        return !l.isEmpty()
               ? Optional.of(l.getFirst())
               : Optional.empty();
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
    public Stream<R> stream() {
        logThreadStarvation();
        return flux().toStream();
    }

    @Override
    public IAsyncResultItem<R> swap(Stream<R> toCache) {
        return new FluxResult<>(Flux.fromStream(toCache));
    }

    @Override
    public Flux<R> flux() {
        return r.flux();
    }

    @Override
    public Mono<R> firstMono() {
        return flux().collectList()
                .flatMap(l -> !l.isEmpty()
                              ? Mono.justOrEmpty(l.getFirst())
                              : Mono.error(new RuntimeException("Called Mono on stream result with more than one value.")));
    }

    @Override
    public boolean didFinish() {
        return finished.get();
    }

    @Override
    public IAsyncResultItem.AsyncTyResultStreamWrapper<R> doAsync(Consumer<? super R> consumer) {
        var wrapper = new AsyncTyResultStreamWrapper<>(
                StreamResultOptions.builder()
                        .build(),
                this.r.flux().doAfterTerminate(() -> this.finished.set(true)),
                this);

        wrapper.throwIfCachedOrCache(consumer);

        return wrapper;
    }

    @Override
    public R block() throws ExecutionException, InterruptedException {
        logThreadStarvation();
        return r.block();
    }

    @Override
    public R block(Duration wait) throws ExecutionException, InterruptedException {
        return this.r.block(wait);
    }

    @Override
    public IResultItem<R> filter(Predicate<R> p) {
        return new MonoResult<>(r.filter(p));
    }

    @Override
    public R get() {
        return this.firstOptional().orElse(null);
    }

    @Override
    public <T> IResultItem<T> flatMap(Function<R, IResultItem<T>> toMap) {
        return new MonoResult<>(r.map(toMap).flatMap(IResultItem::firstMono));
    }

    @Override
    public <T> IResultItem<T> map(Function<R, T> toMap) {
        return new MonoResult<>(r.map(toMap));
    }

    @Override
    public Optional<R> optional() {
        logThreadStarvation();
        return r.blockOptional();
    }

    @Override
    public R orElse(R r) {
        return this.firstOptional().orElse(r);
    }

    @Override
    public R orElseGet(Supplier<R> r) {
        return this.firstOptional().orElseGet(r);
    }

    @Override
    public void ifPresent(Consumer<? super R> consumer) {
        doAsync(consumer);
    }

    @Override
    public IResultItem<R> peek(Consumer<? super R> consumer) {
        return new MonoResult<>(this.r.doOnNext(consumer));
    }

    @Override
    public boolean isMany() {
        return false;
    }

    @Override
    public boolean isOne() {
        return true;
    }

    public Mono<R> r() {
        return r;
    }

    public AtomicBoolean finished() {
        return finished;
    }

}
