package uk.gov.moj.cpp.defence.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.events.Status.ADDED;

import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultEnvelope;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.OrganisationDetails;
import uk.gov.moj.cpp.defence.UsergroupDetails;
import uk.gov.moj.cpp.defence.exception.UserGroupQueryException;

import java.util.List;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class UserGroupServiceTest {

    public static final String GRANT_ACCESS_ADDED_DECRIPTION = "defence context - grant access";
    public static final String DESCRIPTION = "description";
    public static final String SOURCE = "source";
    public static final String TARGET = "target";
    public static final String OBJECT = "object";
    public static final String ACTION = "action";
    public static final String ACTIVE = "active";
    public static final String ID = "id";
    public static final String STATUS = "status";
    public static final String ORGANISATION_NAME = "organisationName";
    public static final String ORGANISATION_ID = "organisationId";
    public static final String ADDRESS_LINE_1 = "addressLine1";
    public static final String ADDRESS_LINE_2 = "addressLine2";
    public static final String ADDRESS_LINE_3 = "addressLine3";
    public static final String ADDRESS_LINE_4 = "addressLine4";
    public static final String ADDRESS_POSTCODE = "addressPostcode";
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";
    public static final String USER_ID = "userId";
    public static final String USERS = "users";
    public static final String GROUP_NAME = "groupName";
    public static final String PERMISSIONS = "permissions";
    public static final String ORGANISATION_TYPE = "organisationType";
    public static final String GROUP_ID = "groupId";
    public static final String GROUPS = "groups";
    @Mock
    private Sender sender;

    @Mock
    private Requester requester;

    @Mock
    private Envelope envelope;

    @InjectMocks
    private UserGroupService userGroupService;

    @Captor
    private ArgumentCaptor<DefaultEnvelope> envelopeCaptor;

    @Test
    public void shouldGivePermission() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);
        final Permission permission = Permission.permission()
                .withSource(randomUUID())
                .withTarget(randomUUID())
                .withObject("caseDocument")
                .withAction("view")
                .withId(randomUUID())
                .withStatus(ADDED)
                .build();

        userGroupService.givePermission(permission, metadata, sender);
        verify(sender).sendAsAdmin(envelopeCaptor.capture());

        final DefaultEnvelope capturedEnvelope = envelopeCaptor.getValue();
        assertThat(capturedEnvelope.metadata().name(), Matchers.is("usersgroups.create-permission-with-details"));

        final JsonObject payload = (JsonObject) capturedEnvelope.payload();
        assertThat(payload.getString(DESCRIPTION), is(GRANT_ACCESS_ADDED_DECRIPTION));
        assertThat(payload.getString(SOURCE), is(permission.getSource().toString()));
        assertThat(payload.getString(TARGET), is(permission.getTarget().toString()));
        assertThat(payload.getString(OBJECT), is(permission.getObject()));
        assertThat(payload.getString(ACTION), is(permission.getAction()));
        assertThat(payload.getBoolean(ACTIVE), is(true));
        assertThat(payload.getString(ID), is(permission.getId().toString()));
        assertThat(payload.getString(STATUS), is(permission.getStatus().toString()));
    }

    @Test
    public void shouldGetNullWhenResponsePayloadIsNull() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            return JsonEnvelope.envelopeFrom(envelope.metadata(), null);
        });

        assertNull(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester));
    }

    @Test
    public void shouldGetNullWhenResponseIsNull() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> null);

        assertNull(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester));
    }

    @Test
    public void shouldGetOrganisationDetailsForUser() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final UUID organisationId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.get-organisation-details-for-user")) {
                responsePayload = createObjectBuilder()
                        .add(ORGANISATION_ID, organisationId.toString())
                        .add(ORGANISATION_NAME, ORGANISATION_NAME)
                        .add(ADDRESS_LINE_1, ADDRESS_LINE_1)
                        .add(ADDRESS_LINE_2, ADDRESS_LINE_2)
                        .add(ADDRESS_LINE_3, ADDRESS_LINE_3)
                        .add(ADDRESS_LINE_4, ADDRESS_LINE_4)
                        .add(ADDRESS_POSTCODE, ADDRESS_POSTCODE)
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final Organisation organisation = userGroupService.getOrganisationDetailsForUser(userId, metadata, requester);

        assertThat(organisation.getOrganisationName(), is(ORGANISATION_NAME));
        assertThat(organisation.getOrgId().toString(), is(organisationId.toString()));
        assertThat(organisation.getAddress().getAddress1(), is(ADDRESS_LINE_1));
        assertThat(organisation.getAddress().getAddress2(), is(ADDRESS_LINE_2));
        assertThat(organisation.getAddress().getAddress3(), is(ADDRESS_LINE_3));
        assertThat(organisation.getAddress().getAddress4(), is(ADDRESS_LINE_4));
        assertThat(organisation.getAddress().getAddressPostcode(), is(ADDRESS_POSTCODE));

    }

    @Test
    public void shouldGetOrganisationForUser() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final UUID organisationId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.get-organisation-details-for-user")) {
                responsePayload = createObjectBuilder()
                        .add(ORGANISATION_ID, organisationId.toString())
                        .add(ORGANISATION_NAME, ORGANISATION_NAME)
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final UUID returnedOrganisationId = userGroupService.getOrganisationForUser(userId, metadata, requester);

        assertThat(returnedOrganisationId, is(organisationId));

    }

    @Test
    public void shouldReturnNullUserDetailsWhenResponsePayloadIsNull() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final String email = "email@email.co.uk";
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            return JsonEnvelope.envelopeFrom(envelope.metadata(), null);
        });

        final PersonDetails personDetails = userGroupService.getUserDetailsWithEmail(email, metadata, requester);

        assertThat(personDetails, nullValue());

    }

    @Test
    public void shouldReturnNullUserDetailsWhenResponseIsNull() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final String email = "email@email.co.uk";
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> null);

        final PersonDetails personDetails = userGroupService.getUserDetailsWithEmail(email, metadata, requester);

        assertThat(personDetails, nullValue());

    }

    @Test
    public void shouldReturnNullUserDetailsWhenUsersIsNull() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final String email = "email@email.co.uk";
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.search-users")) {
                responsePayload = createObjectBuilder()
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final PersonDetails personDetails = userGroupService.getUserDetailsWithEmail(email, metadata, requester);
        assertThat(personDetails, nullValue());
    }

    @Test
    public void shouldReturnNullUserDetailsWhenUsersIsEmpty() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final String email = "email@email.co.uk";
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.search-users")) {
                responsePayload = createObjectBuilder()
                        .add(USERS, createArrayBuilder()
                                .build())
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final PersonDetails personDetails = userGroupService.getUserDetailsWithEmail(email, metadata, requester);
        assertThat(personDetails, nullValue());
    }


    @Test
    public void shouldGetUserDetailsWithEmail() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final String email = "email@email.co.uk";
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.search-users")) {
                responsePayload = createObjectBuilder()
                        .add(USERS, createArrayBuilder()
                                .add(createObjectBuilder()
                                        .add(FIRST_NAME, FIRST_NAME)
                                        .add(LAST_NAME, LAST_NAME)
                                        .add(USER_ID, userId.toString())
                                        .build())
                                .build())
                        .add(ORGANISATION_NAME, ORGANISATION_NAME)
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final PersonDetails personDetails = userGroupService.getUserDetailsWithEmail(email, metadata, requester);

        assertThat(personDetails.getUserId(), is(userId));
        assertThat(personDetails.getFirstName(), is(FIRST_NAME));
        assertThat(personDetails.getLastName(), is(LAST_NAME));

    }

    @Test
    public void shouldGetUserDetailsWithUserId() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.search-users")) {
                responsePayload = createObjectBuilder()
                        .add(USERS, createArrayBuilder()
                                .add(createObjectBuilder()
                                        .add(FIRST_NAME, FIRST_NAME)
                                        .add(LAST_NAME, LAST_NAME)
                                        .add(USER_ID, userId.toString())
                                        .build())
                                .build())
                        .add(ORGANISATION_NAME, ORGANISATION_NAME)
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final PersonDetails personDetails = userGroupService.getUserDetailsWithUserId(userId, metadata, requester);

        assertThat(personDetails.getUserId(), is(userId));
        assertThat(personDetails.getFirstName(), is(FIRST_NAME));
        assertThat(personDetails.getLastName(), is(LAST_NAME));

    }


    @Test
    public void shouldGetGroupNamesForUser() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.get-groups-by-user")) {
                responsePayload = createObjectBuilder()
                        .add(GROUPS, createArrayBuilder()
                                .add(createObjectBuilder()
                                        .add(GROUP_NAME, "groupName1")
                                        .build())
                                .add(createObjectBuilder()
                                        .add(GROUP_NAME, "groupName2")
                                        .build())
                                .build())
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final List<String> groupNames = userGroupService.getGroupNamesForUser(userId, metadata, requester);

        assertThat(groupNames.size(), is(2));
        assertThat(groupNames.get(0), is("groupName1"));
        assertThat(groupNames.get(1), is("groupName2"));

    }

    @Test
    public void shouldThrowExceptionForGetGroupNamesForUserWhenResponsePayloadIsNull() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            return JsonEnvelope.envelopeFrom(envelope.metadata(), null);
        });

        assertThrows(UserGroupQueryException.class, () -> userGroupService.getGroupNamesForUser(userId, metadata, requester));
    }

    @Test
    public void shouldGetPermissions() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        final Permission permission = Permission.permission()
                .withSource(randomUUID())
                .withTarget(randomUUID())
                .withObject("caseDocument")
                .withAction("view")
                .withId(randomUUID())
                .withStatus(ADDED)
                .build();

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.get-logged-in-user-permissions")) {
                responsePayload = createObjectBuilder()
                        .add(PERMISSIONS, createArrayBuilder()
                                .add(createObjectBuilder()
                                        .add(ACTION, permission.getAction())
                                        .add(OBJECT, permission.getObject())
                                        .add(SOURCE, permission.getSource().toString())
                                        .add(TARGET, permission.getTarget().toString())
                                        .build())
                                .build())
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final List<Permission> permissions = userGroupService.getPermissions(userId, metadata, requester);

        assertThat(permissions.size(), is(1));
        assertThat(permissions.get(0).getAction(), is(permission.getAction()));
        assertThat(permissions.get(0).getObject(), is(permission.getObject()));
        assertThat(permissions.get(0).getSource(), is(permission.getSource()));
        assertThat(permissions.get(0).getTarget(), is(permission.getTarget()));

    }

    @Test
    public void shouldReturnEmptyPermissionListWhenPermissionArrayIsNullInResponse() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.get-logged-in-user-permissions")) {
                responsePayload = createObjectBuilder()
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final List<Permission> permissions = userGroupService.getPermissions(userId, metadata, requester);

        assertThat(permissions.size(), is(0));

    }

    @Test
    public void shouldGetUserOrgDetails() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final UUID organisationId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.get-organisation-details-for-user")) {
                responsePayload = createObjectBuilder()
                        .add(ORGANISATION_ID, organisationId.toString())
                        .add(ORGANISATION_NAME, ORGANISATION_NAME)
                        .add(ORGANISATION_TYPE, ORGANISATION_TYPE)
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });
        when(envelope.metadata()).thenReturn(metadata);
        final OrganisationDetails orgDetails = userGroupService.getUserOrgDetails(envelope, requester);

        assertThat(orgDetails.getId(), is(organisationId));
        assertThat(orgDetails.getName(), is(ORGANISATION_NAME));
        assertThat(orgDetails.getType(), is(ORGANISATION_TYPE));

    }

    @Test
    public void shouldGetUserGroupsForUser() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final UUID groupId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(requester.request(any(DefaultEnvelope.class))).thenAnswer(invocationOnMock -> {
            final Envelope envelope = (Envelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.get-logged-in-user-groups")) {
                responsePayload = createObjectBuilder()
                        .add(GROUPS, createArrayBuilder()
                                .add(createObjectBuilder()
                                        .add(GROUP_ID, groupId.toString())
                                        .add(GROUP_NAME, GROUP_NAME)
                                        .build())
                                .build())
                        .build();
            }
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });
        when(envelope.metadata()).thenReturn(metadata);
        final List<UsergroupDetails> userGroupDetails = userGroupService.getUserGroupsForUser(envelope, requester);

        assertThat(userGroupDetails.size(), is(1));
        assertThat(userGroupDetails.get(0).getGroupId(), is(groupId));
        assertThat(userGroupDetails.get(0).getGroupName(), is(GROUP_NAME));

    }


    private Metadata getMetaData(final UUID uuid, final UUID userId) {
        return Envelope
                .metadataBuilder()
                .withName("anyEventName")
                .withId(uuid)
                .withUserId(userId.toString())
                .build();
    }
}