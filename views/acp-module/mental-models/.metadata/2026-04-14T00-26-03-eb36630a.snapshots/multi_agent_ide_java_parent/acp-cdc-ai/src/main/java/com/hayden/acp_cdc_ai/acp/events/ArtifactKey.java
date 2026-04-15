package com.hayden.acp_cdc_ai.acp.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Hierarchical, time-sortable artifact identifier using ULIDs.
 * 
 * Format: ak:<ulid-segment>/<ulid-segment>/...
 * 
 * Properties:
 * - Prefix: ak:
 * - Segments: ULID values (26 chars, Crockford Base32)
 * - Separator: /
 * - Child keys start with parent key followed by /
 * - Lexicographically sortable by creation time
 */
@Slf4j
public record ArtifactKey(String value) implements Comparable<ArtifactKey> {

    public ArtifactKey() {
        this(null);
    }

    @JsonCreator
    public ArtifactKey(String value) {
        if (StringUtils.isBlank(value)) {
            this.value = null;
        } else {
            value = value.replaceAll("ak:", "");
            this.value = "ak:" + value;
            Objects.requireNonNull(this.value, "ArtifactKey value cannot be null");
            if (!isValid(this.value)) {
                throw new IllegalArgumentException("Invalid ArtifactKey format: " + value);
            }
        }
    }
    
    private static final String PREFIX = "ak:";
    private static final char SEPARATOR = '/';
    private static final int ULID_LENGTH = 26;
    
    // Crockford Base32 alphabet (excludes I, L, O, U to avoid confusion)
    private static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    
    // Pattern for validating ArtifactKey format
    private static final Pattern VALID_PATTERN = Pattern.compile(
            "^ak:[0-9A-HJKMNP-TV-Z]{26}(/[0-9A-HJKMNP-TV-Z]{26})*$"
    );
    

    /**
     * Creates a new root ArtifactKey with a fresh ULID.
     */
    public static ArtifactKey createRoot() {
        return new ArtifactKey(PREFIX + generateUlid());
    }
    
    /**
     * Creates a new root ArtifactKey with a specific timestamp.
     */
    public static ArtifactKey createRoot(Instant timestamp) {
        return new ArtifactKey(PREFIX + generateUlid(timestamp));
    }
    
    /**
     * Creates a child key under this parent.
     */

    @JsonIgnore
    public ArtifactKey createChild() {
        return new ArtifactKey(value + SEPARATOR + generateUlid());
    }
    
    /**
     * Creates a child key under this parent with a specific timestamp.
     */
    public ArtifactKey createChild(Instant timestamp) {
        return new ArtifactKey(value + SEPARATOR + generateUlid(timestamp));
    }
    
    /**
     * Returns the parent key, or empty if this is a root key.
     */

    @JsonIgnore
    public Optional<ArtifactKey> parent() {
        int lastSep = value.lastIndexOf(SEPARATOR);
        if (lastSep <= PREFIX.length()) {
            return Optional.empty();
        }
        return Optional.of(new ArtifactKey(value.substring(0, lastSep)));
    }
    
    /**
     * Returns true if this key is a descendant of the given ancestor.
     */
    public boolean isDescendantOf(ArtifactKey ancestor) {
        return value.startsWith(ancestor.value + SEPARATOR);
    }
    
    /**
     * Returns true if this key is a direct child of the given parent.
     */
    public boolean isChildOf(ArtifactKey parent) {
        if (!isDescendantOf(parent)) {
            return false;
        }
        String suffix = value.substring(parent.value.length() + 1);
        return !suffix.contains(String.valueOf(SEPARATOR));
    }
    
    /**
     * Returns the depth of this key (number of segments).
     */
    @JsonIgnore
    public int depth() {
        String withoutPrefix = value.substring(PREFIX.length());
        return (int) withoutPrefix.chars().filter(c -> c == SEPARATOR).count() + 1;
    }
    
    /**
     * Returns true if this is a root key (depth 1).
     */
    @JsonIgnore
    public boolean isRoot() {
        return depth() == 1;
    }
    
