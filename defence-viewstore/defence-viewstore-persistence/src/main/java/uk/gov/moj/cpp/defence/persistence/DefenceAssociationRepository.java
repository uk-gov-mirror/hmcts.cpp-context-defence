package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefenceAssociationRepository extends AbstractDefenceRepository<DefenceAssociation, UUID> {

    public DefenceAssociationRepository() {
        super(DefenceAssociation.class);
    }

    public List<DefenceAssociation> findByLAAContractNumber(final Collection<String> laaContractNumbers) {
        return entityManager.createQuery(
                        "SELECT entity FROM DefenceAssociation entity WHERE entity.laaContractNumber IN (:laaContractNumbers) AND entity.endDate IS NULL",
                        DefenceAssociation.class)
                .setParameter("laaContractNumbers", laaContractNumbers)
                .getResultList();
    }

    public List<DefenceAssociation> findByOrganisationIdAndCaseId(final UUID organisationId, final UUID caseId) {
        return entityManager.createQuery(
                        "SELECT da FROM DefenceAssociation da, DefenceClient dc WHERE da.orgId = :organisationId AND da.defenceAssociationDefendant.defendantId = dc.defendantId AND dc.caseId = :caseId",
                        DefenceAssociation.class)
                .setParameter("organisationId", organisationId)
                .setParameter("caseId", caseId)
                .getResultList();
    }

    public List<DefenceAssociation> findByUserIdAndCurrentDate(final UUID userId, final ZonedDateTime currentDate) {
        return entityManager.createQuery(
                        "SELECT da FROM DefenceAssociation da WHERE da.userId = :userId AND (da.endDate IS NULL OR da.startDate <= :currentDate AND da.endDate >= :currentDate)",
                        DefenceAssociation.class)
                .setParameter("userId", userId)
                .setParameter("currentDate", currentDate)
                .getResultList();
    }
}
