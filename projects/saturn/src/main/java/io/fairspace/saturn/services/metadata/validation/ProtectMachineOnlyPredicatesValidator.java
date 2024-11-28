package io.fairspace.saturn.services.metadata.validation;

import java.util.HashMap;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.rdf.ModelUtils.getBooleanProperty;
import static io.fairspace.saturn.rdf.ModelUtils.getResourceProperties;
import static io.fairspace.saturn.vocabulary.ShapeUtils.getClassShapeForClass;

import static java.util.stream.Collectors.toSet;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;

/**
 * This validator checks whether the requested action will modify any machine-only
 * predicates. If so, the request will not validate
 */
@Component
public class ProtectMachineOnlyPredicatesValidator extends VocabularyAwareValidator {

    public ProtectMachineOnlyPredicatesValidator(@Qualifier("vocabulary") Model vocabulary) {
        super(vocabulary);
    }

    @Override
    public void validate(Model before, Model after, Model removed, Model added, ViolationHandler violationHandler) {
        validateModelAgainstMachineOnlyPredicates(removed, before, vocabulary, violationHandler);
        validateModelAgainstMachineOnlyPredicates(added, after, vocabulary, violationHandler);
    }

    private void validateModelAgainstMachineOnlyPredicates(
            Model diff, Model target, Model vocabulary, ViolationHandler violationHandler) {
        var machineOnlyPropertiesByType = new HashMap<Resource, Set<Property>>();

        diff.listSubjects().forEachRemaining(resource -> {
            var type = resource.inModel(target).getPropertyResourceValue(RDF.type);
            if (type != null) {
                var props = machineOnlyPropertiesByType.computeIfAbsent(type, t -> getClassShapeForClass(t, vocabulary)
                        .map(classShape -> getResourceProperties(classShape, SHACLM.property).stream()
                                .filter(p -> getBooleanProperty(p, FS.machineOnly))
                                .map(p -> createProperty(
                                        p.getPropertyResourceValue(SHACLM.path).getURI()))
                                .collect(toSet()))
                        .orElse(Set.of()));

                resource.listProperties()
                        .filterKeep(stmt -> props.contains(stmt.getPredicate()))
                        .forEachRemaining(stmt -> violationHandler.onViolation(
                                "The given model contains a machine-only predicate", stmt));
            }
        });
    }
}
