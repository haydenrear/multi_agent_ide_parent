package com.hayden.utilitymodule.result.res_support.many.stream;

import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachableStream;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachingOperations;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hayden.utilitymodule.result.res_support.many.stream.StreamWrapper.StandardCache.finalizeOps;

public interface StreamCache<T extends CachingOperations.CachedOperation<ST, ?>, C extends CachableStream<ST, C>, ST> {

    Logger log = LoggerFactory.getLogger(StreamCache.class);

    ConcurrentHashMap<Class<? extends T>, CachingOperations.StreamCacheResult> CACHED_RESULTS();

    Class<? extends CachingOperations.StreamCacheOperation> provider();

    boolean isCached();

    default void doCache(C c) {
        doCache(c, st -> {});
    }

    void doCache(C c, Consumer<? super ST> terminalOp);

    StreamWrapper.CacheResult<ST> cacheToList(C c, Consumer<? super ST> terminalOp);

    boolean isParallel();

    boolean isAsync();

    Optional<ExecutorService> executor();

    default List<ST> cacheToList(C c) {
        return this.cacheToList(c, cst -> {});
    }

    void resetCache();

    static Stream<Class<? extends CachingOperations.StreamCacheOperation>> predicateTypesHierarchy(Class<? extends CachingOperations.StreamCacheOperation> anyClass) {
        return Optional.ofNullable(anyClass.getPermittedSubclasses())
                .stream()
                .flatMap(Arrays::stream)
                .flatMap(spc -> {
                    var permittedSubclasses = Optional.ofNullable(spc.getPermittedSubclasses())
                            .stream()
                            .flatMap(Arrays::stream)
                            .map(c -> (Class<? extends CachingOperations.StreamCacheOperation>) c)
                            .toList();
                    return Stream.concat(
                            Stream.concat(Stream.of((Class<? extends CachingOperations.StreamCacheOperation>) spc, anyClass), permittedSubclasses.stream()),
                            //
                            permittedSubclasses
                                    .stream()
                                    .flatMap(StreamCache::predicateTypesHierarchy)
                    );
                });
    }

