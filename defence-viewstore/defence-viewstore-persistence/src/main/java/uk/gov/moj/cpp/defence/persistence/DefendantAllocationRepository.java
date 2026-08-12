package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocation;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefendantAllocationRepository extends AbstractDefenceRepository<DefendantAllocation, UUID> {

    public DefendantAllocationRepository() {
        super(DefendantAllocation.class);
    }

    public List<DefendantAllocation> findDefendantAllocationByCaseId(final UUID caseId) {
        return entityManager.createQuery(
                        "SELECT p FROM DefendantAllocation p WHERE p.defendantId IN (SELECT dc.defendantId FROM DefenceClient dc WHERE dc.caseId = :caseId)",
                        DefendantAllocation.class)
                .setParameter("caseId", caseId)
                .getResultList();
    }
}
