package io.fairspace.saturn.mapper;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class ViewAttributeDate extends AbstractViewAttribute {
    private Date value;
}
