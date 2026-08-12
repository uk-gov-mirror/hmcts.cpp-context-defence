package uk.gov.moj.cpp.defence.event.processor;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_PROCESSOR)
public class IdpcAccessRecordedEventProcessor {

    @Inject
    private Sender sender;

    @Inject
    private Enveloper enveloper;

    @Handles("defence.event.idpc-access-by-organisation-recorded")
    public void handleIdpcAccessByOrganisationRecorded(final JsonEnvelope jsonEnvelope) {
        sender.send(envelopeFrom(metadataFrom(jsonEnvelope.metadata()).withName("public.defence.event.idpc-accessed-by-organisation"),
                jsonEnvelope.payload()));
    }
}
