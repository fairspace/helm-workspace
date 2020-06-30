package io.fairspace.saturn.services.permissions;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.AccessDeniedException;
import io.fairspace.saturn.services.workspaces.WorkspaceStatus;
import io.fairspace.saturn.vocabulary.FS;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import java.util.*;

import static io.fairspace.saturn.audit.Audit.audit;
import static io.fairspace.saturn.auth.RequestContext.getCurrentUserURI;
import static io.fairspace.saturn.auth.RequestContext.isAdmin;
import static io.fairspace.saturn.rdf.SparqlUtils.generateMetadataIri;
import static io.fairspace.saturn.util.ValidationUtils.validate;
import static java.lang.String.format;
import static java.util.Comparator.naturalOrder;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.lang3.ObjectUtils.min;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;

@AllArgsConstructor
@Slf4j
public class PermissionsService {
    public static final String PERMISSIONS_GRAPH = generateMetadataIri("permissions").getURI();

    private final Transactions transactions;
    private final PermissionChangeEventHandler permissionChangeEventHandler;
    private final String baseCollectionsIri;

    public void createResource(Node resource) {
        createResource(resource, getCurrentUserURI());
    }

    public void createResource(Node resource, Node owner) {
        transactions.executeWrite(dataset -> {
            var model = dataset.getNamedModel(PERMISSIONS_GRAPH);
            model.add(model.asRDFNode(resource).asResource(), FS.manage, model.asRDFNode(owner));
        });

        audit("RESOURCE_CREATED",
                "resource", resource.getURI());
    }

    public void createResources(Collection<Resource> resources) {
        var resourcePermissions = createDefaultModel();
        var user = resourcePermissions.asRDFNode( getCurrentUserURI());
        resources.forEach(resource -> resourcePermissions.add(resource, FS.manage, user));
        transactions.executeWrite(dataset -> dataset.getNamedModel(PERMISSIONS_GRAPH).add(resourcePermissions));
    }

    public void setPermission(Node resource, Node user, Access access) {
        var managingUser =  getCurrentUserURI();

        var success = transactions.calculateWrite(dataset -> {
            var g = dataset.getNamedModel(PERMISSIONS_GRAPH);
            ensureAccess(resource, Access.Manage);
            validate(!user.equals(managingUser), "A user may not change his own permissions");

            validate(isCollection(resource) || isWorkspace(resource), "Cannot set permissions for a regular entity");
            if (isWorkspace(resource)) {
                validate(!dataset.getDefaultModel().asRDFNode(resource).asResource().hasProperty(FS.status, WorkspaceStatus.Active.name()),
                        "Cannot set permissions for an inactive workspace");
            }

            g.removeAll(g.asRDFNode(resource).asResource(), null, g.asRDFNode(user));

            if (access != Access.None) {
                g.add(g.asRDFNode(resource).asResource(), g.createProperty(FS.NS, access.name().toLowerCase()), g.asRDFNode(user));
                return true;
            }
            return false;
        });

        if (success) {
            audit("PERMISSION_UPDATED",
                    "manager", getCurrentUserURI(),
                    "target-user", user.getURI(),
                    "resource", resource.getURI(),
                    "access", access.toString());
        }

        if (permissionChangeEventHandler != null)
            permissionChangeEventHandler.onPermissionChange(managingUser, resource, user, access);
    }

    public void ensureAdmin() {
        if(!isAdmin()) {
            throw new AccessDeniedException(format("User %s has to be an admin.", getCurrentUserURI()));
        }
    }

    public void ensureAccess(Set<Node> nodes, Access requestedAccess) {
        if(!nodes.isEmpty()) {
            getPermissions(nodes).forEach((node, access) -> {
                if (access.compareTo(requestedAccess) < 0) {
                    throw new MetadataAccessDeniedException(format("User %s has no %s access to some of the requested resources",
                            getCurrentUserURI(), requestedAccess.name().toLowerCase()), node);
                }
            });
        }
    }

    private void ensureAccess(Node resource, Access access) {
        if (getPermission(resource).compareTo(access) < 0) {
            throw new MetadataAccessDeniedException(
                    format("User %s has no %s access to resource %s",
                            getCurrentUserURI(), access.name().toLowerCase(), resource), resource);
        }
    }

    public void ensureAdminAccess(Node resource) {
        try {
            ensureAdmin();
        } catch (AccessDeniedException e) {
            throw new MetadataAccessDeniedException("Only admins can remove or overwrite metadata.", resource);
        }
    }

    Map<Node, Access> getPermissions(Node resource) {
        return transactions.calculateRead(dataset -> {
            var authority = getAuthority(resource);
            ensureAccess(authority, Access.Read);

            var result = new HashMap<Node, Access>();
            var g = dataset.getNamedModel(PERMISSIONS_GRAPH);
            g.listStatements(g.asRDFNode(authority).asResource(), null, (RDFNode) null)
                    .forEachRemaining(stmt -> {
                        var user = stmt.getObject().asNode();
                        if (stmt.getPredicate().equals(FS.read)) {
                            result.put(user, Access.Read);
                        } else if (stmt.getPredicate().equals(FS.write)) {
                            result.put(user, Access.Write);
                        } else if (stmt.getPredicate().equals(FS.manage)) {
                            result.put(user, Access.Manage);
                        }
                    });
            return result;
        });
    }

