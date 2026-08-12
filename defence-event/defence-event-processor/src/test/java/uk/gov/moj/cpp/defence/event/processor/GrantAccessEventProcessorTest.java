package uk.gov.moj.cpp.defence.event.processor;

import static java.time.ZonedDateTime.now;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.times;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.messaging.spi.DefaultJsonMetadata.metadataBuilder;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.preparePermissionList;

import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.cps.defence.events.AccessGrantRemoved;
import uk.gov.justice.cps.defence.events.AccessGranted;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.events.Status;
import uk.gov.moj.cpp.defence.json.schema.event.DefenceAccessGrantRemoved;
import uk.gov.moj.cpp.defence.json.schema.event.DefenceAccessGranted;
import uk.gov.moj.cpp.defence.service.UserGroupService;

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
public class GrantAccessEventProcessorTest {

    private static final UUID DEFENCE_CLIENT_ID = randomUUID();

    @Captor
    ArgumentCaptor<Envelope<?>> envelopeArgumentCaptor;
    @Captor
    ArgumentCaptor<Permission> permissionArgumentCaptor;
    @Mock
    private Sender sender;
    @Mock
    private UserGroupService usersGroupService;

    @InjectMocks
    private GrantAccessEventProcessor grantAccessEventProcessor;

    @Test
    public void shouldHandleAccessGrantedEventAndCallUsersGroupsAndRaisePublicEvent() {

        final AccessGranted accessGranted = AccessGranted.accessGranted()
                .withGranteeDetails(PersonDetails.personDetails().withUserId(randomUUID()).build())
                .withGranterDetails(PersonDetails.personDetails().withUserId(randomUUID()).build())
                .withGranteeOrganisation(Organisation.organisation().withOrgId(randomUUID()).build())
                .withPermissions(preparePermissionList(randomUUID(), randomUUID(), true, empty()))
                .build();
        final Envelope<AccessGranted> envelope = createTypedEnvelope(accessGranted);
        grantAccessEventProcessor.handleAccessGrantedEvent(envelope);


        verify(usersGroupService, times(3)).givePermission(permissionArgumentCaptor.capture(), any(), any());
        verify(sender, times(1)).send(envelopeArgumentCaptor.capture());

        final Envelope<DefenceAccessGranted> defenceAccessGrantedEnvelope = (Envelope<DefenceAccessGranted>) envelopeArgumentCaptor.getValue();

        final DefenceAccessGranted payload = defenceAccessGrantedEnvelope.payload();
        assertThat(payload.getGranteeDetails().getUserId(), is(accessGranted.getGranteeDetails().getUserId()));
        assertThat(defenceAccessGrantedEnvelope.metadata().name(), is("public.defence.event.defence-access-granted"));

    }

    @Test
    public void shouldHandleAccessGrantRemovedEventAndCallUsersGroupsAndRaisePublicEvent() {

        final UUID userId = randomUUID();
        final UUID defendantId = randomUUID();

        final AccessGrantRemoved accessGrantRemoved = AccessGrantRemoved.accessGrantRemoved()
                .withPermissions(preparePermissionList(defendantId, userId, true, empty()))
                .build();
        final Envelope<AccessGrantRemoved> envelope = createTypedEnvelope(accessGrantRemoved);
        grantAccessEventProcessor.handleAccessGrantRemovedEvent(envelope);


        verify(usersGroupService, times(3)).givePermission(permissionArgumentCaptor.capture(), any(), any());
        verify(sender, times(1)).send(envelopeArgumentCaptor.capture());


        final List<Permission> permissions = (List<Permission>) permissionArgumentCaptor.getAllValues();
        assertThat(permissions.size(), is(3));
        permissions.forEach(
                permission -> {
                    assertThat(permission.getStatus(), is(Status.DELETED));
                    assertThat(permission.getSource(), is(userId));
                    assertThat(permission.getTarget(), is(defendantId));
                }
        );


        final Envelope<DefenceAccessGrantRemoved> defenceAccessGrantRemovedEnvelope = (Envelope<DefenceAccessGrantRemoved>) envelopeArgumentCaptor.getValue();

        final DefenceAccessGrantRemoved payload = defenceAccessGrantRemovedEnvelope.payload();
        assertThat(payload.getDefenceClientId(), is(accessGrantRemoved.getPermissions().get(0).getTarget()));
        assertThat(payload.getGranteeUserId(), is(accessGrantRemoved.getPermissions().get(0).getSource()));
        assertThat(defenceAccessGrantRemovedEnvelope.metadata().name(), is("public.defence.event.defence-access-grant-removed"));

    }

