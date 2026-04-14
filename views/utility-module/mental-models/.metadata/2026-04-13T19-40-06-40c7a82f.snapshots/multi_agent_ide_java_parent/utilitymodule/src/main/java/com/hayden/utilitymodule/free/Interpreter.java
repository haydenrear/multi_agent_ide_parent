package com.hayden.utilitymodule.free;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public interface Interpreter<F extends Effect, A> extends Function<F, Free<F, A>> {

    Logger log = LoggerFactory.getLogger(Interpreter.class);

    interface FreeErrorMapper<F extends Effect, A> extends Function<Free.Error<F, A>, A> {}

    default FreeErrorMapper<F, A> mapErr() {
        return s -> {
            log.error("No error mapper defined.");
            return null;
        };
    }

}
