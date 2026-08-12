package uk.gov.moj.cpp.defence.command.api;

import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;

import jakarta.inject.Inject;

@ServiceComponent(COMMAND_API)
public class AssociateDefenceOrganisationApi {


    @Inject
    private Sender sender;

    @Handles("defence.associate-defence-organisation")
    public void handle(final JsonEnvelope envelope) {
        sender.send(Enveloper.envelop(envelope.payloadAsJsonObject())
                .withName("defence.command.associate-defence-organisation")
                .withMetadataFrom(envelope));
    }

    @Handles("defence.bdf.unlock-defence-organisation-association")
    public void bdfHandleUnlockDefenceOrganisationAssociation(final JsonEnvelope envelope) {
        sender.send(Enveloper.envelop(envelope.payloadAsJsonObject())
                .withName("defence.bdf.command.unlock-defence-organisation-association")
                .withMetadataFrom(envelope));
    }
}