    public Access getPermission(Node resource) {
        return getPermissions(List.of(resource)).get(resource);
    }

    /**
     * Retrieves the permissions of the current user for the given resources
     *
     * @param nodes
     * @return
     */
    public Map<Node, Access> getPermissions(Collection<Node> nodes) {
        return transactions.calculateRead(dataset -> {
            var authorities = getAuthorities(nodes);
            var permissionsForAuthorities = getPermissionsForAuthorities(authorities.keys());
            var result = new HashMap<Node, Access>();
            authorities.forEach((authority, node) -> result.put(node, permissionsForAuthorities.get(authority)));
            return result;
        });
    }

    /**
     * Retrieves the permissions of the current user for the given authorities.
     *
     * @param authorities the protected resources to get permissions for.
     * @return a map from resource to permission level.
     */
    private Map<Node, Access> getPermissionsForAuthorities(Collection<Node> authorities) {
        return transactions.calculateRead(dataset -> {
            var result = new HashMap<Node, Access>();
            var user = getCurrentUserURI();

            var g = dataset.getNamedModel(PERMISSIONS_GRAPH);
            var userResource = g.wrapAsResource(user);
            authorities.forEach(a -> result.put(a, getResourceAccess(g.wrapAsResource(a), userResource)));

            return result;
        });
    }

    private Access getResourceAccess(Resource r, Resource user) {
        return transactions.calculateRead(dataset -> {
            if (isWorkspace(r.asNode())
                    && dataset.getDefaultModel().wrapAsResource(r.asNode()).hasProperty(FS.status, WorkspaceStatus.Archived.name())) {
                return Access.Read;
            }
            if (isAdmin()) {
                return Access.Manage;
            }
            if (isCollection(r.asNode()) && isUser(user.asNode())) {
                var it = dataset.getDefaultModel()
                        .listSubjectsWithProperty(RDF.type, FS.Workspace)
                        .filterDrop(ws -> ws.hasProperty(FS.dateDeleted))
                        .mapWith(ws -> ws.inModel(dataset.getNamedModel(PERMISSIONS_GRAPH)))
                        .mapWith(ws -> min(getResourceAccess(ws, user), getResourceAccess(r, ws)));
                return stream(spliteratorUnknownSize(it, 0), false)
                        .max(naturalOrder())
                        .orElse(Access.None);
            }

            if (r.hasProperty(FS.manage, user)) {
                return Access.Manage;
            }
            if (r.hasProperty(FS.write, user)) {
                return Access.Write;
            }
            if (r.hasProperty(FS.read, user)) {
                return Access.Read;
            }
            if (isCollection(r.asNode()) || isWorkspace(r.asNode())) {
                return Access.None;
            }

            return Access.Write;
        });
    }

    private boolean isUser(Node resource) {
        return transactions.calculateRead(dataset -> dataset.getDefaultModel().wrapAsResource(resource).hasProperty(RDF.type, FS.User));
    }

    private boolean isCollection(Node resource) {
        return transactions.calculateRead(dataset -> dataset.getDefaultModel().wrapAsResource(resource).hasProperty(RDF.type, FS.Collection));
    }

    private boolean isWorkspace(Node resource) {
        return transactions.calculateRead(dataset -> dataset.getDefaultModel().wrapAsResource(resource).hasProperty(RDF.type, FS.Workspace));
    }

    /**
     * @param resource
     * @return an authoritative resource for the given resource: currently either the parent collection (for files and directories) or the resource itself
     */
    private Node getAuthority(Node resource) {
        var uri = resource.getURI();
        if (uri.startsWith(baseCollectionsIri)) {
            var pos = uri.indexOf('/', baseCollectionsIri.length());
            if (pos > 0) {
                return NodeFactory.createURI(uri.substring(0, pos));
            }
        }

        return resource;
    }

    /**
     * @param nodes
     * @return A multimap from authority to the related nodes (one authority can control multiple resources)
     * The authority could be the original nodes for metadata entities, or the enclosing collection
     * for files or directories
     */
    private Multimap<Node, Node> getAuthorities(Collection<Node> nodes) {
        var result = HashMultimap.<Node, Node>create();
        nodes.forEach(n -> result.put(getAuthority(n), n));
        return result;
    }

    public List<Node> getVisibleCollections() {
        return transactions.calculateRead(ds -> ds.getDefaultModel()
                .listSubjectsWithProperty(RDF.type, FS.Collection)
                .filterDrop(r -> r.hasProperty(FS.dateDeleted))
                .mapWith(Resource::asNode)
                .filterDrop(n -> getPermission(n) == Access.None)
                .toList());
    }
}
