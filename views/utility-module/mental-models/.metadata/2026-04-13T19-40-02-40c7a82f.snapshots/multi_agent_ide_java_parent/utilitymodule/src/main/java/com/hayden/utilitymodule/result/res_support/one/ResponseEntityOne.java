package com.hayden.utilitymodule.result.res_support.one;

import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.error.BindingResultErr;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.ok.Ok;
import com.hayden.utilitymodule.result.ok.ResponseEntityOk;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;

public record ResponseEntityOne<T>(Ok<ResponseEntity<T>> r, Err<BindingResult> e)
        implements OneResult<ResponseEntity<T>, BindingResult> { }