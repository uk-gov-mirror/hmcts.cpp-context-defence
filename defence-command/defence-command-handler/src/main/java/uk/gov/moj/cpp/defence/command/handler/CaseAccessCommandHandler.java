package uk.gov.moj.cpp.defence.command.handler;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.eventsourcing.source.core.Events.streamOf;
import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.cps.defence.commands.AssignCase;
import uk.gov.justice.cps.defence.commands.AssignCaseByHearing;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.CaseAssignmentDetails;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.aggregate.Advocate;
import uk.gov.moj.cpp.defence.events.SystemScheduledForAdvocateAccessTriggered;
import uk.gov.moj.cpp.defence.service.DefenceService;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.defence.domain.common.pojo.CaseHearingAssignmentDetails;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@ServiceComponent(Component.COMMAND_HANDLER)
public class CaseAccessCommandHandler {

    static final String CASE_ID = "caseId";
    static final String ASSIGNEE_USER_ID = "assigneeUserId";
    public static final String IS_AUTOMATIC_UNASSIGNMENT = "isAutomaticUnassignment";
    public static final String CPS = "CPS";

    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;

    @Inject
    private UserGroupService usersGroupService;

    @Inject
    private Requester requester;

    @Inject
    private DefenceService defenceService;

    @Handles("defence.command.handler.advocate.assign-case")
    public void assignCase(final Envelope<AssignCase> envelope) throws EventStreamException {

        final AssignCase assignCase = envelope.payload();
        EventStream eventStream;
        List<String> assigneeGroupList = null;
        Organisation assigneeOrganisation = null;
        PersonDetails assignorDetails = null;
        Organisation assignorOrganisation = null;
        final String assigneeEmailId = assignCase.getAssigneeEmailId().trim();

        final PersonDetails assigneeDetails = usersGroupService.getUserDetailsWithEmail(assigneeEmailId, envelope.metadata(), requester);
        if (assigneeDetails != null) {
            eventStream = eventSource.getStreamById(assigneeDetails.getUserId());
            assigneeGroupList = usersGroupService.getGroupNamesForUser(assigneeDetails.getUserId(), envelope.metadata(), requester);
            assigneeOrganisation = usersGroupService.getOrganisationDetailsForUser(assigneeDetails.getUserId(), envelope.metadata(), requester);

            assignorDetails = usersGroupService.getUserDetailsWithUserId(assignCase.getAssignorId(), envelope.metadata(), requester);
            assignorOrganisation = usersGroupService.getOrganisationDetailsForUser(assignCase.getAssignorId(), envelope.metadata(), requester);

        } else {
            eventStream = eventSource.getStreamById(randomUUID());
        }
        final Advocate advocateAggregate = aggregateService.get(eventStream, Advocate.class);
        for (final CaseAssignmentDetails caseAssignmentDetails : assignCase.getCaseAssignmentDetails()) {
            final String representingOrganisation = isNull(caseAssignmentDetails.getRepresentingOrganisation())  ?  CPS : caseAssignmentDetails.getRepresentingOrganisation() ;
            final Stream<Object> events = advocateAggregate.assignCase(assigneeEmailId, assigneeDetails,
                    assigneeOrganisation, assignorOrganisation,
                    assignorDetails, caseAssignmentDetails.getCaseId(), assigneeGroupList,
                    caseAssignmentDetails.getIsAssigneeDefendingCase(), caseAssignmentDetails.getProsecutionAuthorityId(),
                    caseAssignmentDetails.getIsCps(), caseAssignmentDetails.getIsPolice(), representingOrganisation);
            appendEventsToStream(envelope, eventStream, events);

        }
    }

