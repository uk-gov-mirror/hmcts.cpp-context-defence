package uk.gov.moj.cpp.defence.query.api;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.query.api.service.UsersAndGroupsService;
import uk.gov.moj.cpp.defence.query.view.DefenceAssociationQueryView;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

@ServiceComponent(Component.QUERY_API)
public class DefenceAssociationQueryApi {

    public static final String ASSOCIATION = "association";
    public static final String ASSOCIATIONS = "associations";
    public static final String ORGANISATION_ID = "organisationId";
    public static final String STATUS = "status";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String ORGANISATION_NAME = "organisationName";
    public static final String ADDRESS = "address";
    public static final String ADDRESS_1 = "address1";
    public static final String ADDRESS_2 = "address2";
    public static final String ADDRESS_3 = "address3";
    public static final String ADDRESS_4 = "address4";
    public static final String ADDRESS_LINE_1 = "addressLine1";
    public static final String ADDRESS_LINE_2 = "addressLine2";
    public static final String ADDRESS_LINE_3 = "addressLine3";
    public static final String ADDRESS_LINE_4 = "addressLine4";
    public static final String ADDRESS_POSTCODE = "addressPostcode";
    public static final String EMPTY_JSON_OBJECT = "{}";
    public static final String REPRESENTATION_TYPE = "representationType";
    public static final String PHONE_NUMBER = "phoneNumber";
    public static final String EMAIL = "email";

    @Inject
    private UsersAndGroupsService usersAndGroupsService;

    @Inject
    private DefenceAssociationQueryView defenceAssociationQueryView;

    @Handles("defence.query.associated-organisation")
    public JsonEnvelope getAssociatedOrganisation(final JsonEnvelope query) {

        final JsonEnvelope associationEnvelope = defenceAssociationQueryView.getAssociatedOrganisation(query);
        final JsonObject association = associationEnvelope.payloadAsJsonObject().getJsonObject(ASSOCIATION);
        if (associationExists(association)) {
            return populateOrganisationDetails(associationEnvelope);
        } else {
            return emptyOrganisationDetails(query);
        }
    }

    @Handles("defence.query.associated-organisations")
    public JsonEnvelope getAssociatedOrganisations(final JsonEnvelope query) {

        final JsonEnvelope associationsEnvelope = defenceAssociationQueryView.getAssociatedOrganisations(query);
        final JsonArray associations = associationsEnvelope.payloadAsJsonObject().getJsonArray(ASSOCIATIONS);
        if (!associations.isEmpty()) {
            return populateAllOrganisationDetails(associationsEnvelope);
        } else {
            return JsonEnvelope.envelopeFrom(
                    query.metadata(),
                    createObjectBuilder()
                            .add(ASSOCIATIONS, createArrayBuilder())
                            .build());
        }
    }

    @Handles("defence.query.get-associated-defendants")
    public JsonEnvelope getAssociatedDefendants(final JsonEnvelope query) {
        return defenceAssociationQueryView.getAssociatedDefendants(query);
    }

    private JsonEnvelope populateOrganisationDetails(final JsonEnvelope associationEnvelope) {
        final JsonString organisationId = associationEnvelope.payloadAsJsonObject().getJsonObject(ASSOCIATION).getJsonString(ORGANISATION_ID);
        final JsonEnvelope usersAndGroupsRequestEnvelope = buildUsersAndGroupsRequestEnvelope(associationEnvelope.metadata(), organisationId);
        final JsonObject organisationDetailsFromUsersAndGroupsService = usersAndGroupsService.getOrganisationDetails(usersAndGroupsRequestEnvelope);
        return JsonEnvelope.envelopeFrom(
                associationEnvelope.metadata(),
                createObjectBuilder()
                        .add(ASSOCIATION, formResponsePayload(associationEnvelope.payloadAsJsonObject().getJsonObject(ASSOCIATION),
                                organisationDetailsFromUsersAndGroupsService))
                        .build());

    }

