package io.fairspace.saturn.webdav;

import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.NotAuthorizedException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.RDF;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.blobstore.BlobStore;
import io.fairspace.saturn.webdav.resources.CollectionResource;

import static io.fairspace.saturn.TestUtils.setupRequestContext;
import static io.fairspace.saturn.config.MetadataConfig.METADATA_SERVICE;

import static org.apache.jena.query.DatasetFactory.createTxnMem;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class CollectionResourceTest {
    public static final String BASE_PATH = "/api/webdav";
    private static final String baseUri = "http://example.com" + BASE_PATH;

    private final Model model = createTxnMem().getDefaultModel();
    private CollectionResource resource;

    private final Resource WORKSPACE_1 = model.createResource("http://localhost/iri/W1");
    private final Resource WORKSPACE_2 = model.createResource("http://localhost/iri/W2");
    private final Resource COLLECTION_1 = model.createResource("http://localhost/iri/C1");
    private final Resource USER_1 = model.createResource("http://localhost/iri/userid1");
    private final Resource USER_2 = model.createResource("http://localhost/iri/userid2");
    private final Resource USER_3 = model.createResource("http://localhost/iri/userid3");
    private final Resource USER_4 = model.createResource("http://localhost/iri/userid4");

    @Mock
    BlobStore store;

    @Mock
    UserService userService;

    @Mock
    MetadataService metadataService;

    @Mock
    WebDavProperties webDavProperties;

    Context context = new Context();

    @Before
    public void before() {
        model.add(WORKSPACE_1, RDF.type, FS.Workspace)
                .add(WORKSPACE_2, RDF.type, FS.Workspace)
                .add(COLLECTION_1, RDF.type, FS.Collection)
                .add(COLLECTION_1, FS.ownedBy, WORKSPACE_1)
                .add(COLLECTION_1, FS.belongsTo, WORKSPACE_1);
        var vocabulary = model.read("test-vocabulary.ttl");
        var userVocabulary = model.read("vocabulary.ttl");
        context.set(METADATA_SERVICE, metadataService);
        var factory = new DavFactory(
                model.createResource(baseUri),
                store,
                userService,
                context,
                webDavProperties,
                userVocabulary,
                vocabulary);
        resource = new CollectionResource(factory, COLLECTION_1, Access.Manage, userVocabulary, vocabulary);

        setupRequestContext();
    }

    @Test
    public void testTransferringOwnership() throws NotAuthorizedException, BadRequestException {
        model.add(USER_1, FS.isManagerOf, WORKSPACE_1)
                .add(USER_2, FS.isMemberOf, WORKSPACE_1)
                .add(USER_3, FS.isMemberOf, WORKSPACE_1)
                .add(USER_4, FS.isMemberOf, WORKSPACE_1)
                .add(USER_4, FS.isMemberOf, WORKSPACE_2);
        model.add(USER_1, FS.canManage, COLLECTION_1)
                .add(USER_2, FS.canList, COLLECTION_1)
                .add(USER_3, FS.canWrite, COLLECTION_1)
                .add(USER_4, FS.canManage, COLLECTION_1)
                .add(WORKSPACE_1, FS.canWrite, COLLECTION_1);

        resource.setOwnedBy(WORKSPACE_2);

        assertFalse(model.contains(COLLECTION_1, FS.ownedBy, WORKSPACE_1));
        assertTrue(model.contains(COLLECTION_1, FS.ownedBy, WORKSPACE_2));

        assertFalse(model.contains(COLLECTION_1, FS.belongsTo, WORKSPACE_1));
        assertTrue(model.contains(COLLECTION_1, FS.belongsTo, WORKSPACE_2));

        // User 1 - manager of workspace 1
        assertFalse(model.contains(USER_1, FS.canManage, COLLECTION_1));
        assertFalse(model.contains(USER_1, FS.canWrite, COLLECTION_1));
        assertTrue(model.contains(USER_1, FS.canRead, COLLECTION_1));
        assertFalse(model.contains(USER_1, FS.canList, COLLECTION_1));

        // User 2 - member of workspace 1
        assertFalse(model.contains(USER_2, FS.canManage, COLLECTION_1));
        assertFalse(model.contains(USER_2, FS.canWrite, COLLECTION_1));
        assertFalse(model.contains(USER_2, FS.canRead, COLLECTION_1));
        assertTrue(model.contains(USER_2, FS.canList, COLLECTION_1));

        // User 3 - member of workspace 1
        assertFalse(model.contains(USER_3, FS.canManage, COLLECTION_1));
        assertFalse(model.contains(USER_3, FS.canWrite, COLLECTION_1));
        assertTrue(model.contains(USER_3, FS.canRead, COLLECTION_1));
        assertFalse(model.contains(USER_3, FS.canList, COLLECTION_1));

        // User 4 - member of workspaces 1 and 2
        assertTrue(model.contains(USER_4, FS.canManage, COLLECTION_1));
        assertFalse(model.contains(USER_4, FS.canWrite, COLLECTION_1));
        assertFalse(model.contains(USER_4, FS.canRead, COLLECTION_1));
        assertFalse(model.contains(USER_4, FS.canList, COLLECTION_1));

        // Workspace 1 - previous owner
        assertFalse(model.contains(WORKSPACE_1, FS.canManage, COLLECTION_1));
        assertFalse(model.contains(WORKSPACE_1, FS.canWrite, COLLECTION_1));
        assertFalse(model.contains(WORKSPACE_1, FS.canRead, COLLECTION_1));
        assertFalse(model.contains(WORKSPACE_1, FS.canList, COLLECTION_1));
    }
}
