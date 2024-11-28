package io.fairspace.saturn.controller.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IriValidator.class)
public @interface ValidIri {

    String message() default "Invalid IRI: %s";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
