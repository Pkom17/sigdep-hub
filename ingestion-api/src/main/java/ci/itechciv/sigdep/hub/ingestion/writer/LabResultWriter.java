package ci.itechciv.sigdep.hub.ingestion.writer;

import ci.itechciv.sigdep.contracts.SyncBatchResponse;
import ci.itechciv.sigdep.contracts.SyncBatchResponse.RecordError;
import ci.itechciv.sigdep.contracts.dto.LabResultDto;
import ci.itechciv.sigdep.hub.domain.service.PatientResolver;
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
 * Writes a lab result row. One DTO == one row in core.lab_results.
 * Each record is upserted in its own short transaction so a single
 * bad row doesn't poison the batch.
 */
@Repository
public class LabResultWriter {

    private static final Logger log = LoggerFactory.getLogger(LabResultWriter.class);

    private final JdbcTemplate jdbc;
    private final PatientResolver patientResolver;
    private final TransactionTemplate tx;

    public LabResultWriter(JdbcTemplate jdbc,
                           PatientResolver patientResolver,
                           PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.patientResolver = patientResolver;
        this.tx = new TransactionTemplate(txManager);
    }

    public BatchResult upsertBatch(long siteId, List<LabResultDto> batch) {
        int accepted = 0;
        int rejected = 0;
        List<RecordError> errors = new ArrayList<>();

        for (LabResultDto r : batch) {
            try {
                Long patientId = patientResolver.resolveId(siteId, r.patientSourceUuid());
                if (patientId == null) {
                    rejected++;
                    errors.add(new RecordError(r.sourceUuid(), "UNKNOWN_PATIENT",
                            "Patient " + r.patientSourceUuid() + " not yet ingested"));
                    continue;
                }
                final long pid = patientId;
                tx.executeWithoutResult(status -> upsertOne(siteId, pid, r));
                accepted++;
            } catch (RuntimeException e) {
                rejected++;
                errors.add(new RecordError(r.sourceUuid(), "UPSERT_FAILED",
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("Failed to upsert lab result {}: {}", r.sourceUuid(), e.getMessage());
            }
        }
        return new BatchResult(accepted, rejected, errors);
    }

    private void upsertOne(long siteId, long patientId, LabResultDto r) {
        jdbc.update(
                """
                INSERT INTO core.lab_results (
                  patient_id, site_id, source_uuid, encounter_source_uuid,
                  test_uuid, test_name, exam_date,
                  value_numeric, value_text, value_coded, unit,
                  voided, created_at, updated_at
                ) VALUES (
                  ?, ?, ?, ?,
                  ?, ?, ?,
                  ?, ?, ?, ?,
                  ?, NOW(), NOW()
                )
                ON CONFLICT (site_id, source_uuid) DO UPDATE SET
                  encounter_source_uuid = EXCLUDED.encounter_source_uuid,
                  test_uuid             = EXCLUDED.test_uuid,
                  test_name             = EXCLUDED.test_name,
                  exam_date             = EXCLUDED.exam_date,
                  value_numeric         = EXCLUDED.value_numeric,
                  value_text            = EXCLUDED.value_text,
                  value_coded           = EXCLUDED.value_coded,
                  unit                  = EXCLUDED.unit,
                  voided                = EXCLUDED.voided,
                  updated_at            = NOW()
                """,
                patientId, siteId, r.sourceUuid(), r.encounterSourceUuid(),
                r.testUuid(), r.testName(),
                r.examDate() == null ? null : Date.valueOf(r.examDate()),
                r.valueNumeric(), r.valueText(), r.valueCoded(), r.unit(),
                Boolean.TRUE.equals(r.voided()));
    }

    public record BatchResult(int accepted, int rejected, List<SyncBatchResponse.RecordError> errors) {}
}
