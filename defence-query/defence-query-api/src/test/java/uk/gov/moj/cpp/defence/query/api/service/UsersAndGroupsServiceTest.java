package uk.gov.moj.cpp.defence.query.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import jakarta.json.JsonObject;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UsersAndGroupsServiceTest {

    public static final String ORGANISATION_NAME = "organisationName";
    public static final String ADDRESS_LINE_1 = "addressLine1";
    public static final String ADDRESS_POSTCODE = "addressPostcode";
    public static final String ORGANISATION_ID = "organisationId";
    @Mock
    private Requester requester;

    @Spy
    @InjectMocks
    private UsersAndGroupsService usersAndGroupsService;

    @Test
    public void shouldGetOrganisationDetails() {
        final UUID organisationId = randomUUID();
        final JsonEnvelope requestEnvelope = mock(JsonEnvelope.class);
        final Envelope responseEnvelope = mock(Envelope.class);

        final JsonObject requestJsonObject = createObjectBuilder()
                .add(ORGANISATION_ID, organisationId.toString())
                .build();

        final JsonObject responseJsonObject = createObjectBuilder()
                .add(ORGANISATION_NAME, ORGANISATION_NAME)
                .add(ADDRESS_LINE_1, ADDRESS_LINE_1)
                .add(ADDRESS_POSTCODE, ADDRESS_POSTCODE)
                .build();

        when(responseEnvelope.payload()).thenReturn(responseJsonObject);
        when(requestEnvelope.payloadAsJsonObject()).thenReturn(requestJsonObject);
        when(requestEnvelope.metadata()).thenReturn(getMetaData(randomUUID(), randomUUID()));
        when(requester.requestAsAdmin(any(), any())).thenReturn(responseEnvelope);

        final JsonObject organisationDetails = usersAndGroupsService.getOrganisationDetails(requestEnvelope);

        assertThat(organisationDetails.getString(ADDRESS_LINE_1), is(ADDRESS_LINE_1));
        assertThat(organisationDetails.getString(ADDRESS_POSTCODE), is(ADDRESS_POSTCODE));
    }

    @Test
    public void shouldGetOrganisationDetailsWithOrganisationId() {
        final UUID organisationId = randomUUID();
        final JsonEnvelope requestEnvelope = mock(JsonEnvelope.class);
        final Envelope responseEnvelope = mock(Envelope.class);

        final JsonObject requestJsonObject = createObjectBuilder()
                .add(ORGANISATION_ID, organisationId.toString())
                .build();

        final JsonObject responseJsonObject = createObjectBuilder()
                .add(ORGANISATION_NAME, ORGANISATION_NAME)
                .add(ADDRESS_LINE_1, ADDRESS_LINE_1)
                .add(ADDRESS_POSTCODE, ADDRESS_POSTCODE)
                .build();

        when(responseEnvelope.payload()).thenReturn(responseJsonObject);
        when(requestEnvelope.metadata()).thenReturn(getMetaData(randomUUID(), randomUUID()));
        when(requester.requestAsAdmin(any(), any())).thenReturn(responseEnvelope);

        final JsonObject organisationDetails = usersAndGroupsService.getOrganisationDetails(requestEnvelope, organisationId);

        assertThat(organisationDetails.getString(ADDRESS_LINE_1), is(ADDRESS_LINE_1));
        assertThat(organisationDetails.getString(ADDRESS_POSTCODE), is(ADDRESS_POSTCODE));
    }


    private Metadata getMetaData(UUID uuid, UUID userId) {
        return Envelope
                .metadataBuilder()
                .withName("any_event_name")
                .withId(uuid)
                .withUserId(userId.toString())
                .build();

    }

}
