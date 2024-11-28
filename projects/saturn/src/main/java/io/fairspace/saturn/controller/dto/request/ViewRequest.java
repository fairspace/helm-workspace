package io.fairspace.saturn.controller.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ViewRequest extends CountRequest {
    @Min(1)
    private Integer page;

    @Min(1)
    private Integer size;

    private Boolean includeCounts;

    public boolean includeCounts() {
        return includeCounts != null && includeCounts;
    }

    private Boolean includeJoinedViews;

    public boolean includeJoinedViews() {
        return includeJoinedViews != null && includeJoinedViews;
    }
}
