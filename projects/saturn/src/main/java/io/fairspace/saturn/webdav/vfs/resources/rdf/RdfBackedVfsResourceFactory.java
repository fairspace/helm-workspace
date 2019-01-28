package io.fairspace.saturn.webdav.vfs.resources.rdf;

import io.fairspace.saturn.webdav.vfs.resources.VfsDirectoryResource;
import io.fairspace.saturn.webdav.vfs.resources.VfsFairspaceCollectionResource;
import io.fairspace.saturn.webdav.vfs.resources.VfsFileResource;
import io.fairspace.saturn.webdav.vfs.resources.VfsResource;
import io.fairspace.saturn.webdav.vfs.resources.VfsResourceFactory;
import io.fairspace.saturn.webdav.vfs.resources.VfsRootResource;
import lombok.NonNull;
import org.apache.commons.lang.StringUtils;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfconnection.RDFConnection;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.CONTENT_LOCATION;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.CONTENT_TYPE;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.CREATOR;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.DATE_CREATED;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.DATE_MODIFIED;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.FILESIZE;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.NAME;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.PARENT;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.PATH;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.RDF_TYPE;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.TYPE_COLLECTION;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.TYPE_DIRECTORY;
import static io.fairspace.saturn.webdav.vfs.resources.rdf.VirtualFileSystemIris.TYPE_FILE;

/**
 * VFS Resource factory backed by a RDF triple store
 *
 * Please note that paths provided as parameters must not contain a trailing slash
 *
 * @TODO: Add transactions
 */
public class RdfBackedVfsResourceFactory implements VfsResourceFactory {
    private static final VfsResource ROOT_RESOURCE = new VfsRootResource();
    private RDFConnection connection;

    public RdfBackedVfsResourceFactory(RDFConnection connection) {
        this.connection = connection;
    }

    @Override
    public VfsResource getResource(@NonNull String path) {
        // Special case for the root resource
        if(StringUtils.isEmpty(path) || path.equals("/")) {
            return ROOT_RESOURCE;
        }

        // Retrieve information on the resource from the RDF store
        // This includes at least all triples regarding the subject
        // with the given path
        // Additionally, it includes a creator and its properties, if present
        ParameterizedSparqlString sparql = new ParameterizedSparqlString();
        sparql.setCommandText("CONSTRUCT " +
                "{ ?s ?p ?o . ?creator ?p2 ?o2 } " +
                "WHERE " +
                "{ " +
                    "?s ?filePathPredicate ?path ; ?p ?o . " +
                    "OPTIONAL { ?s ?creatorPredicate ?creator . ?creator ?p2 ?o2 }" +
                "}");
        sparql.setIri("filePathPredicate", PATH.getURI());
        sparql.setIri("creatorPredicate", CREATOR.getURI());
        sparql.setLiteral("path", path);

        // Retrieve the data
        Model model = connection.queryConstruct(sparql.asQuery());

        // If no results are found, stop now
        if(model.isEmpty()) {
            return null;
        }

        // Retrieve the resource (iri) for the current path.
        ResIterator resourceIterator = model.listResourcesWithProperty(PATH);
        Resource rdfResource = resourceIterator.next();

        // Sanity check: we expect only a single subject containing a path
        // (in fact, there could be multiple subjects, e.g. for the resource and its creator
        // but if there are multiple resources with a path, it indicates that we have multiple
        // resources in our query);
        if(resourceIterator.hasNext()) {
            throw new IllegalStateException("Number of metadata subjects returned for path " + path + " is larger than 1");
        }

        // Convert to the correct vfsResource
        return toVfsResource(model, rdfResource);
    }

    @Override
    public VfsDirectoryResource createDirectory(String parentId, @NonNull String path) {
        // The toplevel directories are modelled after fairspace collections. If
        // parentId is null, the user tries to create a toplevel directory, which
        // should be done via the Collections API
        if(parentId == null) {
            return null;
        }

        if(!doesPathMatchParent(parentId, path)) {
            throw new IllegalArgumentException("Given path " + path + " does not match the path of the parent " + parentId);
        }

        // Generate a new model for this collection
        // TODO: Store creator
        Model model = ModelFactory.createDefaultModel();
        Resource collection = model.createResource(generateIri(path));
        model.add(collection, RDF_TYPE, TYPE_DIRECTORY);
        model.add(collection, NAME, baseName(path));
        model.add(collection, PATH, path);
        model.add(collection, DATE_CREATED, ZonedDateTime.now().toString());
        model.add(collection, DATE_MODIFIED, ZonedDateTime.now().toString());
        model.add(collection, PARENT, model.createResource(parentId));

        // Store new triples in rdf store
        connection.load(model);

        return new RdfDirectoryResource(collection, model);
    }

