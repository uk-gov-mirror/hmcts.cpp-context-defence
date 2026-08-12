package uk.gov.moj.cpp.defence.query.api.service;

import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.services.messaging.Metadata;

public class ProgressionQueryService {

    private static final String CASE_ID = "caseId";
    private static final String HEARING_ID = "hearingId";
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressionQueryService.class);
    private static final String PROGRESSION_QUERY_PROSECUTION_CASES = "progression.query.prosecutioncase";
    private static final String PROGRESSION_QUERY_CASE_HEARINGS = "progression.query.case.allhearingtypes";
    private static final String PROGRESSION_QUERY_HEARING = "progression.query.hearing";

    @Inject
    @ServiceComponent(QUERY_API)
    private Requester requester;

    @Inject
    private Enveloper enveloper;

    public Optional<JsonObject> getProsecutionCaseDetailById(final JsonEnvelope envelope, final String caseId) {
        Optional<JsonObject> result = Optional.empty();
        final JsonObject requestParameter = createObjectBuilder()
                .add(CASE_ID, caseId)
                .build();

        LOGGER.info("Calling progression service by caseId {} to get prosecution case detail", caseId);

        final JsonEnvelope prosecutioncase = requester.requestAsAdmin(enveloper
                .withMetadataFrom(envelope, PROGRESSION_QUERY_PROSECUTION_CASES)
                .apply(requestParameter));

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("caseId {}, prosecution case detail payload {}", caseId, prosecutioncase.toObfuscatedDebugString());
        }

        if (!prosecutioncase.payloadAsJsonObject().isEmpty()) {
            result = Optional.of(prosecutioncase.payloadAsJsonObject());
        }
        return result;
    }

    public Optional<JsonObject> getHearingsForProsecutionCase(final JsonEnvelope envelope, final String caseId) {
        LOGGER.info("Calling progression.query.case.allhearingtypes for caseId {} :", caseId);
        final Metadata metadata = metadataFrom(envelope.metadata()).withName(PROGRESSION_QUERY_CASE_HEARINGS).build();
        final JsonEnvelope requestPayload = JsonEnvelope.envelopeFrom(metadata, createObjectBuilder().add(CASE_ID, caseId).build());
        final JsonObject responsePayload = requester.requestAsAdmin(requestPayload, JsonObject.class).payload();
        return responsePayload.isEmpty() ? Optional.empty() : Optional.of(responsePayload);
    }

    public Optional<JsonObject> getHearing(final JsonEnvelope envelope, final String hearingId) {
        LOGGER.info("Calling progression.query.hearing for hearingId {} :", hearingId);
        final Metadata metadata = metadataFrom(envelope.metadata()).withName(PROGRESSION_QUERY_HEARING).build();
        final JsonEnvelope requestPayload = JsonEnvelope.envelopeFrom(metadata, createObjectBuilder().add(HEARING_ID, hearingId).build());
        final JsonObject responsePayload = requester.requestAsAdmin(requestPayload, JsonObject.class).payload();
        return responsePayload.isEmpty() ? Optional.empty() : Optional.of(responsePayload);
    }

}