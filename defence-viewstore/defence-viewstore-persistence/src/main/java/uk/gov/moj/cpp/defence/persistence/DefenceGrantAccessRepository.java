package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.DefenceGrantAccess;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefenceGrantAccessRepository extends AbstractDefenceRepository<DefenceGrantAccess, UUID> {

    public DefenceGrantAccessRepository() {
        super(DefenceGrantAccess.class);
    }

    public List<DefenceGrantAccess> findByDefenceClient(final UUID defendantClientId) {
        return entityManager.createQuery(
                        "SELECT dga FROM DefenceGrantAccess dga WHERE dga.defenceClient.id = :defendantClientId AND dga.removed = false",
                        DefenceGrantAccess.class)
                .setParameter("defendantClientId", defendantClientId)
                .getResultList();
    }

    public DefenceGrantAccess findByDefenceClient(final UUID defendantClientId, final UUID userId) {
        return entityManager.createQuery(
                        "SELECT dga FROM DefenceGrantAccess dga WHERE dga.defenceClient.id = :defendantClientId AND dga.granteeDefenceUserDetails.userId = :userId AND dga.removed = false",
                        DefenceGrantAccess.class)
                .setParameter("defendantClientId", defendantClientId)
                .setParameter("userId", userId)
                .getResultStream().findFirst().orElse(null);
    }

    public List<DefenceGrantAccess> findByGranteeAndCaseId(final UUID caseId, final UUID userId) {
        return entityManager.createQuery(
                        "SELECT dga FROM DefenceGrantAccess dga WHERE dga.defenceClient.caseId = :caseId AND dga.granteeDefenceUserDetails.userId = :userId AND dga.removed = false",
                        DefenceGrantAccess.class)
                .setParameter("caseId", caseId)
                .setParameter("userId", userId)
                .getResultList();
    }
}
