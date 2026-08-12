package uk.gov.moj.cpp.defence.event.service;

import static java.util.UUID.fromString;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.enveloper.Enveloper.envelop;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;


import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;


public class UsersGroupService {

    @ServiceComponent(Component.EVENT_PROCESSOR)
    @Inject
    private Requester requester;



    public UUID getOrganisationByType(final Metadata metadata) {
        final JsonObject getRequest = createObjectBuilder()
                .add("name", "HMCTS")
                .add("type", "HMCTS")
                .add("limit", 1)
                .build();
        final Envelope<JsonObject> response = requester.requestAsAdmin(Envelope.envelopeFrom(metadataFrom(metadata)
                .withName("usersgroups.organisations")
                .build(), getRequest), JsonObject.class);


        String organisationIdStr = response.payload().getJsonArray("organisations")
                .getValuesAs(JsonObject.class)
                .stream().map(s -> s.getString("organisationId")).findFirst().orElseThrow(RuntimeException::new);
        return UUID.fromString(organisationIdStr);
    }

    public UserDetails getUserDetails(final Envelope<?> envelope) {
        final UUID userId = fromString(envelope.metadata().userId().orElseThrow(() -> new RuntimeException("UserId missing from the event")));
        final JsonObject getUserRequest = createObjectBuilder().add("userId", userId.toString()).build();
        final Envelope<UserDetails> response = requester.requestAsAdmin(envelop(getUserRequest).withName("usersgroups.get-user-details").withMetadataFrom(envelope), UserDetails.class);
        return response.payload();
    }

}




