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

import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@ServiceComponent(Component.COMMAND_HANDLER)
public class AssociateOrphanedCaseHandler {

    private static final String LAA_CONTRACT_NUMBER = "laaContractNumber";
    private static final String ORGANISATION_ID = "organisationId";
    private static final String ORGANISATION_NAME = "organisationName";
    private static final String DEFENDANT_ID = "defendantId";

    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;

    @Handles("defence.command.handler.associate-orphaned-case")
    public void handleAssociateOrphanedCase(final JsonEnvelope envelope) throws EventStreamException {
        final String userId = envelope.metadata().userId()
                .orElseThrow(() -> new IllegalArgumentException("No UserId Supplied"));
        final JsonObject payload = envelope.payloadAsJsonObject();
        final String laaContractNumber = payload.getString(LAA_CONTRACT_NUMBER);
        final UUID organisationId = fromString(payload.getString(ORGANISATION_ID));
        final String organisationName = payload.getString(ORGANISATION_NAME);
        final UUID defendantId = fromString(payload.getString(DEFENDANT_ID));


        final EventStream eventStream = eventSource.getStreamById(defendantId);
        final DefenceAssociation defenceAssociationAggregate = aggregateService.get(eventStream, DefenceAssociation.class);

        final Stream<Object> events =
                defenceAssociationAggregate.handleOrphanedDefendantAssociation(organisationId, organisationName, defendantId, laaContractNumber, userId);
        appendEventsToStream(envelope, eventStream, events);

    }
}
