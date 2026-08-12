package uk.gov.moj.cpp.defence.command.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;

import uk.gov.justice.cps.defence.AssociateOrganisationBdf;
import uk.gov.justice.cps.defence.RepresentationType;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.progression.json.schema.event.ProsecutionCaseCreatedBdf;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseCreatedCommandApiTest {

    @Mock
    private Sender sender;

    @Captor
    private ArgumentCaptor<Envelope<JsonObject>> envelopeArgumentCaptor;

    @InjectMocks
    CaseCreatedCommandApi caseCreatedCommandApi;

    @Test
    void shouldHandleCaseCreatedBDF() {
        final Metadata metadata =  Envelope
                .metadataBuilder()
                .withName("defence.prosecution-case-created-bdf")
                .withId(randomUUID())
                .withUserId(randomUUID().toString())
                .build();

        final Envelope<ProsecutionCaseCreatedBdf> envelope = Envelope.envelopeFrom(metadata, new ProsecutionCaseCreatedBdf(randomUUID(), randomUUID()));

        caseCreatedCommandApi.handleProsecutionCaseReceived(envelope);

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is("defence.command.prosecution-case-created-bdf"));
    }

    @Test
    void shouldHandleAssociateOrganisationBdf() {
        final Metadata metadata =  Envelope
                .metadataBuilder()
                .withName("defence.associate-organisation-bdf")
                .withId(randomUUID())
                .withUserId(randomUUID().toString())
                .build();

        final Envelope<AssociateOrganisationBdf> envelope = Envelope.envelopeFrom(metadata, new AssociateOrganisationBdf(randomUUID(), randomUUID(), true, "LAA", "Status", randomUUID(), "org", null, RepresentationType.REPRESENTATION_ORDER, "", randomUUID()));

        caseCreatedCommandApi.handleAssociateOrganisationBdf(envelope);

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is("defence.command.associate-organisation-bdf"));
    }
}
