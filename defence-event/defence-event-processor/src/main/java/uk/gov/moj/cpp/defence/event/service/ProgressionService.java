package uk.gov.moj.cpp.defence.event.service;

import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;

public class ProgressionService {

    private static final String CASE_ID = "caseId";
    private static final String PROGRESSION_QUERY_PROSECUTION_CASE = "progression.query.prosecutioncase";

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    public JsonObject getProsecutionCaseByCaseId(final JsonEnvelope envelope, final String caseId) {
        final Metadata metadataWithActionName = metadataFrom(envelope.metadata()).withName(PROGRESSION_QUERY_PROSECUTION_CASE).build();
        final JsonObject requestParameter = createObjectBuilder()
                .add(CASE_ID, caseId)
                .build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, requestParameter);
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);
        return response.payload().getJsonObject("prosecutionCase");
    }

}
