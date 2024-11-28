package io.fairspace.saturn.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UsersResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fairspace.saturn.config.properties.KeycloakClientProperties;

@Configuration
public class KeycloakConfig {

    @Bean
    public UsersResource usersResource(Keycloak keycloak, KeycloakClientProperties keycloakClientProperties) {
        return keycloak.realm(keycloakClientProperties.getRealm()).users();
    }

    @Bean
    public Keycloak keycloak(KeycloakClientProperties keycloakClientProperties) {
        return KeycloakBuilder.builder()
                .serverUrl(keycloakClientProperties.getAuthServerUrl())
                .realm(keycloakClientProperties.getRealm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(keycloakClientProperties.getClientId())
                .clientSecret(keycloakClientProperties.getClientSecret())
                .username(keycloakClientProperties.getClientId())
                .password(keycloakClientProperties.getClientSecret())
                .build();
    }
}
