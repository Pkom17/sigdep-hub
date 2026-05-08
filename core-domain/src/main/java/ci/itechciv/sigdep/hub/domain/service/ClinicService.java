package ci.itechciv.sigdep.hub.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only clinical follow-up analytics. Reads core.visits, joining
 * sites/districts for the region filter.
 *
 * Two obs the source captures with non-canonical CIEL codes are still in
 * extra_data and read on-the-fly here:
 *  - 164487 → WHO stage (text, e.g. "WHO STAGE 1 ADULT")
 *  - 160108 → TB screening qualitative result
 */
@Service
public class ClinicService {

    private static final String WHO_STAGE_KEY = "164487AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String TB_SCREEN_KEY = "160108AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final JdbcTemplate jdbc;

    public ClinicService(JdbcTemplate jdbc) {
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

    public ClinicSummary summary(int months, Long regionId) {
        LocalDate since = LocalDate.now().minusMonths(months);
        String region = regionJoin(regionId);

        Long visitsTotal = jdbc.queryForObject(
                "SELECT count(*) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE",
                Long.class, buildArgs(regionId));

        Long visitsInPeriod = jdbc.queryForObject(
                "SELECT count(*) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.visit_date >= ?",
                Long.class, buildArgs(regionId, since));

        Long withTbScreen = jdbc.queryForObject(
                "SELECT count(*) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.visit_date >= ?"
                        + "   AND v.extra_data ?? ?",
                Long.class, buildArgs(regionId, since, TB_SCREEN_KEY));

        Long withWhoStage = jdbc.queryForObject(
                "SELECT count(*) FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.visit_date >= ?"
                        + "   AND v.extra_data ?? ?",
                Long.class, buildArgs(regionId, since, WHO_STAGE_KEY));

        BigDecimal tbScreenPct = pct(withTbScreen, visitsInPeriod);
        BigDecimal whoStagePct = pct(withWhoStage, visitsInPeriod);

        // Distributions
        List<Bucket> whoStageDist = jdbc.query(
                "SELECT v.extra_data->>? AS k, count(*) AS n"
                        + " FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.visit_date >= ?"
                        + "   AND v.extra_data ?? ?"
                        + " GROUP BY k ORDER BY n DESC",
                (rs, i) -> new Bucket(rs.getString("k"), rs.getLong("n")),
                buildArgs(regionId, WHO_STAGE_KEY, since, WHO_STAGE_KEY));

        List<Bucket> tbScreenDist = jdbc.query(
                "SELECT v.extra_data->>? AS k, count(*) AS n"
                        + " FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.visit_date >= ?"
                        + "   AND v.extra_data ?? ?"
                        + " GROUP BY k ORDER BY n DESC",
                (rs, i) -> new Bucket(rs.getString("k"), rs.getLong("n")),
                buildArgs(regionId, TB_SCREEN_KEY, since, TB_SCREEN_KEY));

        List<Bucket> arvDist = jdbc.query(
                "SELECT v.arv_regimen AS k, count(*) AS n FROM core.visits v" + region
                        + " WHERE v.voided = FALSE AND v.visit_date >= ?"
                        + "   AND v.arv_regimen IS NOT NULL"
                        + " GROUP BY k ORDER BY n DESC LIMIT 10",
                (rs, i) -> new Bucket(rs.getString("k"), rs.getLong("n")),
                buildArgs(regionId, since));

        // Monthly visit count
        String monthlySql = "WITH months AS ("
                + " SELECT generate_series("
                + "   date_trunc('month', NOW()) - make_interval(months => ? - 1),"
                + "   date_trunc('month', NOW()),"
                + "   INTERVAL '1 month'"
                + " )::date AS month_start"
                + ") SELECT to_char(m.month_start, 'YYYY-MM') AS label,"
                + "   (SELECT count(*) FROM core.visits v" + region
                + "    WHERE v.voided = FALSE"
                + "      AND v.visit_date >= m.month_start"
                + "      AND v.visit_date <  m.month_start + INTERVAL '1 month') AS n"
                + " FROM months m ORDER BY m.month_start";

        List<Object> mArgs = new ArrayList<>();
        mArgs.add(months);
        if (regionId != null) mArgs.add(regionId);

        List<MonthlyCount> monthly = jdbc.query(monthlySql,
                (rs, i) -> new MonthlyCount(rs.getString("label"), rs.getLong("n")),
                mArgs.toArray());

        return new ClinicSummary(
                visitsTotal == null ? 0L : visitsTotal,
                visitsInPeriod == null ? 0L : visitsInPeriod,
                withTbScreen == null ? 0L : withTbScreen,
                withWhoStage == null ? 0L : withWhoStage,
                tbScreenPct,
                whoStagePct,
                monthly,
                whoStageDist,
                tbScreenDist,
                arvDist,
                months);
    }

    private static BigDecimal pct(Long n, Long d) {
        if (n == null || d == null || d == 0) return null;
        return new BigDecimal(n).multiply(new BigDecimal(100))
                .divide(new BigDecimal(d), 1, RoundingMode.HALF_UP);
    }

    public VisitPage visits(int months, Long regionId, int page, int size) {
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
                        + " WHERE v.voided = FALSE AND v.visit_date >= ?",
                Long.class, args.toArray());

        List<Object> pagedArgs = new ArrayList<>(args);
        pagedArgs.add(safeSize);
        pagedArgs.add(offset);

        List<VisitRow> rows = jdbc.query(
                "SELECT v.id, v.visit_date, v.next_visit_date, v.source_form,"
                        + "       v.arv_regimen, v.arv_treatment_days, v.cotrim_treatment_days,"
                        + "       v.weight_kg, v.height_cm, v.bmi,"
                        + "       v.viral_load, v.cd4_count,"
                        + "       v.tpt_status, v.tpt_regimen,"
                        + "       v.extra_data->>'" + WHO_STAGE_KEY + "' AS who_stage,"
                        + "       v.extra_data->>'" + TB_SCREEN_KEY + "' AS tb_screen,"
                        + "       v.patient_id,"
                        + "       site.code AS site_code, site.name AS site_name,"
                        + "       (SELECT pi.identifier_value FROM core.patient_identifiers pi"
                        + "         WHERE pi.patient_id = v.patient_id"
                        + "         ORDER BY pi.id LIMIT 1) AS patient_code"
                        + " FROM core.visits v"
                        + " JOIN core.sites site ON site.id = v.site_id" + regionJoin
                        + " WHERE v.voided = FALSE AND v.visit_date >= ?"
                        + " ORDER BY v.visit_date DESC NULLS LAST, v.id DESC"
                        + " LIMIT ? OFFSET ?",
                (rs, i) -> {
                    short arvDays = rs.getShort("arv_treatment_days");
                    boolean arvDaysNull = rs.wasNull();
                    short ctxDays = rs.getShort("cotrim_treatment_days");
                    boolean ctxDaysNull = rs.wasNull();
                    int cd4 = rs.getInt("cd4_count");
                    boolean cd4Null = rs.wasNull();
                    return new VisitRow(
                            rs.getLong("id"),
                            rs.getDate("visit_date") == null ? null : rs.getDate("visit_date").toLocalDate(),
                            rs.getDate("next_visit_date") == null ? null : rs.getDate("next_visit_date").toLocalDate(),
                            rs.getString("source_form"),
                            rs.getString("arv_regimen"),
                            arvDaysNull ? null : arvDays,
                            ctxDaysNull ? null : ctxDays,
                            rs.getBigDecimal("weight_kg"),
                            rs.getBigDecimal("height_cm"),
                            rs.getBigDecimal("bmi"),
                            rs.getBigDecimal("viral_load"),
                            cd4Null ? null : cd4,
                            rs.getString("tpt_status"),
                            rs.getString("tpt_regimen"),
                            rs.getString("who_stage"),
                            rs.getString("tb_screen"),
                            rs.getLong("patient_id"),
                            rs.getString("patient_code"),
                            rs.getString("site_code"),
                            rs.getString("site_name"));
                },
                pagedArgs.toArray());

        return new VisitPage(rows, total == null ? 0L : total, safePage, safeSize);
    }

    public record ClinicSummary(
            long visitsAllTime,
            long visitsInPeriod,
            long withTbScreening,
            long withWhoStage,
            BigDecimal tbScreeningPct,
            BigDecimal whoStagePct,
            List<MonthlyCount> monthly,
            List<Bucket> whoStageDistribution,
            List<Bucket> tbScreeningDistribution,
            List<Bucket> arvRegimenDistribution,
            int periodMonths
    ) {}

    public record MonthlyCount(String month, long count) {}
    public record Bucket(String label, long count) {}

    public record VisitRow(
            long id,
            LocalDate visitDate,
            LocalDate nextVisitDate,
            String sourceForm,
            String arvRegimen,
            Short arvTreatmentDays,
            Short cotrimTreatmentDays,
            BigDecimal weightKg,
            BigDecimal heightCm,
            BigDecimal bmi,
            BigDecimal viralLoad,
            Integer cd4Count,
            String tptStatus,
            String tptRegimen,
            String whoStage,
            String tbScreening,
            long patientId,
            String patientCode,
            String siteCode,
            String siteName
    ) {}

    public record VisitPage(
            List<VisitRow> content,
            long total,
            int page,
            int size
    ) {}
}
