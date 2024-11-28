package io.fairspace.saturn.services.metadata;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Qualifier;

import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.AccessDeniedException;
import io.fairspace.saturn.services.metadata.validation.MetadataRequestValidator;
import io.fairspace.saturn.services.metadata.validation.ValidationException;
import io.fairspace.saturn.services.metadata.validation.Violation;
import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.audit.Audit.audit;
import static io.fairspace.saturn.auth.RequestContext.getUserURI;
import static io.fairspace.saturn.rdf.ModelUtils.EMPTY_MODEL;
import static io.fairspace.saturn.rdf.ModelUtils.trimLabels;
import static io.fairspace.saturn.rdf.ModelUtils.updatedView;
import static io.fairspace.saturn.rdf.SparqlUtils.toXSDDateTimeLiteral;
import static io.fairspace.saturn.services.users.UserService.currentUserAsSymbol;
import static io.fairspace.saturn.vocabulary.ShapeUtils.getPropertyShapesForResource;

import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;

public class MetadataService {
    private final Transactions transactions;
    private final Model vocabulary;
    private final Model systemVocabulary;
    private final MetadataRequestValidator validator;
    private final MetadataPermissions permissions;

    // The maximum number of items in a list in the right panel, for performance reasons.
    // If you change this, also change it in 'constants.js'
    private final int MAX_LIST_LENGTH = 100;

    public MetadataService(
            Transactions transactions,
            @Qualifier("vocabulary") Model vocabulary,
            Model systemVocabulary,
            MetadataRequestValidator validator,
            MetadataPermissions permissions) {
        this.transactions = transactions;
        this.vocabulary = vocabulary;
        this.systemVocabulary = systemVocabulary;
        this.validator = validator;
        this.permissions = permissions;
    }

    /**
     * Returns a model with statements from the metadata database, based on the given selection criteria
     * <p>
     * If any of the fields is null, that field is not included to filter statements. For example, if only
     * subject is given and predicate and object are null, then all statements with the given subject will be returned.
     *
     * @param subject             Subject URI for which you want to return statements
     * @param withValueProperties If set to true, the returned model will also include statements specifying values for
     *                            certain properties marked as fs:importantProperty in the vocabulary
     * @return
     */
    public Model get(String subject, boolean withValueProperties) {
        var returnValues = createDefaultModel();

        transactions.executeRead(fairspaceData -> {
            var resource = fairspaceData.createResource(subject);
            if (!permissions.canReadMetadata(resource)) {
                throw new AccessDeniedException(subject);
            }

            resource.listProperties().forEachRemaining(stmt -> {
                returnValues.add(stmt);
                if (withValueProperties && stmt.getObject().isURIResource()) {
                    addImportantProperties(stmt.getResource(), returnValues);
                }
            });

            getPropertyShapesForResource(resource, vocabulary).stream()
                    .map(ps -> ps.getProperty(SHACLM.path).getResource().getPropertyResourceValue(SHACLM.inversePath))
                    .filter(Objects::nonNull)
                    .map(p -> p.as(Property.class))
                    .forEach(property ->
                            addStatements(fairspaceData, returnValues, property, resource, withValueProperties));
        });
        return returnValues;
    }

    private void addStatements(
            Model fairspaceData,
            Model returnValues,
            Property property,
            Resource resource,
            boolean withValueProperties) {
        ExtendedIterator<Statement> statements = fairspaceData
                .listStatements(null, property, resource)
                .filterKeep(stmt -> permissions.canReadMetadata(stmt.getSubject()))
                .filterDrop(stmt -> stmt.getSubject().hasProperty(FS.dateDeleted));

        int numberOfStatementsAdded = 0;

        while (statements.hasNext()) {
            Statement stmt = (Statement) statements.next();

            returnValues.add(stmt);
            if (withValueProperties) {
                addImportantProperties(stmt.getSubject(), returnValues);
            }

            if (numberOfStatementsAdded++ == MAX_LIST_LENGTH) {
                break;
            }
        }
    }

    private void addImportantProperties(Resource s, Model dest) {
        getPropertyShapesForResource(s, vocabulary).forEach(shape -> {
            if (shape.hasLiteral(FS.importantProperty, true)) {
                var property = shape.getPropertyResourceValue(SHACLM.path).as(Property.class);
                s.listProperties(property).forEachRemaining(dest::add);
            }
        });
    }

    /**
     * Adds all the statements in the given model to the database
     * <p>
     * If the given model contains any statements for which the predicate is marked as machineOnly,
     * an IllegalArgumentException will be thrown
     *
     * @param model
     */
    public void put(Model model, Boolean doViewsUpdate) {
        logUpdates(update(EMPTY_MODEL, model, doViewsUpdate));
    }

