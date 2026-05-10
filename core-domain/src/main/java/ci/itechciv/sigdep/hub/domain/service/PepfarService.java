package ci.itechciv.sigdep.hub.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * PEPFAR indicator computations against core.*.
 *
 * Quarter convention: USAID fiscal year (Q1 = Oct-Dec, Q2 = Jan-Mar,
 * Q3 = Apr-Jun, Q4 = Jul-Sep). The fiscal year is the calendar year of
 * the September that ends it (FY2025 = 2024-10-01 to 2025-09-30).
 *
 * Age is computed at the *end* of the quarter, per PEPFAR MER convention.
 * Age bands: <15, 15-24, 25-49, 50+.
 *
 * Indicators implemented in this v1:
 *  - TX_NEW: new ARV initiations during the quarter.
 *  - TX_CURR: patients on treatment at end of quarter — has an initiation
 *    on or before Q, no closure on or before Q, and the last visit's
 *    arv_treatment_days dispensation either still covers Q or expired
 *    no more than 28 days before Q. If no visit with a dispensation is
 *    found, we fall back to "has init + no closure" (permissive proxy).
 *  - TX_PVLS denominator: patients on ARV ≥ 6 months at Q with an
 *    eligible viral load test in the past 12 months.
 *  - TX_PVLS numerator: subset of the denominator with VL < 1000
 *    copies/mL on the most recent test.
 *
 * Out of scope here: TX_RTT, TX_ML (require an interruption-in-treatment
 * model we don't have yet).
 */
@Service
public class PepfarService {

    private static final String VL_UUID = "856AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final BigDecimal SUPPRESSED = new BigDecimal("1000");
    private static final int CURR_GRACE_DAYS = 28;
    private static final int PVLS_ON_ARV_MONTHS = 6;

    private final JdbcTemplate jdbc;

    public PepfarService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Maps "FY2025-Q1" to the [start, end] dates of that quarter. */
    public static QuarterRange quarter(int fiscalYear, int q) {
        if (q < 1 || q > 4) throw new IllegalArgumentException("quarter must be 1..4");
        // Q1: Oct-Dec of FY-1 ; Q2: Jan-Mar FY ; Q3: Apr-Jun FY ; Q4: Jul-Sep FY
        LocalDate start = switch (q) {
            case 1 -> LocalDate.of(fiscalYear - 1, 10, 1);
            case 2 -> LocalDate.of(fiscalYear, 1, 1);
            case 3 -> LocalDate.of(fiscalYear, 4, 1);
            case 4 -> LocalDate.of(fiscalYear, 7, 1);
            default -> throw new IllegalStateException();
        };
        LocalDate end = start.plusMonths(3).minusDays(1);
        return new QuarterRange(fiscalYear, q, start, end);
    }

    public PepfarReport report(int fiscalYear, int q, Long regionId, Long districtId, Long siteId) {
        QuarterRange qr = quarter(fiscalYear, q);
        return new PepfarReport(qr,
                txNew(qr, regionId, districtId, siteId),
                txCurr(qr, regionId, districtId, siteId),
                txPvls(qr, regionId, districtId, siteId));
    }

    // ---------- TX_NEW --------------------------------------------------------

    private Disaggregated txNew(QuarterRange qr, Long regionId, Long districtId, Long siteId) {
        // age at end of quarter — DATE_PART would be one option, but
        // birth dates that are just years (born "1971-01-01" placeholder)
        // give consistent results with year-of-month-of-day arithmetic.
        String ageExpr = ageExpr(qr.end());
        String region = regionJoin(regionId, districtId, siteId);

        String sql = "SELECT p.sex AS sex, " + ageBandExpr(ageExpr) + " AS band, count(*) AS n"
                + " FROM core.treatment_initiations ti"
                + " JOIN core.patients p ON p.id = ti.patient_id" + region
                + " WHERE ti.voided = FALSE"
                + "   AND ti.arv_init_date BETWEEN ? AND ?"
                + " GROUP BY p.sex, " + ageBandExpr(ageExpr);

        return aggregate(sql, buildArgs(regionId, districtId, siteId, qr.start(), qr.end()));
    }

    // ---------- TX_CURR -------------------------------------------------------

    private Disaggregated txCurr(QuarterRange qr, Long regionId, Long districtId, Long siteId) {
        // "Active" subquery: patients with init <= Q.end, no closure <= Q.end,
        // and last visit before Q.end with arv_treatment_days has remaining
        // coverage (or expired ≤ 28 days). Patients with init but no visits
        // at all are kept (permissive fallback).
        String activeSql =
                "WITH active_patients AS ("
                + "  SELECT p.id, p.sex, p.birth_date, p.site_id"
                + "  FROM core.patients p"
                + "  JOIN core.treatment_initiations ti ON ti.patient_id = p.id"
                + "    AND ti.voided = FALSE"
                + "    AND COALESCE(ti.arv_init_date, ti.enrollment_date) <= ?"
                + "  WHERE p.voided = FALSE"
                + "    AND NOT EXISTS ("
                + "      SELECT 1 FROM core.closures c"
                + "      WHERE c.patient_id = p.id AND c.voided = FALSE"
                + "        AND c.closure_date <= ?)"
                + "    AND ("
                + "      NOT EXISTS ("
                + "        SELECT 1 FROM core.visits v"
                + "        WHERE v.patient_id = p.id AND v.voided = FALSE"
                + "          AND v.visit_date <= ? AND v.arv_treatment_days IS NOT NULL)"
                + "      OR EXISTS ("
                + "        SELECT 1 FROM ("
                + "          SELECT v.visit_date, v.arv_treatment_days,"
                + "                 row_number() OVER (PARTITION BY v.patient_id"
                + "                                    ORDER BY v.visit_date DESC) AS rn"
                + "          FROM core.visits v"
                + "          WHERE v.patient_id = p.id AND v.voided = FALSE"
                + "            AND v.visit_date <= ? AND v.arv_treatment_days IS NOT NULL"
                + "        ) lv"
                + "        WHERE lv.rn = 1"
                + "          AND lv.visit_date + lv.arv_treatment_days + ? >= ?))"
                + "  GROUP BY p.id, p.sex, p.birth_date, p.site_id"
                + ")";

        // join to sites/districts only if a geo filter applied
        String regionFromActive = regionJoinAp(regionId, districtId, siteId);

        String ageExprAp = "DATE_PART('year', AGE(DATE '" + qr.end() + "', ap.birth_date))";

        String sql = activeSql
                + " SELECT ap.sex AS sex, " + ageBandExpr(ageExprAp) + " AS band, count(*) AS n"
                + " FROM active_patients ap" + regionFromActive
                + " GROUP BY ap.sex, " + ageBandExpr(ageExprAp);

        // Args order: <= ?, <= ?, <= ?, <= ?, grace, end, [geoId]
        List<Object> args = new ArrayList<>();
        args.add(qr.end());                      // init <= end
        args.add(qr.end());                      // closure <= end
        args.add(qr.end());                      // visit <= end (NOT EXISTS)
        args.add(qr.end());                      // visit <= end (latest)
        args.add(CURR_GRACE_DAYS);               // grace
        args.add(qr.end());                      // last visit + days + grace >= end
        Long g = geoArg(regionId, districtId, siteId);
        if (g != null) args.add(g);

        return aggregate(sql, args.toArray());
    }

    // ---------- TX_PVLS -------------------------------------------------------

    private TxPvls txPvls(QuarterRange qr, Long regionId, Long districtId, Long siteId) {
        // Eligible: on ARV ≥ 6 months at Q.end (init <= Q.end - 6 months),
        // no closure on or before Q.end, has at least one VL test with
        // numeric value in [Q.end - 12 months, Q.end].
        LocalDate sinceArv = qr.end().minusMonths(PVLS_ON_ARV_MONTHS);
        LocalDate sinceVl  = qr.end().minusMonths(12);

        // Pull every (patient, sex, age_band, latest_vl_value) triplet that
        // qualifies as denominator; the numerator is the subset with VL < 1000.
        String sql =
                "WITH eligible AS ("
                + "  SELECT p.id, p.sex, p.birth_date, p.site_id"
                + "  FROM core.patients p"
                + "  JOIN core.treatment_initiations ti ON ti.patient_id = p.id"
                + "    AND ti.voided = FALSE"
                + "    AND COALESCE(ti.arv_init_date, ti.enrollment_date) <= ?"
                + "  WHERE p.voided = FALSE"
                + "    AND NOT EXISTS ("
                + "      SELECT 1 FROM core.closures c"
                + "      WHERE c.patient_id = p.id AND c.voided = FALSE"
                + "        AND c.closure_date <= ?)"
                + "  GROUP BY p.id, p.sex, p.birth_date, p.site_id"
                + "), latest_vl AS ("
                + "  SELECT DISTINCT ON (lr.patient_id)"
                + "         lr.patient_id, lr.value_numeric, lr.exam_date"
                + "  FROM core.lab_results lr"
                + "  WHERE lr.voided = FALSE AND lr.test_uuid = ?"
                + "    AND lr.value_numeric IS NOT NULL"
                + "    AND lr.exam_date BETWEEN ? AND ?"
                + "  ORDER BY lr.patient_id, lr.exam_date DESC"
                + ")"
                + " SELECT e.sex AS sex, " + ageBandExpr("DATE_PART('year', AGE(DATE '" + qr.end() + "', e.birth_date))") + " AS band,"
                + "        count(*) AS denom,"
                + "        count(*) FILTER (WHERE lv.value_numeric < ?) AS numer"
                + " FROM eligible e"
                + " JOIN latest_vl lv ON lv.patient_id = e.id"
                + regionJoinE(regionId, districtId, siteId)
                + " GROUP BY e.sex, " + ageBandExpr("DATE_PART('year', AGE(DATE '" + qr.end() + "', e.birth_date))");

        List<Object> args = new ArrayList<>();
        args.add(sinceArv);     // init eligibility
        args.add(qr.end());     // no closure ≤ end
        args.add(VL_UUID);      // latest VL: test
        args.add(sinceVl);      // VL window start
        args.add(qr.end());     // VL window end
        args.add(SUPPRESSED);   // numerator threshold
        Long g = geoArg(regionId, districtId, siteId);
        if (g != null) args.add(g);

        List<DisaggCell> denomCells = new ArrayList<>();
        List<DisaggCell> numerCells = new ArrayList<>();

        jdbc.query(sql, rs -> {
            String sex = rs.getString("sex");
            String band = rs.getString("band");
            long d = rs.getLong("denom");
            long n = rs.getLong("numer");
            denomCells.add(new DisaggCell(sex, band, d));
            numerCells.add(new DisaggCell(sex, band, n));
        }, args.toArray());

        Disaggregated denom = new Disaggregated(sumCells(denomCells), denomCells);
        Disaggregated numer = new Disaggregated(sumCells(numerCells), numerCells);

        BigDecimal pct = denom.total() == 0 ? null
                : new BigDecimal(numer.total()).multiply(new BigDecimal(100))
                        .divide(new BigDecimal(denom.total()), 1, RoundingMode.HALF_UP);

        return new TxPvls(denom, numer, pct);
    }

    // ---------- helpers -------------------------------------------------------

    private Disaggregated aggregate(String sql, Object[] args) {
        List<DisaggCell> cells = new ArrayList<>();
        jdbc.query(sql, rs -> {
            cells.add(new DisaggCell(
                    rs.getString("sex"),
                    rs.getString("band"),
                    rs.getLong("n")));
        }, args);
        return new Disaggregated(sumCells(cells), cells);
    }

    private static long sumCells(List<DisaggCell> cells) {
        long s = 0;
        for (DisaggCell c : cells) s += c.count();
        return s;
    }

    /**
     * Geo filter for queries with a {@code core.patients p} table in scope.
     * Joins core.sites/districts off {@code p.site_id} unless the filter is
     * site-level (no extra join needed beyond a comparison).
     */
    private static String regionJoin(Long regionId, Long districtId, Long siteId) {
        return geoJoinFor("p", regionId, districtId, siteId);
    }

    /** Same but for the {@code active_patients ap} CTE alias used by TX_CURR. */
    private static String regionJoinAp(Long regionId, Long districtId, Long siteId) {
        return geoJoinFor("ap", regionId, districtId, siteId);
    }

    /** Same but for the {@code eligible e} CTE alias used by TX_PVLS. */
    private static String regionJoinE(Long regionId, Long districtId, Long siteId) {
        return geoJoinFor("e", regionId, districtId, siteId);
    }

    private static String geoJoinFor(String alias, Long regionId, Long districtId, Long siteId) {
        if (siteId != null) {
            return " JOIN core.sites s ON s.id = " + alias + ".site_id AND s.id = ?";
        }
        if (districtId != null) {
            return " JOIN core.sites s ON s.id = " + alias + ".site_id AND s.district_id = ?";
        }
        if (regionId != null) {
            return " JOIN core.sites s ON s.id = " + alias + ".site_id"
                 + " JOIN core.districts d ON d.id = s.district_id AND d.region_id = ?";
        }
        return "";
    }

    private static Long geoArg(Long regionId, Long districtId, Long siteId) {
        if (siteId != null)     return siteId;
        if (districtId != null) return districtId;
        return regionId;
    }

    /** Age in completed years at the given as-of date. */
    private static String ageExpr(LocalDate asOf) {
        return "DATE_PART('year', AGE(DATE '" + asOf + "', p.birth_date))";
    }

    private static String ageBandExpr(String yearsExpr) {
        return "CASE"
                + "  WHEN " + yearsExpr + " IS NULL THEN 'unknown'"
                + "  WHEN " + yearsExpr + " < 15 THEN '<15'"
                + "  WHEN " + yearsExpr + " < 25 THEN '15-24'"
                + "  WHEN " + yearsExpr + " < 50 THEN '25-49'"
                + "  ELSE '50+'"
                + " END";
    }

    /**
     * The geo JOIN sits in the FROM clause *before* the WHERE — so its
     * parameter must come before the WHERE-clause args.
     */
    private static Object[] buildArgs(Long regionId, Long districtId, Long siteId, Object... rest) {
        Long g = geoArg(regionId, districtId, siteId);
        if (g == null) return rest;
        Object[] out = new Object[rest.length + 1];
        out[0] = g;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    // ---------- records -------------------------------------------------------

    public record QuarterRange(int fiscalYear, int quarter, LocalDate start, LocalDate end) {}

    public record DisaggCell(String sex, String ageBand, long count) {}

    public record Disaggregated(long total, List<DisaggCell> cells) {}

    public record TxPvls(Disaggregated denominator, Disaggregated numerator, BigDecimal pct) {}

    public record PepfarReport(
            QuarterRange period,
            Disaggregated txNew,
            Disaggregated txCurr,
            TxPvls txPvls
    ) {}
}