    static List<CachingOperations.StreamCacheOperation> predicateTypes(Class<? extends CachingOperations.StreamCacheOperation> anyClass) {
        return predicateTypesHierarchy(anyClass)
                .filter(Predicate.not(Class::isInterface))
                .map(c -> {
                    try {
                        return (CachingOperations.StreamCacheOperation) c.getDeclaredConstructor().newInstance();
                    } catch (
                            InstantiationException |
                            IllegalAccessException |
                            InvocationTargetException |
                            NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    default @NotNull StreamWrapper.CacheFilterResult<ST> cacheFilter(C streamed,
                                                                     StreamResultOptions opts,
                                                                     Consumer<? super ST> terminalOp) {
        if (opts.empty()) {
            var e = new CachingOperations.IsCompletelyEmpty();
            CACHED_RESULTS().computeIfAbsent((Class<? extends T>) e.getClass(),
                    k -> new CachingOperations.StreamCacheResult<>(e, true));
        }

        if (opts.hasRes()) {
            var e = new CachingOperations.HasResult<>();
            CACHED_RESULTS().computeIfAbsent((Class<? extends T>) e.getClass(),
                    k -> new CachingOperations.StreamCacheResult<>(e, true));
        }

        if (opts.hasErr()) {
            var e = new CachingOperations.HasErr<>();
            CACHED_RESULTS().computeIfAbsent((Class<? extends T>) e.getClass(),
                    k -> new CachingOperations.StreamCacheResult<>(e, true));
        }

        var streamCacheOperations = new AtomicReference<>(
                predicateTypes(provider()).stream()
                        .filter(scop -> !opts.isInfinite() || scop instanceof CachingOperations.InfiniteOperation<?, ?>)
                        .filter(Predicate.not(s -> CACHED_RESULTS().containsKey(s.getClass())))
                        .toList());

        if (opts.isInfinite()) {
            return doInfinite(streamed, terminalOp, streamCacheOperations, opts);
        }


        return doStandard(streamed, terminalOp, streamCacheOperations);
    }

    private @NotNull StreamWrapper.CacheFilterResult<ST> doStandard(C streamed, Consumer<? super ST> terminalOp, AtomicReference<List<CachingOperations.StreamCacheOperation>> streamCacheOperations) {
        var stream = stream(streamed);

        List<ST> resultList;
        if (isAsync()) {
            resultList = doAsyncStandard(terminalOp, streamCacheOperations, stream);
        } else {
            resultList = stream
                    .peek(res -> {
                        terminalOp.accept(res);
                        doOps(res, streamCacheOperations);
                    })
                    .toList();
        }


        return new StreamWrapper.CacheFilterResult<>((List) streamCacheOperations.get(), resultList);
    }

    private Stream<ST> stream(C streamed) {
        var stream = streamed.stream();

        if (isParallel())
            stream = stream.parallel();
        return stream;
    }

    private @NotNull List<ST> doAsyncStandard(Consumer<? super ST> terminalOp, AtomicReference<List<CachingOperations.StreamCacheOperation>> streamCacheOperations, Stream<ST> stream) {
        List<ST> resultList;
        try (final ExecutorService te = retrieveExecutor()) {
            var all = stream
                    .map(i -> CompletableFuture.supplyAsync(
                            () -> {
                                terminalOp.accept(i);
                                doOps(i, streamCacheOperations);
                                return i;
                            },
                            te
                    ))
                    .toList();

            resultList = CompletableFuture.allOf(all.toArray(CompletableFuture[]::new))
                    .thenApply(v -> all.stream().flatMap(f -> {
                        try {
                            return Stream.of(f.get());
                        } catch (
                                InterruptedException |
                                ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }))
                    .get()
                    .toList();

        } catch (ExecutionException |
                 InterruptedException e) {
            throw new RuntimeException(e);
        }
        return resultList;
    }

    private @NotNull StreamWrapper.CacheFilterResult<ST> doInfinite(C streamed,
                                                                    Consumer<? super ST> terminalOp,
                                                                    AtomicReference<List<CachingOperations.StreamCacheOperation>> streamCacheOperations,
                                                                    StreamResultOptions opts) {
        Stream<ST> toCache = infiniStream(streamed, streamCacheOperations);

        List<ST> objects = Collections.synchronizedList(new ArrayList<>());
        var f = new StreamWrapper.CacheFilterResult<>((List) streamCacheOperations.get(), objects);

        if (opts.isInfinite() && opts.maxSize() == 0) {
            // TODO?
            log.warn("""
                    Infinite stream has no max size, unbounded memory concerns. Future could be soft references in return list.
                    """);
        }

        toCache = toCache.onClose(() -> {
            try {
                finalizeOps(f, (ConcurrentHashMap) CACHED_RESULTS());
            } catch (Exception e) {
                log.error("Failed to finalize cache operations: {}\n{}",
                        e.getMessage(), SingleError.parseStackTraceToString(e));
            }
        });

        if (isAsync()) {
            try (final ExecutorService te = retrieveExecutor()) {
                toCache.forEach(n -> te.submit(() -> {
                    terminalOp.accept(n);
                    doAddRemoveInfiniCache(opts, n, objects);
                }));
            }
        } else {
            toCache.peek(terminalOp)
                    .forEach(n -> doAddRemoveInfiniCache(opts, n, objects));
        }


        return f;
    }

    private static <ST> void doAddRemoveInfiniCache(StreamResultOptions opts, ST n, List<ST> objects) {
        if (opts.maxSize() != 0 && objects.size() > opts.maxSize()) {
            synchronized (objects) {
                if (objects.size() > opts.maxSize()) {
                    IntStream.range(0, Math.max(objects.size() - opts.maxSize(), 1))
                            .forEach(index -> objects.removeFirst());
                }
                objects.add(n);
            }
        } else {
            objects.add(n);
        }
    }

    private @NotNull ExecutorService retrieveExecutor() {
        if (this.executor().isEmpty())
            throw new RuntimeException("Async execution required for async.");

        final ExecutorService te = this.executor().get();
        return te;
    }

    private @NotNull Stream<ST> infiniStream(C streamed,
                                             AtomicReference<List<CachingOperations.StreamCacheOperation>> streamCacheOperations) {

        Stream<ST> toParseStream = streamed.stream();

        if (isParallel())
            toParseStream = toParseStream.parallel();

        var s = toParseStream
                .peek(c -> doOps(c, streamCacheOperations))
                .onClose(() -> streamCacheOperations.get().stream()
                        .flatMap(sca -> sca instanceof CachingOperations.OnClosedOperation<?, ?> onClose
                                        ? Stream.of(onClose)
                                        : Stream.empty()
                        )
                        .forEach(onClose ->
                                CACHED_RESULTS().put((Class<? extends T>) onClose.getClass(),
                                        new CachingOperations.StreamCacheResult(onClose, onClose.apply(null)))));
        if (isParallel())
            return s.parallel();

        return s;
    }

    private void doOps(ST res, AtomicReference<List<CachingOperations.StreamCacheOperation>> streamCacheOperations) {
        var nextOps = streamCacheOperations.get();
        streamCacheOperations.set(
                nextOps.stream().filter(op -> switch (op) {
                            case CachingOperations.StreamCachePredicate.Any n -> {
                                if (n.test(res)) {
                                    CACHED_RESULTS().computeIfAbsent((Class<? extends T>) n.getClass(),
                                            k -> new CachingOperations.StreamCacheResult<>(n, true));
                                    yield false;
                                }
                                yield true;
                            }
                            case CachingOperations.StreamCachePredicate.All p -> {
                                if (p.test(res)) {
                                    yield true;
                                } else {
                                    CACHED_RESULTS().computeIfAbsent((Class<? extends T>) p.getClass(),
                                            k -> new CachingOperations.StreamCacheResult<>(p, false));
                                    yield false;
                                }
                            }
                            case CachingOperations.StreamCacheFunction fun ->
                                    Optional.ofNullable(fun.apply(res))
                                            .map(app -> {
                                                CACHED_RESULTS().compute((Class<? extends T>) fun.getClass(),
                                                        (key, prev) -> new CachingOperations.StreamCacheResult(fun, app));
                                                return false;
                                            })
                                            .orElse(true);
                            case CachingOperations.OnCloseResultTy o ->
                                    true;
                        })
                        .toList()
        );
    }

    default <W extends CachingOperations.CachedOperation<U, V>, U, V> OneResult<V, SingleError.StandardError> get(Class<W> clazz) {
        return Result.fromOptOrErr(
                        TypeReferenceDelegate.<W>create(clazz),
                        () -> new SingleError.StandardError("Failed to cast to type ref for %s".formatted(clazz.getName()))
                )
                .flatMapResult(this::get)
                .one();
    }

    default <W extends CachingOperations.CachedOperation<U, V>, U, V> OneResult<V, SingleError.StandardError> get(TypeReferenceDelegate<W> clazz) {
        try {
            return Optional.ofNullable(CACHED_RESULTS().get(clazz.underlying()))
                    .map(cachedRes -> Result.<V, SingleError.StandardError>ok((V) cachedRes.cachedResult()))
                    .orElseGet(() -> Result.err(new SingleError.StandardError("Operation did not exist from %s.".formatted(clazz.getName()))));
        } catch (
                ClassCastException castingException) {
            log.error("Error when getting stream cache operation: {}\n{}",
                    castingException.getMessage(),
                    SingleError.parseStackTraceToString(castingException));
            return Result.err(new SingleError.StandardError(castingException));
        }
    }

}
