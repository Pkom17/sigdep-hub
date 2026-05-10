package ci.itechciv.sigdep.hub.domain.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only queries against {@code audit.sync_batch} for the console
 * Synchronisation page.
 *
 * KPIs and charts are computed live from the table; we don't materialise
 * them, since the page is operational (low traffic, fresh-data sensitive).
 *
 * Geo filters reuse the same tightest-wins precedence as the rest of the
 * console: site &gt; district &gt; region.
 */
@Service
public class SyncQueryService {

    private final JdbcTemplate jdbc;

    public SyncQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- summary ------------------------------------------------------

    public SyncSummary summary(Long regionId, Long districtId, Long siteId) {
        String siteJoin = siteJoin(regionId, districtId, siteId);
        Long g = geoArg(regionId, districtId, siteId);
        Object[] noArgs = g == null ? new Object[0] : new Object[]{g};

        // Sites buckets — based on core.sites.last_sync_at, scoped by geo.
        long sitesTotal  = countSites(siteJoin, "TRUE",                                                  noArgs);
        long sitesOnline = countSites(siteJoin, "s.last_sync_at >= NOW() - INTERVAL '24 hours'",        noArgs);
        long sitesLate   = countSites(siteJoin,
                "s.last_sync_at <  NOW() - INTERVAL '24 hours'"
              + " AND s.last_sync_at >= NOW() - INTERVAL '7 days'",                                       noArgs);
        long sitesOffline = countSites(siteJoin,
                "(s.last_sync_at IS NULL OR s.last_sync_at < NOW() - INTERVAL '7 days')",                noArgs);

        // Last batch finished — for "système actif depuis…" hint.
        OffsetDateTime lastBatchAt = jdbc.query(
                "SELECT MAX(b.finished_at) FROM audit.sync_batch b" + auditSiteJoin(regionId, districtId, siteId),
                rs -> {
                    if (rs.next()) {
                        var ts = rs.getTimestamp(1);
                        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
                    }
                    return null;
                }, noArgs);

        // 24h totals.
        long received24h = sumAudit(regionId, districtId, siteId,
                "received_count", "b.finished_at >= NOW() - INTERVAL '24 hours'");
        long accepted24h = sumAudit(regionId, districtId, siteId,
                "accepted",       "b.finished_at >= NOW() - INTERVAL '24 hours'");
        long rejected24h = sumAudit(regionId, districtId, siteId,
                "rejected",       "b.finished_at >= NOW() - INTERVAL '24 hours'");
        long batches24h  = countAudit(regionId, districtId, siteId,
                "b.finished_at >= NOW() - INTERVAL '24 hours'");

        return new SyncSummary(
                sitesTotal, sitesOnline, sitesLate, sitesOffline,
                lastBatchAt,
                batches24h, received24h, accepted24h, rejected24h);
    }

    // ---------- daily volume -------------------------------------------------

    /**
     * One row per day for the last {@code days} days. Days with no batch are
     * present with zeros so the chart shows continuous bars.
     */
    public List<DailyVolume> daily(int days, Long regionId, Long districtId, Long siteId) {
        int safeDays = Math.max(1, Math.min(180, days));
        String join = auditSiteJoin(regionId, districtId, siteId);
        Long g = geoArg(regionId, districtId, siteId);

        // Use generate_series to materialise the day axis.
        String sql =
                "WITH days AS ("
                + "  SELECT generate_series("
                + "    (CURRENT_DATE - (? - 1) * INTERVAL '1 day')::date,"
                + "    CURRENT_DATE,"
                + "    INTERVAL '1 day'"
                + "  )::date AS d"
                + "), agg AS ("
                + "  SELECT date_trunc('day', b.finished_at)::date AS d,"
                + "         count(*)                AS batches,"
                + "         coalesce(sum(received_count), 0) AS received,"
                + "         coalesce(sum(accepted),       0) AS accepted,"
                + "         coalesce(sum(rejected),       0) AS rejected"
                + "    FROM audit.sync_batch b" + join
                + "   WHERE b.finished_at >= CURRENT_DATE - (? - 1) * INTERVAL '1 day'"
                + "   GROUP BY 1"
                + ") SELECT to_char(d.d, 'YYYY-MM-DD') AS day,"
                + "        coalesce(a.batches, 0)  AS batches,"
                + "        coalesce(a.received, 0) AS received,"
                + "        coalesce(a.accepted, 0) AS accepted,"
                + "        coalesce(a.rejected, 0) AS rejected"
                + "   FROM days d LEFT JOIN agg a ON a.d = d.d"
                + "  ORDER BY d.d";

        List<Object> args = new ArrayList<>();
        args.add(safeDays);
        if (g != null) args.add(g);
        args.add(safeDays);

        return jdbc.query(sql, (rs, i) -> new DailyVolume(
                rs.getString("day"),
                rs.getLong("batches"),
                rs.getLong("received"),
                rs.getLong("accepted"),
                rs.getLong("rejected")),
                args.toArray());
    }

