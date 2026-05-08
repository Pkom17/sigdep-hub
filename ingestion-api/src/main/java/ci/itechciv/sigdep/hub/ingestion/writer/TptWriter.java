package ci.itechciv.sigdep.hub.ingestion.writer;

import ci.itechciv.sigdep.contracts.SyncBatchResponse;
import ci.itechciv.sigdep.contracts.SyncBatchResponse.RecordError;
import ci.itechciv.sigdep.contracts.dto.TptRecordDto;
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
 * Writes a TPT record (FOLLOWUP or OUTCOME). Each row in its own short
 * transaction so a single bad record doesn't poison the batch.
 */
@Repository
public class TptWriter {

    private static final Logger log = LoggerFactory.getLogger(TptWriter.class);

    private final JdbcTemplate jdbc;
    private final PatientResolver patientResolver;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public TptWriter(JdbcTemplate jdbc,
                     PatientResolver patientResolver,
                     ObjectMapper json,
                     PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.patientResolver = patientResolver;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    public BatchResult upsertBatch(long siteId, List<TptRecordDto> batch) {
        int accepted = 0;
        int rejected = 0;
        List<RecordError> errors = new ArrayList<>();

        for (TptRecordDto t : batch) {
            try {
                Long patientId = patientResolver.resolveId(siteId, t.patientSourceUuid());
                if (patientId == null) {
                    rejected++;
                    errors.add(new RecordError(t.sourceUuid(), "UNKNOWN_PATIENT",
                            "Patient " + t.patientSourceUuid() + " not yet ingested"));
                    continue;
                }
                final long pid = patientId;
                tx.executeWithoutResult(status -> upsertOne(siteId, pid, t));
                accepted++;
            } catch (RuntimeException e) {
                rejected++;
                errors.add(new RecordError(t.sourceUuid(), "UPSERT_FAILED",
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("Failed to upsert TPT record {}: {}", t.sourceUuid(), e.getMessage());
            }
        }
        return new BatchResult(accepted, rejected, errors);
    }

    private void upsertOne(long siteId, long patientId, TptRecordDto t) {
        String extraJson = t.extraData() == null ? null : writeJson(t.extraData());

        jdbc.update(
                """
                INSERT INTO core.tpt_records (
                  patient_id, site_id, source_uuid,
                  record_type, record_date,
                  tpt_followup_date, tpt_end_date, tpt_outcome, tpt_order_number,
                  tpt_status, tpt_regimen,
                  adherence, weight_kg, next_visit_date,
                  extra_data, voided, created_at, updated_at
                ) VALUES (
                  ?, ?, ?,
                  ?, ?,
                  ?, ?, ?, ?,
                  ?, ?,
                  ?, ?, ?,
                  ?::jsonb, ?, NOW(), NOW()
                )
                ON CONFLICT (site_id, source_uuid) DO UPDATE SET
                  record_type       = EXCLUDED.record_type,
                  record_date       = EXCLUDED.record_date,
                  tpt_followup_date = EXCLUDED.tpt_followup_date,
                  tpt_end_date      = EXCLUDED.tpt_end_date,
                  tpt_outcome       = EXCLUDED.tpt_outcome,
                  tpt_order_number  = EXCLUDED.tpt_order_number,
                  tpt_status        = EXCLUDED.tpt_status,
                  tpt_regimen       = EXCLUDED.tpt_regimen,
                  adherence         = EXCLUDED.adherence,
                  weight_kg         = EXCLUDED.weight_kg,
                  next_visit_date   = EXCLUDED.next_visit_date,
                  extra_data        = EXCLUDED.extra_data,
                  voided            = EXCLUDED.voided,
                  updated_at        = NOW()
                """,
                patientId, siteId, t.sourceUuid(),
                t.recordType(),
                t.recordDate() == null ? null : Date.valueOf(t.recordDate()),
                t.tptFollowupDate() == null ? null : Date.valueOf(t.tptFollowupDate()),
                t.tptEndDate()      == null ? null : Date.valueOf(t.tptEndDate()),
                t.tptOutcome(), t.tptOrderNumber(),
                t.tptStatus(), t.tptRegimen(),
                t.adherence(), t.weightKg(),
                t.nextVisitDate()   == null ? null : Date.valueOf(t.nextVisitDate()),
                extraJson,
                Boolean.TRUE.equals(t.voided()));
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
