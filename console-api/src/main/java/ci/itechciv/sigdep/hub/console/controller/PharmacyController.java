package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.domain.service.PharmacyService;
import ci.itechciv.sigdep.hub.domain.service.PharmacyService.DispensationPage;
import ci.itechciv.sigdep.hub.domain.service.PharmacyService.DispensationRow;
import ci.itechciv.sigdep.hub.domain.service.PharmacyService.PharmacySummary;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','IT_ADMIN','NATIONAL_VIEWER','REGIONAL_COORD','ANALYST','AUDITOR')")
public class PharmacyController {

    private final PharmacyService service;

    public PharmacyController(PharmacyService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public PharmacySummary summary(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId) {
        return service.summary(Math.max(1, Math.min(120, months)),
                regionId, districtId, siteId);
    }

    @GetMapping("/dispensations")
    public DispensationPage dispensations(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.dispensations(Math.max(1, Math.min(120, months)),
                regionId, districtId, siteId, page, size);
    }

    @GetMapping(value = "/dispensations.csv", produces = "text/csv;charset=UTF-8")
    public void dispensationsCsv(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId,
            HttpServletResponse response) throws IOException {

        int safeMonths = Math.max(1, Math.min(120, months));
        DispensationPage page = service.dispensations(safeMonths,
                regionId, districtId, siteId, 0, 5000);

        String filename = "pharmacie-" + safeMonths + "m.csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        DateTimeFormatter df = DateTimeFormatter.ISO_LOCAL_DATE;
        try (PrintWriter w = response.getWriter()) {
            w.write('﻿'); // BOM
            w.println("date_dispensation;patient_code;regime_arv;arv_jours;ctx_jours;prochaine_visite;site_code;site_nom");
            for (DispensationRow r : page.content()) {
                w.print(r.visitDate() == null ? "" : r.visitDate().format(df)); w.print(';');
                w.print(csv(r.patientCode())); w.print(';');
                w.print(csv(r.arvRegimen())); w.print(';');
                w.print(r.arvDays() == null ? "" : r.arvDays()); w.print(';');
                w.print(r.cotrimDays() == null ? "" : r.cotrimDays()); w.print(';');
                w.print(r.nextVisitDate() == null ? "" : r.nextVisitDate().format(df)); w.print(';');
                w.print(csv(r.siteCode())); w.print(';');
                w.println(csv(r.siteName()));
            }
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        boolean q = s.contains(";") || s.contains("\"") || s.contains("\n");
        if (!q) return s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
