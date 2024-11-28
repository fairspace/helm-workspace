package io.fairspace.saturn.webdav;

import java.net.URI;

import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.Resource;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.RDF;

import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.blobstore.BlobStore;
import io.fairspace.saturn.webdav.resources.CollectionResource;
import io.fairspace.saturn.webdav.resources.CollectionRootResource;
import io.fairspace.saturn.webdav.resources.DirectoryResource;
import io.fairspace.saturn.webdav.resources.ExtraStorageRootResource;
import io.fairspace.saturn.webdav.resources.FileResource;
import io.fairspace.saturn.webdav.resources.RootResource;

import static io.fairspace.saturn.auth.RequestContext.getUserURI;
import static io.fairspace.saturn.util.EnumUtils.max;
import static io.fairspace.saturn.util.EnumUtils.min;
import static io.fairspace.saturn.webdav.AccessMode.DataPublished;
import static io.fairspace.saturn.webdav.AccessMode.MetadataPublished;
import static io.fairspace.saturn.webdav.PathUtils.encodePath;
import static io.fairspace.saturn.webdav.WebDAVServlet.isMetadataRequest;
import static io.fairspace.saturn.webdav.WebDAVServlet.showDeleted;

public class DavFactory implements ResourceFactory {
    public final org.apache.jena.rdf.model.Resource rootSubject;
    public final BlobStore store;
    public final UserService userService;
    public final Context context;
    public final RootResource root;
    public final Model userVocabulary;
    public final Model vocabulary;
    // Represents the root URI, not stored in the database
    private final String baseUri;

