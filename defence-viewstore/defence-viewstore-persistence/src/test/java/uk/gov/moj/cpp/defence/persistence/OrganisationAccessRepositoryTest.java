package uk.gov.moj.cpp.defence.persistence;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.services.test.utils.persistence.HibernateTestEntityManagerProvider;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.persistence.entity.AssignmentUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OrganisationAccessRepositoryTest {

    @RegisterExtension
    static HibernateTestEntityManagerProvider hibernateTestEntityManagerProvider =
            new HibernateTestEntityManagerProvider("defence-test-persistence-unit");

    private OrganisationAccessRepository organisationAccessRepository;

    @BeforeEach
    public void createRepositoryAndClearData() {
        organisationAccessRepository = new OrganisationAccessRepository();
        hibernateTestEntityManagerProvider.injectEntityManagerInto(organisationAccessRepository);
        organisationAccessRepository.findAll().forEach(organisationAccessRepository::remove);
        organisationAccessRepository.flush();
    }

    private void createOrganisationAccessRecord(UUID caseId, UUID assigneeOrgId, UUID assigneeId, ZonedDateTime assignmentExpiryDate) {
        // Insert OrganisationAccess record inlined logic from ProsecutionCaseAccessTransformer#toOrganisationAccess
        Organisation assignorOrganisation = Organisation.organisation().withOrgId(UUID.randomUUID()).withOrganisationName("Assignor Org").build();
        PersonDetails assigneeUserDetails = PersonDetails.personDetails().withUserId(UUID.randomUUID()).withFirstName("Assignee").build();
        PersonDetails assignorUserDetails = PersonDetails.personDetails().withUserId(UUID.randomUUID()).withFirstName("Assignor").build();
        String representingOrganisation = "CPS";
        Organisation assigneeOrganisation = Organisation.organisation().withOrgId(assigneeOrgId).withOrganisationName("Assignee Org").build();
        ZonedDateTime assignedDate = ZonedDateTime.now().minusDays(1);
        ProsecutionOrganisationAccess organisationAccess = new ProsecutionOrganisationAccess();
        organisationAccess.setId(new ProsecutionOrganisationCaseKey(caseId, assigneeOrganisation.getOrgId()));
        AssignmentUserDetails assignorDetails = new AssignmentUserDetails(UUID.randomUUID(), assignorUserDetails.getUserId(), assignorUserDetails.getFirstName(), assignorUserDetails.getLastName());
        organisationAccess.setAssignorDetails(assignorDetails);
        organisationAccess.setAssignorOrganisationId(assignorOrganisation.getOrgId());
        organisationAccess.setAssignorOrganisationName(assignorOrganisation.getOrganisationName());
        AssignmentUserDetails assigneeDetails = new AssignmentUserDetails(assigneeId, assigneeUserDetails.getUserId(), assigneeUserDetails.getFirstName(), assigneeUserDetails.getLastName());
        organisationAccess.setAssigneeDetails(assigneeDetails);
        organisationAccess.setAssigneeOrganisationName(assigneeOrganisation.getOrganisationName());
        organisationAccess.setRepresentationType(uk.gov.moj.cpp.defence.persistence.entity.RepresentationType.PROSECUTION);
        organisationAccess.setRepresenting(representingOrganisation);
        organisationAccess.setAssignedDate(assignedDate);
        organisationAccess.setAssignmentExpiryDate(assignmentExpiryDate);
        organisationAccessRepository.save(organisationAccess);
        organisationAccessRepository.flush();
    }

    @Test
    public void shouldRespectLimitInFindExpiredCaseAssignments() {
        // Create 3 records with assignment expiry dates in the past
        for (int i = 0; i < 3; i++) {
            createOrganisationAccessRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ZonedDateTime.now().minusDays(1));
        }
        // Create 2 records with assignment expiry dates in the future
        for (int i = 0; i < 2; i++) {
            createOrganisationAccessRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ZonedDateTime.now().plusDays(1));
        }

        assertThat(organisationAccessRepository.findAll().size(), is(5));
        assertThat(organisationAccessRepository.findExpiredCaseAssignments().size(), is(3));
        assertThat(organisationAccessRepository.findExpiredCaseAssignments(2).size(), is(2));
    }

    @Test
    public void shouldFindByCaseId() {
        final UUID caseId = UUID.randomUUID();
        final UUID otherCaseId = UUID.randomUUID();
        createOrganisationAccessRecord(caseId, UUID.randomUUID(), UUID.randomUUID(), ZonedDateTime.now().plusDays(1));
        createOrganisationAccessRecord(caseId, UUID.randomUUID(), UUID.randomUUID(), ZonedDateTime.now().plusDays(1));
        createOrganisationAccessRecord(otherCaseId, UUID.randomUUID(), UUID.randomUUID(), ZonedDateTime.now().plusDays(1));

        assertThat(organisationAccessRepository.findByCaseId(caseId).size(), is(2));
        assertThat(organisationAccessRepository.findByCaseId(otherCaseId).size(), is(1));
        assertThat(organisationAccessRepository.findByCaseId(UUID.randomUUID()).size(), is(0));
    }

    @Test
    public void shouldFindActiveAssignmentsByCaseIdAndAssigneeOrganisationId() {
        final UUID caseIdExpired = UUID.randomUUID();
        final UUID caseIdActive = UUID.randomUUID();
        final UUID assigneeOrgId = UUID.randomUUID();
        final UUID assigneeIdExpired = UUID.randomUUID();
        final UUID assigneeIdActive = UUID.randomUUID();
        createOrganisationAccessRecord(caseIdExpired, assigneeOrgId, assigneeIdExpired, ZonedDateTime.now().minusDays(1));
        createOrganisationAccessRecord(caseIdActive, assigneeOrgId, assigneeIdActive, ZonedDateTime.now().plusDays(1));

        assertThat(organisationAccessRepository.findAll().size(), is(2));
        assertThat(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseIdActive, assigneeOrgId).size(), is(1));
        assertThat(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseIdExpired, assigneeOrgId).size(), is(1));
        assertThat(organisationAccessRepository.findActiveByCaseIdAndAssigneeOrganisationId(caseIdActive, assigneeOrgId).size(), is(1));
        assertThat(organisationAccessRepository.findActiveByCaseIdAndAssigneeOrganisationId(caseIdExpired, assigneeOrgId).size(), is(0));
    }
}
