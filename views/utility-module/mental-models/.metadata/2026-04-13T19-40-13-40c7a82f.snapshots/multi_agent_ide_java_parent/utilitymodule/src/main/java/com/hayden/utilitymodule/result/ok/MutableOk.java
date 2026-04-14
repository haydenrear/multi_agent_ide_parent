package com.hayden.utilitymodule.result.ok;

import com.hayden.utilitymodule.result.res_ty.IResultItem;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.stream.Stream;

public class MutableOk<R> extends StdOk<R> {
    public MutableOk(R r) {
        super(r);
    }

    public MutableOk(Optional<R> r) {
        super(r);
    }

    public MutableOk(Stream<R> r) {
        super(r);
    }

    public MutableOk(IResultItem<R> r) {
        super(r);
    }

    public MutableOk(Mono<R> r) {
        super(r);
    }

    public MutableOk(Flux<R> r) {
        super(r);
    }

    public void set(R toSet) {
        this.t = new ResultTyResult<>(Optional.ofNullable(toSet));
    }




}
