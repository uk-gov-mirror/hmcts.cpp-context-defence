package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.DefenceCase;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefenceCaseRepository extends AbstractDefenceRepository<DefenceCase, UUID> {

    public DefenceCaseRepository() {
        super(DefenceCase.class);
    }

    public DefenceCase findOptionalByUrn(final String urn) {
        return entityManager.createQuery("SELECT dc FROM DefenceCase dc WHERE dc.urn = :urn", DefenceCase.class)
                .setParameter("urn", urn)
                .getResultStream().findFirst().orElse(null);
    }
}
