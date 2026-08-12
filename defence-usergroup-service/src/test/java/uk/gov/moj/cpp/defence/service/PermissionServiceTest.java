package uk.gov.moj.cpp.defence.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.common.util.ActionTypes.VIEW;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.ACTION;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.OBJECT;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.SOURCE;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.TARGET;
import static uk.gov.moj.cpp.defence.common.util.ObjectTypes.DEFENCE_CLIENT;

import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.util.List;
import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class PermissionServiceTest {

    @Mock
    private Requester requester;

    @InjectMocks
    private PermissionService permissionService;

    private static final String USER_GROUP_PERMISSIONS = "usersgroups.permissions";
    private static final String USERSGROUPS_GET_LOGGED_IN_USER_PERMISSIONS = "usersgroups.get-logged-in-user-permissions";
    private static final String PERMISSIONS = "permissions";

    @Test
    public void shouldGetEmptyListOnNullPayLoad() {
        final String defendantId = randomUUID().toString();
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            return JsonEnvelope.envelopeFrom(envelope.metadata(), null);
        });

        assertTrue(PermissionService.getPermissions(metadata, requester, defendantId).isEmpty());
    }

    @Test
    public void shouldGetEmptyListOnWithNoPermissions() {
        final String defendantId = randomUUID().toString();
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals(USER_GROUP_PERMISSIONS)) {
                responsePayload = createObjectBuilder().add("actions", buildPermissionPayloads(uuid, userId)).build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        assertTrue(PermissionService.getPermissions(metadata, requester, defendantId).isEmpty());
    }

    @Test
    public void shouldGetEmptyListOnWithNullPermissionArray() {
        final String defendantId = randomUUID().toString();

        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals(USER_GROUP_PERMISSIONS)) {
                responsePayload = createObjectBuilder().add(PERMISSIONS, createArrayBuilder()).build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        assertTrue(PermissionService.getPermissions(metadata, requester, defendantId).isEmpty());
    }

    @Test
    public void shouldReturnValidPermission() {
        final String defendantId = randomUUID().toString();
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals(USER_GROUP_PERMISSIONS)) {
                responsePayload = createObjectBuilder().add(PERMISSIONS, buildPermissionPayloads(uuid, userId)).build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });
        List<Permission> permissions = PermissionService.getPermissions(metadata, requester, defendantId);
        assertTrue(permissions.size() > 0);
        Permission permission = permissions.get(0);
        assertThat(permission.getAction(), is(VIEW.getActionName()));
        assertThat(permission.getObject(), is(DEFENCE_CLIENT.getObjectName()));
        assertThat(permission.getSource(), is(uuid));
        assertThat(permission.getTarget(), is(userId));

    }

    @Test
    public void shouldGetEmptyUserPermissionListWhenNullPayLoadReturnsFromUserGroups() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.request(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            return JsonEnvelope.envelopeFrom(envelope.metadata(), null);
        });

        assertTrue(PermissionService.getUserPermissions(metadata, requester).isEmpty());
    }

    @Test
    public void shouldGetEmptyUserPermissionListWhenNoPermissionReturnedFromUserGroups() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.request(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals(USER_GROUP_PERMISSIONS)) {
                responsePayload = createObjectBuilder().add("actions", buildPermissionPayloads(uuid, userId)).build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        assertTrue(PermissionService.getUserPermissions(metadata, requester).isEmpty());
    }

    @Test
    public void shouldGetEmptyUserPermissionListWhenNullPermissionArrayReturnedFromUserGroups() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);
        when(requester.request(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals(USER_GROUP_PERMISSIONS)) {
                responsePayload = createObjectBuilder().add(PERMISSIONS, createArrayBuilder()).build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        assertTrue(PermissionService.getUserPermissions(metadata, requester).isEmpty());
    }

    @Test
    public void shouldReturnValidUserPermissions() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);
        when(requester.request(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals(USERSGROUPS_GET_LOGGED_IN_USER_PERMISSIONS)) {
                responsePayload = createObjectBuilder().add(PERMISSIONS, buildPermissionPayloads(uuid, userId)).build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });
        List<Permission> permissions = PermissionService.getUserPermissions(metadata, requester);
        assertTrue(permissions.size() > 0);
        Permission permission = permissions.get(0);
        assertThat(permission.getAction(), is(VIEW.getActionName()));
        assertThat(permission.getObject(), is(DEFENCE_CLIENT.getObjectName()));
        assertThat(permission.getSource(), is(uuid));
        assertThat(permission.getTarget(), is(userId));

    }

    private JsonArray buildPermissionPayloads(UUID uuid, UUID userId) {
        final JsonArrayBuilder jsonArrayBuilder = createArrayBuilder();
        jsonArrayBuilder.add(this.buildPermissionPayload(uuid, userId));
        return jsonArrayBuilder.build();
    }

    private JsonObjectBuilder buildPermissionPayload(UUID uuid, UUID userId) {
        return createObjectBuilder()
                .add(ACTION, VIEW.getActionName())
                .add(OBJECT, DEFENCE_CLIENT.getObjectName())
                .add(SOURCE, uuid.toString())
                .add(TARGET, userId.toString());
    }

    private Metadata getMetaData(UUID uuid, UUID userId) {
        return Envelope
                .metadataBuilder()
                .withName(USER_GROUP_PERMISSIONS)
                .withId(uuid)
                .withUserId(userId.toString())
                .build();

    }
}