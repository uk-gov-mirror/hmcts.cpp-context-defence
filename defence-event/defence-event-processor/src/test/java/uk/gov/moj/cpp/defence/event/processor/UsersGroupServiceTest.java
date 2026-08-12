package uk.gov.moj.cpp.defence.event.processor;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUIDAndName;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.event.service.UsersGroupService;

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
public class UsersGroupServiceTest {

    @InjectMocks
    private UsersGroupService usersGroupService;

    @Mock
    private Requester requester;

    @Mock
    private JsonEnvelope jsonEnvelope;

    @SuppressWarnings("rawtypes")
    @Mock
    private Envelope envelope;

    @Mock
    private JsonObject jsonObject;

    @Captor
    private ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgumentCaptor;

    @Captor
    private ArgumentCaptor<Envelope<JsonObject>> envelopeArgumentCaptor;

    private static final UUID ORGANISATION_ID = UUID.randomUUID();

    @Test
    public void shouldGetGroupsWithOrganisation() {
        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                metadataWithRandomUUIDAndName(), createObjectBuilder().build());

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);
        when(envelope.payload()).thenReturn(buildGetOrganisationsDetailsForIds());
        UUID organisationId = usersGroupService.getOrganisationByType(jsonEnvelope.metadata());
        verify(requester).requestAsAdmin(envelopeArgumentCaptor.capture(), any());
        assertThat(envelopeArgumentCaptor.getValue(), notNullValue());
        assertThat(organisationId, is(ORGANISATION_ID));
    }

    private JsonObject buildGetOrganisationsDetailsForIds() {
        return createObjectBuilder()
                .add("organisations", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("organisationId", ORGANISATION_ID.toString())
                        )
                        .add(createObjectBuilder()
                                .add("organisationId", "1fc69990-bf59-4c4a-9489-d766b9abde9b")
                        ))
                .build();
    }


}
