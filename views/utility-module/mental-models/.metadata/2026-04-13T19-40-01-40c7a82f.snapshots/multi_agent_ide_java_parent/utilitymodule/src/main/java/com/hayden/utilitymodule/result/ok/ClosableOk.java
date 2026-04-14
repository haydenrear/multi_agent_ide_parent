package com.hayden.utilitymodule.result.ok;

import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_ty.ClosableResult;
import com.hayden.utilitymodule.result.res_ty.IResultItem;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

//DO NOT USE!!!!
public class ClosableOk<R extends AutoCloseable> extends ResultTy<R> implements Ok<R> {

    public ClosableOk(R r) {
        super(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ClosableOk(Optional<R> r) {
        super(r);
    }

    public ClosableOk(IResultItem<R> r) {
        super(r);
    }

    public static <R extends AutoCloseable> ClosableOk<R> ok(R r) {
        return new ClosableOk<R>(r);
    }

    public static <R extends AutoCloseable> ClosableOk<R> ok(ClosableResult<R> r) {
        return new ClosableOk<>(r);
    }

    public static <R extends AutoCloseable> ClosableOk<R> emptyClosable() {
        return new ClosableOk<>(Optional.empty());
    }

    @Override
    public ClosableResult<R> t() {
        return (ClosableResult<R>) t;
    }

    public boolean matches(Object m) {
        return t().filter(r -> m == r).isPresent();
    }

    public void doClose() {
        this.t().doClose();
    }

    public Exception getExcept() {

        if(this.t instanceof ClosableResult<R> c)
            return c.caught();

        return null;
    }

    public boolean isExcept(Predicate<Exception> exc) {
        return this.t instanceof ClosableResult<R> c && exc.test(c.caught());
    }

    public ClosableOk<R> except(Function<Exception, R> function) {
        if (this.t instanceof ClosableResult<R> c && c.caught() != null) {
            return new ClosableOk<>(function.apply(c.caught()));
        }

        return this;
    }

    public ClosableOk<R> except(Predicate<Exception> onExc, Function<Exception, R> function) {
        if (this.t instanceof ClosableResult<R> c && onExc.test(c.caught())) {
            return new ClosableOk<>(function.apply(c.caught()));
        }

        return this;
    }


    public <E> Optional<E> exceptErr(Function<Exception, E> function) {
        if (this.t instanceof ClosableResult<R> c && c.caught() != null) {
            return Optional.ofNullable(function.apply(c.caught()));
        }

        return Optional.empty();
    }

    public R getClosableQuietly() {
        if (this.t instanceof ClosableResult<R> res) {
            return res.getClosableQuietly();
        }

        return this.t.get();
    }
}
