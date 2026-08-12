package uk.gov.moj.cpp.defence.query.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory.createEnveloper;

import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;

import java.util.Optional;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProgressionServiceTest {

    private static final String DEFENCE_QUERY_DEFENCE_CLIENT_ID = "defence.query.defence-client-id";

    @Spy
    private final Enveloper enveloper = createEnveloper();

    @Mock
    private Requester requester;

    @Spy
    @InjectMocks
    private ProgressionQueryService progressionQueryService;

    @Mock
    private JsonEnvelope finalEnvelope;

    @BeforeEach
    public void initMocks() {
        final JsonObject jsonObject = createObjectBuilder()
                .add("prosecutionCase", createObjectBuilder().add("prosecutor",
                        createObjectBuilder().add("prosecutorId", randomUUID().toString())
                                .add("prosecutorName", "CPS").build()))
                .build();
        when(finalEnvelope.payloadAsJsonObject()).thenReturn(jsonObject);
        when(requester.requestAsAdmin(any())).thenReturn(finalEnvelope);
    }

    @Test
    public void shouldGetProsecutionCaseJsonObjectWhenProsecutorIsPresent() {
        final JsonEnvelope jsonEnvelope = getEnvelope(DEFENCE_QUERY_DEFENCE_CLIENT_ID);

        final Optional<JsonObject> prosecutionCaseDetailById = progressionQueryService.getProsecutionCaseDetailById(jsonEnvelope, randomUUID().toString());

        assertThat(prosecutionCaseDetailById.get().getJsonObject("prosecutionCase"), is(notNullValue()));
        assertThat(prosecutionCaseDetailById.get().getJsonObject("prosecutionCase").getJsonObject("prosecutor"), is(notNullValue()));
        assertThat(prosecutionCaseDetailById.get().getJsonObject("prosecutionCase").getJsonObject("prosecutor").getJsonString("prosecutorName").getString(), is("CPS"));
        assertThat(prosecutionCaseDetailById.get().getJsonObject("prosecutionCase").getJsonObject("prosecutor").getJsonString("prosecutorId"), is(notNullValue()));
    }

    @Test
    public void shouldGetProsecutionCaseJsonObjectWithoutProsecutorWhenProsecutorIsNotPresent() {

        final JsonObject jsonObject = createObjectBuilder()
                .add("prosecutionCase", createObjectBuilder().build())
                .build();
        when(finalEnvelope.payloadAsJsonObject()).thenReturn(jsonObject);
        final JsonEnvelope jsonEnvelope = getEnvelope(DEFENCE_QUERY_DEFENCE_CLIENT_ID);

        final Optional<JsonObject> prosecutionCaseDetailById = progressionQueryService.getProsecutionCaseDetailById(jsonEnvelope, randomUUID().toString());

        assertThat(prosecutionCaseDetailById.get().getJsonObject("prosecutionCase").getJsonObject("prosecutor"), is(nullValue()));
    }

    private JsonEnvelope getEnvelope(final String name) {
        return envelopeFrom(
                metadataBuilder().withId(randomUUID()).withName(name).build(),
                createObjectBuilder().build());
    }

}
