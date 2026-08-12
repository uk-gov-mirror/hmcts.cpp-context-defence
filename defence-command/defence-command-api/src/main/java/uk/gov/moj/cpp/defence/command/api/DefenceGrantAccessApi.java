package uk.gov.moj.cpp.defence.command.api;


import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;

import uk.gov.justice.cps.defence.GrantDefenceAccess;
import uk.gov.justice.cps.defence.RemoveGrantDefenceAccess;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;

import jakarta.inject.Inject;

@ServiceComponent(COMMAND_API)
public class DefenceGrantAccessApi {

    @Inject
    private Sender sender;

    @Handles("defence.grant-defence-access")
    public void grantDefenceAccess(final Envelope<GrantDefenceAccess> envelope) {
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.grant-defence-access"),
                envelope.payload()));
    }

    @Handles("defence.remove-grant-defence-access")
    public void removeGrantDefenceAccess(final Envelope<RemoveGrantDefenceAccess> envelope) {
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.remove-grant-defence-access"),
                envelope.payload()));
    }


}
