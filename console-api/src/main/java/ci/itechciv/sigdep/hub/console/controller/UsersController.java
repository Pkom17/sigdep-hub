package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.console.admin.KeycloakAdminService;
import ci.itechciv.sigdep.hub.console.admin.KeycloakAdminService.CreateUserRequest;
import ci.itechciv.sigdep.hub.console.admin.KeycloakAdminService.ResetPasswordRequest;
import ci.itechciv.sigdep.hub.console.admin.KeycloakAdminService.UpdateUserRequest;
import ci.itechciv.sigdep.hub.console.admin.KeycloakAdminService.UserDetail;
import ci.itechciv.sigdep.hub.console.admin.KeycloakAdminService.UserPage;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console-side admin endpoints for the Keycloak realm. All actions go through
 * a service-account ("sigdep-console-admin" client) configured via
 * {@code app.keycloak.*}; no end-user credentials are needed beyond the
 * console JWT used to call these endpoints.
 *
 * Limited to SUPER_ADMIN / IT_ADMIN roles — others have no business creating
 * or disabling accounts.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','IT_ADMIN')")
public class UsersController {

    private final KeycloakAdminService service;

    public UsersController(KeycloakAdminService service) {
        this.service = service;
    }

    @GetMapping
    public UserPage list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(q, page, size);
    }

    @GetMapping("/roles")
    public List<String> availableRoles() {
        return service.availableRoles();
    }

    @GetMapping("/{id}")
    public UserDetail get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody CreateUserRequest req) {
        String id = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable String id,
                                       @RequestBody UpdateUserRequest req) {
        service.update(id, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable String id,
                                              @RequestBody ResetPasswordRequest req) {
        service.resetPassword(id, req.password(), req.temporary());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable String id) {
        service.setEnabled(id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable String id) {
        service.setEnabled(id, false);
        return ResponseEntity.noContent().build();
    }

    /** Disabling instead of deleting is the recommended Keycloak pattern. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable String id) {
        service.setEnabled(id, false);
        return ResponseEntity.noContent().build();
    }
}
