package io.fairspace.saturn.controller.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.Syntax;

/**
 * Validates that a given SPARQL query is a read-only query.
 */
@Slf4j
public class SparqlReadQueryValidator implements ConstraintValidator<ValidSparqlReadQuery, String> {
    @Override
    public boolean isValid(String sparqlQuery, ConstraintValidatorContext constraintValidatorContext) {
        try {
            Query query = QueryFactory.create(sparqlQuery, Syntax.syntaxARQ);
            return query.isSelectType() || query.isAskType() || query.isConstructType() || query.isDescribeType();
        } catch (Exception e) {
            log.error("Error validating SPARQL query", e);
            return false;
        }
    }
}
