package com.hayden.utilitymodule;

import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RandomUtils {

    enum RandomFound {
        Random(new Random());

        Random random;

        RandomFound(Random random) {
            this.random = random;
        }
    }

    public static int numberBetween(int starting, int ending) {
        return RandomFound.Random.random.ints(starting, ending)
                .findFirst()
                .orElseThrow();
    }

    public static String randomNumberString(int length) {
        return IntStream.range(0, length).boxed().map(i -> String.valueOf(numberBetween(0, 9)))
                .collect(Collectors.joining());
    }

}
