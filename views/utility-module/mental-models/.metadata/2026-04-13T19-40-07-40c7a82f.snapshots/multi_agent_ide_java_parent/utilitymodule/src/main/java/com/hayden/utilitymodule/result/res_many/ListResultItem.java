package com.hayden.utilitymodule.result.res_many;

import com.hayden.utilitymodule.result.res_single.ISingleResultItem;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
public class ListResultItem<R> implements IStreamResultItem<R> {

    private List<R> r;

    public ListResultItem(List<R> r) {
        this.r = r;
    }

    public static <R> ListResultItem<R> of(Stream<IResultItem<R>> stream) {
        return new ListResultItem<>(stream.flatMap(IResultItem::stream).toList());
    }

    @Override
    public synchronized void swap(List<R> toSwap) {
        this.r = toSwap;
    }


    @Override
    public <T> IResultItem<T> from(T r) {
        return new ListResultItem<>(Optional.ofNullable(r).stream().toList());
    }

    @Override
    public <T> IResultItem<T> from(Optional<T> r) {
        return new ListResultItem<>(r.stream().toList());
    }

    @Override
    public Stream<R> stream() {
        return r.stream();
    }

    @Override
    public Flux<R> flux() {
        return Flux.fromStream(r.stream());
    }

    @Override
    public Mono<R> firstMono() {
        List<R> streamList = this.r;
        swap(streamList);

        if (streamList.isEmpty()) {
            return Mono.error(new RuntimeException("Called get Mono on list with more than 1."));
        } else {
            if (streamList.size() != 1) {
                log.warn("First only one mono is expected, but got {}", streamList.size());
            }

            return Mono.justOrEmpty(streamList.getFirst());
        }
    }

    @Override
    public ISingleResultItem<R> single() {
        if (this.r.size() > 1) {
            log.warn("Called single on list result ty with {} elements.", r.size());
        }

        return this.r.isEmpty()
               ? new ResultTyResult<>(Optional.empty())
               : new ResultTyResult<>(Optional.ofNullable(this.r.getFirst()));
    }

    @Override
    public IResultItem<R> filter(Predicate<R> p) {
        return new ListResultItem<>(r.stream().filter(p).toList());
    }

    @Override
    public R get() {
        return firstOptional().orElse(null);
    }

    @Override
    public <T> ListResultItem<T> flatMap(Function<R, IResultItem<T>> toMap) {
        return new ListResultItem<>(
                r.stream().map(toMap)
                        .flatMap(IResultItem::stream)
                        .toList()
        );
    }

    @Override
    public ListResultItem<R> add(R r) {
        this.r.add(r);
        return this;
    }

    @Override
    public ListResultItem<R> concat(IManyResultItem<R> r) {
        r.stream().forEach(t -> this.r.add(t));
        return this;
    }

    @Override
    public <T> ListResultItem<T> map(Function<R, T> toMap) {
        return new ListResultItem<>(r.stream().map(toMap).toList());
    }

    @Override
    public R orElse(R r) {
        return firstOptional().orElse(r);
    }

    @Override
    public R orElseGet(Supplier<R> r) {
        return firstOptional().orElseGet(r);
    }

    @Override
    public void ifPresent(Consumer<? super R> consumer) {
        this.r.forEach(consumer);
    }

    @Override
    public ListResultItem<R> peek(Consumer<? super R> consumer) {
        return new ListResultItem<>(this.r.stream().peek(consumer).toList());
    }

    @Override
    public boolean isMany() {
        return true;
    }


    public List<R> toList() {
        return this.r;
    }

    public boolean isStream() {
        return false;
    }

    @Override
    public boolean isOne() {
        return false;
    }

    @Override
    public boolean isPresent() {
        return !this.r.isEmpty();
    }

    public Stream<R> r() {
        return r.stream();
    }


    @Override
    public boolean has(Predicate<R> e) {
        return this.r.stream().anyMatch(e);
    }
}
