package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrganisationAccessRepository extends AbstractDefenceRepository<ProsecutionOrganisationAccess, ProsecutionOrganisationCaseKey> {

    private static final String CASE_ID = "caseId";
    private static final String ASSIGNEE_ORGANISATION_ID = "assigneeOrganisationId";

    public OrganisationAccessRepository() {
        super(ProsecutionOrganisationAccess.class);
    }

    public List<ProsecutionOrganisationAccess> findByCaseId(final UUID caseId) {
        return entityManager.createQuery(
                        "SELECT poa FROM ProsecutionOrganisationAccess poa WHERE poa.id.caseId = :caseId",
                        ProsecutionOrganisationAccess.class)
                .setParameter(CASE_ID, caseId)
                .getResultList();
    }

    public Optional<ProsecutionOrganisationAccess> findByAssigneeOrganisationIdAndCaseId(final UUID assigneeOrganisationId, final UUID caseId) {
        return entityManager.createQuery(
                        "SELECT poa FROM ProsecutionOrganisationAccess poa WHERE poa.id.assigneeOrganisationId = :assigneeOrganisationId AND poa.id.caseId = :caseId",
                        ProsecutionOrganisationAccess.class)
                .setParameter(ASSIGNEE_ORGANISATION_ID, assigneeOrganisationId)
                .setParameter(CASE_ID, caseId)
                .getResultStream().findFirst();
    }

    public List<ProsecutionOrganisationAccess> findByCaseIdAndAssigneeOrganisationId(final UUID caseId, final UUID assigneeOrganisationId) {
        return entityManager.createQuery(
                        "SELECT poa FROM ProsecutionOrganisationAccess poa WHERE poa.id.caseId = :caseId AND poa.id.assigneeOrganisationId = :assigneeOrganisationId",
                        ProsecutionOrganisationAccess.class)
                .setParameter(CASE_ID, caseId)
                .setParameter(ASSIGNEE_ORGANISATION_ID, assigneeOrganisationId)
                .getResultList();
    }

    public List<ProsecutionOrganisationAccess> findActiveByCaseIdAndAssigneeOrganisationId(final UUID caseId, final UUID assigneeOrganisationId) {
        return entityManager.createQuery(
                        "SELECT poa FROM ProsecutionOrganisationAccess poa WHERE poa.id.caseId = :caseId AND poa.id.assigneeOrganisationId = :assigneeOrganisationId AND (poa.assignmentExpiryDate IS NULL OR poa.assignmentExpiryDate > CURRENT_TIMESTAMP)",
                        ProsecutionOrganisationAccess.class)
                .setParameter(CASE_ID, caseId)
                .setParameter(ASSIGNEE_ORGANISATION_ID, assigneeOrganisationId)
                .getResultList();
    }

    public List<ProsecutionOrganisationAccess> findExpiredCaseAssignments() {
        return entityManager.createQuery(
                        "SELECT poa FROM ProsecutionOrganisationAccess poa WHERE poa.assignmentExpiryDate < CURRENT_TIMESTAMP AND poa.prosecutionAdvocatesWithAccess IS EMPTY ORDER BY poa.assignmentExpiryDate DESC",
                        ProsecutionOrganisationAccess.class)
                .getResultList();
    }

    public List<ProsecutionOrganisationAccess> findExpiredCaseAssignments(final int max) {
        return entityManager.createQuery(
                        "SELECT poa FROM ProsecutionOrganisationAccess poa WHERE poa.assignmentExpiryDate < CURRENT_TIMESTAMP AND poa.prosecutionAdvocatesWithAccess IS EMPTY ORDER BY poa.assignmentExpiryDate DESC",
                        ProsecutionOrganisationAccess.class)
                .setMaxResults(max)
                .getResultList();
    }
}
