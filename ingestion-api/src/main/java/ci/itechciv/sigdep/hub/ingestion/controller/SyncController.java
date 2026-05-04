package ci.itechciv.sigdep.hub.ingestion.controller;

import ci.itechciv.sigdep.contracts.EntityType;
import ci.itechciv.sigdep.contracts.SyncBatchRequest;
import ci.itechciv.sigdep.contracts.SyncBatchResponse;
import ci.itechciv.sigdep.contracts.dto.DispensationDto;
import ci.itechciv.sigdep.contracts.dto.VisitDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    @PostMapping("/visits")
    public ResponseEntity<SyncBatchResponse> ingestVisits(@RequestBody SyncBatchRequest<VisitDto> batch) {
        // TODO: validate site, dispatch to VisitWriter.upsertBatch
        return ResponseEntity.ok(new SyncBatchResponse(batch.batchId(), batch.records().size(), 0, List.of()));
    }

    @PostMapping("/dispensations")
    public ResponseEntity<SyncBatchResponse> ingestDispensations(@RequestBody SyncBatchRequest<DispensationDto> batch) {
        // TODO: validate site, dispatch to DispensationWriter.upsertBatch
        return ResponseEntity.ok(new SyncBatchResponse(batch.batchId(), batch.records().size(), 0, List.of()));
    }

    @PostMapping("/{entityType}/backfill")
    public ResponseEntity<SyncBatchResponse> ingestBackfill(
            @PathVariable EntityType entityType,
            @RequestBody SyncBatchRequest<Object> batch) {
        // TODO: rate-limit + dispatch
        return ResponseEntity.ok(new SyncBatchResponse(batch.batchId(), batch.records().size(), 0, List.of()));
    }
}
