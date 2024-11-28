package io.fairspace.saturn.webdav;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import io.milton.http.FileItem;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.MakeCollectionableResource;
import io.milton.resource.PutableResource;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.metadata.validation.ComposedValidator;
import io.fairspace.saturn.services.metadata.validation.UniqueLabelValidator;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.services.workspaces.Workspace;
import io.fairspace.saturn.services.workspaces.WorkspaceService;
import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.blobstore.BlobFileItem;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;
import io.fairspace.saturn.webdav.blobstore.BlobStore;
import io.fairspace.saturn.webdav.resources.DirectoryResource;
import io.fairspace.saturn.webdav.resources.FileResource;

import static io.fairspace.saturn.TestUtils.createTestUser;
import static io.fairspace.saturn.TestUtils.mockAuthentication;
import static io.fairspace.saturn.TestUtils.setupRequestContext;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;
import static io.fairspace.saturn.config.MetadataConfig.METADATA_SERVICE;

import static org.apache.jena.query.DatasetFactory.wrap;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DirectoryResourceTest {
    static final String BASE_PATH = "/api/webdav";
    static final String baseUri = "http://example.com" + BASE_PATH;
    private static final int FILE_SIZE = 10;
    private Model model;

    @Mock
    BlobStore store;

    @Mock
    UserService userService;

    @Mock
    MetadataService metadataService;

    @Mock
    FileItem file;

    @Mock
    BlobFileItem blobFileItem;

    @Mock
    InputStream input;

    @Mock
    private MetadataPermissions permissions;

    private DavFactory davFactory;
    DirectoryResource dir;
    User admin;

    Model vocabulary;
    Model systemVocabulary;
    Model userVocabulary;

    private void selectAdmin() {
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    @Before
    public void before() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var dsg = DatasetGraphFactory.createTxnMem();
        Dataset ds = wrap(dsg);
        Transactions tx = new SimpleTransactions(ds);
        model = ds.getDefaultModel();
        vocabulary = model.read("test-vocabulary.ttl");
        systemVocabulary = model.read("system-vocabulary.ttl");
        userVocabulary = model.read("vocabulary.ttl");
        var workspaceService = new WorkspaceService(tx, userService);
        when(permissions.canWriteMetadata(any())).thenReturn(true);
        Context context = new Context();
        metadataService = new MetadataService(
                tx,
                vocabulary,
                systemVocabulary,
                new ComposedValidator(List.of(new UniqueLabelValidator())),
                permissions);
        context.set(METADATA_SERVICE, metadataService);
        davFactory = new DavFactory(
                model.createResource(baseUri),
                store,
                userService,
                context,
                new WebDavProperties(),
                userVocabulary,
                vocabulary);

        mockAuthentication("admin");
        admin = createTestUser("admin", true);
        new DAO(model).write(admin);

        setupRequestContext();
        var request = getCurrentRequest();

        selectAdmin();

        var taxonomies = model.read("test-taxonomies.ttl");
        metadataService.put(taxonomies, Boolean.FALSE);

        var workspace = workspaceService.createWorkspace(
                Workspace.builder().code("Test").build());

        when(request.getHeader("Owner")).thenReturn(workspace.getIri().getURI());
        var blob = new BlobInfo("id", FILE_SIZE, "md5");
        when(request.getAttribute("BLOB")).thenReturn(blob);
        when(blobFileItem.getBlob()).thenReturn(blob);
        when(file.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(new byte[FILE_SIZE]));

        var root = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH);
        var coll1 = (PutableResource) root.createCollection("coll1");
        coll1.createNew("coffee.jpg", null, 0L, "image/jpeg");

        var testdata = model.read("testdata.ttl");
        metadataService.put(testdata, Boolean.FALSE);
    }

    @Test
    public void testFileUploadSuccess() throws NotAuthorizedException, ConflictException, BadRequestException {
        dir = new DirectoryResource(
                davFactory, model.getResource(baseUri + "/dir"), Access.Manage, userVocabulary, vocabulary);
        dir.subject.addProperty(RDF.type, FS.Directory);

        dir.processForm(Map.of("action", "upload_files"), Map.of("/subdir/file.ext", blobFileItem));

        assertTrue(dir.child("subdir") instanceof DirectoryResource);

        var subdir = (DirectoryResource) dir.child("subdir");

        assertTrue(subdir.child("file.ext") instanceof FileResource);

        var file = (FileResource) subdir.child("file.ext");

        assertEquals(FILE_SIZE, (long) file.getContentLength());
    }

    @Test
    public void testDeleteAllInDirectory()
            throws NotAuthorizedException, ConflictException, BadRequestException, IOException {
        dir = new DirectoryResource(
                davFactory, model.getResource(baseUri + "/dir"), Access.Manage, userVocabulary, vocabulary);
        dir.subject.addProperty(RDF.type, FS.Directory);

        dir.createNew("file1", input, 3L, "text/abc");
        dir.createNew("file2", input, 3L, "text/abc");
        dir.createNew("file3", input, 3L, "text/abc");

        assertEquals(3, dir.getChildren().size());

        dir.processForm(Map.of("action", "delete_all_in_directory"), Map.of());

        verifyNoInteractions(store);
        assertEquals(0, dir.getChildren().size());
        assertNull(dir.child("file1"));
        assertNull(dir.child("file2"));
        assertNull(dir.child("file3"));
    }

    @Test
    public void testFileUploadExistingDir() throws NotAuthorizedException, ConflictException, BadRequestException {
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        dir.processForm(Map.of("action", "upload_files"), Map.of("/coll1/file.ext", blobFileItem));

        assertTrue(dir.child("coll1") instanceof DirectoryResource);

        var subdir = (DirectoryResource) dir.child("coll1");
        assertTrue(subdir.child("file.ext") instanceof FileResource);

        var file = (FileResource) subdir.child("file.ext");
        assertEquals(FILE_SIZE, (long) file.getContentLength());
    }

    @Test
    public void testTypedLiteralMetadataUploadSuccess()
            throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv =
                """
                Path,Description
                .,"Blah"
                ./coffee.jpg,"Blah blah"
                """;
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        DirectoryResource dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));

        assertTrue(dir.subject.hasProperty(RDFS.comment, model.createTypedLiteral("Blah")));

        FileResource file = (FileResource) davFactory.getResource(null, BASE_PATH + "/coll1/coffee.jpg");
        assertTrue(file.subject.hasProperty(RDFS.comment, model.createTypedLiteral("Blah blah")));
    }

    @Test(expected = BadRequestException.class)
    public void testMetadataUploadUnknownProperty()
            throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv = """
                Path,Unknown
                ./coll1,"Blah blah"
                """;
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");

        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }

    @Test(expected = BadRequestException.class)
    public void testMetadataUploadEmptyHeader() throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv = """
                ,
                ./coll1,"Blah blah"
                """;
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");

        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }

    @Test(expected = BadRequestException.class)
    public void testMetadataUploadUnknownFile() throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv = """
                Path,Description
                ./subdir,"Blah blah"
                """;
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");

        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }

    @Test(expected = BadRequestException.class)
    public void testMetadataUploadDeletedFile() throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv = """
                Path,Description
                ./subdir,"Blah blah"
                """;
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        var subdir = (DirectoryResource) dir.createCollection("subdir");
        subdir.delete();

        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }

    @Test
    public void testLinkedMetadataUploadByIRISuccess()
            throws NotAuthorizedException, ConflictException, BadRequestException {
        Property sampleProp = createProperty("https://institut-curie.org/ontology#sample");
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        assert !dir.subject.hasProperty(sampleProp);

        String csv =
                """
                Path,Is about biological sample
                .,"http://example.com/samples#s2-b"
                """;
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));

        assertEquals(dir.subject.getProperty(sampleProp).getResource().getURI(), "http://example.com/samples#s2-b");
    }

    @Test
    public void testLinkedMetadataUploadByLabelSuccess()
            throws NotAuthorizedException, ConflictException, BadRequestException {
        Property sampleProp = createProperty("https://institut-curie.org/ontology#sample");
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        assert !dir.subject.hasProperty(sampleProp);

        String csv =
                """
                Path,Is about biological sample
                .,"Sample A for subject 1"
                """;
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));

        assertEquals(dir.subject.getProperty(sampleProp).getResource().getURI(), "http://example.com/samples#s1-a");
    }

    @Test(expected = BadRequestException.class)
    public void testLinkedMetadataUploadByUnknownIRI()
            throws NotAuthorizedException, ConflictException, BadRequestException {
        Property sampleProp = createProperty("https://institut-curie.org/ontology#sample");
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        assert !dir.subject.hasProperty(sampleProp);

        String csv =
                """
                Path,Is about biological sample
                .,"http://example.com/samples#unknown-sample"
                """;
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }
}
