package com.hayden.utilitymodule.result.ok;

import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@Data
public class ResponseEntityOk<R> extends ResultTy<ResponseEntity<R>> implements Ok<ResponseEntity<R>> {

    public ResponseEntityOk(ResponseEntity<R> r) {
        super(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ResponseEntityOk(Optional<ResponseEntity<R>> r) {
        super(r);
    }

    public ResponseEntityOk(Stream<ResponseEntity<R>> r) {
        super(r);
    }

    public ResponseEntityOk(IResultItem<ResponseEntity<R>> r) {
        super(r);
    }

    public ResponseEntityOk(Mono<ResponseEntity<R>> r) {
        super(r);
    }

    public ResponseEntityOk(Flux<ResponseEntity<R>> r) {
        super(r);
    }

    @Override
    public IResultItem<ResponseEntity<R>> t() {
        return t;
    }
}
