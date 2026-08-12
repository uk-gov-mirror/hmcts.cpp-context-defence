package uk.gov.moj.cpp.defence.query.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.CASE_DEFENDANT_ORGANISATION;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.CASE_ID;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.CASE_URN;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.DEFENDANTS;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.getJsonEnvelope;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.getUsersAndGroupsRequestEnvelope;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.toDefendantOrganisationWithAddressJson;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.toDefendantOrganisationWithNoAddressJson;

import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.util.UUID;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CaseDefendantOrganisationHelperTest {

    public static final String ORGANISATION_ID = "organisationId";
    public static final String ASSOCIATED_ORGANISATION = "associatedOrganisation";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String ORGANISATION_NAME = "organisationName";
    public static final String DEFENDANT_FIRST_NAME = "defendantFirstName";
    public static final String DEFENDANT_LAST_NAME = "defendantLastName";
    public static final String ORG_NAME = "ORG NAME";
    public static final String ORGANISATION_ADDRESS = "organisationAddress";
    public static final String ADDRESS_1 = "address1";
    public static final String ADDRESS_POSTCODE = "addressPostcode";
    public static final String ADDRESS_LINE_1 = "addressLine1";
    @Mock
    private Metadata metadata;

    @Test
    public void shouldBuildCaseDefendantOrganisationJsonWhenCaseIdIsNull() {
        final String caseUrn = "CASEURN123";
        final JsonObject caseDefendantOrganisation = createObjectBuilder().add(CASE_URN, caseUrn).build();
        final JsonArrayBuilder defendants = createArrayBuilder();
        final JsonEnvelope actualJsonEnvelope = getJsonEnvelope(metadata, caseDefendantOrganisation, defendants);

        JsonObject caseDefendantOrgJson = actualJsonEnvelope.payloadAsJsonObject().getJsonObject(CASE_DEFENDANT_ORGANISATION);
        assertThat(caseDefendantOrgJson.get(CASE_ID), is(nullValue()));
        assertThat(caseDefendantOrgJson.getString(CASE_URN), is(caseUrn));
        assertThat(caseDefendantOrgJson.getJsonArray(DEFENDANTS), is(defendants.build()));
    }

    @Test
    public void shouldBuildCaseDefendantOrganisationJsonWhenCaseUrnIsNull() {
        final String caseId = randomUUID().toString();
        final JsonObject caseDefendantOrganisation = createObjectBuilder().add(CASE_ID, caseId).build();
        final JsonArrayBuilder defendants = createArrayBuilder();
        final JsonEnvelope actualJsonEnvelope = getJsonEnvelope(metadata, caseDefendantOrganisation, defendants);

        JsonObject caseDefendantOrgJson = actualJsonEnvelope.payloadAsJsonObject().getJsonObject(CASE_DEFENDANT_ORGANISATION);
        assertThat(caseDefendantOrgJson.getString(CASE_ID), is(caseId));
        assertThat(caseDefendantOrgJson.get(CASE_URN), is(nullValue()));
        assertThat(caseDefendantOrgJson.getJsonArray(DEFENDANTS), is(defendants.build()));
    }

    @Test
    public void shouldBuildDefendantOrganisationWithAddress() {
        final UUID defendantId = randomUUID();

        final JsonObject defendantJson = createObjectBuilder()
                .add(DEFENDANT_ID, defendantId.toString())
                .add(ASSOCIATED_ORGANISATION, ASSOCIATED_ORGANISATION)
                .add(DEFENDANT_FIRST_NAME, DEFENDANT_FIRST_NAME)
                .add(DEFENDANT_LAST_NAME, DEFENDANT_LAST_NAME)
                .build();
        final JsonObject organisationDetails = createObjectBuilder()
                .add(ORGANISATION_NAME, ORGANISATION_NAME)
                .add(ADDRESS_LINE_1, ADDRESS_1)
                .add(ADDRESS_POSTCODE, ADDRESS_POSTCODE)
                .build();
        final JsonObject organisationWithAddressJson = toDefendantOrganisationWithAddressJson(defendantJson, organisationDetails);

        assertThat(organisationWithAddressJson.getString(ORGANISATION_NAME), is(ORGANISATION_NAME));
        final JsonObject caseDefendantOrgJson = organisationWithAddressJson.getJsonObject(ORGANISATION_ADDRESS);
        assertThat(caseDefendantOrgJson.getString(ADDRESS_1), is(ADDRESS_1));
        assertThat(caseDefendantOrgJson.getString(ADDRESS_POSTCODE), is(ADDRESS_POSTCODE));
    }

    @Test
    public void shouldBuildDefendantOrganisationWithNoAddress() {
        final UUID defendantId = randomUUID();

        final JsonObject defendantJson = createObjectBuilder()
                .add(DEFENDANT_ID, defendantId.toString())
                .add(ORGANISATION_NAME, ORG_NAME)
                .add(DEFENDANT_FIRST_NAME, DEFENDANT_FIRST_NAME)
                .add(DEFENDANT_LAST_NAME, DEFENDANT_LAST_NAME)
                .build();
        final JsonObject organisationWithAddressJson = toDefendantOrganisationWithNoAddressJson(defendantJson);

        assertThat(organisationWithAddressJson.getString(ORGANISATION_NAME), is(ORG_NAME));
        assertThat(organisationWithAddressJson.getJsonObject(ORGANISATION_ADDRESS), nullValue());
    }

    @Test
    public void shouldBuildUsersAndGroupsRequestEnvelope() {
        final UUID associatedOrganisation = randomUUID();

        final JsonEnvelope jsonEnvelope = mock(JsonEnvelope.class);
        when(jsonEnvelope.metadata()).thenReturn(getMetaData(randomUUID(), randomUUID()));

        final JsonObject defendantJson = createObjectBuilder()
                .add(ASSOCIATED_ORGANISATION, associatedOrganisation.toString())
                .build();
        final JsonEnvelope usersAndGroupsRequestEnvelope = getUsersAndGroupsRequestEnvelope(jsonEnvelope, defendantJson);

        assertThat(usersAndGroupsRequestEnvelope.payloadAsJsonObject().getString(ORGANISATION_ID), is(associatedOrganisation.toString()));
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