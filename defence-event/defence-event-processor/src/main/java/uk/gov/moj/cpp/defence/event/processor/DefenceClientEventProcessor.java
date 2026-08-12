package uk.gov.moj.cpp.defence.event.processor;

import static uk.gov.justice.cps.defence.ReceiveAllegationsAgainstADefenceClient.receiveAllegationsAgainstADefenceClient;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.moj.cpp.defence.IdpcDetails.idpcDetails;
import static uk.gov.moj.cpp.defence.event.processor.commands.RecordIdpcDetails.recordIdpcDetails;

import uk.gov.justice.cps.defence.DefenceClientMappedToACase;
import uk.gov.justice.cps.defence.DefendantAdded;
import uk.gov.justice.cps.defence.ReceiveAllegationsAgainstADefenceClient;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.IdpcDetails;
import uk.gov.moj.cpp.defence.common.util.GenericEnveloper;
import uk.gov.moj.cpp.defence.event.processor.commands.ReceiveDefenceClient;
import uk.gov.moj.cpp.defence.event.processor.commands.RecordIdpcDetails;
import uk.gov.moj.cpp.progression.json.schema.event.PublicDefendantIdpcAdded;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@ServiceComponent(EVENT_PROCESSOR)
public class DefenceClientEventProcessor {


    @Inject
    private Sender sender;

    @Inject
    private GenericEnveloper genericEnveloper;

    @Handles("defence.event.defence-client-mapped-to-a-case")
    public void handleSuspectAdded(final Envelope<DefenceClientMappedToACase> eventEnvelope) {
        final DefenceClientMappedToACase event = eventEnvelope.payload();
        sender.send(
                receiveDefenceClient(
                        eventEnvelope,
                        event
                )
        );
    }

    @Handles("public.progression.idpc-document-received")
    public void handleIdpcPublishedForDefendant(final Envelope<PublicDefendantIdpcAdded> envelope) {
        final PublicDefendantIdpcAdded defendantIdpcAdded = envelope.payload();
        final IdpcDetails idpcDetails = buildIdpcDetails(defendantIdpcAdded);

        final RecordIdpcDetails recordIdpcDetails = recordIdpcDetails()
                .withDefenceClientId(defendantIdpcAdded.getDefendantId())
                .withIdpcDetails(idpcDetails)
                .build();

        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.record-idpc-details"),
                recordIdpcDetails));
    }


    @Handles("defence.event.defendant-added")
    public void handleSuspectIsCharged(final Envelope<DefendantAdded> event) {
        final DefendantAdded defendantAdded = event.payload();
        sender.send(receiveAllegations(defendantAdded, event.metadata()));
    }

    @Handles("defence.event.instruction-details-recorded")
    public void handleRecordInstructionDetails(final JsonEnvelope jsonEnvelope) {
        final JsonObject privateEventPayload = jsonEnvelope.payloadAsJsonObject();

        sender.send(envelopeFrom(metadataFrom(jsonEnvelope.metadata()).withName("public.defence.event.record-instruction-details"),
                privateEventPayload));
    }

    private Envelope<ReceiveAllegationsAgainstADefenceClient> receiveAllegations(final DefendantAdded defendantAdded,
                                                                                 final Metadata metadata) {
        return genericEnveloper.envelopeWithNewActionName(
                receiveAllegationsCommand(defendantAdded),
                metadata,
                "defence.command.receive-allegations-against-a-defence-client");
    }

    private ReceiveAllegationsAgainstADefenceClient receiveAllegationsCommand(final DefendantAdded defendantAdded) {
        return receiveAllegationsAgainstADefenceClient()
                .withDefenceClientId(defendantAdded.getDefenceClientId())
                .withDefendantDetails(defendantAdded.getDefendantDetails())
                .withDefendantId(defendantAdded.getDefendantId())
                .withOffences(defendantAdded.getOffences())
                .withPoliceDefendantId(defendantAdded.getPoliceDefendantId()).build();
    }


    private Envelope<ReceiveDefenceClient> receiveDefenceClient(final Envelope<DefenceClientMappedToACase> eventEnvelope, final DefenceClientMappedToACase event) {
        return genericEnveloper.envelopeWithNewActionName(receiveDefenceClient(event), eventEnvelope.metadata(), "defence.command.receive-defence-client");
    }

    private ReceiveDefenceClient receiveDefenceClient(final DefenceClientMappedToACase event) {
        return ReceiveDefenceClient.receiveDefenceClient()
                .withDefenceClientDetails(event.getDefenceClientDetails())
                .withDefenceClientId(event.getDefenceClientId())
                .withDefendantDetails(event.getDefendantDetails())
                .withUrn(event.getUrn())
                .withDefendantId(event.getDefendantId())
                .build();
    }

    private IdpcDetails buildIdpcDetails(final PublicDefendantIdpcAdded defendantIdpcAdded) {

        return idpcDetails()
                .withMaterialId(defendantIdpcAdded.getMaterialId())
                .withSize(defendantIdpcAdded.getSize())
                .withPageCount(defendantIdpcAdded.getPageCount())
                .withPublishedDate(defendantIdpcAdded.getPublishedDate())
                .build();
    }
}
