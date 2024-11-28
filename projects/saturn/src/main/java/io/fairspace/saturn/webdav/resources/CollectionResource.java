package io.fairspace.saturn.webdav.resources;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.milton.http.Auth;
import io.milton.http.FileItem;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.*;

import static io.fairspace.saturn.rdf.ModelUtils.getStringProperty;
import static io.fairspace.saturn.webdav.DavFactory.getGrantedPermission;

import static io.milton.http.ResponseStatus.SC_FORBIDDEN;
import static java.util.stream.Collectors.joining;

public class CollectionResource extends DirectoryResource {

    public CollectionResource(
            DavFactory factory, Resource subject, Access access, Model userVocabulary, Model vocabulary) {
        super(factory, subject, access, userVocabulary, vocabulary);
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return switch (method) {
            case DELETE -> canDelete();
            case POST -> canManage() || canWrite() || canUndelete() || canUnpublish();
            default -> super.authorise(request, method, auth);
        };
    }

    @Override
    public void moveTo(io.milton.resource.CollectionResource rDest, String name)
            throws ConflictException, NotAuthorizedException, BadRequestException {
        if (!canManage()) {
            throw new NotAuthorizedException("Not authorized to copy the resource.", this, SC_FORBIDDEN);
        }
        if (!(rDest instanceof CollectionRootResource)) {
            throw new BadRequestException(this, "Cannot move a collection to a non-root folder.");
        }
        if (getAccessMode() == AccessMode.DataPublished) {
            throw new BadRequestException(this, "Cannot move a published collection.");
        }
        if (name != null) {
            name = name.trim();
        }
        factory.root.validateTargetCollectionName(name);
        super.moveTo(rDest, name);
    }

    @Override
    public void copyTo(io.milton.resource.CollectionResource toCollection, String name)
            throws NotAuthorizedException, BadRequestException, ConflictException {
        if (!(toCollection instanceof CollectionRootResource)) {
            throw new BadRequestException(this, "Cannot copy a collection to a non-root folder.");
        }
        if (name != null) {
            name = name.trim();
        }
        factory.root.validateTargetCollectionName(name);
        super.copyTo(toCollection, name);
    }

    @Property
    public String getOwnedByCode() {
        return Optional.ofNullable(subject.getPropertyResourceValue(FS.ownedBy))
                .map(workspace -> getStringProperty(workspace, RDFS.label))
                .orElse(null);
    }

    @Property
    public String getOwnedBy() {
        return Optional.ofNullable(subject.getPropertyResourceValue(FS.ownedBy))
                .map(Resource::getURI)
                .orElse(null);
    }

    public void setOwnedBy(Resource owner) throws NotAuthorizedException, BadRequestException {
        if (!canManage()) {
            throw new NotAuthorizedException("Not authorized to set the resource owner.", this, SC_FORBIDDEN);
        }
        if (!owner.hasProperty(RDF.type, FS.Workspace)) {
            throw new BadRequestException(this, "Invalid owner");
        }

        updateParents(subject);

        var old = subject.getPropertyResourceValue(FS.ownedBy);

        subject.removeAll(FS.ownedBy)
                .addProperty(FS.ownedBy, owner)
                .removeAll(FS.belongsTo)
                .addProperty(FS.belongsTo, owner);

        updateParents(subject);

        if (old != null) {
            subject.getModel()
                    .listResourcesWithProperty(FS.isMemberOf, old)
                    .andThen(subject.getModel().listResourcesWithProperty(FS.isManagerOf, old))
                    .filterDrop(
                            user -> user.hasProperty(FS.isMemberOf, owner) || user.hasProperty(FS.isManagerOf, owner))
                    .filterKeep(
                            user -> user.hasProperty(FS.canManage, subject) || user.hasProperty(FS.canWrite, subject))
                    .toList()
                    .forEach(user -> user.getModel()
                            .remove(user, FS.canManage, subject)
                            .remove(user, FS.canWrite, subject)
                            .add(user, FS.canRead, subject));
            subject.getModel()
                    .removeAll(old, FS.canManage, subject)
                    .removeAll(old, FS.canWrite, subject)
                    .removeAll(old, FS.canRead, subject);
        }
    }

    @Property
    public String getAccess() {
        return access.name();
    }

    @Property
    public boolean getCanRead() {
        return access.canRead();
    }

    @Property
    public boolean getCanWrite() {
        return access.canWrite();
    }

    @Property
    public boolean getCanManage() {
        return canManage();
    }

    @Property
    public AccessMode getAccessMode() {
        return AccessMode.valueOf(subject.getProperty(FS.accessMode).getString());
    }

    @Property
    public String getAvailableAccessModes() {
        return availableAccessModes().stream().map(Enum::name).collect(joining(","));
    }

