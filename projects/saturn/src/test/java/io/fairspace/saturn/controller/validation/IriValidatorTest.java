package io.fairspace.saturn.controller.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IriValidatorTest {

    @InjectMocks
    private IriValidator iriValidator;

    @Mock
    private ConstraintValidatorContext constraintValidatorContext;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder constraintViolationBuilder;

    @Test
    void testValidIri() {
        String validIri = "http://example.com/resource/123";

        // Test that a valid IRI returns true
        assertTrue(iriValidator.isValid(validIri, constraintValidatorContext));

        // No violations should be added for valid IRI
        verify(constraintValidatorContext, never()).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void testInvalidIri() {
        String invalidIri = " fd ";

        // Set up mocking behavior for invalid IRI case
        when(constraintValidatorContext.getDefaultConstraintMessageTemplate()).thenReturn("Invalid IRI: %s");
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(constraintViolationBuilder);

        // Test that an invalid IRI returns false
        assertFalse(iriValidator.isValid(invalidIri, constraintValidatorContext));

        // Verify that a violation was added
        verify(constraintValidatorContext).disableDefaultConstraintViolation();
        verify(constraintValidatorContext).buildConstraintViolationWithTemplate(anyString());
    }
}
