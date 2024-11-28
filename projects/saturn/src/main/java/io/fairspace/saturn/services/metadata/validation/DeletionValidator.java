package io.fairspace.saturn.services.metadata.validation;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.springframework.stereotype.Component;

import io.fairspace.saturn.vocabulary.FS;

@Component
public class DeletionValidator implements MetadataRequestValidator {
    @Override
    public void validate(Model before, Model after, Model removed, Model added, ViolationHandler violationHandler) {
        removed.listSubjects()
                .andThen(added.listSubjects())
                .filterKeep(Resource::isURIResource)
                .filterKeep(resource -> resource.inModel(before).hasProperty(FS.dateDeleted))
                .forEachRemaining(resource ->
                        violationHandler.onViolation("Cannot modify deleted resource", resource, null, null));
    }
}
