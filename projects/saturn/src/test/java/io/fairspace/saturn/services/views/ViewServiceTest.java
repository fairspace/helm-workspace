package io.fairspace.saturn.services.views;

import java.io.IOException;
import java.sql.SQLException;
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
import org.apache.jena.sparql.util.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.PostgresAwareTest;
import io.fairspace.saturn.auth.RequestContext;
import io.fairspace.saturn.config.properties.CacheProperties;
import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.rdf.SparqlUtils;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.rdf.transactions.TxnIndexDatasetGraph;
import io.fairspace.saturn.services.AccessDeniedException;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.metadata.validation.ComposedValidator;
import io.fairspace.saturn.services.metadata.validation.UniqueLabelValidator;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.services.workspaces.Workspace;
import io.fairspace.saturn.services.workspaces.WorkspaceService;
import io.fairspace.saturn.webdav.DavFactory;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;
import io.fairspace.saturn.webdav.blobstore.BlobStore;

import static io.fairspace.saturn.TestUtils.createTestUser;
import static io.fairspace.saturn.TestUtils.loadViewsConfig;
import static io.fairspace.saturn.TestUtils.setupRequestContext;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;
import static io.fairspace.saturn.services.views.ViewService.USER_DOES_NOT_HAVE_PERMISSIONS_TO_READ_FACETS;

import static org.apache.jena.sparql.core.DatasetImpl.wrap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ViewServiceTest extends PostgresAwareTest {
    static final String BASE_PATH = "/api/webdav";
    static final String baseUri = "http://localhost:8080" + BASE_PATH;

    @Mock
    BlobStore store;

    @Mock
    UserService userService;

    @Mock
    private MetadataPermissions permissions;

    @Mock
    private MaterializedViewService materializedViewService;

    MetadataService api;
    ViewService viewService;

    @Before
    public void before()
            throws SQLException, BadRequestException, ConflictException, NotAuthorizedException, IOException {
        var viewDatabase = buildViewDatabaseConfig();
        var viewsProperties = loadViewsConfig("src/test/resources/test-views.yaml");
        var configuration = new ViewStoreClient.ViewStoreConfiguration(viewsProperties);
        var dataSource = getDataSource(viewDatabase);
        var viewStoreClientFactory = new ViewStoreClientFactory(
                viewsProperties, viewDatabase, materializedViewService, dataSource, configuration);
        var dsg = new TxnIndexDatasetGraph(
                viewsProperties, DatasetGraphFactory.createTxnMem(), viewStoreClientFactory, "http://localhost:8080");

        Dataset ds = wrap(dsg);

        loadTestData(ds);

        var searchProperties = buildSearchProperties();
        var viewStoreReader =
                new ViewStoreReader(searchProperties, viewsProperties, viewStoreClientFactory, configuration);
        viewService = new ViewService(
                searchProperties,
                new CacheProperties(),
                viewsProperties,
                ds,
                viewStoreReader,
                viewStoreClientFactory,
                permissions);
    }

    @Test
    public void testFetchViewConfig() {
        when(permissions.canReadFacets()).thenReturn(true);
        var facets = viewService.getFacets();
        var dateFacets = facets.stream()
                .filter(facet -> facet.type() == ViewsProperties.ColumnType.Date)
                .toList();
        Assert.assertEquals(2, dateFacets.size());

        var boolFacets = facets.stream()
                .filter(facet -> facet.type() == ViewsProperties.ColumnType.Boolean)
                .toList();
        Assert.assertEquals(1, boolFacets.size());
    }

    @Test
    public void testNoAccessExceptionFetchingFacetsWhenUserHasNoPermissions() {
        when(permissions.canReadFacets()).thenReturn(false);

        Assert.assertThrows(
                USER_DOES_NOT_HAVE_PERMISSIONS_TO_READ_FACETS,
                AccessDeniedException.class,
                () -> viewService.getFacets());
    }

    @Test
    public void testDisplayIndex_IsSet() {
        var views = viewService.getViews();
        var columns = views.get(1).columns().stream().toList();
        var selectedColumn = columns.stream()
                .filter(c -> c.title().equals("Morphology"))
                .toList()
                .getFirst();
        Assert.assertEquals(Integer.valueOf(1), selectedColumn.displayIndex());
    }

    @Test
    public void testDisplayIndex_IsNotSet() {
        var views = viewService.getViews();
        var columns = views.get(1).columns().stream().toList();
        var selectedColumn = columns.stream()
                .filter(c -> c.title().equals("Laterality"))
                .toList()
                .getFirst();
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), selectedColumn.displayIndex());
    }

    @Test
    public void testFetchCachedFacets() {
        // given
        var sut = spy(viewService);
        when(permissions.canReadFacets()).thenReturn(true);

        // when
        var facets = sut.getFacets();

        // then
        Assert.assertEquals(facets.size(), 11);
        verify(sut, never()).fetchFacets();
    }

    @Test
    public void testFetchCachedViews() {
        // given
        var sut = spy(viewService);

        // when
        var views = viewService.getViews();

        // then
        Assert.assertEquals(views.size(), 4);
        verify(sut, never()).fetchViews();
    }

    private void loadTestData(Dataset ds)
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        // TODO: loaded data to be mocked instead of loading them this way
        Transactions tx = new SimpleTransactions(ds);
        Model model = ds.getDefaultModel();
        var vocabulary = model.read("test-vocabulary.ttl");
        var userVocabulary = model.read("vocabulary.ttl");
        var systemVocabulary = model.read("system-vocabulary.ttl");

        var workspaceService = new WorkspaceService(tx, userService);

        var context = new Context();

        var davFactory = new DavFactory(
                model.createResource(baseUri),
                store,
                userService,
                context,
                new WebDavProperties(),
                userVocabulary,
                vocabulary);

        when(permissions.canWriteMetadata(any())).thenReturn(true);
        api = new MetadataService(
                tx,
                vocabulary,
                systemVocabulary,
                new ComposedValidator(List.of(new UniqueLabelValidator())),
                permissions);

        setupRequestContext();

        var currentRequest = mock(HttpServletRequest.class);
        RequestContext.setCurrentRequest(currentRequest);
        RequestContext.setCurrentUserStringUri(
                SparqlUtils.generateMetadataIriFromId("user").getURI());
        var request = getCurrentRequest();

        var taxonomies = model.read("test-taxonomies.ttl");
        api.put(taxonomies, Boolean.TRUE);

        User user = createTestUser("user", true);
        when(userService.currentUser()).thenReturn(user);
        var workspace = workspaceService.createWorkspace(
                Workspace.builder().code("Test").build());
        when(request.getHeader("Owner")).thenReturn(workspace.getIri().getURI());
        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", 0, "md5"));

        var root = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH);
        var coll1 = (PutableResource) root.createCollection("coll1");
        coll1.createNew("coffee.jpg", null, 0L, "image/jpeg");

        var testdata = model.read("testdata.ttl");
        api.put(testdata, Boolean.TRUE);
    }
}
