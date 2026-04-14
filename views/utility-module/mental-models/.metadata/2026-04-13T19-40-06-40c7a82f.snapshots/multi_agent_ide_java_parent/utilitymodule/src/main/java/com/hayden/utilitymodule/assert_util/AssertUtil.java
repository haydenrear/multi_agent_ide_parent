package com.hayden.utilitymodule.assert_util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.function.Supplier;

public interface AssertUtil {

    boolean isDebugEnabled = LoggerFactory.getLogger(AssertUtil.class).isDebugEnabled();

    interface Assertion extends Supplier<Boolean> {}
    interface AssertionMessage extends Supplier<String> {}

    static void assertTrue(Assertion toAssert, String message) {
        if (isDebugEnabled) {
            Assert.isTrue(toAssert.get(), message);
        }
    }

    static void assertTrue(Assertion toAssert, AssertionMessage message) {
        if (isDebugEnabled) {
            Assert.isTrue(toAssert.get(), message.get());
        }
    }

}
