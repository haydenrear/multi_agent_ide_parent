package com.hayden.utilitymodule.result.async;

import com.hayden.utilitymodule.result.res_many.IManyResultItem;
import com.hayden.utilitymodule.result.res_many.StreamResultItem;
import com.hayden.utilitymodule.result.res_single.ISingleResultItem;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResultOptions;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
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
public class FluxResult<R> implements IAsyncManyResultItem<R> {

    private final AtomicBoolean finished = new AtomicBoolean(false);

    private Flux<R> r;

    public FluxResult(Flux<R> r) {
        this.r = r;
    }

    public void swap(Flux<R> toSwap) {
        this.r = toSwap;
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
        return r.subscribeOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()))
                .toStream();
    }

    @Override
    public IAsyncResultItem<R> swap(Stream<R> toCache) {
        this.r = Flux.fromStream(toCache);
        return this;
    }

    @Override
    public Flux<R> flux() {
        return r;
    }

    @Override
    public Mono<R> firstMono() {
        return r.collectList()
                .flatMap(l -> !l.isEmpty()
                              ? Mono.justOrEmpty(l.getFirst())
                              : Mono.error(new RuntimeException("Called Mono on stream result with more than one value.")));
    }

    @Override
    public ISingleResultItem<R> single() {
        logThreadStarvation();
        var created = this.r.toStream().toList();

        if (created.size() > 1) {
            log.warn("Called single() on flux result with more than one value. Returning first.");
        }

        return !created.isEmpty()
               ? new ResultTyResult<>(Optional.ofNullable(created.getFirst()))
               : new ResultTyResult<>(Optional.empty());
    }

    @Override
    public boolean didFinish() {
        return finished.get();
    }

    @Override
    public IAsyncResultItem.AsyncTyResultStreamWrapper<R> doAsync(Consumer<? super R> consumer) {
        var wrapper = new AsyncTyResultStreamWrapper<>(
                StreamResultOptions.builder().build(),
                this.r.doOnComplete(() -> finished.set(true)),
                this);

        wrapper.throwIfCachedOrCache(consumer);

        return wrapper;

    }

    /**
     * TODO: await until it's finished and then wake it up
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Override
    public R block() throws ExecutionException, InterruptedException {
        return blockFirst();
    }

    @Override
    public R blockFirst() {
        logFluxSingle();
        logThreadStarvation();
        return r.blockFirst();
    }

    @Override
    public R blockLast() {
        logFluxSingle();
        logThreadStarvation();
        return r.blockLast();
    }

    @Override
    public List<R> blockAll() {
        logThreadStarvation();
        return r.collectList().block();
    }

    @Override
    public List<R> blockAll(Duration duration) {
        logThreadStarvation();
        return r.buffer(duration)
                .blockFirst();
    }

    @Override
    public R block(Duration wait) throws ExecutionException, InterruptedException {
        logFluxSingle();
        return this.r.blockFirst(wait);
    }

    private static void logFluxSingle() {
        log.warn("Calling block single on Flux. Could mean that multiple will not be returned.");
    }

    @Override
    public IResultItem<R> filter(Predicate<R> p) {
        return new FluxResult<>(r.filter(p));
    }

    @Override
    public R get() {
        return this.firstOptional().orElse(null);
    }

    @Override
    public <T> IManyResultItem<T> flatMap(Function<R, IResultItem<T>> toMap) {
        return new FluxResult<>(
                r.map(toMap)
                        .flatMap(IResultItem::flux)
        );
    }

    @Override
    public IManyResultItem<R> add(R r) {
        return new FluxResult<>(Flux.concat(this.r, Flux.just(r)));
    }

    @Override
    public IManyResultItem<R> concat(IManyResultItem<R> r) {
        return new FluxResult<>(Flux.concat(this.r, r.flux()));
    }


    @Override
    public <T> IResultItem<T> map(Function<R, T> toMap) {
        return new FluxResult<>(r.map(toMap));
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
        return new FluxResult<>(this.r.doOnNext(consumer));
    }

    @Override
    public boolean isMany() {
        return true;
    }

    @Override
    public boolean isOne() {
        return false;
    }

    @Override
    public boolean isPresent() {
        return false;
    }

    public Flux<R> r() {
        return r;
    }

    public AtomicBoolean finished() {
        return finished;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (FluxResult) obj;
        return Objects.equals(this.r, that.r) &&
               Objects.equals(this.finished, that.finished);
    }

    @Override
    public int hashCode() {
        return Objects.hash(r, finished);
    }

    @Override
    public String toString() {
        return "FluxResult[" +
               "r=" + r + ", " +
               "finished=" + finished + ']';
    }

    @Override
    public void swap(List<R> toSwap) {
        this.r = Flux.fromIterable(toSwap);
    }
}
