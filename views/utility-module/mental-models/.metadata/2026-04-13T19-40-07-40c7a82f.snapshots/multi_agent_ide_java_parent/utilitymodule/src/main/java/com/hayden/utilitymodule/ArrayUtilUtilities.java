package com.hayden.utilitymodule;

//import com.squareup.javapoet.MethodSpec;
//import com.squareup.javapoet.TypeSpec;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;

//import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


public class ArrayUtilUtilities {

    private ArrayUtilUtilities() {}

    public static <T> Stream<T> fromArray(@Nullable T[] array) {
        return Optional.ofNullable(array).stream().flatMap(Arrays::stream);
    }

    public static void assertFloatArrayEquals(float[] one, float[] two) {
//        assertThat(Arrays.equals(one, two)).isTrue();
    }

    public static void assertArrayEquals(Object[] one, Object[] two) {
//        assertThat(Arrays.equals(one, two)).isTrue();
    }

    public static double[][] toPrimitive(Double[][] arr) {
        return Arrays.stream(arr)
                .map(ArrayUtils::toPrimitive)
                .toArray(double[][]::new);
    }

    public static double[] toPrimitive(Double[] arr) {
        return ArrayUtils.toPrimitive(arr);
    }

    public static long[][] toPrimitive(Long[][] arr) {
        return Arrays.stream(arr)
                .map(ArrayUtils::toPrimitive)
                .toArray(long[][]::new);
    }

    public static double[][][][] fromDoubleCollection(List<List<double[][]>> lst) {
        double[][][][] arr = new double[lst.size()][lst.stream().map(List::size).max(Integer::compareTo).orElse(0)][][];
        for (int i=0; i<lst.size(); ++i) {
            for (int j=0; j<lst.get(0).size(); ++j) {
                arr[i][j] = lst.get(i).get(j);
            }
        }
        return arr;
    }

    public static float[][] toPrimitive(Float[][] arr) {
        return Arrays.stream(arr)
                .map(ArrayUtils::toPrimitive)
                .toArray(float[][]::new);
    }

    public static Double[][] fromPrimitive(double[][] arr) {
        return Arrays.stream(arr)
                .map(d -> Arrays.stream(d).boxed().toArray(Double[]::new))
                .toArray(Double[][]::new);
    }

    public static Long[][] fromPrimitive(long[][] arr) {
        return Arrays.stream(arr)
                .map(d -> Arrays.stream(d).boxed().toArray(Long[]::new))
                .toArray(Long[][]::new);
    }

    public static Float[][] fromPrimitive(float[][] arr) {
        return Arrays.stream(arr)
                .map(d -> {
                    Float[] f = new Float[d.length];
                    for (int i=0; i<d.length; ++i) {
                        f[i] = d[i];
                    }
                    return f;
                })
                .toArray(Float[][]::new);
    }

    public static <T> T[] toArray(Collection<T> path, IntFunction<T[]> arrayFactory) {
        return Optional.ofNullable(path)
                .stream().flatMap(Collection::stream)
                .toArray(arrayFactory);
    }
}
