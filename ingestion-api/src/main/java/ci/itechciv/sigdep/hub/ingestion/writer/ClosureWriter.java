package ci.itechciv.sigdep.hub.ingestion.writer;

import ci.itechciv.sigdep.contracts.SyncBatchResponse;
import ci.itechciv.sigdep.contracts.SyncBatchResponse.RecordError;
import ci.itechciv.sigdep.contracts.dto.ClosureDto;
import ci.itechciv.sigdep.hub.domain.service.PatientResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes a closure row. Each record is upserted in its own short
 * transaction so a single bad row doesn't poison the batch.
 */
@Repository
public class ClosureWriter {

    private static final Logger log = LoggerFactory.getLogger(ClosureWriter.class);

    private final JdbcTemplate jdbc;
    private final PatientResolver patientResolver;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public ClosureWriter(JdbcTemplate jdbc,
                         PatientResolver patientResolver,
                         ObjectMapper json,
                         PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.patientResolver = patientResolver;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    public BatchResult upsertBatch(long siteId, List<ClosureDto> batch) {
        int accepted = 0;
        int rejected = 0;
        List<RecordError> errors = new ArrayList<>();

        for (ClosureDto c : batch) {
            try {
                Long patientId = patientResolver.resolveId(siteId, c.patientSourceUuid());
                if (patientId == null) {
                    rejected++;
                    errors.add(new RecordError(c.sourceUuid(), "UNKNOWN_PATIENT",
                            "Patient " + c.patientSourceUuid() + " not yet ingested"));
                    continue;
                }
                final long pid = patientId;
                tx.executeWithoutResult(status -> upsertOne(siteId, pid, c));
                accepted++;
            } catch (RuntimeException e) {
                rejected++;
                errors.add(new RecordError(c.sourceUuid(), "UPSERT_FAILED",
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("Failed to upsert closure {}: {}", c.sourceUuid(), e.getMessage());
            }
        }
        return new BatchResult(accepted, rejected, errors);
    }

    private void upsertOne(long siteId, long patientId, ClosureDto c) {
        String extraJson = c.extraData() == null ? null : writeJson(c.extraData());

        jdbc.update(
                """
                INSERT INTO core.closures (
                  patient_id, site_id, source_uuid,
                  closure_type, closure_date,
                  transfer_date, transfer_destination, transfer_reason,
                  death_date, actual_death_date, death_cause_code, death_cause_text,
                  voluntary_stop_date, hiv_negative_date,
                  extra_data, voided, created_at, updated_at
                ) VALUES (
                  ?, ?, ?,
                  ?, ?,
                  ?, ?, ?,
                  ?, ?, ?, ?,
                  ?, ?,
                  ?::jsonb, ?, NOW(), NOW()
                )
                ON CONFLICT (site_id, source_uuid) DO UPDATE SET
                  closure_type         = EXCLUDED.closure_type,
                  closure_date         = EXCLUDED.closure_date,
                  transfer_date        = EXCLUDED.transfer_date,
                  transfer_destination = EXCLUDED.transfer_destination,
                  transfer_reason      = EXCLUDED.transfer_reason,
                  death_date           = EXCLUDED.death_date,
                  actual_death_date    = EXCLUDED.actual_death_date,
                  death_cause_code     = EXCLUDED.death_cause_code,
                  death_cause_text     = EXCLUDED.death_cause_text,
                  voluntary_stop_date  = EXCLUDED.voluntary_stop_date,
                  hiv_negative_date    = EXCLUDED.hiv_negative_date,
                  extra_data           = EXCLUDED.extra_data,
                  voided               = EXCLUDED.voided,
                  updated_at           = NOW()
                """,
                patientId, siteId, c.sourceUuid(),
                c.closureType(),
                c.closureDate() == null ? null : Date.valueOf(c.closureDate()),
                c.transferDate() == null ? null : Date.valueOf(c.transferDate()),
                c.transferDestination(), c.transferReason(),
                c.deathDate() == null ? null : Date.valueOf(c.deathDate()),
                c.actualDeathDate() == null ? null : Date.valueOf(c.actualDeathDate()),
                c.deathCauseCode(), c.deathCauseText(),
                c.voluntaryStopDate() == null ? null : Date.valueOf(c.voluntaryStopDate()),
                c.hivNegativeDate() == null ? null : Date.valueOf(c.hivNegativeDate()),
                extraJson,
                Boolean.TRUE.equals(c.voided()));
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize extra_data", e);
        }
    }

    public record BatchResult(int accepted, int rejected, List<SyncBatchResponse.RecordError> errors) {}
}
