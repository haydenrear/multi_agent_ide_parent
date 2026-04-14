package com.hayden.utilitymodule.result.res_support.one;

import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.ok.Ok;

/**
 * @param <T>
 * @param <E> Note OneOkErrRes can also contain Ok or Err with StreamResult inside
 */
public record One<T, E>(Ok<T> r, Err<E> e) implements OneResult<T, E> {

    @Override
    public boolean isErrStream() {
        return r.isStream() ;
    }

    @Override
    public boolean isOkStream() {
        return e.isStream();
    }
}
