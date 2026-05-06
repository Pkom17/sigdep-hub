package ci.itechciv.sigdep.hub.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only KPI computations against core.* — straight SQL on the live tables.
 * Phase 3 will move these to materialised views in analytics.* with periodic
 * refresh; for now the queries are simple enough to run on demand and the
 * console caches results for a minute.
 *
 * The "national" denominators (e.g. ARV coverage) are not yet computable
 * since we don't have an authoritative count of expected PVVIH at the
 * country level — those KPIs return null for now.
 */
@Service
public class KpiService {

    /** UUID of the "VIH CHARGE VIRALE" lab concept (CIEL 856). */
    private static final String VIRAL_LOAD_TEST_UUID = "856AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    /** Threshold for "viral suppression" (TX_PVLS, copies/mL). */
    private static final BigDecimal VL_SUPPRESSED_THRESHOLD = new BigDecimal("1000");

    private final JdbcTemplate jdbc;

    public KpiService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Cacheable(cacheNames = "publicKpis")
    public PublicKpis publicKpis() {
        Long patientsActive = jdbc.queryForObject(
                "SELECT count(*) FROM core.patients WHERE voided = FALSE",
                Long.class);

        Long sitesWithData = jdbc.queryForObject(
                """
                SELECT count(*) FROM core.sites s
                WHERE s.runs_sigdep IS TRUE
                   OR EXISTS (SELECT 1 FROM core.patients p
                              WHERE p.site_id = s.id AND p.voided = FALSE)
                """,
                Long.class);

        BigDecimal viralSuppression = computeViralSuppression();
        List<MonthBucket> activeFile = computeActiveFile();

        // ARV coverage requires a national PVVIH denominator we don't have.
        BigDecimal arvCoverage = null;

        return new PublicKpis(
                patientsActive == null ? 0L : patientsActive,
                sitesWithData == null ? 0L : sitesWithData,
                viralSuppression,
                arvCoverage,
                activeFile);
    }

    private BigDecimal computeViralSuppression() {
        return jdbc.query(
                """
                SELECT
                  count(*) FILTER (WHERE value_numeric < ?) AS suppressed,
                  count(*)                                  AS total
                FROM core.lab_results
                WHERE test_uuid = ?
                  AND value_numeric IS NOT NULL
                  AND exam_date >= ?
                  AND voided = FALSE
                """,
                rs -> {
                    if (!rs.next()) return null;
                    long suppressed = rs.getLong("suppressed");
                    long total = rs.getLong("total");
                    return total == 0 ? null
                            : new BigDecimal(suppressed).multiply(new BigDecimal(100))
                                    .divide(new BigDecimal(total), 1, RoundingMode.HALF_UP);
                },
                VL_SUPPRESSED_THRESHOLD, VIRAL_LOAD_TEST_UUID, LocalDate.now().minusYears(1));
    }

    private List<MonthBucket> computeActiveFile() {
        return jdbc.query(
                """
                WITH months AS (
                  SELECT generate_series(
                           date_trunc('month', NOW()) - INTERVAL '11 months',
                           date_trunc('month', NOW()),
                           INTERVAL '1 month'
                         )::date AS month_start
                )
                SELECT to_char(m.month_start, 'YYYY-MM') AS label,
                       (SELECT count(DISTINCT patient_id)
                        FROM core.visits v
                        WHERE v.visit_date >= m.month_start
                          AND v.visit_date <  m.month_start + INTERVAL '1 month'
                          AND v.voided = FALSE) AS active
                FROM months m
                ORDER BY m.month_start
                """,
                (rs, i) -> new MonthBucket(rs.getString("label"), rs.getLong("active")));
    }

    public record PublicKpis(
            long patientsActive,
            long sitesWithData,
            BigDecimal viralSuppression,
            BigDecimal arvCoverage,
            List<MonthBucket> activeFile
    ) {}

    public record MonthBucket(String month, long count) {}

    // ---------------- Authenticated dashboard KPIs ----------------

    @Cacheable(cacheNames = "dashboardKpis")
    public DashboardKpis dashboardKpis() {
        // File active = patients with at least one visit in the last 12 months.
        Long fileActive = jdbc.queryForObject(
                """
                SELECT count(DISTINCT patient_id) FROM core.visits
                WHERE visit_date >= ? AND voided = FALSE
                """,
                Long.class,
                LocalDate.now().minusYears(1));

        // TX_NEW (this month) = patients with an enrolment / arv_init_date in the
        // current month. We use arv_init_date when present, otherwise enrollment_date.
        Long txNewMonth = jdbc.queryForObject(
                """
                SELECT count(*) FROM core.treatment_initiations
                WHERE COALESCE(arv_init_date, enrollment_date) >= date_trunc('month', NOW())
                  AND voided = FALSE
                """,
                Long.class);

        // Sites en ligne = synced in the last 24h, against total sites runs_sigdep.
        Long sitesOnline = jdbc.queryForObject(
                """
                SELECT count(*) FROM core.sites
                WHERE last_sync_at >= NOW() - INTERVAL '24 hours'
                """,
                Long.class);

        Long sitesTotalScope = jdbc.queryForObject(
                """
                SELECT count(*) FROM core.sites s
                WHERE s.runs_sigdep IS TRUE
                   OR EXISTS (SELECT 1 FROM core.patients p
                              WHERE p.site_id = s.id AND p.voided = FALSE)
                """,
                Long.class);

        BigDecimal viralSuppression = computeViralSuppression();

        // Sync alerts: 3 buckets.
        Long sitesNoSync7d = jdbc.queryForObject(
                """
                SELECT count(*) FROM core.sites
                WHERE (runs_sigdep IS TRUE OR EXISTS (
                         SELECT 1 FROM core.patients p WHERE p.site_id = sites.id))
                  AND (last_sync_at IS NULL OR last_sync_at < NOW() - INTERVAL '7 days')
                """,
                Long.class);

        Long sitesNoSync24h = jdbc.queryForObject(
                """
                SELECT count(*) FROM core.sites
                WHERE (runs_sigdep IS TRUE OR EXISTS (
                         SELECT 1 FROM core.patients p WHERE p.site_id = sites.id))
                  AND (last_sync_at IS NULL OR last_sync_at < NOW() - INTERVAL '24 hours')
                """,
                Long.class);

        // Last batch received = most recent last_sync_at across all sites.
        java.time.OffsetDateTime lastBatchAt = jdbc.query(
                "SELECT max(last_sync_at) FROM core.sites",
                rs -> {
                    if (!rs.next()) return null;
                    java.sql.Timestamp ts = rs.getTimestamp(1);
                    return ts == null ? null : ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
                });

        return new DashboardKpis(
                fileActive == null ? 0L : fileActive,
                txNewMonth == null ? 0L : txNewMonth,
                viralSuppression,
                sitesOnline == null ? 0L : sitesOnline,
                sitesTotalScope == null ? 0L : sitesTotalScope,
                computeActiveFile(),
                new SyncAlerts(
                        sitesNoSync7d == null ? 0L : sitesNoSync7d,
                        sitesNoSync24h == null ? 0L : sitesNoSync24h,
                        lastBatchAt));
    }

    public record DashboardKpis(
            long fileActive,
            long txNewMonth,
            BigDecimal viralSuppression,
            long sitesOnline,
            long sitesTotalScope,
            List<MonthBucket> activeFile,
            SyncAlerts syncAlerts
    ) {}

    public record SyncAlerts(
            long sitesNoSync7d,
            long sitesNoSync24h,
            java.time.OffsetDateTime lastBatchAt
    ) {}
}
