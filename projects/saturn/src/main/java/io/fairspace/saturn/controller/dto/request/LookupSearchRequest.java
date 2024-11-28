package io.fairspace.saturn.controller.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LookupSearchRequest extends SearchRequest {
    private String resourceType;
}
