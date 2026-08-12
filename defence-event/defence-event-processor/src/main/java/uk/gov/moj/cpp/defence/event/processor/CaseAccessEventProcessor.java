package uk.gov.moj.cpp.defence.event.processor;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.moj.cpp.defence.event.helper.ProcessorHelper.sendPublicEvent;

import uk.gov.justice.cps.defence.AssigneeForProsecutionIsDefendingCase;
import uk.gov.justice.cps.defence.AssigneeNotInAllowedGroups;
import uk.gov.justice.cps.defence.CaseAssignmentsByHearingListingFailed;
import uk.gov.justice.cps.defence.UserAlreadyAssigned;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.events.CaseAssigmentToOrganisationRemoved;
import uk.gov.moj.cpp.defence.events.CaseAssignedToAdvocate;
import uk.gov.moj.cpp.defence.events.CaseAssignedToOrganisation;
import uk.gov.moj.cpp.defence.events.CaseAssignmentToAdvocateRemoved;
import uk.gov.moj.cpp.defence.events.CasesAssignedToAdvocate;
import uk.gov.moj.cpp.defence.events.CasesAssignedToOrganisation;
import uk.gov.moj.cpp.defence.event.service.AdvocateAccessScheduledService;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.moj.cpp.defence.events.SystemScheduledForAdvocateAccessTriggered;

@ServiceComponent(EVENT_PROCESSOR)
public class CaseAccessEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseAccessEventProcessor.class);

    public static final String PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR = "public.defence.event.case-assigned-to-prosecutor";
    public static final String PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENT_TO_PROSECUTOR_REMOVED = "public.defence.event.case-assignment-to-prosecutor-removed";
    public static final String PUBLIC_DEFENCE_EVENT_ASSIGNEE_NOT_IN_ALLOWED_GROUPS = "public.defence.event.assignee-not-in-allowed-groups";
    public static final String PUBLIC_DEFENCE_EVENT_USER_ALREADY_ASSIGNED = "public.defence.event.user-already-assigned";
    public static final String PUBLIC_DEFENCE_EVENT_ASSIGNEE_FOR_PROSECUTION_IS_DEFENDING_CASE = "public.defence.event.assignee-for-prosecution-is-defending-case";
    public static final String PUBLIC_DEFENCE_EVENT_CASES_ASSIGNED_TO_PROSECUTOR = "public.defence.event.cases-assigned-to-prosecutor";
    public static final String PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENTS_FAILED = "public.defence.event.case-assignments-failed";
    @Inject
    private Sender sender;

    @Inject
    private AdvocateAccessScheduledService advocateAccessScheduledService;

    @Handles("defence.events.case-assigned-to-advocate")
    public void handleCaseAssignedToAdvocate(final Envelope<CaseAssignedToAdvocate> envelope) {
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.events.case-assigned-to-organisation")
    public void handleCaseAssignedToOrganisation(final Envelope<CaseAssignedToOrganisation> envelope) {
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.events.case-assignment-to-advocate-removed")
    public void handleCaseAssignmentToAdvocateRemoved(final Envelope<CaseAssignmentToAdvocateRemoved> envelope) {
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENT_TO_PROSECUTOR_REMOVED, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.events.case-assignment-to-organisation-removed")
    public void handleCaseAssignmentToOrganisationRemoved(final Envelope<CaseAssigmentToOrganisationRemoved> envelope) {
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENT_TO_PROSECUTOR_REMOVED, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.events.cases-assigned-to-advocate")
    public void handleCasesAssignedToAdvocate(final Envelope<CasesAssignedToAdvocate> envelope) {

        LOGGER.info("Inside handleCasesAssignedToAdvocate={}", envelope.payload());
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_CASES_ASSIGNED_TO_PROSECUTOR, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.events.cases-assigned-to-organisation")
    public void handleCasesAssignedToOrganisation(final Envelope<CasesAssignedToOrganisation> envelope) {
        LOGGER.info("Inside handleCasesAssignedToOrganisation={}", envelope.payload());
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_CASES_ASSIGNED_TO_PROSECUTOR, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.event.case-assignments-by-hearing-listing-failed")
    public void handleCaseAssignmentsByHearingListingFailed(final Envelope<CaseAssignmentsByHearingListingFailed> envelope) {
        LOGGER.info("Inside handleCaseAssignmentsByHearingListingFailed={}", envelope.payload());
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENTS_FAILED, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.event.assignee-not-in-allowed-groups")
    public void handleAssigneeNotInAllowedGroup(final Envelope<AssigneeNotInAllowedGroups> envelope) {
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_ASSIGNEE_NOT_IN_ALLOWED_GROUPS, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.event.user-already-assigned")
    public void handleUserAlreadyAssigned(final Envelope<UserAlreadyAssigned> envelope) {
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_USER_ALREADY_ASSIGNED, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.event.assignee-for-prosecution-is-defending-case")
    public void handleAssigneeIsDefendingTheCaseEvent(final Envelope<AssigneeForProsecutionIsDefendingCase> envelope) {
        sendPublicEvent(PUBLIC_DEFENCE_EVENT_ASSIGNEE_FOR_PROSECUTION_IS_DEFENDING_CASE, metadataFrom(envelope.metadata()).build(), envelope.payload(), sender);
    }

    @Handles("defence.event.system-scheduled-for-advocate-access-triggered")
    public void handleSystemScheduledForAdvocateAccessTriggered(final Envelope<SystemScheduledForAdvocateAccessTriggered> envelope) {
        advocateAccessScheduledService.unassignExpiredAssignments();
    }


}
