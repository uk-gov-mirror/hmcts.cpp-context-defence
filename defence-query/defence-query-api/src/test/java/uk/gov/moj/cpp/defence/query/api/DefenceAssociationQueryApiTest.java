package uk.gov.moj.cpp.defence.query.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.justice.services.test.utils.core.messaging.JsonEnvelopeBuilder;
import uk.gov.moj.cpp.defence.query.api.service.UsersAndGroupsService;
import uk.gov.moj.cpp.defence.query.view.DefenceAssociationQueryView;

import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)

public class DefenceAssociationQueryApiTest {

    public static final String organisationName = "TEST_ORG";

    @Mock
    private JsonEnvelope query;
    @Mock
    private UsersAndGroupsService usersAndGroupsService;
    @Mock
    private DefenceAssociationQueryView defenceAssociationQueryView;

    @InjectMocks
    private DefenceAssociationQueryApi defenceAssociationQueryApi;

    @Test
    public void shouldReturnAssociatedOrganisationDetails() {

        //Given
        final UUID userId = randomUUID();
        final UUID organisationId = randomUUID();
        final UUID defendantId = randomUUID();

        final MetadataBuilder metadataBuilder = stubbedMetadataBuilder(userId);
        final JsonEnvelope requestEnvelopeForApiView = JsonEnvelopeBuilder.envelope().with(metadataBuilder).withPayloadOf(defendantId.toString(), "defendantId").build();
        final JsonEnvelope responseEnvelopeForApiView = JsonEnvelope.envelopeFrom(metadataBuilder, stubbedDefenceAssociationDataToReturnFromPersistedData(organisationId.toString()));
        final JsonObject userGroupsResponsePayload = stubbedDefenceAssociationDataReturnedFromUsersAndGroupService(organisationId.toString());
        when(defenceAssociationQueryView.getAssociatedOrganisation(requestEnvelopeForApiView)).thenReturn(responseEnvelopeForApiView);
        when(usersAndGroupsService.getOrganisationDetails(any()))
                .thenReturn(userGroupsResponsePayload);

        //When
        final JsonEnvelope associatedOrganizationResponse = defenceAssociationQueryApi.getAssociatedOrganisation(requestEnvelopeForApiView);

        //Then
        final JsonObject association = associatedOrganizationResponse.payloadAsJsonObject().getJsonObject("association");
        assertThat(getValue(association, "organisationId"), equalTo(organisationId.toString()));
        assertThat(getValue(association, "organisationName"), equalTo(organisationName));
        assertThat(getValue(association, "status"), equalTo("Active Barrister/Solicitor of record"));
        assertThat(getValue(association, "email"), equalTo(userGroupsResponsePayload.getString("email")));
        assertThat(getValue(association, "phoneNumber"), equalTo(userGroupsResponsePayload.getString("phoneNumber")));
    }

    @Test
    public void shouldReturnAssociatedOrganisationDetailsWhenOrganisationMissingFromUSersGroups() {

        //Given
        final UUID userId = randomUUID();
        final UUID organisationId = randomUUID();
        final UUID defendantId = randomUUID();

        final MetadataBuilder metadataBuilder = stubbedMetadataBuilder(userId);
        final JsonEnvelope requestEnvelopeForApiView = JsonEnvelopeBuilder.envelope().with(metadataBuilder).withPayloadOf(defendantId.toString(), "defendantId").build();
        final JsonEnvelope responseEnvelopeForApiView = JsonEnvelope.envelopeFrom(metadataBuilder, stubbedDefenceAssociationDataToReturnFromPersistedData(organisationId.toString()));
        when(defenceAssociationQueryView.getAssociatedOrganisation(requestEnvelopeForApiView)).thenReturn(responseEnvelopeForApiView);
        when(usersAndGroupsService.getOrganisationDetails(any()))
                .thenReturn(null);

        //When
        final JsonEnvelope associatedOrganizationResponse = defenceAssociationQueryApi.getAssociatedOrganisation(requestEnvelopeForApiView);

        //Then
        final JsonObject association = associatedOrganizationResponse.payloadAsJsonObject().getJsonObject("association");
        assertThat(getValue(association, "status"), equalTo("Active Barrister/Solicitor of record"));
    }

    @Test
    public void shouldReturnEmptyOrganisationDetailsWhenNoOrganisationAssociated() {

        //Given
        final UUID userId = randomUUID();
        when(defenceAssociationQueryView.getAssociatedOrganisation(query)).thenReturn(emptyOrganisationDetails(userId));

        //When
        final JsonEnvelope associatedOrganizationResponse = defenceAssociationQueryApi.getAssociatedOrganisation(query);

        //Then
        final JsonObject association = associatedOrganizationResponse.payloadAsJsonObject().getJsonObject("association");
        assertThat(association.toString(), equalTo("{}"));
    }

