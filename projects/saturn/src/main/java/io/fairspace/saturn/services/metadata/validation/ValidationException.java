package io.fairspace.saturn.services.metadata.validation;

import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ValidationException extends RuntimeException {
    private final Set<Violation> violations;

    @Override
    public String getMessage() {
        if (violations == null || violations.isEmpty()) {
            return "Validation failed with no specific violations.";
        }
        return "Validation failed with the following violations: "
                + violations.stream().map(Violation::toString).collect(Collectors.joining(", "));
    }
}
