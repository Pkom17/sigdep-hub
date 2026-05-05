package ci.itechciv.sigdep.hub.ingestion.writer;

import ci.itechciv.sigdep.contracts.SyncBatchResponse;
import ci.itechciv.sigdep.contracts.SyncBatchResponse.RecordError;
import ci.itechciv.sigdep.contracts.dto.VisitDto;
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

/**
 * Each record is upserted in its own short transaction so a single bad row
 * (e.g. a visit_date with no matching partition) doesn't poison the whole
 * batch. The PostgreSQL "current transaction is aborted" error after the
 * first failure was the symptom — see commit-time investigation in
 * VisitWriter for the partitioning issue that surfaced this.
 */
@Repository
public class VisitWriter {

    private static final Logger log = LoggerFactory.getLogger(VisitWriter.class);

    private final JdbcTemplate jdbc;
    private final PatientResolver patientResolver;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public VisitWriter(JdbcTemplate jdbc,
                       PatientResolver patientResolver,
                       ObjectMapper json,
                       PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.patientResolver = patientResolver;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    public BatchResult upsertBatch(long siteId, List<VisitDto> batch) {
        int accepted = 0;
        int rejected = 0;
        List<RecordError> errors = new ArrayList<>();

        for (VisitDto v : batch) {
            try {
                Long patientId = patientResolver.resolveId(siteId, v.patientSourceUuid());
                if (patientId == null) {
                    rejected++;
                    errors.add(new RecordError(v.sourceUuid(), "UNKNOWN_PATIENT",
                            "Patient " + v.patientSourceUuid() + " not yet ingested"));
                    continue;
                }
                tx.executeWithoutResult(status -> upsertOne(siteId, patientId, v));
                accepted++;
            } catch (RuntimeException e) {
                rejected++;
                errors.add(new RecordError(v.sourceUuid(), "UPSERT_FAILED",
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                log.warn("Failed to upsert visit {}: {}", v.sourceUuid(), e.getMessage());
            }
        }
        return new BatchResult(accepted, rejected, errors);
    }

    private void upsertOne(long siteId, long patientId, VisitDto v) {
        String extraJson = v.extraData() == null ? null : writeJson(v.extraData());

        jdbc.update(
                """
                INSERT INTO core.visits (
                  patient_id, site_id, source_uuid, source_form, visit_date,
                  next_visit_date, tb_screening_result, tb_diagnosed,
                  tb_treatment_status, tb_treatment_start_date,
                  who_stage, cdc_stage, ctx_prescribed, ctx_start_date,
                  ivsa_success_confirmation_date, is_pregnant, is_breastfeeding,
                  weight_kg, height_cm, extra_data, voided,
                  created_at, updated_at
                ) VALUES (
                  ?, ?, ?, ?, ?,
                  ?, ?, ?,
                  ?, ?,
                  ?, ?, ?, ?,
                  ?, ?, ?,
                  ?, ?, ?::jsonb, ?,
                  NOW(), NOW()
                )
                ON CONFLICT (site_id, source_uuid, visit_date) DO UPDATE SET
                  next_visit_date          = EXCLUDED.next_visit_date,
                  tb_screening_result      = EXCLUDED.tb_screening_result,
                  tb_diagnosed             = EXCLUDED.tb_diagnosed,
                  tb_treatment_status      = EXCLUDED.tb_treatment_status,
                  tb_treatment_start_date  = EXCLUDED.tb_treatment_start_date,
                  who_stage                = EXCLUDED.who_stage,
                  cdc_stage                = EXCLUDED.cdc_stage,
                  ctx_prescribed           = EXCLUDED.ctx_prescribed,
                  ctx_start_date           = EXCLUDED.ctx_start_date,
                  ivsa_success_confirmation_date = EXCLUDED.ivsa_success_confirmation_date,
                  is_pregnant              = EXCLUDED.is_pregnant,
                  is_breastfeeding         = EXCLUDED.is_breastfeeding,
                  weight_kg                = EXCLUDED.weight_kg,
                  height_cm                = EXCLUDED.height_cm,
                  extra_data               = EXCLUDED.extra_data,
                  voided                   = EXCLUDED.voided,
                  updated_at               = NOW()
                """,
                new Object[] {
                        patientId, siteId, v.sourceUuid(), v.sourceForm(),
                        v.visitDate() == null ? null : Date.valueOf(v.visitDate()),
                        v.nextVisitDate() == null ? null : Date.valueOf(v.nextVisitDate()),
                        v.tbScreeningResult(), v.tbDiagnosed(),
                        v.tbTreatmentStatus(),
                        v.tbTreatmentStartDate() == null ? null : Date.valueOf(v.tbTreatmentStartDate()),
                        v.whoStage(), v.cdcStage(), v.ctxPrescribed(),
                        v.ctxStartDate() == null ? null : Date.valueOf(v.ctxStartDate()),
                        v.ivsaSuccessConfirmationDate() == null ? null : Date.valueOf(v.ivsaSuccessConfirmationDate()),
                        v.isPregnant(), v.isBreastfeeding(),
                        v.weightKg(), v.heightCm(),
                        extraJson,
                        Boolean.TRUE.equals(v.voided())
                },
                new int[] {
                        Types.BIGINT, Types.BIGINT, Types.OTHER, Types.VARCHAR, Types.DATE,
                        Types.DATE, Types.VARCHAR, Types.BOOLEAN,
                        Types.VARCHAR, Types.DATE,
                        Types.SMALLINT, Types.VARCHAR, Types.BOOLEAN, Types.DATE,
                        Types.DATE, Types.BOOLEAN, Types.BOOLEAN,
                        Types.DECIMAL, Types.DECIMAL, Types.VARCHAR, Types.BOOLEAN
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
