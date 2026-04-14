package com.hayden.utilitymodule.result.async;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.res_many.StreamResultItem;
import com.hayden.utilitymodule.result.res_support.many.stream.ResultStreamWrapper;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResultOptions;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachableStream;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachingOperations;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface IAsyncResultItem<R> extends IResultItem<R>, CachableStream<R, IAsyncResultItem<R>> {

    @Slf4j
    final class AsyncTyResultStreamWrapper<R> extends ResultStreamWrapper<IAsyncResultItem<R>, R> {

        public AsyncTyResultStreamWrapper(StreamResultOptions options, Flux<R> underlying, IAsyncResultItem<R> res) {
            this(asyncVirtual(options),
                    underlying.publishOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor())).toStream(),
                    res);
        }

        public AsyncTyResultStreamWrapper(StreamResultOptions options, Mono<R> underlying, IAsyncResultItem<R> res) {
            this(asyncVirtual(options), underlying.flux(), res);
        }

        public AsyncTyResultStreamWrapper(StreamResultOptions options, Stream<R> underlying, IAsyncResultItem<R> res) {
            super(asyncVirtual(options), underlying, CachingOperations.ResultTyStreamWrapperOperation.class, res);
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

    private static StreamResultOptions asyncVirtual(StreamResultOptions options) {
        return options.toBuilder().isAsync(true).isVirtual(true).build();
    }

    boolean didFinish();

    AsyncTyResultStreamWrapper<R> doAsync(Consumer<? super R> consumer);

    R block() throws ExecutionException, InterruptedException;

    default R block(Duration wait) throws ExecutionException, InterruptedException {
        return block();
    }

    default boolean isAsyncSub() {
        return true;
    }

    default IAsyncManyResultItem<R> many() {
        if (this instanceof IAsyncManyResultItem<R> t) {
            return t;
        }

        return new FluxResult<>(this.firstMono().flux());
    }

}
