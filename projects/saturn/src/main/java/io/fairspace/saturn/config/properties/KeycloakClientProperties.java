package io.fairspace.saturn.config.properties;

import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakClientProperties {

    private String authServerUrl;

    private String realm;

    private String clientId;

    private String clientSecret;

    private String superAdminUser;

    private List<String> defaultUserRoles;
}
