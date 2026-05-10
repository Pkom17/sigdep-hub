package ci.itechciv.sigdep.hub.console.admin;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds a Keycloak admin client backed by the {@code sigdep-console-admin}
 * service account. The client uses {@code client_credentials} so no human
 * user is required; the service account itself must hold the realm-management
 * roles needed by the controller (manage-users, view-users at minimum).
 */
@Configuration
public class KeycloakAdminConfig {

    @Bean
    public Keycloak keycloakAdmin(
            @Value("${app.keycloak.server-url}") String serverUrl,
            @Value("${app.keycloak.realm}") String realm,
            @Value("${app.keycloak.admin-client-id}") String clientId,
            @Value("${app.keycloak.admin-client-secret:}") String clientSecret) {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .grantType("client_credentials")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
    }
}
