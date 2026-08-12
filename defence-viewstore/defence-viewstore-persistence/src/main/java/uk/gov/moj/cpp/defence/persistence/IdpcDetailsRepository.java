package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.IdpcDetails;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IdpcDetailsRepository extends AbstractDefenceRepository<IdpcDetails, UUID> {

    public IdpcDetailsRepository() {
        super(IdpcDetails.class);
    }

    public IdpcDetails findIdpcDetailsForDefenceClient(final UUID defenceClientId) {
        return entityManager.createQuery(
                        "SELECT idpc FROM IdpcDetails idpc WHERE idpc.defenceClientId = :defenceClientId",
                        IdpcDetails.class)
                .setParameter("defenceClientId", defenceClientId)
                .getResultStream().findFirst().orElse(null);
    }

    public IdpcDetails findOptionalByDefenceClientId(final UUID defenceClientId) {
        return findIdpcDetailsForDefenceClient(defenceClientId);
    }

    public IdpcDetails findIdpcDetailsForDefendantId(final UUID defendantId) {
        return entityManager.createQuery(
                        "SELECT idpc FROM IdpcDetails idpc WHERE idpc.defenceClientId IN (SELECT dc.id FROM DefenceClient dc WHERE dc.defendantId = :defendantId)",
                        IdpcDetails.class)
                .setParameter("defendantId", defendantId)
                .getResultStream().findFirst().orElse(null);
    }
}
