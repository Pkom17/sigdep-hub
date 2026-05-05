package ci.itechciv.sigdep.hub.ingestion.writer;

import ci.itechciv.sigdep.contracts.SyncBatchResponse;
import ci.itechciv.sigdep.contracts.SyncBatchResponse.RecordError;
import ci.itechciv.sigdep.contracts.dto.TreatmentInitiationDto;
import ci.itechciv.sigdep.hub.domain.service.PatientResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class InitiationWriter {

    private static final Logger log = LoggerFactory.getLogger(InitiationWriter.class);

    private final JdbcTemplate jdbc;
    private final PatientResolver patientResolver;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public InitiationWriter(JdbcTemplate jdbc,
                            PatientResolver patientResolver,
                            ObjectMapper json,
                            PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.patientResolver = patientResolver;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    public BatchResult upsertBatch(long siteId, List<TreatmentInitiationDto> batch) {
        int accepted = 0;
        int rejected = 0;
        List<RecordError> errors = new ArrayList<>();

        for (TreatmentInitiationDto t : batch) {
            try {
                Long patientId = patientResolver.resolveId(siteId, t.patientSourceUuid());
                if (patientId == null) {
                    rejected++;
                    errors.add(new RecordError(t.sourceUuid(), "UNKNOWN_PATIENT",
                            "Patient " + t.patientSourceUuid() + " not yet ingested"));
                    continue;
                }
                tx.executeWithoutResult(status -> upsertOne(siteId, patientId, t));
                accepted++;
            } catch (RuntimeException e) {
                rejected++;
                errors.add(new RecordError(t.sourceUuid(), "UPSERT_FAILED",
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("Failed to upsert initiation {}: {}", t.sourceUuid(), e.getMessage());
            }
        }
        return new BatchResult(accepted, rejected, errors);
    }

    private void upsertOne(long siteId, long patientId, TreatmentInitiationDto t) {
        String extraJson = t.extraData() == null ? null : writeJson(t.extraData());

        jdbc.update(
                """
                INSERT INTO core.treatment_initiations (
                  patient_id, site_id, source_uuid,
                  enrollment_date, arv_init_date, hiv_test_date,
                  hiv_type, entry_point, extra_data,
                  voided, created_at, updated_at
                ) VALUES (
                  ?, ?, ?,
                  ?, ?, ?,
                  ?, ?, ?::jsonb,
                  ?, NOW(), NOW()
                )
                ON CONFLICT (site_id, source_uuid) DO UPDATE SET
                  enrollment_date = EXCLUDED.enrollment_date,
                  arv_init_date   = EXCLUDED.arv_init_date,
                  hiv_test_date   = EXCLUDED.hiv_test_date,
                  hiv_type        = EXCLUDED.hiv_type,
                  entry_point     = EXCLUDED.entry_point,
                  extra_data      = EXCLUDED.extra_data,
                  voided          = EXCLUDED.voided,
                  updated_at      = NOW()
                """,
                new Object[] {
                        patientId, siteId, t.sourceUuid(),
                        t.enrollmentDate() == null ? null : Date.valueOf(t.enrollmentDate()),
                        t.arvInitDate()    == null ? null : Date.valueOf(t.arvInitDate()),
                        t.hivTestDate()    == null ? null : Date.valueOf(t.hivTestDate()),
                        t.hivType(), t.entryPoint(), extraJson,
                        Boolean.TRUE.equals(t.voided())
                },
                new int[] {
                        Types.BIGINT, Types.BIGINT, Types.OTHER,
                        Types.DATE, Types.DATE, Types.DATE,
                        Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                        Types.BOOLEAN
                });
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
