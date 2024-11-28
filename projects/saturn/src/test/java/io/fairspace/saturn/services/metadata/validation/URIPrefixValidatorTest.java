package io.fairspace.saturn.services.metadata.validation;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.fairspace.saturn.rdf.ModelUtils.EMPTY_MODEL;
import static io.fairspace.saturn.rdf.ModelUtils.modelOf;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class URIPrefixValidatorTest {

    @Mock
    private ViolationHandler violationHandler;

    private URIPrefixValidator validator;

    @Before
    public void setUp() {
        validator = new URIPrefixValidator("http://example.com");
    }

    @Test
    public void resourcesWithRestrictedPrefixCannotBeCreated() {
        Resource resource1 = createResource("http://example.com/api/webdav/123");
        Resource resource1Type = createResource("http://example.com/Resource");
        var model = modelOf(resource1, RDF.type, resource1Type);
        validator.validate(EMPTY_MODEL, model, EMPTY_MODEL, model, violationHandler);

        verify(violationHandler)
                .onViolation(
                        "Cannot add resource with URI starting with restricted prefix 'http://example.com/api/webdav'.",
                        resource1,
                        null,
                        null);
        verifyNoMoreInteractions(violationHandler);
    }

    @Test
    public void resourcesWithRestrictedPrefixCanBeUpdated() {
        Resource resource1 = createResource("http://example.com/api/webdav/123");
        Resource resource1Type = createResource("http://example.com/Resource");
        var model = modelOf(resource1, RDF.type, resource1Type);

        var toAdd = modelOf(resource1, RDFS.comment, createTypedLiteral(123));

        validator.validate(model, model.union(toAdd), EMPTY_MODEL, toAdd, violationHandler);
        verifyNoInteractions(violationHandler);
    }
}
