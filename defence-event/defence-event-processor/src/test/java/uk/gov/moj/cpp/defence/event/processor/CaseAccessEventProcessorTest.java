package uk.gov.moj.cpp.defence.event.processor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static uk.gov.moj.cpp.defence.event.helper.EnvelopeHelper.getEnvelope;
import static uk.gov.moj.cpp.defence.event.helper.EnvelopeHelper.verifySendAtIndex;
import static uk.gov.moj.cpp.defence.event.processor.CaseAccessEventProcessor.PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENT_TO_PROSECUTOR_REMOVED;

import uk.gov.justice.cps.defence.AssigneeForProsecutionIsDefendingCase;
import uk.gov.justice.cps.defence.AssigneeNotInAllowedGroups;
import uk.gov.justice.cps.defence.CaseAssignmentsByHearingListingFailed;
import uk.gov.justice.cps.defence.UserAlreadyAssigned;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.event.service.AdvocateAccessScheduledService;
import uk.gov.moj.cpp.defence.events.CaseAssigmentToOrganisationRemoved;
import uk.gov.moj.cpp.defence.events.CaseAssignedToAdvocate;
import uk.gov.moj.cpp.defence.events.CaseAssignedToOrganisation;
import uk.gov.moj.cpp.defence.events.CaseAssignmentToAdvocateRemoved;
import uk.gov.moj.cpp.defence.events.CasesAssignedToAdvocate;
import uk.gov.moj.cpp.defence.events.CasesAssignedToOrganisation;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.defence.events.SystemScheduledForAdvocateAccessTriggered;

import jakarta.inject.Inject;

@ExtendWith(MockitoExtension.class)
public class CaseAccessEventProcessorTest {

    public static final String PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR = "public.defence.event.case-assigned-to-prosecutor";
    @InjectMocks
    private CaseAccessEventProcessor caseAccessEventProcessor;

    @Mock
    private AdvocateAccessScheduledService advocateAccessScheduledService;

    @Mock
    private Sender sender;

    @Captor
    private ArgumentCaptor<Envelope<?>> privateEventCaptor;

    @Test
    public void shouldHandleCaseAssignedToAdvocate() {

        final CaseAssignedToAdvocate caseAssignedToAdvocate = CaseAssignedToAdvocate.caseAssignedToAdvocate().build();
        final Envelope<CaseAssignedToAdvocate> envelope = getEnvelope(caseAssignedToAdvocate, "defence.events.case-assigned-to-advocate");

        caseAccessEventProcessor.handleCaseAssignedToAdvocate(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, 0);
    }

    @Test
    public void shouldHandleCaseAssignedToOrganisation() {

        final CaseAssignedToOrganisation caseAssignedToOrganisation = CaseAssignedToOrganisation.caseAssignedToOrganisation().build();
        final Envelope<CaseAssignedToOrganisation> envelope = getEnvelope(caseAssignedToOrganisation, "defence.events.case-assigned-to-organisation");

        caseAccessEventProcessor.handleCaseAssignedToOrganisation(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, 0);
    }

    @Test
    public void shouldHandleCaseAssignmentToAdvocateRemoved() {

        final CaseAssignmentToAdvocateRemoved caseAssignmentToAdvocateRemoved = CaseAssignmentToAdvocateRemoved.caseAssignmentToAdvocateRemoved().build();
        final Envelope<CaseAssignmentToAdvocateRemoved> envelope = getEnvelope(caseAssignmentToAdvocateRemoved, "defence.events.case-assignment-to-advocate-removed");

        caseAccessEventProcessor.handleCaseAssignmentToAdvocateRemoved(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENT_TO_PROSECUTOR_REMOVED, 0);
    }

    @Test
    public void shouldHandleCaseAssignmentToOrganisationRemoved() {

        final CaseAssigmentToOrganisationRemoved caseAssignedToOrganisation = CaseAssigmentToOrganisationRemoved.caseAssigmentToOrganisationRemoved().build();
        final Envelope<CaseAssigmentToOrganisationRemoved> envelope = getEnvelope(caseAssignedToOrganisation, "defence.events.case-assigned-to-organisation");

        caseAccessEventProcessor.handleCaseAssignmentToOrganisationRemoved(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENT_TO_PROSECUTOR_REMOVED, 0);
    }

