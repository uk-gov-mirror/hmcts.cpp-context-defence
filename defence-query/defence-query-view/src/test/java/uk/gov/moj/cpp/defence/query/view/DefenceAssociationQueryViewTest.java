package uk.gov.moj.cpp.defence.query.view;

import static java.time.ZoneId.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationDefendantRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociationDefendant;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefenceAssociationQueryViewTest {

    private static final UUID DEFENDANT_ID = randomUUID();
    private static final UUID USER_ID = randomUUID();
    private static final UUID ORGANISATION_ID = randomUUID();
    private static final UUID ORGANISATION_ID2 = randomUUID();
    private static final String UTC = "UTC";
    private static final String PRO_BONO = "PRO_BONO";


    @InjectMocks
    private DefenceAssociationQueryView defenceAssociationQueryView;

    @Mock
    private DefenceAssociationDefendantRepository defenceAssociationDefendantRepository;

    @Mock
    private DefenceAssociationRepository defenceAssociationRepository;

    @Test
    public void shouldReturnDefenceAssociation() {

        //Given
        when(defenceAssociationDefendantRepository.findOptionalByDefendantId(DEFENDANT_ID)).thenReturn(stubbedCurrentDefenceAssociationDefendant());

        //When
        final JsonEnvelope defenceAssociationResponse = defenceAssociationQueryView.getAssociatedOrganisation(stubbedQueryObject());

        //Then
        final JsonObject association = defenceAssociationResponse.payloadAsJsonObject().getJsonObject("association");
        assertThat(association, notNullValue());
        assertThat(getValue(association, "organisationId"), equalTo(ORGANISATION_ID.toString()));
        assertThat(getValue(association, "status"), equalTo("Active Barrister/Solicitor of record"));
        assertThat(getValue(association, "representationType"), equalTo(PRO_BONO));
    }

    @Test
    public void shouldReturnEmptyDataWhenNoAssociationExist() {

        //Given
        when(defenceAssociationDefendantRepository.findOptionalByDefendantId(DEFENDANT_ID)).thenReturn(stubbedEmptyDefenceAssociation());

        //When
        final JsonEnvelope defenceAssociationResponse = defenceAssociationQueryView.getAssociatedOrganisation(stubbedQueryObject());

        //Then
        final JsonObject association = defenceAssociationResponse.payloadAsJsonObject().getJsonObject("association");
        assertThat(association.toString(), equalTo("{}"));
    }


    @Test
    public void shouldReturnEmptyDataWhenAssociationIsNull() {

        //Given
        when(defenceAssociationDefendantRepository.findOptionalByDefendantId(DEFENDANT_ID)).thenReturn(null);

        //When
        final JsonEnvelope defenceAssociationResponse = defenceAssociationQueryView.getAssociatedOrganisation(stubbedQueryObject());

        //Then
        final JsonObject association = defenceAssociationResponse.payloadAsJsonObject().getJsonObject("association");
        assertThat(association.toString(), equalTo("{}"));
    }

    @Test
    public void shouldReturnCurrentAssociationGivenExpiredAssociationExist() {

        //Given
        when(defenceAssociationDefendantRepository.findOptionalByDefendantId(DEFENDANT_ID)).thenReturn(stubbedExpiredAssociationAndCurrentAssociation());

        //When
        final JsonEnvelope defenceAssociationResponse = defenceAssociationQueryView.getAssociatedOrganisation(stubbedQueryObject());

        //Then
        final JsonObject association = defenceAssociationResponse.payloadAsJsonObject().getJsonObject("association");
        assertThat(association, notNullValue());
        assertThat(getValue(association, "organisationId"), equalTo(ORGANISATION_ID.toString()));
        assertThat(getValue(association, "status"), equalTo("Active Barrister/Solicitor of record"));
    }

    @Test
    public void shouldReturnEmptyDataWhenOnlyExpiredAssociationEntryExist() {

        //Given
        when(defenceAssociationDefendantRepository.findOptionalByDefendantId(DEFENDANT_ID)).thenReturn(stubbedOnlyExpiredAssociation());

        //When
        final JsonEnvelope defenceAssociationResponse = defenceAssociationQueryView.getAssociatedOrganisation(stubbedQueryObject());

        //Then
        final JsonObject association = defenceAssociationResponse.payloadAsJsonObject().getJsonObject("association");
        assertThat(association.toString(), equalTo("{}"));

    }

    @Test
    public void shouldReturnDefendantIdsWhenDefendantsAreAvailableForUser() {

        final UUID defendantId1 = randomUUID();
        final UUID defendantId2 = randomUUID();

        when(defenceAssociationRepository.findByUserIdAndCurrentDate(any(), any())).thenReturn(getAssociatedDefendantsDetails(defendantId1, defendantId2));

        final JsonEnvelope defenceAssociationResponse = defenceAssociationQueryView.getAssociatedDefendants(getQueryWithUserId());

        final JsonArray defendantIds = defenceAssociationResponse.payloadAsJsonObject().getJsonArray("defendantIds");
        assertThat(defendantIds.size(), is(2));
    }

    @Test
    public void shouldReturnEmptyDefenceAssociationWhenDefendantNotPresentForUser() {

        final JsonEnvelope defenceAssociationResponse = defenceAssociationQueryView.getAssociatedDefendants(getQueryWithUserId());

        final JsonArray defendantIds = defenceAssociationResponse.payloadAsJsonObject().getJsonArray("defendantIds");
        assertThat(defendantIds.size(), is(0));
        assertThat(defendantIds.toString(), equalTo("[]"));
    }

    @Test
    void shouldReturnDefenceAssociations() {

        //Given
        when(defenceAssociationDefendantRepository.findOptionalByDefendantId(DEFENDANT_ID)).thenReturn(stubbedExpiredAssociationAndCurrentAssociation());

        //When
        final JsonEnvelope defenceAssociationsResponse = defenceAssociationQueryView.getAssociatedOrganisations(stubbedQueryObject());

        //Then
        final JsonArray associations = defenceAssociationsResponse.payloadAsJsonObject().getJsonArray("associations");
        assertThat(associations.size(), equalTo(2));
        assertThat(getValue(associations.get(0).asJsonObject(), "organisationId"), equalTo(ORGANISATION_ID2.toString()));
        assertThat(getValue(associations.get(0).asJsonObject(), "status"), equalTo(""));
        assertThat(getValue(associations.get(0).asJsonObject(), "representationType"), equalTo(PRO_BONO));
        assertThat(getValue(associations.get(0).asJsonObject(), "startDate"), notNullValue());
        assertThat(getValue(associations.get(0).asJsonObject(), "endDate"), notNullValue());


        assertThat(getValue(associations.get(1).asJsonObject(), "organisationId"), equalTo(ORGANISATION_ID.toString()));
        assertThat(getValue(associations.get(1).asJsonObject(), "status"), equalTo("Active Barrister/Solicitor of record"));
        assertThat(getValue(associations.get(1).asJsonObject(), "representationType"), equalTo(PRO_BONO));
        assertThat(getValue(associations.get(1).asJsonObject(), "startDate"), notNullValue());
        assertThat(associations.get(1).asJsonObject().get("endDate"), nullValue());

    }

    @Test
    void shouldReturnEmptyAssociationsArrayWhenNoAssociationsExist() {

        //Given
        when(defenceAssociationDefendantRepository.findOptionalByDefendantId(DEFENDANT_ID)).thenReturn(stubbedEmptyDefenceAssociation());

        //When
        final JsonEnvelope defenceAssociationsResponse = defenceAssociationQueryView.getAssociatedOrganisations(stubbedQueryObject());

        //Then
        final JsonArray associations = defenceAssociationsResponse.payloadAsJsonObject().getJsonArray("associations");
        assertThat(associations.size(), equalTo(0));
    }

    @Test
    void shouldReturnEmptyAssociationsArrayWhenAssociationIsNull() {

        //Given
        when(defenceAssociationDefendantRepository.findOptionalByDefendantId(DEFENDANT_ID)).thenReturn(null);

        //When
        final JsonEnvelope defenceAssociationsResponse = defenceAssociationQueryView.getAssociatedOrganisations(stubbedQueryObject());

        //Then
        final JsonArray associations = defenceAssociationsResponse.payloadAsJsonObject().getJsonArray("associations");
        assertThat(associations.size(), equalTo(0));
    }

    @Test
    void shouldReturnExpiredAssociationDataWhenOnlyExpiredAssociationEntryExist() {

        //Given
        when(defenceAssociationDefendantRepository.findOptionalByDefendantId(DEFENDANT_ID)).thenReturn(stubbedOnlyExpiredAssociation());

        //When
        final JsonEnvelope defenceAssociationsResponse = defenceAssociationQueryView.getAssociatedOrganisations(stubbedQueryObject());

        //Then
        final JsonArray associations = defenceAssociationsResponse.payloadAsJsonObject().getJsonArray("associations");
        assertThat(associations.size(), equalTo(1));
        assertThat(getValue(associations.get(0).asJsonObject(), "organisationId"), equalTo(ORGANISATION_ID2.toString()));
        assertThat(getValue(associations.get(0).asJsonObject(), "status"), equalTo(""));
        assertThat(getValue(associations.get(0).asJsonObject(), "representationType"), equalTo(PRO_BONO));
        assertThat(getValue(associations.get(0).asJsonObject(), "startDate"), notNullValue());
        assertThat(getValue(associations.get(0).asJsonObject(), "endDate"), notNullValue());
    }

    private DefenceAssociationDefendant stubbedEmptyDefenceAssociation() {
        final DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(DEFENDANT_ID);
        defenceAssociationDefendant.setDefenceAssociations(new ArrayList<>());
        return defenceAssociationDefendant;
    }

    private String getValue(final JsonObject associationsJsonObject, final String key) {
        return associationsJsonObject.getString(key);
    }

    private JsonEnvelope stubbedQueryObject() {
        return JsonEnvelope.envelopeFrom(
                stubbedMetadataBuilder(),
                stubbedQueryObjectPayload());
    }

    private JsonEnvelope getQueryWithUserId() {
        return JsonEnvelope.envelopeFrom(
                stubbedMetadataBuilder(),
                createObjectBuilder()
                        .add("userId", USER_ID.toString())
                        .build()
        );
    }

    private JsonObject stubbedQueryObjectPayload() {
        return createObjectBuilder()
                .add("defendantId", DEFENDANT_ID.toString())
                .build();
    }

    private MetadataBuilder stubbedMetadataBuilder() {
        return JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName("defence.query.associated-organisation")
                .withCausation(randomUUID())
                .withClientCorrelationId(randomUUID().toString())
                .withStreamId(randomUUID())
                .withUserId(randomUUID().toString());
    }

    private DefenceAssociationDefendant stubbedCurrentDefenceAssociationDefendant() {
        final DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(DEFENDANT_ID);
        final DefenceAssociation defenceAssociation = stubbedAssociation(ZonedDateTime.now(of(UTC)), null, defenceAssociationDefendant, USER_ID, ORGANISATION_ID);
        defenceAssociationDefendant.setDefenceAssociations(new ArrayList<>());
        defenceAssociationDefendant.getDefenceAssociations().add(defenceAssociation);
        return defenceAssociationDefendant;
    }

    private List<DefenceAssociation> getAssociatedDefendantsDetails(final UUID defendantId1, final UUID defendantId2) {
        final DefenceAssociationDefendant defenceAssociationDefendant1 = new DefenceAssociationDefendant();
        defenceAssociationDefendant1.setDefendantId(defendantId1);
        final DefenceAssociation defenceAssociation1 = stubbedAssociation(ZonedDateTime.now(of(UTC)), null, defenceAssociationDefendant1, USER_ID, ORGANISATION_ID);
        defenceAssociationDefendant1.setDefenceAssociations(new ArrayList<>());
        defenceAssociationDefendant1.getDefenceAssociations().add(defenceAssociation1);

        final DefenceAssociationDefendant defenceAssociationDefendant2 = new DefenceAssociationDefendant();
        defenceAssociationDefendant2.setDefendantId(defendantId2);
        final DefenceAssociation defenceAssociation2 = stubbedAssociation(ZonedDateTime.now(of(UTC)), null, defenceAssociationDefendant2, USER_ID, ORGANISATION_ID);
        defenceAssociationDefendant2.setDefenceAssociations(new ArrayList<>());
        defenceAssociationDefendant2.getDefenceAssociations().add(defenceAssociation2);

        return Arrays.asList(defenceAssociation1, defenceAssociation2);
    }

    private DefenceAssociationDefendant stubbedOnlyExpiredAssociation() {
        final DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(DEFENDANT_ID);
        final DefenceAssociation defenceAssociation = stubbedAssociation(ZonedDateTime.now(of(UTC)), ZonedDateTime.now(of(UTC)), defenceAssociationDefendant, randomUUID(), ORGANISATION_ID2);
        defenceAssociationDefendant.setDefenceAssociations(new ArrayList<>());
        defenceAssociationDefendant.getDefenceAssociations().add(defenceAssociation);
        return defenceAssociationDefendant;
    }

    private DefenceAssociationDefendant stubbedExpiredAssociationAndCurrentAssociation() {
        final DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(DEFENDANT_ID);
        DefenceAssociation defenceAssociation = stubbedAssociation(ZonedDateTime.now(of(UTC)), ZonedDateTime.now(of(UTC)), defenceAssociationDefendant, randomUUID(), ORGANISATION_ID2);
        defenceAssociationDefendant.setDefenceAssociations(new ArrayList<>());
        defenceAssociationDefendant.getDefenceAssociations().add(defenceAssociation);
        defenceAssociation = stubbedAssociation(ZonedDateTime.now(of(UTC)), null, defenceAssociationDefendant, USER_ID, ORGANISATION_ID);
        defenceAssociationDefendant.getDefenceAssociations().add(defenceAssociation);
        assertThat(defenceAssociationDefendant.getDefenceAssociations().size(), equalTo(2));
        return defenceAssociationDefendant;
    }


    private DefenceAssociation stubbedAssociation(final ZonedDateTime startDate,
                                                  final ZonedDateTime endDate,
                                                  final DefenceAssociationDefendant defenceAssociationDefendant,
                                                  final UUID userId,
                                                  final UUID orgId) {
        final DefenceAssociation defenceAssociation = new DefenceAssociation();
        defenceAssociation.setId(randomUUID());
        defenceAssociation.setDefenceAssociationDefendant(defenceAssociationDefendant);
        defenceAssociation.setUserId(userId);
        defenceAssociation.setOrgId(orgId);
        defenceAssociation.setStartDate(startDate);
        defenceAssociation.setEndDate(endDate);
        defenceAssociation.setRepresentationType(PRO_BONO);
        return defenceAssociation;
    }

}
