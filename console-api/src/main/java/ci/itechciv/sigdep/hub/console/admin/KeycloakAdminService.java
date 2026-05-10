package ci.itechciv.sigdep.hub.console.admin;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read/write wrapper around the Keycloak admin REST API for the console
 * "Utilisateurs" page. All methods operate on the realm configured in
 * {@code app.keycloak.realm}. Method-level errors propagate as
 * {@link ResponseStatusException} so Spring renders a clean HTTP status
 * to the frontend instead of leaking Keycloak's internal exception types.
 *
 * Realm-level role filtering: we hide Keycloak's built-in roles (default-roles,
 * uma_authorization, offline_access) so only the SIGDEP business roles
 * surface in the UI.
 */
@Service
public class KeycloakAdminService {

    private static final Set<String> HIDDEN_ROLES = Set.of(
            "default-roles-sigdep", "default-roles-master",
            "offline_access", "uma_authorization");

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakAdminService(Keycloak keycloak,
                                @Value("${app.keycloak.realm}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    private RealmResource realm() {
        return keycloak.realm(realm);
    }

    // ---------- queries ------------------------------------------------------

    public UserPage list(String search, int page, int size) {
        int safeSize = Math.max(1, Math.min(200, size));
        int safePage = Math.max(0, page);
        int first = safePage * safeSize;

        try {
            int total = search == null || search.isBlank()
                    ? realm().users().count()
                    : realm().users().count(search);

            List<UserRepresentation> users = realm().users()
                    .search(search == null || search.isBlank() ? null : search,
                            first, safeSize, true);

            List<UserRow> rows = new ArrayList<>(users.size());
            for (UserRepresentation u : users) {
                rows.add(toRow(u));
            }
            return new UserPage(rows, total, safePage, safeSize);
        } catch (WebApplicationException ex) {
            throw rethrow(ex);
        }
    }

    public UserDetail get(String id) {
        try {
            UserResource ur = realm().users().get(id);
            UserRepresentation u = ur.toRepresentation();
            List<String> realmRoles = ur.roles().realmLevel().listEffective().stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> !HIDDEN_ROLES.contains(name))
                    .sorted()
                    .toList();
            return toDetail(u, realmRoles);
        } catch (NotFoundException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Utilisateur introuvable");
        } catch (WebApplicationException ex) {
            throw rethrow(ex);
        }
    }

