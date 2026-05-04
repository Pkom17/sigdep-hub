package ci.itechciv.sigdep.hub.domain.repository;

import ci.itechciv.sigdep.hub.domain.entity.Site;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {
    Optional<Site> findByCode(String code);
}