    // ---------- recent batches listing --------------------------------------

    private static final Map<String, String> BATCH_SORTABLE = Map.of(
            "finishedAt",   "b.finished_at",
            "site",         "s.code",
            "entityType",   "b.entity_type",
            "received",     "b.received_count",
            "accepted",     "b.accepted",
            "rejected",     "b.rejected",
            "durationMs",   "b.duration_ms",
            "status",       "b.status"
    );

    public BatchPage batches(Long regionId, Long districtId, Long siteId,
                             String entityType, String status,
                             String sort, String dir,
                             int page, int size) {
        int safeSize = Math.max(1, Math.min(500, size));
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;

        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> args = new ArrayList<>();

        Long g = geoArg(regionId, districtId, siteId);
        if (g != null) {
            // The JOIN below already filters; nothing to add in WHERE.
        }
        String join = auditSiteJoin(regionId, districtId, siteId);
        if (g != null) args.add(g);

        if (entityType != null && !entityType.isBlank()) {
            where.append(" AND b.entity_type = ?");
            args.add(entityType);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND b.status = ?");
            args.add(status);
        }

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM audit.sync_batch b" + join + " " + where,
                Long.class, args.toArray());

        String sql = "SELECT b.id, b.batch_id, b.entity_type,"
                + "       b.received_count, b.accepted, b.rejected,"
                + "       b.started_at, b.finished_at, b.duration_ms,"
                + "       b.status, b.error_sample::text AS error_sample,"
                + "       b.site_code,"
                + "       s.id AS site_pk, s.code AS site_resolved_code, s.name AS site_name"
                + " FROM audit.sync_batch b"
                + auditSiteJoinForSelect(regionId, districtId, siteId)
                + " " + where
                + SortSpec.orderBy(sort, dir, BATCH_SORTABLE,
                        "b.finished_at DESC NULLS LAST, b.id DESC")
                + " LIMIT ? OFFSET ?";

        List<Object> pagedArgs = new ArrayList<>(args);
        pagedArgs.add(safeSize);
        pagedArgs.add(offset);

        List<BatchRow> rows = jdbc.query(sql, (rs, i) -> {
            var started = rs.getTimestamp("started_at");
            var finished = rs.getTimestamp("finished_at");
            int duration = rs.getInt("duration_ms");
            boolean durationNull = rs.wasNull();
            return new BatchRow(
                    rs.getLong("id"),
                    rs.getString("batch_id"),
                    rs.getString("entity_type"),
                    rs.getInt("received_count"),
                    rs.getInt("accepted"),
                    rs.getInt("rejected"),
                    started == null ? null : started.toInstant().atOffset(ZoneOffset.UTC),
                    finished == null ? null : finished.toInstant().atOffset(ZoneOffset.UTC),
                    durationNull ? null : duration,
                    rs.getString("status"),
                    rs.getString("error_sample"),
                    rs.getString("site_code"),
                    rs.getString("site_resolved_code"),
                    rs.getString("site_name"));
        }, pagedArgs.toArray());

