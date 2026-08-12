package uk.gov.moj.cpp.defence.query.api.service;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.query.api.DefenceQueryApi.ORGANISATION_ID;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

public class OrganisationQueryService {

    @ServiceComponent(Component.QUERY_API)
    @Inject
    private Requester requester;


    public List<OrganisationNameVO> getOrganisationNamesForIds(final List<String> ids, final Metadata metadata) {

        final String orgIdsToLookUp = String.join(",", ids);
        final JsonObject getOrganisationForUserRequest = createObjectBuilder().add("ids", orgIdsToLookUp).build();
        final Metadata metadataWithActionName = metadataFrom(metadata).withName("usersgroups.get-organisation-names-forids").build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getOrganisationForUserRequest);
        final JsonObject response = requester.requestAsAdmin(requestEnvelope, JsonObject.class).payload();
        if (nonNull(response)) {
            return response.getJsonArray("organisations").stream().map(x -> (JsonObject) x).map(y -> new OrganisationNameVO(y.getString("organisationId"), y.getString("organisationName"))).collect(Collectors.toList());
        } else {
            return emptyList();
        }
    }

    public String getOrganisationOfLoggedInUser(final Metadata metadata) {

        final String userId = metadata.userId().orElseThrow(() -> new NullPointerException("User id Not Supplied for the UserGroups look up"));
        final JsonObject getOrganisationForUserRequest = createObjectBuilder().add("userId", userId).build();

        final Metadata metadataWithActionName = metadataFrom(metadata).withName("usersgroups.get-organisation-details-for-user").build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getOrganisationForUserRequest);
        final JsonObject organisationOfLoggedInUser = requester.requestAsAdmin(requestEnvelope, JsonObject.class).payload();

        if (organisationOfLoggedInUser != null && organisationOfLoggedInUser.size() > 0) {
            return organisationOfLoggedInUser.getString(ORGANISATION_ID);
        } else {
            return null;
        }
    }

}
