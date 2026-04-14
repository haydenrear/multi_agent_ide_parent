package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PropagatorRegistrationResponse(boolean ok, String registrationId, String status, String message) {
}
