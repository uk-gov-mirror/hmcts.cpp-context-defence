package uk.gov.moj.cpp.defence.service;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static java.util.stream.Collectors.toList;
import static jakarta.json.JsonValue.NULL;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.ACTION;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.OBJECT;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.SOURCE;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.TARGET;
import static uk.gov.moj.cpp.defence.service.PermissionService.hasNullPayload;

import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.JsonObjects;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.defence.Address;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.OrganisationDetails;
import uk.gov.moj.cpp.defence.UsergroupDetails;
import uk.gov.moj.cpp.defence.exception.UserGroupQueryException;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;


public class UserGroupService {

    public static final String ORGANISATION_ID = "organisationId";
    public static final String ORGANISATION_NAME = "organisationName";
    public static final String ORGANISATION_TYPE = "organisationType";
    public static final String ADDRESS_LINE1 = "addressLine1";
    public static final String ADDRESS_LINE2 = "addressLine2";
    public static final String ADDRESS_LINE3 = "addressLine3";
    public static final String ADDRESS_LINE4 = "addressLine4";
    public static final String ADDRESS_POSTCODE = "addressPostcode";
    public static final String GROUPS = "groups";
    public static final String GROUP_ID = "groupId";
    public static final String GROUP_NAME = "groupName";
    public static final String USER_ID = "userId";
    public static final String USER_IDS = "userIds";
    public static final String EMAIL = "email";
    public static final String USERS = "users";
    public static final String PERMISSIONS = "permissions";
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";

    public static final String DESC = "description";
    public static final String ACTIVE = "active";
    public static final String STATUS = "status";
    public static final String ID = "id";


    public void givePermission(final Permission permission, final Metadata metadata, final Sender sender) {
        final JsonObject permissionRequest = createObjectBuilder()
                .add(DESC, "defence context - grant access")
                .add(SOURCE, permission.getSource().toString())
                .add(TARGET, permission.getTarget().toString())
                .add(OBJECT, permission.getObject())
                .add(ACTION, permission.getAction())
                .add(ACTIVE, true)
                .add(ID, permission.getId().toString())
                .add(STATUS, permission.getStatus().toString())
                .build();
        final MetadataBuilder metadataWithActionName = metadataBuilderWithNewActionName(metadata, "usersgroups.create-permission-with-details");
        sender.sendAsAdmin(Envelope.envelopeFrom(metadataWithActionName, permissionRequest));
    }