        return new BatchPage(rows, total == null ? 0L : total, safePage, safeSize);
    }

    // ---------- late sites listing ------------------------------------------

    private static final Map<String, String> LATE_SORTABLE = Map.of(
            "code",       "s.code",
            "name",       "s.name",
            "region",     "r.name",
            "district",   "d.name",
            "lastSyncAt", "s.last_sync_at"
    );

    /**
     * @param bucket "late" (24h..7j), "offline" (>7j), "never" (NULL),
     *               anything else returns the union of all three.
     */
    public LateSitePage lateSites(String bucket,
                                  Long regionId, Long districtId, Long siteId,
                                  String sort, String dir,
                                  int page, int size) {
        int safeSize = Math.max(1, Math.min(500, size));
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;

        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (siteId != null) {
            where.append(" AND s.id = ?");           args.add(siteId);
        } else if (districtId != null) {
            where.append(" AND d.id = ?");           args.add(districtId);
        } else if (regionId != null) {
            where.append(" AND r.id = ?");           args.add(regionId);
        }

        switch (bucket == null ? "" : bucket) {
            case "late" -> where.append(
                    " AND s.last_sync_at <  NOW() - INTERVAL '24 hours'"
                  + " AND s.last_sync_at >= NOW() - INTERVAL '7 days'");
            case "offline" -> where.append(
                    " AND s.last_sync_at IS NOT NULL"
                  + " AND s.last_sync_at < NOW() - INTERVAL '7 days'");
            case "never" -> where.append(" AND s.last_sync_at IS NULL");
            default -> where.append(
                    " AND (s.last_sync_at IS NULL"
                  + "      OR s.last_sync_at < NOW() - INTERVAL '24 hours')");
        }

        String join = " FROM core.sites s"
                + " JOIN core.districts d ON d.id = s.district_id"
                + " JOIN core.regions   r ON r.id = d.region_id";

        Long total = jdbc.queryForObject(
                "SELECT count(*)" + join + " " + where,
                Long.class, args.toArray());

        String sql = "SELECT s.id, s.code, s.name, s.last_sync_at,"
                + "       d.name AS district_name,"
                + "       r.name AS region_name,"
                + "       (SELECT count(*) FROM core.patients p"
                + "          WHERE p.site_id = s.id AND p.voided = FALSE) AS patient_count"
                + join + " " + where
                + SortSpec.orderBy(sort, dir, LATE_SORTABLE,
                        "s.last_sync_at ASC NULLS FIRST, s.code ASC")
                + " LIMIT ? OFFSET ?";

        List<Object> pagedArgs = new ArrayList<>(args);
        pagedArgs.add(safeSize);
        pagedArgs.add(offset);

        List<LateSiteRow> rows = jdbc.query(sql, (rs, i) -> {
            var lastSync = rs.getTimestamp("last_sync_at");
            return new LateSiteRow(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("region_name"),
                    rs.getString("district_name"),
                    lastSync == null ? null : lastSync.toInstant().atOffset(ZoneOffset.UTC),
                    rs.getLong("patient_count"));
        }, pagedArgs.toArray());

        return new LateSitePage(rows, total == null ? 0L : total, safePage, safeSize);
    }

    // ---------- helpers -----------------------------------------------------

    /** Geo filter for queries with {@code core.sites s} aliased. */
    private static String siteJoin(Long regionId, Long districtId, Long siteId) {
        if (siteId != null)     return " WHERE s.id = ?";
        if (districtId != null) return " WHERE s.district_id = ?";
        if (regionId != null)   return " JOIN core.districts d ON d.id = s.district_id"
                                     + " WHERE d.region_id = ?";
        return "";
    }

    /** Geo filter for audit-only count/sum queries. */
    private static String auditSiteJoin(Long regionId, Long districtId, Long siteId) {
        if (siteId != null) {
            return " JOIN core.sites s ON s.id = b.site_id AND s.id = ?";
        }
        if (districtId != null) {
            return " JOIN core.sites s ON s.id = b.site_id AND s.district_id = ?";
        }
        if (regionId != null) {
            return " JOIN core.sites s ON s.id = b.site_id"
                 + " JOIN core.districts d ON d.id = s.district_id AND d.region_id = ?";
        }
        return "";
    }

    /**
     * Same as {@link #auditSiteJoin} but always joins core.sites (LEFT) so
     * the SELECT can surface the resolved code/name even when no geo filter
     * is active.
     */
    private static String auditSiteJoinForSelect(Long regionId, Long districtId, Long siteId) {
        if (siteId != null) {
            return " JOIN core.sites s ON s.id = b.site_id AND s.id = ?";
        }
        if (districtId != null) {
            return " JOIN core.sites s ON s.id = b.site_id AND s.district_id = ?";
        }
        if (regionId != null) {
            return " JOIN core.sites s ON s.id = b.site_id"
                 + " JOIN core.districts d ON d.id = s.district_id AND d.region_id = ?";
        }
        return " LEFT JOIN core.sites s ON s.id = b.site_id";
    }

    private static Long geoArg(Long regionId, Long districtId, Long siteId) {
        if (siteId != null)     return siteId;
        if (districtId != null) return districtId;
        return regionId;
    }

    private long countSites(String siteJoin, String predicate, Object[] args) {
        // siteJoin already includes WHERE when geo filter is set.
        String sql;
        if (siteJoin.startsWith(" WHERE")) {
            sql = "SELECT count(*) FROM core.sites s" + siteJoin + " AND " + predicate;
        } else if (siteJoin.contains("WHERE")) {
            // " JOIN ... WHERE …"
            sql = "SELECT count(*) FROM core.sites s" + siteJoin + " AND " + predicate;
        } else {
            sql = "SELECT count(*) FROM core.sites s WHERE " + predicate;
        }
        Long total = jdbc.queryForObject(sql, Long.class, args);
        return total == null ? 0L : total;
    }

    private long countAudit(Long regionId, Long districtId, Long siteId, String predicate) {
        String join = auditSiteJoin(regionId, districtId, siteId);
        Long g = geoArg(regionId, districtId, siteId);
        Object[] args = g == null ? new Object[0] : new Object[]{g};
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM audit.sync_batch b" + join + " WHERE " + predicate,
                Long.class, args);
        return total == null ? 0L : total;
    }

    private long sumAudit(Long regionId, Long districtId, Long siteId,
                          String column, String predicate) {
        String join = auditSiteJoin(regionId, districtId, siteId);
        Long g = geoArg(regionId, districtId, siteId);
        Object[] args = g == null ? new Object[0] : new Object[]{g};
        Long sum = jdbc.queryForObject(
                "SELECT coalesce(sum(" + column + "), 0) FROM audit.sync_batch b"
                        + join + " WHERE " + predicate,
                Long.class, args);
        return sum == null ? 0L : sum;
    }

    // ---------- records -----------------------------------------------------

    public record SyncSummary(
            long sitesTotal,
            long sitesOnline,
            long sitesLate,
            long sitesOffline,
            OffsetDateTime lastBatchAt,
            long batches24h,
            long received24h,
            long accepted24h,
            long rejected24h
    ) {}

    public record DailyVolume(
            String day,
            long batches,
            long received,
            long accepted,
            long rejected
    ) {}

    public record BatchRow(
            long id,
            String batchId,
            String entityType,
            int receivedCount,
            int accepted,
            int rejected,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            Integer durationMs,
            String status,
            String errorSample,   // JSON string, decoded on the frontend
            String siteCode,      // raw code from the request
            String siteResolvedCode,
            String siteName
    ) {}

    public record BatchPage(List<BatchRow> content, long total, int page, int size) {}

    public record LateSiteRow(
            long id,
            String code,
            String name,
            String regionName,
            String districtName,
            OffsetDateTime lastSyncAt,
            long patientCount
    ) {}

    public record LateSitePage(List<LateSiteRow> content, long total, int page, int size) {}
}
