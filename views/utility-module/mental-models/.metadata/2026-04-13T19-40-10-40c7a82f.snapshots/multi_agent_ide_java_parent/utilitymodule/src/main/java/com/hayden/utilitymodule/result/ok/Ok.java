package com.hayden.utilitymodule.result.ok;

import com.hayden.utilitymodule.result.res_many.IManyResultItem;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public interface Ok<R> extends IResultItem<R> {

    static <R> Ok<R> ok(R r) {
        if (r instanceof AutoCloseable closeable) {
            Ok<AutoCloseable> c = new ClosableOk<>(closeable);
            return (Ok<R>) c;
        }
        return new StdOk<>(r);
    }

    static <R> Ok<R> stream(Stream<R> r) {
        return new StdOk<>(r);
    }

    static <R> Ok<R> ok(IResultItem<R> r) {
        return new StdOk<>(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <R> Ok<R> ok(Optional<R> r) {
        return new StdOk<>(r);
    }

    static <R> Ok<R> empty() {
        return new StdOk<>(Optional.empty());
    }

    static <R> Ok<ResponseEntity<R>> ok(ResponseEntity<R> r) {
        return new ResponseEntityOk(r);
    }

    IResultItem<R> t();

    default <S> Ok<S> mapResult(Function<R, S> toMap) {
        return switch (this.t()) {
            case IManyResultItem<R> s ->
                    Ok.ok(s.map(toMap));
            default -> {
                if (this.t().isPresent())
                    yield Ok.ok(toMap.apply(t().get()));

                yield Ok.empty();
            }

        };

    }

    default <S> Ok<S> flatMapResult(Function<R, StdOk<S>> toMap) {
        return switch (this.t()) {
            case IManyResultItem<R> s ->
                    Ok.ok(s.flatMap(st -> {
                        var mapped = toMap.apply(st);
                        return mapped.t();
                    }));
            default -> {
                if (this.t().isPresent())
                    yield toMap.apply(t().get());

                yield Ok.empty();
            }

        };
    }

    default <U> Ok<U> cast() {
        return switch (this.t()) {
            case IManyResultItem<R> s ->
                    Ok.ok(s.map(r -> (U) r));
            default -> {
                if (t().isEmpty())
                    yield Ok.empty();
                try {
                    yield this.mapResult(s -> (U) s);
                } catch (ClassCastException c) {
                    yield Ok.empty();
                }
            }
        };
    }

    default R orElseRes(R orRes) {
        return this.t().orElse(orRes);
    }

}
