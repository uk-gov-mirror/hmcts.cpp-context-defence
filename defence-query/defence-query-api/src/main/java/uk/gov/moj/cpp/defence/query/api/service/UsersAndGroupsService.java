package uk.gov.moj.cpp.defence.query.api.service;

import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@SuppressWarnings("squid:S1168")
public class UsersAndGroupsService {

    public static final String ORGANISATION_ID = "organisationId";

    @Inject
    @ServiceComponent(QUERY_API)
    private Requester requester;

    public JsonObject getOrganisationDetails(final JsonEnvelope envelope) {
        final JsonObject organisationDetail = createObjectBuilder().add(ORGANISATION_ID,
                envelope.payloadAsJsonObject().getJsonString(ORGANISATION_ID).getString()).build();
        return getOrganisationDetailsFromUserGroups(envelope, organisationDetail);
    }

    public JsonObject getOrganisationDetails(final JsonEnvelope envelope, final UUID organisationId) {
        final JsonObject organisationDetail = createObjectBuilder().add(ORGANISATION_ID, organisationId.toString()).build();
        return getOrganisationDetailsFromUserGroups(envelope, organisationDetail);
    }

    private JsonObject getOrganisationDetailsFromUserGroups(final JsonEnvelope envelope, final JsonObject organisationDetail) {
        final Envelope<JsonObject> requestEnvelope = Enveloper.envelop(organisationDetail).withName("usersgroups.get-organisation-details").withMetadataFrom(envelope);
        final JsonEnvelope usersAndGroupsRequestEnvelope = JsonEnvelope.envelopeFrom(requestEnvelope.metadata(), requestEnvelope.payload());
        final Envelope<JsonObject> response = requester.requestAsAdmin(JsonEnvelope.envelopeFrom(usersAndGroupsRequestEnvelope.metadata(), usersAndGroupsRequestEnvelope.payload()), JsonObject.class);
        final JsonEnvelope responseJsonEnvelope = envelopeFrom(response.metadata(), response.payload());
        if (responseJsonEnvelope.payloadIsNull()) {
            return null;
        }
        return responseJsonEnvelope.payloadAsJsonObject();
    }
}
