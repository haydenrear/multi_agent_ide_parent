package com.hayden.utilitymodule.result.res_support.many.stream;

import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachableStream;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachingOperations;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

@Slf4j
public abstract class StreamWrapper<C extends CachableStream<ST, C>, ST> implements Stream<ST> {

    public record CacheFilterResult<ST>(List<CachingOperations.StreamCacheOperation<ST, ?>> res,
                                        @Delegate List<ST> results) implements List<ST> { }

    public record CacheResult<ST>(Collection<CachingOperations.StreamCacheResult> res,
                                  @Delegate List<ST> results) implements List<ST> { }

    @Delegate
    protected Stream<ST> underlying;

    protected C res;

    protected final Class<? extends CachingOperations.StreamCacheOperation> provider;

    protected final StreamResultOptions options;

    @Getter
    final StreamCache<? extends CachingOperations.CachedOperation, C, ST> cached;

    protected final class InfiniCache implements StreamCache<CachingOperations.InfiniteOperation<ST, ?>, C, ST> {

        private final ConcurrentHashMap<Class<? extends CachingOperations.InfiniteOperation<ST, ?>>, CachingOperations.StreamCacheResult> CACHED_RESULTS
                = new ConcurrentHashMap<>();

        private final Class<? extends CachingOperations.StreamCacheOperation> provider;

        private final StreamResultOptions streamResultOptions;

        private ExecutorService executorService;

        private volatile boolean cached = false;

        public InfiniCache(Class<? extends CachingOperations.StreamCacheOperation> provider,
                           StreamResultOptions streamResultOptions) {
            this.provider = provider;
            this.streamResultOptions = streamResultOptions;

            if (this.streamResultOptions.isAsync())
                this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        }

        @Override
        public synchronized void doCache(C c, Consumer<? super ST> st) {
            if (this.isCached())
                return;


            cacheFilter(c, options, st);

            this.cached = true;
        }

        @Override
        public CacheResult<ST> cacheToList(C c, Consumer<? super ST> st) {
            if (this.isCached())
                throw new IllegalStateException();


            var cachedL = cacheFilter(c, options, st);

            this.cached = true;

            return new CacheResult<>(CACHED_RESULTS.values(), cachedL.results);
        }

        @Override
        public boolean isParallel() {
            return streamResultOptions.isParallel();
        }

        @Override
        public boolean isAsync() {
            return this.streamResultOptions.isAsync();
        }

        @Override
        public Optional<ExecutorService> executor() {
            return Optional.ofNullable(this.executorService);
        }

        @Override
        public synchronized void resetCache() {
            cached = false;
        }

        @Override
        public ConcurrentHashMap<Class<? extends CachingOperations.InfiniteOperation<ST, ?>>, CachingOperations.StreamCacheResult> CACHED_RESULTS() {
            return CACHED_RESULTS;
        }

        @Override
        public Class<? extends CachingOperations.StreamCacheOperation> provider() {
            return provider;
        }

        @Override
        public boolean isCached() {
            return cached;
        }

    }

    protected final class StandardCache implements StreamCache<CachingOperations.CachedOperation<ST, ?>, C, ST> {

        private final ConcurrentHashMap<Class<? extends CachingOperations.CachedOperation<ST, ?>>, CachingOperations.StreamCacheResult> CACHED_RESULTS
                = new ConcurrentHashMap<>();

        private final Class<? extends CachingOperations.StreamCacheOperation> provider;

        private final StreamResultOptions streamResultOptions;

        private ExecutorService executorService;

        private volatile boolean cached = false;

        public StandardCache(Class<? extends CachingOperations.StreamCacheOperation> provider,
                             StreamResultOptions streamResultOptions) {
            this.provider = provider;
            this.streamResultOptions = streamResultOptions;
            if (this.streamResultOptions.isAsync())
                this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        }

        public synchronized void doCache(C c) {
            this.doCache(c, nextItem -> {});
        }

        public synchronized void doCache(C c, Consumer<? super ST> terminalOp) {
            doCaching(c, terminalOp);
        }

        @Override
        public CacheResult<ST> cacheToList(C c, Consumer<? super ST> terminalOp) {
             var created = doCaching(c, terminalOp);

             return new CacheResult<>(CACHED_RESULTS.values(), created.results);
        }

        private @NotNull StreamWrapper.CacheFilterResult<ST> doCaching(C c, Consumer<? super ST> terminalOp) {
            if (this.isCached())
                throw new IllegalStateException();

            var created = cacheFilter(c, options, terminalOp);
            finalizeOps(created);
            this.cached = true;
            return created;
        }

        void finalizeOps(StreamWrapper.CacheFilterResult<ST> created) {
            finalizeOps(created, CACHED_RESULTS());
        }

        static <ST> void finalizeOps(StreamWrapper.CacheFilterResult<ST> created,
                                     ConcurrentHashMap<Class<? extends CachingOperations.CachedOperation<ST, ?>>, CachingOperations.StreamCacheResult> cachedResults) {
            created.res.forEach(sp -> {
                switch (sp) {
                    case CachingOperations.StreamCachePredicate.Any n ->
                            cachedResults.computeIfAbsent((Class<? extends CachingOperations.CachedOperation<ST, ?>>) n.getClass(), k -> new CachingOperations.StreamCacheResult<>(n, false));
                    case CachingOperations.StreamCachePredicate.All p ->
                            cachedResults.computeIfAbsent((Class<? extends CachingOperations.CachedOperation<ST, ?>>) p.getClass(), k -> new CachingOperations.StreamCacheResult<>(p, true));
                    default -> {}
                }
            });
        }

