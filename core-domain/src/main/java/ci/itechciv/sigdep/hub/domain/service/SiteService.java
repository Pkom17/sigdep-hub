package ci.itechciv.sigdep.hub.domain.service;

import ci.itechciv.sigdep.hub.domain.entity.Site;
import ci.itechciv.sigdep.hub.domain.repository.SiteRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SiteService {

    private final SiteRepository sites;

    public SiteService(SiteRepository sites) {
        this.sites = sites;
    }

    public Optional<Site> findByCode(String code) {
        return sites.findByCode(code);
    }

    public Optional<Site> findBySourceUuid(String sourceUuid) {
        return sites.findBySourceUuid(sourceUuid);
    }
}
