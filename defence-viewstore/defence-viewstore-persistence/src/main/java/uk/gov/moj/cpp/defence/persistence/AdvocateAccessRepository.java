package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionAdvocateAccess;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Advocate-access viewstore repository. Migrated from a DeltaSpike {@code AbstractEntityRepository} to a
 * concrete {@code @ApplicationScoped} JPA repository as part of the Java 25 / Jakarta EE upgrade; the
 * {@code @Query} methods become explicit JPQL / native queries preserving the original semantics.
 */
@ApplicationScoped
public class AdvocateAccessRepository extends AbstractDefenceRepository<ProsecutionAdvocateAccess, UUID> {

    public AdvocateAccessRepository() {
        super(ProsecutionAdvocateAccess.class);
    }

    public List<ProsecutionAdvocateAccess> findByCaseIdAndAssigneeId(final UUID caseId, final UUID assigneeId) {
        return entityManager.createQuery(
                "select entity from ProsecutionAdvocateAccess entity where entity.prosecutionOrganisation.id.caseId = :caseId and entity.assigneeDetails.userId = :assigneeId",
                ProsecutionAdvocateAccess.class)
                .setParameter("caseId", caseId)
                .setParameter("assigneeId", assigneeId)
                .getResultList();
    }

    public List<ProsecutionAdvocateAccess> findActiveByCaseIdAndAssigneeId(final UUID caseId, final UUID assigneeId) {
        return entityManager.createQuery(
                "select entity from ProsecutionAdvocateAccess entity where entity.prosecutionOrganisation.id.caseId = :caseId and entity.assigneeDetails.userId = :assigneeId and (entity.assignmentExpiryDate is null or entity.assignmentExpiryDate > :currentTime)",
                ProsecutionAdvocateAccess.class)
                .setParameter("caseId", caseId)
                .setParameter("assigneeId", assigneeId)
                .setParameter("currentTime", ZonedDateTime.now(ZoneOffset.UTC))
                .getResultList();
    }

    public List<ProsecutionAdvocateAccess> findExpiredCaseAssignments() {
        return entityManager.createQuery(
                "select entity from ProsecutionAdvocateAccess entity where entity.assignmentExpiryDate < :currentTime order by entity.assignmentExpiryDate desc",
                ProsecutionAdvocateAccess.class)
                .setParameter("currentTime", ZonedDateTime.now(ZoneOffset.UTC))
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<ProsecutionAdvocateAccess> findExpiredCaseAssignments(final int limitCount) {
        return entityManager.createNativeQuery(
                "select pa.*  from prosecution_advocate_access pa  JOIN prosecution_organisation_access po ON pa.assignee_organisation_id = po.assignee_organisation_id AND pa.case_id = po.case_id where pa.assignment_expiry_date  < now() order by pa.assignment_expiry_date desc",
                ProsecutionAdvocateAccess.class)
                .setMaxResults(limitCount)
                .getResultList();
    }
}
