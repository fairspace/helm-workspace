package io.fairspace.saturn.webdav.resources;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;
import javax.xml.namespace.QName;

import io.milton.http.Auth;
import io.milton.http.FileItem;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.property.PropertySource;
import io.milton.property.PropertySource.PropertyMetaData;
import io.milton.property.PropertySource.PropertySetException;
import io.milton.resource.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.Access;
import io.fairspace.saturn.webdav.DavFactory;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;

import static io.fairspace.saturn.audit.Audit.audit;
import static io.fairspace.saturn.auth.RequestContext.getUserURI;
import static io.fairspace.saturn.rdf.ModelUtils.*;
import static io.fairspace.saturn.rdf.SparqlUtils.parseXSDDateTimeLiteral;
import static io.fairspace.saturn.webdav.DavFactory.childSubject;
import static io.fairspace.saturn.webdav.WebDAVServlet.*;

import static io.milton.http.ResponseStatus.SC_FORBIDDEN;
import static io.milton.property.PropertySource.PropertyAccessibility.READ_ONLY;
import static io.milton.property.PropertySource.PropertyAccessibility.WRITABLE;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.beanutils.PropertyUtils.getPropertyDescriptor;
import static org.apache.commons.beanutils.PropertyUtils.getPropertyDescriptors;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;

