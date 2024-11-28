package io.fairspace.saturn.webdav;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;

import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.DeletableResource;
import io.milton.resource.FolderResource;
import io.milton.resource.GetableResource;
import io.milton.resource.MakeCollectionableResource;
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

import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;
import io.fairspace.saturn.webdav.blobstore.BlobStore;
import io.fairspace.saturn.webdav.blobstore.DeletableLocalBlobStore;
import io.fairspace.saturn.webdav.resources.DirectoryResource;
import io.fairspace.saturn.webdav.resources.ExtraStorageRootResource;
import io.fairspace.saturn.webdav.resources.FileResource;

import static io.fairspace.saturn.TestUtils.ADMIN;
import static io.fairspace.saturn.TestUtils.USER;
import static io.fairspace.saturn.TestUtils.createTestUser;
import static io.fairspace.saturn.TestUtils.mockAuthentication;
import static io.fairspace.saturn.TestUtils.setupRequestContext;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;

import static java.lang.String.format;
import static org.apache.jena.query.DatasetFactory.createTxnMem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DavFactoryExtraStorageTest {
    static final long FILE_SIZE = 3L;
    static final String EXTRA_STORAGE_PATH = "/api/extra-storage";
    static final QName VERSION = new QName(FS.NS, "version");
    static final String extraStorageUri = "http://example.com" + EXTRA_STORAGE_PATH;

    @Mock(extraInterfaces = BlobStore.class)
    DeletableLocalBlobStore store;

    @Mock
    InputStream input;

    @Mock
    UserService userService;

    Context context = new Context();
    User user;
    User admin;
    private HttpServletRequest request;

    private ResourceFactory factory;
    private final Dataset ds = createTxnMem();
    private final Model model = ds.getDefaultModel();

    private final String defaultExtraStorageRootName = "analysis-export";

    private void selectRegularUser() {
        mockAuthentication(USER);
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    private void selectAdmin() {
        mockAuthentication(ADMIN);
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    @Before
    public void before() {
        WebDavProperties webDavProperties = new WebDavProperties();
        webDavProperties.setBlobStorePath("db");
        var extraStorage = new WebDavProperties.ExtraStorage();
        extraStorage.setBlobStorePath("db/extra");
        extraStorage.setDefaultRootCollections(List.of(defaultExtraStorageRootName));
        webDavProperties.setExtraStorage(extraStorage);
        var vocabulary = model.read("test-vocabulary.ttl");
        var userVocabulary = model.read("vocabulary.ttl");
        factory = new DavFactory(
                model.createResource(extraStorageUri),
                store,
                userService,
                context,
                webDavProperties,
                userVocabulary,
                vocabulary);

        user = createTestUser("user", false);
        new DAO(model).write(user);

        admin = createTestUser("admin", true);
        new DAO(model).write(admin);

        setupRequestContext();
        request = getCurrentRequest();
        selectRegularUser();
        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", 3, "md5"));
    }

    @Test
    public void testExtraStorageRoot() throws NotAuthorizedException, BadRequestException {
        var root = factory.getResource(null, EXTRA_STORAGE_PATH);

        assertTrue(root instanceof MakeCollectionableResource);
        assertTrue(root instanceof ExtraStorageRootResource);
        assertFalse(root instanceof PutableResource);
    }

    @Test
    public void testCreateRootExtraStorageFolder()
            throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, EXTRA_STORAGE_PATH);
        var coll = root.createCollection(defaultExtraStorageRootName);

        var collName = defaultExtraStorageRootName;
        assertTrue(coll instanceof FolderResource);
        assertEquals(collName, coll.getName());
        assertNotNull(root.child(collName));
        assertNotNull(factory.getResource(null, format("/api/extra-storage/%s/", collName)));
        assertEquals(1, root.getChildren().size());
    }

    @Test
    public void testNonExistingExtraStorageResource() throws NotAuthorizedException, BadRequestException {
        assertNull(factory.getResource(null, EXTRA_STORAGE_PATH + "coll/dir/file"));
    }

    @Test
    public void testOverwriteFile() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, EXTRA_STORAGE_PATH);
        var coll = (FolderResource) root.createCollection(defaultExtraStorageRootName);

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
        var root = (MakeCollectionableResource) factory.getResource(null, EXTRA_STORAGE_PATH);
        var coll = (FolderResource) root.createCollection(defaultExtraStorageRootName);

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
    public void testExtraStorageAccess()
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = factory.getResource(null, EXTRA_STORAGE_PATH);
        var rootResource = model.createResource(extraStorageUri);
        var extraStorageSubdir1 = ((DavFactory) factory).root.createCollection(defaultExtraStorageRootName);
        var subdirResource = model.createResource(extraStorageUri + "/" + defaultExtraStorageRootName);
        var file = ((FolderResource) extraStorageSubdir1).createNew("file", input, FILE_SIZE, "text/abc");
        var fileResource = model.createResource(extraStorageUri + "/" + defaultExtraStorageRootName + "/file");

        assertTrue(root instanceof ExtraStorageRootResource);
        assertTrue(extraStorageSubdir1 instanceof DirectoryResource);
        assertTrue(file instanceof FileResource);

        assertEquals(Access.Read, ((DavFactory) factory).getAccess(rootResource));
        assertEquals(Access.Write, ((DavFactory) factory).getAccess(subdirResource));
        assertEquals(Access.Write, ((DavFactory) factory).getAccess(fileResource));

        selectAdmin();
        assertEquals(Access.Read, ((DavFactory) factory).getAccess(rootResource));
        assertEquals(Access.Write, ((DavFactory) factory).getAccess(subdirResource));
        assertEquals(Access.None, ((DavFactory) factory).getAccess(fileResource));
    }

    @Test
    public void testDeleteExtraStoreFile()
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var extraStorageSubdir1 = ((DavFactory) factory).root.createCollection(defaultExtraStorageRootName);
        var file = ((FolderResource) extraStorageSubdir1).createNew("file", input, FILE_SIZE, "text/abc");

        assertTrue(file instanceof DeletableResource);
        ((DeletableResource) file).delete();

        verify(store, times(1)).delete("id"); // check if blob is deleted

        assertEquals(0, extraStorageSubdir1.getChildren().size());
        assertNull(extraStorageSubdir1.child("file"));
    }

    @Test
    public void testDeleteAllInFolder()
            throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, EXTRA_STORAGE_PATH);
        var coll = (FolderResource) root.createCollection(defaultExtraStorageRootName);
        coll.createNew("file1", input, FILE_SIZE, "text/abc");
        coll.createNew("file2", input, FILE_SIZE, "text/abc");
        coll.createNew("file3", input, FILE_SIZE, "text/abc");

        ((PostableResource) root.child(defaultExtraStorageRootName))
                .processForm(Map.of("action", "delete_all_in_directory"), Map.of());

        verify(store, times(3)).delete(any()); // check if all blob are deleted

        assertEquals(0, coll.getChildren().size());
        assertNull(coll.child("file1"));
        assertNull(coll.child("file2"));
        assertNull(coll.child("file3"));
    }

    @Test(expected = NotAuthorizedException.class)
    public void testThrowsErrorWhenNonDefaultRootDirectoryName()
            throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, EXTRA_STORAGE_PATH);
        root.createCollection("coll1");
    }

    @Test()
    public void testThrowsErrorWhenDeletingRootDirectory()
            throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, EXTRA_STORAGE_PATH);
        var coll = (FolderResource) root.createCollection(defaultExtraStorageRootName);
        assertThrows(NotAuthorizedException.class, coll::delete);
    }
}
