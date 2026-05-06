package ci.itechciv.sigdep.hub.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only patient listing for the console. We deliberately don't expose
 * names, phone or address — those are filtered out before reaching the hub.
 */
@Service
public class PatientQueryService {

    private final JdbcTemplate jdbc;

    public PatientQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PatientPage list(String search, int page, int size) {
        int safeSize = Math.max(1, Math.min(100, size));
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;

        String like = search == null || search.isBlank() ? null : "%" + search.trim() + "%";

        StringBuilder where = new StringBuilder("WHERE p.voided = FALSE");
        if (like != null) {
            where.append(" AND (p.source_uuid::text ILIKE ? ")
                 .append("OR EXISTS (SELECT 1 FROM core.patient_identifiers pi ")
                 .append("WHERE pi.patient_id = p.id AND pi.identifier_value ILIKE ?))");
        }

        Long total = like == null
                ? jdbc.queryForObject("SELECT count(*) FROM core.patients p " + where, Long.class)
                : jdbc.queryForObject("SELECT count(*) FROM core.patients p " + where, Long.class, like, like);

        String sql = """
                SELECT p.id, p.source_uuid, p.sex, p.birth_date,
                       s.code AS site_code, s.name AS site_name,
                       (SELECT pi.identifier_value FROM core.patient_identifiers pi
                          WHERE pi.patient_id = p.id
                          ORDER BY pi.id LIMIT 1) AS primary_identifier
                FROM core.patients p
                JOIN core.sites s ON s.id = p.site_id
                """ + where + """
                 ORDER BY p.id DESC
                LIMIT ? OFFSET ?
                """;

        List<PatientRow> rows = like == null
                ? jdbc.query(sql, this::mapRow, safeSize, offset)
                : jdbc.query(sql, this::mapRow, like, like, safeSize, offset);

        return new PatientPage(rows, total == null ? 0L : total, safePage, safeSize);
    }

    private PatientRow mapRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        java.sql.Date bd = rs.getDate("birth_date");
        return new PatientRow(
                rs.getLong("id"),
                UUID.fromString(rs.getString("source_uuid")),
                rs.getString("sex"),
                bd == null ? null : bd.toLocalDate(),
                rs.getString("site_code"),
                rs.getString("site_name"),
                rs.getString("primary_identifier"));
    }

    public record PatientRow(
            long id,
            UUID sourceUuid,
            String sex,
            LocalDate birthDate,
            String siteCode,
            String siteName,
            String primaryIdentifier
    ) {}

    public record PatientPage(
            List<PatientRow> content,
            long total,
            int page,
            int size
    ) {}

    public PatientDetail get(long id) {
        List<PatientDetail> rows = jdbc.query(
                """
                SELECT p.id, p.source_uuid, p.sex, p.birth_date,
                       p.profession, p.education_level, p.marital_status,
                       p.birth_place,
                       s.code AS site_code, s.name AS site_name
                FROM core.patients p
                JOIN core.sites s ON s.id = p.site_id
                WHERE p.id = ? AND p.voided = FALSE
                """,
                (rs, i) -> {
                    java.sql.Date bd = rs.getDate("birth_date");
                    return new PatientDetail(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("source_uuid")),
                            rs.getString("sex"),
                            bd == null ? null : bd.toLocalDate(),
                            rs.getString("profession"),
                            rs.getString("education_level"),
                            rs.getString("marital_status"),
                            rs.getString("birth_place"),
                            rs.getString("site_code"),
                            rs.getString("site_name"),
                            jdbc.queryForList(
                                    "SELECT identifier_value FROM core.patient_identifiers WHERE patient_id = ? ORDER BY id",
                                    String.class, rs.getLong("id")));
                },
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public record PatientDetail(
            long id,
            UUID sourceUuid,
            String sex,
            LocalDate birthDate,
            String profession,
            String educationLevel,
            String maritalStatus,
            String birthPlace,
            String siteCode,
            String siteName,
            List<String> identifiers
    ) {}

    public List<TimelineEntry> timeline(long patientId) {
        return jdbc.query(
                """
                SELECT entry_date, kind, label, detail FROM (
                  SELECT visit_date AS entry_date,
                         'visit' AS kind,
                         COALESCE(source_form, 'Visite') AS label,
                         to_char(visit_date, 'DD/MM/YYYY') AS detail
                    FROM core.visits
                   WHERE patient_id = ? AND voided = FALSE
                  UNION ALL
                  SELECT COALESCE(arv_init_date, enrollment_date) AS entry_date,
                         'initiation' AS kind,
                         'Initiation ARV' AS label,
                         COALESCE(arv_regimen_initial, '—') AS detail
                    FROM core.treatment_initiations
                   WHERE patient_id = ? AND voided = FALSE
                  UNION ALL
                  SELECT exam_date AS entry_date,
                         'lab' AS kind,
                         COALESCE(test_name, 'Examen') AS label,
                         COALESCE(value_text, value_numeric::text, '—') AS detail
                    FROM core.lab_results
                   WHERE patient_id = ? AND voided = FALSE
                ) t
                WHERE entry_date IS NOT NULL
                ORDER BY entry_date DESC
                LIMIT 200
                """,
                (rs, i) -> new TimelineEntry(
                        rs.getDate("entry_date").toLocalDate(),
                        rs.getString("kind"),
                        rs.getString("label"),
                        rs.getString("detail")),
                patientId, patientId, patientId);
    }

    public record TimelineEntry(
            LocalDate date,
            String kind,
            String label,
            String detail
    ) {}
}
