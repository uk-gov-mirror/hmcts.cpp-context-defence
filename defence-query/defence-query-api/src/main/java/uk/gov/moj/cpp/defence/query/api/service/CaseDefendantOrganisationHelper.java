package uk.gov.moj.cpp.defence.query.api.service;

import static java.util.Optional.ofNullable;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_1;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_2;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_3;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_4;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_LINE_1;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_LINE_2;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_LINE_3;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_LINE_4;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_POSTCODE;
import static uk.gov.moj.cpp.defence.service.UserGroupService.ORGANISATION_ID;

import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

public class CaseDefendantOrganisationHelper {

    public static final String CASE_DEFENDANT_ORGANISATION = "caseDefendantOrganisation";
    public static final String DEFENDANTS = "defendants";
    public static final String ASSOCIATED_ORGANISATION = "associatedOrganisation";
    private static final String ORGANISATION_NAME = "organisationName";
    public static final String CASE_ID = "caseId";
    public static final String CASE_URN = "urn";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String ORGANISATION_ADDRESS = "organisationAddress";
    public static final String DEFENDANT_FIRST_NAME = "defendantFirstName";
    public static final String DEFENDANT_LAST_NAME = "defendantLastName";

    private CaseDefendantOrganisationHelper() {
    }

    public static JsonEnvelope getJsonEnvelope(final Metadata metadata, final JsonObject caseDefendantOrganisation, final JsonArrayBuilder defendantsJsonArray) {

        final JsonObjectBuilder caseDefendantOrgJsonObjBuilder = createObjectBuilder();
        ofNullable(caseDefendantOrganisation.get(CASE_ID))
                .ifPresent(caseId -> caseDefendantOrgJsonObjBuilder.add(CASE_ID, caseId));
        ofNullable(caseDefendantOrganisation.get(CASE_URN))
                .ifPresent(caseUrn -> caseDefendantOrgJsonObjBuilder.add(CASE_URN, caseUrn));
        caseDefendantOrgJsonObjBuilder.add(DEFENDANTS, defendantsJsonArray.build());

        return envelopeFrom(
                metadata, createObjectBuilder()
                        .add(CASE_DEFENDANT_ORGANISATION, caseDefendantOrgJsonObjBuilder.build())
                        .build());
    }

    public static JsonObject toDefendantOrganisationWithAddressJson(final JsonObject defendantJson, final JsonObject organisationDetails) {
        final JsonObjectBuilder defendantJsonBuilder = createObjectBuilder()
                .add(DEFENDANT_ID, defendantJson.get(DEFENDANT_ID))
                .add(ASSOCIATED_ORGANISATION, defendantJson.get(ASSOCIATED_ORGANISATION));

        ofNullable(defendantJson.get(DEFENDANT_FIRST_NAME)).ifPresent(firstName -> defendantJsonBuilder.add(DEFENDANT_FIRST_NAME, firstName));
        ofNullable(defendantJson.get(DEFENDANT_LAST_NAME)).ifPresent(lastName -> defendantJsonBuilder.add(DEFENDANT_LAST_NAME, lastName));

        return defendantJsonBuilder
                .add(ORGANISATION_NAME, ofNullable(organisationDetails.get(ORGANISATION_NAME)).map(v -> organisationDetails.getString(ORGANISATION_NAME))
                        .orElse(ofNullable(defendantJson.get(ORGANISATION_NAME)).map(v -> defendantJson.getString(ORGANISATION_NAME)).orElse("")))
                .add(ORGANISATION_ADDRESS, createObjectBuilder()
                        .add(ADDRESS_1, organisationDetails.getString(ADDRESS_LINE_1))
                        .add(ADDRESS_2, organisationDetails.getString(ADDRESS_LINE_2, ""))
                        .add(ADDRESS_3, organisationDetails.getString(ADDRESS_LINE_3, ""))
                        .add(ADDRESS_4, organisationDetails.getString(ADDRESS_LINE_4, ""))
                        .add(ADDRESS_POSTCODE, organisationDetails.getString(ADDRESS_POSTCODE))
                )
                .build();
    }

    public static JsonObject toDefendantOrganisationWithNoAddressJson(final JsonObject defendantJson) {
        final JsonObjectBuilder defendantJsonBuilder = createObjectBuilder()
                .add(DEFENDANT_ID, defendantJson.get(DEFENDANT_ID))
                .add(ORGANISATION_NAME, ofNullable(defendantJson.get(ORGANISATION_NAME)).map(v -> defendantJson.getString(ORGANISATION_NAME)).orElse(""));

        ofNullable(defendantJson.get(DEFENDANT_FIRST_NAME)).ifPresent(firstName -> defendantJsonBuilder.add(DEFENDANT_FIRST_NAME, firstName));
        ofNullable(defendantJson.get(DEFENDANT_LAST_NAME)).ifPresent(lastName -> defendantJsonBuilder.add(DEFENDANT_LAST_NAME, lastName));

        return defendantJsonBuilder.build();
    }

    public static JsonEnvelope getUsersAndGroupsRequestEnvelope(final JsonEnvelope jsonEnvelope, final JsonObject defendantJson) {
        return envelopeFrom(jsonEnvelope.metadata(), createObjectBuilder()
                .add(ORGANISATION_ID, defendantJson.getJsonString(ASSOCIATED_ORGANISATION)).build());
    }

}
