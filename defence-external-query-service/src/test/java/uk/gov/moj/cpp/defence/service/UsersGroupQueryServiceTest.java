package uk.gov.moj.cpp.defence.service;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.MetadataBuilder;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UsersGroupQueryServiceTest {

    private static final String DEFENCE_ORG = "Defence Org";
    @InjectMocks
    private UsersGroupQueryService usersGroupQueryService;

    @Mock
    private Requester requester;

    @Mock
    private JsonEnvelope jsonEnvelope;

    @Mock
    private JsonObject jsonObject;

    @Captor
    private ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgumentCaptor;


    private static final UUID USER_ID = UUID.randomUUID();
    public static final String NON_CPS_PROSECUTORS = "Non CPS Prosecutors";

    @Test
    public void shouldGetOrganisationForUser() {
        final List<UUID> causation = new ArrayList();
        causation.add(UUID.randomUUID());
        final MetadataBuilder metadataBuilder = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName("abc")
                .createdAt(ZonedDateTime.now())
                .withCausation(causation.get(0));

        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), jsonObject);
        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);

        when(jsonObject.getString("organisationId")).thenReturn(DEFENCE_ORG);
        final String userOrganisationId = usersGroupQueryService.getOrganisationForUser(USER_ID, metadataBuilder.build());
        verify(requester).requestAsAdmin(any(), any());
        assertThat("Organisation does not match", userOrganisationId, is(DEFENCE_ORG));

    }

    @Test
    public void shouldGetUserGroups() {
        final MetadataBuilder metadataBuilder = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName("test");
        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), jsonObject);
        when(requester.request(any(), any())).thenReturn(envelope);

        usersGroupQueryService.getUserGroups(metadataBuilder.build(), USER_ID);
        verify(requester).request(any(), any());
    }

    @Test
    public void shouldReturnTrueForValidateNonCPSUser() {
        final MetadataBuilder metadataBuilder = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName("test");
        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), getUserGroupsResponse());
        when(requester.request(any(), any())).thenReturn(envelope);
        final boolean isNonCPSUser = usersGroupQueryService.validateNonCPSUser(metadataBuilder.build(), randomUUID(), NON_CPS_PROSECUTORS, "DVLA");
        assertTrue(isNonCPSUser);
    }

    @Test
    public void shouldReturnTrueForValidateNonCPSUserOrg() {
        final MetadataBuilder metadataBuilder = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName("test");
        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), getUserGroupsResponse());
        when(requester.request(any(), any())).thenReturn(envelope);
        final Optional<String> isNonCPSUserOrg = usersGroupQueryService.validateNonCPSUserOrg(metadataBuilder.build(), randomUUID(), NON_CPS_PROSECUTORS, "DVLA");
        assertTrue(isNonCPSUserOrg.isPresent());
        assertThat(isNonCPSUserOrg.get(), is("OrganisationMatch"));
    }

    @Test
    public void shouldReturnTrueForValidateNonCPSUserNonOrg() {
        final MetadataBuilder metadataBuilder = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName("test");
        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), getUserGroupsResponse());
        when(requester.request(any(), any())).thenReturn(envelope);
        final Optional<String> isNonCPSUserOrg = usersGroupQueryService.validateNonCPSUserOrg(metadataBuilder.build(), randomUUID(), NON_CPS_PROSECUTORS, "DVLA1");
        assertTrue(isNonCPSUserOrg.isPresent());
        assertThat(isNonCPSUserOrg.get(), is("OrganisationMisMatch"));
    }

    @Test
    public void shouldReturnFalseForValidateNonCPSUserWithInvalidGroupAndValidProsecutingAuthority() {
        final MetadataBuilder metadataBuilder = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName("test");
        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), getUserGroupsResponse());
        when(requester.request(any(), any())).thenReturn(envelope);
        final boolean isNonCPSUser = usersGroupQueryService.validateNonCPSUser(metadataBuilder.build(), randomUUID(), "CPS_PROSECUTORS", "DVLA");
        assertFalse(isNonCPSUser);

    }

    @Test
    public void shouldReturnFalseForValidateNonCPSUserWithValidGroupInvalidProsecutingAuthority() {
        final MetadataBuilder metadataBuilder = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName("test");
        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), getUserGroupsResponse());
        when(requester.request(any(), any())).thenReturn(envelope);
        final boolean isNonCPSUser = usersGroupQueryService.validateNonCPSUser(metadataBuilder.build(), randomUUID(), NON_CPS_PROSECUTORS, "TVL");
        assertFalse(isNonCPSUser);
    }

    @Test
    public void shouldReturnFalseForValidateNonCPSUserWithInValidGroupInvalidProsecutingAuthority() {
        final MetadataBuilder metadataBuilder = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName("test");
        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), getUserGroupsResponse());
        when(requester.request(any(), any())).thenReturn(envelope);
        final boolean isNonCPSUser = usersGroupQueryService.validateNonCPSUser(metadataBuilder.build(), randomUUID(), "CPS_PROSECUTORS", "TVL");
        assertFalse(isNonCPSUser);
    }

    @Test
    public void shouldReturnTrueForNonCPSProsecutorWithValidProsecutingAuthority() {
        final boolean result = usersGroupQueryService.isNonCPSProsecutorWithValidProsecutingAuthority(getUserGroupsResponse(), NON_CPS_PROSECUTORS, "DVLA");
        assertTrue(result);
    }

    @Test
    public void shouldReturnFalseForNonCPSProsecutorWithValidProsecutingAuthority() {
        final boolean result = usersGroupQueryService.isNonCPSProsecutorWithValidProsecutingAuthority(getUserGroupsResponse(), NON_CPS_PROSECUTORS, "DVLA1");
        assertFalse(result);
    }

    private JsonObject getUserGroupsResponse() {
        final JsonArrayBuilder groupsArray = createArrayBuilder();
        groupsArray.add(createObjectBuilder().add("groupId", String.valueOf(randomUUID())).add("groupName", "Non CPS Prosecutors"));
        groupsArray.add(createObjectBuilder().add("groupId", String.valueOf(randomUUID())).add("groupName", "DVLA Prosectutors").add("prosecutingAuthority" , "DVLA"));
        return createObjectBuilder().add("groups", groupsArray).build();
    }

}
