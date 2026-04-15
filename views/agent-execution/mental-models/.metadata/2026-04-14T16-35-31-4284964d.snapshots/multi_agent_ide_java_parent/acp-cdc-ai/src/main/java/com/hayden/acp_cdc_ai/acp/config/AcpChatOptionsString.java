package com.hayden.acp_cdc_ai.acp.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;

import java.util.LinkedHashMap;
import java.util.Map;

public record AcpChatOptionsString(
        String version,
        String sessionArtifactKey,
        String requestedModel,
        String requestedProvider,
        Map<String, Object> options
) {

    public static final String CURRENT_VERSION = "1";
    public static final String MODEL_DELIMITER = "___";
    public static final String DEFAULT_MODEL_NAME = "DEFAULT";

    public AcpChatOptionsString {
        version = isBlank(version) ? CURRENT_VERSION : version;
        sessionArtifactKey = isBlank(sessionArtifactKey) ? ArtifactKey.createRoot().value() : sessionArtifactKey;
        options = options == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(options));
    }

    public static AcpChatOptionsString create(
            String sessionArtifactKey,
            String requestedModel,
            String requestedProvider,
            Map<String, Object> options
    ) {
        return new AcpChatOptionsString(CURRENT_VERSION, sessionArtifactKey, requestedModel, requestedProvider, options);
    }

    public String encodeModel(ObjectMapper objectMapper) {
        try {
            return sessionArtifactKey + MODEL_DELIMITER + objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode ACP chat options", e);
        }
    }

    public static AcpChatOptionsString fromEncodedModel(String encodedModel, ObjectMapper objectMapper) {
        if (isBlank(encodedModel)) {
            return create(ArtifactKey.createRoot().value(), null, null, Map.of());
        }

        String[] parts = encodedModel.split(MODEL_DELIMITER, 2);
        String leadingSessionKey = parts[0];
        if (parts.length == 1 || isBlank(parts[1])) {
            return create(leadingSessionKey, null, null, Map.of());
        }

        try {
            AcpChatOptionsString decoded = objectMapper.readValue(parts[1], AcpChatOptionsString.class);
            if (!leadingSessionKey.equals(decoded.sessionArtifactKey())) {
                throw new IllegalArgumentException("ACP chat options session key does not match transport prefix");
            }
            return decoded;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to decode ACP chat options payload", e);
        }
    }

    public static boolean looksLikeEncodedModel(String encodedModel) {
        return encodedModel != null && encodedModel.contains(MODEL_DELIMITER);
    }

    public ArtifactKey artifactKey() {
        return new ArtifactKey(sessionArtifactKey);
    }

    public String getVersion() {
        return version;
    }

    public String getSessionArtifactKey() {
        return sessionArtifactKey;
    }

    public String getRequestedModel() {
        return requestedModel;
    }

    public String getRequestedProvider() {
        return requestedProvider;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
