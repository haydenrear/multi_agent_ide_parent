package com.hayden.acp_cdc_ai.acp.config;

public record AcpSessionRoutingKey(
        String sessionArtifactKey,
        AcpProvider providerName,
        String effectiveModel,
        String optionsFingerprint
) {

    public static AcpSessionRoutingKey from(AcpResolvedCall resolvedCall, String optionsFingerprint) {
        return new AcpSessionRoutingKey(
                resolvedCall.sessionArtifactKey(),
                resolvedCall.providerName(),
                resolvedCall.effectiveModel(),
                optionsFingerprint == null ? "" : optionsFingerprint
        );
    }

    public String getSessionArtifactKey() {
        return sessionArtifactKey;
    }

    public String getEffectiveModel() {
        return effectiveModel;
    }

    public String getOptionsFingerprint() {
        return optionsFingerprint;
    }
}
