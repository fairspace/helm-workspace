package io.fairspace.saturn.controller.dto;

import lombok.Builder;
import lombok.NonNull;

@Builder
public record SearchResultDto(@NonNull String id, String label, String type, String comment) {}