    /**
     * Marks an entity as deleted
     *
     * @param subject Subject URI to mark as deleted
     */
    public boolean softDelete(Resource subject) {
        var success = transactions.calculateWrite(model -> {
            var resource = subject.inModel(model);
            if (!permissions.canWriteMetadata(resource)) {
                throw new AccessDeniedException(resource.getURI());
            }
            var machineOnly = resource.listProperties(RDF.type)
                    .mapWith(Statement::getObject)
                    .filterKeep(systemVocabulary::containsResource)
                    .hasNext();

            if (machineOnly) {
                throw new IllegalArgumentException("Cannot mark as deleted machine-only entity " + resource);
            }
            if (resource.getModel().containsResource(resource) && !resource.hasProperty(FS.dateDeleted)) {
                resource.addLiteral(FS.dateDeleted, toXSDDateTimeLiteral(Instant.now()));
                resource.addProperty(FS.deletedBy, model.wrapAsResource(getUserURI()));
                return true;
            }
            return false;
        });

        if (success) {
            audit("METADATA_MARKED_AS_DELETED", "iri", subject.getURI());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Deletes the statements in the given model from the database.
     * <p>
     * If the model contains any statements for which the predicate is marked as 'machineOnly', an IllegalArgumentException will be thrown.
     *
     * @param model
     */
    public void delete(Model model, Boolean doViewsUpdate) {
        logDeleted(update(model, EMPTY_MODEL, doViewsUpdate));
    }

    /**
     * Overwrites metadata in the database with statements from the given model.
     * <p>
     * For any subject/predicate combination in the model to add, the existing data in the database will be removed,
     * before adding the new data. This means that if the given model contains a triple
     * S rdfs:label "test"
     * then any statement in the database specifying the rdfs:label for S will be deleted. This effectively overwrites
     * values in the database.
     * <p>
     * If the given model contains any statements for which the predicate is marked as machineOnly,
     * an IllegalArgumentException will be thrown
     *
     * @param model
     */
    public void patch(Model model, Boolean doViewsUpdate) {
        logUpdates(transactions.calculateWrite(before -> {
            var existing = createDefaultModel();
            model.listStatements()
                    .filterKeep(stmt -> stmt.getSubject().isURIResource())
                    .mapWith(stmt -> Pair.of(stmt.getSubject(), stmt.getPredicate()))
                    .toSet()
                    .forEach(pair ->
                            existing.add(before.listStatements(pair.getKey(), pair.getValue(), (RDFNode) null)));

            return update(
                    existing.difference(model), model.remove(existing).removeAll(null, null, FS.nil), doViewsUpdate);
        }));
    }

    private Set<Resource> update(Model modelToRemove, Model modelToAdd, Boolean doViewsUpdate) {
        var symbol = currentUserAsSymbol();
        transactions.setContextValue(
                symbol, doViewsUpdate); // to be read downstream when Jena's transaction is being committed
        return transactions.calculateWrite(before -> {
            trimLabels(modelToAdd);
            var after = updatedView(before, modelToRemove, modelToAdd);

            validate(before, after, modelToRemove, modelToAdd);

            persist(modelToRemove, modelToAdd);

            return modelToRemove
                    .listSubjects()
                    .andThen(modelToAdd.listSubjects())
                    .toSet();
        });
    }

    private void logDeleted(Set<Resource> updatedResources) {
        updatedResources.forEach(resource -> audit("METADATA_DELETED", "iri", resource.getURI()));
    }

    private void logUpdates(Set<Resource> updatedResources) {
        updatedResources.forEach(resource -> audit("METADATA_UPDATED", "iri", resource.getURI()));
    }

    private void validate(Model before, Model after, Model modelToRemove, Model modelToAdd) {
        modelToAdd
                .listSubjects()
                .andThen(modelToRemove.listSubjects())
                .filterDrop(s -> permissions.canWriteMetadata(s.inModel(before)))
                .forEachRemaining(s -> {
                    throw new AccessDeniedException(s.getURI());
                });

        var violations = new LinkedHashSet<Violation>();
        validator.validate(
                before,
                after,
                modelToRemove,
                modelToAdd,
                (message, subject, predicate, object) -> violations.add(new Violation(
                        message,
                        subject.toString(),
                        Objects.toString(predicate, null),
                        Objects.toString(object, null))));

        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    private void persist(Model modelToRemove, Model modelToAdd) {
        transactions.executeWrite(model -> {
            var created = modelToAdd
                    .listSubjects()
                    .filterKeep(RDFNode::isURIResource)
                    .filterDrop(
                            s -> model.listStatements(s, null, (RDFNode) null).hasNext())
                    .toSet();

            model.remove(modelToRemove).add(modelToAdd);

            var user = model.wrapAsResource(getUserURI());
            var now = toXSDDateTimeLiteral(Instant.now());

            created.forEach(s -> model.add(s, FS.createdBy, user).add(s, FS.dateCreated, now));

            modelToAdd
                    .listSubjects()
                    .andThen(modelToRemove.listSubjects())
                    .filterKeep(RDFNode::isURIResource)
                    .filterKeep(
                            s -> model.listStatements(s, null, (RDFNode) null).hasNext())
                    .forEachRemaining(s -> model.removeAll(s, FS.modifiedBy, null)
                            .removeAll(s, FS.dateModified, null)
                            .add(s, FS.modifiedBy, user)
                            .add(s, FS.dateModified, now));
        });
    }
}
