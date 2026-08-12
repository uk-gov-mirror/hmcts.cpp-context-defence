package uk.gov.moj.cpp.defence.event.listener;

import static com.google.common.collect.ImmutableList.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.events.CaseAssigmentToOrganisationRemoved;
import uk.gov.moj.cpp.defence.events.CaseAssignedToOrganisation;
import uk.gov.moj.cpp.defence.events.CaseHearingAssignments;
import uk.gov.moj.cpp.defence.events.CasesAssignedToOrganisation;
import uk.gov.moj.cpp.defence.persistence.OrganisationAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;

import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProsecutionOrganisationAccessEventListenerTest {
    @Mock
    private OrganisationAccessRepository organisationAccessRepository;

    @Mock
    private Envelope<CaseAssignedToOrganisation> envelope;

    @Mock
    private Envelope<CasesAssignedToOrganisation> casesEnvelope;

    @Mock
    private Envelope<CaseAssigmentToOrganisationRemoved> envelopeRemoveCaseAssignment;

    @Mock
    private ProsecutionOrganisationService prosecutionOrganisationService;

    @InjectMocks
    private ProsecutionOrganisationAccessEventListener organisationAccessEventListener;

    @Captor
    private ArgumentCaptor<ProsecutionOrganisationAccess> organisationAccessCaptor;

    @Captor
    private ArgumentCaptor<ProsecutionOrganisationCaseKey> prosecutionOrganisationCaseKeyCaptor;

    @Mock
    private EntityManager entityManager;

    @Test
    public void shouldProcessAndPersistCaseAssignmentSucceededEvent() {

        final CaseAssignedToOrganisation caseAssignedToOrganisation = CaseAssignedToOrganisation.caseAssignedToOrganisation()
                .withCaseId(randomUUID())
                .withAssignorDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("aFirstName").withLastName("aLastName").build())
                .withAssignorOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("oName").build())
                .withAssigneeDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("bFirstName").withLastName("bLastName").build())
                .withAssigneeOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("ooName").build())
                .withRepresentingOrganisation("CPS")
                .withAssignmentTimestamp(ZonedDateTime.now())
                .build();

        when(envelope.payload()).thenReturn(caseAssignedToOrganisation);

        organisationAccessEventListener.prosecutionCaseAssignmentToOrganisationReceived(envelope);

        verify(organisationAccessRepository).save(organisationAccessCaptor.capture());
        ProsecutionOrganisationAccess actualAdvocateAssignment = organisationAccessCaptor.getValue();
        assertThat(actualAdvocateAssignment.getId().getCaseId(), is(caseAssignedToOrganisation.getCaseId()));
        assertSavedCaseAssignment(actualAdvocateAssignment, caseAssignedToOrganisation.getAssignmentTimestamp(),
                caseAssignedToOrganisation.getAssigneeDetails(), caseAssignedToOrganisation.getAssignorDetails(),
                caseAssignedToOrganisation.getAssignorOrganisation());
        assertThat(actualAdvocateAssignment.getAssignmentExpiryDate(), is(nullValue()));
    }

    @Test
    public void givenCaseAssignedToOrganisation_WhenRemoveCaseAssignmentForTheOrganisation_shouldRemoveOrganisationAccess() {
        final UUID caseId = randomUUID();
        final UUID assigneeOrgId = randomUUID();
        final CaseAssigmentToOrganisationRemoved caseAssigmentToOrganisationRemoved = CaseAssigmentToOrganisationRemoved.caseAssigmentToOrganisationRemoved()
                .withCaseId(caseId)
                .withAssigneeOrganisationId(assigneeOrgId)
                .build();

        when(envelopeRemoveCaseAssignment.payload()).thenReturn(caseAssigmentToOrganisationRemoved);

        ProsecutionOrganisationAccess prosecutionOrganisation = new ProsecutionOrganisationAccess();
        prosecutionOrganisation.setId(new ProsecutionOrganisationCaseKey(caseId, assigneeOrgId));
        prosecutionOrganisation.setAssignedDate(ZonedDateTime.now());

        when(organisationAccessRepository.findBy(prosecutionOrganisationCaseKeyCaptor.capture())).thenReturn(prosecutionOrganisation);

        organisationAccessEventListener.prosecutionRemoveCaseAssignmentToOrganisationReceived(envelopeRemoveCaseAssignment);

        verify(organisationAccessRepository, times(1)).remove(any(ProsecutionOrganisationAccess.class));
    }

    @Test
    public void shouldProcessAndPersistCaseAssignmentsSucceededEvent() {

        final UUID caseId = randomUUID();
        final ZonedDateTime assignmentTimestamp = ZonedDateTime.now();

        CasesAssignedToOrganisation casesAssignedToOrganisation = CasesAssignedToOrganisation.casesAssignedToOrganisation()
                .withCaseHearingAssignments(of(CaseHearingAssignments.caseHearingAssignments().withCaseId(caseId).withHearingId(randomUUID()).build()))
                .withAssignorDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("aFirstName").withLastName("aLastName").build())
                .withAssignorOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("oName").build())
                .withAssigneeDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("bFirstName").withLastName("bLastName").build())
                .withAssigneeOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("ooName").build())
                .withRepresentingOrganisation("CPS")
                .withAssignmentTimestamp(assignmentTimestamp)
                .build();

        when(casesEnvelope.payload()).thenReturn(casesAssignedToOrganisation);

        organisationAccessEventListener.prosecutionCasesAssignmentToOrganisationReceived(casesEnvelope);

        verify(prosecutionOrganisationService).updateOrSave(caseId, casesAssignedToOrganisation.getAssigneeDetails(), casesAssignedToOrganisation.getAssigneeOrganisation(), casesAssignedToOrganisation.getAssignorDetails(),
                casesAssignedToOrganisation.getAssignorOrganisation(), String.valueOf(casesAssignedToOrganisation.getRepresentingOrganisation()),
                casesAssignedToOrganisation.getAssignmentTimestamp());
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
}