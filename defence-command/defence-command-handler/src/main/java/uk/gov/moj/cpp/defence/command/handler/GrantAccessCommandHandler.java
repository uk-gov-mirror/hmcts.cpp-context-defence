package uk.gov.moj.cpp.defence.command.handler;

import static java.util.UUID.fromString;
import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.aggregate.DefenceClient;
import uk.gov.moj.cpp.defence.commands.GrantDefenceAccess;
import uk.gov.moj.cpp.defence.commands.RemoveAllGrantDefenceAccess;
import uk.gov.moj.cpp.defence.commands.RemoveGrantDefenceAccess;
import uk.gov.moj.cpp.defence.service.DefenceService;
import uk.gov.moj.cpp.defence.service.UserGroupService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;

@ServiceComponent(Component.COMMAND_HANDLER)
public class GrantAccessCommandHandler {

    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;

    @Inject
    private UserGroupService usersGroupService;

    @Inject
    private DefenceService defenceService;

    @Inject
    private Requester requester;


    @Handles("defence.command.grant-defence-access")
    public void receiveGrantDefenceAccess(final Envelope<GrantDefenceAccess> envelope) throws EventStreamException {

        final GrantDefenceAccess grantDefenceAccess = envelope.payload();
        final UUID defenceClientId = grantDefenceAccess.getDefenceClientId();
        final String granteeEmail = grantDefenceAccess.getGranteeEmailId().trim();

        final UUID granterUserId = fromString(envelope.metadata().userId().orElse(null));

        final EventStream eventStream = eventSource.getStreamById(defenceClientId);
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStream, DefenceClient.class);


        List<String> granteeGroupList = null;
        List<String> granterGroupList = null;
        Organisation granteeOrganisation = null;
        PersonDetails granterDetails = null;
        Organisation granterOrganisation = null;
        UUID associatedOrganisationId = null;
        boolean isInProsecutorRole = false;

        final PersonDetails granteeDetails = usersGroupService.getUserDetailsWithEmail(granteeEmail, envelope.metadata(), requester);
        if (granteeDetails != null) {
            isInProsecutorRole = defenceService.isInProsecutorRole(envelope, defenceClientAggregate.getCaseId(), granteeDetails.getUserId());
            granteeGroupList = usersGroupService.getGroupNamesForUser(granteeDetails.getUserId(), envelope.metadata(), requester);
            granterDetails = usersGroupService.getUserDetailsWithUserId(granterUserId, envelope.metadata(), requester);
            granterGroupList = usersGroupService.getGroupNamesForUser(granterDetails.getUserId(), envelope.metadata(), requester);
            granteeOrganisation = usersGroupService.getOrganisationDetailsForUser(granteeDetails.getUserId(), envelope.metadata(), requester);

            granterOrganisation = usersGroupService.getOrganisationDetailsForUser(granterUserId, envelope.metadata(), requester);
            associatedOrganisationId = getAssociatedOrganisationId(defenceClientAggregate.getDefendantId());
        }

        final Stream<Object> events = defenceClientAggregate.grantAccessToUser(defenceClientId, granteeEmail, granteeDetails, granteeGroupList, granterGroupList, granteeOrganisation, granterOrganisation, granterDetails, associatedOrganisationId, isInProsecutorRole);

        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("defence.command.remove-grant-defence-access")
    public void receiveRemoveGrantDefenceAccess(final Envelope<RemoveGrantDefenceAccess> envelope) throws EventStreamException {

        final RemoveGrantDefenceAccess removeGrantDefenceAccess = envelope.payload();
        final UUID defenceClientId = removeGrantDefenceAccess.getDefenceClientId();
        final UUID granteeUserId = removeGrantDefenceAccess.getGranteeUserId();
        final UUID loggedInUserId = fromString(envelope.metadata().userId().orElse(null));


        final EventStream eventStream = eventSource.getStreamById(defenceClientId);
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStream, DefenceClient.class);


        final UUID associatedOrganisationId = getAssociatedOrganisationId(defenceClientAggregate.getDefendantId());
        final Organisation loggedInUserOrganisation = usersGroupService.getOrganisationDetailsForUser(loggedInUserId, envelope.metadata(), requester);
        final Organisation granteeOrganisation = usersGroupService.getOrganisationDetailsForUser(granteeUserId, envelope.metadata(), requester);
        final List<String> loggedInUserGroupList = usersGroupService.getGroupNamesForUser(loggedInUserId, envelope.metadata(), requester);

        final Stream<Object> events = defenceClientAggregate.removeGrantAccessToUser(granteeUserId, loggedInUserId, associatedOrganisationId, loggedInUserOrganisation, granteeOrganisation, loggedInUserGroupList);

        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("defence.command.remove-all-grant-defence-access")
    public void receiveRemoveAllGrantDefenceAccess(final Envelope<RemoveAllGrantDefenceAccess> envelope) throws EventStreamException {

        final RemoveAllGrantDefenceAccess removeAllGrantDefenceAccess = envelope.payload();

        final EventStream eventStream = eventSource.getStreamById(removeAllGrantDefenceAccess.getDefenceClientId());
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStream, DefenceClient.class);

        final Stream<Object> events = defenceClientAggregate.removeAllGrantees();

        appendEventsToStream(envelope, eventStream, events);
    }

    private UUID getAssociatedOrganisationId(final UUID defendantID) {
        final EventStream associationEventStream = eventSource.getStreamById(defendantID);
        final DefenceAssociation defenceAssociationAggregate = aggregateService.get(associationEventStream, DefenceAssociation.class);
        return defenceAssociationAggregate.getAssociatedOrganisationId();
    }


}