public abstract class BaseResource
        implements PropFindableResource,
                DeletableResource,
                MoveableResource,
                CopyableResource,
                MultiNamespaceCustomPropertyResource,
                PostableResource {
    protected final DavFactory factory;
    public final Resource subject;
    protected final Access access;
    private final Model userVocabulary;

    BaseResource(DavFactory factory, Resource subject, Access access, Model userVocabulary) {
        this.factory = factory;
        this.subject = subject;
        this.access = access;
        this.userVocabulary = userVocabulary;
    }

    @Override
    public String getUniqueId() {
        return subject.getURI();
    }

    String getRelativePath() {
        return getUniqueId().substring(factory.root.getUniqueId().length());
    }

    @Override
    public String getName() {
        return subject.getRequiredProperty(RDFS.label).getString();
    }

    @Override
    public Object authenticate(String user, String password) {
        return null;
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        // for POST requests performAction *must* implement action-specific checks and throw NotAuthorizedException if
        // necessary

        return (!method.isWrite && access.canList()) || (method.isWrite && access.canWrite());
    }

    @Override
    public String getRealm() {
        return null;
    }

    @Override
    public String checkRedirect(Request request) throws NotAuthorizedException, BadRequestException {
        return null;
    }

    @Override
    public Date getCreateDate() {
        return parseDate(subject, FS.dateCreated);
    }

    @Override
    public void delete() throws NotAuthorizedException, ConflictException, BadRequestException {
        boolean purge;
        if (factory.isExtraStoreResource()) {
            if (subject.hasProperty(RDF.type, FS.ExtraStorageDirectory)) {
                throw new NotAuthorizedException(
                        "Not authorized to purge the extra store root directory.", this, SC_FORBIDDEN);
            }
            if (subject.hasProperty(RDF.type, FS.File)) {
                ((FileResource) factory.getResource(subject)).deleteContent();
            }
            purge = true;
        } else {
            purge = subject.hasProperty(FS.dateDeleted);
            if (purge && !factory.userService.currentUser().isAdmin()) {
                throw new NotAuthorizedException("Not authorized to purge the resource.", this, SC_FORBIDDEN);
            }
        }
        delete(purge);
        updateParents(subject);
        if (purge) {
            audit("FS_DELETE", "path", getRelativePath(), "success", true);
        } else {
            audit("FS_MARK_AS_DELETED", "path", getRelativePath(), "success", true);
        }
    }

    protected void delete(boolean purge) throws ConflictException, BadRequestException {
        if (purge) {
            subject.getModel().removeAll(subject, null, null).removeAll(null, null, subject);
        } else if (!subject.hasProperty(FS.dateDeleted)) {
            subject.addProperty(FS.dateDeleted, timestampLiteral())
                    .addProperty(FS.deletedBy, factory.currentUserResource());
        }
    }

    private void validateTarget(io.milton.resource.CollectionResource parent, String name)
            throws BadRequestException, ConflictException, NotAuthorizedException {
        if (name == null || name.isEmpty()) {
            throw new BadRequestException("The name is empty.");
        }
        if (name.contains("\\")) {
            throw new BadRequestException("The name contains an illegal character (\\)");
        }
        var existing = parent.child(name);
        if (existing != null) {
            throw new ConflictException(existing);
        }
    }

    @Override
    public void moveTo(io.milton.resource.CollectionResource parent, String name)
            throws ConflictException, NotAuthorizedException, BadRequestException {
        if (name != null) {
            name = name.trim();
        }
        validateTarget(parent, name);
        move(subject, (parent instanceof DirectoryResource) ? ((DirectoryResource) parent).subject : null, name, true);
    }

    private void move(Resource subject, Resource parent, String name, boolean isTop) {
        var newSubject = childSubject(parent != null ? parent : factory.rootSubject, name);
        newSubject.removeProperties().addProperty(RDFS.label, name);
        if (parent != null) {
            newSubject.addProperty(FS.belongsTo, parent);
        }

        subject.listProperties()
                .filterDrop(stmt -> stmt.getPredicate().equals(RDFS.label))
                .filterDrop(stmt -> stmt.getPredicate().equals(FS.belongsTo))
                .filterDrop(stmt -> stmt.getPredicate().equals(FS.versions))
                .toSet() // convert to set, to prevent updating a model while iterating over its elements
                .forEach(stmt -> newSubject.addProperty(stmt.getPredicate(), stmt.getObject()));

        var versions = getListProperty(subject, FS.versions);

        if (versions != null) {
            var newVersions = subject.getModel()
                    .createList(versions.iterator().mapWith(RDFNode::asResource).mapWith(BaseResource::copyVersion));
            newSubject.addProperty(FS.versions, newVersions);
        }

        subject.getModel()
                .listSubjectsWithProperty(FS.belongsTo, subject)
                .toSet() // convert to set, to prevent updating a model while iterating over its elements
                .forEach(r -> move(r, newSubject, getStringProperty(r, RDFS.label), false));

        subject.getModel()
                .listStatements(null, null, subject)
                .filterDrop(stmt -> stmt.getPredicate().equals(FS.belongsTo))
                .toSet() // convert to set, to prevent updating a model while iterating over its elements
                .forEach(stmt -> stmt.getSubject().addProperty(stmt.getPredicate(), newSubject));

        subject.getModel().removeAll(subject, null, null).removeAll(null, null, subject);

        subject.addProperty(FS.movedTo, newSubject);

        if (isTop) {
            updateParents(subject);
            updateParents(newSubject);
        }
    }

    private static Resource copyVersion(Resource ver) {
        var newVer = ver.getModel().createResource();
        copyProperties(
                ver.asResource(), newVer, RDF.type, FS.dateModified, FS.deletedBy, FS.fileSize, FS.blobId, FS.md5);
        return newVer;
    }

    @Override
    public void copyTo(io.milton.resource.CollectionResource parent, String name)
            throws NotAuthorizedException, BadRequestException, ConflictException {
        if (!((DirectoryResource) parent).access.canWrite()) {
            throw new NotAuthorizedException("Not authorized to copy this resource.", this, SC_FORBIDDEN);
        }
        if (name != null) {
            name = name.trim();
        }
        copy(subject, ((DirectoryResource) parent).subject, name, factory.currentUserResource(), timestampLiteral());
    }

    private void copy(Resource subject, Resource parent, String name, Resource user, Literal date) {
        var newSubject = childSubject(parent, name);
        newSubject.removeProperties();
        newSubject.addProperty(FS.belongsTo, parent);
        newSubject
                .addProperty(RDFS.label, name)
                .addProperty(FS.dateCreated, date)
                .addProperty(FS.createdBy, user);

        copyProperties(subject, newSubject, RDF.type, FS.contentType);

        if (subject.hasProperty(FS.versions)) {
            var src = getListProperty(subject, FS.versions).getHead().asResource();

            var ver = newSubject
                    .getModel()
                    .createResource()
                    .addProperty(RDF.type, FS.FileVersion)
                    .addProperty(FS.modifiedBy, user)
                    .addProperty(FS.dateModified, date);

            copyProperties(src, ver, FS.blobId, FS.fileSize, FS.md5);

            newSubject
                    .addLiteral(FS.currentVersion, 1)
                    .addProperty(FS.versions, newSubject.getModel().createList(ver));
        }
        subject.getModel()
                .listSubjectsWithProperty(FS.belongsTo, subject)
                .toSet() // convert to set, to prevent updating a model while iterating over its elements
                .forEach(r -> copy(r, newSubject, getStringProperty(r, RDFS.label), user, date));

        updateParents(subject);
        updateParents(newSubject);
    }

    @Override
    public List<QName> getAllPropertyNames() {
        return Stream.of(getPropertyDescriptors(getClass()))
                .filter(p -> p.getReadMethod().isAnnotationPresent(io.fairspace.saturn.webdav.Property.class))
                .map(p -> new QName(FS.NS, p.getName()))
                .collect(toList());
    }

    @Override
    public Object getProperty(QName name) {
        try {
            return getPropertyDescriptor(this, name.getLocalPart())
                    .getReadMethod()
                    .invoke(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setProperty(QName name, Object value) throws PropertySetException, NotAuthorizedException {
        try {
            getPropertyDescriptor(this, name.getLocalPart()).getWriteMethod().invoke(this, value);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof PropertySetException) {
                throw (PropertySetException) e.getTargetException();
            }
            if (e.getTargetException() instanceof NotAuthorizedException) {
                throw (NotAuthorizedException) e.getTargetException();
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public PropertySource.PropertyMetaData getPropertyMetaData(QName name) {
        try {
            var pd = getPropertyDescriptor(this, name.getLocalPart());
            if (pd != null) {
                return new PropertyMetaData(pd.getWriteMethod() != null ? WRITABLE : READ_ONLY, pd.getPropertyType());
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    @io.fairspace.saturn.webdav.Property
    public String getIri() {
        return subject.getURI();
    }

    @io.fairspace.saturn.webdav.Property
    public Date getDateDeleted() {
        return parseDate(subject, FS.dateDeleted);
    }

    @io.fairspace.saturn.webdav.Property
    public String getMetadataLinks() {
        if (includeMetadataLinks()) {
            return String.join(",", metadataLinks());
        }
        return null;
    }

    public Set<String> metadataLinks() {
        var userVocabularyPaths = userVocabulary
                .listStatements()
                .filterKeep(stmt -> stmt.getObject().isResource()
                        && stmt.getPredicate().getURI().equals(SHACL.path.getURI()))
                .mapWith(stmt -> stmt.getObject().asResource().getURI())
                .toSet();
        return subject.listProperties()
                .filterKeep(stmt -> stmt.getObject().isResource()
                        && userVocabularyPaths.contains(stmt.getPredicate().getURI()))
                .mapWith(Statement::getResource)
                .mapWith(Resource::getURI)
                .toSet();
    }

    @io.fairspace.saturn.webdav.Property
    public String getDeletedBy() {
        var deletedBy = subject.getPropertyResourceValue(FS.deletedBy);
        if (deletedBy != null) {
            return deletedBy.getURI();
        }
        return null;
    }

    @Override
    public String toString() {
        return subject.getURI().substring(factory.rootSubject.getURI().length());
    }

    protected Resource newVersion(BlobInfo blob) {
        updateParents(subject);
        return subject.getModel()
                .createResource()
                .addProperty(RDF.type, FS.FileVersion)
                .addProperty(FS.blobId, blob.id)
                .addLiteral(FS.fileSize, blob.size)
                .addProperty(FS.md5, blob.md5)
                .addProperty(FS.dateModified, timestampLiteral())
                .addProperty(FS.modifiedBy, factory.currentUserResource());
    }

    protected static void updateParents(Resource subject) {
        var now = timestampLiteral();
        for (var s = subject.getPropertyResourceValue(FS.belongsTo);
                s != null && !s.hasProperty(RDF.type, FS.Workspace);
                s = s.getPropertyResourceValue(FS.belongsTo)) {
            s.removeAll(FS.dateModified)
                    .removeAll(FS.modifiedBy)
                    .addProperty(FS.dateModified, now)
                    .addProperty(FS.modifiedBy, createResource(getUserURI().getURI()));
        }
    }

    protected static Date parseDate(Resource s, org.apache.jena.rdf.model.Property p) {
        if (!s.hasProperty(p)) {
            return null;
        }
        return Date.from(parseXSDDateTimeLiteral(s.getProperty(p).getLiteral()));
    }

    @Override
    public String processForm(Map<String, String> parameters, Map<String, FileItem> files)
            throws BadRequestException, NotAuthorizedException, ConflictException {
        var action = parameters.get("action");
        if (action == null) {
            throw new BadRequestException(this, "No action specified");
        }
        performAction(action, parameters, files);
        return null;
    }

    protected void performAction(String action, Map<String, String> parameters, Map<String, FileItem> files)
            throws BadRequestException, NotAuthorizedException, ConflictException {
        switch (action) {
            case "undelete" -> undelete();
            default -> throw new BadRequestException(this, "Unrecognized action " + action);
        }
    }

    protected boolean canUndelete() {
        return access.canWrite();
    }

    protected void undelete() throws BadRequestException, NotAuthorizedException, ConflictException {
        if (!canUndelete()) {
            throw new NotAuthorizedException("Not authorized to undelete this resource.", this, SC_FORBIDDEN);
        }
        if (!subject.hasProperty(FS.dateDeleted)) {
            throw new ConflictException(this, "Cannot restore");
        }
        var date = subject.getProperty(FS.dateDeleted).getLiteral();
        var user = subject.getProperty(FS.deletedBy).getResource();
        undelete(subject, date, user);
        updateParents(subject);
    }

    private void undelete(Resource resource, Literal date, Resource user) {
        if (resource.hasProperty(FS.deletedBy, user) && resource.hasProperty(FS.dateDeleted, date)) {
            resource.removeAll(FS.dateDeleted).removeAll(FS.deletedBy);

            resource.getModel()
                    .listSubjectsWithProperty(FS.belongsTo, resource)
                    .toSet() // convert to set, to prevent updating a model while iterating over its elements
                    .forEach(r -> undelete(r, date, user));
        }
    }
}