    private JsonEnvelope populateAllOrganisationDetails(final JsonEnvelope associationsEnvelope) {
        final JsonArrayBuilder associationsWithOrganisationDetailsArray = createArrayBuilder();
        associationsEnvelope.payloadAsJsonObject().getJsonArray(ASSOCIATIONS).forEach(jsonValue -> {
            final JsonString organisationId = jsonValue.asJsonObject().getJsonString(ORGANISATION_ID);
            final JsonEnvelope usersAndGroupsRequestEnvelope = buildUsersAndGroupsRequestEnvelope(associationsEnvelope.metadata(), organisationId);
            final JsonObject organisationDetailsFromUsersAndGroupsService = usersAndGroupsService.getOrganisationDetails(usersAndGroupsRequestEnvelope);
            associationsWithOrganisationDetailsArray.add(formResponsePayload(jsonValue.asJsonObject(), organisationDetailsFromUsersAndGroupsService));
        });

        return JsonEnvelope.envelopeFrom(
                associationsEnvelope.metadata(),
                createObjectBuilder().add(ASSOCIATIONS, associationsWithOrganisationDetailsArray).build());
    }

    private JsonEnvelope buildUsersAndGroupsRequestEnvelope(final Metadata metadata, final JsonString organisationId) {
        return JsonEnvelope.envelopeFrom(metadata, createObjectBuilder().add(ORGANISATION_ID, organisationId).build());
    }

    private boolean associationExists(final JsonObject association) {
        return !association.toString().equals(EMPTY_JSON_OBJECT);
    }

    private JsonEnvelope emptyOrganisationDetails(final JsonEnvelope query) {
        return JsonEnvelope.envelopeFrom(
                query.metadata(),
                createObjectBuilder()
                        .add(ASSOCIATION, createObjectBuilder())
                        .build());
    }

    private JsonObject formResponsePayload(final JsonObject association, final JsonObject organisationDetailsForUserJsonObject) {
        final String status = association.getString(STATUS);
        final String startDate = association.getString(START_DATE);
        final Optional<String> endDate = ofNullable(association.getString(END_DATE, null));
        final String representationType = association.getString(REPRESENTATION_TYPE);
        String address2 = "";
        String address3 = "";
        String email = "";
        if (nonNull(organisationDetailsForUserJsonObject)) {
            if (organisationDetailsForUserJsonObject.toString().contains(ADDRESS_LINE_2)) {
                address2 = organisationDetailsForUserJsonObject.getString(ADDRESS_LINE_2);
            }
            if (organisationDetailsForUserJsonObject.toString().contains(ADDRESS_LINE_3)) {
                address3 = organisationDetailsForUserJsonObject.getString(ADDRESS_LINE_3);
            }
            if (organisationDetailsForUserJsonObject.toString().contains(EMAIL)) {
                email = organisationDetailsForUserJsonObject.getString(EMAIL);
            }
        }
        if (nonNull(organisationDetailsForUserJsonObject)) {
            final JsonObjectBuilder objectBuilder = createObjectBuilder()
                    .add(ORGANISATION_ID, organisationDetailsForUserJsonObject.getString(ORGANISATION_ID))
                    .add(ORGANISATION_NAME, organisationDetailsForUserJsonObject.getString(ORGANISATION_NAME))
                    .add(ADDRESS, createObjectBuilder()
                            .add(ADDRESS_1, organisationDetailsForUserJsonObject.getString(ADDRESS_LINE_1))
                            .add(ADDRESS_2, address2)
                            .add(ADDRESS_3, address3)
                            .add(ADDRESS_4, organisationDetailsForUserJsonObject.getString(ADDRESS_LINE_4))
                            .add(ADDRESS_POSTCODE, organisationDetailsForUserJsonObject.getString(ADDRESS_POSTCODE))
                    )
                    .add(STATUS, status)
                    .add(START_DATE, startDate)
                    .add(REPRESENTATION_TYPE, representationType)
                    .add(PHONE_NUMBER, organisationDetailsForUserJsonObject.getString(PHONE_NUMBER))
                    .add(EMAIL, email);

            endDate.ifPresent(edt -> objectBuilder.add(END_DATE, edt));
            return objectBuilder.build();
        } else {
            return createObjectBuilder()
                    .add(STATUS, status)
                    .add(START_DATE, startDate)
                    .add(REPRESENTATION_TYPE, representationType)
                    .build();
        }

    }
}
