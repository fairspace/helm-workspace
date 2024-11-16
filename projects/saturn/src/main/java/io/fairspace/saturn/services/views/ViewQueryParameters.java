package io.fairspace.saturn.services.views;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ViewQueryParameters {

    public enum AttributeType {
        STRING, NUMBER, DATE, BOOLEAN, UNCLASSIFIED
    }

    private String parentViewNamePattern;
    private Map<String, List<ViewFilter>> filterMap;

    public ViewQueryParameters(List<ViewFilter> filters) {

        // Isolate filter for parent view name.+
        Optional<ViewFilter> parentNameFilter = filters.stream()
                .filter(f -> f.getAttribute().equals("viewName"))
                .findFirst();
        parentViewNamePattern = parentNameFilter.isPresent() ? parentNameFilter.get().getPrefix() : null;

        List<ViewFilter> attributeFilters = filters.stream()
                .filter(f -> !f.getAttribute().equals("viewName"))
                .collect(Collectors.toList());
        filterMap = attributeFilters.stream().collect(Collectors.groupingBy(
                filter -> {
                    if (filter.getValues() != null && filter.getValues().size() > 0) {
                        return AttributeType.STRING.toString();
                    }
                    if (filter.getBooleanValue() != null) {
                        return AttributeType.BOOLEAN.toString();
                    }
                    if (filter.getNumericValue() != null && !filter.getNumericValue()) {
                        return AttributeType.DATE.toString();
                    }
                    if (filter.getNumericValue() != null && filter.getNumericValue()) {
                        return AttributeType.NUMBER.toString();
                    }
                    return AttributeType.UNCLASSIFIED.toString();
                })
        );

        if (filterMap.containsKey(AttributeType.UNCLASSIFIED.toString())) {
            List<ViewFilter> unclassified = filterMap.get(AttributeType.UNCLASSIFIED.toString());
            throw new IllegalArgumentException("ViewFilter could not be classified as one of "+ AttributeType.values() +
                    ". Filters: " + unclassified.stream().map(ViewFilter::toString).collect(Collectors.joining("\n")));
        }

    }

    public Boolean hasParentNameFilter() {
        return parentViewNamePattern != null;
    }

    public Boolean hasAttributeFilters() {
        return !filterMap.isEmpty();
    }

    public Boolean hasStringFilters() {
        return filterMap.containsKey(AttributeType.STRING.toString());
    }

    public Boolean hasNumberFilters() {
        return filterMap.containsKey(AttributeType.NUMBER.toString());
    }

    public Boolean hasDateFilters() {
        return filterMap.containsKey(AttributeType.DATE.toString());
    }

    public Boolean hasBooleanFilters() {
        return filterMap.containsKey(AttributeType.BOOLEAN.toString());
    }

    public String getParentViewNamePattern() {
        return parentViewNamePattern;
    }

    public List<ViewFilter> getStringFilters() {
        return filterMap.get(AttributeType.STRING.toString());
    }

    public List<ViewFilter> getNumberFilters() {
        return filterMap.get(AttributeType.NUMBER.toString());
    }

    public List<ViewFilter> getDateFilters() {
        return filterMap.get(AttributeType.DATE.toString());
    }

    public List<ViewFilter> getBooleanFilters() {
        return filterMap.get(AttributeType.BOOLEAN.toString());
    }

}
