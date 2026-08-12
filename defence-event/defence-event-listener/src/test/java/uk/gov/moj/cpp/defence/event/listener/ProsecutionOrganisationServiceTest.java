package uk.gov.moj.cpp.defence.event.listener;

import static com.google.common.collect.ImmutableList.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.event.listener.utils.ApplicationParameters;
import uk.gov.moj.cpp.defence.events.CaseHearingAssignments;
import uk.gov.moj.cpp.defence.events.CasesAssignedToOrganisation;
import uk.gov.moj.cpp.defence.persistence.OrganisationAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.AssignmentUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.NoResultException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProsecutionOrganisationServiceTest {

    @Mock
    private OrganisationAccessRepository organisationAccessRepository;

    @Mock
    private ApplicationParameters applicationParameters;

    @InjectMocks
    private ProsecutionOrganisationService prosecutionOrganisationService;

    @Mock
    private ProsecutionOrganisationAccess prosecutionOrganisationAccess;

    @Captor
    private ArgumentCaptor<ProsecutionOrganisationCaseKey> prosecutionOrgCaseKey;

    @Captor
    private ArgumentCaptor<ProsecutionOrganisationAccess> organisationAccessCaptor;

    @Test
    public void shouldFindProsecutionOrganisationAccess() {

        final UUID caseId = randomUUID();
        final UUID organisationId = randomUUID();
        when(organisationAccessRepository.findBy(prosecutionOrgCaseKey.capture())).thenReturn(prosecutionOrganisationAccess);

        final Optional<ProsecutionOrganisationAccess> prosecutionOrganisationAccess = prosecutionOrganisationService.getProsecutionOrganisationAccess(caseId, organisationId);

        assertThat(prosecutionOrganisationAccess.isPresent(), is(true));
        assertThat(prosecutionOrgCaseKey.getValue().getCaseId(), is(caseId));
        assertThat(prosecutionOrgCaseKey.getValue().getAssigneeOrganisationId(), is(organisationId));
    }

    @Test
    public void shouldReturnEmptyProsecutionOrganisationAccessWhenNotFound() {
        final UUID caseId = randomUUID();
        final UUID organisationId = randomUUID();
        when(organisationAccessRepository.findBy(prosecutionOrgCaseKey.capture())).thenThrow(NoResultException.class);

        final Optional<ProsecutionOrganisationAccess> prosecutionOrganisationAccess = prosecutionOrganisationService.getProsecutionOrganisationAccess(caseId, organisationId);

        assertThat(prosecutionOrganisationAccess.isPresent(), is(false));
        assertThat(prosecutionOrgCaseKey.getValue().getCaseId(), is(caseId));
        assertThat(prosecutionOrgCaseKey.getValue().getAssigneeOrganisationId(), is(organisationId));
    }

    @Test
    public void shouldNotUpdateProsecutionOrganisationAccessWhenNoExistingExpiryDate() {
        final UUID caseId = randomUUID();
        final UUID organisationId = randomUUID();
        final ZonedDateTime assignmentTimestamp = ZonedDateTime.now();

        final ProsecutionOrganisationAccess savedEntity = new ProsecutionOrganisationAccess();

        when(organisationAccessRepository.findBy(any())).thenReturn(savedEntity);

        final CasesAssignedToOrganisation casesAssignedToOrganisation = getCasesAssignedToOrganisation(caseId, organisationId, assignmentTimestamp).build();
        ProsecutionOrganisationAccess updatedProsecutionOrganisationAccess = prosecutionOrganisationService.updateOrSave(caseId,
                casesAssignedToOrganisation.getAssigneeDetails(), casesAssignedToOrganisation.getAssigneeOrganisation(), casesAssignedToOrganisation.getAssignorDetails(),
                casesAssignedToOrganisation.getAssignorOrganisation(), String.valueOf(casesAssignedToOrganisation.getRepresentingOrganisation()),
                casesAssignedToOrganisation.getAssignmentTimestamp());

        assertThat(updatedProsecutionOrganisationAccess.getAssignmentExpiryDate(), is(nullValue()));
    }

    @Test
    public void shouldUpdateAssignorDetailsWhenHasExistingExpiryDateAndAssignorUserDifferent() {
        final UUID caseId = randomUUID();
        final UUID organisationId = randomUUID();
        final ZonedDateTime assignmentTimestamp = ZonedDateTime.now();
        final int expiryHours = 2;

        final AssignmentUserDetails assignorDetails = new AssignmentUserDetails(randomUUID(), randomUUID(), "f", "l");
        final ProsecutionOrganisationAccess savedEntity = new ProsecutionOrganisationAccess();
        savedEntity.setAssignorDetails(assignorDetails);
        savedEntity.setAssignmentExpiryDate(assignmentTimestamp.plusHours(2));

        when(applicationParameters.getAssignmentExpiryHours()).thenReturn(expiryHours);
        when(organisationAccessRepository.findBy(any())).thenReturn(savedEntity);

        final CasesAssignedToOrganisation casesAssignedToOrganisation = getCasesAssignedToOrganisation(caseId, organisationId, assignmentTimestamp).build();
        ProsecutionOrganisationAccess updatedProsecutionOrganisationAccess = prosecutionOrganisationService.updateOrSave(caseId,
                casesAssignedToOrganisation.getAssigneeDetails(), casesAssignedToOrganisation.getAssigneeOrganisation(), casesAssignedToOrganisation.getAssignorDetails(),
                casesAssignedToOrganisation.getAssignorOrganisation(), String.valueOf(casesAssignedToOrganisation.getRepresentingOrganisation()),
                casesAssignedToOrganisation.getAssignmentTimestamp());

        assertThat(updatedProsecutionOrganisationAccess.getAssignedDate(), is(assignmentTimestamp));
        assertThat(updatedProsecutionOrganisationAccess.getAssignmentExpiryDate(), is(assignmentTimestamp.plusHours(expiryHours)));
        assertThat(updatedProsecutionOrganisationAccess.getAssignorDetails().getUserId(), is(casesAssignedToOrganisation.getAssignorDetails().getUserId()));
        assertThat(updatedProsecutionOrganisationAccess.getAssignorOrganisationId(), is(casesAssignedToOrganisation.getAssignorOrganisation().getOrgId()));
    }

    @Test
    public void shouldNotUpdateAssignorDetailsWhenHasExistingExpiryDateAndAssignorUserSame() {
        final UUID caseId = randomUUID();
        final UUID organisationId = randomUUID();
        final ZonedDateTime assignmentTimestamp = ZonedDateTime.now();
        final int expiryHours = 2;

        final AssignmentUserDetails assignorDetails = new AssignmentUserDetails(randomUUID(), randomUUID(), "f", "l");
        final ProsecutionOrganisationAccess savedEntity = new ProsecutionOrganisationAccess();
        savedEntity.setAssignorDetails(assignorDetails);
        savedEntity.setAssignmentExpiryDate(assignmentTimestamp.plusHours(2));

        when(applicationParameters.getAssignmentExpiryHours()).thenReturn(expiryHours);
        when(organisationAccessRepository.findBy(any())).thenReturn(savedEntity);

        final CasesAssignedToOrganisation casesAssignedToOrganisation = getCasesAssignedToOrganisation(caseId, organisationId, assignmentTimestamp)
                .withAssignorDetails(PersonDetails.personDetails().withUserId(assignorDetails.getUserId()).build()).build();

        ProsecutionOrganisationAccess updatedProsecutionOrganisationAccess = prosecutionOrganisationService.updateOrSave(caseId,
                casesAssignedToOrganisation.getAssigneeDetails(), casesAssignedToOrganisation.getAssigneeOrganisation(), casesAssignedToOrganisation.getAssignorDetails(),
                casesAssignedToOrganisation.getAssignorOrganisation(), String.valueOf(casesAssignedToOrganisation.getRepresentingOrganisation()),
                casesAssignedToOrganisation.getAssignmentTimestamp());


        assertThat(updatedProsecutionOrganisationAccess.getAssignedDate(), is(assignmentTimestamp));
        assertThat(updatedProsecutionOrganisationAccess.getAssignmentExpiryDate(), is(assignmentTimestamp.plusHours(expiryHours)));
        assertThat(updatedProsecutionOrganisationAccess.getAssignorDetails(), is(assignorDetails));
    }

    @Test
    public void shouldSaveNewProsecutionOrganisationAccessWhenNoExistingProsecutionOrganisationAccess() {
        final UUID caseId = randomUUID();
        final UUID organisationId = randomUUID();
        final ZonedDateTime assignmentTimestamp = ZonedDateTime.now();
        final int expiryHours = 2;

        final AssignmentUserDetails assignorDetails = new AssignmentUserDetails(randomUUID(), randomUUID(), "f", "l");
        final ProsecutionOrganisationAccess savedEntity = new ProsecutionOrganisationAccess();
        savedEntity.setAssignorDetails(assignorDetails);
        savedEntity.setAssignmentExpiryDate(assignmentTimestamp.plusHours(2));

        when(applicationParameters.getAssignmentExpiryHours()).thenReturn(expiryHours);
        when(organisationAccessRepository.findBy(any())).thenThrow(NoResultException.class);

        final CasesAssignedToOrganisation casesAssignedToOrganisation = getCasesAssignedToOrganisation(caseId, organisationId, assignmentTimestamp)
                .withAssignorDetails(PersonDetails.personDetails().withUserId(assignorDetails.getUserId()).build()).build();
        prosecutionOrganisationService.updateOrSave(caseId,
                casesAssignedToOrganisation.getAssigneeDetails(), casesAssignedToOrganisation.getAssigneeOrganisation(), casesAssignedToOrganisation.getAssignorDetails(),
                casesAssignedToOrganisation.getAssignorOrganisation(), String.valueOf(casesAssignedToOrganisation.getRepresentingOrganisation()),
                casesAssignedToOrganisation.getAssignmentTimestamp());

        verify(organisationAccessRepository).saveAndFlush(organisationAccessCaptor.capture());
        final ProsecutionOrganisationAccess actualOrganisationAccess = organisationAccessCaptor.getValue();
        assertThat(actualOrganisationAccess.getId().getCaseId(), is(caseId));

        assertSavedCaseAssignment(actualOrganisationAccess, casesAssignedToOrganisation.getAssignmentTimestamp(),
                casesAssignedToOrganisation.getAssigneeDetails(), casesAssignedToOrganisation.getAssignorDetails(),
                casesAssignedToOrganisation.getAssignorOrganisation());

        assertThat(actualOrganisationAccess.getAssignmentExpiryDate(), is(assignmentTimestamp.plusHours(expiryHours)));
    }


    private void assertSavedCaseAssignment(final ProsecutionOrganisationAccess actualOrganisationAccess, final ZonedDateTime casesAssignedToOrganisation,
                                           final PersonDetails casesAssignedToOrganisation1, final PersonDetails casesAssignedToOrganisation2,
                                           final Organisation casesAssignedToOrganisation3) {

        assertThat(actualOrganisationAccess.getAssignedDate(), is(casesAssignedToOrganisation));

        assertThat(actualOrganisationAccess.getAssigneeDetails().getId(), is(notNullValue()));
        assertThat(actualOrganisationAccess.getAssigneeDetails().getUserId(), is(casesAssignedToOrganisation1.getUserId()));
        assertThat(actualOrganisationAccess.getAssigneeDetails().getFirstName(), is(casesAssignedToOrganisation1.getFirstName()));
        assertThat(actualOrganisationAccess.getAssigneeDetails().getLastName(), is(casesAssignedToOrganisation1.getLastName()));

        assertThat(actualOrganisationAccess.getAssignorDetails().getId(), is(notNullValue()));
        assertThat(actualOrganisationAccess.getAssignorDetails().getUserId(), is(casesAssignedToOrganisation2.getUserId()));
        assertThat(actualOrganisationAccess.getAssignorDetails().getFirstName(), is(casesAssignedToOrganisation2.getFirstName()));
        assertThat(actualOrganisationAccess.getAssignorDetails().getLastName(), is(casesAssignedToOrganisation2.getLastName()));

        assertThat(actualOrganisationAccess.getAssignorOrganisationId(), is(notNullValue()));
        assertThat(actualOrganisationAccess.getAssignorOrganisationName(), is(casesAssignedToOrganisation3.getOrganisationName()));
    }

    private CasesAssignedToOrganisation.Builder getCasesAssignedToOrganisation(final UUID caseId, final UUID organisationId, final ZonedDateTime assignmentTimestamp) {
        return CasesAssignedToOrganisation.casesAssignedToOrganisation()
                .withCaseHearingAssignments(of(CaseHearingAssignments.caseHearingAssignments().withCaseId(caseId).withHearingId(randomUUID()).build()))
                .withAssignorDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("aFirstName").withLastName("aLastName").build())
                .withAssignorOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("oName").build())
                .withAssigneeDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("bFirstName").withLastName("bLastName").build())
                .withAssigneeOrganisation(Organisation.organisation().withOrgId(organisationId).withOrganisationName("ooName").build())
                .withRepresentingOrganisation("CPS")
                .withAssignmentTimestamp(assignmentTimestamp);
    }
}