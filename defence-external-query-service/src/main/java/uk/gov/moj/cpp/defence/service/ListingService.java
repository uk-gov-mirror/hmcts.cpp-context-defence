package uk.gov.moj.cpp.defence.service;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.json.schemas.listing.Hearings;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ListingService {

    public static final String APPLICATION_ID = "applicationId";
    private static final String LISTING_SEARCH_HEARINGS_QUERY = "listing.allocated.and.unallocated.hearings";
    private static final String CASE_ID = "caseId";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProducer().objectMapper();
    @Inject
    @ServiceComponent(QUERY_API)
    private Requester requester;

    @SuppressWarnings("squid:S1166")
    public List<Hearing> getHearings(final Metadata metadata, final String caseId) {

        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder().add(CASE_ID, caseId);
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName(LISTING_SEARCH_HEARINGS_QUERY),
                jsonObjectBuilder.build());
        final JsonValue response = requester.requestAsAdmin(queryEnvelope, JsonObject.class).payload();

        Hearings hearing = null;
        if (nonNull(response)) {
            try {
                hearing = OBJECT_MAPPER.readValue(response.toString(), Hearings.class);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to unmarshal Hearings", e);

            }
        }

        return ofNullable(hearing).map(Hearings::getHearings).orElse(Collections.emptyList());
    }
}
