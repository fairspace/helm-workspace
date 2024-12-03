package io.fairspace.saturn.mapper;

import lombok.Data;

@Data
public class ViewRelation {
    private String parentViewType;
    private String parentViewName;
    private String childViewType;
    private String childViewName;
    private Boolean selfReference;

    public Boolean isSelfReference() {
        return selfReference;
    }
}
