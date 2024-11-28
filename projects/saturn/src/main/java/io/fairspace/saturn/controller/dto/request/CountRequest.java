package io.fairspace.saturn.controller.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import io.fairspace.saturn.services.views.ViewFilter;

@Data
public class CountRequest {
    @NotBlank
    private String view;

    private List<ViewFilter> filters;
}
