package io.fairspace.saturn.services.workspaces;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;

import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.AccessDeniedException;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.audit.Audit.audit;
import static io.fairspace.saturn.auth.RequestContext.getUserURI;
import static io.fairspace.saturn.util.ValidationUtils.validate;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.stream.Collectors.toList;

@Log4j2
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final Transactions tx;

    private final UserService userService;

    public List<Workspace> listWorkspaces() {
        return tx.calculateRead(m -> {
            var user = m.wrapAsResource(getUserURI());
            return new DAO(m)
                    .list(Workspace.class).stream()
                            .peek(ws -> {
                                var res = m.wrapAsResource(ws.getIri());
                                ws.setCanManage(
                                        userService.currentUser().isAdmin() || user.hasProperty(FS.isManagerOf, res));
                                ws.setCanCollaborate(ws.isCanManage() || user.hasProperty(FS.isMemberOf, res));
                                var workspaceCollections = m.listSubjectsWithProperty(FS.ownedBy, res)
                                        .filterKeep(r -> r.hasProperty(RDF.type, FS.Collection))
                                        .toList();
                                var totalCollectionCount = workspaceCollections.size();
                                var nonDeletedCollectionCount = (int) workspaceCollections.stream()
                                        .filter(collection -> !collection.hasProperty(FS.dateDeleted))
                                        .count();
                                var memberCount = m.listSubjectsWithProperty(RDF.type, FS.User)
                                        .filterKeep(u -> u.hasProperty(FS.isMemberOf, res))
                                        .toList()
                                        .size();
                                var managers = new DAO(m)
                                        .list(User.class).stream()
                                                .filter(u -> m.wrapAsResource(u.getIri())
                                                        .hasProperty(FS.isManagerOf, res))
                                                .collect(toList());
                                ws.setSummary(WorkspaceSummary.builder()
                                        .totalCollectionCount(totalCollectionCount)
                                        .nonDeletedCollectionCount(nonDeletedCollectionCount)
                                        .memberCount(memberCount + managers.size())
                                        .build());
                                ws.setManagers(managers);
                            })
                            .filter(ws -> userService.currentUser().isCanViewPublicMetadata()
                                    || ws.isCanManage()
                                    || ws.isCanCollaborate())
                            .collect(toList());
        });
    }

    public Workspace getWorkspace(Node iri) {
        return tx.calculateRead(model -> {
            var ws = new DAO(model).read(Workspace.class, iri);
            if (ws == null) {
                return null;
            }
            var res = model.wrapAsResource(ws.getIri());
            var user = model.wrapAsResource(getUserURI());
            ws.setCanManage(userService.currentUser().isAdmin() || user.hasProperty(FS.isManagerOf, res));
            ws.setCanCollaborate(ws.isCanManage() || user.hasProperty(FS.isMemberOf, res));
            return ws;
        });
    }

    private Optional<Workspace> findExistingWorkspace(Model model, String name) {
        return new DAO(model)
                .list(Workspace.class).stream()
                        .filter(ws -> {
                            var res = model.wrapAsResource(ws.getIri());
                            return res.hasLiteral(model.createProperty(RDFS.uri, "label"), name);
                        })
                        .findAny();
    }

    public Workspace createWorkspace(Workspace ws) {
        if (!userService.currentUser().isAdmin()) {
            throw new AccessDeniedException();
        }
        validate(ws.getIri() == null, "IRI must be empty");
        validate(!isNullOrEmpty(ws.getCode()), "Please specify workspace code");

        var created = tx.calculateWrite(m -> {
            var conflictingWorkspace = findExistingWorkspace(m, ws.getCode());
            if (conflictingWorkspace.isPresent()) {
                throw new IllegalArgumentException("Workspace already exists with the same code.");
            }

            var workspace = new DAO(m).write(ws);

            m.wrapAsResource(getUserURI()).addProperty(FS.isManagerOf, m.wrapAsResource(workspace.getIri()));
            workspace.setCanManage(true);
            workspace.setCanCollaborate(true);
            return workspace;
        });

        audit("WS_CREATE", "workspace", created.getIri());
        return created;
    }

    public void deleteWorkspace(Node iri) {
        if (!userService.currentUser().isAdmin()) {
            throw new AccessDeniedException();
        }
        validate(iri != null, "No IRI provided");
        tx.executeWrite(m -> {
            var r = m.wrapAsResource(iri);
            validateResource(r, FS.Workspace);
            validate(!m.contains(null, FS.ownedBy, r), "Workspace is not empty");

            m.removeAll(r, null, null).removeAll(null, null, r);
        });
        audit("WS_DELETE", "workspace", iri);
    }

    public Map<Node, WorkspaceRole> getUsers(Node iri) {
        var result = new HashMap<Node, WorkspaceRole>();
        tx.executeRead(m -> {
            var r = m.wrapAsResource(iri);
            validateResource(r, FS.Workspace);
            m.listResourcesWithProperty(FS.isMemberOf, r)
                    .filterDrop(user -> user.hasProperty(FS.dateDeleted))
                    .forEachRemaining(user -> result.put(user.asNode(), WorkspaceRole.Member));
            m.listResourcesWithProperty(FS.isManagerOf, r)
                    .filterDrop(user -> user.hasProperty(FS.dateDeleted))
                    .forEachRemaining(user -> result.put(user.asNode(), WorkspaceRole.Manager));
        });
        return result;
    }

    public void setUserRole(Node workspace, Node user, WorkspaceRole role) {
        validate(workspace != null, "Workspace is not provided");
        validate(user != null, "User is not provided");
        validate(role != null, "Role is not provided");

        tx.executeWrite(m -> {
            var workspaceResource = m.wrapAsResource(workspace);
            var userResource = m.wrapAsResource(user);
            validateResource(workspaceResource, FS.Workspace);
            validateResource(userResource, FS.User);
            var canManage = m.wrapAsResource(getUserURI()).hasProperty(FS.isManagerOf, workspaceResource)
                    || userService.currentUser().isAdmin();
            if (!canManage) {
                throw new AccessDeniedException();
            }
            m.removeAll(userResource, FS.isManagerOf, workspaceResource)
                    .removeAll(userResource, FS.isMemberOf, workspaceResource);
            switch (role) {
                case Member -> {
                    userResource.addProperty(FS.isMemberOf, workspaceResource);
                }
                case Manager -> {
                    userResource.addProperty(FS.isManagerOf, workspaceResource);
                }
                default -> {
                    var writeableCollections = workspaceResource
                            .getModel()
                            .listSubjectsWithProperty(FS.ownedBy, workspaceResource)
                            .filterKeep(coll -> userResource.hasProperty(FS.canManage, coll)
                                    || userResource.hasProperty(FS.canWrite, coll))
                            .toList();
                    writeableCollections.forEach(c -> c.getModel()
                            .removeAll(userResource, FS.canManage, c)
                            .removeAll(userResource, FS.canWrite, c)
                            .add(userResource, FS.canRead, c));
                }
            }
        });

        audit("WS_SET_USER_ROLE", "workspace", workspace, "affected_user", user, "role", role);
    }

    private void validateResource(Resource r, Resource type) {
        validate(r.hasProperty(RDF.type, type), "Invalid resource type");
        validate(!r.hasProperty(FS.dateDeleted), "Resource was deleted");
    }

    public boolean canList(Resource resource) {
        return false;
    }
}
