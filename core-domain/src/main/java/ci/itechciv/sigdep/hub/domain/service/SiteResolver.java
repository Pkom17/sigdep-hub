package ci.itechciv.sigdep.hub.domain.service;

import ci.itechciv.sigdep.hub.domain.entity.Site;
import ci.itechciv.sigdep.hub.domain.repository.SiteRepository;
import org.springframework.stereotype.Service;

@Service
public class SiteResolver {

    private final SiteRepository sites;

    public SiteResolver(SiteRepository sites) {
        this.sites = sites;
    }

    /**
     * Resolve a site by its DHIS2 code first, falling back to the OpenMRS
     * source UUID. Throws if neither resolves — sync requests must reference
     * a known site.
     */
    public Site resolve(String siteCode, String sourceUuid) {
        if (siteCode != null && !siteCode.isBlank()) {
            var bySiteCode = sites.findByCode(siteCode);
            if (bySiteCode.isPresent()) {
                return bySiteCode.get();
            }
        }
        if (sourceUuid != null && !sourceUuid.isBlank()) {
            var byUuid = sites.findBySourceUuid(sourceUuid);
            if (byUuid.isPresent()) {
                return byUuid.get();
            }
        }
        throw new UnknownSiteException(siteCode, sourceUuid);
    }

    public static class UnknownSiteException extends RuntimeException {
        public UnknownSiteException(String code, String uuid) {
            super("Unknown site: code=" + code + ", source_uuid=" + uuid);
        }
    }
}