    @Override
    public List<? extends VfsResource> getChildren(String parentId) {
        if(parentId == null) {
            return this.getFairspaceCollectionResources();
        } else {
            return this.getChildrenFromStore(parentId);
        }
    }

    private List<VfsFairspaceCollectionResource> getFairspaceCollectionResources() {
        // TODO: Invoke collections api here
        ParameterizedSparqlString sparql = new ParameterizedSparqlString();
        sparql.setCommandText("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?type ?collection ; ?p ?o }");
        sparql.setIri("type", RDF_TYPE.getURI());
        sparql.setIri("collection", TYPE_COLLECTION.getURI());

        // Retrieve the data
        Model model = connection.queryConstruct(sparql.asQuery());

        // If no results are found, stop now
        if(model.isEmpty()) {
            return Collections.emptyList();
        }

        // Loop through all subjects in the model and return the resource in a list
        return model.listSubjects()
                .mapWith(rdfResource -> (VfsFairspaceCollectionResource) toVfsResource(model, rdfResource))
                .toList();

    }

    private List<? extends VfsResource> getChildrenFromStore(@NonNull String parentId) {
        ParameterizedSparqlString sparql = new ParameterizedSparqlString();
        sparql.setCommandText("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?parent ?parentId ; ?p ?o }");
        sparql.setIri("parent", PARENT.getURI());
        sparql.setIri("parentId", parentId);
        sparql.setLiteral("true", "true");

        // Retrieve the data
        Model model = connection.queryConstruct(sparql.asQuery());

        // If no results are found, stop now
        if(model.isEmpty()) {
            return Collections.emptyList();
        }

        // Loop through all subjects in the model and return the resource in a list
        return model.listSubjects()
                .mapWith(rdfResource -> toVfsResource(model, rdfResource))
                .toList();

    }

    @Override
    public VfsFileResource storeFile(String parentId, String path, Long fileSize, String contentType, String contentLocation) {
        // The toplevel directories are modelled after fairspace collections
        // The user can not create files outside a collection
        if(parentId == null) {
            return null;
        }

        // Verify whether the path matches the path of the parent
        if(!doesPathMatchParent(parentId, path)) {
            throw new IllegalArgumentException("Given path " + path + " does not match the path of the parent " + parentId);
        }

        // Overwrite file if it already exists
        VfsResource existingResource = getResource(path);
        if(existingResource == null) {
            return createFile(parentId, path, fileSize, contentType, contentLocation);
        } else {
            return updateFile(existingResource, fileSize, contentType, contentLocation);
        }
    }

    @Override
    public VfsFileResource updateFile(VfsResource resource, Long fileSize, String contentType, String contentLocation) {
        // Update an existing file in the triple store
        // Please note that it expects a dateModified and a contentLocation triple to be present
        ParameterizedSparqlString deleteCommand = new ParameterizedSparqlString();

        deleteCommand.setCommandText(
                "DELETE WHERE { ?subject ?dateModified ?o1 } ; " +
                "DELETE WHERE { ?subject ?fileSize ?o2 } ; " +
                "DELETE WHERE { ?subject ?contentLocation ?o3 } ; " +
                "DELETE WHERE { ?subject ?contentType ?o4 }");
        deleteCommand.setIri("subject", resource.getUniqueId());
        deleteCommand.setIri("dateModified", DATE_MODIFIED.getURI());
        deleteCommand.setIri("fileSize", FILESIZE.getURI());
        deleteCommand.setIri("contentType", CONTENT_TYPE.getURI());
        deleteCommand.setIri("contentLocation", CONTENT_LOCATION.getURI());

        ParameterizedSparqlString insertCommand = new ParameterizedSparqlString();
        insertCommand.setCommandText("INSERT DATA { ?subject ?dateModified ?now ; ?fileSize ?size ; ?contentLocation ?location ; ?contentType ?type }");
        insertCommand.setIri("subject", resource.getUniqueId());
        insertCommand.setIri("dateModified", DATE_MODIFIED.getURI());
        insertCommand.setIri("fileSize", FILESIZE.getURI());
        insertCommand.setIri("contentType", CONTENT_TYPE.getURI());
        insertCommand.setIri("contentLocation", CONTENT_LOCATION.getURI());
        insertCommand.setLiteral("now", ZonedDateTime.now().toString());
        insertCommand.setLiteral("size", FileSize.format(fileSize));
        insertCommand.setLiteral("location", contentLocation);
        insertCommand.setLiteral("type", contentType);

        // Send both commands in a single transaction
        connection.update(deleteCommand.toString() + "\n;\n" + insertCommand.toString());

        // Return a full representation of the file
        return (VfsFileResource) getResource(resource.getPath());
    }

