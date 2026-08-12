package uk.gov.moj.cpp.defence.event.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.query.view.DefenceQueryView;
import uk.gov.moj.cpp.defence.query.view.DefendantQueryView;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;

@ExtendWith(MockitoExtension.class)
public class DefenceServiceTest {

    @InjectMocks
    private DefenceService defenceService;

    @Mock
    private DefenceQueryView defenceQueryView;

    @Mock
    private DefendantQueryView defendantQueryView;

    @Mock
    private Envelope<DefenceClient> defenceClientEnvelope;


    @Mock
    private JsonEnvelope inputJsonEnvelope;

    @Mock
    private JsonEnvelope outputJsonEnvelope;


    @Test
    public void shouldGetCaseIdForDefenceClient() {

        final UUID defendantId = randomUUID();

        final DefenceClient defenceClient = new DefenceClient();
        defenceClient.setCaseId(randomUUID());

        final Metadata metadata = metadataBuilder()
                .withName("defence.command.prosecution-case-receive-details")
                .withId(randomUUID())
                .build();
        when(defenceClientEnvelope.payload()).thenReturn(defenceClient);
        when(defenceQueryView.getDefenceClientByDefendantId(any())).thenReturn(defenceClientEnvelope);

        final UUID caseId = defenceService.getCaseIdForDefenceClient(defendantId, metadata);

        assertThat(caseId, is(defenceClient.getCaseId()));
    }

    @Test
    public void shouldGetDefendantsByLAAContractNumber() {

        final String laaContractNumbers = randomUUID().toString();

        final Metadata metadata = metadataBuilder()
                .withName("defence.command.prosecution-case-receive-details")
                .withId(randomUUID())
                .build();

        final JsonObject defenceClient = createObjectBuilder().add("defendants", createArrayBuilder()
                .add(createObjectBuilder()
                        .add("id", randomUUID().toString())
                        .build())
                .add(createObjectBuilder()
                        .add("id", randomUUID().toString())
                        .build())).build();

        when(inputJsonEnvelope.metadata()).thenReturn(metadata);
        when(outputJsonEnvelope.payloadAsJsonObject()).thenReturn(defenceClient);
        when(defendantQueryView.getDefendantsByLAAContractNumber(any())).thenReturn(outputJsonEnvelope);

        final JsonArray defendantsJsonArray = defenceService.getDefendantsByLAAContractNumber(inputJsonEnvelope, asList(laaContractNumbers));

        assertThat(defendantsJsonArray.size(), is(2));
    }
}