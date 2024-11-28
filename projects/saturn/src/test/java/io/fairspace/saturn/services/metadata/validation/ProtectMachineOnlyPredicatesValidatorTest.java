package io.fairspace.saturn.services.metadata.validation;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.rdf.ModelUtils.EMPTY_MODEL;
import static io.fairspace.saturn.rdf.ModelUtils.modelOf;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.apache.jena.rdf.model.ResourceFactory.createStatement;
import static org.apache.jena.riot.RDFDataMgr.loadModel;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@RunWith(MockitoJUnitRunner.class)
public class ProtectMachineOnlyPredicatesValidatorTest {
    private static final Property MACHINE_ONLY_PROPERTY = FS.createdBy;
    private static final Resource S1 = createResource("http://localhost/iri/S1");
    private static final Resource S2 = createResource("http://localhost/iri/S2");
    private static final Resource S3 = createResource("http://localhost/iri/S3");
    private static final Property P1 = createProperty("https://fairspace.nl/ontology/P1");
    private static final Property P2 = createProperty("https://fairspace.nl/ontology/P2");

    private final ProtectMachineOnlyPredicatesValidator validator =
            new ProtectMachineOnlyPredicatesValidator(loadModel("system-vocabulary.ttl"));

    @Mock
    private ViolationHandler violationHandler;

    @Test
    public void testContainsMachineOnlyPredicates() {

        var testModel = modelOf(
                // A machine-only property may be used as subject or object
                P1,
                RDF.type,
                RDF.Property,
                MACHINE_ONLY_PROPERTY,
                RDF.value,
                P1,

                // Other statements are allowed as well
                S1,
                P2,
                S2,
                S2,
                P2,
                S1);

        validator.validate(EMPTY_MODEL, testModel, testModel, EMPTY_MODEL, violationHandler);
        verifyNoInteractions(violationHandler);
    }

    @Test
    public void testHasMachineOnlyPredicatesRecognizesMachineOnlyStatements() {
        // Create a model that contains one machine only statement between several non-machine-only
        var testModel = modelOf(
                S1,
                P2,
                S2,
                S2,
                P2,
                S1,
                S3,
                P2,
                S1,
                S3,
                RDF.type,
                FS.File,
                S3,
                MACHINE_ONLY_PROPERTY,
                S1,
                S2,
                P2,
                S3,
                S1,
                P2,
                S3,
                S3,
                P2,
                S2);

        validator.validate(EMPTY_MODEL, testModel, EMPTY_MODEL, testModel, violationHandler);

        verify(violationHandler)
                .onViolation(
                        "The given model contains a machine-only predicate",
                        createStatement(S3, MACHINE_ONLY_PROPERTY, S1));
    }

    @Test
    public void testHasMachineOnlyPredicatesOnEmptyModel() {
        validator.validate(EMPTY_MODEL, EMPTY_MODEL, EMPTY_MODEL, EMPTY_MODEL, violationHandler);
        verifyNoInteractions(violationHandler);
    }
}
