package ci.itechciv.sigdep.hub.console.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicKpiController {

    @GetMapping("/kpis")
    public Map<String, Object> nationalKpis() {
        // TODO: replace with cached query against analytics views
        return Map.of(
                "patientsActive", 0,
                "sites", 0,
                "viralSuppression", 0.0,
                "arvCoverage", 0.0
        );
    }
}
