package uk.gov.moj.cpp.defence.event.processor;

import static jakarta.json.Json.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;

import uk.gov.justice.core.courts.DefendantsAddedToCase;
import uk.gov.justice.core.courts.ProsecutionCase;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.event.converter.DefendantsAddedConverter;
import uk.gov.moj.cpp.defence.event.converter.ProsecutionCaseConverter;
import uk.gov.moj.cpp.defence.event.processor.events.CaseRemovedFromGroupCases;
import uk.gov.moj.cpp.defence.event.service.ProgressionService;
import uk.gov.moj.cpp.defence.events.CaseCreatedBdf;
import uk.gov.moj.cpp.progression.json.schema.event.ProsecutionCaseCreated;

import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@ServiceComponent(EVENT_PROCESSOR)
public class DefenceClientMapEventProcessor {

    public static final String DEFENCE_COMMAND_PROSECUTION_CASE_RECEIVE_DETAILS = "defence.command.prosecution-case-receive-details";
    @Inject
    ProgressionService progressionService;

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Inject
    ProsecutionCaseConverter prosecutionCaseConverter;

    @Inject
    DefendantsAddedConverter defendantsAddedConverter;
    @Inject
    private Sender sender;

    @Handles("defence.event.case-created-bdf")
    public void handleProsecutionCaseCreatedBdf(final Envelope<CaseCreatedBdf> envelope) {
        final CaseCreatedBdf payload = envelope.payload();

        final JsonEnvelope requestEnvelopeWithCaseId = JsonEnvelope.envelopeFrom(
                metadataFrom(envelope.metadata()),
                createObjectBuilder()
        );
        final JsonObject prosecutionCaseJson = progressionService.getProsecutionCaseByCaseId(requestEnvelopeWithCaseId, payload.getProsecutionCaseId().toString());
        final ProsecutionCase prosecutionCase = jsonObjectToObjectConverter.convert(prosecutionCaseJson, ProsecutionCase.class);

        if(Objects.isNull(payload.getDefendantId())) {
            sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName(DEFENCE_COMMAND_PROSECUTION_CASE_RECEIVE_DETAILS),
                    prosecutionCaseConverter.convertToProsecutionCaseReceiveDetails(prosecutionCase)));
        } else {
            prosecutionCase.getDefendants().stream().filter(def -> def.getId().equals(payload.getDefendantId()))
                    .forEach(defendant ->
                        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.add-defendant"), defendantsAddedConverter.convert(defendant)))
                    );
        }
    }

    @Handles("public.progression.prosecution-case-created")
    public void handleProsecutionCaseReceived(final Envelope<ProsecutionCaseCreated> envelope) {
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName(DEFENCE_COMMAND_PROSECUTION_CASE_RECEIVE_DETAILS),
                prosecutionCaseConverter.convertToProsecutionCaseReceiveDetails(envelope.payload().getProsecutionCase())));
    }

    @Handles("public.progression.case-removed-from-group-cases")
    public void handleCaseRemovedFromGroupCases(final Envelope<CaseRemovedFromGroupCases> envelope) {
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName(DEFENCE_COMMAND_PROSECUTION_CASE_RECEIVE_DETAILS),
                prosecutionCaseConverter.convertToProsecutionCaseReceiveDetails(envelope.payload().getRemovedCase())));
    }

    @Handles("public.progression.defendants-added-to-case")
    public void handleSpiProsecutionDefendantsAdded(final Envelope<DefendantsAddedToCase> envelope) {

        envelope.payload().getDefendants().forEach(defendant -> {
            defendantsAddedConverter.convert(defendant);
            sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.add-defendant"), defendantsAddedConverter.convert(defendant)));

        });

    }

}
