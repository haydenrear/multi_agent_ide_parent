package com.hayden.utilitymodule.result.error;

import com.hayden.utilitymodule.result.res_many.IManyResultItem;
import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Data
public class StdErr<R> extends ResultTy<R> implements Err<R> {

    public StdErr(Stream<R> r) {
        super(r);
    }

    public StdErr(IResultItem<R> r) {
        super(r);
    }

    public StdErr(Mono<R> r) {
        super(r);
    }

    public StdErr(Flux<R> r) {
        super(r);
    }

    public StdErr(R r) {
        super(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public StdErr(Optional<R> r) {
        super(r);
    }

    @Override
    public IResultItem<R> t() {
        return this.t;
    }

}
