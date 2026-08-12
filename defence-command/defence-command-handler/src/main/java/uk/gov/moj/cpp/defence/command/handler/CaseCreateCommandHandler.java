package uk.gov.moj.cpp.defence.command.handler;

import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

import uk.gov.justice.cps.defence.AssociateOrganisationBdf;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.aggregate.CaseDefenceClientMap;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociatedBdf;
import uk.gov.moj.cpp.progression.json.schema.event.ProsecutionCaseCreatedBdf;

import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@ServiceComponent(Component.COMMAND_HANDLER)
public class CaseCreateCommandHandler {

    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Handles("defence.command.prosecution-case-created-bdf")
    public void caseCreatedBdf(final Envelope<ProsecutionCaseCreatedBdf> envelope) throws EventStreamException {

        final EventStream eventStream = eventSource.getStreamById(envelope.payload().getProsecutionCaseId());
        final CaseDefenceClientMap caseDefenceClientMap = aggregateService.get(eventStream, CaseDefenceClientMap.class);
        final Stream<Object> events = caseDefenceClientMap.createCaseBdf(envelope.payload().getProsecutionCaseId(), envelope.payload().getDefendantId());
        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("defence.command.associate-organisation-bdf")
    public void handleAssociateOrganisationBdf(final Envelope<AssociateOrganisationBdf> envelope) throws EventStreamException {
        final AssociateOrganisationBdf payload = envelope.payload();

        final EventStream eventStream = eventSource.getStreamById(payload.getDefendantId());
        final JsonObject jsonEvent = objectToJsonObjectConverter.convert(payload);
        final DefenceOrganisationAssociatedBdf defenceOrganisationAssociatedBdf = jsonObjectToObjectConverter.convert(jsonEvent, DefenceOrganisationAssociatedBdf.class);
        final Stream<Object> events = Stream.builder().add(defenceOrganisationAssociatedBdf).build();

        appendEventsToStream(envelope, eventStream, events);
    }
}
