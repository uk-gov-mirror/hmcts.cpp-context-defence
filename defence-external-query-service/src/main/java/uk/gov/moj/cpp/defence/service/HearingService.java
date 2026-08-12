package uk.gov.moj.cpp.defence.service;

import static java.util.Objects.nonNull;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.json.schemas.hearing.Timeline;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.query.hearing.api.Hearings;

import java.io.IOException;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;

public class HearingService {

    private static final String HEARING_CASE_TIMELINE = "hearing.case.timeline";
    private static final String HEARING_GET_HEARINGS = "hearing.get.hearings";
    private static final String HEARING_APPLICATION_TIMELINE = "hearing.application.timeline";
    private static final String ID = "id";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProducer().objectMapper();
    @Inject
    @ServiceComponent(QUERY_API)
    private Requester requester;

    @SuppressWarnings("squid:S1166")
    public Timeline getHearingTimelineByCaseId(final Metadata metadata, final UUID caseId) {
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName(HEARING_CASE_TIMELINE),
                createObjectBuilder().
                        add(ID, caseId.toString()).build());

        final JsonObject response = requester.requestAsAdmin(queryEnvelope, JsonObject.class).payload();

        Timeline timeline = null;
        if (null != response) {
            try {
                timeline = OBJECT_MAPPER.readValue(response.toString(), Timeline.class);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to unmarshal timeline", e);
            }
        }

        return timeline;
    }

    public Timeline getHearingTimelineByApplicationId(final Metadata metadata, final UUID applicationId) {
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName(HEARING_APPLICATION_TIMELINE),
                createObjectBuilder().
                        add(ID, applicationId.toString()).build());

        final JsonObject response = requester.requestAsAdmin(queryEnvelope, JsonObject.class).payload();

        Timeline timeline = null;
        if (null != response) {
            try {
                timeline = OBJECT_MAPPER.readValue(response.toString(), Timeline.class);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to unmarshal timeline", e);
            }
        }

        return timeline;
    }

    public Hearings getHearings(final Metadata metadata, final String date, final String courtCentreId) {
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName(HEARING_GET_HEARINGS),
                createObjectBuilder()
                        .add("date", date)
                        .add("courtCentreId", courtCentreId).build());

        final JsonObject response = requester.request(queryEnvelope, JsonObject.class).payload();

        if (nonNull(response)) {
            try {
                return OBJECT_MAPPER.readValue(response.toString(), Hearings.class);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to unmarshal hearings", e);
            }
        }

        return null;
    }


}