    @Test
    public void shouldHandleUserAlreadyAssigned() {

        final UserAlreadyAssigned userAlreadyAssigned = UserAlreadyAssigned.userAlreadyAssigned().build();
        final Envelope<UserAlreadyAssigned> envelope = getEnvelope(userAlreadyAssigned, "defence.event.user-already-assigned");

        caseAccessEventProcessor.handleUserAlreadyAssigned(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, "public.defence.event.user-already-assigned", 0);
    }

    @Test
    public void shouldHandleAssigneeNotInAllowedGroup() {

        final AssigneeNotInAllowedGroups assigneeNotInAllowedGroups = AssigneeNotInAllowedGroups.assigneeNotInAllowedGroups().build();
        final Envelope<AssigneeNotInAllowedGroups> envelope = getEnvelope(assigneeNotInAllowedGroups, "defence.event.assignee-not-in-allowed-groups");

        caseAccessEventProcessor.handleAssigneeNotInAllowedGroup(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, "public.defence.event.assignee-not-in-allowed-groups", 0);
    }

    @Test
    public void shouldHandleAssigneeIsDefendingCase() {

        final AssigneeForProsecutionIsDefendingCase assigneeForProsecutionIsDefendingCase = AssigneeForProsecutionIsDefendingCase.assigneeForProsecutionIsDefendingCase().build();
        final Envelope<AssigneeForProsecutionIsDefendingCase> envelope = getEnvelope(assigneeForProsecutionIsDefendingCase, "defence.event.assignee-not-in-allowed-groups");

        caseAccessEventProcessor.handleAssigneeIsDefendingTheCaseEvent(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, "public.defence.event.assignee-for-prosecution-is-defending-case", 0);
    }

    @Test
    public void shouldHandleCasesAssignedToAdvocate() {

        final CasesAssignedToAdvocate casesAssignedToAdvocate = CasesAssignedToAdvocate.casesAssignedToAdvocate().build();
        final Envelope<CasesAssignedToAdvocate> envelope = getEnvelope(casesAssignedToAdvocate, "defence.events.cases-assigned-to-advocate");

        caseAccessEventProcessor.handleCasesAssignedToAdvocate(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, "public.defence.event.cases-assigned-to-prosecutor", 0);
    }

    @Test
    public void shouldHandleCasesAssignedToOrganisation() {

        final CasesAssignedToOrganisation casesAssignedToOrganisation = CasesAssignedToOrganisation.casesAssignedToOrganisation().build();
        final Envelope<CasesAssignedToOrganisation> envelope = getEnvelope(casesAssignedToOrganisation, "defence.events.cases-assigned-to-organisation");

        caseAccessEventProcessor.handleCasesAssignedToOrganisation(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, "public.defence.event.cases-assigned-to-prosecutor", 0);
    }

    @Test
    public void shouldHandleCaseAssignmentsFailed() {

        final CaseAssignmentsByHearingListingFailed caseAssignmentsByHearingListingFailed = CaseAssignmentsByHearingListingFailed.caseAssignmentsByHearingListingFailed().build();
        final Envelope<CaseAssignmentsByHearingListingFailed> envelope = getEnvelope(caseAssignmentsByHearingListingFailed, "defence.event.case-assignments-by-hearing-listing-failed");

        caseAccessEventProcessor.handleCaseAssignmentsByHearingListingFailed(envelope);
        verify(sender).send(privateEventCaptor.capture());
        final List<Envelope<?>> messageEnvelope = privateEventCaptor.getAllValues();

        assertThat(messageEnvelope, hasSize(1));
        verifySendAtIndex(messageEnvelope, "public.defence.event.case-assignments-failed", 0);
    }

    @Test
    public void shouldHandleSystemScheduledForAdvocateAccessTriggered() {
        final SystemScheduledForAdvocateAccessTriggered systemScheduledForAdvocateAccessTriggered = SystemScheduledForAdvocateAccessTriggered.systemScheduledForAdvocateAccessTriggered().build();
        final Envelope<SystemScheduledForAdvocateAccessTriggered> envelope = getEnvelope(systemScheduledForAdvocateAccessTriggered, "defence.event.system-scheduled-for-advocate-access-triggered");
        caseAccessEventProcessor.handleSystemScheduledForAdvocateAccessTriggered(envelope);
        verify(advocateAccessScheduledService).unassignExpiredAssignments();
    }

}
