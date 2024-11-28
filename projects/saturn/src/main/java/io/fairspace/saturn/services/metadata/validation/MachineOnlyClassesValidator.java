package io.fairspace.saturn.services.metadata.validation;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.rdf.ModelUtils.getBooleanProperty;

@Component
public class MachineOnlyClassesValidator extends VocabularyAwareValidator {
    public MachineOnlyClassesValidator(@Qualifier("vocabulary") Model vocabulary) {
        super(vocabulary);
    }

    @Override
    public void validate(Model before, Model after, Model removed, Model added, ViolationHandler violationHandler) {
        vocabulary
                .listSubjectsWithProperty(RDF.type, SHACLM.NodeShape)
                .filterKeep(shape -> getBooleanProperty(shape, FS.machineOnly))
                .forEachRemaining(moc -> {
                    added.listStatements(null, RDF.type, moc)
                            .forEachRemaining(statement ->
                                    violationHandler.onViolation("Trying to create a machine-only entity", statement));
                    removed.listStatements(null, RDF.type, moc)
                            .forEachRemaining(statement -> violationHandler.onViolation(
                                    "Trying to change type of a machine-only entity", statement));
                });
    }
}
