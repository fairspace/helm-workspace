package io.fairspace.saturn.mapper;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ViewAttributeInt extends AbstractViewAttribute {
    private Integer value;
}
