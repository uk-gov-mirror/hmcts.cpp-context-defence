package uk.gov.moj.cpp.defence.command.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultEnvelope;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.moj.cpp.defence.command.api.service.OrganisationService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class DefencePleaCommandApiTest {

    @Mock
    private Sender sender;

    @Mock
    private OrganisationService organisationService;

    @Mock
    private Requester requester;

    @Mock
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Captor
    private ArgumentCaptor<DefaultEnvelope> envelopeCaptor;

    @InjectMocks
    private DefencePleaCommandApi defencePleaApi;

    @Test
    public void shouldCreatePleaForDefendantsOffence() {
        final UUID userId = randomUUID();
        final UUID defendantId = randomUUID();
        List<UUID> defendantIds = Collections.singletonList(defendantId);
        final JsonObject payload = createObjectBuilder()
                .add("organisationId", randomUUID().toString())
                .add("defendantId", defendantId.toString())
                .build();
        when(organisationService.getAssociatedDefendants(any(), any())).thenReturn(defendantIds);
        final JsonEnvelope commandEnvelope = createEnvelop(userId, payload, "add");
        defencePleaApi.createPleaForDefendantsOffence(commandEnvelope);
        verify(sender, times(1)).send(any());
        verify(sender).send(envelopeCaptor.capture());

        final DefaultEnvelope capturedEnvelope = envelopeCaptor.getValue();
        assertThat(capturedEnvelope.metadata().name(), is("defence.command.add-offence-pleas"));
        assertThat(((JsonObject) capturedEnvelope.payload()).get("pleasAllocation"), is(payload));
    }

    @Test
    public void shouldThorExceptionForCreatePleaForDefendantsOffenceWhenUserNotHavePermission() {
        final UUID userId = randomUUID();
        final UUID defendantId = randomUUID();
        List<UUID> defendantIds = Collections.singletonList(defendantId);
        final JsonObject payload = createObjectBuilder()
                .add("organisationId", randomUUID().toString())
                .add("defendantId", defendantId.toString())
                .build();
        when(organisationService.getAssociatedDefendants(any(), any())).thenReturn(Collections.emptyList());
        final JsonEnvelope commandEnvelope = createEnvelop(userId, payload, "add");
        assertThrows(ForbiddenRequestException.class, () -> defencePleaApi.createPleaForDefendantsOffence(commandEnvelope));
        verify(sender, times(0)).send(any());
    }

    @Test
    public void shouldUpdatePleaForDefendantsOffence() {

        final UUID userId = randomUUID();
        final UUID defendantId = randomUUID();
        List<UUID> defendantIds = Collections.singletonList(defendantId);
        final JsonObject payload = createObjectBuilder()
                .add("organisationId", randomUUID().toString())
                .add("defendantId", defendantId.toString())
                .build();

        final JsonEnvelope commandEnvelope = createEnvelop(userId, payload, "update");
        when(organisationService.getAssociatedDefendants(any(), any())).thenReturn(defendantIds);
        defencePleaApi.updatePleaForDefendantsOffence(commandEnvelope);
        verify(sender, times(1)).send(any());
        verify(sender).send(envelopeCaptor.capture());

        final DefaultEnvelope capturedEnvelope = envelopeCaptor.getValue();
        assertThat(capturedEnvelope.metadata().name(), is("defence.command.update-offence-pleas"));
        assertThat(((JsonObject) capturedEnvelope.payload()).get("pleasAllocation"), is(payload));

    }

    @Test
    public void shouldThrowExceptionForUpdatePleaForDefendantsOffenceWhenUserNotHavePermission() {
        final UUID userId = randomUUID();
        final UUID defendantId = randomUUID();
        final JsonObject payload = createObjectBuilder()
                .add("organisationId", randomUUID().toString())
                .add("defendantId", defendantId.toString())
                .build();

        final JsonEnvelope commandEnvelope = createEnvelop(userId, payload, "update");
        when(organisationService.getAssociatedDefendants(any(), any())).thenReturn(Collections.emptyList());
        assertThrows(ForbiddenRequestException.class, () -> defencePleaApi.updatePleaForDefendantsOffence(commandEnvelope));

    }

    private JsonEnvelope createEnvelop(final UUID userId, final JsonObject payload, String commandType) {
        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName("defence.command.add-offence-pleas")
                .withId(randomUUID())
                .withUserId(userId.toString())
                .build();

        final JsonEnvelope commandEnvelope = new DefaultJsonEnvelopeProvider().envelopeFrom(metadata, payload);
        return commandEnvelope;
    }

}
