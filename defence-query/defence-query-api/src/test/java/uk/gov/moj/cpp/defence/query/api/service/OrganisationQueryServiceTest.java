package uk.gov.moj.cpp.defence.query.api.service;

import static java.util.UUID.randomUUID;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;

import java.util.List;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OrganisationQueryServiceTest {

    public static final String ORGANISATION_NAME = "organisationName";
    @Mock
    private Requester requester;

    @Spy
    @InjectMocks
    private OrganisationQueryService organisationQueryService;

    @Test
    public void shouldGetProsecutionCaseJsonObjectWhenProsecutorIsPresent() {
        final UUID organisationId = randomUUID();
        final List<String> ids = asList(organisationId.toString());
        final Envelope responseEnvelope = mock(Envelope.class);

        final JsonObject responseJsonObject = createObjectBuilder()
                .add("organisations", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("organisationId", organisationId.toString())
                                .add(ORGANISATION_NAME, ORGANISATION_NAME)
                                .build())
                        .build())
                .build();

        when(responseEnvelope.payload()).thenReturn(responseJsonObject);
        when(requester.requestAsAdmin(any(), any())).thenReturn(responseEnvelope);

        final List<OrganisationNameVO> organisationNamesForIds = organisationQueryService.getOrganisationNamesForIds(ids, getMetaData(randomUUID(), randomUUID()));

        assertThat(organisationNamesForIds.size(), is(1));
        assertThat(organisationNamesForIds.get(0).getOrganisationId(), is(organisationId.toString()));
        assertThat(organisationNamesForIds.get(0).getOrganisationName(), is(ORGANISATION_NAME));
    }

    @Test
    public void shouldReturnEmptyListWhenOrganizationsIdsNotFound() {
        final UUID organisationId = randomUUID();
        final List<String> ids = asList(organisationId.toString());
        final Envelope responseEnvelope = mock(Envelope.class);

        when(responseEnvelope.payload()).thenReturn(null);
        when(requester.requestAsAdmin(any(), any())).thenReturn(responseEnvelope);

        final List<OrganisationNameVO> organisationNamesForIds = organisationQueryService.getOrganisationNamesForIds(ids, getMetaData(randomUUID(), randomUUID()));

        assertThat(organisationNamesForIds.size(), is(0));
    }

    @Test
    public void shouldGetOrganisationOfLoggedInUser() {
        final UUID organisationId = randomUUID();
        final Envelope responseEnvelope = mock(Envelope.class);

        final JsonObject responseJsonObject = createObjectBuilder()
                .add("organisationId", organisationId.toString())
                .build();

        when(responseEnvelope.payload()).thenReturn(responseJsonObject);
        when(requester.requestAsAdmin(any(), any())).thenReturn(responseEnvelope);

        final String organisationOfLoggedInUser = organisationQueryService.getOrganisationOfLoggedInUser(getMetaData(randomUUID(), randomUUID()));

        assertThat(organisationOfLoggedInUser, is(organisationId.toString()));
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
