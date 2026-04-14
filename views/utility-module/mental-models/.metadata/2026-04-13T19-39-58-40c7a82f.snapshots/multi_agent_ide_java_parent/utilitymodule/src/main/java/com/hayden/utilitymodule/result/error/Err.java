package com.hayden.utilitymodule.result.error;

import com.hayden.utilitymodule.result.res_many.IManyResultItem;
import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import org.springframework.validation.BindingResult;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface Err<R> extends IResultItem<R> {
    static <R> Err<R> stream(Stream<R> r) {
        return new StdErr<>(r);
    }

    static <R> Err<R> emptyStream() {
        return new StdErr<>(Stream.empty());
    }

    static <R> Err<R> err(IResultItem<R> r) {
        return new StdErr<>(r);
    }

    static Err<BindingResult> err(BindingResult r) {
        return new BindingResultErr(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <R> Err<R> err(Optional<R> r) {
        return new StdErr<>(r);
    }

    static <R> Err<R> empty() {
        return new StdErr<>(Optional.empty());
    }

    static <R> Err<R> err(R r) {
        return new StdErr<>(Optional.ofNullable(r));
    }

    IResultItem<R> t();

    default <S> Err<S> mapErr(Function<R, S> toMap) {
        return switch(this.t()) {
            case IManyResultItem<R> s ->
                    Err.err(s.map(toMap));
            default -> {
                if (this.t().isPresent())
                    yield Err.err(toMap.apply(t().get()));

                yield Err.empty();
            }

        };
    }

    default Err<R> addErrors(List<R> e) {
        return this.addError(Err.err(new ListResultItem<>(e)));
    }

    default Err<R> addError(R e) {
        return addError(Err.err(e));
    }

    default Err<R> addError(Err<R> e) {
        var list = this.toMutableList();
        e.forEach(list::add);
        return Err.err(new ListResultItem<>(list));
    }

    default Err<R> filterErr(Predicate<R> b) {
        return switch(this.t()) {
            case IManyResultItem<R> s ->
                    Err.err(s.filter(b));
            default -> {
                if (this.t().isPresent() && b.test(t().get())) {
                    yield this;
                }

                yield Err.empty();
            }

        };
    }

    default <S> Err<S> flatMapErr(Function<R, Err<S>> toMap) {
        return switch(this.t()) {
            case IManyResultItem<R> s ->
                    Err.err(s.flatMap(st-> {
                        var mapped = toMap.apply(st);
                        return mapped.many();
                    }));
            default ->
                    Err.err(this.t().flatMap(s -> toMap.apply(s).single()));
        };
    }

    default <U> Err<U> cast() {
        if (t().isEmpty())
            return Err.empty();
        try {
            return this.mapErr(s -> (U) s);
        } catch (ClassCastException c) {
            return Err.empty();
        }
    }

    default Err<R> orErr(Supplier<Err<R>> orRes) {
        if (this.t().isPresent())
            return this;

        return orRes.get();
    }
}
