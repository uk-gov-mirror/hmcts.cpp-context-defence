package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociationDefendant;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefenceAssociationDefendantRepository extends AbstractDefenceRepository<DefenceAssociationDefendant, UUID> {

    public DefenceAssociationDefendantRepository() {
        super(DefenceAssociationDefendant.class);
    }

    public DefenceAssociationDefendant findOptionalByDefendantId(final UUID defendantId) {
        return entityManager.createQuery("SELECT e FROM DefenceAssociationDefendant e LEFT JOIN FETCH e.defenceAssociations WHERE e.defendantId = :defendantId", DefenceAssociationDefendant.class)
                .setParameter("defendantId", defendantId)
                .getResultStream().findFirst().orElse(null);
    }
}
