package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.ok.Ok;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResult;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface ManyResult<R, E> extends Result<R, E> {

    @Override
    Err<E> e();

    @Override
    Ok<R> r();

    @Override
    default String errorMessage() {
        return e().stream()
                .map(e -> {
                    if (e instanceof SingleError s) {
                        return s.getMessage();
                    } else return e.toString();
                })
                .collect(Collectors.joining(", "));
    }

    default List<R> getAll() {
        return this.toList();
    }

    @Override
    default ManyResult<R, E> many() {
        return this;
    }

    default boolean isStreamResult() {
        return false;
    }

    Result<R, E> hasAnyOr(Supplier<Result<R, E>> s);

    Result<R, E> last(Consumer<Result<R, E>> last);

    R hasFirstErrOrElseGet(Function<Err<E>, R> or);

    R firstResOrElse(R or);

    Result<R, E> firstErrOr(Supplier<Err<E>> s);

    @Override
    ManyResult<R, E> filterErr(Predicate<E> b);

    @Override
    ManyResult<R, E> filterResult(Predicate<R> b);

    @Override
    <E1> ManyResult<R, E1> mapError(Function<E, E1> mapper, E1 defaultValue);

    @Override
    <U> Result<U, E> flatMap(Function<R, Result<U, E>> mapper);

    @Override
    Stream<R> toStream();

    @Override
    Stream<R> detachedStream();

    @Override
    OneResult<R, E> one();

    @Override
    default StreamResult<R, E> streamResult() {
        return Result.super.streamResult();
    }

    @Override
    default OneResult<List<R>, List<E>> collectList() {
        return Result.super.collectList();
    }

    @Override
    default Stream<E> streamErr() {
        return Result.super.streamErr();
    }

    @Override
    <U> ManyResult<U, E> map(Function<R, U> mapper);

    @Override
    ManyResult<R, E> peek(Consumer<R> mapper) ;

    @Override
    <U> ManyResult<U, E> map(Function<R, U> mapper, Supplier<E> err);

    @Override
    ManyResult<R, E> peekError(Consumer<E> mapper);

    <E1> Result<R, E1> mapError(Function<E, E1> mapper);

    @Override
    <U> ManyResult<U, E> flatMap(Function<R, Result<U, E>> mapper, Supplier<E> errorSupplier);

    @Override
    <U> ManyResult<U, E> flatMapResult(Function<R, Result<U, E>> mapper);

    @Override
    default boolean isOk() {
        return Result.super.isOk();
    }

    @Override
    default <U, NE> Stream<Result<U, NE>> flatMapStreamResult(Function<R, Stream<Result<U, NE>>> mapper) {
        return Result.super.flatMapStreamResult(mapper);
    }

    @Override
    default <U> ManyResult<U, E> cast() {
        return Result.super.<U>cast().many();
    }

    @Override
    default <V> Result<R, V> castError() {
        return Result.super.castError();
    }

     @Override
    default ManyResult<R, E> doOnError(Consumer<E> e) {
        return Result.super.doOnError(e).many();
    }

}
