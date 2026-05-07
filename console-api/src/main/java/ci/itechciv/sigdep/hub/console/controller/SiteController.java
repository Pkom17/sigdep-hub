package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.domain.service.SiteQueryService;
import ci.itechciv.sigdep.hub.domain.service.SiteQueryService.RegionRef;
import ci.itechciv.sigdep.hub.domain.service.SiteQueryService.SitePage;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sites")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','IT_ADMIN','NATIONAL_VIEWER','REGIONAL_COORD','ANALYST','AUDITOR')")
public class SiteController {

    private final SiteQueryService service;

    public SiteController(SiteQueryService service) {
        this.service = service;
    }

    @GetMapping
    public SitePage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long regionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(q, status, regionId, page, size);
    }

    @GetMapping("/regions")
    public List<RegionRef> regions() {
        return service.regions();
    }
}
