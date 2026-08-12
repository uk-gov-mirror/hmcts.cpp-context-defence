package uk.gov.moj.cpp.defence.event.processor;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.event.service.DefenceService;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_PROCESSOR)
public class UsersGroupsEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsersGroupsEventProcessor.class.getName());

    private static final String LAA_CONTRACT_NUMBERS = "laaContractNumbers";
    private static final String LAA_CONTRACT_NUMBER = "laaContractNumber";
    private static final String ORGANISATION_ID = "organisationId";
    private static final String ORGANISATION_NAME = "organisationName";
    private static final String DEFENDANT_ID = "defendantId";

    @Inject
    private Sender sender;

    @Inject
    private DefenceService defenceService;

    @Handles("public.usersgroups.organisation-created")
    public void setUpLAAOrganisation(final JsonEnvelope envelope) {

        LOGGER.info("Received Organisation Created Event  {}", envelope.payloadAsJsonObject());

        final JsonObject payload = envelope.payloadAsJsonObject().getJsonObject("organisationDetails");

        if (payload.containsKey(LAA_CONTRACT_NUMBERS)) {
            handleOrganisationSetupAndUpdate(envelope, payload);
        } else {
            LOGGER.info("No Organisation Set up for Legal Aid Agency");
        }
    }

    private void handleOrganisationSetupAndUpdate(JsonEnvelope envelope, JsonObject payload) {
        final List<String> laaContractNumbers = laaContractsAsList(payload);

        if (laaContractNumbers.isEmpty()) {
            LOGGER.info("Empty list of Organisation LAA Contract Number found ... returning");
            return;
        }

        final String organisationId = payload.getString(ORGANISATION_ID);
        final String organisationName = payload.getString(ORGANISATION_NAME);
        final JsonArray defendants = defenceService.getDefendantsByLAAContractNumber(envelope, laaContractNumbers);

        defendants.stream().map(defendantJsonValue -> (JsonObject) defendantJsonValue).forEach(defendantJsonObject -> {
            final JsonObject associateOrphanedCasePayload = createObjectBuilder()
                    .add(LAA_CONTRACT_NUMBER, defendantJsonObject.getString(LAA_CONTRACT_NUMBER))
                    .add(ORGANISATION_ID, organisationId)
                    .add(ORGANISATION_NAME, organisationName)
                    .add(DEFENDANT_ID, defendantJsonObject.getString("id"))
                    .build();

            sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.handler.associate-orphaned-case"),
                    associateOrphanedCasePayload));

        });
    }

    private List<String> laaContractsAsList(final JsonObject payload) {
        final JsonArray laaContractNumbersAsJsonArray = payload.getJsonArray(LAA_CONTRACT_NUMBERS);
        final List<String> laaContractNumbers = new ArrayList<>();
        for (int i = 0; i < laaContractNumbersAsJsonArray.size(); i++) {
            laaContractNumbers.add(laaContractNumbersAsJsonArray.getString(i));
        }
        return laaContractNumbers;
    }
}
