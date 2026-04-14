package com.hayden.utilitymodule.result.async;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.res_single.ISingleResultItem;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResultOptions;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hayden.utilitymodule.result.Result.logAsync;
import static com.hayden.utilitymodule.result.Result.logThreadStarvation;

@Slf4j
public record CompletableFutureResult<R>(CompletableFuture<R> r, AtomicBoolean finished) implements IAsyncResultItem<R>, ISingleResultItem<R> {

    public CompletableFutureResult(CompletableFuture<R> r) {
        this(r, new AtomicBoolean(false));
    }

    @Override
    public boolean isZeroOrOneAbstraction() {
        return true;
    }

    @Override
    public Optional<R> firstOptional() {
        logThreadStarvation();
        try {
            var v = r.get();
            return Optional.ofNullable(v);
        } catch (InterruptedException |
                 ExecutionException e) {
            throw new RuntimeException(e);
        }
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
        return Mono.fromFuture(this.r)
                .flux()
                .publishOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()))
                .toStream();
    }

    @Override
    public IAsyncResultItem<R> swap(Stream<R> toCache) {
        return new CompletableFutureResult<>(CompletableFuture.completedFuture(toCache.findAny().orElse(null)), new AtomicBoolean(true));
    }

    @Override
    public Flux<R> flux() {
        return Flux.fromStream(stream());
    }

    @Override
    public Mono<R> firstMono() {
        return Mono.fromFuture(this.r);
    }

    @Override
    public ISingleResultItem<R> single() {
        return this;
    }

    @Override
    public boolean didFinish() {
        return finished.get();
    }

    @Override
    public AsyncTyResultStreamWrapper<R> doAsync(Consumer<? super R> consumer) {
        var wrapper = new AsyncTyResultStreamWrapper<>(
                StreamResultOptions.builder().build(),
                Mono.fromFuture(r)
                        .doAfterTerminate(() -> this.finished.set(false)),
                this);


        wrapper.throwIfCachedOrCache(consumer);

        return wrapper;
    }

    @Override
    public R block() throws ExecutionException, InterruptedException {
        logThreadStarvation();
        var gotten = r.get();
        return gotten;
    }

    @Override
    public R block(Duration wait) throws ExecutionException, InterruptedException {
        try {
            return this.r.get(wait.getSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException t) {
            throw new ExecutionException(t);
        }
    }

    @Override
    public IResultItem<R> filter(Predicate<R> p) {
        return new MonoResult<>(firstMono())
                .filter(p);
    }

    @Override
    public R get() {
        return this.firstOptional().orElse(null);
    }

    @Override
    public <T> IResultItem<T> flatMap(Function<R, IResultItem<T>> toMap) {
        return new MonoResult<>(firstMono())
                .flatMap(toMap);
    }

    @Override
    public <T> IResultItem<T> map(Function<R, T> toMap) {
        return new CompletableFutureResult<>(r.thenApply(toMap));
    }

    @Override
    public Optional<R> optional() {
        return firstOptional();
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
        return new MonoResult<>(firstMono())
                .peek(consumer);
    }

    @Override
    public boolean isMany() {
        return false;
    }

    @Override
    public boolean isOne() {
        return true;
    }
}
