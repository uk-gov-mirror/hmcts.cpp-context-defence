package uk.gov.moj.cpp.defence.event.processor;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.progression.courts.DefendantLegalaidStatusUpdated;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.event.processor.events.CaseDefendantChanged;
import uk.gov.moj.cpp.defence.event.processor.events.DefenceOrganisationForLaaAssociated;
import uk.gov.moj.cpp.defence.event.processor.events.DefenceOrganisationForLaaDisassociated;
import uk.gov.moj.cpp.defence.event.processor.events.DefendantLaaContractAssociated;
import uk.gov.moj.cpp.defence.event.processor.events.DefendantOffencesChanged;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class ProgressionDefenceOperationsEventProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressionDefenceOperationsEventProcessor.class);
    @Inject
    private Sender sender;

    @Inject
    ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Handles("public.progression.case-defendant-changed")
    public void handleCaseDefendantChanged(final Envelope<CaseDefendantChanged> envelope) {
        final CaseDefendantChanged caseDefendantChanged = envelope.payload();
        final JsonObject caseDefendantChangedJson = removeProperty(objectToJsonObjectConverter.convert(caseDefendantChanged), "associatedDefenceOrganisation");
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.case-defendant-changed"),
                caseDefendantChangedJson));
    }

    @Handles("public.progression.defendant-offences-changed")
    public void handleDefendantOffencesChanged(final Envelope<DefendantOffencesChanged> envelope) {
        JsonObject jsonObject = objectToJsonObjectConverter.convert(envelope.payload());
        LOGGER.info("Inside handleDefendantOffencesChanged={}", jsonObject != null ? jsonObject.toString() : "");
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.update-defendant-offences"),
                envelope.payload()));
    }

    @Handles("public.progression.defence-organisation-for-laa-associated")
    public void handleAssociateDefenceOrganisationForLAA(final Envelope<DefenceOrganisationForLaaAssociated> envelope) {
        JsonObject jsonObject = objectToJsonObjectConverter.convert(envelope.payload());
        LOGGER.info("Inside handleAssociateDefenceOrganisationForLAA={}", jsonObject != null ? jsonObject.toString() : "");
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.associate-defence-organisation-for-laa"),
                envelope.payload()));
    }

    @Handles("public.progression.defendant-legalaid-status-updated")
    public void handleDefendantLegalStatusUpdated(final Envelope<DefendantLegalaidStatusUpdated> envelope) {
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.record-defendant-legal-status-updated"),
                envelope.payload()));
    }

    @Handles("public.progression.defence-organisation-for-laa-disassociated")
    public void handleDefenceOrganisationForLAADisassociated(final Envelope<DefenceOrganisationForLaaDisassociated> envelope) {
        JsonObject jsonObject = objectToJsonObjectConverter.convert(envelope.payload());
        LOGGER.info("Inside handleDefenceOrganisationForLAADisassociated={}", jsonObject != null ? jsonObject.toString() : "");
        final DefenceOrganisationForLaaDisassociated incomingDisassociation = envelope.payload();
        final DefenceOrganisationForLaaDisassociated defenceOrganisationForLaaDisassociated = DefenceOrganisationForLaaDisassociated.defenceOrganisationForLaaDisassociated()
                .withOrganisationId(incomingDisassociation.getOrganisationId())
                .withIsLAA(true)
                .withDefendantId(incomingDisassociation.getDefendantId())
                .withCaseId(incomingDisassociation.getCaseId())
                .build();
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.disassociate-defence-organisation"),
                defenceOrganisationForLaaDisassociated));
    }

    @Handles("public.progression.defendant-laa-contract-associated")
    public void handleDefendantLaaContractAssociated(final Envelope<DefendantLaaContractAssociated> envelope) {
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.handler.lock-defence-association-for-laa"),
                envelope.payload()));
    }

    public static JsonObject removeProperty(JsonObject origin, String key) {
        final JsonObjectBuilder builder = createObjectBuilder();

        for (final Map.Entry<String, JsonValue> entry : origin.entrySet()) {
            if (!entry.getKey().equals(key)) {
                if (entry.getValue().getValueType() == JsonValue.ValueType.OBJECT) {
                    builder.add(entry.getKey(), removeProperty(origin.getJsonObject(entry.getKey()), key));
                } else {
                    builder.add(entry.getKey(), entry.getValue());
                }
            }
        }
        return builder.build();
    }
}


