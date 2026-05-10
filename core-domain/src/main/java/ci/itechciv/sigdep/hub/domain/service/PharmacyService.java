package ci.itechciv.sigdep.hub.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only ARV pharmacy / dispensation analytics. Reads core.visits where
 * arv_regimen is set — each such visit is treated as one dispensation event,
 * with arv_treatment_days as the proxy for the dispensed duration.
 */
@Service
public class PharmacyService {

    private final JdbcTemplate jdbc;

    public PharmacyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String regionJoin(Long regionId) {
        return regionId == null ? ""
                : " JOIN core.sites s ON s.id = v.site_id"
                + " JOIN core.districts d ON d.id = s.district_id AND d.region_id = ?";
    }

    private static Object[] buildArgs(Long regionId, Object... rest) {
        if (regionId == null) return rest;
        Object[] out = new Object[rest.length + 1];
        out[0] = regionId;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    public PharmacySummary summary(int months, Long regionId) {
        LocalDate since = LocalDate.now().minusMonths(months);
        String region = regionJoin(regionId);

        Long dispensationsTotal = jdbc.queryForObject(
                "SELECT count(*) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL",
                Long.class, buildArgs(regionId));

        Long dispensationsInPeriod = jdbc.queryForObject(
                "SELECT count(*) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                        + "   AND v.visit_date >= ?",
                Long.class, buildArgs(regionId, since));

        // Distinct patients on ARV in period (file active "pharmacie")
        Long patientsOnArv = jdbc.queryForObject(
                "SELECT count(DISTINCT v.patient_id) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                        + "   AND v.visit_date >= ?",
                Long.class, buildArgs(regionId, since));

        Long distinctRegimens = jdbc.queryForObject(
                "SELECT count(DISTINCT v.arv_regimen) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                        + "   AND v.visit_date >= ?",
                Long.class, buildArgs(regionId, since));

        // % short dispensations (<30 days) — proxy for non-MMD
        Long shortCount = jdbc.queryForObject(
                "SELECT count(*) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                        + "   AND v.visit_date >= ?"
                        + "   AND v.arv_treatment_days IS NOT NULL"
                        + "   AND v.arv_treatment_days < 30",
                Long.class, buildArgs(regionId, since));

        Long withDuration = jdbc.queryForObject(
                "SELECT count(*) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                        + "   AND v.visit_date >= ?"
                        + "   AND v.arv_treatment_days IS NOT NULL",
                Long.class, buildArgs(regionId, since));

        BigDecimal shortPct = pct(shortCount, withDuration);

        // Top regimens
        List<Bucket> regimens = jdbc.query(
                "SELECT v.arv_regimen AS k, count(*) AS n, count(DISTINCT v.patient_id) AS p"
                        + " FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                        + "   AND v.visit_date >= ?"
                        + " GROUP BY v.arv_regimen ORDER BY n DESC LIMIT 15",
                (rs, i) -> new Bucket(rs.getString("k"), rs.getLong("n"), rs.getLong("p")),
                buildArgs(regionId, since));

        // Duration distribution (4 buckets)
        DurationBuckets durations = jdbc.query(
                "SELECT"
                        + "  count(*) FILTER (WHERE v.arv_treatment_days BETWEEN 1 AND 7)   AS b1,"
                        + "  count(*) FILTER (WHERE v.arv_treatment_days BETWEEN 8 AND 30)  AS b2,"
                        + "  count(*) FILTER (WHERE v.arv_treatment_days BETWEEN 31 AND 90) AS b3,"
                        + "  count(*) FILTER (WHERE v.arv_treatment_days > 90)              AS b4,"
                        + "  count(*) FILTER (WHERE v.arv_treatment_days IS NULL)           AS bn,"
                        + "  count(*)                                                       AS total"
                        + " FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                        + "   AND v.visit_date >= ?",
                rs -> {
                    if (!rs.next()) return new DurationBuckets(0, 0, 0, 0, 0, 0);
                    return new DurationBuckets(
                            rs.getLong("b1"), rs.getLong("b2"),
                            rs.getLong("b3"), rs.getLong("b4"),
                            rs.getLong("bn"), rs.getLong("total"));
                },
                buildArgs(regionId, since));

        // Monthly volume
        String monthlySql = "WITH months AS ("
                + " SELECT generate_series("
                + "   date_trunc('month', NOW()) - make_interval(months => ? - 1),"
                + "   date_trunc('month', NOW()),"
                + "   INTERVAL '1 month'"
                + " )::date AS month_start"
                + ") SELECT to_char(m.month_start, 'YYYY-MM') AS label,"
                + "   (SELECT count(*) FROM core.visits v" + region
                + "    WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                + "      AND v.visit_date >= m.month_start"
                + "      AND v.visit_date <  m.month_start + INTERVAL '1 month') AS n"
                + " FROM months m ORDER BY m.month_start";

        List<Object> mArgs = new ArrayList<>();
        mArgs.add(months);
        if (regionId != null) mArgs.add(regionId);

        List<MonthlyCount> monthly = jdbc.query(monthlySql,
                (rs, i) -> new MonthlyCount(rs.getString("label"), rs.getLong("n")),
                mArgs.toArray());

        return new PharmacySummary(
                dispensationsTotal == null ? 0L : dispensationsTotal,
                dispensationsInPeriod == null ? 0L : dispensationsInPeriod,
                patientsOnArv == null ? 0L : patientsOnArv,
                distinctRegimens == null ? 0L : distinctRegimens,
                shortPct,
                monthly,
                regimens,
                durations,
                months);
    }

    private static BigDecimal pct(Long n, Long d) {
        if (n == null || d == null || d == 0) return null;
        return new BigDecimal(n).multiply(new BigDecimal(100))
                .divide(new BigDecimal(d), 1, RoundingMode.HALF_UP);
    }

    public DispensationPage dispensations(int months, Long regionId, int page, int size) {
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
                "SELECT count(*) FROM core.visits v"
                        + " JOIN core.sites site ON site.id = v.site_id" + regionJoin
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                        + "   AND v.visit_date >= ?",
                Long.class, args.toArray());

        List<Object> pagedArgs = new ArrayList<>(args);
        pagedArgs.add(safeSize);
        pagedArgs.add(offset);

        List<DispensationRow> rows = jdbc.query(
                "SELECT v.id, v.visit_date, v.next_visit_date, v.arv_regimen,"
                        + "       v.arv_treatment_days, v.cotrim_treatment_days,"
                        + "       v.patient_id,"
                        + "       site.code AS site_code, site.name AS site_name,"
                        + "       (SELECT pi.identifier_value FROM core.patient_identifiers pi"
                        + "          JOIN core.identifier_types it ON it.id = pi.identifier_type_id"
                        + "         WHERE pi.patient_id = v.patient_id AND it.code = 'CODE_ARV'"
                        + "         ORDER BY pi.id LIMIT 1) AS patient_code"
                        + " FROM core.visits v"
                        + " JOIN core.sites site ON site.id = v.site_id" + regionJoin
                        + " WHERE v.voided = FALSE AND v.arv_regimen IS NOT NULL"
                        + "   AND v.visit_date >= ?"
                        + " ORDER BY v.visit_date DESC NULLS LAST, v.id DESC"
                        + " LIMIT ? OFFSET ?",
                (rs, i) -> {
                    short arvDays = rs.getShort("arv_treatment_days");
                    boolean arvDaysNull = rs.wasNull();
                    short ctxDays = rs.getShort("cotrim_treatment_days");
                    boolean ctxDaysNull = rs.wasNull();
                    return new DispensationRow(
                            rs.getLong("id"),
                            rs.getDate("visit_date") == null ? null : rs.getDate("visit_date").toLocalDate(),
                            rs.getDate("next_visit_date") == null ? null : rs.getDate("next_visit_date").toLocalDate(),
                            rs.getString("arv_regimen"),
                            arvDaysNull ? null : arvDays,
                            ctxDaysNull ? null : ctxDays,
                            rs.getLong("patient_id"),
                            rs.getString("patient_code"),
                            rs.getString("site_code"),
                            rs.getString("site_name"));
                },
                pagedArgs.toArray());

        return new DispensationPage(rows, total == null ? 0L : total, safePage, safeSize);
    }

    public record PharmacySummary(
            long dispensationsAllTime,
            long dispensationsInPeriod,
            long patientsOnArvInPeriod,
            long distinctRegimensInPeriod,
            BigDecimal shortDispensationPct,
            List<MonthlyCount> monthly,
            List<Bucket> regimens,
            DurationBuckets durations,
            int periodMonths
    ) {}

    public record MonthlyCount(String month, long count) {}
    public record Bucket(String label, long count, long patients) {}

    public record DurationBuckets(
            long d1_7,
            long d8_30,
            long d31_90,
            long d90p,
            long unknown,
            long total
    ) {}

    public record DispensationRow(
            long id,
            LocalDate visitDate,
            LocalDate nextVisitDate,
            String arvRegimen,
            Short arvDays,
            Short cotrimDays,
            long patientId,
            String patientCode,
            String siteCode,
            String siteName
    ) {}

    public record DispensationPage(
            List<DispensationRow> content,
            long total,
            int page,
            int size
    ) {}
}
