package ci.itechciv.sigdep.hub.ingestion.controller;

import ci.itechciv.sigdep.contracts.EntityType;
import ci.itechciv.sigdep.contracts.SyncBatchRequest;
import ci.itechciv.sigdep.contracts.SyncBatchResponse;
import ci.itechciv.sigdep.contracts.dto.DispensationDto;
import ci.itechciv.sigdep.contracts.dto.PatientDto;
import ci.itechciv.sigdep.contracts.dto.TreatmentInitiationDto;
import ci.itechciv.sigdep.contracts.dto.VisitDto;
import ci.itechciv.sigdep.hub.domain.entity.Site;
import ci.itechciv.sigdep.hub.domain.repository.SiteRepository;
import ci.itechciv.sigdep.hub.domain.service.SiteResolver;
import ci.itechciv.sigdep.hub.ingestion.writer.InitiationWriter;
import ci.itechciv.sigdep.hub.ingestion.writer.PatientWriter;
import ci.itechciv.sigdep.hub.ingestion.writer.VisitWriter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SiteResolver siteResolver;
    private final SiteRepository sites;
    private final PatientWriter patientWriter;
    private final VisitWriter visitWriter;
    private final InitiationWriter initiationWriter;

    public SyncController(SiteResolver siteResolver,
                          SiteRepository sites,
                          PatientWriter patientWriter,
                          VisitWriter visitWriter,
                          InitiationWriter initiationWriter) {
        this.siteResolver = siteResolver;
        this.sites = sites;
        this.patientWriter = patientWriter;
        this.visitWriter = visitWriter;
        this.initiationWriter = initiationWriter;
    }

    @PostMapping("/patients")
    public ResponseEntity<SyncBatchResponse> ingestPatients(@RequestBody SyncBatchRequest<PatientDto> batch) {
        Site site = siteResolver.resolve(batch.siteCode(), null);
        log.info("Ingesting {} patients for site {} (batch {})",
                batch.records().size(), site.getCode(), batch.batchId());

        var result = patientWriter.upsertBatch(site.getId(), batch.records());
        sites.touchLastSyncAt(site.getId());

        return ResponseEntity.ok(new SyncBatchResponse(
                batch.batchId(), result.accepted(), result.rejected(), result.errors()));
    }

    @PostMapping("/visits")
    public ResponseEntity<SyncBatchResponse> ingestVisits(@RequestBody SyncBatchRequest<VisitDto> batch) {
        Site site = siteResolver.resolve(batch.siteCode(), null);
        log.info("Ingesting {} visits for site {} (batch {})",
                batch.records().size(), site.getCode(), batch.batchId());

        var result = visitWriter.upsertBatch(site.getId(), batch.records());
        sites.touchLastSyncAt(site.getId());

        return ResponseEntity.ok(new SyncBatchResponse(
                batch.batchId(), result.accepted(), result.rejected(), result.errors()));
    }

    @PostMapping("/treatment_initiations")
    public ResponseEntity<SyncBatchResponse> ingestInitiations(
            @RequestBody SyncBatchRequest<TreatmentInitiationDto> batch) {
        Site site = siteResolver.resolve(batch.siteCode(), null);
        log.info("Ingesting {} treatment initiations for site {} (batch {})",
                batch.records().size(), site.getCode(), batch.batchId());

        var result = initiationWriter.upsertBatch(site.getId(), batch.records());
        sites.touchLastSyncAt(site.getId());

        return ResponseEntity.ok(new SyncBatchResponse(
                batch.batchId(), result.accepted(), result.rejected(), result.errors()));
    }

    @PostMapping("/dispensations")
    public ResponseEntity<SyncBatchResponse> ingestDispensations(@RequestBody SyncBatchRequest<DispensationDto> batch) {
        return ResponseEntity.ok(new SyncBatchResponse(batch.batchId(), batch.records().size(), 0, List.of()));
    }

    @PostMapping("/{entityType}/backfill")
    public ResponseEntity<SyncBatchResponse> ingestBackfill(
            @PathVariable EntityType entityType,
            @RequestBody SyncBatchRequest<Object> batch) {
        return ResponseEntity.ok(new SyncBatchResponse(batch.batchId(), batch.records().size(), 0, List.of()));
    }
}