    /**
     * Extracts the timestamp from the last (most recent) ULID segment.
     */
    public Instant extractTimestamp() {
        String lastSegment = getLastSegment();
        return decodeUlidTimestamp(lastSegment);
    }
    
    /**
     * Extracts the timestamp from the root ULID segment.
     */

    @JsonIgnore
    public Instant extractRootTimestamp() {
        String rootSegment = value.substring(PREFIX.length(), PREFIX.length() + ULID_LENGTH);
        return decodeUlidTimestamp(rootSegment);
    }

    @JsonIgnore
    private String getLastSegment() {
        int lastSep = value.lastIndexOf(SEPARATOR);
        if (lastSep < 0) {
            return value.substring(PREFIX.length());
        }
        return value.substring(lastSep + 1);
    }
    
    @Override
    public String value() {
        return value;
    }
    
    @Override
    public int compareTo(ArtifactKey other) {
        return this.value.compareTo(other.value);
    }
    
    @Override
    @JsonIgnore
    public String toString() {
        return value;
    }
    
    // ========== Validation ==========
    
    /**
     * Validates an ArtifactKey string format.
     */
    public static boolean isValid(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return VALID_PATTERN.matcher(value).matches();
    }
    
    // ========== ULID Generation ==========
    
    /**
     * Generates a ULID string with current timestamp.
     */
    private static String generateUlid() {
        return generateUlid(Instant.now());
    }
    
    /**
     * Generates a ULID string with specific timestamp.
     * 
     * ULID structure:
     * - First 10 chars: millisecond timestamp (48 bits)
     * - Last 16 chars: randomness (80 bits)
     */
    private static String generateUlid(Instant timestamp) {
        long time = timestamp.toEpochMilli();
        StringBuilder sb = new StringBuilder(ULID_LENGTH);
        
        // Encode timestamp (10 characters, 48 bits)
        encodeTimestamp(sb, time);
        
        // Encode randomness (16 characters, 80 bits)
        encodeRandomness(sb);
        
        return sb.toString();
    }
    
    private static void encodeTimestamp(StringBuilder sb, long timestamp) {
        // 48 bits = 10 base32 characters (5 bits each)
        char[] chars = new char[10];
        for (int i = 9; i >= 0; i--) {
            chars[i] = CROCKFORD_ALPHABET.charAt((int) (timestamp & 0x1F));
            timestamp >>>= 5;
        }
        sb.append(chars);
    }
    
    private static void encodeRandomness(StringBuilder sb) {
        // Generate 80 bits of randomness using UUID
        UUID uuid = UUID.randomUUID();
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        
        // Use 80 bits (16 base32 chars)
        // First 8 chars from MSB, last 8 from LSB
        for (int i = 0; i < 8; i++) {
            int shift = (7 - i) * 5;
            if (shift < 64) {
                sb.append(CROCKFORD_ALPHABET.charAt((int) ((msb >>> shift) & 0x1F)));
            }
        }
        for (int i = 0; i < 8; i++) {
            int shift = (7 - i) * 5;
            if (shift < 64) {
                sb.append(CROCKFORD_ALPHABET.charAt((int) ((lsb >>> shift) & 0x1F)));
            }
        }
    }
    
    private static Instant decodeUlidTimestamp(String ulid) {
        if (ulid.length() < 10) {
            throw new IllegalArgumentException("Invalid ULID: " + ulid);
        }
        
        long timestamp = 0;
        for (int i = 0; i < 10; i++) {
            char c = ulid.charAt(i);
            int val = CROCKFORD_ALPHABET.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid ULID character: " + c);
            }
            timestamp = (timestamp << 5) | val;
        }
        return Instant.ofEpochMilli(timestamp);
    }

    @JsonIgnore
    public ArtifactKey root() {
        var firstSep = value.indexOf(SEPARATOR);
        return new ArtifactKey(value.substring(0, firstSep));
    }
}
