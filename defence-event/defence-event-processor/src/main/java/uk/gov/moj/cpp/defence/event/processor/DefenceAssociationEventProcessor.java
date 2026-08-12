package uk.gov.moj.cpp.defence.event.processor;

import static org.apache.commons.lang3.BooleanUtils.toBoolean;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.event.processor.commands.RemoveAllGrantDefenceAccess;
import uk.gov.moj.cpp.defence.event.service.DefenceService;
import uk.gov.moj.cpp.defence.events.DefenceAssociationFailed;
import uk.gov.moj.cpp.defence.events.DefenceDisassociationFailed;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociated;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociationUnlockedBdf;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationDisassociated;
import uk.gov.moj.cpp.defence.service.UserGroupService;

import java.util.UUID;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_PROCESSOR)
public class DefenceAssociationEventProcessor {

    @Inject
    private Sender sender;


    @Inject
    private DefenceService defenceService;

    @Inject
    private UserGroupService usersGroupService;

    @Handles("defence.event.defence-organisation-associated")
    public void handleOrganisationAssociated(final Envelope<DefenceOrganisationAssociated> envelope) {

        final DefenceOrganisationAssociated defenceOrganisationAssociated = envelope.payload();


        final UUID caseId = defenceService.getCaseIdForDefenceClient(defenceOrganisationAssociated.getDefendantId(), envelope.metadata());

        final DefenceOrganisationAssociated defenceOrganisationAssociatedWithCaseId = DefenceOrganisationAssociated.defenceOrganisationAssociated()
                .withCaseId(caseId)
                .withUserId(defenceOrganisationAssociated.getUserId())
                .withDefendantId(defenceOrganisationAssociated.getDefendantId())
                .withOrganisationId(defenceOrganisationAssociated.getOrganisationId())
                .withOrganisationName(defenceOrganisationAssociated.getOrganisationName())
                .withStartDate(defenceOrganisationAssociated.getStartDate())
                .withLaaContractNumber(defenceOrganisationAssociated.getLaaContractNumber())
                .withRepresentationType(defenceOrganisationAssociated.getRepresentationType())
                .withIsLAA(toBoolean(defenceOrganisationAssociated.getIsLAA()))
                .build();
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("public.defence.defence-organisation-associated"),
                defenceOrganisationAssociatedWithCaseId));

        envelope.payload().getPermissions().forEach(permission -> usersGroupService.givePermission(permission, envelope.metadata(), sender));
    }

    @Handles("defence.event.defence-organisation-association-unlocked-bdf")
    public void bdfProcessUnlockDefenceOrganisationAssociation(final Envelope<DefenceOrganisationAssociationUnlockedBdf> envelope) {
        envelope.payload().getPermissions().forEach(permission -> usersGroupService.givePermission(permission, envelope.metadata(), sender));
    }

    @Handles("defence.event.defence-organisation-disassociated")
    public void handleOrganisationDisassociated(final Envelope<DefenceOrganisationDisassociated> envelope) {

        final DefenceOrganisationDisassociated defenceOrganisationDisassociated = envelope.payload();

        final UUID caseId = defenceService.getCaseIdForDefenceClient(defenceOrganisationDisassociated.getDefendantId(), envelope.metadata());

        final DefenceOrganisationDisassociated defenceOrganisationDisassociatedWithCaseId = DefenceOrganisationDisassociated.defenceOrganisationDisassociated()
                .withCaseId(caseId)
                .withDefendantId(defenceOrganisationDisassociated.getDefendantId())
                .withEndDate(defenceOrganisationDisassociated.getEndDate())
                .withOrganisationId(defenceOrganisationDisassociated.getOrganisationId())
                .withUserId(defenceOrganisationDisassociated.getUserId())
                .withIsLAA(toBoolean(defenceOrganisationDisassociated.getIsLAA()))
                .build();

        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("public.defence.defence-organisation-disassociated"),
                defenceOrganisationDisassociatedWithCaseId));



        final RemoveAllGrantDefenceAccess removeAllGrantDefenceAccess = RemoveAllGrantDefenceAccess.removeAllGrantDefenceAccess()
                .withDefenceClientId(defenceOrganisationDisassociated.getDefendantId())
                .build();

        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.remove-all-grant-defence-access"),
                removeAllGrantDefenceAccess));


        envelope.payload().getPermissions().forEach(permission -> usersGroupService.givePermission(permission, envelope.metadata(), sender));

    }

    @Handles("defence.event.defence-association-failed")
    public void handleFailedOrganisationAssociated(final Envelope<DefenceAssociationFailed> envelope) {

        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("public.defence.defence-organisation-association-failed"),
                envelope.payload()));

    }

    @Handles("defence.event.defence-disassociation-failed")
    public void handleFailedOrganisationDisassociated(final Envelope<DefenceDisassociationFailed> envelope) {

        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("public.defence.defence-organisation-disassociation-failed"),
                envelope.payload()));

    }
}
