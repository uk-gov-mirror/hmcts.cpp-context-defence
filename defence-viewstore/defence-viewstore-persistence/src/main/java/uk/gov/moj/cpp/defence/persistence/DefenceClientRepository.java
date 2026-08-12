package uk.gov.moj.cpp.defence.persistence;

import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefenceClientRepository extends AbstractDefenceRepository<DefenceClient, UUID> {

    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String IS_CIVIL = "isCivil";
    private static final String DATE_OF_BIRTH = "dateOfBirth";
    private static final String ORGANISATION_NAME = "organisationName";
    private static final String IS_GROUP_MEMBER = "isGroupMember";
    private static final String PTI_URN = "ptiUrn";
    private static final String DEFENDANT_ID = "defendantId";

    public DefenceClientRepository() {
        super(DefenceClient.class);
    }

    public List<DefenceClient> findDefenceClientByCriteria(final String firstName, final String lastName, final LocalDate dateOfBirth, final String ptiUrn) {
        return entityManager.createQuery("select dc FROM DefenceClient dc INNER JOIN DefenceCase c ON c.id = dc.caseId WHERE TRIM(upper(dc.firstName)) = upper(:firstName) and TRIM(upper(dc.lastName)) = upper(:lastName) and dc.dateOfBirth = :dateOfBirth and c.urn = :ptiUrn and dc.visible = true", DefenceClient.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(DATE_OF_BIRTH, dateOfBirth).setParameter(PTI_URN, ptiUrn)
                .getResultList();
    }

    public List<DefenceClient> findDefenceClientByCriteria(final String firstName, final String lastName, final LocalDate dateOfBirth, final String ptiUrn, final boolean isCivil) {
        return entityManager.createQuery("select dc FROM DefenceClient dc INNER JOIN DefenceCase c ON c.id = dc.caseId WHERE TRIM(upper(dc.firstName)) = upper(:firstName) and TRIM(upper(dc.lastName)) = upper(:lastName) and dc.dateOfBirth = :dateOfBirth and c.urn = :ptiUrn and dc.visible = true and c.isCivil = :isCivil", DefenceClient.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(DATE_OF_BIRTH, dateOfBirth).setParameter(PTI_URN, ptiUrn).setParameter(IS_CIVIL, isCivil)
                .getResultList();
    }

    public List<DefenceClient> findDefenceClientByCriteriaWithOutDob(final String firstName, final String lastName, final String ptiUrn, final boolean isCivil) {
        return entityManager.createQuery("select dc FROM DefenceClient dc INNER JOIN DefenceCase c ON c.id = dc.caseId WHERE upper(dc.firstName) = upper(:firstName) and upper(dc.lastName) = upper(:lastName) and c.urn = :ptiUrn and dc.visible = true and c.isCivil = :isCivil", DefenceClient.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(PTI_URN, ptiUrn).setParameter(IS_CIVIL, isCivil)
                .getResultList();
    }

    public List<DefenceClient> findDefenceClientByCriteria(final String firstName, final String lastName, final LocalDate dateOfBirth) {
        return entityManager.createQuery("select dc FROM DefenceClient dc INNER JOIN DefenceCase c ON c.id = dc.caseId WHERE TRIM(upper(dc.firstName)) = upper(:firstName) and TRIM(upper(dc.lastName)) = upper(:lastName) and dc.dateOfBirth = :dateOfBirth and dc.visible = true", DefenceClient.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(DATE_OF_BIRTH, dateOfBirth)
                .getResultList();
    }

    public List<DefenceClient> findDefenceClientByCriteria(final String firstName, final String lastName, final LocalDate dateOfBirth, final boolean isCivil) {
        return entityManager.createQuery("select dc FROM DefenceClient dc INNER JOIN DefenceCase c ON c.id = dc.caseId WHERE TRIM(upper(dc.firstName)) = upper(:firstName) and TRIM(upper(dc.lastName)) = upper(:lastName) and dc.dateOfBirth = :dateOfBirth and dc.visible = true and c.isCivil = :isCivil", DefenceClient.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(DATE_OF_BIRTH, dateOfBirth).setParameter(IS_CIVIL, isCivil)
                .getResultList();
    }

    public List<DefenceClient> findDefenceClientByCriteriaWithOutDob(final String firstName, final String lastName, final boolean isCivil) {
        return entityManager.createQuery("select dc FROM DefenceClient dc INNER JOIN DefenceCase c ON c.id = dc.caseId WHERE upper(dc.firstName) = upper(:firstName) and upper(dc.lastName) = upper(:lastName) and dc.visible = true and c.isCivil = :isCivil", DefenceClient.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(IS_CIVIL, isCivil)
                .getResultList();
    }

    public List<DefenceClient> findDefenceClientByCriteria(final String organisationName, final String ptiUrn) {
        return entityManager.createQuery("select dc FROM DefenceClient dc, DefenceCase c WHERE upper(dc.organisationName) = upper(:organisationName) and upper(c.urn) = upper(:ptiUrn) and dc.visible = true and dc.caseId = c.id", DefenceClient.class)
                .setParameter(ORGANISATION_NAME, organisationName).setParameter(PTI_URN, ptiUrn)
                .getResultList();
    }

    public List<DefenceClient> findDefenceClientByCriteria(final String organisationName, final String ptiUrn, final boolean isCivil) {
        return entityManager.createQuery("select dc FROM DefenceClient dc, DefenceCase c WHERE upper(dc.organisationName) = upper(:organisationName) and upper(c.urn) = upper(:ptiUrn) and dc.visible = true and dc.caseId = c.id and c.isCivil = :isCivil", DefenceClient.class)
                .setParameter(ORGANISATION_NAME, organisationName).setParameter(PTI_URN, ptiUrn).setParameter(IS_CIVIL, isCivil)
                .getResultList();
    }

    public List<DefenceClient> findDefenceClientByCriteria(final String organisationName) {
        return entityManager.createQuery("select dc FROM DefenceClient dc, DefenceCase c WHERE upper(dc.organisationName) = upper(:organisationName) and dc.visible = true and dc.caseId = c.id", DefenceClient.class)
                .setParameter(ORGANISATION_NAME, organisationName)
                .getResultList();
    }

    public List<DefenceClient> findDefenceClientByCriteria(final String organisationName, final boolean isCivil) {
        return entityManager.createQuery("select dc FROM DefenceClient dc, DefenceCase c WHERE upper(dc.organisationName) = upper(:organisationName) and dc.visible = true and dc.caseId = c.id and c.isCivil = :isCivil", DefenceClient.class)
                .setParameter(ORGANISATION_NAME, organisationName).setParameter(IS_CIVIL, isCivil)
                .getResultList();
    }

    public DefenceClient findOptionalByDefendantId(final UUID defendantId) {
        return entityManager.createQuery("select dc FROM DefenceClient dc WHERE dc.defendantId = :defendantId", DefenceClient.class)
                .setParameter(DEFENDANT_ID, defendantId)
                .getResultStream().findFirst().orElse(null);
    }

    public DefenceClient findDefenceClientByCriteria(final UUID defendantId) {
        return entityManager.createQuery("select dc FROM DefenceClient dc WHERE dc.defendantId = :defendantId", DefenceClient.class)
                .setParameter(DEFENDANT_ID, defendantId)
                .getResultStream().findFirst().orElse(null);
    }

    public DefenceClient findOptionalByDefendantIdAndCaseId(final UUID defendantId, final UUID caseId) {
        return entityManager.createQuery("select dc FROM DefenceClient dc WHERE dc.defendantId = :defendantId and dc.caseId = :caseId", DefenceClient.class)
                .setParameter(DEFENDANT_ID, defendantId).setParameter("caseId", caseId)
                .getResultStream().findFirst().orElse(null);
    }

    public List<DefenceClient> findByCaseId(final UUID caseId) {
        return entityManager.createQuery("select dc FROM DefenceClient dc WHERE dc.caseId = :caseId", DefenceClient.class)
                .setParameter("caseId", caseId)
                .getResultList();
    }

    public List<UUID> findCasesAssociatedWithDefenceClientByPersonDefendant(final String firstName, final String lastName, final LocalDate dateOfBirth) {
        return entityManager.createQuery("SELECT dc.caseId FROM DefenceClient dc, DefenceCase c WHERE TRIM(upper(dc.firstName)) = upper(:firstName) and TRIM(upper(dc.lastName)) = upper(:lastName) and dc.dateOfBirth = :dateOfBirth and dc.visible = true and dc.caseId = c.id", UUID.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(DATE_OF_BIRTH, dateOfBirth)
                .getResultList();
    }

    public List<UUID> findCasesAssociatedWithDefenceClientByPersonDefendant(final String firstName, final String lastName, final LocalDate dateOfBirth, final boolean isCivil, final boolean isGroupMember) {
        return entityManager.createQuery("SELECT dc.caseId FROM DefenceClient dc, DefenceCase c WHERE TRIM(upper(dc.firstName)) = upper(:firstName) and TRIM(upper(dc.lastName)) = upper(:lastName) and dc.dateOfBirth = :dateOfBirth and dc.visible = true and dc.caseId = c.id and c.isCivil = :isCivil and c.isGroupMember = :isGroupMember", UUID.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(DATE_OF_BIRTH, dateOfBirth).setParameter(IS_CIVIL, isCivil).setParameter(IS_GROUP_MEMBER, isGroupMember)
                .getResultList();
    }

    public List<UUID> findCasesAssociatedWithDefenceClientByPersonDefendantWithoutDob(final String firstName, final String lastName, final boolean isCivil, final boolean isGroupMember) {
        return entityManager.createQuery("SELECT DISTINCT dc.caseId FROM DefenceClient dc, DefenceCase c WHERE upper(dc.firstName) = upper(:firstName) and upper(dc.lastName) = upper(:lastName) and dc.visible = true and dc.caseId = c.id and c.isCivil = :isCivil and c.isGroupMember = :isGroupMember", UUID.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(IS_CIVIL, isCivil).setParameter(IS_GROUP_MEMBER, isGroupMember)
                .getResultList();
    }

    public List<UUID> findCasesAssociatedWithDefenceClientByOrganisationDefendant(final String organisationName) {
        return entityManager.createQuery("SELECT dc.caseId FROM DefenceClient dc, DefenceCase c WHERE upper(dc.organisationName) = upper(:organisationName) and dc.visible = true and dc.caseId = c.id", UUID.class)
                .setParameter(ORGANISATION_NAME, organisationName)
                .getResultList();
    }

    public List<UUID> findCasesAssociatedWithDefenceClientByOrganisationDefendant(final String organisationName, final boolean isCivil, final boolean isGroupMember) {
        return entityManager.createQuery("SELECT dc.caseId FROM DefenceClient dc, DefenceCase c WHERE upper(dc.organisationName) = upper(:organisationName) and dc.visible = true and dc.caseId = c.id and c.isCivil = :isCivil and c.isGroupMember = :isGroupMember", UUID.class)
                .setParameter(ORGANISATION_NAME, organisationName).setParameter(IS_CIVIL, isCivil).setParameter(IS_GROUP_MEMBER, isGroupMember)
                .getResultList();
    }

    public List<UUID> getPersonDefendant(final String firstName, final String lastName, final LocalDate dateOfBirth) {
        return entityManager.createQuery("SELECT dc.defendantId FROM DefenceClient dc WHERE TRIM(upper(dc.firstName)) = upper(:firstName) and TRIM(upper(dc.lastName)) = upper(:lastName) and dc.dateOfBirth = :dateOfBirth", UUID.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(DATE_OF_BIRTH, dateOfBirth)
                .getResultList();
    }

    public List<UUID> getPersonDefendant(final String firstName, final String lastName, final LocalDate dateOfBirth, final boolean isCivil, final boolean isGroupMember) {
        return entityManager.createQuery("SELECT dc.defendantId FROM DefenceClient dc INNER JOIN DefenceCase c ON c.id = dc.caseId WHERE TRIM(upper(dc.firstName)) = upper(:firstName) and TRIM(upper(dc.lastName)) = upper(:lastName) and dc.dateOfBirth = :dateOfBirth and c.isCivil = :isCivil and c.isGroupMember = :isGroupMember", UUID.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(DATE_OF_BIRTH, dateOfBirth).setParameter(IS_CIVIL, isCivil).setParameter(IS_GROUP_MEMBER, isGroupMember)
                .getResultList();
    }

    public List<UUID> getPersonDefendantWithOutDob(final String firstName, final String lastName, final boolean isCivil, final boolean isGroupMember) {
        return entityManager.createQuery("SELECT dc.defendantId FROM DefenceClient dc INNER JOIN DefenceCase c ON c.id = dc.caseId WHERE upper(dc.firstName) = upper(:firstName) and upper(dc.lastName) = upper(:lastName) and c.isCivil = :isCivil and c.isGroupMember = :isGroupMember", UUID.class)
                .setParameter(FIRST_NAME, firstName).setParameter(LAST_NAME, lastName).setParameter(IS_CIVIL, isCivil).setParameter(IS_GROUP_MEMBER, isGroupMember)
                .getResultList();
    }

    public List<UUID> getOrganisationDefendant(final String organisationName) {
        return entityManager.createQuery("SELECT dc.defendantId FROM DefenceClient dc WHERE upper(dc.organisationName) = upper(:organisationName)", UUID.class)
                .setParameter(ORGANISATION_NAME, organisationName)
                .getResultList();
    }

    public List<UUID> getOrganisationDefendant(final String organisationName, final boolean isCivil, final boolean isGroupMember) {
        return entityManager.createQuery("SELECT dc.defendantId FROM DefenceClient dc INNER JOIN DefenceCase c ON c.id = dc.caseId WHERE upper(dc.organisationName) = upper(:organisationName) and c.isCivil = :isCivil and c.isGroupMember = :isGroupMember", UUID.class)
                .setParameter(ORGANISATION_NAME, organisationName).setParameter(IS_CIVIL, isCivil).setParameter(IS_GROUP_MEMBER, isGroupMember)
                .getResultList();
    }
}