    /** Realm roles the UI can assign — built-in/default roles excluded. */
    public List<String> availableRoles() {
        try {
            return realm().roles().list().stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> !HIDDEN_ROLES.contains(name))
                    .sorted()
                    .toList();
        } catch (WebApplicationException ex) {
            throw rethrow(ex);
        }
    }

    // ---------- writes -------------------------------------------------------

    public String create(CreateUserRequest req) {
        UserRepresentation u = new UserRepresentation();
        u.setUsername(req.username());
        u.setEmail(req.email());
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        u.setEnabled(req.enabled() == null || req.enabled());
        u.setEmailVerified(Boolean.TRUE.equals(req.emailVerified()));

        try (Response resp = realm().users().create(u)) {
            if (resp.getStatus() != 201) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.valueOf(resp.getStatus()),
                        "Création refusée par Keycloak");
            }
            String location = resp.getHeaderString("Location");
            String id = location == null ? null : location.substring(location.lastIndexOf('/') + 1);
            if (id == null || id.isBlank()) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "Impossible de récupérer l'id du nouvel utilisateur");
            }

            if (req.password() != null && !req.password().isBlank()) {
                resetPassword(id, req.password(), Boolean.TRUE.equals(req.passwordTemporary()));
            }
            if (req.realmRoles() != null && !req.realmRoles().isEmpty()) {
                applyRoles(id, req.realmRoles());
            }
            return id;
        } catch (WebApplicationException ex) {
            throw rethrow(ex);
        }
    }

    public void update(String id, UpdateUserRequest req) {
        try {
            UserResource ur = realm().users().get(id);
            UserRepresentation u = ur.toRepresentation();
            if (req.email() != null)         u.setEmail(req.email());
            if (req.firstName() != null)     u.setFirstName(req.firstName());
            if (req.lastName() != null)      u.setLastName(req.lastName());
            if (req.enabled() != null)       u.setEnabled(req.enabled());
            if (req.emailVerified() != null) u.setEmailVerified(req.emailVerified());
            ur.update(u);

            if (req.realmRoles() != null) applyRoles(id, req.realmRoles());
        } catch (NotFoundException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Utilisateur introuvable");
        } catch (WebApplicationException ex) {
            throw rethrow(ex);
        }
    }

    public void resetPassword(String id, String password, boolean temporary) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(temporary);
        try {
            realm().users().get(id).resetPassword(cred);
        } catch (NotFoundException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Utilisateur introuvable");
        } catch (WebApplicationException ex) {
            throw rethrow(ex);
        }
    }

    public void setEnabled(String id, boolean enabled) {
        try {
            UserResource ur = realm().users().get(id);
            UserRepresentation u = ur.toRepresentation();
            u.setEnabled(enabled);
            ur.update(u);
        } catch (NotFoundException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Utilisateur introuvable");
        } catch (WebApplicationException ex) {
            throw rethrow(ex);
        }
    }

    /**
     * Diff the user's current realm roles against {@code desired} and apply
     * the minimal add/remove. Roles in {@link #HIDDEN_ROLES} are never touched
     * — they're managed by Keycloak itself.
     */
    private void applyRoles(String id, List<String> desired) {
        UserResource ur = realm().users().get(id);
        var roles = ur.roles().realmLevel();
        List<RoleRepresentation> current = roles.listAll();
        Set<String> currentNames = new HashSet<>();
        for (RoleRepresentation r : current) currentNames.add(r.getName());

        Set<String> desiredSet = new HashSet<>(desired);
        // Don't touch hidden / default realm roles.
        currentNames.removeAll(HIDDEN_ROLES);

        Set<String> toAdd = new HashSet<>(desiredSet);
        toAdd.removeAll(currentNames);

        Set<String> toRemove = new HashSet<>(currentNames);
        toRemove.removeAll(desiredSet);

        if (!toAdd.isEmpty()) {
            List<RoleRepresentation> add = new ArrayList<>(toAdd.size());
            for (String name : toAdd) add.add(realm().roles().get(name).toRepresentation());
            roles.add(add);
        }
        if (!toRemove.isEmpty()) {
            List<RoleRepresentation> remove = new ArrayList<>(toRemove.size());
            for (RoleRepresentation r : current) {
                if (toRemove.contains(r.getName())) remove.add(r);
            }
            roles.remove(remove);
        }
    }

    private static UserRow toRow(UserRepresentation u) {
        return new UserRow(
                u.getId(),
                u.getUsername(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                Boolean.TRUE.equals(u.isEnabled()),
                Boolean.TRUE.equals(u.isEmailVerified()),
                u.getCreatedTimestamp());
    }

    private static UserDetail toDetail(UserRepresentation u, List<String> roles) {
        return new UserDetail(
                u.getId(),
                u.getUsername(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                Boolean.TRUE.equals(u.isEnabled()),
                Boolean.TRUE.equals(u.isEmailVerified()),
                u.getCreatedTimestamp(),
                roles == null ? Collections.emptyList() : roles);
    }

    private static ResponseStatusException rethrow(WebApplicationException ex) {
        int status = ex.getResponse() == null ? 500 : ex.getResponse().getStatus();
        String reason = "Keycloak error " + status;
        return new ResponseStatusException(
                org.springframework.http.HttpStatus.resolve(status) == null
                        ? org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
                        : org.springframework.http.HttpStatus.resolve(status),
                reason);
    }

    // ---------- DTOs --------------------------------------------------------

    public record UserRow(
            String id,
            String username,
            String firstName,
            String lastName,
            String email,
            boolean enabled,
            boolean emailVerified,
            Long createdAt
    ) {}

    public record UserPage(List<UserRow> content, long total, int page, int size) {}

    public record UserDetail(
            String id,
            String username,
            String firstName,
            String lastName,
            String email,
            boolean enabled,
            boolean emailVerified,
            Long createdAt,
            List<String> realmRoles
    ) {}

    public record CreateUserRequest(
            String username,
            String email,
            String firstName,
            String lastName,
            Boolean enabled,
            Boolean emailVerified,
            String password,
            Boolean passwordTemporary,
            List<String> realmRoles
    ) {}

    public record UpdateUserRequest(
            String email,
            String firstName,
            String lastName,
            Boolean enabled,
            Boolean emailVerified,
            List<String> realmRoles
    ) {}

    public record ResetPasswordRequest(String password, boolean temporary) {}
}
