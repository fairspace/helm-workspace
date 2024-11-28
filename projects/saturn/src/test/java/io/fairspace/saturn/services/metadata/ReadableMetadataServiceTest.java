package io.fairspace.saturn.services.metadata;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.vocabulary.FS;

import static org.apache.jena.query.DatasetFactory.createTxnMem;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.apache.jena.rdf.model.ResourceFactory.createStatement;
import static org.apache.jena.rdf.model.ResourceFactory.createStringLiteral;
import static org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral;
import static org.apache.jena.riot.RDFDataMgr.loadModel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReadableMetadataServiceTest {
    private static final Resource S1 = createResource("http://localhost/iri/S1");
    private static final Resource S2 = createResource("http://localhost/iri/S2");
    private static final Resource S3 = createResource("http://localhost/iri/S3");
    private static final Property P1 = createProperty("https://fairspace.nl/ontology/P1");

    private static final Statement STMT1 = createStatement(S1, P1, S2);
    private static final Statement STMT2 = createStatement(S2, P1, S3);

    private static final Statement LBL_STMT1 = createStatement(S1, RDFS.label, createStringLiteral("subject1"));
    private static final Statement LBL_STMT2 = createStatement(S2, RDFS.label, createStringLiteral("subject2"));

    private final Transactions txn = new SimpleTransactions(createTxnMem());
    private MetadataService api;
    private final Model systemVocabulary = loadModel("system-vocabulary.ttl");
    private final Model vocabulary = systemVocabulary.union(createDefaultModel());

    @Mock
    MetadataPermissions permissions;

    @Before
    public void setUp() {
        when(permissions.canReadMetadata(any())).thenReturn(true);
        api = new MetadataService(txn, vocabulary, systemVocabulary, null, permissions);
    }

    @Test
    public void get() {
        assertEquals(0, api.get(null, false).size());

        txn.executeWrite(m -> m.add(STMT1).add(STMT2));

        assertTrue(api.get(S1.getURI(), false).contains(STMT1));
        assertTrue(api.get(S2.getURI(), false).contains(STMT2));

        assertTrue(api.get(S3.getURI(), false).isEmpty());
    }

    @Test
    public void getWithImportantPropertiesWorksWithoutImportantProperties() {
        txn.executeWrite(m -> m.add(STMT1).add(LBL_STMT1));

        assertTrue(api.get(S1.getURI(), true).contains(LBL_STMT1));
    }

    @Test
    public void getWithImportantPropertiesIncludesImportantProperties() {
        var someProperty = createProperty("http://ex.com/some");
        var importantProperty = createProperty("http://ex.com/important");
        var unimportantProperty = createProperty("http://ex.com/unimportant");

        var clazz = createResource("http://ex.com/Class");
        var clazzShape = createResource("http://ex.com/ClassShape");
        var importantPropertyShape = createResource("http://ex.com/importantShape");
        var unimportantPropertyShape = createResource("http://ex.com/unimportantShape");
        ;

        txn.executeWrite(m -> {
            setupImportantProperties();

            vocabulary
                    .add(clazzShape, SHACLM.targetClass, clazz)
                    .add(clazzShape, SHACLM.property, importantPropertyShape)
                    .add(importantPropertyShape, SHACLM.path, importantProperty)
                    .addLiteral(importantPropertyShape, FS.importantProperty, true)
                    .add(clazzShape, SHACLM.property, unimportantPropertyShape)
                    .add(unimportantPropertyShape, SHACLM.path, unimportantProperty);

            m.add(S1, someProperty, S2)
                    .add(S2, RDF.type, clazz)
                    .add(S2, unimportantProperty, S3)
                    .add(S2, importantProperty, S3);
        });

        var result = api.get(S1.getURI(), true);

        assertEquals(2, result.size());
        assertTrue(result.contains(S1, someProperty, S2));
        assertTrue(result.contains(S2, importantProperty, S3));
        assertFalse(result.contains(S2, unimportantProperty, S3));
    }

    private void setupImportantProperties() {
        Resource labelShape = createResource("http://labelShape");
        Resource createdByShape = createResource("http://createdByShape");
        Resource md5Shape = createResource("http://md5Shape");

        vocabulary
                .add(labelShape, FS.importantProperty, createTypedLiteral(Boolean.TRUE))
                .add(labelShape, SHACLM.path, RDFS.label)
                .add(createdByShape, FS.importantProperty, createTypedLiteral(Boolean.TRUE))
                .add(createdByShape, SHACLM.path, FS.createdBy)
                .add(md5Shape, FS.importantProperty, createTypedLiteral(Boolean.FALSE))
                .add(md5Shape, SHACLM.path, FS.md5);
    }
}
