package com.hayden.utilitymodule.result.res_many;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public interface IManyResultItem<R> extends IResultItem<R> {

    Logger log = LoggerFactory.getLogger(IManyResultItem.class);

    <V> IResultItem<V> flatMap(Function<R, IResultItem<V>> toMap);

    /**
     * @param r
     * @return
     */
    IManyResultItem<R> add(R r);

    /**
     * @param r
     * @return
     */
    IManyResultItem<R> concat(IManyResultItem<R> r);

    boolean has(Predicate<R> e);

    void swap(List<R> toSwap);

    @Override
    default Optional<R> firstOptional() {
        return getFirst();
    }

    private @NotNull Optional<R> getFirst() {
        var l = Lists.newArrayList(toList())
                .stream().filter(Objects::nonNull).toList();
        if (l.size() > 1) {
           log.error("Called optional on stream result with more than one value. Returning first.");
        }

        if (l.size() != 1 && !l.isEmpty())
            log.warn("Called get first on many result and discarded {} results.", l.size() - 1);

        return !l.isEmpty() ? Optional.of(l.getFirst()) : Optional.empty();
    }

    default StreamResultItem<R> streamResult() {
        return this instanceof StreamResultItem<R> r
               ? r
               : new StreamResultItem<>(this.stream());
    }

    default List<R> getAll() {
        return toList();
    }


}