    public Organisation getOrganisationDetailsForUser(final UUID userId, final Metadata metadata, final Requester requester) {
        final JsonObject getOrganisationForUserRequest = createObjectBuilder().add(USER_ID, userId.toString()).build();
        final MetadataBuilder metadataWithActionName = metadataBuilderWithNewActionName(metadata, "usersgroups.get-organisation-details-for-user");
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getOrganisationForUserRequest);
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);

        if (hasNullPayload(response)) {
            return null;
        }

        final Address.Builder addressBuilder = Address.address();
        if (response.payload().toString().contains(ADDRESS_LINE1)) {
            addressBuilder.withAddress1(response.payload().getJsonString(ADDRESS_LINE1).getString());
        }
        if (response.payload().toString().contains(ADDRESS_LINE2)) {
            addressBuilder.withAddress2(response.payload().getJsonString(ADDRESS_LINE2).getString());
        }
        if (response.payload().toString().contains(ADDRESS_LINE3)) {
            addressBuilder.withAddress3(response.payload().getJsonString(ADDRESS_LINE3).getString());
        }
        if (response.payload().toString().contains(ADDRESS_LINE4)) {
            addressBuilder.withAddress4(response.payload().getJsonString(ADDRESS_LINE4).getString());
        }
        if (response.payload().toString().contains(ADDRESS_POSTCODE)) {
            addressBuilder.withAddressPostcode(response.payload().getJsonString(ADDRESS_POSTCODE).getString());
        }

        return uk.gov.moj.cpp.defence.Organisation.organisation()
                .withOrgId(fromString(response.payload().getJsonString(ORGANISATION_ID).getString()))
                .withOrganisationName(response.payload().getJsonString(ORGANISATION_NAME).getString())
                .withAddress(addressBuilder.build())
                .build();
    }


    public UUID getOrganisationForUser(final UUID userId, final Metadata metadata, final Requester requester) {
        return getOrganisationDetailsForUser(userId, metadata, requester).getOrgId();
    }

    public PersonDetails getUserDetailsWithEmail(final String email, final Metadata metadata, final Requester requester) {
        final JsonObject getOrganisationForUserRequest = createObjectBuilder().add(EMAIL, email).build();
        final MetadataBuilder metadataWithActionName = metadataBuilderWithNewActionName(metadata, "usersgroups.search-users");
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getOrganisationForUserRequest);

        return getUserDetails(requestEnvelope, requester);
    }

    public PersonDetails getUserDetailsWithUserId(final UUID userId, final Metadata metadata, final Requester requester) {
        final JsonObject getOrganisationForUserRequest = createObjectBuilder().add(USER_IDS, userId.toString()).build();
        final MetadataBuilder metadataWithActionName = metadataBuilderWithNewActionName(metadata, "usersgroups.search-users");
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getOrganisationForUserRequest);

        return getUserDetails(requestEnvelope, requester);
    }

    private PersonDetails getUserDetails(final JsonEnvelope requestEnvelope, final Requester requester) {

        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);

        if (hasNullPayload(response) || response.payload().getJsonArray(USERS) == null || response.payload().getJsonArray(USERS).isEmpty()) {
            return null;
        }

        final JsonObject userJson = ((JsonObject) response.payload().getJsonArray(USERS).get(0));

        return PersonDetails.personDetails()
                .withFirstName(userJson.getString(FIRST_NAME))
                .withLastName(userJson.getString(LAST_NAME))
                .withUserId(fromString(userJson.getString(USER_ID)))
                .build();
    }

    public List<String> getGroupNamesForUser(final UUID userId, final Metadata metadata, final Requester requester) {
        final JsonObject getGroupsForUserRequest = createObjectBuilder().add(USER_ID, userId.toString()).build();
        final MetadataBuilder metadataWithActionName = metadataBuilderWithNewActionName(metadata, "usersgroups.get-groups-by-user");
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getGroupsForUserRequest);
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);

        if (hasNullPayload(response) || response.payload().getJsonArray(GROUPS) == null) {
            throw new UserGroupQueryException(format("Groups information could not be found for the user %s", userId.toString()));
        }
        final JsonArray groupsJsonArray = response.payload().getJsonArray(GROUPS);

        return groupsJsonArray.stream()
                .map(group -> JsonObjects.getString((JsonObject) group, GROUP_NAME).get()
                ).collect(Collectors.toList());

    }

    public List<Permission> getPermissions(final UUID userId, final Metadata metadata, final Requester requester) {
        final JsonObject getOrganisationForUserRequest = createObjectBuilder().build();
        final MetadataBuilder metadataWithActionName = metadataBuilderWithNewActionName(metadata, "usersgroups.get-logged-in-user-permissions");
        metadataWithActionName.withUserId(userId.toString());
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getOrganisationForUserRequest);
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);

        final JsonArray permissionsJsonArray = response.payload().getJsonArray(PERMISSIONS);

        if (permissionsJsonArray == null) {
            return Collections.emptyList();
        }

        return permissionsJsonArray.stream()
                .map(p -> (JsonObject) p)
                .map(permission ->
                        Permission.permission()
                                .withAction(JsonObjects.getString(permission, ACTION).orElse(null))
                                .withObject(JsonObjects.getString(permission, OBJECT).orElse(null))
                                .withSource(getNullableUUID(permission, SOURCE))
                                .withTarget(getNullableUUID(permission, TARGET))
                                .build()
                ).collect(Collectors.toList());

    }

    private static UUID getNullableUUID(final JsonObject permission, final String attribute) {
        final String uuidString = JsonObjects.getString(permission, attribute).orElse(null);
        if (nonNull(uuidString)) {
            return fromString(uuidString);
        } else {
            return null;
        }

    }

    public MetadataBuilder metadataBuilderWithNewActionName(final Metadata metadata, final String actionName) {

        final MetadataBuilder metadataBuilder = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName(actionName)
                .createdAt(ZonedDateTime.now())
                .withCausation(metadata.causation().toArray(new UUID[metadata.causation().size()]));

        metadata.clientCorrelationId().ifPresent(metadataBuilder::withClientCorrelationId);
        metadata.sessionId().ifPresent(metadataBuilder::withSessionId);
        metadata.streamId().ifPresent(metadataBuilder::withStreamId);
        metadata.userId().ifPresent(metadataBuilder::withUserId);

        return metadataBuilder;
    }

    public OrganisationDetails getUserOrgDetails(final Envelope<?> envelope, final Requester requester) {
        final JsonEnvelope orgResponse = getOrganisationDetailsForUser(envelope, requester);
        if (notFound(orgResponse)) {
            return OrganisationDetails.organisationDetails().build();
        }
        return OrganisationDetails.organisationDetails().withId(fromString(orgResponse.payloadAsJsonObject().getString(ORGANISATION_ID)))
                .withName(orgResponse.payloadAsJsonObject().getString(ORGANISATION_NAME))
                .withType(orgResponse.payloadAsJsonObject().getString(ORGANISATION_TYPE)).build();
    }

    public OrganisationDetails getOrganisationByLaaReference(final Envelope<?> envelope, final Requester requester, final String laaRef) {
        final JsonEnvelope orgResponse = getOrganisationDetailsForLaaNumber(envelope, requester, laaRef);
        if (notFound(orgResponse)) {
            return OrganisationDetails.organisationDetails().build();
        }
        return OrganisationDetails.organisationDetails().withId(fromString(orgResponse.payloadAsJsonObject().getString(ORGANISATION_ID)))
                .withName(orgResponse.payloadAsJsonObject().getString(ORGANISATION_NAME))
                .withType(orgResponse.payloadAsJsonObject().getString(ORGANISATION_TYPE)).build();
    }

    private JsonEnvelope getOrganisationDetailsForUser(final Envelope<?> envelope, final Requester requester) {

        final String userId = envelope.metadata().userId().orElse(null);
        final JsonObject getOrganisationForUserRequest = createObjectBuilder().add(USER_ID, userId).build();
        final Envelope<JsonObject> requestEnvelope = Enveloper.envelop(getOrganisationForUserRequest)
                .withName("usersgroups.get-organisation-details-for-user").withMetadataFrom(envelope);
        final Envelope<JsonObject> organisationUnitsResponse = requester.requestAsAdmin(envelopeFrom(requestEnvelope.metadata(),
                requestEnvelope.payload()), JsonObject.class);
        return envelopeFrom(organisationUnitsResponse.metadata(), organisationUnitsResponse.payload());

    }

    private JsonEnvelope getOrganisationDetailsForLaaNumber(final Envelope<?> envelope, final Requester requester, final String laaRef) {

        final JsonObject getOrganisationForUserRequest = createObjectBuilder().add("laaContractNumber", laaRef).build();
        final Envelope<JsonObject> requestEnvelope = Enveloper.envelop(getOrganisationForUserRequest)
                .withName("usersgroups.get-organisation-details-by-laaContractNumber").withMetadataFrom(envelope);
        final Envelope<JsonObject> organisationUnitsResponse = requester.requestAsAdmin(envelopeFrom(requestEnvelope.metadata(),
                requestEnvelope.payload()), JsonObject.class);
        return envelopeFrom(organisationUnitsResponse.metadata(), organisationUnitsResponse.payload());

    }

    public List<UsergroupDetails> getUserGroupsForUser(final Envelope<?> envelope, final Requester requester) {
        final JsonObject userGroups = getUserGroupsDetailsForUser(envelope, requester);
        return userGroups.getJsonArray(GROUPS)
                .getValuesAs(JsonObject.class)
                .stream()
                .map(o -> UsergroupDetails.usergroupDetails().withGroupId(fromString(o.getString(GROUP_ID))).withGroupName(o.getString(GROUP_NAME)).build())
                .collect(toList());
    }


    private JsonObject getUserGroupsDetailsForUser(final Envelope<?> envelope, final Requester requester) {

        final String userId = envelope.metadata().userId().orElse(null);
        final JsonObject getUserGroupsForUserRequest = createObjectBuilder().add(USER_ID, userId).build();
        final Envelope<JsonObject> requestEnvelope = Enveloper.envelop(getUserGroupsForUserRequest)
                .withName("usersgroups.get-logged-in-user-groups").withMetadataFrom(envelope);
        final JsonEnvelope response = requester.request(requestEnvelope);
        checkGroupExistsForUser(userId, response);
        return response.payloadAsJsonObject();
    }

    private void checkGroupExistsForUser(final String userId, final JsonEnvelope response) {
        if (notFound(response)
                || (response.payloadAsJsonObject().getJsonArray(GROUPS) == null)
                || (response.payloadAsJsonObject().getJsonArray(GROUPS).isEmpty())) {
            throw new IllegalArgumentException(format("User %s does not belong to any of the HMCTS groups", userId));
        }
    }

    private static boolean notFound(JsonEnvelope response) {
        final JsonValue payload = response.payload();

        return payload == null
                || payload.equals(NULL);
    }

}
