package uk.gov.moj.cpp.defence.command.handler;

import static java.util.UUID.fromString;
import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.aggregate.DefenceClient;

import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@ServiceComponent(Component.COMMAND_HANDLER)
public class AssociateDefenceOrganisationForLAAHandler {

    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;



    @Handles("defence.command.associate-defence-organisation-for-laa")
    public void handleAssociateDefenceOrganisationForLaa(final JsonEnvelope envelope) throws EventStreamException {
        final JsonObject payload = envelope.payloadAsJsonObject();
        final String userId = envelope.metadata().userId()
                .orElseThrow(() -> new IllegalArgumentException("No UserId Supplied"));
        final UUID organisationId = fromString(payload.getString("organisationId"));
        final UUID defendantId = fromString(payload.getString("defendantId"));
        final String organisationName = payload.getString("organisationName");
        final String representationType = payload.getString("representationType");
        final String laaContractNumber = payload.getString("laaContractNumber");

        final EventStream eventStreamForDefenceClient = eventSource.getStreamById(defendantId);
        final EventStream eventStreamForAssociation = eventSource.getStreamById(defendantId);
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStreamForDefenceClient, DefenceClient.class);
        final DefenceAssociation defenceAssociationAggregate = aggregateService.get(eventStreamForAssociation, DefenceAssociation.class);

        final UUID caseId = defenceClientAggregate.getCaseId();

        final Stream<Object> events =
                defenceAssociationAggregate.associateOrganisationForRepOrder(defendantId,
                        organisationId,
                        organisationName,
                        representationType,
                        laaContractNumber,
                        userId,
                        caseId
                );
        appendEventsToStream(envelope, eventStreamForAssociation, events);


    }

}
