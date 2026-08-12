package uk.gov.moj.cpp.defence.event.processor;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;

import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.cps.defence.events.AccessGrantRemoved;
import uk.gov.justice.cps.defence.events.AccessGranted;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.events.Status;
import uk.gov.moj.cpp.defence.json.schema.event.DefenceAccessGrantRemoved;
import uk.gov.moj.cpp.defence.json.schema.event.DefenceAccessGranted;
import uk.gov.moj.cpp.defence.service.UserGroupService;

import java.util.List;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_PROCESSOR)
public class GrantAccessEventProcessor {


    @Inject
    private Sender sender;

    @Inject
    private UserGroupService usersGroupService;


    @Handles("defence.event.user-not-found")
    public void handleUserNotFoundFailedEvent(final JsonEnvelope envelope) {
        final Metadata metadata = Envelope.metadataFrom(envelope.metadata()).withName("public.defence.event.user-not-found").build();
        sender.send(JsonEnvelope.envelopeFrom(metadata, envelope.payload()));
    }

    @Handles("defence.event.assignee-for-defence-is-prosecuting-case")
    public void handledAssigneeForDefenceIsProsecutingCaseEvent(final JsonEnvelope envelope) {
        final Metadata metadata = Envelope.metadataFrom(envelope.metadata()).withName("public.defence.event.assignee-for-defence-is-prosecuting-case").build();
        sender.send(JsonEnvelope.envelopeFrom(metadata, envelope.payload()));
    }

    @Handles("defence.event.grantee-user-not-in-allowed-groups")
    public void handleGranteeUserNotInAllowedGroupsFailedEvent(final JsonEnvelope envelope) {
        final Metadata metadata = Envelope.metadataFrom(envelope.metadata()).withName("public.defence.event.grantee-user-not-in-allowed-groups").build();
        sender.send(JsonEnvelope.envelopeFrom(metadata, envelope.payload()));
    }

    @Handles("defence.event.user-already-granted")
    public void handleUserAlreadyGrantedFailedEvent(final JsonEnvelope envelope) {
        final Metadata metadata = Envelope.metadataFrom(envelope.metadata()).withName("public.defence.event.user-already-granted").build();
        sender.send(JsonEnvelope.envelopeFrom(metadata, envelope.payload()));
    }

    @Handles("defence.events.grant-access-failed")
    public void handleGrantAccessFailedEvent(final JsonEnvelope envelope) {
        final Metadata metadata = Envelope.metadataFrom(envelope.metadata()).withName("public.defence.events.grant-access-failed").build();
        sender.send(JsonEnvelope.envelopeFrom(metadata, envelope.payload()));
    }

    @Handles("defence.event.access-granted")
    public void handleAccessGrantedEvent(final Envelope<AccessGranted> envelope) {
        final AccessGranted accessGranted = envelope.payload();

        accessGranted.getPermissions().forEach(permission ->
                usersGroupService.givePermission(permission,envelope.metadata(),sender)
        );

        final DefenceAccessGranted defenceAccessGranted = DefenceAccessGranted.defenceAccessGranted().withGranteeDetails(accessGranted.getGranteeDetails()).build();

        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("public.defence.event.defence-access-granted"),
                defenceAccessGranted));

    }

    @Handles("defence.event.access-grant-removed")
    public void handleAccessGrantRemovedEvent(final Envelope<AccessGrantRemoved> envelope) {
        final List<Permission> accessGrantPermission = envelope.payload().getPermissions();

        accessGrantPermission.forEach(permissionObj -> {
            final Permission permission = Permission.permission()
                    .withId(permissionObj.getId())
                    .withAction(permissionObj.getAction())
                    .withObject(permissionObj.getObject())
                    .withDescription(permissionObj.getDescription())
                    .withSource(permissionObj.getSource())
                    .withStatus(Status.DELETED)
                    .withTarget(permissionObj.getTarget())
                    .build();
            usersGroupService.givePermission(permission,envelope.metadata(),sender);
        });


        final DefenceAccessGrantRemoved defenceAccessGrantRemoved = DefenceAccessGrantRemoved.defenceAccessGrantRemoved()
                .withDefenceClientId(accessGrantPermission.get(0).getTarget())
                .withGranteeUserId(accessGrantPermission.get(0).getSource())
                .build();

        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("public.defence.event.defence-access-grant-removed"),
                defenceAccessGrantRemoved));

    }


}
