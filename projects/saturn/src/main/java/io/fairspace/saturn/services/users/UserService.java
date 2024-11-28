package io.fairspace.saturn.services.users;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.sparql.util.Symbol;
import org.keycloak.admin.client.resource.UsersResource;
import org.springframework.stereotype.Service;

import io.fairspace.saturn.config.properties.KeycloakClientProperties;
import io.fairspace.saturn.rdf.SparqlUtils;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.AccessDeniedException;

import static io.fairspace.saturn.audit.Audit.audit;
import static io.fairspace.saturn.auth.RequestContext.getCurrentUserStringUri;

import static java.util.stream.Collectors.toMap;

@Log4j2
@Service
public class UserService {
    private final LoadingCache<Boolean, Map<String, User>> usersCache;
    private final Transactions transactions;
    private final KeycloakClientProperties keycloakClientProperties;
    private final UsersResource usersResource;
    private final ExecutorService threadpool = Executors.newSingleThreadExecutor();

    public UserService(
            KeycloakClientProperties keycloakClientProperties, Transactions transactions, UsersResource usersResource) {
        this.keycloakClientProperties = keycloakClientProperties;
        this.transactions = transactions;
        this.usersResource = usersResource;
        usersCache = CacheBuilder.newBuilder()
                .refreshAfterWrite(30, TimeUnit.SECONDS)
                .build(new CacheLoader<>() {
                    @Override
                    public Map<String, User> load(Boolean key) {
                        return fetchAndUpdateUsers();
                    }
                });
    }

    public Collection<User> getUsers() {
        return getUsersMap().values();
    }

    public User currentUser() {
        return getCurrentUserStringUri().map(uri -> getUsersMap().get(uri)).orElse(null);
    }

    public Map<String, User> getUsersMap() {
        try {
            return usersCache.get(Boolean.FALSE);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static Symbol currentUserAsSymbol() {
        var uri = getCurrentUserStringUri().orElse("anonymous");
        return Symbol.create(uri);
    }

    /**
     * Fetches users from Keycloak and updates the users in the database
     * if a user does not exist or if the user details have changed.
     */
    private Map<String, User> fetchAndUpdateUsers() {
        var userCount = usersResource.count();
        var keycloakUsers = usersResource.list(0, userCount);
        var updated = new HashSet<User>();
        var users = transactions.calculateRead(model -> {
            var dao = new DAO(model);
            return keycloakUsers.stream()
                    .map(ku -> {
                        var iri = SparqlUtils.generateMetadataIriFromId(ku.getId());
                        var user = dao.read(User.class, iri);
                        if (user == null) {
                            user = new User();
                            user.setIri(iri);
                            user.setId(ku.getId());

                            if (keycloakClientProperties.getSuperAdminUser().equalsIgnoreCase(ku.getUsername())) {
                                user.setSuperadmin(true);
                                user.setAdmin(true);
                                user.setCanViewPublicMetadata(true);
                                user.setCanViewPublicData(true);
                            }

                            for (var role : keycloakClientProperties.getDefaultUserRoles()) {
                                switch (role) {
                                    case "admin" -> user.setAdmin(true);
                                    case "canViewPublicMetadata" -> user.setCanViewPublicMetadata(true);
                                    case "canViewPublicData" -> user.setCanViewPublicData(true);
                                    case "canAddSharedMetadata" -> user.setCanAddSharedMetadata(true);
                                    case "canQueryMetadata" -> user.setCanQueryMetadata(true);
                                }
                            }

                            updated.add(user);
                        }

                        var name = Stream.of(ku.getFirstName(), ku.getLastName())
                                .filter(StringUtils::isNotEmpty)
                                .map(String::trim)
                                .collect(Collectors.joining(" "));
                        if (name.isEmpty()) {
                            name = ku.getUsername();
                        }

                        if (!Objects.equals(user.getName(), name)
                                || !Objects.equals(user.getEmail(), ku.getEmail())
                                || !Objects.equals(user.getUsername(), ku.getUsername())) {
                            user.setEmail(ku.getEmail());
                            user.setName(name);
                            user.setUsername(ku.getUsername());
                            updated.add(user);
                        }

                        return user;
                    })
                    .collect(toMap(user -> user.getIri().toString(), u -> u));
        });

        if (!updated.isEmpty()) {
            threadpool.submit(() -> {
                log.info("Updating users asynchronously");
                transactions.executeWrite(model -> {
                    var dao = new DAO(model);
                    updated.forEach(dao::write);
                });
            });
        }
        return users;
    }

    public void update(UserRolesUpdate roles) {
        if (!currentUser().isAdmin()) {
            throw new AccessDeniedException();
        }
        final String[] username = new String[1];
        transactions.executeWrite(model -> {
            var dao = new DAO(model);
            var user = dao.read(User.class, SparqlUtils.generateMetadataIriFromId(roles.getId()));
            if (user == null) {
                throw new NotFoundException();
            }
            if (user.isSuperadmin()) {
                if (Stream.of(roles.getAdmin(), roles.getCanViewPublicData(), roles.getCanViewPublicMetadata())
                        .anyMatch(role -> role != null && !role)) {
                    throw new IllegalArgumentException(
                            "Cannot revoke admin or public access roles from superadmin user.");
                }
            }
            username[0] = user.getUsername();
            if (roles.getAdmin() != null) {
                user.setAdmin(roles.getAdmin());
                if (user.isAdmin()) {
                    user.setCanViewPublicData(true);
                    user.setCanViewPublicMetadata(true);
                }
            }
            if (roles.getCanViewPublicData() != null) {
                user.setCanViewPublicData(roles.getCanViewPublicData());
                if (user.isCanViewPublicData()) {
                    user.setCanViewPublicMetadata(true);
                }
            }
            if (roles.getCanQueryMetadata() != null) {
                user.setCanQueryMetadata(roles.getCanQueryMetadata());
                if (user.isCanQueryMetadata()) {
                    user.setCanViewPublicMetadata(true);
                }
            }
            if (roles.getCanViewPublicMetadata() != null) {
                user.setCanViewPublicMetadata(roles.getCanViewPublicMetadata());
            }
            if (roles.getCanAddSharedMetadata() != null) {
                user.setCanAddSharedMetadata(roles.getCanAddSharedMetadata());
            }

            if (user.isAdmin() && !user.isCanViewPublicData()
                    || user.isCanViewPublicData() && !user.isCanViewPublicMetadata()
                    || user.isCanQueryMetadata() && !user.isCanViewPublicMetadata()) {
                throw new IllegalArgumentException("Inconsistent organisation-level roles");
            }

            dao.write(user);
        });
        audit("USER_UPDATE", "affected_user", username[0]);
        usersCache.invalidateAll();
    }
}
