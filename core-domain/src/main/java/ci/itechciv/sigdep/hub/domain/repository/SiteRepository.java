package ci.itechciv.sigdep.hub.domain.repository;

import ci.itechciv.sigdep.hub.domain.entity.Site;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SiteRepository extends JpaRepository<Site, Long> {
    Optional<Site> findByCode(String code);
    Optional<Site> findBySourceUuid(String sourceUuid);
    List<Site> findByDistrictId(Long districtId);

    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.lastSyncAt = CURRENT_TIMESTAMP WHERE s.id = :siteId")
    int touchLastSyncAt(Long siteId);
}
