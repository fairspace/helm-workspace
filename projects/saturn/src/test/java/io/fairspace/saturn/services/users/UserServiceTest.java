package io.fairspace.saturn.services.users;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.config.properties.KeycloakClientProperties;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.workspaces.Workspace;
import io.fairspace.saturn.services.workspaces.WorkspaceService;

import static io.fairspace.saturn.TestUtils.ADMIN;
import static io.fairspace.saturn.TestUtils.USER;
import static io.fairspace.saturn.TestUtils.createTestUser;
import static io.fairspace.saturn.TestUtils.mockAuthentication;
import static io.fairspace.saturn.TestUtils.setupRequestContext;
import static io.fairspace.saturn.rdf.SparqlUtils.generateMetadataIriFromId;

import static org.apache.jena.query.DatasetFactory.createTxnMem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {
    private final Transactions tx = new SimpleTransactions(createTxnMem());
    private WorkspaceService workspaceService;

    @Mock
    private UsersResource usersResource;

    private UserService userService;
    User user;
    User admin;
    List<UserRepresentation> keycloakUsers;

    @Before
    public void setUp() {
        setupRequestContext(ADMIN);

        tx.executeWrite(model -> {
            mockAuthentication(USER);
            user = createTestUser(USER, false);
            user.setCanViewPublicData(true);
            user.setCanViewPublicMetadata(true);
            DAO dao = new DAO(model);
            dao.write(user);
            mockAuthentication(ADMIN);
            admin = createTestUser(ADMIN, true);
            dao.write(admin);
        });

        keycloakUsers = Stream.of(user, admin)
                .map(user -> {
                    var keycloakUser = new UserRepresentation();
                    keycloakUser.setId(user.getId());
                    keycloakUser.setUsername(user.getUsername());
                    return keycloakUser;
                })
                .collect(Collectors.toList());
        when(usersResource.list(any(), any())).thenReturn(keycloakUsers);

        userService = new UserService(new KeycloakClientProperties(), tx, usersResource);
        workspaceService = new WorkspaceService(tx, userService);

        selectAdmin();
        // Create test workspace
        workspaceService.createWorkspace(Workspace.builder().code("ws1").build());
    }

    public static void selectRegularUser() {
        mockAuthentication(USER);
    }

    public static void selectAdmin() {
        mockAuthentication(ADMIN);
    }

    private void triggerKeycloakUserUpdate() {
        selectAdmin();
        var update = new UserRolesUpdate();
        update.setId("user");
        // Invalidate users cache in the user service
        userService.update(update);

        // Change Keycloak info, triggering a database write
        // when the user cache is refreshed
        keycloakUsers.getFirst().setLastName("Updated");
        when(usersResource.list(any(), any())).thenReturn(keycloakUsers);
    }

    /**
     * In some parts of the application, the current user object is requested
     * to perform access checks. Requesting the current user may trigger a read from
     * Keycloak, as the list of users is cached by Saturn only for limited time (see
     * {@link UserService(KeycloakClientProperties, Transactions, UsersResource)}).
     * <p>
     * While fetching the list of users, Saturn may update the user objects in the RDF database
     * when some user properties have changed in Keycloak or when new users have been added.
     * That update did trigger a transaction error when occurring during a read action, such as fetching
     * the list of workspaces (see <a href="https://thehyve.atlassian.net/browse/VRE-1455">VRE-1455</a>).
     * <p>
     * This test ensures that such updates happen asynchronously, not interfering with the
     * ongoing read transaction.
     */
    @Test
    public void testFetchUsersWhileFetchingWorkspaces() throws InterruptedException {
        var pristineUser =
                tx.calculateRead(model -> new DAO(model).read(User.class, generateMetadataIriFromId("user")));
        Assert.assertEquals("user", pristineUser.getName());

        triggerKeycloakUserUpdate();

        selectRegularUser();
        // Fetching the list of workspaces triggers fetching the current user (for access checks).
        // This will trigger saving the updated user (in a write transactions) during a read transaction.
        workspaceService.listWorkspaces();

        Thread.sleep(500);
        var updatedUser = tx.calculateRead(model -> new DAO(model).read(User.class, generateMetadataIriFromId("user")));
        // Check that the updated user was correctly saved to the database.
        Assert.assertEquals("Updated", updatedUser.getName());
    }
}
