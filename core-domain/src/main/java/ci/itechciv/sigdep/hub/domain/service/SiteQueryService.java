package ci.itechciv.sigdep.hub.domain.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SiteQueryService {

    private final JdbcTemplate jdbc;

    public SiteQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param status one of "all" / "online" (<24h) / "late" (24h..7j) /
     *               "offline" (>7j or null). null/empty == all.
     */
    public SitePage list(String search, String status, Long regionId, int page, int size) {
        int safeSize = Math.max(1, Math.min(200, size));
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;

        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            where.append(" AND (s.code ILIKE ? OR s.name ILIKE ?)");
            String like = "%" + search.trim() + "%";
            args.add(like);
            args.add(like);
        }

        if (regionId != null) {
            where.append(" AND r.id = ?");
            args.add(regionId);
        }

        if (status != null && !status.isBlank() && !"all".equals(status)) {
            switch (status) {
                case "online" -> where.append(" AND s.last_sync_at >= NOW() - INTERVAL '24 hours'");
                case "late" -> where.append(" AND s.last_sync_at < NOW() - INTERVAL '24 hours' AND s.last_sync_at >= NOW() - INTERVAL '7 days'");
                case "offline" -> where.append(" AND (s.last_sync_at IS NULL OR s.last_sync_at < NOW() - INTERVAL '7 days')");
                default -> { /* ignore unknown */ }
            }
        }

        String join = """
                FROM core.sites s
                JOIN core.districts d ON d.id = s.district_id
                JOIN core.regions   r ON r.id = d.region_id
                """;

        Long total = jdbc.queryForObject(
                "SELECT count(*) " + join + where, Long.class, args.toArray());

        String sql = """
                SELECT s.id, s.code, s.name, s.facility_type, s.runs_sigdep,
                       s.last_sync_at,
                       d.id   AS district_id, d.name AS district_name,
                       r.id   AS region_id,   r.name AS region_name,
                       (SELECT count(*) FROM core.patients p
                          WHERE p.site_id = s.id AND p.voided = FALSE) AS patient_count
                """ + join + where + " ORDER BY s.code ASC LIMIT ? OFFSET ?";

        List<Object> pagedArgs = new ArrayList<>(args);
        pagedArgs.add(safeSize);
        pagedArgs.add(offset);

        List<SiteRow> rows = jdbc.query(sql, (rs, i) -> {
            java.sql.Timestamp ts = rs.getTimestamp("last_sync_at");
            return new SiteRow(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("facility_type"),
                    (Boolean) rs.getObject("runs_sigdep"),
                    ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC),
                    rs.getLong("district_id"),
                    rs.getString("district_name"),
                    rs.getLong("region_id"),
                    rs.getString("region_name"),
                    rs.getLong("patient_count"));
        }, pagedArgs.toArray());

        return new SitePage(rows, total == null ? 0L : total, safePage, safeSize);
    }

    public List<RegionRef> regions() {
        return jdbc.query(
                "SELECT id, name FROM core.regions ORDER BY name",
                (rs, i) -> new RegionRef(rs.getLong("id"), rs.getString("name")));
    }

    public record SiteRow(
            long id,
            String code,
            String name,
            String facilityType,
            Boolean runsSigdep,
            OffsetDateTime lastSyncAt,
            long districtId,
            String districtName,
            long regionId,
            String regionName,
            long patientCount
    ) {}

    public record SitePage(
            List<SiteRow> content,
            long total,
            int page,
            int size
    ) {}

    public record RegionRef(long id, String name) {}
}
