package io.fairspace.saturn.webdav;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.xml.namespace.QName;

import io.milton.http.Request;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.DeletableResource;
import io.milton.resource.FolderResource;
import io.milton.resource.GetableResource;
import io.milton.resource.MakeCollectionableResource;
import io.milton.resource.MoveableResource;
import io.milton.resource.MultiNamespaceCustomPropertyResource;
import io.milton.resource.PostableResource;
import io.milton.resource.PutableResource;
import io.milton.resource.ReplaceableResource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.util.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.config.properties.JenaProperties;
import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.services.workspaces.Workspace;
import io.fairspace.saturn.services.workspaces.WorkspaceRole;
import io.fairspace.saturn.services.workspaces.WorkspaceService;
import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;
import io.fairspace.saturn.webdav.blobstore.BlobStore;

import static io.fairspace.saturn.TestUtils.ADMIN;
import static io.fairspace.saturn.TestUtils.USER;
import static io.fairspace.saturn.TestUtils.createTestUser;
import static io.fairspace.saturn.TestUtils.mockAuthentication;
import static io.fairspace.saturn.TestUtils.setupRequestContext;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;

import static io.milton.http.ResponseStatus.SC_FORBIDDEN;
import static java.lang.String.format;
import static org.apache.jena.query.DatasetFactory.createTxnMem;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DavFactoryTest {
    public static final long FILE_SIZE = 3L;
    public static final String BASE_PATH = "/api/webdav";
    public static final QName VERSION = new QName(FS.NS, "version");
    private static final String baseUri = "http://example.com" + BASE_PATH;

    @Mock
    BlobStore store;

    @Mock
    InputStream input;

    @Mock
    UserService userService;

    WorkspaceService workspaceService;
    Workspace workspace;

    Context context = new Context();
    User user;
    User workspaceManager;
    User admin;
    private HttpServletRequest request;

    private ResourceFactory factory;
    private final Dataset ds = createTxnMem();
    private final Transactions tx = new SimpleTransactions(ds);
    private final Model model = ds.getDefaultModel();
    private final DAO dao = new DAO(model);

    private void selectRegularUser() {
        mockAuthentication(USER);
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    private void selectWorkspaceManager() {
        mockAuthentication("workspace-admin");
        lenient().when(userService.currentUser()).thenReturn(workspaceManager);
    }

    private void selectAdmin() {
        mockAuthentication(ADMIN);
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    @Before
    public void before() {
        JenaProperties.setMetadataBaseIRI("http://localhost/iri/");
        workspaceService = new WorkspaceService(tx, userService);
        var vocabulary = model.read("test-vocabulary.ttl");
        var userVocabulary = model.read("vocabulary.ttl");
        factory = new DavFactory(
                model.createResource(baseUri),
                store,
                userService,
                context,
                new WebDavProperties(),
                userVocabulary,
                vocabulary);

        user = createTestUser("user", false);
        dao.write(user);
        workspaceManager = createTestUser("workspace-admin", false);
        dao.write(workspaceManager);
        admin = createTestUser("admin", true);
        dao.write(admin);

        setupRequestContext();
        request = getCurrentRequest();

        selectAdmin();
        workspace = workspaceService.createWorkspace(
                Workspace.builder().code("Test").build());
        workspaceService.setUserRole(workspace.getIri(), workspaceManager.getIri(), WorkspaceRole.Manager);
        workspaceService.setUserRole(workspace.getIri(), user.getIri(), WorkspaceRole.Member);

        selectRegularUser();
        when(request.getHeader("Owner")).thenReturn(workspace.getIri().getURI());
        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", 3, "md5"));
    }

    @Test
    public void testRoot() throws NotAuthorizedException, BadRequestException {
        var root = factory.getResource(null, BASE_PATH);

        assertTrue(root instanceof MakeCollectionableResource);
        assertFalse(root instanceof PutableResource);
    }

    @Test
    public void testCreateCollection() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = root.createCollection("coll");

        var collName = "coll";
        assertTrue(coll instanceof FolderResource);
        assertEquals(collName, coll.getName());
        assertNotNull(root.child(collName));
        assertNotNull(factory.getResource(null, format("/api/webdav/%s/", collName)));
        assertEquals(1, root.getChildren().size());
    }

    @Test
    public void testCreateCollectionStartingWithDash()
            throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = root.createCollection("-coll");

        var collName = "-coll";
        assertTrue(coll instanceof FolderResource);
        assertEquals(collName, coll.getName());
        assertNotNull(root.child(collName));
        assertNotNull(factory.getResource(null, format("/api/webdav/%s/", collName)));
        assertEquals(1, root.getChildren().size());
    }

    @Test
    public void testCreateCollectionWithTooLongName()
            throws NotAuthorizedException, ConflictException, BadRequestException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        try {
            root.createCollection("");
            fail("Empty collection labe; should be rejected.");
        } catch (BadRequestException e) {
            assertEquals("The collection name is empty.", e.getReason());
        }
        var tooLongName = "test123_56".repeat(20);
        try {
            root.createCollection(tooLongName);
            fail("Collection name should be rejected as too long.");
        } catch (BadRequestException e) {
            assertEquals("The collection name exceeds maximum length 127.", e.getReason());
        }
    }

    @Test
    public void testNonExistingResource() throws NotAuthorizedException, BadRequestException {
        assertNull(factory.getResource(null, BASE_PATH + "coll/dir/file"));
    }

    @Test
    public void testInaccessibleResource() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        root.createCollection("coll");

        var collName = "coll";
        model.removeAll(null, FS.canManage, model.createResource(baseUri + "/" + collName));

        assertTrue(root.getChildren().isEmpty());

        var coll = root.child(collName);
        for (var method : Request.Method.values()) {
            assertFalse("Shouldn't be able to " + method, coll.authorise(null, method, null));
        }
    }

    @Test
    public void testAdminAccess() throws NotAuthorizedException, BadRequestException, ConflictException {
        selectWorkspaceManager();
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var collName = "coll";
        root.createCollection(collName);

        selectAdmin();
        assertEquals(1, root.getChildren().size());
        assertEquals(Access.Manage, ((DavFactory) factory).getAccess(model.getResource(baseUri + "/" + collName)));

        model.removeAll(
                model.getResource(admin.getIri().getURI()),
                FS.isManagerOf,
                model.getResource(workspace.getIri().getURI()));
        assertEquals(Access.List, ((DavFactory) factory).getAccess(model.getResource(baseUri + "/" + collName)));
    }

    @Test(expected = ConflictException.class)
    public void testCreateCollectionTwiceFails() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        assertNotNull(root.createCollection("coll"));
        assertNull(root.createCollection("coll"));
        assertEquals(1, root.getChildren().size());
    }

    @Test
    public void testCreateCollectionWithSameNameButDifferentCaseDoesNotFail()
            throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        assertNotNull(root.createCollection("coll"));
        assertNotNull(root.createCollection("COLL"));
        assertEquals(2, root.getChildren().size());
    }

    @Test
    public void testCreateDirectory() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");
        var dir = coll.createCollection("dir");
        assertNotNull(dir);
        assertEquals("dir", dir.getName());
        assertEquals(1, coll.getChildren().size());
    }

    @Test
    public void testCreateFile() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");

        verifyNoInteractions(input, store);

        assertNotNull(factory.getResource(null, BASE_PATH + format("/%s/file", "coll")));

        assertTrue(file instanceof GetableResource);
        assertEquals("file", file.getName());
        assertEquals(FILE_SIZE, ((GetableResource) file).getContentLength().longValue());
        assertEquals("text/abc", ((GetableResource) file).getContentType(BASE_PATH));

        assertTrue(file instanceof MultiNamespaceCustomPropertyResource);

        assertEquals(1, ((MultiNamespaceCustomPropertyResource) file).getProperty(VERSION));
    }

    @Test
    public void testReadOnlyCollection() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        root.createCollection("coll");

        var collName = "coll";
        model.removeAll(null, FS.canManage, model.createResource(baseUri + "/" + collName))
                .add(
                        createResource(baseUri + "/" + collName),
                        FS.canRead,
                        model.createResource(baseUri + "/" + collName));

        assertFalse(root.child(collName).authorise(null, Request.Method.PUT, null));
    }

    @Test
    public void testOverwriteFile() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");

        assertTrue(file instanceof ReplaceableResource);

        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", FILE_SIZE + 1, "md5"));
        ((ReplaceableResource) file).replaceContent(input, FILE_SIZE + 1);

        verifyNoInteractions(input, store);

        var file2 = coll.child("file");
        assertEquals(2, ((MultiNamespaceCustomPropertyResource) file2).getProperty(VERSION));
        assertEquals(FILE_SIZE + 1, ((GetableResource) file2).getContentLength().longValue());
    }

    @Test
    public void testGetVersion() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");

        assertTrue(file instanceof ReplaceableResource);

        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", FILE_SIZE + 1, "md5"));
        ((ReplaceableResource) file).replaceContent(input, FILE_SIZE + 1);

        when(request.getHeader("Version")).thenReturn("1");
        var ver1 = coll.child("file");
        assertEquals(1, ((MultiNamespaceCustomPropertyResource) ver1).getProperty(VERSION));
        assertEquals(FILE_SIZE, ((GetableResource) ver1).getContentLength().longValue());

        when(request.getHeader("Version")).thenReturn("2");
        var ver2 = coll.child("file");
        assertEquals(2, ((MultiNamespaceCustomPropertyResource) ver2).getProperty(VERSION));
        assertEquals(FILE_SIZE + 1, ((GetableResource) ver2).getContentLength().longValue());
    }

    @Test
    public void testRevert() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");

        assertTrue(file instanceof ReplaceableResource);

        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", FILE_SIZE + 1, "md5"));
        ((ReplaceableResource) file).replaceContent(input, FILE_SIZE + 1);

        ((PostableResource) coll.child("file")).processForm(Map.of("action", "revert", "version", "1"), Map.of());

        var ver3 = coll.child("file");
        assertEquals(3, ((MultiNamespaceCustomPropertyResource) ver3).getProperty(VERSION));
        assertEquals(FILE_SIZE, ((GetableResource) ver3).getContentLength().longValue());
    }

    @Test
    public void testDeleteFile() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");
        assertTrue(file instanceof DeletableResource);
        ((DeletableResource) file).delete();

        verifyNoInteractions(input, store);

        assertEquals(0, coll.getChildren().size());
        assertNull(coll.child("file"));
    }

    @Test
    public void testShowDeletedFiles()
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");
        assertTrue(file instanceof DeletableResource);
        ((DeletableResource) file).delete();

        when(request.getHeader("Show-Deleted")).thenReturn("on");

        assertEquals(1, coll.getChildren().size());
        assertNotNull(coll.child("file"));

        var deleted = (MultiNamespaceCustomPropertyResource) coll.child("file");

        assertNotNull(deleted.getProperty(new QName(FS.NS, "dateDeleted")));
    }

    @Test
    public void testRestoreDeletedFiles()
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");
        assertTrue(file instanceof DeletableResource);
        ((DeletableResource) file).delete();

        when(request.getHeader("Show-Deleted")).thenReturn("on");
        var deleted = (PostableResource) coll.child("file");

        deleted.processForm(Map.of("action", "undelete"), Map.of());

        var restored = (MultiNamespaceCustomPropertyResource) coll.child("file");
        assertNull(restored.getProperty(new QName(FS.NS, "dateDeleted")));
    }

    @Test
    public void testPurgeFile() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");
        assertTrue(file instanceof DeletableResource);
        ((DeletableResource) file).delete();

        when(request.getHeader("Show-Deleted")).thenReturn("on");

        assertEquals(1, coll.getChildren().size());
        assertNotNull(coll.child("file"));

        var deleted = (DeletableResource) coll.child("file");

        try {
            deleted.delete();
            fail("Only admin can purge a collection.");
        } catch (NotAuthorizedException e) {
            assertNotNull(e);
            assertEquals(SC_FORBIDDEN, e.getRequiredStatusCode());
            assertEquals("Not authorized to purge the resource.", e.getMessage());
        }

        userService.currentUser().setAdmin(true);
        deleted.delete();

        assertNull(coll.child("file"));
    }

    @Test
    public void testRenameDirectory()
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var dir = coll.createCollection("old");
        ((FolderResource) dir).createNew("file", input, FILE_SIZE, "text/abc");

        ((MoveableResource) dir).moveTo(coll, "new");

        assertEquals(1, coll.getChildren().size());
        assertNull(coll.child("old"));
        assertNotNull(coll.child("new"));

        assertNull(factory.getResource(null, BASE_PATH + "/coll/old/file"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/coll/new/file"));
    }

    @Test
    public void testRenameFile() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("old", input, FILE_SIZE, "text/abc");

        ((MoveableResource) file).moveTo(coll, "new");

        assertEquals(1, coll.getChildren().size());
        assertNull(coll.child("old"));
        assertNotNull(coll.child("new"));
    }

    @Test
    public void testRenameCollection()
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("old");

        var dir = coll.createCollection("dir");
        ((FolderResource) dir).createNew("file", input, FILE_SIZE, "text/abc");

        coll.moveTo(root, "new");

        assertNull(factory.getResource(null, BASE_PATH + "/old"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/new"));
        assertNull(factory.getResource(null, BASE_PATH + "/old/dir"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/new/dir"));
        assertNull(factory.getResource(null, BASE_PATH + "/old/dir/file"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/new/dir/file"));
    }

    @Test(expected = ConflictException.class)
    public void testRenameCollectionToExistingFails()
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        root.createCollection("coll1");

        var coll2 = (FolderResource) root.createCollection("coll2");
        var dir = coll2.createCollection("dir");
        ((FolderResource) dir).createNew("file", input, FILE_SIZE, "text/abc");

        coll2.moveTo(root, "coll1");
    }

    @Test
    public void testCopyDirectory() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll1 = (FolderResource) root.createCollection("c1");
        var coll2 = (FolderResource) root.createCollection("c2");

        var dir1 = (MakeCollectionableResource) coll1.createCollection("dir1");
        var subdir = dir1.createCollection("old");
        ((FolderResource) subdir).createNew("file", input, FILE_SIZE, "text/abc");

        var dir2 = coll2.createCollection("dir2");

        ((MoveableResource) subdir).moveTo(dir2, "new");

        assertNull(factory.getResource(null, BASE_PATH + "/c1/dir1/old"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/c2/dir2/new"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/c2/dir2/new/file"));
    }

    @Test(expected = ConflictException.class)
    public void testCopyCollectionToExistingFails()
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        root.createCollection("coll1");

        var coll2 = (FolderResource) root.createCollection("coll2");
        var dir = coll2.createCollection("dir");
        ((FolderResource) dir).createNew("file", input, FILE_SIZE, "text/abc");

        coll2.copyTo(root, "coll1");
    }
}
