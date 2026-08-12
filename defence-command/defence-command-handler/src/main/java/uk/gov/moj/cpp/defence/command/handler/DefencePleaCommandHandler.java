package uk.gov.moj.cpp.defence.command.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.cps.defence.AddOffencePleas;
import uk.gov.justice.cps.defence.OffencePleaDetails;
import uk.gov.justice.cps.defence.PleasAllocationDetails;
import uk.gov.justice.cps.defence.UpdateOffencePleas;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.aggregate.DefenceClient;
import uk.gov.moj.cpp.defence.aggregate.DefencePleaAggregate;

import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

@ServiceComponent(COMMAND_HANDLER)
public class DefencePleaCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefencePleaCommandHandler.class.getName());

    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;

    @Handles("defence.command.add-offence-pleas")
    public void createPlea(final Envelope<AddOffencePleas> envelope) throws EventStreamException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("defence.command.add-offence-pleas command received {}", envelope.payload());
        }
        final AddOffencePleas addOffencePlea = envelope.payload();
        final EventStream defenceClientEventStream = eventSource.getStreamById(addOffencePlea.getPleasAllocation().getDefendantId());
        final EventStream defencePleaEventStream = eventSource.getStreamById(addOffencePlea.getPleasAllocation().getDefendantId());
        final DefenceClient defenceClientAggregate = aggregateService.get(defenceClientEventStream, DefenceClient.class);
        final DefencePleaAggregate pleaAggregate = aggregateService.get(defencePleaEventStream, DefencePleaAggregate.class);
        final Stream<Object> events = pleaAggregate.createPlea(setPleaDate(addOffencePlea.getPleasAllocation(), defenceClientAggregate.getCaseId()));
        appendEventsToStream(envelope, defencePleaEventStream, events);
    }

    @Handles("defence.command.update-offence-pleas")
    public void updatePlea(final Envelope<UpdateOffencePleas> envelope) throws EventStreamException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("defence.command.update-offence-pleas command received {}", envelope.payload());
        }
        final UpdateOffencePleas updateOffencePleas = envelope.payload();
        final EventStream defenceClientEventStream = eventSource.getStreamById(updateOffencePleas.getPleasAllocation().getDefendantId());
        final EventStream defencePleaEventStream = eventSource.getStreamById(updateOffencePleas.getPleasAllocation().getDefendantId());
        final DefenceClient defenceClientAggregate = aggregateService.get(defenceClientEventStream, DefenceClient.class);
        final DefencePleaAggregate pleaAggregate = aggregateService.get(defencePleaEventStream, DefencePleaAggregate.class);

        final Stream<Object> events = pleaAggregate.updatePlea(setPleaDate(updateOffencePleas.getPleasAllocation(), defenceClientAggregate.getCaseId()));
        appendEventsToStream(envelope, defencePleaEventStream, events);
    }

    private PleasAllocationDetails setPleaDate(final PleasAllocationDetails pleasAllocationDetails, final UUID caseId) {
        return PleasAllocationDetails.pleasAllocationDetails()
                .withValuesFrom(pleasAllocationDetails)
                .withCaseId(caseId)
                .withOffencePleas(pleasAllocationDetails.getOffencePleas().stream()
                        .map(s -> OffencePleaDetails.offencePleaDetails()
                                .withValuesFrom(s)
                                .withPleaDate(LocalDate.now())
                                .build())
                        .toList())
                .build();

    }

}
