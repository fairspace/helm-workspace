package io.fairspace.saturn.services.search;

import java.io.IOException;
import java.sql.SQLException;
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
import org.apache.jena.sparql.util.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.PostgresAwareTest;
import io.fairspace.saturn.config.properties.CacheProperties;
import io.fairspace.saturn.config.properties.JenaProperties;
import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.controller.dto.request.FileSearchRequest;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.rdf.transactions.TxnIndexDatasetGraph;
import io.fairspace.saturn.services.maintenance.MaintenanceService;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.metadata.validation.ComposedValidator;
import io.fairspace.saturn.services.metadata.validation.UniqueLabelValidator;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.services.views.ViewService;
import io.fairspace.saturn.services.views.ViewStoreClient;
import io.fairspace.saturn.services.views.ViewStoreClientFactory;
import io.fairspace.saturn.services.views.ViewStoreReader;
import io.fairspace.saturn.services.workspaces.Workspace;
import io.fairspace.saturn.services.workspaces.WorkspaceRole;
import io.fairspace.saturn.services.workspaces.WorkspaceService;
import io.fairspace.saturn.webdav.DavFactory;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;
import io.fairspace.saturn.webdav.blobstore.BlobStore;

import static io.fairspace.saturn.TestUtils.ADMIN;
import static io.fairspace.saturn.TestUtils.USER;
import static io.fairspace.saturn.TestUtils.createTestUser;
import static io.fairspace.saturn.TestUtils.loadViewsConfig;
import static io.fairspace.saturn.TestUtils.mockAuthentication;
import static io.fairspace.saturn.TestUtils.setupRequestContext;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;

import static org.apache.jena.query.DatasetFactory.wrap;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JdbcFileSearchServiceTest extends PostgresAwareTest {
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
    MaintenanceService maintenanceService;

    private DAO dao;

    User user;
    User user2;
    User workspaceManager;
    User admin;

    private void selectRegularUser() {
        mockAuthentication(USER);
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    private void selectAdmin() {
        mockAuthentication(ADMIN);
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    private void setupUsers() {
        user = createTestUser("user", false);
        user.setCanViewPublicMetadata(true);
        dao.write(user);
        user2 = createTestUser("user2", false);
        dao.write(user2);
        workspaceManager = createTestUser("workspace-admin", false);
        dao.write(workspaceManager);
        admin = createTestUser("admin", true);
        dao.write(admin);
    }

    @Before
    public void before()
            throws SQLException, NotAuthorizedException, BadRequestException, ConflictException, IOException {
        JenaProperties.setMetadataBaseIRI("http://localhost/iri/");
        var viewsProperties = loadViewsConfig("src/test/resources/test-views.yaml");
        var searchProperties = buildSearchProperties();
        var viewDatabase = buildViewDatabaseConfig();
        var configuration = new ViewStoreClient.ViewStoreConfiguration(viewsProperties);
        var dataSource = getDataSource(viewDatabase);
        var viewStoreClientFactory = new ViewStoreClientFactory(
                viewsProperties, viewDatabase, dataSource, configuration);
        var dsg = new TxnIndexDatasetGraph(
                viewsProperties, DatasetGraphFactory.createTxnMem(), viewStoreClientFactory, "http://localhost:8080");
        Dataset ds = wrap(dsg);
        Transactions tx = new SimpleTransactions(ds);
        Model model = ds.getDefaultModel();
        var vocabulary = model.read("test-vocabulary.ttl");
        var userVocabulary = model.read("vocabulary.ttl");
        var systemVocabulary = model.read("system-vocabulary.ttl");
        var viewStoreReader =
                new ViewStoreReader(searchProperties, viewsProperties, viewStoreClientFactory, configuration);
        var viewService = new ViewService(
                searchProperties,
                new CacheProperties(),
                viewsProperties,
                ds,
                viewStoreReader,
                viewStoreClientFactory,
                permissions);

        maintenanceService = new MaintenanceService(
                viewsProperties, userService, ds, viewStoreClientFactory, viewService, "http://localhost:8080");

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
        fileSearchService = new JdbcFileSearchService(tx, davFactory.root, viewStoreReader);

        when(permissions.canWriteMetadata(any())).thenReturn(true);
        api = new MetadataService(
                tx,
                vocabulary,
                systemVocabulary,
                new ComposedValidator(List.of(new UniqueLabelValidator())),
                permissions);

        dao = new DAO(model);

        setupUsers();

        setupRequestContext();
        var request = getCurrentRequest();

        selectAdmin();

        var taxonomies = model.read("test-taxonomies.ttl");
        api.put(taxonomies, Boolean.TRUE);

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

        var coll3 = (PutableResource) root.createCollection("coll3");

        coll3.createNew("sample-s2-b-rna_copy.fastq", null, 0L, "chemical/seq-na-fastq");

        var testdata = model.read("testdata.ttl");
        api.put(testdata, Boolean.TRUE);
    }

    @Test
    public void testSearchFiles() {
        var request = new FileSearchRequest();
        // There are two files with 'rna' in the file name in coll2.
        request.setQuery("rna");
        var results = fileSearchService.searchFiles(request);
        Assert.assertEquals(2, results.size());
        // Expect the results to be sorted by id
        Assert.assertEquals("sample-s2-b-rna.fastq", results.get(0).label());
        Assert.assertEquals("sample-s2-b-rna_copy.fastq", results.get(1).label());
    }

    @Test
    public void testSearchFilesRestrictsToAccessibleCollections() {
        var request = new FileSearchRequest();
        // There is one file named coffee.jpg in coll1, not accessible by the regular user.
        request.setQuery("coffee");
        var results = fileSearchService.searchFiles(request);
        Assert.assertEquals(0, results.size());

        selectAdmin();
        results = fileSearchService.searchFiles(request);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("coffee.jpg", results.getFirst().label());
    }

    @Test
    public void testSearchFilesRestrictsToAccessibleCollectionsAfterReindexing() {
        maintenanceService.recreateIndex();
        var request = new FileSearchRequest();
        // There is one file named coffee.jpg in coll1, not accessible by the regular user.
        request.setQuery("coffee");
        var results = fileSearchService.searchFiles(request);
        Assert.assertEquals(0, results.size());

        selectAdmin();
        results = fileSearchService.searchFiles(request);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("coffee.jpg", results.getFirst().label());
    }

    @Test
    public void testSearchFilesRestrictsToParentDirectory() {
        selectAdmin();
        var request = new FileSearchRequest();
        // There is one file named coffee.jpg in coll1.
        request.setQuery("coffee");

        request.setParentIRI("http://localhost:8080/api/webdav/coll1");
        var results = fileSearchService.searchFiles(request);
        Assert.assertEquals(1, results.size());

        request.setParentIRI("http://localhost:8080/api/webdav/coll2");
        results = fileSearchService.searchFiles(request);
        Assert.assertEquals(0, results.size());
    }

    @Test
    public void testSearchFileDescription() {
        selectAdmin();
        var request = new FileSearchRequest();
        // There is one file named sample-s2-b-rna.fastq with a description
        request.setQuery("corona");

        // request.setParentIRI(ConfigLoader.CONFIG.publicUrl + "/api/webdav/coll1");
        var results = fileSearchService.searchFiles(request);
        Assert.assertEquals(1, results.size());
    }
}
