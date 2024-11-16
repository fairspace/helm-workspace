package io.fairspace.saturn.mapper;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ViewAttributeString  extends AbstractViewAttribute {
    private String value;
}
