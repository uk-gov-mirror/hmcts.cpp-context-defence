package uk.gov.moj.cpp.defence.command.api;

import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;


import uk.gov.justice.cps.defence.AssociateOrganisationBdf;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.progression.json.schema.event.ProsecutionCaseCreatedBdf;

import jakarta.inject.Inject;

@ServiceComponent(COMMAND_API)
public class CaseCreatedCommandApi {

    @Inject
    private Sender sender;

    @Handles("defence.prosecution-case-created-bdf")
    public void handleProsecutionCaseReceived(final Envelope<ProsecutionCaseCreatedBdf> envelope) {
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.prosecution-case-created-bdf"),
                envelope.payload()));
    }

    @Handles("defence.associate-organisation-bdf")
    public void handleAssociateOrganisationBdf(final Envelope<AssociateOrganisationBdf> envelope){
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.associate-organisation-bdf"),
                envelope.payload()));
    }
}
