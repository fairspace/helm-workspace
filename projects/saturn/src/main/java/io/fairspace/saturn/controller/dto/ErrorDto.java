package io.fairspace.saturn.controller.dto;

import ioinformarics.oss.jackson.module.jsonld.annotation.JsonldProperty;
import ioinformarics.oss.jackson.module.jsonld.annotation.JsonldType;

import io.fairspace.saturn.vocabulary.FS;

@JsonldType(FS.ERROR_URI)
public record ErrorDto(
        @JsonldProperty(FS.ERROR_STATUS_URI) int status,
        @JsonldProperty(FS.ERROR_MESSAGE_URI) String message,
        @JsonldProperty(FS.ERROR_DETAILS_URI) Object details) {}
