package uk.gov.moj.cpp.defence.command.handler;

import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.service.UserGroupService;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.UUID.fromString;
import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

@ServiceComponent(Component.COMMAND_HANDLER)
public class RecordDefendantLegalStatusUpdated {


    private static final String DEFENDANT_ID = "defendantId";
    private static final String LAA_CONTRACT_NUMBER = "laaContractNumber";
    private static final String LEGAL_AID_STATUS = "legalAidStatus";

    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;

    @Inject
    private UserGroupService userGroupService;

    @Inject
    private Requester requester;



    @Handles("defence.command.record-defendant-legal-status-updated")
    public void recordDefendantLegalStatusUpdated(final JsonEnvelope envelope) throws EventStreamException {
        final JsonObject payload = envelope.payloadAsJsonObject();
        final UUID defendantId = fromString(payload.getString(DEFENDANT_ID));
        final String laaContractNumber = payload.getString(LAA_CONTRACT_NUMBER, null);
        final String legalStatusUpdated = payload.getString(LEGAL_AID_STATUS);
        UUID orgId = null;
        if( laaContractNumber != null ) {
            orgId = userGroupService.getOrganisationByLaaReference(envelope, requester, laaContractNumber).getId();
        }
        final EventStream eventStream = eventSource.getStreamById(defendantId);
        final DefenceAssociation defenceAssociationAggregate = aggregateService.get(eventStream, DefenceAssociation.class);
        final Stream<Object> events =
                defenceAssociationAggregate.recordLegalStatusForDefendant(defendantId, orgId, legalStatusUpdated);
        appendEventsToStream(envelope, eventStream, events);
    }

}