    @Test
    public void shouldReturnAllAssociatedOrganisationsDetails() {
        //Given
        final UUID userId = randomUUID();
        final UUID organisationId1 = randomUUID();
        final UUID organisationId2 = randomUUID();
        final UUID defendantId = randomUUID();

        final MetadataBuilder metadataBuilder = stubbedMetadataBuilder(userId);
        final JsonEnvelope requestEnvelopeForApiView = JsonEnvelopeBuilder.envelope().with(metadataBuilder).withPayloadOf(defendantId.toString(), "defendantId").build();
        final JsonEnvelope responseEnvelopeForApiView = JsonEnvelope.envelopeFrom(metadataBuilder, stubbedDefenceAssociationListToReturnFromPersistedData(organisationId1.toString(), organisationId2.toString()));
        final JsonObject userGroupsResponsePayload1 = stubbedDefenceAssociationDataReturnedFromUsersAndGroupService(organisationId1.toString());
        final JsonObject userGroupsResponsePayload2 = stubbedDefenceAssociationDataReturnedFromUsersAndGroupService(organisationId2.toString());
        when(defenceAssociationQueryView.getAssociatedOrganisations(requestEnvelopeForApiView)).thenReturn(responseEnvelopeForApiView);
        when(usersAndGroupsService.getOrganisationDetails(any())).thenReturn(userGroupsResponsePayload1, userGroupsResponsePayload2);

        //When
        final JsonEnvelope associatedOrganizationResponse = defenceAssociationQueryApi.getAssociatedOrganisations(requestEnvelopeForApiView);

        //Then
        final JsonObject association1 = associatedOrganizationResponse.payloadAsJsonObject().getJsonArray("associations").getJsonObject(0);
        assertThat(getValue(association1, "organisationId"), equalTo(organisationId1.toString()));
        assertThat(getValue(association1, "organisationName"), equalTo(organisationName));
        assertThat(getValue(association1, "status"), equalTo(""));
        assertThat(getValue(association1, "startDate"), notNullValue());
        assertThat(getValue(association1, "endDate"), notNullValue());
        assertThat(getValue(association1, "email"), equalTo(userGroupsResponsePayload1.getString("email")));
        assertThat(getValue(association1, "phoneNumber"), equalTo(userGroupsResponsePayload1.getString("phoneNumber")));

        final JsonObject association2 = associatedOrganizationResponse.payloadAsJsonObject().getJsonArray("associations").getJsonObject(1);
        assertThat(getValue(association2, "organisationId"), equalTo(organisationId2.toString()));
        assertThat(getValue(association2, "organisationName"), equalTo(organisationName));
        assertThat(getValue(association2, "status"), equalTo("Active Barrister/Solicitor of record"));
        assertThat(getValue(association2, "startDate"), notNullValue());
        assertThat(association2.containsKey("endDate"), equalTo(false));
    }

    @Test
    public void shouldReturnEmptyOrganisationsListWhenNoOrganisationAssociated() {

        //Given
        final UUID userId = randomUUID();
        when(defenceAssociationQueryView.getAssociatedOrganisations(query)).thenReturn(noOrganisationsAssociated(userId));

        //When
        final JsonEnvelope associatedOrganizationResponse = defenceAssociationQueryApi.getAssociatedOrganisations(query);

        //Then
        final JsonArray associations = associatedOrganizationResponse.payloadAsJsonObject().getJsonArray("associations");
        assertThat(associations.isEmpty(), equalTo(true));
    }

    private JsonEnvelope emptyOrganisationDetails(final UUID userId) {
        return JsonEnvelope.envelopeFrom(
                stubbedMetadataBuilder(userId),
                createObjectBuilder()
                        .add("association", createObjectBuilder())
                        .build());
    }

    private JsonEnvelope noOrganisationsAssociated(final UUID userId) {
        return JsonEnvelope.envelopeFrom(
                stubbedMetadataBuilder(userId),
                createObjectBuilder()
                        .add("associations", createArrayBuilder())
                        .build());
    }

    private String getValue(final JsonObject associationsJsonObject, final String key) {
        return associationsJsonObject.getString(key);
    }

    private MetadataBuilder stubbedMetadataBuilder(final UUID userId) {
        return JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName("defence.query.associated-organisation")
                .withCausation(randomUUID())
                .withClientCorrelationId(randomUUID().toString())
                .withStreamId(randomUUID())
                .withUserId(userId.toString());
    }

    private JsonObject stubbedDefenceAssociationDataToReturnFromPersistedData(final String organisationId) {
        return createObjectBuilder()
                .add("association", createObjectBuilder()
                        .add("organisationId", organisationId)
                        .add("status", "Active Barrister/Solicitor of record")
                        .add("startDate", ZonedDateTime.now().toString())
                        .add("representationType", "PRO_BONO")
                )
                .build();
    }

    private JsonObject stubbedDefenceAssociationListToReturnFromPersistedData(final String organisationId1, final String organisationId2) {
        return createObjectBuilder()
                .add("associations", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("organisationId", organisationId1)
                                .add("status", "")
                                .add("startDate", ZonedDateTime.now().minusDays(30).toString())
                                .add("endDate", ZonedDateTime.now().minusDays(1).toString())
                                .add("representationType", "PRO_BONO1"))
                        .add(createObjectBuilder()
                                .add("organisationId", organisationId2)
                                .add("status", "Active Barrister/Solicitor of record")
                                .add("startDate", ZonedDateTime.now().toString())
                                .add("representationType", "PRO_BONO2"))
                ).build();
    }

    private JsonObject stubbedDefenceAssociationDataReturnedFromUsersAndGroupService(final String organisationId) {
        return createObjectBuilder()
                .add("organisationId", organisationId)
                .add("organisationName", organisationName)
                .add("addressLine1", "add line 1")
                .add("addressLine4", "add line 4")
                .add("addressPostcode", "CR01XG")
                .add("phoneNumber", "1234567890")
                .add("email", "moj@email.com")
                .build();
    }
}
