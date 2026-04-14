package com.hayden.utilitymodule.result.ok;

import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@Data
public class StdOk<R> extends ResultTy<R> implements Ok<R> {

    public StdOk(R r) {
        super(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public StdOk(Optional<R> r) {
        super(r);
    }

    public StdOk(Stream<R> r) {
        super(r);
    }

    public StdOk(IResultItem<R> r) {
        super(r);
    }

    public StdOk(Mono<R> r) {
        super(r);
    }

    public StdOk(Flux<R> r) {
        super(r);
    }

    @Override
    public IResultItem<R> t() {
        return t;
    }
}
