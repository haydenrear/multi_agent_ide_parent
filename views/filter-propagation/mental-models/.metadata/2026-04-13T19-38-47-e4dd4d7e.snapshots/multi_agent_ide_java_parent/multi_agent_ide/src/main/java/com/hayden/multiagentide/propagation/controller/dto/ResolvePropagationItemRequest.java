package com.hayden.multiagentide.propagation.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolvePropagationItemRequest(@NotBlank String resolutionType, String resolutionNotes) {
}
