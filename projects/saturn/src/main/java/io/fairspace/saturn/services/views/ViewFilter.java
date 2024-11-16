package io.fairspace.saturn.services.views;

import java.util.*;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewFilter {
    /**
     * Field name of the shape `${view}_${attribute}`.
     */
    @NotBlank
    String field;

    @JsonIgnore
    String view;

    @JsonIgnore
    String attribute;

    List<Object> values;
    Object min;
    Object max;
    Boolean booleanValue;
    Boolean numericValue;
    String prefix;
    /**
     * Used internally for filtering on resource location.
     */
    @JsonIgnore
    List<String> prefixes;

    public void setField(@NotBlank String field) {
        this.field = field;
        String[] fields =
                Arrays.stream(field.split("_")).map(String::toLowerCase).toArray(String[]::new);
        this.view = fields[0];
        // For free text searches on names of Views, no attribute is specified in the filter.field variable.
        // Here we default to "viewName" in these situations.
        this.attribute = fields.length == 2 ? fields[1] : "viewName";
    }
}
