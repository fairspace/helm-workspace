package io.fairspace.saturn.controller.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.*;

@Value
@Builder
public class ViewPageDto {
    /**
     * The key of every row is `${view}_${column}`.
     */
    @NonNull
    List<Map<String, Set<ValueDto>>> rows;

    boolean hasNext;
    boolean timeout;
    Long totalCount;
    Long totalPages;
}
