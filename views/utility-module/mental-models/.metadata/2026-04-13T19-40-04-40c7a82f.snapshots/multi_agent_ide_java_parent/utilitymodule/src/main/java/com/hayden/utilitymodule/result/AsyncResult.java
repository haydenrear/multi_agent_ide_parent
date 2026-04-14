package com.hayden.utilitymodule.result;

import java.util.function.Consumer;

public interface AsyncResult<R, E> extends Result<R, E> {


    void subscribe(Consumer<? super R> subscribe);


}
