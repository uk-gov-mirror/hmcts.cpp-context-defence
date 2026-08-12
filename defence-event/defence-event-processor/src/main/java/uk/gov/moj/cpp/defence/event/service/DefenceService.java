package uk.gov.moj.cpp.defence.event.service;

import static java.util.Objects.nonNull;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.query.view.DefenceQueryView;
import uk.gov.moj.cpp.defence.query.view.DefendantQueryView;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

public class DefenceService {

    private static final String DEFENDANTS = "defendants";

    @Inject
    private DefenceQueryView defenceQueryView;

    @Inject
    private DefendantQueryView defendantQueryView;

    public UUID getCaseIdForDefenceClient(final UUID defendantId, final Metadata metadata) {
        final JsonObject getDefenceClientForDefendantId = createObjectBuilder().add("defendantId", defendantId.toString()).build();
        final Metadata metadataWithActionName = metadataFrom(metadata).withName("defence.query.defence-client-defendantId").build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getDefenceClientForDefendantId);
        final Envelope<DefenceClient> response = defenceQueryView.getDefenceClientByDefendantId(requestEnvelope);
        return response.payload().getCaseId();
    }

    public JsonArray getDefendantsByLAAContractNumber(final JsonEnvelope envelope, final List<String> laaContractNumbers) {
        final String laaContractNumbersAsString = String.join(",", laaContractNumbers);
        final JsonObject getDefendantsByLaaContractNumberRequest = createObjectBuilder().add("laaContractNumbers", laaContractNumbersAsString).build();
        final Metadata metadataWithActionName = metadataFrom(envelope.metadata()).withName("defence.query.defendants-by-laacontractnumber").build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getDefendantsByLaaContractNumberRequest);
        final JsonEnvelope response = defendantQueryView.getDefendantsByLAAContractNumber(requestEnvelope);
        return response.payloadAsJsonObject().getJsonArray(DEFENDANTS);
    }

    public JsonArray getPleaAndAllocationDetailsForACase(final Metadata metadata, final UUID caseId) {
        final JsonObject getDefendantsByLaaContractNumberRequest = createObjectBuilder().add("caseId", caseId.toString()).build();
        final Metadata metadataWithActionName = metadataFrom(metadata).withName("defence.query.pleas-and-allocation").build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getDefendantsByLaaContractNumberRequest);
        final JsonEnvelope response = defenceQueryView.findPleasAndAllocationByCaseId(requestEnvelope);

        if (nonNull(response.payloadAsJsonObject())) {
            return response.payloadAsJsonObject().getJsonArray("pleasAllocation");
        }
        return null;
    }

}
