package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.domain.service.KpiService;
import ci.itechciv.sigdep.hub.domain.service.KpiService.DashboardKpis;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardKpiController {

    private final KpiService kpiService;

    public DashboardKpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping("/kpis")
    public DashboardKpis dashboardKpis() {
        return kpiService.dashboardKpis();
    }
}
