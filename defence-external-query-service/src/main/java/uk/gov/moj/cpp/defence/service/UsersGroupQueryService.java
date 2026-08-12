package uk.gov.moj.cpp.defence.service;


import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

public class UsersGroupQueryService {

    public static final String GROUPS = "groups";
    public static final String PROSECUTING_AUTHORITY = "prosecutingAuthority";
    public static final String GROUP_NAME = "groupName";
    public static final String ORGANISATION_MATCH = "OrganisationMatch";
    public static final String ORGANISATION_MIS_MATCH = "OrganisationMisMatch";
    @ServiceComponent(Component.QUERY_VIEW)
    @Inject
    private Requester requester;

    public String getOrganisationForUser(final UUID userId, final Metadata metadata) {
        final JsonObject getOrganisationForUserRequest = createObjectBuilder().add("userId", userId.toString()).build();
        final Metadata metadataWithActionName = metadataFrom(metadata).withName("usersgroups.get-organisation-details-for-user").build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getOrganisationForUserRequest);
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);
        return response.payload().getString("organisationId");
    }

    public JsonObject getUserGroups(final Metadata metadata, final UUID userId) {

        final JsonObject getGroupsForUserRequest = createObjectBuilder().add("userId", userId.toString()).build();
        final Metadata metadataWithActionName = metadataFrom(metadata).withName("usersgroups.get-logged-in-user-groups").build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getGroupsForUserRequest);
        final Envelope<JsonObject> response = requester.request(requestEnvelope, JsonObject.class);
        return response.payload();
    }

    public boolean validateNonCPSUser(final Metadata metadata, final UUID userId, final String groupName, final String shortName) {
        final JsonObject userGroupPayload = getUserGroups(metadata, userId);
        return isNonCpsUserGroup(userGroupPayload, groupName) && isNonCpsProsecutors(userGroupPayload, shortName);
    }

    public Optional<String> validateNonCPSUserOrg(final Metadata metadata, final UUID userId, final String groupName, final String shortName) {
        final JsonObject userGroupPayload = getUserGroups(metadata, userId);
        if (isNonCpsUserGroup(userGroupPayload, groupName)) {
            if (isNonCpsProsecutors(userGroupPayload, shortName)) {
                return Optional.of(ORGANISATION_MATCH);
            } else {
                return Optional.of(ORGANISATION_MIS_MATCH);
            }
        }
        return Optional.empty();
    }

    public boolean isNonCPSProsecutorWithValidProsecutingAuthority(final JsonObject userGroupPayload, final String groupName, final String shortName) {
        return isNonCpsUserGroup(userGroupPayload, groupName) && isNonCpsProsecutors(userGroupPayload, shortName);
    }

    public Boolean isNonCpsUserGroup(final JsonObject userGroups, final String groupName) {
        final Stream<JsonObject> stream = userGroups.getJsonArray(GROUPS).getValuesAs(JsonObject.class).stream();
        return stream
                .filter(usergroup -> usergroup.containsKey(GROUP_NAME))
                .anyMatch(usergroup -> groupName.equals(usergroup.getString(GROUP_NAME)));
    }

    public Boolean isNonCpsProsecutors(final JsonObject userGroups, final String shortName) {
        final Stream<JsonObject> stream = userGroups.getJsonArray(GROUPS).getValuesAs(JsonObject.class).stream();
        return stream
                .filter(usergroup -> usergroup.containsKey(PROSECUTING_AUTHORITY))
                .anyMatch(usergroup -> shortName.equals(usergroup.getString(PROSECUTING_AUTHORITY)));
    }
}
