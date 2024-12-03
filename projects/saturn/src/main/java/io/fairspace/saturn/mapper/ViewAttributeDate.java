package io.fairspace.saturn.mapper;

import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ViewAttributeDate extends AbstractViewAttribute {
    private Date value;
}
