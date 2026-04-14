package com.hayden.utilitymodule.cast;

import com.hayden.utilitymodule.stream.StreamUtil;
import org.apache.commons.lang3.ClassUtils;

import java.util.Collection;
import java.util.List;

public class UtilityClassUtils {

    public static Object safeCast(Class<?> clazz, String value) {
        if (clazz.equals(int.class) || clazz.equals(Integer.class)) {
            return clazz.cast(Integer.parseInt(value));
        } else if (clazz.equals(long.class) || clazz.equals(Long.class)) {
            return clazz.cast(Long.parseLong(value));
        } else if (clazz.equals(double.class) || clazz.equals(Double.class)) {
            return clazz.cast(Double.parseDouble(value));
        } else if (clazz.equals(float.class) || clazz.equals(Float.class)) {
            return clazz.cast(Float.parseFloat(value));
        } else if (clazz.equals(char.class) || clazz.equals(Character.class)) {
            return clazz.cast(value.charAt(0));
        } else if (clazz.equals(boolean.class) || clazz.equals(Boolean.class)) {
            return clazz.cast(Boolean.parseBoolean(value));
        } else if (clazz.equals(byte.class) || clazz.equals(Byte.class)) {
            return clazz.cast(Byte.parseByte(value));
        } else if (clazz.equals(short.class) || clazz.equals(Short.class)) {
            return clazz.cast(Short.parseShort(value));
        } else if (clazz.equals(long[].class)) {
            return clazz.cast(StreamUtil.toStream(value.split(",")).map(Long::parseLong).toArray(Long[]::new));
        } else if (clazz.equals(int[].class)) {
            return clazz.cast(StreamUtil.toStream(value.split(",")).map(Integer::parseInt).toArray(Integer[]::new));
        } else if (clazz.equals(double[].class)) {
            return clazz.cast(StreamUtil.toStream(value.split(",")).map(Double::parseDouble).toArray(Double[]::new));
        } else if (clazz.equals(float[].class)) {
            return clazz.cast(StreamUtil.toStream(value.split(",")).map(Float::parseFloat).toArray(Float[]::new));
        } else if (clazz.equals(byte[].class)) {
            return clazz.cast(value.getBytes());
        }

        // Add more cases as needed
        throw new IllegalArgumentException("Unsupported type");
    }

    public static String parseTypeName(Class<?> clazz) {
        if (String.class.equals(clazz)) {
            return "string";
        } else if (ClassUtils.isPrimitiveOrWrapper(clazz)) {
            if (Number.class.isAssignableFrom(clazz) ||
                clazz.equals(int.class) || clazz.equals(double.class) ||
                clazz.equals(long.class) || clazz.equals(float.class) ||
                clazz.equals(short.class) || clazz.equals(byte.class)) {
                return "number";
            } else {
                return "string";
            }
        } else if (clazz.isArray() || Collection.class.isAssignableFrom(clazz)) {
            return "array";
        } else {
            return "object";
        }
    }
}
