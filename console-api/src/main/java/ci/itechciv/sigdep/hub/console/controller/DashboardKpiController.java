package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.domain.service.KpiService;
import ci.itechciv.sigdep.hub.domain.service.KpiService.DashboardKpis;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','IT_ADMIN','NATIONAL_VIEWER','REGIONAL_COORD','DISTRICT_COORD','SITE_USER','ANALYST','AUDITOR')")
public class DashboardKpiController {

    private final KpiService kpiService;

    public DashboardKpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    /**
     * Dashboard KPIs are national-scope at the moment. Region/district/site-bound
     * users currently see country totals here; the per-page listings (Patients,
     * Sites, Visites, ...) are properly scoped through AuthScope. Scoping the
     * dashboard requires reworking 7 SQL queries plus the cache key — left as
     * a follow-up so the rest of the security model can land first.
     */
    @GetMapping("/kpis")
    public DashboardKpis dashboardKpis() {
        return kpiService.dashboardKpis();
    }
}
