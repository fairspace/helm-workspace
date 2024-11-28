package io.fairspace.saturn.services.search;

import java.io.IOException;
import java.util.List;

import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.MakeCollectionableResource;
import io.milton.resource.PutableResource;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.DatasetImpl;
import org.apache.jena.sparql.util.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.controller.dto.request.FileSearchRequest;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.search.FilteredDatasetGraph;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.metadata.validation.ComposedValidator;
import io.fairspace.saturn.services.metadata.validation.UniqueLabelValidator;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.services.workspaces.Workspace;
import io.fairspace.saturn.services.workspaces.WorkspaceRole;
import io.fairspace.saturn.services.workspaces.WorkspaceService;
import io.fairspace.saturn.webdav.DavFactory;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;
import io.fairspace.saturn.webdav.blobstore.BlobStore;

import static io.fairspace.saturn.TestUtils.createTestUser;
import static io.fairspace.saturn.TestUtils.setupRequestContext;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;

import static org.apache.jena.query.DatasetFactory.wrap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SparqlFileSearchServiceTest {
    static final String BASE_PATH = "/api/webdav";
    static final String baseUri = "http://localhost:8080" + BASE_PATH;

    @Mock
    BlobStore store;

    @Mock
    UserService userService;

    @Mock
    private MetadataPermissions permissions;

    WorkspaceService workspaceService;
    MetadataService api;
    FileSearchService fileSearchService;

    User user;
    User user2;
    User workspaceManager;
    User admin;

    private void selectRegularUser() {
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    private void selectAdmin() {
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    private void setupUsers(Model model) {
        user = createTestUser("user", false);
        user.setCanViewPublicMetadata(true);
        new DAO(model).write(user);
        user2 = createTestUser("user2", false);
        new DAO(model).write(user2);
        workspaceManager = createTestUser("workspace-admin", false);
        new DAO(model).write(workspaceManager);
        admin = createTestUser("admin", true);
        new DAO(model).write(admin);
    }

    @Before
    public void before() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var dsg = DatasetGraphFactory.createTxnMem();
        Dataset ds = wrap(dsg);
        Transactions tx = new SimpleTransactions(ds);
        Model model = ds.getDefaultModel();
        var vocabulary = model.read("test-vocabulary.ttl");
        var systemVocabulary = model.read("system-vocabulary.ttl");
        var userVocabulary = model.read("vocabulary.ttl");

        workspaceService = new WorkspaceService(tx, userService);

        var context = new Context();

        var davFactory = new DavFactory(
                model.createResource(baseUri),
                store,
                userService,
                context,
                new WebDavProperties(),
                userVocabulary,
                vocabulary);
        var metadataPermissions = new MetadataPermissions(workspaceService, davFactory, userService);
        var filteredDatasetGraph = new FilteredDatasetGraph(ds.asDatasetGraph(), metadataPermissions);
        var filteredDataset = DatasetImpl.wrap(filteredDatasetGraph);

        fileSearchService = new SparqlFileSearchService(filteredDataset);

        when(permissions.canWriteMetadata(any())).thenReturn(true);
        api = new MetadataService(
                tx,
                vocabulary,
                systemVocabulary,
                new ComposedValidator(List.of(new UniqueLabelValidator())),
                permissions);

        setupUsers(model);

        setupRequestContext();
        var request = getCurrentRequest();

        selectAdmin();

        var taxonomies = model.read("test-taxonomies.ttl");
        api.put(taxonomies, Boolean.FALSE);

        var workspace = workspaceService.createWorkspace(
                Workspace.builder().code("Test").build());
        workspaceService.setUserRole(workspace.getIri(), workspaceManager.getIri(), WorkspaceRole.Manager);
        workspaceService.setUserRole(workspace.getIri(), user.getIri(), WorkspaceRole.Member);

        when(request.getHeader("Owner")).thenReturn(workspace.getIri().getURI());
        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", 0, "md5"));

        var root = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH);
        var coll1 = (PutableResource) root.createCollection("coll1");
        coll1.createNew("coffee.jpg", null, 0L, "image/jpeg");

        selectRegularUser();

        var coll2 = (PutableResource) root.createCollection("coll2");
        coll2.createNew("sample-s2-b-rna.fastq", null, 0L, "chemical/seq-na-fastq");
        coll2.createNew("sample-s2-b-rna_copy.fastq", null, 0L, "chemical/seq-na-fastq");

        var testdata = model.read("testdata.ttl");
        api.put(testdata, Boolean.FALSE);
    }

    @Test
    public void testRetrieveFilesForParent() {
        selectAdmin();
        var request = new FileSearchRequest();
        request.setQuery("coffee");
        request.setParentIRI(baseUri + "/coll1");

        var results = fileSearchService.searchFiles(request);
        assertEquals(1, results.size());
    }

    @Test
    public void testRetrieveFilesForInvalidParent() {
        selectAdmin();
        var request = new FileSearchRequest();
        request.setQuery("coffee");
        request.setParentIRI(">; INSERT something");

        Exception exception =
                assertThrows(IllegalArgumentException.class, () -> fileSearchService.searchFiles(request));
        String expectedMessage = "Invalid IRI";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }
}
