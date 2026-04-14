package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.assert_util.AssertUtil;
import com.hayden.utilitymodule.result.closable.ClosableMonitor;
import com.hayden.utilitymodule.result.ok.ClosableOk;
import com.hayden.utilitymodule.result.ok.Ok;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResult;
import com.hayden.utilitymodule.result.res_ty.IResultItem;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hayden.utilitymodule.result.res_ty.ClosableResult.isSameClosable;
import static com.hayden.utilitymodule.result.res_ty.ClosableResult.logClosableEqualsErr;

//DO NOT USE!!!!
public interface ClosableResult<T extends AutoCloseable, E> extends OneResult<T, E>, AutoCloseable {

    /**
     * for debugging
     */
    ClosableMonitor closableMonitor = new ClosableMonitor();

    ClosableOk<T> r();

    @Override
    default boolean isClosable() {
        return true;
    }

    default Result<T, E> except(Function<Exception, T> toDo) {
        return except(Objects::nonNull, toDo);
    }

    default Result<T, E> exceptEmpty(Consumer<Exception> toDo) {
        return except(Objects::nonNull, f -> {
            toDo.accept(f);
            return null;
        });
    }

    default Result<T, E> except(Predicate<Exception> exc,
                                Function<Exception, T> toDo) {
        return Result.ok(r().except(exc, toDo));
    }

    default Result<T, E> flatExcept(Function<Exception, Result<T, E>> toDo) {
        return flatExcept(Objects::nonNull, toDo, e -> e);
    }

    default <U, V> Result<U, V> flatExcept(Predicate<Exception> exc,
                                           Function<Exception, Result<U, V>> toDo,
                                           Function<Result<T, E>, Result<U, V>> fallbackMapper) {
        return flatExcept(exc, toDo, () -> fallbackMapper.apply(this));
    }

    default <U, V> Result<U, V> flatExcept(Function<Exception, Result<U, V>> toDo,
                                           Function<Result<T, E>, Result<U, V>> fallbackMapper) {
        return flatExcept(Objects::nonNull, toDo, fallbackMapper);
    }

    default <U, V> Result<U, V> flatExcept(Function<Exception, Result<U, V>> toDo,
                                           Supplier<Result<U, V>> fallback) {
        return flatExcept(Objects::nonNull, toDo, fallback);
    }

    default <U, V> Result<U, V> flatExcept(Predicate<Exception> exc,
                                           Function<Exception, Result<U, V>> toDo,
                                           Supplier<Result<U, V>> fallback) {
        if (r().isExcept(exc)) {
            return toDo.apply(r().getExcept());
        }

        return fallback.get();
    }

    default Result<T, E> exceptRuntime() {
        return except(exc -> {
           if(exc instanceof RuntimeException r)
               throw r;

           throw new RuntimeException(exc);
        });
    }

    default <U> OneResult<U, E> map(Function<T, U> mapper) {
        if (this.r().isPresent()) {
            var toRet = this.r().map(mapper);
            return Result.from(Ok.ok(toRet), this.e()).one();
        }

        return this.cast();
    }

    default <U> ManyResult<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        var applied = this.r().map(mapper).stream();
        return new StreamResult<>(applied).concatWith(this.e().stream().map(Result::err));
    }

    @Override
    default <U> ManyResult<U, E> flatMapResult(Function<T, Result<U, E>> mapper) {
        var res = OneResult.super.flatMapResult(toMap -> {
            var applied = mapper.apply(toMap);
            if (!applied.isClosable()) {
                this.r().doClose();
                return applied;
            }

            return applied.peek(u -> {
                if (!isSameClosable(this.r(), u)) {
                    this.r().doClose();
                }
            });
        });


        return res;
    }

    private <U> void doClose(ManyResult<U, E> c) {
        log.warn("Performing equals on closable in flat map to check to see if need to perform close.");

        if (com.hayden.utilitymodule.result.res_ty.ClosableResult.isSameClosable(this.r(), c))
        if (!this.r().matches(c)) {
            try {
                this.r().t().doClose();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    default Result<T, E> exceptErr(Function<Exception, E> toDo) {
        return r().exceptErr(toDo)
                .map(Result::<T, E>err)
                .orElse(this);
    }

    static boolean hasOpenResources() {
        return closableMonitor.hasOpenResources();
    }

    static void registerClosed(AutoCloseable closeable) {
        closableMonitor.registerClosed(closeable);
    }

    static void closeAllOpenResources() {
        closableMonitor.closeAll();
    }

    default void onInitialize() {
        if (this.r().isPresent()) {
            AssertUtil.assertTrue(() -> this.r().isOne(), "On initialize failed - Closable result type was more than one - not implemented.");
            closableMonitor.onInitialize(() -> this.r().getClosableQuietly());
        }
    }

    default ClosableResult doOnClosable(Consumer<? super T> e) {
        this.r().ifPresent(a -> {
            e.accept(a);
            closableMonitor.afterClose(() -> a);
        });
        return this;
    }

    default void doOnEach(Consumer<? super T> e) {
        doOnClosable(e);
    }

    default void ifPresent(Consumer<? super T> consumer) {
        doOnClosable(consumer);
    }

}