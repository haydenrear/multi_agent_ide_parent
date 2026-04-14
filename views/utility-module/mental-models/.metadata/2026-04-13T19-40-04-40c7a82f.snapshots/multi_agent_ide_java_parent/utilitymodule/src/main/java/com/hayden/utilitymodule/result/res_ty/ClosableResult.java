package com.hayden.utilitymodule.result.res_ty;

import com.hayden.utilitymodule.result.ManyResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.ok.ClosableOk;
import com.hayden.utilitymodule.result.res_many.IManyResultItem;
import com.hayden.utilitymodule.result.res_many.StreamResultItem;
import com.hayden.utilitymodule.result.res_single.ISingleResultItem;
import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 *
 *
 * @param r
 * @param <R>
 */
@Slf4j
@Builder
//DO NOT USE
public record ClosableResult<R extends AutoCloseable>(Optional<R> r, @Nullable Exception caught, @Nullable Callable<Void> onClose, @Nullable Function<R, Void> d, AtomicBoolean closed)
        implements ISingleResultItem<R> {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ClosableResult(Optional<R> r) {
        this(r, null, null, null, new AtomicBoolean(false));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ClosableResult(Optional<R> r, Callable<Void> onClose) {
        this(r, null, onClose, null, new AtomicBoolean(false));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ClosableResult(Optional<R> r, Function<@org.jspecify.annotations.Nullable R, Void> onClose) {
        this(r, null, null, onClose, new AtomicBoolean(false));
    }

    @Override
    public IManyResultItem<R> many() {
        throw new UnsupportedOperationException("Many result items not supported for closable types.");
    }

    @Override
    public boolean isClosable() {
        return true;
    }

    @Override
    public Stream<R> stream() {
        // TODO: should this fail?
        log.warn("Calling close after terminate on stream for result AutoClosable.");
        return r.stream().onClose(this::doClose);
    }

    @Override
    public Flux<R> flux() {
        // TODO: should this fail?
        throw new NotImplementedException();
//        log.warn("Calling close after terminate on flux for result AutoClosable.");
//        return r.map(s -> {
//
//                    return Flux.using(() -> s, f -> Flux.just(f));
//                })
//                .orElse(Flux.empty())
//                .doAfterTerminate(this::doClose);
    }

    @Override
    public Mono<R> firstMono() {
        // TODO: should this fail?
        log.warn("Calling close after terminate on mono for result AutoClosable.");
        return Mono.justOrEmpty(r).doAfterTerminate(this::doClose);
    }

    @Override
    public Optional<R> firstOptional() {
        // TODO: should this fail?
        if (!closed.get())
            Result.logClosableMaybeNotClosed();
        return r();
    }

    @Override
    public <T> IResultItem<T> from(T r) {
        if (r instanceof AutoCloseable a) {
            return (IResultItem<T>) new ClosableResult<>(Optional.of(a));
        }

        return new ResultTyResult<>(Optional.ofNullable(r));
    }

    @Override
    public boolean isZeroOrOneAbstraction() {
        return true;
    }

    @Override
    public <T> IResultItem<T> from(Optional<T> r) {
        return IResultItem.toRes(r.orElse(null));
    }

    @Override
    public void forEach(Consumer<? super R> consumer) {
        ifPresent(consumer);
    }

    @Override
    public IResultItem<R> filter(Predicate<R> p) {
        return from(r.filter(p));
    }

    @Override
    public R get() {
        if (!closed.get()) {
            callOrElse();
            Result.logClosableMaybeNotClosed();
        }
        return this.r.orElse(null);
    }

    public R getClosableQuietly() {
        return this.r.orElse(null);
    }

    @Override
    public <T> IResultItem<T> flatMap(Function<R, IResultItem<T>> toMap) {
        return r.map(t -> {
                    var applied = toMap.apply(t);

                    if (isSameClosable(t, applied))
                        return applied;

                    doClose();

                    return applied;
                })
                .orElse(IResultItem.empty());
    }

    public static <R extends AutoCloseable, T> boolean isSameClosable(ClosableOk<R> closable, T applied) {
        logClosableEqualsErr();
        if (closable.isEmpty())
            return false;

        var t = closable.get();

        return isSameClosable(t, applied);
    }

    public static <R extends AutoCloseable, T> boolean isSameClosable(ClosableOk<R> closable, IResultItem<T> applied) {
        logClosableEqualsErr();
        if (closable.isEmpty())
            return false;

        var t = closable.get();

        return applied.isClosable() && applied.isOne() && applied.isPresent() && isSameClosable(t, applied.get());
    }

    public static <R extends AutoCloseable, T> boolean isSameClosable(R t, T applied) {
        logClosableEqualsErr();
        return applied == t;
    }

    public static void logClosableEqualsErr() {
        log.debug("Performed object equals to check if should close.");
    }

    @Override
    public <T> IResultItem<T> map(Function<R, T> toMap) {
        return from(r.map(m -> {
            var toApply = toMap.apply(m);
            if (isSameClosable(m, toApply)) {
                return toApply;
            }

            doClose();
            return toApply;
        }));
    }

    @Override
    public Optional<R> optional() {
        return r;
    }

    @Override
    public R orElse(R o) {
        if (!closed.get()) {
            callOrElse();
            Result.logClosableMaybeNotClosed();
        }
        return r.orElse(o);
    }

    @Override
    public R orElseGet(Supplier<R> o) {
        if (!closed.get()) {
            callOrElse();
            Result.logClosableMaybeNotClosed();
        }
        return r.orElseGet(o);
    }

    private static void callOrElse() {
        log.debug("Calling or else on closable. This probably means you have to close yourself...");
    }

    @Override
    public void ifPresent(Consumer<? super R> consumer) {
        r.ifPresent(consumer);
        doClose();
    }

    public void doWithoutClosing(Consumer<? super R> consumer) {
        r.ifPresent(consumer);
    }

    public void doClose() {
        try {
            if (this.closed.get()) {
                log.warn("Called close multiple times on ClosableResult.");
                return;
            }

            this.closed.set(true);
            log.debug("Doing close on ClosableResult closable.");
            this.r.ifPresent(rFound -> {
                try {
                    rFound.close();
                    com.hayden.utilitymodule.result.ClosableResult.registerClosed(rFound);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            Optional.ofNullable(onClose)
                    .ifPresent(c -> {
                        try {
                            c.call();
                        } catch (
                                Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public ClosableResult<R> peek(Consumer<? super R> consumer) {
        r.ifPresent(consumer);
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


}
