package com.hayden.utilitymodule.result.map;

import com.hayden.utilitymodule.Either;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.ok.Ok;
import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.res_ty.IResultItem;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class StreamResultCollector<R, ERR>
        implements Collector<Either<Ok<R>, Err<ERR>>, Result<List<R>, List<ERR>>, Result<List<R>, List<ERR>>> {

    @Override
    public Supplier<Result<List<R>, List<ERR>>> supplier() {
        return () -> Result.from(new ArrayList<>(), new ArrayList<>());
    }

    @Override
    public BiConsumer<Result<List<R>, List<ERR>>, Either<Ok<R>, Err<ERR>>> accumulator() {
        return (r, e) -> {
            Optional.ofNullable(e.getLeft())
                    .stream()
                    .flatMap(IResultItem::stream)
                    .filter(Objects::nonNull)
                    .forEach(o -> r.r().get().add(o));
            Optional.ofNullable(e.getRight())
                    .stream()
                    .flatMap(IResultItem::stream)
                    .filter(Objects::nonNull)
                    .forEach(o -> r.e().get().add(o));
        };
    }

    @Override
    public BinaryOperator<Result<List<R>, List<ERR>>> combiner() {
        return (f, s) -> {
            var fLst = f.r().get();
            var dLst = f.e().get();
            var sLst = s.r().get();
            var eLst = s.e().get();

            var newF = new ArrayList<>(fLst);
            newF.addAll(sLst);
            var newE = new ArrayList<>(dLst);
            newE.addAll(eLst);
            return Result.from(newF, newE);
        };
    }

    @Override
    public Function<Result<List<R>, List<ERR>>, Result<List<R>, List<ERR>>> finisher() {
        return f -> f;
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Set.of();
    }
}
