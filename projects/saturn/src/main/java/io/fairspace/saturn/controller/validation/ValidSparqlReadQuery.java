package io.fairspace.saturn.controller.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SparqlReadQueryValidator.class)
public @interface ValidSparqlReadQuery {

    String message() default "Invalid SPARQL query. Only SELECT, ASK, CONSTRUCT, and DESCRIBE queries are allowed.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
