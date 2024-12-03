package io.fairspace.saturn.mapper;

import lombok.Data;

@Data
public abstract class AbstractViewAttribute {
    private String viewType;
    private String viewName;
    private String attributeName;
}