        @Override
        public void resetCache() {
            cached = false;
        }

        @Override
        public ConcurrentHashMap<Class<? extends CachingOperations.CachedOperation<ST, ?>>, CachingOperations.StreamCacheResult> CACHED_RESULTS() {
            return CACHED_RESULTS;
        }

        @Override
        public Class<? extends CachingOperations.StreamCacheOperation> provider() {
            return provider;
        }

        @Override
        public boolean isCached() {
            return cached;
        }

        @Override
        public boolean isParallel() {
            return streamResultOptions.isParallel();
        }

        @Override
        public boolean isAsync() {
            return this.streamResultOptions.isAsync();
        }

        @Override
        public Optional<ExecutorService> executor() {
            return Optional.ofNullable(this.executorService);
        }

    }

    public StreamWrapper(StreamResultOptions options, Stream<ST> underlying,
                         Class<? extends CachingOperations.StreamCacheOperation> provider,
                         StreamWrapper<C, ST> other,
                         C res) {
        this.res = res;
        this.options = options;
        this.underlying = underlying;
        this.provider = provider;

        if (this.options.isInfinite())
            this.cached = new InfiniCache(provider, options);
        else
            this.cached = new StandardCache(provider, options);

        if (other != null)
            throw new RuntimeException("Not implemented yet");
    }

    public StreamWrapper(StreamResultOptions options, Stream<ST> underlying,
                         Class<? extends CachingOperations.StreamCacheOperation> provider,
                         C c) {
        this(options, underlying, provider, null, c);
    }

    public synchronized void swap(List<ST> to) {
        underlying = to.stream();
    }

    protected <T extends CachingOperations.CachedOperation<RE, V>, RE, V> OneResult<V, SingleError.StandardError> get(Class<T> t) {
        return cached.get(t);
    }

    protected <T extends CachingOperations.CachedOperation<RE, V>, RE, V> Result<V, SingleError.StandardError> get(TypeReferenceDelegate<T> t) {
        return cached.get(t);
    }

    public synchronized void resetCache() {
        this.cached.resetCache();
    }

     public synchronized boolean isAnyNonNull(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.IsAnyNonNull.class)
                .mapError(se -> {log.error("{}", se); return se;})
                .one()
                .get();
    }

    public synchronized boolean isCompletelyEmpty() {
        return isCompletelyEmpty(this.res);
    }

    public synchronized boolean isCompletelyEmpty(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.IsCompletelyEmpty.class)
                .mapError(se -> {log.error("{}", se); return se;})
                .one()
                .get();
    }

    public synchronized boolean hasAnyResult() {
        return this.hasAnyResult(this.res);
    }

    public synchronized boolean isAnyNonNull() {
        return this.isAnyNonNull(this.res);
    }

    public synchronized boolean hasAnyResult(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.HasResult.class)
                .mapError(se -> {log.error("{}", se); return se;})
                .one()
                .orElseRes(false);
    }

    public synchronized boolean hasAnyError(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.HasErr.class)
                .mapError(se -> {log.error("{}", se); return se;})
                .one()
                .orElseRes(false);
    }

    protected void cacheResultsIfNotCached() {
        cacheResultsIfNotCached(c -> {});
    }

    protected void cacheResultsIfNotCached(Consumer<? super ST> consumer) {
        if (!cached.isCached())
            this.cached.doCache(this.res, consumer);
    }

    public CacheResult<ST> cacheResultsIfNotCachedWithList(Consumer<? super ST> consumer) {
        if (!cached.isCached())
            return this.cached.cacheToList(this.res, consumer);

        throw new RuntimeException("Multiple terminal operations have been called.");
    }

    public synchronized CacheResult<ST> throwIfCachedOrCacheWithList(boolean infinite, Consumer<? super ST> consumer) {
        if (this.cached.isCached())
            throw new RuntimeException("Already cached!");

        return cacheResultsIfNotCachedWithList(consumer);
    }

    public synchronized CacheResult<ST> throwIfCachedOrCacheWithList(Consumer<? super ST> consumer) {
        return throwIfCachedOrCacheWithList(false, consumer);
    }

    public synchronized CacheResult<ST> throwIfCachedOrCacheWithList() {
        return cacheResultsIfNotCachedWithList(st -> {});
    }

    public synchronized void throwIfCachedOrCache(boolean infinite, Consumer<? super ST> consumer) {
        if (this.cached.isCached())
            throw new RuntimeException("Already cached!");

        cacheResultsIfNotCached(consumer);
    }

    public synchronized void throwIfCachedOrCache() {
        throwIfCachedOrCache(false, st -> {});
    }

    public synchronized void throwIfCachedOrCache(Consumer<? super ST> consumer) {
        throwIfCachedOrCache(false, consumer);
    }

    @Override
    public void close() {
        underlying.close();
    }

    @Override
    public boolean isParallel() {
        return underlying.isParallel();
    }

    @Override
    public Stream<ST> dropWhile(Predicate<? super ST> predicate) {
        return underlying.dropWhile(predicate);
    }

    @Override
    public CacheResult<ST> toList() {
        return throwIfCachedOrCacheWithList();
    }

    public CacheResult<ST> toList(Consumer<? super ST> consumer) {
        return throwIfCachedOrCacheWithList(consumer);
    }

    @Override
    public void forEachOrdered(Consumer<? super ST> action) {
        throwIfCachedOrCache(action);
    }

    @Override
    public void forEach(Consumer<? super ST> action) {
        throwIfCachedOrCache(action);
    }

}
