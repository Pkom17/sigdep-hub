package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.domain.service.ClinicService;
import ci.itechciv.sigdep.hub.domain.service.ClinicService.ClinicSummary;
import ci.itechciv.sigdep.hub.domain.service.ClinicService.VisitPage;
import ci.itechciv.sigdep.hub.domain.service.ClinicService.VisitRow;
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
@RequestMapping("/api/v1/clinic")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','IT_ADMIN','NATIONAL_VIEWER','REGIONAL_COORD','ANALYST','AUDITOR')")
public class ClinicController {

    private final ClinicService service;

    public ClinicController(ClinicService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ClinicSummary summary(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId) {
        return service.summary(Math.max(1, Math.min(120, months)),
                regionId, districtId, siteId);
    }

    @GetMapping("/visits")
    public VisitPage visits(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.visits(Math.max(1, Math.min(120, months)),
                regionId, districtId, siteId, page, size);
    }

    @GetMapping(value = "/visits.csv", produces = "text/csv;charset=UTF-8")
    public void visitsCsv(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId,
            HttpServletResponse response) throws IOException {

        int safeMonths = Math.max(1, Math.min(120, months));
        VisitPage page = service.visits(safeMonths, regionId, districtId, siteId, 0, 5000);

        String filename = "clinique-" + safeMonths + "m.csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        DateTimeFormatter df = DateTimeFormatter.ISO_LOCAL_DATE;
        try (PrintWriter w = response.getWriter()) {
            w.write('﻿'); // BOM
            w.println("date_visite;patient_code;formulaire;stade_oms;dépistage_tb;regime_arv;arv_jours;ctx_jours;poids_kg;taille_cm;imc;cv_copies_ml;cd4;tpt_statut;tpt_protocole;prochaine_visite;site_code;site_nom");
            for (VisitRow v : page.content()) {
                w.print(v.visitDate() == null ? "" : v.visitDate().format(df)); w.print(';');
                w.print(csv(v.patientCode())); w.print(';');
                w.print(csv(v.sourceForm())); w.print(';');
                w.print(csv(v.whoStage())); w.print(';');
                w.print(csv(v.tbScreening())); w.print(';');
                w.print(csv(v.arvRegimen())); w.print(';');
                w.print(v.arvTreatmentDays() == null ? "" : v.arvTreatmentDays()); w.print(';');
                w.print(v.cotrimTreatmentDays() == null ? "" : v.cotrimTreatmentDays()); w.print(';');
                w.print(v.weightKg() == null ? "" : v.weightKg().toPlainString()); w.print(';');
                w.print(v.heightCm() == null ? "" : v.heightCm().toPlainString()); w.print(';');
                w.print(v.bmi() == null ? "" : v.bmi().toPlainString()); w.print(';');
                w.print(v.viralLoad() == null ? "" : v.viralLoad().toPlainString()); w.print(';');
                w.print(v.cd4Count() == null ? "" : v.cd4Count()); w.print(';');
                w.print(csv(v.tptStatus())); w.print(';');
                w.print(csv(v.tptRegimen())); w.print(';');
                w.print(v.nextVisitDate() == null ? "" : v.nextVisitDate().format(df)); w.print(';');
                w.print(csv(v.siteCode())); w.print(';');
                w.println(csv(v.siteName()));
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
