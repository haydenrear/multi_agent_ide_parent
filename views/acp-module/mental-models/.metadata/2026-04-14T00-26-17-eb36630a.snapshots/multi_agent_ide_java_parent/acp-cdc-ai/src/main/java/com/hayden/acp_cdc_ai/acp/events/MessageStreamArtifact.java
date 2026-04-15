package com.hayden.acp_cdc_ai.acp.events;

import lombok.Builder;
import lombok.With;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Captures token/stream deltas, thought deltas, and message fragments
 * emitted during model interaction.
 */
@Builder(toBuilder = true)
@With
public record MessageStreamArtifact(
        ArtifactKey artifactKey,
        StreamType streamType,
        String nodeId,
        Instant eventTimestamp,
        Map<String, Object> payloadJson,
        String hash,
        Map<String, String> metadata,
        List<Artifact> children
) implements Artifact {
    
    /**
     * Stream event types.
     */
    public enum StreamType {
        NODE_STREAM_DELTA,
        NODE_THOUGHT_DELTA,
        USER_MESSAGE_CHUNK,
        ADD_MESSAGE
    }

    
    @Override
    public Optional<String> contentHash() {
        return Optional.ofNullable(hash);
    }
}