    @Handles("defence.command.handler.advocate.remove-case-assignment")
    public void removeCaseAssignment(final JsonEnvelope envelope) throws EventStreamException {

        final JsonObject payload = envelope.payloadAsJsonObject();
        final UUID caseId = fromString(payload.getString(CASE_ID));
        final UUID assigneeUserId = fromString(payload.getString(ASSIGNEE_USER_ID));
        final boolean isAutomaticUnAssignment = payload.containsKey(IS_AUTOMATIC_UNASSIGNMENT) && payload.getBoolean(IS_AUTOMATIC_UNASSIGNMENT);

        final String removedByUserId = envelope.metadata().userId()
                .orElseThrow(() -> new IllegalArgumentException("No UserId Supplied"));

        final EventStream eventStream = eventSource.getStreamById(assigneeUserId);
        final Advocate advocateAggregate = aggregateService.get(eventStream, Advocate.class);

        final List<String> assigneeGroupList = usersGroupService.getGroupNamesForUser(assigneeUserId, envelope.metadata(), requester);
        final boolean hasAdvocatesAssignedToTheCase = defenceService.hasAdvocatesAssignedToTheCase(envelope, caseId.toString(), advocateAggregate.getAssigneeOrganisationId().toString());

        final Stream<Object> events = advocateAggregate.removeCaseAssignment(caseId, assigneeUserId, assigneeGroupList, hasAdvocatesAssignedToTheCase, fromString(removedByUserId), isAutomaticUnAssignment);
        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("defence.command.handler.system-schedule-advocate-access")
    public void systemScheduleForAdvocateAccess(final JsonEnvelope envelope) throws EventStreamException {
        final SystemScheduledForAdvocateAccessTriggered systemScheduledForAdvocateAccessTriggered =  SystemScheduledForAdvocateAccessTriggered.systemScheduledForAdvocateAccessTriggered().build();
        final Stream<Object> events = streamOf(systemScheduledForAdvocateAccessTriggered);
        final UUID streamId = randomUUID();
        final EventStream eventStream = eventSource.getStreamById(streamId);
        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("defence.command.handler.advocate.assign-case-by-hearing-listing")
    public void assignCaseByHearingListing(final Envelope<AssignCaseByHearing> envelope) throws EventStreamException {
        final AssignCaseByHearing assignCaseByHearing = envelope.payload();
        final Optional<uk.gov.moj.cpp.defence.CaseHearingAssignmentDetails> caseHearingAssignmentDetails = assignCaseByHearing.getCaseHearingAssignmentDetails().stream().findFirst();
        final String representingOrganisation = caseHearingAssignmentDetails.isPresent() && nonNull(caseHearingAssignmentDetails.get().getRepresentingOrganisation()) ? caseHearingAssignmentDetails.get().getRepresentingOrganisation() : CPS;
        EventStream eventStream;
        List<String> assigneeGroupList = emptyList();
        Organisation assigneeOrganisation = null;

        final PersonDetails assigneeDetails = usersGroupService.getUserDetailsWithEmail(assignCaseByHearing.getAssigneeEmailId(), envelope.metadata(), requester);

        if (nonNull(assigneeDetails)) {
            eventStream = eventSource.getStreamById(assigneeDetails.getUserId());
            assigneeGroupList = usersGroupService.getGroupNamesForUser(assigneeDetails.getUserId(), envelope.metadata(), requester);
            assigneeOrganisation = usersGroupService.getOrganisationDetailsForUser(assigneeDetails.getUserId(), envelope.metadata(), requester);
        } else {
            eventStream = eventSource.getStreamById(randomUUID());
        }
        final PersonDetails assignorDetails = usersGroupService.getUserDetailsWithUserId(assignCaseByHearing.getAssignorId(), envelope.metadata(), requester);
        final Organisation assignorOrganisation = usersGroupService.getOrganisationDetailsForUser(assignCaseByHearing.getAssignorId(), envelope.metadata(), requester);

        final Advocate advocateAggregate = aggregateService.get(eventStream, Advocate.class);
        final Stream<Object> events = advocateAggregate.assignCaseHearing(assignCaseByHearing.getAssigneeEmailId(),
                assigneeDetails, assigneeOrganisation, assigneeGroupList, assignorDetails, assignorOrganisation,
                toCaseHearingAssignmentDetails(assignCaseByHearing.getCaseHearingAssignmentDetails()), representingOrganisation);
        appendEventsToStream(envelope, eventStream, events);

    }

    private List<CaseHearingAssignmentDetails> toCaseHearingAssignmentDetails(List<uk.gov.moj.cpp.defence.CaseHearingAssignmentDetails> caseHearingAssignmentDetails) {
        return caseHearingAssignmentDetails.stream()
                .map(chAssignment -> new CaseHearingAssignmentDetails(chAssignment.getAssigneeId(), chAssignment.getCaseId(),
                        chAssignment.getHearingId(), chAssignment.getIsAssigneeDefendingCase(),
                        chAssignment.getIsCps(), chAssignment.getIsPolice(), chAssignment.getProsecutionAuthorityId(),
                        chAssignment.getFailureReason(), chAssignment.getErrorCode()))
                .collect(Collectors.toList());
    }

}

