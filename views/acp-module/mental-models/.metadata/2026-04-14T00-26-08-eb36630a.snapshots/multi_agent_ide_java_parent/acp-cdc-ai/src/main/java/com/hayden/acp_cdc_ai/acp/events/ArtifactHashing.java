package com.hayden.acp_cdc_ai.acp.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Content hashing utilities for artifacts.
 * 
 * Uses SHA-256 with lowercase hexadecimal encoding (64 characters).
 * 
 * Canonical JSON rules:
 * - Object keys sorted lexicographically (Unicode code point order)
 * - No insignificant whitespace (compact form)
 * - Numbers: no trailing zeros, no leading zeros except 0.x, no positive sign
 * - Strings: UTF-8 encoded, escape only required characters
 * - Explicit null values included, missing keys omitted
 * - Array ordering preserved
 */
public final class ArtifactHashing {
    
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final HexFormat HEX_FORMAT = HexFormat.of().withLowerCase();
    
    // ObjectMapper configured for canonical JSON output
    private static final ObjectMapper CANONICAL_MAPPER = createCanonicalMapper();
    
    private ArtifactHashing() {
        // Utility class
    }
    
    /**
     * Computes SHA-256 hash of UTF-8 text, returning lowercase hex.
     */
    public static String hashText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Cannot hash null text");
        }
        return hashBytes(text.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Computes SHA-256 hash of raw bytes, returning lowercase hex.
     */
    public static String hashBytes(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Cannot hash null bytes");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(bytes);
            return HEX_FORMAT.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Computes SHA-256 hash of an object serialized as canonical JSON.
     */
    public static String hashJson(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Cannot hash null object");
        }
        String canonicalJson = toCanonicalJson(obj);
        return hashText(canonicalJson);
    }
    
    /**
     * Computes SHA-256 hash of a Map serialized as canonical JSON.
     */
    public static String hashMap(Map<String, ?> map) {
        if (map == null) {
            throw new IllegalArgumentException("Cannot hash null map");
        }
        String canonicalJson = toCanonicalJson(map);
        return hashText(canonicalJson);
    }
    
    /**
     * Serializes an object to canonical JSON.
     * 
     * Canonical JSON properties:
     * - Keys sorted lexicographically
     * - Compact (no whitespace)
     * - Deterministic output for identical input
     */
    public static String toCanonicalJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            // For maps, ensure sorted keys
            return CANONICAL_MAPPER.writeValueAsString(CanonicalJson.canonicalize(obj));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to canonical JSON", e);
        }
    }
    
    /**
     * Parses canonical JSON to the specified type.
     */
    public static <T> T fromCanonicalJson(String json, Class<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return CANONICAL_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse canonical JSON", e);
        }
    }
    
    /**
     * Verifies that a content hash matches the expected value.
     */
    public static boolean verifyHash(String content, String expectedHash) {
        if (content == null || expectedHash == null) {
            return false;
        }
        String actualHash = hashText(content);
        return actualHash.equalsIgnoreCase(expectedHash);
    }
    
    /**
     * Verifies that a JSON object hash matches the expected value.
     */
    public static boolean verifyJsonHash(Object obj, String expectedHash) {
        if (obj == null || expectedHash == null) {
            return false;
        }
        String actualHash = hashJson(obj);
        return actualHash.equalsIgnoreCase(expectedHash);
    }
    
    // ========== Private Helpers ==========
    
    private static ObjectMapper createCanonicalMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Sort map keys for deterministic output
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        
        // Compact output (no pretty printing)
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        
        // Write dates as ISO-8601 strings
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        // Don't fail on empty beans
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        return mapper;
    }
    
}
