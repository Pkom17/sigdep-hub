package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.console.security.AuthScope;
import ci.itechciv.sigdep.hub.console.security.AuthScope.Scope;
import ci.itechciv.sigdep.hub.domain.service.SiteQueryService;
import ci.itechciv.sigdep.hub.domain.service.SiteQueryService.DistrictRef;
import ci.itechciv.sigdep.hub.domain.service.SiteQueryService.RegionRef;
import ci.itechciv.sigdep.hub.domain.service.SiteQueryService.SitePage;
import ci.itechciv.sigdep.hub.domain.service.SiteQueryService.SiteRef;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sites")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','IT_ADMIN','NATIONAL_VIEWER','REGIONAL_COORD','DISTRICT_COORD','SITE_USER','ANALYST','AUDITOR')")
public class SiteController {

    private final SiteQueryService service;
    private final AuthScope authScope;

    public SiteController(SiteQueryService service, AuthScope authScope) {
        this.service = service;
        this.authScope = authScope;
    }

    @GetMapping
    public SitePage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Scope s = authScope.effective(regionId, districtId, siteId);
        return service.list(q, status, s.regionId(), s.districtId(), s.siteId(), sort, dir, page, size);
    }

    @GetMapping("/regions")
    public List<RegionRef> regions() {
        return service.regions();
    }

    /** Districts of a region (or all districts if regionId is omitted). */
    @GetMapping("/districts")
    public List<DistrictRef> districts(
            @RequestParam(required = false) Long regionId) {
        return service.districts(regionId);
    }

    /**
     * Sites of a district, or sites of a region when only regionId is given.
     * Returns an empty list if neither is set, to avoid sending 3,880 sites.
     */
    @GetMapping("/list-of")
    public List<SiteRef> sitesOf(
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId) {
        return service.sitesOf(regionId, districtId);
    }
}
