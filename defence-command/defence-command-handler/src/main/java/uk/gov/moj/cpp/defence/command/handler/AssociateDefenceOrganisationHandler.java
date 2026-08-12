package uk.gov.moj.cpp.defence.command.handler;

import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

import uk.gov.justice.cps.defence.UnlockDefenceOrganisationAssociation;
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
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.progression.command.AssociateDefenceOrganisation;

import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(Component.COMMAND_HANDLER)
public class AssociateDefenceOrganisationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssociateDefenceOrganisationHandler.class);
    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Inject
    private EventSource eventSource;
    @Inject
    private UserGroupService usersGroupService;
    @Inject
    private AggregateService aggregateService;
    @Inject
    private Requester requester;



    @Handles("defence.command.associate-defence-organisation")
    public void handleAssociateDefenceOrganisation(final Envelope<AssociateDefenceOrganisation> envelope) throws EventStreamException {
        JsonObject jsonObject = objectToJsonObjectConverter.convert(envelope.payload());
        LOGGER.info("Inside handleAssociateDefenceOrganisation={}", jsonObject != null ? jsonObject.toString(): "");
        final AssociateDefenceOrganisation associateDefenceOrganisation = envelope.payload();

        final String userId = envelope.metadata().userId()
                .orElseThrow(() -> new IllegalArgumentException("No UserId Supplied"));

        final OrganisationDetails userOrgDetails = usersGroupService.getUserOrgDetails(envelope,requester);

        final EventStream eventStream = eventSource.getStreamById(associateDefenceOrganisation.getDefendantId());
        final DefenceAssociation defenceAssociationAggregate = aggregateService.get(eventStream, DefenceAssociation.class);
        final Stream<Object> events =
                defenceAssociationAggregate.associateOrganisation(associateDefenceOrganisation.getDefendantId(),
                        userOrgDetails.getId(),
                        userOrgDetails.getName(),
                        associateDefenceOrganisation.getRepresentationType().toString(),
                        associateDefenceOrganisation.getLaaContractNumber(),
                        userId);
        appendEventsToStream(envelope, eventStream, events);
    }


    @Handles("defence.bdf.command.unlock-defence-organisation-association")
    public void bdfHandleUnlockDefenceOrganisationAssociation(final Envelope<UnlockDefenceOrganisationAssociation> envelope) throws EventStreamException {
        final UnlockDefenceOrganisationAssociation unlockDefenceOrganisationAssociation = envelope.payload();
        final EventStream eventStream = eventSource.getStreamById(unlockDefenceOrganisationAssociation.getDefendantId());

        final DefenceAssociation defenceAssociationAggregate = aggregateService.get(eventStream, DefenceAssociation.class);
        final Stream<Object> events =
                defenceAssociationAggregate.unlockDefenceOrganisationAssociationBDF(unlockDefenceOrganisationAssociation.getDefendantId(),
                        unlockDefenceOrganisationAssociation.getOrganisationId());
        appendEventsToStream(envelope, eventStream, events);
    }

}