    @Test
    public void shouldHandleGrantAccessFailedEvent_andGenerateAPublicEvent() {
        final String privateEventName = "defence.events.grant-access-failed";
        final String publicEventName = "public.defence.events.grant-access-failed";

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(metadataBuilder()
                        .withName(privateEventName)
                        .withId(UUID.randomUUID())
                        .build(),
                createObjectBuilder().build());

        //test
        grantAccessEventProcessor.handleGrantAccessFailedEvent(envelope);

        //assert
        verify(sender, times(1)).send(envelopeArgumentCaptor.capture());
        assertThat(envelopeArgumentCaptor.getValue().metadata().name(), Matchers.is(publicEventName));

    }

    @Test
    public void shouldHandleUserAlreadyGrantedFailedEvent_andGenerateAPublicEvent() {
        final String privateEventName = "defence.event.user-already-granted";
        final String publicEventName = "public.defence.event.user-already-granted";

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(metadataBuilder()
                        .withName(privateEventName)
                        .withId(UUID.randomUUID())
                        .build(),
                createObjectBuilder().build());

        //test
        grantAccessEventProcessor.handleUserAlreadyGrantedFailedEvent(envelope);

        //assert
        verify(sender, times(1)).send(envelopeArgumentCaptor.capture());
        assertThat(envelopeArgumentCaptor.getValue().metadata().name(), Matchers.is(publicEventName));

    }

    @Test
    public void shouldHandleGranteeUserNotInAllowedGroupsFailedEvent_andGenerateAPublicEvent() {
        final String privateEventName = "defence.event.grantee-user-not-in-allowed-groups";
        final String publicEventName = "public.defence.event.grantee-user-not-in-allowed-groups";

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(metadataBuilder()
                        .withName(privateEventName)
                        .withId(UUID.randomUUID())
                        .build(),
                createObjectBuilder().build());

        //test
        grantAccessEventProcessor.handleGranteeUserNotInAllowedGroupsFailedEvent(envelope);

        //assert
        verify(sender, times(1)).send(envelopeArgumentCaptor.capture());
        assertThat(envelopeArgumentCaptor.getValue().metadata().name(), Matchers.is(publicEventName));

    }

    @Test
    public void shouldHandleUserNotFoundFailedEvent_andGenerateAPublicEvent() {
        final String privateEventName = "handleUserNotFoundFailedEvent";
        final String publicEventName = "public.defence.event.user-not-found";

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(metadataBuilder()
                        .withName(privateEventName)
                        .withId(UUID.randomUUID())
                        .build(),
                createObjectBuilder().build());

        //test
        grantAccessEventProcessor.handleUserNotFoundFailedEvent(envelope);

        //assert
        verify(sender, times(1)).send(envelopeArgumentCaptor.capture());
        assertThat(envelopeArgumentCaptor.getValue().metadata().name(), Matchers.is(publicEventName));

    }

    @Test
    public void shouldhandledAssigneeForDefenceIsProsecutingCaseEvent_andGenerateAPublicEvent() {
        final String privateEventName = "defence.event.assignee-for-defence-is-prosecuting-case";
        final String publicEventName = "public.defence.event.assignee-for-defence-is-prosecuting-case";
        final UUID userId = randomUUID();
        final UUID caseId = randomUUID();
        final String email = "email1@hmcts.net";

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(metadataBuilder()
                        .withName(privateEventName)
                        .withId(UUID.randomUUID())
                        .build(),
                createObjectBuilder()
                        .add("userId", userId.toString())
                        .add("caseId", caseId.toString())
                        .add("email", email)
                        .build());

        //test
        grantAccessEventProcessor.handledAssigneeForDefenceIsProsecutingCaseEvent(envelope);

        //assert
        verify(sender, times(1)).send(envelopeArgumentCaptor.capture());
        assertThat(envelopeArgumentCaptor.getValue().metadata().name(), Matchers.is(publicEventName));

        final JsonObject body = (JsonObject) envelopeArgumentCaptor.getValue().payload();
        assertThat(body.getString("email"), Matchers.is(email));
        assertThat(body.getString("userId"), Matchers.is(userId.toString()));
        assertThat(body.getString("caseId"), Matchers.is(caseId.toString()));

    }

    private <T> Envelope<T> createTypedEnvelope(final T t) {

        final Metadata metadata = Envelope.metadataBuilder()
                .withId(DEFENCE_CLIENT_ID)
                .withName("actionName")
                .createdAt(now())
                .build();
        return Envelope.envelopeFrom(metadata, t);
    }
}
