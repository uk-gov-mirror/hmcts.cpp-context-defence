package uk.gov.moj.cpp.defence.event.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.event.listener.utils.ApplicationParameters;
import uk.gov.moj.cpp.defence.events.CaseAssignedToAdvocate;
import uk.gov.moj.cpp.defence.events.CaseAssignmentToAdvocateRemoved;
import uk.gov.moj.cpp.defence.events.CaseHearingAssignments;
import uk.gov.moj.cpp.defence.events.CasesAssignedToAdvocate;
import uk.gov.moj.cpp.defence.persistence.AdvocateAccessRepository;
import uk.gov.moj.cpp.defence.persistence.OrganisationAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.AssignmentUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionAdvocateAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;
import uk.gov.moj.cpp.defence.persistence.entity.RepresentationType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.collect.ImmutableList.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProsecutionAdvocateAccessEventListenerTest {

    @Mock
    private AdvocateAccessRepository advocateAssignmentRepository;

    @Mock
    private OrganisationAccessRepository organisationAccessRepository;

    @Mock
    private Envelope<CaseAssignedToAdvocate> envelope;

    @Mock
    private Envelope<CasesAssignedToAdvocate> casesEnvelope;

    @Mock
    private Envelope<CaseAssignmentToAdvocateRemoved> envelopeRemoveCaseAssignment;

    @Mock
    private ApplicationParameters applicationParameters;

    @InjectMocks
    private ProsecutionAdvocateAccessEventListener advocateAccessEventListener;

    @Captor
    private ArgumentCaptor<ProsecutionAdvocateAccess> prosecutionAdvocateAccessCaptor;

    @Captor
    private ArgumentCaptor<ProsecutionOrganisationAccess> prosecutionOrganisationAccessCaptor;

    @Mock
    private ProsecutionOrganisationAccess prosecutionOrganisationAccess;

    @Captor
    private ArgumentCaptor<ProsecutionOrganisationCaseKey> prosecutionOrganisationCaseKeyCaptor;

    @Mock
    private ProsecutionOrganisationService prosecutionOrganisationService;

    @Mock
    private EntityManager entityManager;

    @Test
    public void givenNoPreviousCaseAssignmentFoundForAssigneeOrganisation_shouldCreateCaseAssignmentForTheAssigneeOrganisationFirstAndThenAdvocateAssignment() {
        CaseAssignedToAdvocate caseAssignedToAdvocate = getCaseAssignedToAdvocate();

        when(envelope.payload()).thenReturn(caseAssignedToAdvocate);
        when(organisationAccessRepository.findByAssigneeOrganisationIdAndCaseId(caseAssignedToAdvocate.getAssigneeOrganisation().getOrgId(), caseAssignedToAdvocate.getCaseId())).thenReturn(Optional.empty());

        advocateAccessEventListener.prosecutionCaseAssignmentToAdvocateReceived(envelope);

        verify(organisationAccessRepository).save(prosecutionOrganisationAccessCaptor.capture());
        ProsecutionOrganisationAccess prosecutionOrganisationAccess = prosecutionOrganisationAccessCaptor.getValue();
        assertProsecutionOrganisationAccess(caseAssignedToAdvocate.getCaseId(), caseAssignedToAdvocate.getAssigneeOrganisation(), caseAssignedToAdvocate.getAssigneeDetails(),
                caseAssignedToAdvocate.getAssignorDetails(), String.valueOf(caseAssignedToAdvocate.getRepresentingOrganisation()), caseAssignedToAdvocate.getAssignmentTimestamp(), prosecutionOrganisationAccess);

        verify(advocateAssignmentRepository).save(prosecutionAdvocateAccessCaptor.capture());
        ProsecutionAdvocateAccess actualAdvocateAssignment = prosecutionAdvocateAccessCaptor.getValue();
        assertProsecutionAdvocateAccess(caseAssignedToAdvocate.getAssigneeDetails(), caseAssignedToAdvocate.getAssignorDetails(),
                caseAssignedToAdvocate.getAssignmentTimestamp(), prosecutionOrganisationAccess, actualAdvocateAssignment);
        assertThat(actualAdvocateAssignment.getAssignmentExpiryDate(), is(nullValue()));

    }

    @Test
    public void givenNoPreviousCaseAssignmentFoundForAssigneeOrganisation_shouldCreateCaseAssignmentForTheAssigneeOrganisationFirstAndThenAdvocateAssignmentNonCps() {
        CaseAssignedToAdvocate caseAssignedToAdvocate = getCaseAssignedToAdvocateNonCps();

        when(envelope.payload()).thenReturn(caseAssignedToAdvocate);
        when(organisationAccessRepository.findByAssigneeOrganisationIdAndCaseId(caseAssignedToAdvocate.getAssigneeOrganisation().getOrgId(), caseAssignedToAdvocate.getCaseId())).thenReturn(Optional.empty());

        advocateAccessEventListener.prosecutionCaseAssignmentToAdvocateReceived(envelope);

        verify(organisationAccessRepository).save(prosecutionOrganisationAccessCaptor.capture());
        ProsecutionOrganisationAccess prosecutionOrganisationAccess = prosecutionOrganisationAccessCaptor.getValue();
        assertProsecutionOrganisationAccess(caseAssignedToAdvocate.getCaseId(), caseAssignedToAdvocate.getAssigneeOrganisation(), caseAssignedToAdvocate.getAssigneeDetails(),
                caseAssignedToAdvocate.getAssignorDetails(), String.valueOf(caseAssignedToAdvocate.getRepresentingOrganisation()), caseAssignedToAdvocate.getAssignmentTimestamp(), prosecutionOrganisationAccess);

        verify(advocateAssignmentRepository).save(prosecutionAdvocateAccessCaptor.capture());
        ProsecutionAdvocateAccess actualAdvocateAssignment = prosecutionAdvocateAccessCaptor.getValue();
        assertProsecutionAdvocateAccess(caseAssignedToAdvocate.getAssigneeDetails(), caseAssignedToAdvocate.getAssignorDetails(),
                caseAssignedToAdvocate.getAssignmentTimestamp(), prosecutionOrganisationAccess, actualAdvocateAssignment);
        assertThat(actualAdvocateAssignment.getAssignmentExpiryDate(), is(nullValue()));

    }

    @Test
    public void givenExistingPreviousCaseAssignmentForAssigneeOrganisation_shouldOnlyCreateCaseAssignmentForTheAdvocate() {
        CaseAssignedToAdvocate caseAssignedToAdvocate = getCaseAssignedToAdvocate();

        when(envelope.payload()).thenReturn(caseAssignedToAdvocate);
        when(organisationAccessRepository.findByAssigneeOrganisationIdAndCaseId(caseAssignedToAdvocate.getAssigneeOrganisation().getOrgId(), caseAssignedToAdvocate.getCaseId())).thenReturn(Optional.of(prosecutionOrganisationAccess));

        advocateAccessEventListener.prosecutionCaseAssignmentToAdvocateReceived(envelope);

        verify(organisationAccessRepository, times(0)).save(any());

        verify(advocateAssignmentRepository).save(prosecutionAdvocateAccessCaptor.capture());
        ProsecutionAdvocateAccess actualAdvocateAssignment = prosecutionAdvocateAccessCaptor.getValue();
        assertProsecutionAdvocateAccess(caseAssignedToAdvocate.getAssigneeDetails(), caseAssignedToAdvocate.getAssignorDetails(),
                caseAssignedToAdvocate.getAssignmentTimestamp(), prosecutionOrganisationAccess, actualAdvocateAssignment);
        assertThat(actualAdvocateAssignment.getAssignmentExpiryDate(), is(nullValue()));
    }

    @Test
    public void givenCaseAssignedToMultipleAdvocatesInTheSameOrganisation_WhenRemoveCaseAssignmentForTheAdvocate_shouldRemoveSpecificAdvocateAccess() {
        UUID caseId = randomUUID();
        UUID assigneeUserId = randomUUID();
        UUID assigneeOrgId = randomUUID();
        CaseAssignmentToAdvocateRemoved caseAssignmentToAdvocateRemoved = CaseAssignmentToAdvocateRemoved.caseAssignmentToAdvocateRemoved()
                .withCaseId(caseId)
                .withAssigneeUserId(assigneeUserId)
                .withAssigneeOrganisationId(assigneeOrgId)
                .build();

        when(envelopeRemoveCaseAssignment.payload()).thenReturn(caseAssignmentToAdvocateRemoved);

        ProsecutionOrganisationAccess prosecutionOrganisation = new ProsecutionOrganisationAccess();
        prosecutionOrganisation.setId(new ProsecutionOrganisationCaseKey(caseId, assigneeOrgId));
        prosecutionOrganisation.setAssignedDate(ZonedDateTime.now());
        prosecutionOrganisation.getProsecutionAdvocatesWithAccess().add(getProsecutionAdvocates(caseId, assigneeUserId, assigneeOrgId));
        prosecutionOrganisation.getProsecutionAdvocatesWithAccess().add(getProsecutionAdvocates(caseId, randomUUID(), assigneeOrgId));

        when(organisationAccessRepository.findBy(prosecutionOrganisationCaseKeyCaptor.capture())).thenReturn(prosecutionOrganisation);

        advocateAccessEventListener.prosecutionRemoveCaseAssignmentToAdvocateReceived(envelopeRemoveCaseAssignment);

        verify(organisationAccessRepository, times(1)).save(prosecutionOrganisationAccessCaptor.capture());
        ProsecutionOrganisationAccess updatedProsecutionOrganisationAccess = prosecutionOrganisationAccessCaptor.getValue();
        assertThat(updatedProsecutionOrganisationAccess.getProsecutionAdvocatesWithAccess().size(), is(1));
        assertThat(updatedProsecutionOrganisationAccess.getProsecutionAdvocatesWithAccess().stream().noneMatch(paa -> paa.getAssigneeDetails().getUserId().equals(assigneeUserId)), is(true));
    }

    @Test
    public void givenCaseAssignedToSingleAdvocatesInTheSameOrganisation_WhenRemoveCaseAssignmentForTheAdvocate_shouldRemoveSpecificAdvocateAccessAndOrganisationAccess() {
        UUID caseId = randomUUID();
        UUID assigneeUserId = randomUUID();
        UUID assigneeOrgId = randomUUID();
        CaseAssignmentToAdvocateRemoved caseAssignmentToAdvocateRemoved = CaseAssignmentToAdvocateRemoved.caseAssignmentToAdvocateRemoved()
                .withCaseId(caseId)
                .withAssigneeUserId(assigneeUserId)
                .withAssigneeOrganisationId(assigneeOrgId)
                .build();

        when(envelopeRemoveCaseAssignment.payload()).thenReturn(caseAssignmentToAdvocateRemoved);

        ProsecutionOrganisationAccess prosecutionOrganisation = new ProsecutionOrganisationAccess();
        prosecutionOrganisation.setId(new ProsecutionOrganisationCaseKey(caseId, assigneeOrgId));
        prosecutionOrganisation.setAssignedDate(ZonedDateTime.now());
        prosecutionOrganisation.getProsecutionAdvocatesWithAccess().add(getProsecutionAdvocates(caseId, assigneeUserId, assigneeOrgId));

        when(organisationAccessRepository.findBy(prosecutionOrganisationCaseKeyCaptor.capture())).thenReturn(prosecutionOrganisation);

        advocateAccessEventListener.prosecutionRemoveCaseAssignmentToAdvocateReceived(envelopeRemoveCaseAssignment);

        verify(organisationAccessRepository, times(1)).remove(prosecutionOrganisationAccessCaptor.capture());
        ProsecutionOrganisationAccess updatedProsecutionOrganisationAccess = prosecutionOrganisationAccessCaptor.getValue();
        assertThat(updatedProsecutionOrganisationAccess.getProsecutionAdvocatesWithAccess().size(), is(0));
        assertThat(updatedProsecutionOrganisationAccess.getProsecutionAdvocatesWithAccess().stream().noneMatch(paa -> paa.getAssigneeDetails().getUserId().equals(assigneeUserId)), is(true));
    }

    @Test
    public void givenNoPreviousCaseAssignmentsFoundForAssigneeOrganisation_shouldCreateCaseAssignmentsForTheAssigneeOrganisationFirstAndThenAdvocateAssignment() {
        final UUID caseId = randomUUID();
        final ZonedDateTime assignmentTimestamp = ZonedDateTime.now();
        final int expiryHours = 2;
        final CasesAssignedToAdvocate casesAssignedToAdvocate = getCasesAssignedToAdvocate(caseId, assignmentTimestamp);

        when(applicationParameters.getAssignmentExpiryHours()).thenReturn(expiryHours);
        when(casesEnvelope.payload()).thenReturn(casesAssignedToAdvocate);
        when(prosecutionOrganisationService.updateOrSave(eq(caseId), any(), any(), any(), any(), any(), any())).thenReturn(prosecutionOrganisationAccess);

        advocateAccessEventListener.prosecutionCasesAssignmentToAdvocateReceived(casesEnvelope);

        verify(advocateAssignmentRepository).save(prosecutionAdvocateAccessCaptor.capture());
        ProsecutionAdvocateAccess actualAdvocateAssignment = prosecutionAdvocateAccessCaptor.getValue();
        assertProsecutionAdvocateAccess(casesAssignedToAdvocate.getAssigneeDetails(), casesAssignedToAdvocate.getAssignorDetails(),
                casesAssignedToAdvocate.getAssignmentTimestamp(), prosecutionOrganisationAccess, actualAdvocateAssignment);
        assertThat(actualAdvocateAssignment.getAssignmentExpiryDate(), is(assignmentTimestamp.plusHours(expiryHours)));
    }

    @Test
    public void shouldNotUpdateProsecutionAdvocateAccessWhenNoExistingExpiryDate() {
        final UUID caseId = randomUUID();
        final ZonedDateTime assignmentTimestamp = ZonedDateTime.now();
        final CasesAssignedToAdvocate casesAssignedToAdvocate = getCasesAssignedToAdvocate(caseId, assignmentTimestamp);
        final ProsecutionAdvocateAccess savedEntity = new ProsecutionAdvocateAccess();

        when(casesEnvelope.payload()).thenReturn(casesAssignedToAdvocate);
        when(advocateAssignmentRepository.findByCaseIdAndAssigneeId(any(), any())).thenReturn(Collections.singletonList(savedEntity));

        advocateAccessEventListener.prosecutionCasesAssignmentToAdvocateReceived(casesEnvelope);

        verify(advocateAssignmentRepository, times(0)).save(any());
    }

    @Test
    public void shouldUpdateProsecutionAdvocateAccessWhenHasExistingExpiryDate() {
        final int expiryHours = 2;
        final UUID caseId = randomUUID();
        final ZonedDateTime assignmentTimestamp = ZonedDateTime.now();
        final CasesAssignedToAdvocate casesAssignedToAdvocate = getCasesAssignedToAdvocate(caseId, assignmentTimestamp);
        final ProsecutionAdvocateAccess savedEntity = new ProsecutionAdvocateAccess();
        savedEntity.setAssignmentExpiryDate(ZonedDateTime.now());
        savedEntity.setAssignorDetails(new AssignmentUserDetails(randomUUID(), randomUUID(), "f", "l"));

        when(casesEnvelope.payload()).thenReturn(casesAssignedToAdvocate);
        when(prosecutionOrganisationService.updateOrSave(eq(caseId), any(), any(), any(), any(), any(), any())).thenReturn(prosecutionOrganisationAccess);
        when(advocateAssignmentRepository.findByCaseIdAndAssigneeId(any(), any())).thenReturn(Collections.singletonList(savedEntity));
        when(applicationParameters.getAssignmentExpiryHours()).thenReturn(expiryHours);

        advocateAccessEventListener.prosecutionCasesAssignmentToAdvocateReceived(casesEnvelope);

        verify(advocateAssignmentRepository).save(prosecutionAdvocateAccessCaptor.capture());
        ProsecutionAdvocateAccess actualAdvocateAssignment = prosecutionAdvocateAccessCaptor.getValue();

        assertThat(actualAdvocateAssignment.getAssignorDetails().getUserId(), is(casesAssignedToAdvocate.getAssignorDetails().getUserId()));
        assertThat(actualAdvocateAssignment.getAssignorDetails().getFirstName(), is(casesAssignedToAdvocate.getAssignorDetails().getFirstName()));
        assertThat(actualAdvocateAssignment.getAssignorDetails().getLastName(), is(casesAssignedToAdvocate.getAssignorDetails().getLastName()));
        assertThat(actualAdvocateAssignment.getAssignorOrganisationId(), is(casesAssignedToAdvocate.getAssignorOrganisation().getOrgId()));
        assertThat(actualAdvocateAssignment.getAssignmentExpiryDate(), is(assignmentTimestamp.plusHours(expiryHours)));
        assertThat(actualAdvocateAssignment.getProsecutionOrganisation(), is(prosecutionOrganisationAccess));
    }

    private ProsecutionAdvocateAccess getProsecutionAdvocates(UUID caseId, UUID assigneeId, UUID assigneeOrgId) {
        ProsecutionAdvocateAccess prosecutionAdvocateAccess = new ProsecutionAdvocateAccess();
        prosecutionAdvocateAccess.setAssigneeDetails(new AssignmentUserDetails(randomUUID(), assigneeId, "f name", "l name"));
        ProsecutionOrganisationAccess prosecutionOrganisation = new ProsecutionOrganisationAccess();
        prosecutionOrganisation.setId(new ProsecutionOrganisationCaseKey(caseId, assigneeOrgId));
        prosecutionAdvocateAccess.setProsecutionOrganisation(prosecutionOrganisation);
        return prosecutionAdvocateAccess;
    }

    private void assertProsecutionOrganisationAccess(final UUID caseId, final Organisation assigneeOrganisation,
                                                     final PersonDetails assigneeDetails, final PersonDetails assignorDetails,
                                                     final String representingOrganisation, final ZonedDateTime assignmentTimestamp,
                                                     final ProsecutionOrganisationAccess actualProsecutionOrganisationAccess) {
        assertThat(actualProsecutionOrganisationAccess.getId().getCaseId(), is(caseId));
        assertThat(actualProsecutionOrganisationAccess.getId().getAssigneeOrganisationId(), is(assigneeOrganisation.getOrgId()));

        assertThat(actualProsecutionOrganisationAccess.getAssigneeDetails().getId(), is(notNullValue()));
        assertThat(actualProsecutionOrganisationAccess.getAssigneeDetails().getUserId(), is(assigneeDetails.getUserId()));
        assertThat(actualProsecutionOrganisationAccess.getAssigneeDetails().getFirstName(), is(assigneeDetails.getFirstName()));
        assertThat(actualProsecutionOrganisationAccess.getAssigneeDetails().getLastName(), is(assigneeDetails.getLastName()));

        assertThat(actualProsecutionOrganisationAccess.getAssignorDetails().getId(), is(notNullValue()));
        assertThat(actualProsecutionOrganisationAccess.getAssignorDetails().getUserId(), is(assignorDetails.getUserId()));
        assertThat(actualProsecutionOrganisationAccess.getAssignorDetails().getFirstName(), is(assignorDetails.getFirstName()));
        assertThat(actualProsecutionOrganisationAccess.getAssignorDetails().getLastName(), is(assignorDetails.getLastName()));

        assertThat(actualProsecutionOrganisationAccess.getAssignedDate(), is(assignmentTimestamp));
        assertThat(actualProsecutionOrganisationAccess.getRepresentationType(), is(RepresentationType.PROSECUTION));
        assertThat(actualProsecutionOrganisationAccess.getRepresenting(), is(representingOrganisation));
    }

    private void assertProsecutionAdvocateAccess(final PersonDetails assigneeDetails, final PersonDetails assignorDetails,
                                                 final ZonedDateTime assignmentTimestamp, final ProsecutionOrganisationAccess actualProsecutionOrganisationAccess,
                                                 final ProsecutionAdvocateAccess actualAdvocateAssignment) {

        assertThat(actualAdvocateAssignment.getProsecutionOrganisation(), is(actualProsecutionOrganisationAccess));

        assertThat(actualAdvocateAssignment.getAssigneeDetails().getId(), is(notNullValue()));
        assertThat(actualAdvocateAssignment.getAssigneeDetails().getUserId(), is(assigneeDetails.getUserId()));
        assertThat(actualAdvocateAssignment.getAssigneeDetails().getFirstName(), is(assigneeDetails.getFirstName()));
        assertThat(actualAdvocateAssignment.getAssigneeDetails().getLastName(), is(assigneeDetails.getLastName()));

        assertThat(actualAdvocateAssignment.getAssignorDetails().getId(), is(notNullValue()));
        assertThat(actualAdvocateAssignment.getAssignorDetails().getUserId(), is(assignorDetails.getUserId()));
        assertThat(actualAdvocateAssignment.getAssignorDetails().getFirstName(), is(assignorDetails.getFirstName()));
        assertThat(actualAdvocateAssignment.getAssignorDetails().getLastName(), is(assignorDetails.getLastName()));

        assertThat(actualAdvocateAssignment.getAssignedDate(), is(assignmentTimestamp));

    }

    private CaseAssignedToAdvocate getCaseAssignedToAdvocate() {
        return CaseAssignedToAdvocate.caseAssignedToAdvocate()
                .withCaseId(randomUUID())
                .withAssignorDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("aFirstName").withLastName("aLastName").build())
                .withAssignorOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("oName").build())
                .withAssigneeDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("bFirstName").withLastName("bLastName").build())
                .withAssigneeOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("ooName").build())
                .withAssignmentTimestamp(ZonedDateTime.now())
                .withRepresentingOrganisation("CPS")
                .build();
    }

    private CaseAssignedToAdvocate getCaseAssignedToAdvocateNonCps() {
        return CaseAssignedToAdvocate.caseAssignedToAdvocate()
                .withCaseId(randomUUID())
                .withAssignorDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("aFirstName").withLastName("aLastName").build())
                .withAssignorOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("oName").build())
                .withAssigneeDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("bFirstName").withLastName("bLastName").build())
                .withAssigneeOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("ooName").build())
                .withAssignmentTimestamp(ZonedDateTime.now())
                .withRepresentingOrganisation("DVLA")
                .build();
    }

    private CasesAssignedToAdvocate getCasesAssignedToAdvocate(final UUID caseId, final ZonedDateTime assignmentTimestamp) {
        return CasesAssignedToAdvocate.casesAssignedToAdvocate()
                .withCaseHearingAssignments(of(CaseHearingAssignments.caseHearingAssignments().withCaseId(caseId).withHearingId(randomUUID()).build()))
                .withAssignorDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("aFirstName").withLastName("aLastName").build())
                .withAssignorOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("oName").build())
                .withAssigneeDetails(PersonDetails.personDetails().withUserId(randomUUID()).withFirstName("bFirstName").withLastName("bLastName").build())
                .withAssigneeOrganisation(Organisation.organisation().withOrgId(randomUUID()).withOrganisationName("ooName").build())
                .withAssignmentTimestamp(assignmentTimestamp)
                .withRepresentingOrganisation("CPS")
                .build();
    }

}