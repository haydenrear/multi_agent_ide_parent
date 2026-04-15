package com.hayden.acp_cdc_ai.acp.events;

import java.lang.reflect.Array;
import java.util.*;

public interface CanonicalJson {

    /**
     * Canonicalizes an arbitrary object graph for deterministic JSON serialization:
     * - Map -> TreeMap with lexicographically sorted string keys
     * - Iterable / arrays -> element-wise canonicalization (preserves order)
     * - Otherwise -> returned as-is
     *
     * If two distinct keys stringify to the same JSON field name, throws.
     */
    static Object canonicalize(Object input) {
        if (input == null) return null;

        if (input instanceof Map<?, ?> map) {
            return canonicalizeMap(map);
        }

        if (input instanceof Iterable<?> it) {
            List<Object> out = new ArrayList<>();
            for (Object e : it) out.add(canonicalize(e));
            return out;
        }

        if (input.getClass().isArray()) {
            int n = Array.getLength(input);
            List<Object> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(canonicalize(Array.get(input, i)));
            return out;
        }

        // You can add Optional, Stream, etc. handling here if desired.
        return input;
    }

    private static SortedMap<String, Object> canonicalizeMap(Map<?, ?> map) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        // Track original keys to detect collisions after String.valueOf
        Map<String, Object> originalKeyFor = new HashMap<>();

        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object rawKey = e.getKey();
            String key = String.valueOf(rawKey);

            Object prev = originalKeyFor.putIfAbsent(key, rawKey);
            if (prev != null && !Objects.equals(prev, rawKey)) {
                // Two different keys become the same JSON field name — ambiguous.
                throw new IllegalStateException(
                        "Key collision after stringification: '" + key + "' from keys " + prev + " and " + rawKey
                );
            }

            Object value = canonicalize(e.getValue());
            sorted.put(key, value);
        }
        return sorted;
    }
}
