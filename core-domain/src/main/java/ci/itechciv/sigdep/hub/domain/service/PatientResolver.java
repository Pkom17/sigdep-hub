package ci.itechciv.sigdep.hub.domain.service;

import ci.itechciv.sigdep.hub.domain.repository.PatientRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves a (siteId, patientSourceUuid) pair to the central patient_id.
 * Returns null if the patient hasn't been ingested yet — child entities
 * (visits, dispensations…) referencing such a patient will be rejected
 * with a clear error code rather than silently losing the link.
 */
@Service
public class PatientResolver {

    private final PatientRepository patients;

    public PatientResolver(PatientRepository patients) {
        this.patients = patients;
    }

    public Long resolveId(long siteId, UUID patientSourceUuid) {
        if (patientSourceUuid == null) return null;
        return patients.findBySiteIdAndSourceUuid(siteId, patientSourceUuid)
                .map(p -> p.getId())
                .orElse(null);
    }
}