    private VfsFileResource createFile(String parentId, String path, Long fileSize, String contentType, String contentLocation) {
        // Generate a new model for this file
        // TODO Store creator
        Model model = ModelFactory.createDefaultModel();
        Resource file = model.createResource(generateIri(path));
        model.add(file, RDF_TYPE, TYPE_FILE);
        model.add(file, NAME, baseName(path));
        model.add(file, PATH, path);
        model.add(file, DATE_CREATED, ZonedDateTime.now().toString());
        model.add(file, DATE_MODIFIED, ZonedDateTime.now().toString());
        model.add(file, PARENT, model.createResource(parentId));

        model.add(file, FILESIZE, FileSize.format(fileSize));

        if(contentType != null) {
            model.add(file, CONTENT_TYPE, contentType);
        }
        model.add(file, CONTENT_LOCATION, contentLocation);

        // Store new triples in rdf store
        connection.load(model);

        // Return the resource to be used immediately
        return new RdfFileResource(file, model);
    }

    private RdfAbstractResource toVfsResource(Model model, Resource rdfResource) {
        // Determine the resource type
        Statement typeTriple = model.getProperty(rdfResource, RDF_TYPE);
        if(typeTriple == null) {
            throw new IllegalStateException("No type specified for metadata entity with id " + rdfResource.toString());
        }

        if(typeTriple.getObject().equals(TYPE_DIRECTORY)) {
            return new RdfDirectoryResource(rdfResource, model);
        } else if(typeTriple.getObject().equals(TYPE_FILE)) {
            return new RdfFileResource(rdfResource, model);
        } else if(typeTriple.getObject().equals(TYPE_COLLECTION)) {
            return new RdfFairspaceCollectionResource(rdfResource, model);
        } else {
            throw new IllegalStateException("Invalid type specified for metadata entity with id " + rdfResource.toString() + ": " + typeTriple.getObject().toString());
        }
    }

    /**
     * Checks whether the given path matches the parent path, i.e. the path is a direct descendant of the
     * path of its parent
     *
     * @param parentId  URI of the parent resource
     * @param path      Path of the child resource
     * @return
     */
    private boolean doesPathMatchParent(String parentId, String path) {
        // Check whether the model contains the parent resource
        // and its path matches the requested path
        ParameterizedSparqlString sparql = new ParameterizedSparqlString();
        sparql.setCommandText("ASK { ?parent ?path ?parentPath }");
        sparql.setIri("parent", parentId);
        sparql.setIri("path", PATH.getURI());
        sparql.setLiteral("parentPath", parentPath(path));

        // Check whether the data is consistent
        return connection.queryAsk(sparql.toString());
    }

    private String generateIri(String path) {
        // TODO: Improve Iri generation to be unique and deterministic
        // TODO: Use configured workspace URI
        return "http://workspace/webdav" + path;
    }

    private String baseName(String path) {
        if (!path.contains("/") || path.endsWith("/")) {
            throw new IllegalArgumentException("Invalid path $path");
        } else {
            return path.substring(path.lastIndexOf("/") + 1);
        }
    }

    private String parentPath(String path) {
        if (!path.contains("/") || path.endsWith("/")) {
            throw new IllegalArgumentException("Invalid path ");
        } else {
            return path.substring(0, path.lastIndexOf("/"));
        }
    }

}
