package io.fairspace.saturn.controller.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import static org.apache.jena.riot.system.Checker.checkIRI;

/**
 * Validates that a given IRI is valid.
 */
@Slf4j
public class IriValidator implements ConstraintValidator<ValidIri, String> {
    @Override
    public boolean isValid(String subject, ConstraintValidatorContext context) {
        try {
            var isValid = subject == null || checkIRI(subject);
            if (!isValid) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                String.format(context.getDefaultConstraintMessageTemplate(), subject))
                        .addConstraintViolation();
            }
            return isValid;
        } catch (Exception e) {
            log.error("Error validating IRI", e);
            return false;
        }
    }
}
