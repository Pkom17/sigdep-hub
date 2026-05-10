package ci.itechciv.sigdep.hub.console.controller;

import ci.itechciv.sigdep.hub.domain.service.SyncQueryService;
import ci.itechciv.sigdep.hub.domain.service.SyncQueryService.BatchPage;
import ci.itechciv.sigdep.hub.domain.service.SyncQueryService.DailyVolume;
import ci.itechciv.sigdep.hub.domain.service.SyncQueryService.LateSitePage;
import ci.itechciv.sigdep.hub.domain.service.SyncQueryService.SyncSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoints feeding the console "Synchronisation" page. The actual
 * sync ingest endpoints live on a separate service ({@code ingestion-api},
 * /api/v1/sync/...) — this controller only queries the audit log.
 */
@RestController
@RequestMapping("/api/v1/sync")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','IT_ADMIN','NATIONAL_VIEWER','AUDITOR')")
public class SyncQueryController {

    private final SyncQueryService service;

    public SyncQueryController(SyncQueryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public SyncSummary summary(
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId) {
        return service.summary(regionId, districtId, siteId);
    }

    @GetMapping("/daily")
    public List<DailyVolume> daily(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId) {
        return service.daily(days, regionId, districtId, siteId);
    }

    @GetMapping("/batches")
    public BatchPage batches(
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.batches(regionId, districtId, siteId,
                entityType, status, sort, dir, page, size);
    }

    @GetMapping("/late-sites")
    public LateSitePage lateSites(
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.lateSites(bucket, regionId, districtId, siteId,
                sort, dir, page, size);
    }
}
