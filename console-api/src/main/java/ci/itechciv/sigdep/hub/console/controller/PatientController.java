package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.domain.service.PatientQueryService;
import ci.itechciv.sigdep.hub.domain.service.PatientQueryService.EncounterDay;
import ci.itechciv.sigdep.hub.domain.service.PatientQueryService.PatientDetail;
import ci.itechciv.sigdep.hub.domain.service.PatientQueryService.PatientPage;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','IT_ADMIN','ANALYST','AUDITOR')")
public class PatientController {

    private final PatientQueryService service;

    public PatientController(PatientQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PatientPage list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(q, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientDetail> get(@PathVariable long id) {
        PatientDetail detail = service.get(id);
        return detail == null ? ResponseEntity.notFound().build()
                              : ResponseEntity.ok(detail);
    }

    @GetMapping("/{id}/encounters")
    public List<EncounterDay> encounters(@PathVariable long id) {
        return service.encounters(id);
    }
}
