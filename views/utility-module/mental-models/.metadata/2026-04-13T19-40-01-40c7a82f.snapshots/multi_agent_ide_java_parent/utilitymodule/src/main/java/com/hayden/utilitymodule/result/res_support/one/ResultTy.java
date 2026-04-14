package com.hayden.utilitymodule.result.res_support.one;

import com.hayden.utilitymodule.result.async.FluxResult;
import com.hayden.utilitymodule.result.async.MonoResult;
import com.hayden.utilitymodule.result.res_ty.ClosableResult;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import com.hayden.utilitymodule.result.res_many.StreamResultItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shared for Ok and Err
 * @param <U>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class ResultTy<U> {

    @Delegate
    protected IResultItem<U> t;

    public ResultTy(Stream<U> t) {
        this.t = new StreamResultItem<>(t);
    }

    public ResultTy(Mono<U> t) {
        this.t = new MonoResult<>(t);
    }

    public ResultTy(Flux<U> t) {
        this.t = new FluxResult<>(t);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ResultTy(Optional<U> t) {
        if (t.isEmpty()) {
            this.t = new ResultTyResult<>(Optional.empty());
        } else {
            var to = t.get();

            if (to instanceof AutoCloseable a) {
                this.t = (IResultItem<U>) new ClosableResult<>(Optional.of(a));
            } else {
                this.t = new ResultTyResult<>(Optional.of(to));
            }

        }
    }

    public ResultTy(U t) {
        if (t instanceof AutoCloseable a) {
            this.t = (IResultItem<U>) new ClosableResult<>(Optional.of(a));
        } else {
            this.t = new ResultTyResult<>(Optional.ofNullable(t));
        }
    }


    public void set(U u) {
        this.t = from(u);
    }

    public List<U> toList() {
        return this.t.toList();
    }

}
