package com.hayden.utilitymodule.stream;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Stream;

public class StreamUtil {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Slf4j
    public static class StreamBuilderDelegate<T> {

        @Delegate
        Stream.Builder<T> builder = Stream.builder();

        public static <T> StreamBuilderDelegate<T> builder() {
            return new StreamBuilderDelegate<T>();
        }

        public StreamBuilderDelegate<T> addAllStreams(Stream ... streams) {
            Arrays.stream(streams)
                    .flatMap(s -> s)
                    .forEach(t -> {
                        try {
                            builder.add((T) t);
                        } catch (ClassCastException c) {
                            log.error("Could not add builder in stream builder delegate: {}.", c.getMessage());
                            throw c;
                        }
                    });
            return this;
        }

        public StreamBuilderDelegate<T> add(T ... streams) {
            Arrays.stream(streams)
                   .filter(Objects::nonNull)
                  .forEach(builder::add);
            return this;
        }

        public StreamBuilderDelegate<T> addStream(Stream<T> streams) {
            streams.forEach(builder::add);
            return this;
        }

        public StreamBuilderDelegate<T> addAll(Stream<T> stream) {
            stream.forEach(builder::add);
            return this;
        }

    }

    public static <T> Stream<T> toStream(Stream<T> t) {
        return t == null ? Stream.empty() : t;
    }

    public static <K, V> Stream<Map.Entry<K, V>> toStream(Map<K, V> t) {
        return t == null ? Stream.empty() : t.entrySet().stream();
    }

    public static <T> Stream<T> toStream(T t) {
        return Optional.ofNullable(t).stream();
    }

    public static <T> Stream<T> toStream(Optional<T> t) {
        return t.isPresent() ? toStream(t.get()) : Stream.empty();
    }

    public static <T> Stream<T> toStream(Iterator<T> t) {
        return Lists.newArrayList(t).stream();
    }

    public static <T> Stream<T> toStream(IResultItem<T> t) {
        return Optional.ofNullable(t).stream()
                .flatMap(IResultItem::stream);
    }

    public static <T> Stream<T> toStream(T[] t) {
        return Optional.ofNullable(t)
                .stream()
                .flatMap(Arrays::stream)
                .filter(Objects::nonNull);
    }

    public static <T> Stream<T> toStream(Collection<T> t) {
        return Optional.ofNullable(t)
                .stream()
                .flatMap(Collection::stream);
    }
}
