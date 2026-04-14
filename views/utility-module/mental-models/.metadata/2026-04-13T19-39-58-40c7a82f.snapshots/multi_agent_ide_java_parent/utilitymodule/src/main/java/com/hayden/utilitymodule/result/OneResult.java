package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.Either;
import com.hayden.utilitymodule.assert_util.AssertUtil;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.ok.Ok;
import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResult;
import com.hayden.utilitymodule.result.res_support.one.One;
import com.hayden.utilitymodule.result.res_ty.IResultItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface OneResult<R, E> extends ManyResult<R, E> {

    default Result<R, E> flatMapErr(Function<R, Result<R, E>> mapper) {
        return flatMapResult(mapper);
    }


    default IResultItem<R> toResultItem() {
        return this.r().isMany()
               ? this.r().many()
               : this.r().single();
    }

    default OneResult<R, E> one() {
        return this;
    }

    default R get() {
        return this.r().get();
    }

    default boolean isPresent() {
        return r().isPresent();
    }

    default <U> ManyResult<U, E> flatMap(Function<R, Result<U, E>> mapper) {
        if (this.r().isMany()) {
            var m = Result.from(Stream.of(Result.ok(this.r()), Result.err(this.e())))
                    .flatMap(mapper);

            return m.many();
        } else {
            var applied = this.r().map(mapper).stream();
            return new StreamResult<>(applied).concatWith(this.e().stream().map(Result::err));
        }
    }

    default <U> OneResult<U, E> map(Function<R, U> mapper) {
        if (this.r().isMany())
            return Result.from(Ok.ok(this.r().many().map(mapper)), this.e()).one();
        else {
            if (this.r().isPresent()) {
                var toRet = mapper.apply(this.r().get());
                return Result.from(Ok.ok(toRet), this.e()).one();
            }

            return this.cast();
        }
    }

    default <U> OneResult<U, E> cast() {
        return Result.<U, E>from(this.r().cast(), this.e()).one();
    }


    default Optional<R> toOptional() {
        return r().firstOptional();
    }

    default Optional<R> optional() {
        return r().firstOptional();
    }

    default Result<R, E> orError(Supplier<Err<E>> s) {
        if (e().isPresent())
            return this;

        return Result.from(this.r(), s.get());
    }

    @Override
    default ManyResult<R, E> many() {
        return new One<>(this.r(), this.e());
    }

    @Override
    default OneResult<R, E> hasAnyOr(Supplier<Result<R, E>> s) {
        return this.or(s).one();
    }

    @Override
    default ManyResult<R, E> last(Consumer<Result<R, E>> last) {
        return this.many();
    }

    @Override
    default R hasFirstErrOrElseGet(Function<Err<E>, R> or) {
        if (this.e().isPresent()) {
            return this.r().get();
        }

        return or.apply(this.e());
    }

    @Override
    default R firstResOrElse(R or) {
        if (this.r().isPresent()) {
            return this.r().get();
        }

        return or;
    }

    @Override
    default OneResult<R, E> firstErrOr(Supplier<Err<E>> s) {
        if (this.e().isPresent()) {
            return this;
        }

        return Result.from(this.r(), s.get())
                .one();
    }

    default Err<E> error() {
        return e();
    }

    default Stream<R> stream() {
        return r().stream();
    }

    default Stream<E> streamErr() {
        return e().stream();
    }

    default void ifPresent(Consumer<? super R> t) {
        this.r().ifPresent(t);
    }

    default boolean isError() {
        return r().isEmpty();
    }

    default boolean hasError() {
        return e().isPresent();
    }

    default R orElseGet(Supplier<R> o) {
        if (this.r().isPresent())
            return this.r().get();

        return o.get();
    }

    default boolean hasErr(Predicate<E> e) {
        if (this.e().isMany())
            return this.e().many().has(e);
        else return this.e().filterErr(e)
                .isPresent();
    }

    default OneResult<R, E> peek(Consumer<R> mapper) {
        return Result.from(Ok.ok(this.r().peek(mapper)), this.e()).one();
    }


    default <U> OneResult<U, E> map(Function<R, U> mapper, Supplier<E> err) {
        return r().<Result<U, E>>map(t -> Result.from(Ok.ok(mapper.apply(t)), this.e()))
                .orElse(Result.err(err.get())).one();
    }

    @Override
    default OneResult<R, E> peekError(Consumer<E> mapper) {
        return new One<>(this.r(), Err.err(this.e().peek(mapper)));
    }


    default <E1> OneResult<R, E1> mapError(Function<E, E1> mapper) {
        if (this.e().isMany())
            return Result.from(this.r(), Err.err(new ListResultItem<>(this.e().stream().map(mapper).toList()))).one();
        else {
            if (this.e().isPresent()) {
                Err<E1> r1 = this.e().mapErr(mapper);
                return Result.from(this.r(), r1).one();
            }

            return this.castError();
        }
    }

    default Result<R, E> or(Supplier<? extends Result<R, E>> s) {
        if (this.r().isPresent())
            return this;

        Result<R, E> teResult = s.get();
        return Result.from(teResult.r(), teResult.e().addError(this.e()));
    }


    default OneResult<R, E> filterErr(Predicate<E> b) {
        if (this.e().isMany()) {
            var filtered = this.e().many().filter(b);

            return Result.from(this.r(), Err.err(filtered)).one();
        } else {
            if (e().isPresent() && b.test(e().get())) {
                return this;
            }

            return Result.<R, E>from(this.r(), Err.empty()).one();
        }
    }

    default OneResult<R, E> filterResult(Predicate<R> b) {
        if (this.r().isMany()) {
            var filtered = r().many().filter(b);

            return Result.from(Ok.ok(filtered), this.e()).one();
        } else {
            if (r().isPresent() && b.test(r().get())) {
                return this;
            }

            return Result.<R, E>err(this.e()).one();
        }
    }

    default <E1> OneResult<R, E1> mapError(Function<E, E1> mapper, E1 publicValue) {
        if (this.e().isMany()) {
            return Result.from(this.r(), Err.err(this.e().many().map(mapper))).one();
        } else {
            var err = this.mapError(mapper);
            if (err.e().isEmpty()) {
                return Result.from(r(), Err.err(publicValue)).one();
            } else {
                return mapError(mapper).one().orError(() -> Err.err(publicValue)).one();
            }
        }
    }


    @Override
    default Stream<R> toStream() {
        return this.r().stream();
    }

    @Override
    default Stream<R> detachedStream() {
        return this.r().stream();
    }

    default R orElseRes(R or) {
        if (this.r().isPresent())
            return this.r().get();

        return or;
    }

    default R orElseErrRes(Function<Err<E>, R> or) {
        if (this.r().isPresent())
            return this.r().get();

        return or.apply(this.e());
    }

    default <U> OneResult<U, E> flatMap(Function<R, Result<U, E>> mapper, Supplier<E> errorSupplier) {
        return r().map(mapper)
                .filter(r -> r.r().isPresent())
                .orElse(Result.err(errorSupplier.get()))
                .one();
    }

    default <U> ManyResult<U, E> flatMapToStreamResult(Function<R, StreamResult<U, E>> mapper) {
        if (this.r().isMany()) {
            var srt = r().many().map(mapper);
            return Result.from(Stream.concat(srt.stream(), Stream.of(Result.err(this.e())))).many();
        } else {
            if (this.r().isEmpty()) {
                return this.cast();
            } else {
                return new StreamResult<>(Stream.concat(
                        r().map(mapper).get().stream(),
                        Stream.of(Result.err(this.e()))));
            }
        }
    }

    default <U> ManyResult<U, E> flatMapResult(Function<R, Result<U, E>> mapper) {
        if (this.r().isMany()) {
            var srt = r().many().map(mapper);
            return Result.from(Stream.concat(srt.stream(), Stream.of(Result.err(this.e())))).many();
        } else {
            if (this.r().isEmpty()) {
                return this.cast();
            } else {
                var f = r().map(mapper);
                Result<U, E> ueResult = f.get();

                if (ueResult.isOkStream()) {
                    //TODO: is this important?
//                    Stream<Result<U, E>> errStream = Stream.of(Result.err(ueResult.e().addError(this.e())));
                    Stream<Result<U, E>> okStream = ueResult.toStream().map(Result::ok);
                    return Result.from(okStream).many();
                }

                var s = ueResult.one();
                return Result.from(s.r(), s.e().addError(this.e()))
                        .one();
            }
        }
    }

    default boolean isOk() {
        return r().isPresent();
    }


    default <V> OneResult<R, V> castError() {
        return Result.<R, V>from(this.r(), this.e().cast()).one();
    }

    default <R extends Result<U, V>, U, V> R cast(TypeReference<R> refDelegate) {
        return (R) this.map(c -> (U) c);
    }

    default OneResult<R, E> doOnError(Consumer<E> e) {
        this.e().ifPresent(e);
        return this;
    }

    default void doOnEach(Consumer<? super R> e) {
        this.r().forEach(e);
    }

    default Stream<Either<Ok<R>, Err<E>>> toEntryStream() {
        List<Either<Ok<R>, Err<E>>> l = this.r().stream()
                .map(t -> Either.<Ok<R>, Err<E>>from(Ok.ok(t), null))
                .collect(Collectors.toCollection(ArrayList::new));
        List<Either<Ok<R>, Err<E>>> r = this.e().stream()
                .map(t -> Either.<Ok<R>, Err<E>>from(null, Err.err(t)))
                .collect(Collectors.toCollection(() -> l));

        return r.stream();
    }

}
