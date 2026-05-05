package ci.itechciv.sigdep.hub.domain.repository;

import ci.itechciv.sigdep.hub.domain.entity.IdentifierType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentifierTypeRepository extends JpaRepository<IdentifierType, Integer> {
    Optional<IdentifierType> findByCode(String code);
}
