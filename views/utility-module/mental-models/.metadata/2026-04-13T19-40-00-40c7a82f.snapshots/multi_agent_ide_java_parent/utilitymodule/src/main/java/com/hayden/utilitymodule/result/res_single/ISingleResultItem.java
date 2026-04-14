package com.hayden.utilitymodule.result.res_single;

import com.hayden.utilitymodule.result.res_many.IManyResultItem;
import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_many.StreamResultItem;
import com.hayden.utilitymodule.result.res_ty.IResultItem;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface ISingleResultItem<R> extends IResultItem<R> {


    Optional<R> optional();

    R orElse(R r);

    R orElseGet(Supplier<R> r);

    default boolean isEmpty() {
        return optional().isEmpty();
    }

    default boolean isPresent() {
        return optional().isPresent();
    }

    default ISingleResultItem<R> single() {
        return this;
    }

    default IManyResultItem<R> add(R r) {
        var s = Stream.concat(this.stream(), Stream.of(r)).toList();
        return new ListResultItem<>(s);
    }

    default IManyResultItem<R> concat(IManyResultItem<R> r) {
        return new StreamResultItem<>(Stream.concat(r.stream(), this.stream()));
    }

    default boolean has(Predicate<R> e) {
        return this.filter(e).isPresent();
    }
}