    /**
     * Compute available access modes for the collection, given its current status
     * and the user's permissions.
     * <p>
     * Returns only the current access mode when:
     * - the current user does not have manage permission; or
     * - the collection is deleted; or
     * - the access mode is {@link AccessMode#DataPublished}, which is terminal;
     * returns all access modes except that {@link AccessMode#DataPublished} is only
     * included when in status {@link Status#ReadOnly}.
     */
    public Set<AccessMode> availableAccessModes() {
        if (!canManage()) {
            return EnumSet.of(getAccessMode());
        }

        if (getAccessMode() == AccessMode.DataPublished) {
            return EnumSet.of(getAccessMode());
        }

        var accessModes = EnumSet.of(AccessMode.MetadataPublished, AccessMode.Restricted);

        if (getStatus() == Status.ReadOnly) {
            accessModes.add(AccessMode.DataPublished);
        }

        return accessModes;
    }

    @Property
    public String getUserPermissions() {
        return getPermissions(FS.User);
    }

    @Property
    public String getWorkspacePermissions() {
        return getPermissions(FS.Workspace);
    }

    @Property
    public String getComment() {
        return getStringProperty(subject, RDFS.comment);
    }

    @Property
    public String getCreatedBy() {
        return subject.listProperties(FS.createdBy)
                .nextOptional()
                .map(Statement::getResource)
                .map(Resource::getURI)
                .orElse(null);
    }

    @Property
    public Status getStatus() {
        return Status.valueOf(subject.getProperty(FS.status).getString());
    }

    @Property
    public String getAvailableStatuses() {
        return availableStatuses().stream().map(Enum::name).collect(joining(","));
    }

    public Set<Status> availableStatuses() {
        if (!canManage() || getStatus() == Status.Deleted) {
            return EnumSet.of(getStatus());
        }

        if (getAccessMode() == AccessMode.DataPublished) {
            return EnumSet.of(Status.ReadOnly);
        }

        return EnumSet.of(Status.Active, Status.Archived, Status.ReadOnly);
    }

    /**
     * Whether the current user has the permission to manage the collection, i.e.,
     * manage access and change status or view mode.
     * A deleted collection cannot be managed (but can only be undeleted or permanently deleted).
     * In other statuses, manage access is granted to users that have Manage permission on the collection
     * and to managers of the owner workspace.
     */
    private boolean canManage() {
        var currentUser = factory.currentUserResource();
        if (subject.hasProperty(FS.dateDeleted)) {
            return false;
        }
        return access.canManage()
                || getGrantedPermission(subject, currentUser) == Access.Manage
                || currentUser.hasProperty(FS.isManagerOf, subject.getPropertyResourceValue(FS.ownedBy));
    }

    private boolean canWrite() {
        if (subject.hasProperty(FS.dateDeleted)) {
            return false;
        }
        return access.canWrite();
    }

    private String getPermissions(Resource principalType) {
        var builder = new StringBuilder();

        dumpPermissions(FS.canManage, Access.Manage, principalType, builder);
        dumpPermissions(FS.canWrite, Access.Write, principalType, builder);
        dumpPermissions(FS.canRead, Access.Read, principalType, builder);
        dumpPermissions(FS.canList, Access.List, principalType, builder);

        return builder.toString();
    }

    private void dumpPermissions(
            org.apache.jena.rdf.model.Property property, Access access, Resource type, StringBuilder builder) {
        subject.getModel()
                .listSubjectsWithProperty(property, subject)
                .filterKeep(r -> r.hasProperty(RDF.type, type))
                .filterDrop(r -> r.hasProperty(FS.dateDeleted))
                .mapWith(Resource::getURI)
                .forEachRemaining(uri -> {
                    if (builder.length() > 0) {
                        builder.append(',');
                    }
                    builder.append(uri).append(' ').append(access);
                });
    }

    @Override
    protected void performAction(String action, Map<String, String> parameters, Map<String, FileItem> files)
            throws BadRequestException, NotAuthorizedException, ConflictException {
        switch (action) {
            case "set_access_mode" -> setAccessMode(getEnumParameter(parameters, "mode", AccessMode.class));
            case "set_status" -> setStatus(getEnumParameter(parameters, "status", Status.class));
            case "set_permission" -> setPermission(
                    getResourceParameter(parameters, "principal"),
                    getEnumParameter(parameters, "access", Access.class));
            case "set_owned_by" -> setOwnedBy(getResourceParameter(parameters, "owner"));
            case "unpublish" -> unpublish();
            default -> super.performAction(action, parameters, files);
        }
    }

    private void setStatus(Status status) throws NotAuthorizedException, ConflictException {
        if (!canManage()) {
            throw new NotAuthorizedException(
                    "Not authorized to change the status of this resource.", this, SC_FORBIDDEN);
        }
        if (!availableStatuses().contains(status)) {
            throw new ConflictException(this);
        }
        if (status == Status.Deleted) {
            throw new ConflictException(
                    this, "Cannot set 'Deleted' status using 'set_status' action. " + "Use resource deletion instead.");
        }
        subject.removeAll(FS.status).addProperty(FS.status, status.name());
    }

