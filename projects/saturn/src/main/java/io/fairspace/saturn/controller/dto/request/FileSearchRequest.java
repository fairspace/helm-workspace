package io.fairspace.saturn.controller.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileSearchRequest extends SearchRequest {
    private String parentIRI;
}
