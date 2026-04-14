package com.hayden.utilitymodule.reflection;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class TypeReferenceDelegate<T> {
    @Delegate
    private final Class<T> underlying;
    @Delegate
    private final ParameterizedTypeReference<T> typeReference;

    public Optional<T> create(Object ... args) {
        try {
            return Optional.of(
                    underlying.getConstructor((Class<?>[]) Arrays.stream(args)
                                    .map(Object::getClass).toArray()
                            )
                            .newInstance(args)
            );
        } catch (InstantiationException |
                 IllegalAccessException |
                 InvocationTargetException |
                 NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    public static <T> Optional<TypeReferenceDelegate<T>> create(Class<?> clzz) {
        try {
            return Optional.of(new TypeReferenceDelegate<T>((Class<T>) clzz, new ParameterizedTypeReference<T>() {}));
        } catch (ClassCastException c) {
            log.error("Error attempting to create type reference delegate for {} with error {}.", clzz.getSimpleName(), c.getMessage());
            return Optional.empty();
        }
    }

    @Delegate
    public Class<T> underlying() {
        return underlying;
    }

    @Delegate
    public ParameterizedTypeReference<T> typeReference() {
        return typeReference;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (TypeReferenceDelegate) obj;
        return Objects.equals(this.underlying, that.underlying) &&
                Objects.equals(this.typeReference, that.typeReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(underlying, typeReference);
    }

    @Override
    public String toString() {
        return "TypeReferenceDelegate[" +
                "underlying=" + underlying + ", " +
                "typeReference=" + typeReference + ']';
    }

}
