package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.domain.service.TptService;
import ci.itechciv.sigdep.hub.domain.service.TptService.RecordPage;
import ci.itechciv.sigdep.hub.domain.service.TptService.TptRecord;
import ci.itechciv.sigdep.hub.domain.service.TptService.TptSummary;
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
@RequestMapping("/api/v1/tpt")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','IT_ADMIN','NATIONAL_VIEWER','REGIONAL_COORD','ANALYST','AUDITOR')")
public class TptController {

    private final TptService service;

    public TptController(TptService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public TptSummary summary(
            @RequestParam(defaultValue = "60") int months,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId) {
        int safe = Math.max(1, Math.min(120, months));
        return service.summary(safe, regionId, districtId, siteId);
    }

    @GetMapping("/records")
    public RecordPage records(
            @RequestParam(defaultValue = "60") int months,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.records(Math.max(1, Math.min(120, months)),
                regionId, districtId, siteId, page, size);
    }

    @GetMapping(value = "/records.csv", produces = "text/csv;charset=UTF-8")
    public void recordsCsv(
            @RequestParam(defaultValue = "60") int months,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId,
            HttpServletResponse response) throws IOException {

        int safeMonths = Math.max(1, Math.min(120, months));
        RecordPage page = service.records(safeMonths,
                regionId, districtId, siteId, 0, 5000);

        String filename = "tpt-" + safeMonths + "m.csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        DateTimeFormatter df = DateTimeFormatter.ISO_LOCAL_DATE;
        try (PrintWriter w = response.getWriter()) {
            w.write('﻿'); // BOM
            w.println("date_record;patient_code;date_suivi;date_fin;statut;protocole;outcome;ordre;observance;poids_kg;prochaine_visite;site_code;site_nom");
            for (TptRecord r : page.content()) {
                w.print(r.recordDate() == null ? "" : r.recordDate().format(df));
                w.print(';');
                w.print(csv(r.patientCode()));
                w.print(';');
                w.print(r.followupDate() == null ? "" : r.followupDate().format(df));
                w.print(';');
                w.print(r.endDate() == null ? "" : r.endDate().format(df));
                w.print(';');
                w.print(csv(r.tptStatus()));
                w.print(';');
                w.print(csv(r.tptRegimen()));
                w.print(';');
                w.print(csv(r.outcome()));
                w.print(';');
                w.print(csv(r.orderNumber()));
                w.print(';');
                w.print(csv(r.adherence()));
                w.print(';');
                w.print(r.weightKg() == null ? "" : r.weightKg().toPlainString());
                w.print(';');
                w.print(r.nextVisitDate() == null ? "" : r.nextVisitDate().format(df));
                w.print(';');
                w.print(csv(r.siteCode()));
                w.print(';');
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
