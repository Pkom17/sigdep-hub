package ci.itechciv.sigdep.hub.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * TPT (TB Preventive Therapy) analytics. Reads core.tpt_records.
 *
 * In the current ingestion only FOLLOWUP records are emitted by the
 * source (no INITIATION/END), so we treat each row as the lifecycle
 * record for one patient × course: record_date is the start, tpt_end_date
 * the end (if any), tpt_outcome the closing label.
 */
@Service
public class TptService {

    private final JdbcTemplate jdbc;

    public TptService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String regionFilter(Long regionId) {
        return regionId == null ? ""
                : " JOIN core.sites s ON s.id = t.site_id"
                + " JOIN core.districts d ON d.id = s.district_id AND d.region_id = ?";
    }

    private static Object[] buildArgs(Long regionId, Object... rest) {
        if (regionId == null) return rest;
        Object[] out = new Object[rest.length + 1];
        out[0] = regionId;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    public TptSummary summary(int months, Long regionId) {
        LocalDate since = LocalDate.now().minusMonths(months);
        String region = regionFilter(regionId);

        // Cards
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM core.tpt_records t" + region
                        + " WHERE t.voided = FALSE",
                Long.class, buildArgs(regionId));

        Long inPeriod = jdbc.queryForObject(
                "SELECT count(*) FROM core.tpt_records t" + region
                        + " WHERE t.voided = FALSE AND t.record_date >= ?",
                Long.class, buildArgs(regionId, since));

        Long completed = jdbc.queryForObject(
                "SELECT count(*) FROM core.tpt_records t" + region
                        + " WHERE t.voided = FALSE AND t.tpt_outcome IS NOT NULL"
                        + "   AND t.record_date >= ?",
                Long.class, buildArgs(regionId, since));

        Long ongoing = jdbc.queryForObject(
                "SELECT count(*) FROM core.tpt_records t" + region
                        + " WHERE t.voided = FALSE"
                        + "   AND (t.tpt_outcome IS NULL OR t.tpt_end_date IS NULL"
                        + "        OR t.tpt_end_date > CURRENT_DATE)",
                Long.class, buildArgs(regionId));

        BigDecimal completionPct = inPeriod == null || inPeriod == 0 ? null
                : new BigDecimal(completed == null ? 0L : completed)
                        .multiply(new BigDecimal(100))
                        .divide(new BigDecimal(inPeriod), 1, RoundingMode.HALF_UP);

        // Yearly series (last <months> months bucketed by year)
        List<YearBucket> yearly = jdbc.query(
                "SELECT EXTRACT(year FROM t.record_date)::int AS yr, count(*) AS n"
                        + " FROM core.tpt_records t" + region
                        + " WHERE t.voided = FALSE AND t.record_date >= ?"
                        + " GROUP BY yr ORDER BY yr",
                (rs, i) -> new YearBucket(rs.getInt("yr"), rs.getLong("n")),
                buildArgs(regionId, since));

        // Outcome distribution
        List<Bucket> outcomes = jdbc.query(
                "SELECT COALESCE(t.tpt_outcome, '(non clos)') AS k, count(*) AS n"
                        + " FROM core.tpt_records t" + region
                        + " WHERE t.voided = FALSE AND t.record_date >= ?"
                        + " GROUP BY k ORDER BY n DESC",
                (rs, i) -> new Bucket(rs.getString("k"), rs.getLong("n")),
                buildArgs(regionId, since));

        // Adherence distribution
        List<Bucket> adherence = jdbc.query(
                "SELECT COALESCE(t.adherence, '(non renseigné)') AS k, count(*) AS n"
                        + " FROM core.tpt_records t" + region
                        + " WHERE t.voided = FALSE AND t.record_date >= ?"
                        + " GROUP BY k ORDER BY n DESC",
                (rs, i) -> new Bucket(rs.getString("k"), rs.getLong("n")),
                buildArgs(regionId, since));

        // Status distribution (Début / En cours / Fin / Pas de TPT)
        List<Bucket> statuses = jdbc.query(
                "SELECT COALESCE(t.tpt_status, '(non renseigné)') AS k, count(*) AS n"
                        + " FROM core.tpt_records t" + region
                        + " WHERE t.voided = FALSE AND t.record_date >= ?"
                        + " GROUP BY k ORDER BY n DESC",
                (rs, i) -> new Bucket(rs.getString("k"), rs.getLong("n")),
                buildArgs(regionId, since));

        // Regimen distribution (3HP / 6H / INH / …)
        List<Bucket> regimens = jdbc.query(
                "SELECT COALESCE(t.tpt_regimen, '(non renseigné)') AS k, count(*) AS n"
                        + " FROM core.tpt_records t" + region
                        + " WHERE t.voided = FALSE AND t.record_date >= ?"
                        + " GROUP BY k ORDER BY n DESC",
                (rs, i) -> new Bucket(rs.getString("k"), rs.getLong("n")),
                buildArgs(regionId, since));

        return new TptSummary(
                total == null ? 0L : total,
                inPeriod == null ? 0L : inPeriod,
                completed == null ? 0L : completed,
                ongoing == null ? 0L : ongoing,
                completionPct,
                yearly,
                outcomes,
                adherence,
                statuses,
                regimens,
                months);
    }

