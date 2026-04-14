package com.hayden.utilitymodule.proxies;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.cglib.proxy.Proxy;

import java.util.Objects;

@Slf4j
@UtilityClass
public class ProxyUtil {

    public boolean isProxy(Object obj) {
        if (obj == null)
            return false;

        if (obj instanceof Class c)
            throw new RuntimeException("Attempted to check if class class was proxy class!");

        var aopProxy = AopProxyUtils.ultimateTargetClass(obj);
        if (!Objects.equals(obj.getClass(), aopProxy)) {
            return true;
        }
        var i = Proxy.isProxyClass(obj.getClass());

        if (i)
            return true;

        log.debug("Didn't check JDK proxy!");
        return false;
    }



}
