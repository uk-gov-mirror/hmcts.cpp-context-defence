package uk.gov.moj.cpp.defence.command.handler;

import static java.lang.String.format;
import static java.util.UUID.fromString;
import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.OrganisationDetails;
import uk.gov.moj.cpp.defence.UsergroupDetails;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.progression.command.DisassociateDefenceOrganisation;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(Component.COMMAND_HANDLER)
public class DisassociateDefenceOrganisationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisassociateDefenceOrganisationHandler.class);
    private static final List<String> allowedGroupsForHMCTSGroups = Arrays.asList("Court Clerks", "Court Administrators", "Crown Court Admin", "Listing Officers", "Legal Advisers");
    private static final String SYSTEM_USER_GROUP =  "System Users";

    @Inject
    private EventSource eventSource;
    @Inject
    private AggregateService aggregateService;
    @Inject
    private UserGroupService usersGroupService;
    @Inject
    private Requester requester;
    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Handles("defence.command.disassociate-defence-organisation")
    public void handleDisassociateDefenceOrganisation(final Envelope<DisassociateDefenceOrganisation> envelope) throws EventStreamException {
        JsonObject jsonObject = objectToJsonObjectConverter.convert(envelope.payload());
        LOGGER.info("Inside handleDisassociateDefenceOrganisation={}", jsonObject != null ? jsonObject.toString(): "");
        final DisassociateDefenceOrganisation disassociateDefenceOrganisation = envelope.payload();
        final UUID defendantId = disassociateDefenceOrganisation.getDefendantId();

        final EventStream eventStream = eventSource.getStreamById(defendantId);
        final DefenceAssociation defenceAssociationAggregate = aggregateService.get(eventStream, DefenceAssociation.class);

        final String userId = envelope.metadata().userId().orElse(null);
        final OrganisationDetails userOrgDetails = usersGroupService.getUserOrgDetails(envelope, requester);

        Stream<Object> events = null;

        if (!isValidDisassociationCommand(disassociateDefenceOrganisation.getOrganisationId(), userOrgDetails.getId(), envelope)) {
            events = defenceAssociationAggregate.raiseDisassociationFailedEvent(defendantId, format("The given Organisation is not qualified to perform this disassociation %s", userOrgDetails.getId()));
        } else {
            events = defenceAssociationAggregate.disassociateOrganisation(disassociateDefenceOrganisation.getDefendantId(),
                    disassociateDefenceOrganisation.getOrganisationId(), disassociateDefenceOrganisation.getCaseId(), fromString(userId),  disassociateDefenceOrganisation.getIsLAA());
        }

        appendEventsToStream(envelope, eventStream, events);
    }

    private boolean isValidDisassociationCommand(final UUID organisationId,
                                              final UUID userOrganisationId,
                                              final Envelope<DisassociateDefenceOrganisation> envelope) {
        return isLoggedInUserAssociatedWithDefendant(organisationId, userOrganisationId) || isSystemUser(envelope) || isHMCTSUser(envelope);
    }

    private boolean isHMCTSUser(final Envelope<DisassociateDefenceOrganisation> envelope) {
        final List<UsergroupDetails> retrievedUserGroupDetails = usersGroupService.getUserGroupsForUser(envelope,requester);
        return retrievedUserGroupDetails.stream().anyMatch(userGroupDetails ->
                allowedGroupsForHMCTSGroups.contains(userGroupDetails.getGroupName().trim())
        );
    }

    private boolean isSystemUser(final Envelope<DisassociateDefenceOrganisation> envelope) {
        final List<UsergroupDetails> retrievedUserGroupDetails = usersGroupService.getUserGroupsForUser(envelope,requester);
        return retrievedUserGroupDetails.stream().anyMatch(userGroupDetails ->
                SYSTEM_USER_GROUP.equals(userGroupDetails.getGroupName().trim())
        );

    }

    private boolean isLoggedInUserAssociatedWithDefendant(final UUID organisationId, final UUID userOrganisationId) {
        return organisationId.equals(userOrganisationId) ;
    }
}
