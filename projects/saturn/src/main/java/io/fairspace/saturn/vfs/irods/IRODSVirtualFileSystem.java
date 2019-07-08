package io.fairspace.saturn.vfs.irods;

import io.fairspace.saturn.services.collections.Collection;
import io.fairspace.saturn.services.collections.CollectionsService;
import io.fairspace.saturn.vfs.FileInfo;
import io.fairspace.saturn.vfs.VirtualFileSystem;
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.CollectionAndDataObjectListAndSearchAO;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.domain.ObjStat;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.pub.io.IRODSFileInputStream;
import org.irods.jargon.core.pub.io.IRODSFileOutputStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static io.fairspace.saturn.vfs.PathUtils.*;
import static java.time.Instant.ofEpochMilli;
import static org.apache.commons.io.IOUtils.copyLarge;
import static org.irods.jargon.core.query.CollectionAndDataObjectListingEntry.ObjectType.COLLECTION;
import static org.irods.jargon.core.query.CollectionAndDataObjectListingEntry.ObjectType.LOCAL_DIR;

public class IRODSVirtualFileSystem implements VirtualFileSystem {
    public static final String TYPE = "irods";

    private final IRODSFileSystem fs;
    private final CollectionsService collections;

    public IRODSVirtualFileSystem(CollectionsService collections) {
        try {
            this.fs = IRODSFileSystem.instance();
        } catch (JargonException e) {
            throw new RuntimeException(e);
        }
        this.collections = collections;
    }

    @Override
    public FileInfo stat(String path) throws IOException {
        try {
            var collection = collectionByPath(path);
            var account = accountForCollection(collection);
            var f = getFile(path);
            if (!f.exists()) {
                return null;
            }

            var stat = getAccessObject(account).retrieveObjectStatForPath(f.getAbsolutePath());

            return FileInfo.builder()
                    .iri(getIri(account, stat))
                    .path(path)
                    .isDirectory(isDirectory(stat))
                    .readOnly(!collection.canWrite() || !f.canWrite())
                    .created(ofEpochMilli(stat.getCreatedAt().getTime()))
                    .modified(ofEpochMilli(stat.getModifiedAt().getTime()))
                    .size(stat.getObjSize())
                    .build();
        } catch (JargonException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileInfo> list(String parentPath) throws IOException {
        try {
            var collection = collectionByPath(parentPath);
            var account = accountForCollection(collection);
            var f = getFile(parentPath);

            var cao = getAccessObject(account);

            var result = new ArrayList<FileInfo>();

            for (var child : f.listFiles()) {
                var stat = cao.retrieveObjectStatForPath(child.getAbsolutePath());
                result.add(FileInfo.builder()
                        .iri(getIri(account, stat))
                        .path(parentPath + "/" + child.getName())
                        .isDirectory(isDirectory(stat))
                        .readOnly(!collection.canWrite() || !child.canWrite())
                        .created(ofEpochMilli(stat.getCreatedAt().getTime()))
                        .modified(ofEpochMilli(stat.getModifiedAt().getTime()))
                        .size(stat.getObjSize())
                        .build());
            }
            return result;
        } catch (JargonException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void mkdir(String path) throws IOException {
        getFile(path).mkdir();
    }

    @Override
    public void create(String path, InputStream in) throws IOException {
        getFile(path).createNewFile();
        modify(path, in);
    }

    @Override
    public void modify(String path, InputStream in) throws IOException {
        try (var out = getOutputStream(path)) {
            copyLarge(in, out);
        }
    }

    @Override
    public void read(String path, OutputStream out) throws IOException {
        try (var in = getInputStream(path)) {
            copyLarge(in, out);
        }
    }

    @Override
    public void copy(String from, String to) throws IOException {
        try {
            var fromAccount = getAccount(from);
            var toAccount = getAccount(to);
            if (fromAccount.equals(toAccount)) {
                getDataTransferOperations(fromAccount).copy(getIrodsPath(from), fromAccount.getDefaultStorageResource(), getIrodsPath(to), null, null);
                // TODO: Copy metadata as well
            } else {
                throw new IOException("Copying files between different iRODS accounts is not implemented yet");
            }
        } catch (JargonException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void move(String from, String to) throws IOException {
        try {
            var fromAccount = getAccount(from);
            var toAccount = getAccount(to);
            if (fromAccount.equals(toAccount)) {
                getDataTransferOperations(fromAccount).move(getIrodsPath(from), getIrodsPath(to));
            } else {
                throw new IOException("Moving files between different iRODS accounts is not implemented yet");
            }
        } catch (JargonException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void delete(String path) throws IOException {
        getFile(path).delete();
    }

    @Override
    public void close() throws IOException {
        try {
            fs.close();
        } catch (JargonException e) {
            throw new IOException(e);
        }
    }

    private IRODSFile getFile(String path) throws IOException {
        var account = getAccount(path);
        try {
            return getIrodsFileFactory(account).instanceIRODSFile(getIrodsPath(path));
        } catch (JargonException e) {
            throw new IOException(e);
        }
    }

    private IRODSAccount getAccount(String path) throws IOException {
        return accountForCollection(collectionByPath(path));
    }

    private Collection collectionByPath(String parentPath) throws FileNotFoundException {
        var collection = collections.getByLocation(splitPath(parentPath)[0]);
        if (collection == null) {
            throw new FileNotFoundException(parentPath);
        }
        return collection;
    }

    private String getIrodsPath(String path) throws IOException {
        return "/" + joinPaths(getAccount(path).getHomeDirectory(), subPath(path));
    }

    private IRODSFileInputStream getInputStream(String path) throws IOException {
        var account = getAccount(path);
        try {
            return getIrodsFileFactory(account).instanceIRODSFileInputStream(getIrodsPath(path));
        } catch (JargonException e) {
            throw new IOException(e);
        }
    }

    private IRODSFileOutputStream getOutputStream(String path) throws IOException {
        var account = getAccount(path);
        try {
            return getIrodsFileFactory(account).instanceIRODSFileOutputStream(getIrodsPath(path));
        } catch (JargonException e) {
            throw new IOException(e);
        }
    }

    private CollectionAndDataObjectListAndSearchAO getAccessObject(IRODSAccount account) throws JargonException {
        return fs.getIRODSAccessObjectFactory().getCollectionAndDataObjectListAndSearchAO(account);
    }

    private DataTransferOperations getDataTransferOperations(IRODSAccount fromAccount) throws JargonException {
        return fs.getIRODSAccessObjectFactory().getDataTransferOperations(fromAccount);
    }

    private IRODSFileFactory getIrodsFileFactory(IRODSAccount account) throws JargonException {
        return fs.getIRODSAccessObjectFactory().getIRODSFileFactory(account);
    }

    private static boolean isDirectory(ObjStat stat) {
        return stat.getObjectType() == COLLECTION || stat.getObjectType() == LOCAL_DIR;
    }

    private static String getIri(IRODSAccount account, ObjStat stat) {
        return "irods://" + account.getHost() + "#" + stat.getDataId();
    }

    private static IRODSAccount accountForCollection(Collection collection) throws IOException {
        try {
            var uri = new URI(collection.getConnectionString());
            var userInfo = uri.getUserInfo().split("[.:]");
            return IRODSAccount.instance(
                    uri.getHost(),
                    uri.getPort(),
                    userInfo[0],
                    userInfo[2],
                    uri.getPath(),
                    userInfo[1],
                    "",
                    new ClientServerNegotiationPolicy()
            );
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
