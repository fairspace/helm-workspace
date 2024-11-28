package io.fairspace.saturn.services.metadata.validation;

import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.sparql.path.Path;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static org.apache.jena.shacl.validation.ValidationProc.plainValidationNode;

@Component
public class ShaclValidator extends VocabularyAwareValidator {
    private final Shapes shapes;

    public ShaclValidator(@Qualifier("vocabulary") Model vocabulary) {
        super(vocabulary);
        shapes = Shapes.parse(vocabulary);
    }

    @Override
    public void validate(Model before, Model after, Model removed, Model added, ViolationHandler violationHandler) {
        var affected = removed.listSubjects()
                .filterKeep(s -> after.contains(s, null))
                .andThen(added.listSubjects())
                .mapWith(FrontsNode::asNode)
                .toSet();

        if (affected.isEmpty()) {
            return;
        }

        var data = after.getGraph();

        affected.forEach(
                node -> plainValidationNode(shapes, data, node).getEntries().forEach(entry -> {
                    if (entry.severity().level() == SHACL.Violation) {
                        violationHandler.onViolation(
                                entry.message(), entry.focusNode(), pathToNode(entry.resultPath()), entry.value());
                    }
                }));
    }

    private static Node pathToNode(Path path) {
        return (path instanceof P_Link) ? ((P_Link) path).getNode() : null;
    }
}
