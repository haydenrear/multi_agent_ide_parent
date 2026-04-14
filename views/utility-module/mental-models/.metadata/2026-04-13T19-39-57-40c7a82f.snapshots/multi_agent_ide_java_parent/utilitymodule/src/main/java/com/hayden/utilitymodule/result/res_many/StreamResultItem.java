package com.hayden.utilitymodule.result.res_many;

import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.res_single.ISingleResultItem;
import com.hayden.utilitymodule.result.res_support.many.stream.ResultStreamWrapper;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResultOptions;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamWrapper;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachableStream;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachingOperations;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
public class StreamResultItem<R> implements IStreamResultItem<R>, CachableStream<R, StreamResultItem<R>> {

    final ResultTyStreamWrapper<R> r;

    protected final static class ResultTyStreamWrapper<R> extends ResultStreamWrapper<StreamResultItem<R>, R> {

        public ResultTyStreamWrapper(StreamResultOptions options, Mono<R> underlying, StreamResultItem<R> res) {
            this(options, underlying.flux(), res);
        }

        public ResultTyStreamWrapper(StreamResultOptions options, Flux<R> underlying, StreamResultItem<R> res) {
            this(options, underlying.toStream(), res);
        }

        public ResultTyStreamWrapper(StreamResultOptions options, Stream<R> underlying, StreamResultItem<R> res) {
            super(options, underlying, CachingOperations.ResultTyStreamWrapperOperation.class, res);
        }

        public R first() {
            return Result.fromOpt(
                            TypeReferenceDelegate.<CachingOperations.RetrieveFirstTy<R>>create(CachingOperations.RetrieveFirstTy.class),
                            new SingleError.StandardError("Failed to parse type reference delegate for %s".formatted(CachingOperations.RetrieveFirstTy.class.getName()))
                    )
                    .flatMapResult(this::get)
                    .peekError(se -> log.error("Found err: {}", se))
                    .r()
                    .orElse(null);
        }

        @Override
        public @NotNull Optional<R> findAny() {
            cacheResultsIfNotCached();
            return Optional.ofNullable(first());
        }

        @Override
        public @NotNull Optional<R> findFirst() {
            return findAny();
        }

    }

    public StreamResultItem(Stream<R> r) {
        this.r = new ResultTyStreamWrapper<>(StreamResultOptions.builder().build(), r, this);
    }

    public StreamResultItem(Stream<R> r, StreamResultOptions options) {
        this.r = new ResultTyStreamWrapper<>(options, r, this);
    }

    public static <R> StreamResultItem<R> of(Stream<IResultItem<R>> stream) {
        return new StreamResultItem<>(stream.flatMap(IResultItem::stream));
    }

    @Override
    public synchronized void swap(List<R> toSwap) {
        this.swap(toSwap.stream());
    }

    @Override
    public StreamResultItem<R> swap(Stream<R> toCache) {
        var cached = toCache.toList();
        this.r.swap(cached);

        return new StreamResultItem<>(cached.stream());
    }

    @Override
    public <T> IResultItem<T> from(T r) {
        return new StreamResultItem<>(Stream.ofNullable(r));
    }

    @Override
    public <T> IResultItem<T> from(Optional<T> r) {
        return new StreamResultItem<>(r.stream());
    }

    @Override
    public Stream<R> stream() {
        return this.r.filter(Objects::nonNull);
    }

    @Override
    public Flux<R> flux() {
        return Flux.fromStream(r);
    }

    @Override
    public Mono<R> firstMono() {
        List<R> streamList = this.r.toList();
        if (streamList.isEmpty())
            return Mono.error(new RuntimeException("Called get Mono on list with more than 1."));

        if (streamList.size() != 1)
            log.warn("Called first mono on StreamResult and discarded {}.", streamList.size() - 1);

        return Mono.justOrEmpty(streamList.getFirst());
    }

    @Override
    public ISingleResultItem<R> single() {
        var last = this.r.cacheResultsIfNotCachedWithList(c -> {});
        if (last.size() > 1) {
            log.warn("Called one() on StreamResult with size greater than 1. Discarding all other.");
        }


        return !last.isEmpty()
               ? Optional.ofNullable(last.getFirst())
                       .map(res -> new ResultTyResult<>(Optional.of(res)))
                       .orElse(null)
               : new ResultTyResult<>(Optional.empty());
    }

    @Override
    public IResultItem<R> filter(Predicate<R> p) {
        return new StreamResultItem<>(r.filter(p));
    }

    @Override
    public R get() {
        return firstOptional().orElse(null);
    }

    @Override
    public <T> StreamResultItem<T> flatMap(Function<R, IResultItem<T>> toMap) {
        return new StreamResultItem<>(
                r.map(toMap)
                        .flatMap(IResultItem::stream)
        );
    }

    @Override
    public IManyResultItem<R> add(R r) {
        return new StreamResultItem<>(Stream.concat(this.r, Stream.of(r)));
    }

    @Override
    public IManyResultItem<R> concat(IManyResultItem<R> r) {
        return new StreamResultItem<>(Stream.concat(r.stream(), this.r));
    }

    @Override
    public <T> StreamResultItem<T> map(Function<R, T> toMap) {
        return new StreamResultItem<>(r.map(toMap));
    }

    @Override
    public R orElse(R r) {
        return firstOptional().orElse(r);
    }

    @Override
    public R orElseGet(Supplier<R> r) {
        return firstOptional().orElseGet(r);
    }

    @Override
    public void ifPresent(Consumer<? super R> consumer) {
        this.r.forEach(consumer);
    }

    @Override
    public StreamResultItem<R> peek(Consumer<? super R> consumer) {
        return new StreamResultItem<>(this.r.peek(consumer));
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
        return this.r.isAnyNonNull(this);
    }

    public Stream<R> r() {
        return r;
    }

    @Override
    public boolean has(Predicate<R> e) {
        var lst = toList();
        var is = lst.stream().anyMatch(e);
        swap(lst);

        return is;
    }

    @Override
    public void forEach(Consumer<? super R> consumer) {
        this.r.forEach(consumer);
    }

    public boolean isStream() {
        return true;
    }

    @Override
    public StreamWrapper.CacheResult<R> getAll() {
        return this.r.toList();
    }

    @Override
    public StreamWrapper.CacheResult<R> toList() {
        return this.r.toList();
    }
}
