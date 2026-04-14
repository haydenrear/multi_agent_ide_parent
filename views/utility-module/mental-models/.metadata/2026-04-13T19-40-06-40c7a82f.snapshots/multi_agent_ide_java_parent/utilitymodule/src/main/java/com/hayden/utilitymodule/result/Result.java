package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.Either;
import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.agg.AggregateParamError;
import com.hayden.utilitymodule.result.agg.Responses;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.map.StreamResultCollector;
import com.hayden.utilitymodule.result.ok.ClosableOk;
import com.hayden.utilitymodule.result.ok.MutableOk;
import com.hayden.utilitymodule.result.ok.Ok;
import com.hayden.utilitymodule.result.ok.StdOk;
import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResult;
import com.hayden.utilitymodule.result.res_support.one.ClosableOne;
import com.hayden.utilitymodule.result.res_support.one.MutableOne;
import com.hayden.utilitymodule.result.res_support.one.One;
import com.hayden.utilitymodule.result.res_support.one.ResponseEntityOne;
import com.hayden.utilitymodule.result.res_ty.CachedCollectedResult;
import com.hayden.utilitymodule.result.res_ty.ClosableResult;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import jakarta.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MapBindingResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Result<T, E> {
    Logger log = LoggerFactory.getLogger(Result.class);

    default String errorMessage() {
        return this.e()
            .map(err ->
                err instanceof SingleError s ? s.getMessage() : err.toString()
            )
            .orElse("No error getMessage to be printed.");
    }

    interface Monadic<R> {
        R get();

        Stream<R> stream();
        Optional<R> firstOptional();
        IResultItem<R> filter(Predicate<R> p);

        <T> IResultItem<T> flatMap(Function<R, IResultItem<T>> toMap);
        <T> IResultItem<T> map(Function<R, T> toMap);

        R orElse(R r);

        R orElseGet(Supplier<R> r);

        void ifPresent(Consumer<? super R> consumer);

        boolean isEmpty();
    }

    default T unwrap() {
        return this.r().get();
    }

    default E getError() {
        return this.e().get();
    }

    default E unwrapErr() {
        return this.e().get();
    }

    default E unwrapError() {
        return this.e().get();
    }

    default T getValue() {
        return unwrap();
    }

    default boolean isClosable() {
        return false;
    }

    default Result<T, E> onErrorMap(Supplier<T> mapper) {
        return onErrorMap(Objects::nonNull, mapper);
    }

    default Result<Object, E> onErrorMapTo(Supplier<Object> mapTo) {
        return onErrorMapTo(Objects::nonNull, mapTo);
    }

    default Result<Object, E> onErrorMapTo(
        Predicate<E> hasErr,
        Supplier<Object> mapTo
    ) {
        return onErrorMapToResult(hasErr, mapTo, () ->
            (Result<Object, E>) this
        );
    }

    default <U, V> Result<U, V> onErrorFlatMapResult(
        Supplier<Result<U, V>> mapTo,
        Function<Result<T, E>, Result<U, V>> fallback
    ) {
        return onErrorFlatMapResult(Objects::nonNull, mapTo, fallback);
    }

    default <U, V> Result<U, V> onErrorFlatMapResult(
        Function<E, Result<U, V>> mapTo,
        Function<Result<T, E>, Result<U, V>> fallback
    ) {
        return onErrorFlatMapResult(Objects::nonNull, mapTo, fallback);
    }

    default Result<T, E> onErrorFlatMapResult(Function<E, Result<T, E>> mapTo) {
        return onErrorFlatMapResult(Objects::nonNull, mapTo, e -> e);
    }

    default <U, V> Result<U, V> onErrorFlatMapResult(
        Predicate<E> hasErr,
        Supplier<Result<U, V>> mapTo,
        Function<Result<T, E>, Result<U, V>> fallback
    ) {
        if (this.e().filter(hasErr).isPresent()) {
            return mapTo.get();
        }

        return fallback.apply(this);
    }

    default Result<T, E> onErrorFlatMapResult(
        Predicate<E> hasErr,
        Function<E, Result<T, E>> mapTo
    ) {
        return onErrorFlatMapResult(hasErr, mapTo, e -> e);
    }

    default <U, V> Result<U, V> onErrorFlatMapResult(
        Predicate<E> hasErr,
        Function<E, Result<U, V>> mapTo,
        Function<Result<T, E>, Result<U, V>> fallback
    ) {
        if (
            this.e()
                .filter(e -> {
                    if (e instanceof SingleError err) return err.isError();

                    return true;
                })
                .filter(hasErr)
                .isPresent()
        ) {
            return mapTo.apply(this.e().get());
        }

        return fallback.apply(this);
    }

    default <V> Result<V, E> onErrorMapTo(
        Supplier<V> hasErr,
        Supplier<V> fallback
    ) {
        return onErrorMapTo(Objects::nonNull, hasErr, fallback);
    }

    default <V> Result<V, E> onErrorMapToResult(
        Predicate<E> matcher,
        Supplier<V> hasErr,
        Supplier<Result<V, E>> fallback
    ) {
        if (this.e().filter(matcher).isPresent()) {
            return Result.ok(hasErr.get());
        }

        return fallback.get();
    }

    default <V> Result<V, E> onErrorMapTo(
        Predicate<E> matcher,
        Supplier<V> hasErr,
        Supplier<V> fallback
    ) {
        if (this.e().filter(matcher).isPresent()) {
            return Result.ok(hasErr.get());
        }

        return Result.from(Ok.ok(fallback.get()), this.e());
    }

    default Result<T, E> onErrorMap(Predicate<E> matcher, Supplier<T> hasErr) {
        if (this.e().filter(matcher).isPresent()) {
            return Result.ok(hasErr.get());
        }

        return this;
    }

    default Result<T, E> onErrorMap(
        Predicate<E> matcher,
        Function<E, T> hasErr
    ) {
        if (this.e().filter(matcher).isPresent()) {
            return Result.ok(hasErr.apply(this.e().filter(matcher).get()));
        }

        return this;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <T, E> Result<T, E> fromOpt(
        Optional<T> stringStringEntry,
        E gitAggregateError
    ) {
        return from(new StdOk<>(stringStringEntry), Err.err(gitAggregateError));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <T, E> Result<T, E> fromOpt(Optional<T> opt) {
        return from(new StdOk<>(opt), Err.empty());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <T, E> Result<T, E> fromOpt(
        Optional<T> stringStringEntry,
        Optional<E> gitAggregateError
    ) {
        return from(new StdOk<>(stringStringEntry), Err.err(gitAggregateError));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <T, E> Result<T, E> fromOptOrErr(
        Optional<T> stringStringEntry,
        E gitAggregateError
    ) {
        return stringStringEntry.isPresent()
            ? from(Ok.ok(stringStringEntry), Err.empty())
            : from(Ok.empty(), Err.err(gitAggregateError));
    }

    static <T, E> Result<T, E> fromOptOrErr(
        Optional<T> stringStringEntry,
        Supplier<E> gitAggregateError
    ) {
        return stringStringEntry.isPresent()
            ? from(Ok.ok(stringStringEntry), Err.empty())
            : from(Ok.empty(), Err.err(gitAggregateError.get()));
    }

    static <T extends AutoCloseable, E> com.hayden.utilitymodule.result.Result<
        T,
        E
    > tryFromThrow(T o) {
        return tryFromThrow(o, () -> null);
    }

    static <T extends AutoCloseable, E> com.hayden.utilitymodule.result.Result<
        T,
        E
    > tryFromThrow(Callable<T> o) {
        try {
            return tryFromThrow(o.call(), () -> null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static <T extends AutoCloseable, E> com.hayden.utilitymodule.result.Result<
        T,
        E
    > tryFromThrow(T o, Callable<Void> onClose) {
        try {
            return Result.tryOk(
                new ClosableResult<>(Optional.ofNullable(o), onClose)
            );
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static <T extends AutoCloseable, E> com.hayden.utilitymodule.result.ClosableResult<T, E> tryFrom(
        Callable<T> o,
        Callable<Void> onClose
    ) {
        log.debug("Doing try from with result ty. Means there was a closable opened. Will log debug on close.");
        try {
            return Result.tryOk(new ClosableResult<>(Optional.ofNullable(o.call()), onClose));
        } catch (Exception e) {
            return Result.tryOk(
                ClosableResult.<T>builder()
                    .caught(e)
                    .r(Optional.empty())
                    .build()
            );
        }
    }

    static <T extends AutoCloseable, E> com.hayden.utilitymodule.result.Result<T, E> tryFrom(T o, Callable<Void> onClose) {
        log.debug("Doing try from with result ty. Means there was a closable opened. Will log debug on close.");
        return Result.tryOk(
            new ClosableResult<>(Optional.ofNullable(o), onClose)
        );
    }

    static <T extends AutoCloseable, E> com.hayden.utilitymodule.result.ClosableResult<T, E> tryFrom(
        Callable<T> o
    ) {
        return tryFrom(o, () -> null);
    }

    static <T, E> Result<T, E> stream(Mono<T> o) {
        try {
            return Result.ok(o);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static <T, E> Result<T, E> stream(Stream<T> o) {
        try {
            return Result.ok(o);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static <R, E> OneResult<R, E> ok(R r) {
        return new One<>(new StdOk<>(r), Err.empty());
    }

    static <R> OneResult<ResponseEntity<R>, BindingResult> ok(
        ResponseEntity<R> r
    ) {
        return new ResponseEntityOne<>(
            Ok.ok(r),
            Err.err(new MapBindingResult(new HashMap<>(), "Binding"))
        );
    }

    static <R, E> MutableResult<R, E> mutableOk(R r) {
        return new MutableOne<>(new MutableOk<>(r), Err.empty());
    }

    static <
        R extends AutoCloseable, E
    > com.hayden.utilitymodule.result.ClosableResult<R, E> tryOk(R r) {
        return new ClosableOne<>(ClosableOk.ok(r), Err.empty());
    }

    static <R, E> OneResult<R, E> resOk(R r) {
        return new One<>(new StdOk<>(r), Err.empty());
    }

    static <R, E> Result<R, E> ok(IResultItem<R> r) {
        return new One<>(new StdOk<>(r), Err.empty());
    }

    static <
        R extends AutoCloseable, E
    > com.hayden.utilitymodule.result.ClosableResult<R, E> tryOk(
        ClosableResult<R> r
    ) {
        return new ClosableOne<>(ClosableOk.ok(r), Err.empty());
    }

    static <R, E> StreamResult<R, E> from(Stream<Result<R, E>> r) {
        return new StreamResult<>(r);
    }

    static <R, E> Result<R, E> ok(Mono<R> r) {
        return new One<>(new StdOk<>(r), Err.empty());
    }

    static <R, E> Result<R, E> ok(Flux<R> r) {
        return new One<>(new StdOk<>(r), Err.empty());
    }

    static <R, E> Result<R, E> ok(Stream<R> r) {
        return okStream(r);
    }

    static <R, E> Result<R, E> okStream(Stream<R> r) {
        return new StreamResult<>(r.map(Result::ok));
    }

    static <R, E> Result<R, E> ok(Ok<R> r) {
        return new One<>(r, Err.empty());
    }

    static <R, E> OneResult<R, E> err(Optional<E> r) {
        return new One<>(Ok.empty(), Err.err(r));
    }

    static <R, E> OneResult<R, E> err(E r) {
        if (r != null) {
            try {
                Object getMessage = r
                    .getClass()
                    .getMethod("getMessage")
                    .invoke(r);
                if (getMessage instanceof String s && !s.isBlank())
                    log.error("Found error {}", s);
            } catch (
                NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException e
            ) {
                log.error("Found error", e);
            }
        }
        return new One<>(Ok.empty(), Err.err(r));
    }

    static <
        R extends AutoCloseable, E
    > com.hayden.utilitymodule.result.ClosableResult<R, E> tryErr(E r) {
        return new ClosableOne<>(ClosableOk.emptyClosable(), Err.err(r));
    }

    static <R, E> Result<R, E> err(Err<E> r) {
        return new One<>(Ok.empty(), r);
    }

    static <R, E> Result<R, E> from(R r, E e) {
        return new One<>(Ok.ok(r), Err.err(e));
    }

    static <R, E> Result<R, E> fromOr(R r, Supplier<E> e) {
        return Optional.ofNullable(r)
            .map(Result::<R, E>ok)
            .orElseGet(() -> Result.<R, E>err(e.get()).one());
    }

    static <R, E> Result<R, E> from(IResultItem<R> r, IResultItem<E> e) {
        return new One<>(Ok.ok(r), Err.err(e));
    }

    static <R, E> Result<R, E> from(Ok<R> r, Err<E> e) {
        return new One<>(r, e);
    }

    static <
        T extends Responses.AggregateResponse, E extends AggregateError
    > @Nullable Result<T, E> all(
        Collection<Result<T, E>> mapper,
        Result<T, E> finalResult
    ) {
        return Optional.ofNullable(all(mapper))
            .flatMap(r -> Optional.ofNullable(r.e()))
            .map(e -> {
                finalResult
                    .e()
                    .flatMap(f -> IResultItem.toRes(f.errors()))
                    .ifPresent(f ->
                        e.ifPresent(toAdd -> f.addAll(toAdd.errors()))
                    );
                return finalResult;
            })
            .orElse(finalResult);
    }

    static void logThreadStarvation() {
        log.warn(
            "Calling blocking operation on subscription. This could lead to thread  starvation."
        );
    }

    static void logClosableMaybeNotClosed() {
        log.debug(
            "Called a function on closable that maybe means close was not closed."
        );
    }

    static void logAsync() {
        log.warn(
            "Calling async operation on Mono subscription. This could lead to operations happening before or after expected."
        );
    }

    @SafeVarargs
    static <
        T extends Responses.AggregateResponse, E extends AggregateParamError
    > @Nullable Result<T, E> all(Result<T, E>... mapper) {
        return all(Arrays.asList(mapper));
    }

    static <
        T extends Responses.AggregateResponse, E extends AggregateParamError
    > @Nullable Result<T, E> all(Collection<Result<T, E>> mapper) {
        Result<T, E> result = null;
        for (Result<T, E> nextResultToAdd : mapper) {
            if (result == null) {
                result = nextResultToAdd;
                Assert.isTrue(
                    result.r().isOne(),
                    "All results must have only one result when calling all with aggregates."
                );
                Assert.isTrue(
                    result.e().isOne(),
                    "All results must have only one result when calling all with aggregates."
                );
            }
            if (nextResultToAdd.r().isPresent()) {
                if (result.r().isEmpty()) {
                    var temp = result;
                    result = nextResultToAdd;
                    nextResultToAdd = temp;
                } else {
                    result.r().get().addAgg(nextResultToAdd.r().get());
                }
            }

            result = addErrors(nextResultToAdd, result);
        }

        return result;
    }

    static <
        T extends Responses.AggregateResponse, E extends AggregateParamError
    > Result<T, E> addErrors(Result<T, E> toAdd, Result<T, E> toAddTo) {
        if (toAdd.e().isPresent()) {
            if (toAddTo.e().isEmpty()) {
                return Result.from(toAddTo.r(), toAdd.e());
            } else {
                toAdd
                    .filterErr(
                        Predicate.not(toFilter ->
                            toAddTo.hasErr(re -> re == toFilter)
                        )
                    )
                    .streamErr()
                    .forEach(toAddTo.e().get()::addError);
                //                if (result.e().t instanceof IManyResultTy<E> res) {
                //                    res!!!!!!!!!!
                //                }
            }
        }
        return toAddTo;
    }

    static <E, T> Result<T, E> empty() {
        return new One<>(Ok.empty(), Err.empty());
    }

    static <E, T> MutableResult<T, E> mutableEmpty() {
        return new MutableOne<>(new MutableOk<>(Optional.empty()), Err.empty());
    }

    Result<T, E> filterErr(Predicate<E> b);

    Result<T, E> filterResult(Predicate<T> b);

    <E1> Result<T, E1> mapError(Function<E, E1> mapper, E1 defaultValue);

    <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper);

    /**
     * //TODO: This is bad, replace
     * This is bad
     * @return
     */
    Err<E> e();

    /**
     * //TODO: This is bad, replace
     * @return
     */
    Ok<T> r();

    default boolean isErrStream() {
        return false;
    }

    default boolean isOkStream() {
        return false;
    }

    Stream<T> toStream();

    Stream<T> detachedStream();

    ManyResult<T, E> many();

    OneResult<T, E> one();

    default StreamResult<T, E> streamResult() {
        if (this instanceof StreamResult<T, E> s) {
            return s;
        } else return new StreamResult<>(
            Stream.concat(
                this.r().stream().map(Result::ok),
                this.e().stream().map(Result::err)
            )
        );
    }

    default OneResult<List<T>, List<E>> collectList() {
        return toResultLists();
    }

    default Stream<E> streamErr() {
        return e().stream();
    }

    default void ifPresent(Consumer<? super T> t) {
        this.r().ifPresent(t);
    }

    default boolean isError() {
        return isErr();
    }

    default boolean isErr() {
        return hasErr();
    }

    default boolean hasErr() {
        return this.e().filterErr(Result::errFilter).isPresent();
    }

    private static <E> boolean errFilter(E e) {
        if (e instanceof AggregateError<?> a) {
            return a.isError();
        }
        if (e instanceof SingleError s) {
            return s.isError();
        }

        return true;
    }

    default Result<T, E> dropEmptyErr() {
        if (this.isOk() && StringUtils.isEmpty(this.errorMessage())) {
            return Result.ok(this.r());
        }

        return this;
    }

    default Result<T, E> dropEmptyAgg() {
        if (
            this.isOk() &&
            this.hasErr() &&
            this.hasErr(
                    err ->
                        err instanceof AggregateError<?> a &&
                        (a.errors().isEmpty() ||
                            StringUtils.isEmpty(a.getMessage()))
                )
        ) {
            return Result.ok(this.r());
        }

        return this;
    }

    default boolean hasErr(Predicate<E> e) {
        if (this.e().isMany()) {
            return this.e().many().has(e);
        } else {
            return this.e().filterErr(e).isPresent();
        }
    }

    default <U> Result<U, E> map(Function<T, U> mapper) {
        if (this.r().isMany()) return Result.from(
            Ok.ok(this.r().many().map(mapper)),
            this.e()
        );
        else {
            if (this.r().isPresent()) {
                var toRet = mapper.apply(this.r().get());
                return Result.from(Ok.ok(toRet), this.e());
            }

            return this.cast();
        }
    }

    default Result<T, E> peek(Consumer<T> mapper) {
        return Result.from(Ok.ok(this.r().peek(mapper)), this.e());
    }

    default <U> Result<U, E> map(Function<T, U> mapper, Supplier<E> err) {
        return r()
            .<Result<U, E>>map(t ->
                Result.from(Ok.ok(mapper.apply(t)), this.e())
            )
            .orElse(Result.err(err.get()));
    }

    default Result<T, E> peekError(Consumer<E> mapper) {
        return Result.from(this.r(), Err.err(this.e().peek(mapper)));
    }

    default Result<T, E> dropErr() {
        return mapError(e -> null);
    }

    default <E1> Result<T, E1> mapError(Function<E, E1> mapper) {
        if (this.e().isMany()) {
            return Result.from(
                this.r(),
                Err.err(
                    new ListResultItem<>(
                        this.e()
                            .stream()
                            .map(mapper)
                            .filter(Objects::nonNull)
                            .toList()
                    )
                )
            );
        } else {
            if (this.e().isPresent()) {
                Err<E1> r1 =
                    this.e().mapErr(mapper).filterErr(Objects::nonNull);
                return r1.isPresent()
                    ? Result.from(this.r(), r1)
                    : Result.ok(this.r());
            }

            return this.castError();
        }
    }

    default <U> Result<U, E> flatMap(
        Function<T, Result<U, E>> mapper,
        Supplier<E> errorSupplier
    ) {
        return r()
            .map(mapper)
            .filter(r -> r.r().isPresent())
            .orElse(Result.err(errorSupplier.get()));
    }

    <U> Result<U, E> flatMapResult(Function<T, Result<U, E>> mapper);

    default boolean isOk() {
        return r().isPresent();
    }

    default <U, NE> Stream<Result<U, NE>> flatMapStreamResult(
        Function<T, Stream<Result<U, NE>>> mapper
    ) {
        var p = map(mapper);
        return p.toStream().flatMap(s -> s);
    }

    default <U> Result<U, E> cast() {
        return Result.from(this.r().cast(), this.e());
    }

    default <V> Result<T, V> castError() {
        return Result.from(this.r(), this.e().cast());
    }

    default <R extends Result<U, V>, U, V> R cast(
        TypeReference<R> refDelegate
    ) {
        return (R) this.map(c -> (U) c);
    }

    default Result<T, E> doOnError(Consumer<E> e) {
        this.e().ifPresent(e);
        return this;
    }

    default void doOnEach(Consumer<? super T> e) {
        this.r().forEach(e);
    }

    /**
     * If underlying is stream, then collect to list, otherwise then
     * will be lists of 1.
     * @return
     */
    default OneResult<List<T>, List<E>> toResultLists() {
        return this.toEntryStream()
            .collect(new StreamResultCollector<>())
            .one();
    }

    default Stream<Either<Ok<T>, Err<E>>> toEntryStream() {
        List<Either<Ok<T>, Err<E>>> l =
            this.r()
                .stream()
                .map(t -> Either.<Ok<T>, Err<E>>from(Ok.ok(t), null))
                .collect(Collectors.toCollection(ArrayList::new));
        List<Either<Ok<T>, Err<E>>> r =
            this.e()
                .stream()
                .map(t -> Either.<Ok<T>, Err<E>>from(null, Err.err(t)))
                .collect(Collectors.toCollection(() -> l));

        return r.stream();
    }

    default CachedCollectedResult<T, E> toList() {
        return this.streamResult().collectCachedResults();
    }
}
