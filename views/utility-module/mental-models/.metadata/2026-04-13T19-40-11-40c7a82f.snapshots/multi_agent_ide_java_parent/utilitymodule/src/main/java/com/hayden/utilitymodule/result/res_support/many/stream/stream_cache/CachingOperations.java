package com.hayden.utilitymodule.result.res_support.many.stream.stream_cache;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.ok.Ok;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

public interface CachingOperations {

    interface CachedOperation<T, U> extends Function<T, U> {}

    // TODO: more ops?
    interface InfiniteOperation<T, U> extends CachedOperation<T, U> {}

    interface OnClosedOperation<T, U> extends InfiniteOperation<T, U> {}

    record StreamCacheResult<T, U>(CachedOperation<T, U> predicateType,
                                   U cachedResult) {}

    sealed interface StreamCacheOperation<T, U> extends Function<T, U>
            permits
                CachingOperations.ResultTyStreamWrapperOperation,
                CachingOperations.ResultStreamCacheOperation { }


    sealed interface ResultTyStreamWrapperOperation<T, U> extends StreamCacheOperation<T, U>
                        permits
                            CachingOperations.ResultTyPredicate,
                            CachingOperations.ResultTyStreamCacheFunction { }

    interface StreamCacheFunction<T, U> extends CachedOperation<T, U>, InfiniteOperation<T, U> { }

    sealed interface ResultStreamCacheOperation<T, U> extends StreamCacheOperation<T, U>
            permits
                CachingOperations.ResultStreamCacheFunction,
                CachingOperations.ResultStreamCachePredicate{ }

    sealed interface ResultStreamCacheFunction<T, U> extends StreamCacheFunction<T, U>, ResultStreamCacheOperation<T, U>
            permits
                CachingOperations.RetrieveError,
                CachingOperations.RetrieveRes,
                CachingOperations.RetrieveFirstRes { }

    sealed interface ResultTyStreamCacheFunction<T, U> extends StreamCacheFunction<T, U>, ResultTyStreamWrapperOperation<T, U>
            permits CachingOperations.RetrieveFirstTy { }

    record OnCloseResultTy<T>() implements OnClosedOperation<T, Boolean>, ResultTyPredicate<T>, ResultStreamCachePredicate<T> {
        @Override
        public Boolean apply(T teResult) {
            return true;
        }

        @Override
        public boolean test(T t) {
            return false;
        }
    }

    record RetrieveFirstTy<T>() implements ResultTyStreamCacheFunction<T, T> {
        @Override
        public T apply(T teResult) {
            return teResult;
        }
    }

    record RetrieveError<T, E>() implements ResultStreamCacheFunction<Result<T, E>, Err<E>> {
        @Override
        public Err<E> apply(Result<T, E> teResult) {
            return teResult.e();
        }
    }

    record RetrieveRes<T, E>(List<Result<T, E>> results) implements ResultStreamCacheFunction<Result<T, E>, Ok<T>>{

        public RetrieveRes() {
            this(new ArrayList<>());
        }

        @Override
        public Ok<T> apply(Result<T, E> teResult) {
            return teResult.r();
        }
    }

    record RetrieveFirstRes<T, E>() implements ResultStreamCacheFunction<Result<T, E>, Result<T, E>>{
        @Override
        public Result<T, E> apply(Result<T, E> teResult) {
            return teResult;
        }
    }

    interface StreamCachePredicate<T> extends Predicate<T>, CachedOperation<T, Boolean>, InfiniteOperation<T, Boolean> {

        non-sealed interface Any<T> extends CachingOperations.ResultTyPredicate<T>, CachingOperations.ResultStreamCachePredicate<T> {
            @Override
            default boolean test(T t) {
                return false;
            }

            @Override
            default Boolean apply(T t) {
                return this.test(t);
            }
        }

        non-sealed interface All<T> extends CachingOperations.ResultTyPredicate<T>, CachingOperations.ResultStreamCachePredicate<T> {
            @Override
            default boolean test(T t) {
                return false;
            }

            @Override
            default Boolean apply(T t) {
                return this.test(t);
            }
        }
    }

    sealed interface ResultTyPredicate<T> extends StreamCachePredicate<T>, ResultTyStreamWrapperOperation<T, Boolean>
            permits IsAnyNonNull, IsCompletelyEmpty, OnCloseResultTy, StreamCachePredicate.All, StreamCachePredicate.Any {

        @Override
        default Boolean apply(T t) {
            return this.test(t);
        }
    }

    sealed interface ResultStreamCachePredicate<T> extends
                StreamCachePredicate<T>,
                ResultStreamCacheOperation<T, Boolean>
            permits HasErr, HasResult, IsAnyNonNull, IsCompletelyEmpty, OnCloseResultTy, StreamCachePredicate.All, StreamCachePredicate.Any {

        @Override
        default Boolean apply(T t) {
            return this.test(t);
        }
    }

    interface PersistentCacheResult  {}


    record IsAnyNonNull() implements StreamCachePredicate.Any, ResultStreamCachePredicate, ResultTyPredicate, PersistentCacheResult {
        @Override
        public boolean test(Object o) {
            return o != null;
        }
    }

    record IsCompletelyEmpty() implements StreamCachePredicate.All, ResultStreamCachePredicate, ResultTyPredicate, PersistentCacheResult {
        @Override
        public boolean test(Object o) {
            return false;
        }
    }

    @Slf4j
    record HasResult<R, E>() implements StreamCachePredicate.Any<Result<R, E>>, ResultStreamCachePredicate<Result<R, E>>, PersistentCacheResult {
        @Override
        public boolean test(Result<R, E> o) {
            if (o.isOkStream()) {
                if (log.isDebugEnabled())
                    log.debug("Could not check if had ok because ok was of stream type, cachable cannot be cached inside of cache.");
                return false;
            }

            return o.isOk();
        }
    }

    @Slf4j
    record HasErr<R, E>() implements StreamCachePredicate.Any<Result<R, E>>, ResultStreamCachePredicate<Result<R, E>>, PersistentCacheResult {
        @Override
        public boolean test(Result<R, E> o) {
            if (o.isErrStream()) {
                if (log.isDebugEnabled())
                    log.debug("Could not check if had error because error was of stream type, cachable cannot be cached inside of cache.");
                return false;
            }
            return o.isError();
        }
    }


}
