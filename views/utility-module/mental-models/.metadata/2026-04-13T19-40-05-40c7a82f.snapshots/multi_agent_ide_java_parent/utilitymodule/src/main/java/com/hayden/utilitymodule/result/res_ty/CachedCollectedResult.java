package com.hayden.utilitymodule.result.res_ty;

import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachingOperations;
import lombok.experimental.Delegate;

import java.util.Collection;
import java.util.List;

public record CachedCollectedResult<T, E>(Collection<CachingOperations.StreamCacheResult> res,
                                          @Delegate List<T> results,
                                          Collection<CachingOperations.StreamCacheResult> errs,
                                          List<E> errsList,
                                          Collection<CachingOperations.StreamCacheResult> resultCache) implements List<T> { }