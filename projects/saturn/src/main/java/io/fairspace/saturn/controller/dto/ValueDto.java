package io.fairspace.saturn.controller.dto;

public record ValueDto(String label, Object value) implements Comparable<ValueDto> {
    @Override
    public int compareTo(ValueDto o) {
        return label.compareTo(o.label);
    }
}
