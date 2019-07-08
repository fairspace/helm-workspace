package io.fairspace.saturn.vfs;

import io.fairspace.saturn.services.collections.Collection;
import io.fairspace.saturn.services.collections.CollectionsService;
import io.fairspace.saturn.vfs.managed.ManagedFileSystem;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static io.fairspace.saturn.vfs.PathUtils.splitPath;
import static java.util.stream.Collectors.toList;

public class CompoundFileSystem implements VirtualFileSystem {
    private final CollectionsService collections;
    private final Map<? super String, ? extends VirtualFileSystem> fileSystemsByType;

    public CompoundFileSystem(CollectionsService collections, Map<? super String, ? extends VirtualFileSystem> fileSystemsByType) {
        this.collections = collections;
        this.fileSystemsByType = fileSystemsByType;
    }

    @Override
    public FileInfo stat(String path) throws IOException {
        var fs = fileSystemByPath(path);
        if (fs == null) {
            return null;
        }
        return fs.stat(path);
    }

    @Override
    public List<FileInfo> list(String parentPath) throws IOException {
        if (parentPath.isEmpty()) {
            return collections.list()
                    .stream()
                    .map(CompoundFileSystem::fileInfo)
                    .collect(toList());
        }
        return fileSystemByPath(parentPath).list(parentPath);
    }

    @Override
    public void mkdir(String path) throws IOException {
        fileSystemByPath(path).mkdir(path);
    }

    @Override
    public void create(String path, InputStream in) throws IOException {
        fileSystemByPath(path).create(path, in);
    }

    @Override
    public void modify(String path, InputStream in) throws IOException {
        fileSystemByPath(path).modify(path, in);
    }

    @Override
    public void read(String path, OutputStream out) throws IOException {
        fileSystemByPath(path).read(path, out);
    }

    @Override
    public void copy(String from, String to) throws IOException {
        if (fileSystemByPath(from).equals(fileSystemByPath(to))) {
            fileSystemByPath(from).copy(from, to);
        } else {
            throw new IOException("Copying files between collections of different types is not implemented yet");
        }
    }

    @Override
    public void move(String from, String to) throws IOException {
        if (fileSystemByPath(from).equals(fileSystemByPath(to))) {
            fileSystemByPath(from).move(from, to);
        } else {
            throw new IOException("Moving files between collections of different types is not implemented yet");
        }
    }

    @Override
    public void delete(String path) throws IOException {
        fileSystemByPath(path).delete(path);
    }

    @Override
    public void close() throws IOException {
        for (var c: fileSystemsByType.values()) {
            c.close();
        }
    }

    private VirtualFileSystem fileSystemByPath(String path) throws IOException {
        var collection = collections.getByLocation(splitPath(path)[0]);
        if (collection == null) {
            throw new FileNotFoundException(path);
        }
        return fileSystemsByType.get(collectionType(collection));
    }

    private static FileInfo fileInfo(Collection collection) {
        return FileInfo.builder()
                .iri(collection.getIri().getURI())
                .path(collection.getLocation())
                .size(0)
                .isDirectory(true)
                .created(collection.getDateCreated())
                .modified(collection.getDateCreated())
                .readOnly(!collection.getAccess().canWrite())
                .build();
    }

    private static String collectionType(Collection collection) throws IOException {
        try {
            return collection.getConnectionString().isEmpty()
                    ? ManagedFileSystem.TYPE
                    : new URI(collection.getConnectionString()).getScheme();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }
}
