package uk.gov.moj.cpp.defence.service;

import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static jakarta.json.JsonValue.NULL;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.common.util.DefencePermission.VIEW_DEFENDANT_PERMISSION;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.ACTION;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.OBJECT;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.SOURCE;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.TARGET;
import static uk.gov.moj.cpp.defence.service.UserGroupService.PERMISSIONS;

import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.JsonObjects;
import uk.gov.justice.services.messaging.Metadata;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

public class PermissionService {


    private PermissionService() {

    }

    public static List<Permission> getPermissions(final Metadata metadata, final Requester requester, String defendantId) {
        final JsonObject getPermissionsforDefendantRequest = createObjectBuilder().add(ACTION, VIEW_DEFENDANT_PERMISSION.getActionType()).add(OBJECT, VIEW_DEFENDANT_PERMISSION.getObjectType()).add(TARGET, defendantId).build();
        final Metadata metadataWithActionName = metadataFrom(metadata).withName("usersgroups.permissions").build();

        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, getPermissionsforDefendantRequest);
        final Envelope<JsonObject> response = requester.requestAsAdmin(requestEnvelope, JsonObject.class);


        if (hasNullPayload(response) || !response.payload().containsKey(PERMISSIONS)) {
            return Collections.emptyList();
        }
        final JsonArray permissionsJsonArray = response.payload().getJsonArray(PERMISSIONS);

        if (permissionsJsonArray == null || permissionsJsonArray.isEmpty()) {
            return Collections.emptyList();
        }

        return permissionsJsonArray.stream()
                .map(p -> (JsonObject)p)
                .map(permission ->
                        Permission.permission()
                                .withAction(JsonObjects.getString(permission, ACTION).orElse(null))
                                .withObject(JsonObjects.getString(permission, OBJECT).orElse(null))
                                .withSource(getNullableUUID(permission, SOURCE))
                                .withTarget(getNullableUUID(permission, TARGET))
                                .build()
                ).collect(Collectors.toList());

    }


    public static List<Permission> getUserPermissions(final Metadata metadata, final Requester requester) {

        final Metadata metadataWithActionName = metadataFrom(metadata).withName("usersgroups.get-logged-in-user-permissions").build();
        final JsonEnvelope requestEnvelope = envelopeFrom(metadataWithActionName, createObjectBuilder().build());
        final Envelope<JsonObject> response = requester.request(requestEnvelope, JsonObject.class);

        if (hasNullPayload(response) || !response.payload().containsKey(PERMISSIONS)) {
            return Collections.emptyList();
        }

        final JsonArray permissionsJsonArray = response.payload().getJsonArray(PERMISSIONS);

        if (permissionsJsonArray == null || permissionsJsonArray.isEmpty()) {
            return Collections.emptyList();
        }

        return permissionsJsonArray.stream()
                .map(p -> (JsonObject)p)
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
        final String uuidString = JsonObjects.getString( permission, attribute).orElse(null);
        return nonNull(uuidString) ? fromString(uuidString) : null;
    }

    static boolean hasNullPayload(final Envelope<JsonObject> response) {
        return response == null || response.payload() == null || NULL.equals(response.payload());
    }

}
