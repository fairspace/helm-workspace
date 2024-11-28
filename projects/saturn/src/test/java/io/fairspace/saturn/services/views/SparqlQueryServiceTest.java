package io.fairspace.saturn.services.views;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.MakeCollectionableResource;
import io.milton.resource.PutableResource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.DatasetImpl;
import org.apache.jena.sparql.util.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.config.properties.JenaProperties;
import io.fairspace.saturn.config.properties.SearchProperties;
import io.fairspace.saturn.config.properties.StoreParamsProperties;
import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.controller.dto.request.CountRequest;
import io.fairspace.saturn.controller.dto.request.ViewRequest;
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

import static io.fairspace.saturn.TestUtils.ADMIN;
import static io.fairspace.saturn.TestUtils.USER;
import static io.fairspace.saturn.TestUtils.createTestUser;
import static io.fairspace.saturn.TestUtils.loadViewsConfig;
import static io.fairspace.saturn.TestUtils.mockAuthentication;
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
public class SparqlQueryServiceTest {
    static final String BASE_PATH = "/api/webdav";
    static final String baseUri = "http://localhost:8080" + BASE_PATH;
    static final String SAMPLE_NATURE_BLOOD = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C12434";
    static final String ANALYSIS_TYPE_RNA_SEQ = "https://institut-curie.org/osiris#O6-12";
    static final String ANALYSIS_TYPE_IMAGING = "https://institut-curie.org/osiris#C37-2";

    @Mock
    BlobStore store;

    @Mock
    UserService userService;

    @Mock
    private MetadataPermissions permissions;

    WorkspaceService workspaceService;
    MetadataService api;
    QueryService queryService;

    private DAO dao;

    User user;
    User user2;
    User workspaceManager;
    User admin;

    // TODO: move the selectUser methods to a parent with mocked UserService
    private void selectExternalUser() {
        mockAuthentication("user2");
        lenient().when(userService.currentUser()).thenReturn(user2);
    }

    private void selectAdmin() {
        mockAuthentication(ADMIN);
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    private void selectRegularUser() {
        mockAuthentication(USER);
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    private void setupUsers() {
        user = createTestUser(USER, false);
        user.setCanViewPublicMetadata(true);
        dao.write(user);
        user2 = createTestUser("user2", false);
        dao.write(user2);
        workspaceManager = createTestUser("workspace-admin", false);
        dao.write(workspaceManager);
        admin = createTestUser(ADMIN, true);
        dao.write(admin);
    }

    @Before
    public void before() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        JenaProperties.setMetadataBaseIRI("http://localhost/iri/");
        var dsg = DatasetGraphFactory.createTxnMem();
        Dataset ds = wrap(dsg);
        Transactions tx = new SimpleTransactions(ds);
        Model model = ds.getDefaultModel();
        var vocabulary = model.read("test-vocabulary.ttl");
        var userVocabulary = model.read("vocabulary.ttl");
        var systemVocabulary = model.read("system-vocabulary.ttl");

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

        SearchProperties searchProperties = new SearchProperties();
        searchProperties.setCountRequestTimeout(60000);
        searchProperties.setPageRequestTimeout(10000);
        searchProperties.setMaxJoinItems(50);
        queryService = new SparqlQueryService(
                searchProperties,
                new JenaProperties("http://localhost/iri/", new StoreParamsProperties()),
                loadViewsConfig("src/test/resources/test-views.yaml"),
                filteredDataset,
                tx);

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
        HttpServletRequest request = getCurrentRequest();

        selectAdmin();

        var taxonomies = model.read("test-taxonomies.ttl");
        api.put(taxonomies, Boolean.FALSE);
        when(userService.currentUser()).thenReturn(admin);
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
    public void testRetrieveSamplePage() {
        var viewRequest = new ViewRequest();
        viewRequest.setView("Sample");
        viewRequest.setPage(1);
        viewRequest.setSize(10);
        var page = queryService.retrieveViewPage(viewRequest);
        assertEquals(2, page.getRows().size());
        // The implementation does not sort results. Probably deterministic,
        // but no certain order is guaranteed.
        var row =
                page.getRows().get(0).get("Sample").iterator().next().value().equals("http://example.com/samples#s1-a")
                        ? page.getRows().get(0)
                        : page.getRows().get(1);
        assertEquals(
                "Sample A for subject 1", row.get("Sample").iterator().next().label());
        assertEquals(
                SAMPLE_NATURE_BLOOD, row.get("Sample_nature").iterator().next().value());
        assertEquals("Blood", row.get("Sample_nature").iterator().next().label());
        assertEquals("Liver", row.get("Sample_topography").iterator().next().label());
        assertEquals(
                45.2f,
                ((Number) row.get("Sample_tumorCellularity").iterator().next().value()).floatValue(),
                0.01);
    }

    @Test
    public void testCountSamplesWithoutMaxDisplayCount() {
        selectRegularUser();
        var requestParams = new CountRequest();
        requestParams.setView("Sample");
        var result = queryService.count(requestParams);
        assertEquals(2, result.count());
    }

    @Test
    public void testCountSubjectWithMaxDisplayCountLimitLessThanTotalCount() {
        var request = new CountRequest();
        request.setView("Subject");
        var result = queryService.count(request);
        Assert.assertEquals(1, result.count());
    }

    @Test
    public void testCountResourceWithAccess() {
        selectRegularUser();
        var request = new CountRequest();
        request.setView("Resource");

        var result = queryService.count(request);
        Assert.assertEquals(3, result.count());
    }

    @Test
    public void testCountSamplesWithoutViewAccess() {
        selectExternalUser();
        var countRequest = new CountRequest();
        countRequest.setView("Sample");
        var result = queryService.count(countRequest);
        assertEquals(0, result.count());
    }

    @Test
    public void testRetrieveSamplePageUsingSampleFilter() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(List.of(ViewFilter.builder()
                .field("Sample_nature")
                .values(List.of(SAMPLE_NATURE_BLOOD))
                .build()));
        var page = queryService.retrieveViewPage(request);
        assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveSamplePageUsingPrefixFilter() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(
                List.of(ViewFilter.builder().field("Sample").prefix("sample b").build()));
        var page = queryService.retrieveViewPage(request);
        assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveSamplePageForAccessibleCollection() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(Collections.singletonList(ViewFilter.builder()
                .field("Resource_analysisType")
                .values(Collections.singletonList(ANALYSIS_TYPE_RNA_SEQ))
                .build()));
        var page = queryService.retrieveViewPage(request);
        assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveUniqueSamplesForLocation() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(Collections.singletonList(ViewFilter.builder()
                .field("location")
                .values(Collections.singletonList(baseUri + "/coll2"))
                .build()));
        var page = queryService.retrieveViewPage(request);
        assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveSamplesForInvalidLocation() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(Collections.singletonList(ViewFilter.builder()
                .field("location")
                .values(Collections.singletonList(">; INSERT something"))
                .build()));
        Exception exception =
                assertThrows(IllegalArgumentException.class, () -> queryService.retrieveViewPage(request));
        String expectedMessage = "Invalid IRI";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    public void testRetrieveSamplePageForUnaccessibleCollection() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(List.of(ViewFilter.builder()
                .field("Resource_analysisType")
                .values(List.of(ANALYSIS_TYPE_IMAGING))
                .build()));
        var page = queryService.retrieveViewPage(request);
        assertEquals(0, page.getRows().size());
    }
}
