package com.hayden.utilitymodule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Either<T,V> {

    @Nullable
    T left;
    @Nullable
    V right;

    public static <T,V> Either<T,V> from(@Nullable T first, @Nullable V second) {
        return new Either<>(first, second);
    }

}
