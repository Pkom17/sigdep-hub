package ci.itechciv.sigdep.hub.ingestion.writer;

import ci.itechciv.sigdep.contracts.dto.VisitDto;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class VisitWriter {

    private final JdbcTemplate jdbc;

    public VisitWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void upsertBatch(long siteId, List<VisitDto> batch) {
        // TODO: implement batch upsert with ON CONFLICT (site_id, source_uuid, visit_date)
        // jdbc.batchUpdate(...) — see spec.md §5.5
    }
}
