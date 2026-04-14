package com.hayden.multiagentide.transformation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record TransformerRegistrationResponse(boolean ok, String registrationId, String status, String message) {
}