    public DavFactory(
            org.apache.jena.rdf.model.Resource rootSubject,
            BlobStore store,
            UserService userService,
            Context context,
            WebDavProperties webDavProperties,
            Model userVocabulary,
            Model vocabulary) {
        this.rootSubject = rootSubject;
        this.store = store;
        this.userService = userService;
        this.context = context;
        this.userVocabulary = userVocabulary;
        this.vocabulary = vocabulary;
        var uri = URI.create(rootSubject.getURI());
        this.baseUri = URI.create(
                        uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""))
                .toString();
        root = uri.toString().endsWith("/api/webdav")
                ? new CollectionRootResource(this)
                : new ExtraStorageRootResource(this, webDavProperties);
    }

    @Override
    public Resource getResource(String host, String path) throws NotAuthorizedException {
        return getResource(this.rootSubject.getModel().createResource(baseUri + "/" + encodePath(path)));
    }

    public Resource getResource(org.apache.jena.rdf.model.Resource subject) {
        if (subject.equals(this.rootSubject)) {
            return root;
        }

        if (!subject.getModel().containsResource(subject) || subject.hasProperty(FS.movedTo)) {
            return null;
        }

        return getResource(subject, getAccess(subject));
    }

    public Resource getResource(org.apache.jena.rdf.model.Resource subject, Access access) {
        if (isExtraStoreResource()
                && subject.hasProperty(RDF.type, FS.File)
                && !subject.hasProperty(FS.createdBy, currentUserResource())) {
            return null;
        }
        if (subject.hasProperty(FS.dateDeleted) && !showDeleted()) {
            return null;
        }
        if (subject.hasProperty(FS.movedTo)) {
            return null;
        }
        return getResourceByType(subject, access);
    }

    public Resource getResourceByType(org.apache.jena.rdf.model.Resource subject, Access access) {
        if (subject.hasProperty(RDF.type, FS.File)) {
            return new FileResource(this, subject, access, userVocabulary);
        }
        if (subject.hasProperty(RDF.type, FS.Directory)) {
            return new DirectoryResource(this, subject, access, userVocabulary, vocabulary);
        }
        if (subject.hasProperty(RDF.type, FS.Collection)) {
            return new CollectionResource(this, subject, access, userVocabulary, vocabulary);
        }
        if (subject.hasProperty(RDF.type, FS.ExtraStorageDirectory)) {
            return new DirectoryResource(this, subject, access, userVocabulary, vocabulary);
        }

        return null;
    }

    public boolean isExtraStoreResource() {
        return root instanceof ExtraStorageRootResource;
    }

    public Access getAccess(org.apache.jena.rdf.model.Resource subject) {
        if (isExtraStoreResource()) {
            return getExtraStorageAccess(subject);
        }

        var uri = subject.getURI();
        var nextSeparatorPos = uri.indexOf('/', rootSubject.getURI().length() + 1);
        var coll =
                rootSubject.getModel().createResource(nextSeparatorPos < 0 ? uri : uri.substring(0, nextSeparatorPos));
        if (!coll.hasProperty(RDF.type, FS.Collection)) {
            return Access.None;
        }

        var user = currentUserResource();
        var ownerWs = coll.getPropertyResourceValue(FS.ownedBy);
        var deleted = coll.hasProperty(FS.dateDeleted) || (ownerWs != null && ownerWs.hasProperty(FS.dateDeleted));

        var access = getGrantedPermission(coll, user);

        if (user.hasProperty(FS.isManagerOf, ownerWs)) {
            access = Access.Manage;
        }

        if (coll.hasLiteral(FS.accessMode, DataPublished.name())
                && (userService.currentUser().isCanViewPublicData() || access.canRead())) {
            return Access.Read;
        }
        if (!access.canList()
                && userService.currentUser().isCanViewPublicMetadata()
                && (coll.hasLiteral(FS.accessMode, MetadataPublished.name())
                        || coll.hasLiteral(FS.accessMode, DataPublished.name()))) {
            access = Access.List;
        }

        var userWorkspacesIterator = rootSubject
                .getModel()
                .listSubjectsWithProperty(RDF.type, FS.Workspace)
                .filterKeep(ws -> user.hasProperty(FS.isManagerOf, ws) || user.hasProperty(FS.isMemberOf, ws))
                .filterDrop(ws -> ws.hasProperty(FS.dateDeleted));
        while (userWorkspacesIterator.hasNext() && access != Access.Manage) {
            access = max(access, getGrantedPermission(coll, userWorkspacesIterator.next()));
        }

        if (deleted) {
            if (!showDeleted() && !isMetadataRequest()) {
                return Access.None;
            } else {
                access = min(access, Access.List);
            }
        } else if (coll.hasProperty(FS.status, Status.ReadOnly.name())) {
            access = min(access, Access.Read);
        } else if (coll.hasProperty(FS.status, Status.Archived.name())) {
            access = min(access, Access.List);
        }

        if (access == Access.None && userService.currentUser().isAdmin()) {
            return Access.List;
        }

        return access;
    }

    private Access getExtraStorageAccess(org.apache.jena.rdf.model.Resource subject) {
        if (subject.equals(this.rootSubject)) {
            return Access.Read;
        }
        if (subject.hasProperty(RDF.type, FS.ExtraStorageDirectory)
                || subject.hasProperty(FS.createdBy, currentUserResource())) {
            return Access.Write;
        }
        return Access.None;
    }

    public static Access getGrantedPermission(
            org.apache.jena.rdf.model.Resource resource, org.apache.jena.rdf.model.Resource principal) {
        if (principal.hasProperty(FS.canManage, resource)) {
            return Access.Manage;
        }
        if (principal.hasProperty(FS.canWrite, resource)) {
            return Access.Write;
        }
        if (principal.hasProperty(FS.canRead, resource)) {
            return Access.Read;
        }
        if (principal.hasProperty(FS.canList, resource)) {
            return Access.List;
        }
        return Access.None;
    }

    public static org.apache.jena.rdf.model.Resource childSubject(
            org.apache.jena.rdf.model.Resource subject, String name) {
        return subject.getModel().createResource(subject.getURI() + "/" + encodePath(name));
    }

    public org.apache.jena.rdf.model.Resource currentUserResource() {
        Node userURI = getUserURI();
        if (userURI == null) {
            return null;
        }
        return rootSubject.getModel().createResource(userURI.getURI());
    }

    public boolean isFileSystemResource(org.apache.jena.rdf.model.Resource resource) {
        return resource.getURI().startsWith(rootSubject.getURI());
    }
}
