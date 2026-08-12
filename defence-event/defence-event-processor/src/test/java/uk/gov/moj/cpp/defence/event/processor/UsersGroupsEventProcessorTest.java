package uk.gov.moj.cpp.defence.event.processor;

import static com.google.common.collect.ImmutableList.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.event.service.DefenceService;

import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UsersGroupsEventProcessorTest {

    private static final String LAA_CONTRACT_NUMBERS_LABEL = "laaContractNumbers";
    private static final String LAA_CONTRACT_NUMBER_LABEL = "laaContractNumber";
    private static final String ORGANISATION_ID_LABEL = "organisationId";
    private static final String ORGANISATION_NAME_LABEL = "organisationName";
    private static final String DEFENDANT_ID_LABEL = "defendantId";

    private static final String LAA_CONTRACT_NUMBER = "LAA3456";

    @Mock
    private DefenceService defenceService;

    @Mock
    private Sender sender;

    @InjectMocks
    private UsersGroupsEventProcessor usersGroupsEventProcessor;

    @Captor
    private ArgumentCaptor<Envelope<JsonObject>> envelopeCaptor;

    @Test
    public void shouldSetUpLAAOrganisation() {
        UUID organisationId = UUID.randomUUID();
        String organisationName = "Org1";
        UUID defendantId = UUID.randomUUID();

        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder().withId(randomUUID()).withName("public.usersgroups.organisation-created").build(),
                createPayloadForOrganisationSetup(organisationId, organisationName));

        when(defenceService.getDefendantsByLAAContractNumber(jsonEnvelope, of(LAA_CONTRACT_NUMBER))).thenReturn(createDefendantLaaNumberPayload(defendantId));

        usersGroupsEventProcessor.setUpLAAOrganisation(jsonEnvelope);

        verify(sender).send(envelopeCaptor.capture());

        final Envelope<JsonObject> command = envelopeCaptor.getValue();
        assertThat(command.metadata().name(), is("defence.command.handler.associate-orphaned-case"));
        JsonObject commandAssociateOrphanedCasePayload = command.payload();
        assertThat(commandAssociateOrphanedCasePayload.getString(LAA_CONTRACT_NUMBER_LABEL), is(LAA_CONTRACT_NUMBER));
        assertThat(commandAssociateOrphanedCasePayload.getString(ORGANISATION_ID_LABEL), is(organisationId.toString()));
        assertThat(commandAssociateOrphanedCasePayload.getString(ORGANISATION_NAME_LABEL), is(organisationName));
        assertThat(commandAssociateOrphanedCasePayload.getString(DEFENDANT_ID_LABEL), is(defendantId.toString()));
    }

    private JsonArray createDefendantLaaNumberPayload(final UUID defendantId) {
        return createArrayBuilder().add(createObjectBuilder().add("id", defendantId.toString()).add("laaContractNumber", LAA_CONTRACT_NUMBER).build()).build();
    }

    private JsonObject createPayloadForOrganisationSetup(final UUID organisationId, final String organisationName) {
        return createObjectBuilder()
                .add("organisationDetails", createObjectBuilder()
                        .add(ORGANISATION_ID_LABEL, organisationId.toString())
                        .add(ORGANISATION_NAME_LABEL, organisationName)
                        .add(LAA_CONTRACT_NUMBERS_LABEL, createArrayBuilder().add(LAA_CONTRACT_NUMBER))
                        .add("timeTriggered", "2011-12-03T10:15:30+01:00")
                        .add("organisationType", "LEGAL_ORGANISATION")
                        .add("addressLine1", "Address Line1")
                        .add("addressLine4", "Address Line4")
                        .add("addressPostcode", "SE14 2AB")
                        .add("phoneNumber", "080012345678")
                        .add("email", "joe@example.com")
                        .build())
                .build();

    }
}