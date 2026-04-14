package com.hayden.multiagentide.transformation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record DeactivateTransformerResponse(boolean ok, String registrationId, String status, String message) {
}