    public RecordPage records(int months, Long regionId, int page, int size) {
        int safeSize = Math.max(1, Math.min(500, size));
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;
        LocalDate since = LocalDate.now().minusMonths(months);

        String regionJoin = regionId == null ? "" :
                " JOIN core.districts d ON d.id = site.district_id AND d.region_id = ?";

        List<Object> args = new ArrayList<>();
        if (regionId != null) args.add(regionId);
        args.add(since);

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM core.tpt_records t"
                        + " JOIN core.sites site ON site.id = t.site_id" + regionJoin
                        + " WHERE t.voided = FALSE AND t.record_date >= ?",
                Long.class, args.toArray());

        List<Object> pagedArgs = new ArrayList<>(args);
        pagedArgs.add(safeSize);
        pagedArgs.add(offset);

        List<TptRecord> rows = jdbc.query(
                "SELECT t.id, t.record_date, t.tpt_followup_date, t.tpt_end_date,"
                        + "       t.tpt_outcome, t.tpt_order_number,"
                        + "       t.tpt_status, t.tpt_regimen,"
                        + "       t.adherence, t.weight_kg, t.next_visit_date, t.patient_id,"
                        + "       site.code AS site_code, site.name AS site_name,"
                        + "       (SELECT pi.identifier_value FROM core.patient_identifiers pi"
                        + "         WHERE pi.patient_id = t.patient_id"
                        + "         ORDER BY pi.id LIMIT 1) AS patient_code"
                        + " FROM core.tpt_records t"
                        + " JOIN core.sites site ON site.id = t.site_id" + regionJoin
                        + " WHERE t.voided = FALSE AND t.record_date >= ?"
                        + " ORDER BY t.record_date DESC NULLS LAST, t.id DESC"
                        + " LIMIT ? OFFSET ?",
                (rs, i) -> new TptRecord(
                        rs.getLong("id"),
                        toLocal(rs.getDate("record_date")),
                        toLocal(rs.getDate("tpt_followup_date")),
                        toLocal(rs.getDate("tpt_end_date")),
                        rs.getString("tpt_outcome"),
                        rs.getString("tpt_order_number"),
                        rs.getString("tpt_status"),
                        rs.getString("tpt_regimen"),
                        rs.getString("adherence"),
                        rs.getBigDecimal("weight_kg"),
                        toLocal(rs.getDate("next_visit_date")),
                        rs.getLong("patient_id"),
                        rs.getString("patient_code"),
                        rs.getString("site_code"),
                        rs.getString("site_name")),
                pagedArgs.toArray());

        return new RecordPage(rows, total == null ? 0L : total, safePage, safeSize);
    }

    private static LocalDate toLocal(java.sql.Date d) {
        return d == null ? null : d.toLocalDate();
    }

    public record TptSummary(
            long totalAllTime,
            long startedInPeriod,
            long completedInPeriod,
            long ongoing,
            BigDecimal completionPct,
            List<YearBucket> yearly,
            List<Bucket> outcomes,
            List<Bucket> adherence,
            List<Bucket> statuses,
            List<Bucket> regimens,
            int periodMonths
    ) {}

    public record YearBucket(int year, long count) {}
    public record Bucket(String label, long count) {}

    public record TptRecord(
            long id,
            LocalDate recordDate,
            LocalDate followupDate,
            LocalDate endDate,
            String outcome,
            String orderNumber,
            String tptStatus,
            String tptRegimen,
            String adherence,
            BigDecimal weightKg,
            LocalDate nextVisitDate,
            long patientId,
            String patientCode,
            String siteCode,
            String siteName
    ) {}

    public record RecordPage(
            List<TptRecord> content,
            long total,
            int page,
            int size
    ) {}
}