    private void setAccessMode(AccessMode mode) throws NotAuthorizedException, ConflictException {
        if (!canManage()) {
            throw new NotAuthorizedException(
                    "Not authorized to change the access mode of this resource.", this, SC_FORBIDDEN);
        }
        if (!availableAccessModes().contains(mode)) {
            throw new ConflictException(this);
        }
        subject.removeAll(FS.accessMode).addProperty(FS.accessMode, mode.name());
    }

    private void setPermission(Resource principal, Access grantedAccess)
            throws BadRequestException, NotAuthorizedException {
        if (!canManage()) {
            throw new NotAuthorizedException(
                    "Not authorized to change permissions on this resource.", this, SC_FORBIDDEN);
        }
        if (principal.hasProperty(RDF.type, FS.User)) {
            if (grantedAccess == Access.Write || grantedAccess == Access.Manage) {
                var ownerWs = subject.getPropertyResourceValue(FS.ownedBy);
                if (!principal.hasProperty(FS.isMemberOf, ownerWs) && !principal.hasProperty(FS.isManagerOf, ownerWs)) {
                    throw new BadRequestException(this);
                }
            }
        } else if (principal.hasProperty(RDF.type, FS.Workspace)) {
            if ((grantedAccess == Access.Write || grantedAccess == Access.Manage)
                    && !subject.hasProperty(FS.ownedBy, principal)) {
                throw new BadRequestException(this);
            }
        } else {
            throw new BadRequestException(this, "Invalid principal");
        }

        subject.getModel()
                .removeAll(principal, FS.canList, subject)
                .removeAll(principal, FS.canRead, subject)
                .removeAll(principal, FS.canWrite, subject)
                .removeAll(principal, FS.canManage, subject);

        switch (grantedAccess) {
            case List -> principal.addProperty(FS.canList, subject);
            case Read -> principal.addProperty(FS.canRead, subject);
            case Write -> principal.addProperty(FS.canWrite, subject);
            case Manage -> principal.addProperty(FS.canManage, subject);
        }
    }

    @Property
    public boolean getCanUnpublish() {
        return canUnpublish();
    }

    private boolean canUnpublish() {
        return getAccessMode() == AccessMode.DataPublished
                && factory.userService.currentUser().isAdmin();
    }

    private void unpublish() throws NotAuthorizedException, ConflictException {
        if (!factory.userService.currentUser().isAdmin()) {
            throw new NotAuthorizedException("Not authorized to unpublish this resource.", this, SC_FORBIDDEN);
        }
        if (getAccessMode() != AccessMode.DataPublished) {
            throw new ConflictException(this, "Cannot unpublish collection that is not published.");
        }
        subject.removeAll(FS.accessMode).addProperty(FS.accessMode, AccessMode.MetadataPublished.name());
    }

    @Property
    public boolean getCanDelete() {
        return canDelete();
    }

    private boolean canDelete() {
        if (getAccessMode() == AccessMode.DataPublished) {
            return false;
        }
        if (subject.hasProperty(FS.dateDeleted)) {
            // The resource is already deleted. Deleting it permanently
            // required the admin role.
            return factory.userService.currentUser().isAdmin();
        } else {
            return canManage();
        }
    }

    @Property
    public boolean getCanUndelete() {
        return canUndelete();
    }

    @Override
    protected boolean canUndelete() {
        return subject.hasProperty(FS.dateDeleted)
                && factory.userService.currentUser().isAdmin();
    }

    @Override
    public void delete(boolean purge) throws ConflictException, BadRequestException {
        if (!canDelete()) {
            throw new ConflictException(this);
        }
        if (!purge) {
            subject.removeAll(FS.status).addProperty(FS.status, Status.Deleted.name());
        }
        super.delete(purge);
    }

    @Override
    protected void undelete() throws BadRequestException, NotAuthorizedException, ConflictException {
        super.undelete();
        subject.removeAll(FS.status).addProperty(FS.status, Status.Archived.name());
    }

    private <T extends Enum<T>> T getEnumParameter(Map<String, String> parameters, String name, Class<T> type)
            throws BadRequestException {
        checkParameterPresence(parameters, name);
        try {
            return Enum.valueOf(type, parameters.get(name));
        } catch (Exception e) {
            throw new BadRequestException(this, "Invalid \"" + name + "\" parameter");
        }
    }

    private Resource getResourceParameter(Map<String, String> parameters, String name) throws BadRequestException {
        checkParameterPresence(parameters, name);

        Resource r = null;
        try {
            r = subject.getModel().createResource(parameters.get(name));
        } catch (Exception ignore) {
        }
        if (r == null || r.hasProperty(FS.dateDeleted)) {
            throw new BadRequestException(this, "Invalid \"" + name + "\" parameter");
        }
        return r;
    }

    private void checkParameterPresence(Map<String, String> parameters, String name) throws BadRequestException {
        if (!parameters.containsKey(name)) {
            throw new BadRequestException(this, "Missing \"" + name + "\" parameter");
        }
    }
}
