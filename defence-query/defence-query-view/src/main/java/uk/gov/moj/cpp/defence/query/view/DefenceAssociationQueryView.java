package uk.gov.moj.cpp.defence.query.view;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static uk.gov.justice.services.core.annotation.Component.QUERY_VIEW;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationDefendantRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociationDefendant;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.persistence.NoResultException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(QUERY_VIEW)
public class DefenceAssociationQueryView {

    public static final String EMPTY_VALUE = "";
    private static final Logger LOGGER = LoggerFactory.getLogger(DefenceAssociationQueryView.class);
    private static final String DEFENDANT_ID = "defendantId";
    private static final String USER_ID = "userId";
    private static final String ASSOCIATED = "Active Barrister/Solicitor of record";
    private static final String ASSOCIATION = "association";
    private static final String ASSOCIATIONS = "associations";
    private static final String ORGANISATION_ID = "organisationId";
    private static final String ORGANISATION_NAME = "organisationName";
    private static final String STATUS = "status";
    private static final String ADDRESS = "address";
    private static final String ADDRESS_LINE_1 = "address1";
    private static final String ADDRESS_LINE_4 = "address4";
    private static final String ADDRESS_POSTCODE = "addressPostcode";
    private static final String EMAIL = "email";
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";
    private static final String REPRESENTATION_TYPE = "representationType";

    @Inject
    private DefenceAssociationDefendantRepository defenceAssociationDefendantRepository;

    @Inject
    private DefenceAssociationRepository defenceAssociationRepository;

    @Handles("defence.query.associated-organisation")
    public JsonEnvelope getAssociatedOrganisation(final JsonEnvelope envelope) {
        final UUID defendantId = fromString(envelope.payloadAsJsonObject().getString(DEFENDANT_ID));
        final DefenceAssociationDefendant defenceAssociationDefendant;
        try {
            defenceAssociationDefendant = defenceAssociationDefendantRepository.findOptionalByDefendantId(defendantId);
        } catch (final NoResultException nre) {
            LOGGER.debug("No Association exist", nre);
            return emptyAssociation(envelope);
        }
        final DefenceAssociation defenceAssociation = extractCurrentDefenceAssociation(defenceAssociationDefendant);
        if (defenceAssociation == null || defenceAssociation.getOrgId() == null) {
            return emptyAssociation(envelope);
        }
        return formResponseWithAssociationDetails(envelope, defenceAssociation);
    }

    @Handles("defence.query.associated-organisations")
    public JsonEnvelope getAssociatedOrganisations(final JsonEnvelope envelope) {
        final UUID defendantId = fromString(envelope.payloadAsJsonObject().getString(DEFENDANT_ID));
        final DefenceAssociationDefendant defenceAssociationDefendant;
        try {
            defenceAssociationDefendant = defenceAssociationDefendantRepository.findOptionalByDefendantId(defendantId);
        } catch (final NoResultException nre) {
            LOGGER.debug("No Association exist", nre);
            return emptyAssociations(envelope);
        }
        final List<DefenceAssociation> defenceAssociationList = extractDefenceAssociations(defenceAssociationDefendant);
        if (isNull(defenceAssociationList) || defenceAssociationList.isEmpty()) {
            return emptyAssociations(envelope);
        }
        return JsonEnvelope.envelopeFrom(
                envelope.metadata(),
                formDefenceAssociationsPayload(defenceAssociationList));
    }

    @Handles("defence.query.get-associated-defendants")
    public JsonEnvelope getAssociatedDefendants(final JsonEnvelope envelope) {
        final UUID userId = fromString(envelope.payloadAsJsonObject().getString(USER_ID));

        final List<DefenceAssociation> defenceAssociations = defenceAssociationRepository.findByUserIdAndCurrentDate(userId, ZonedDateTime.now());
        if (!defenceAssociations.isEmpty()) {
            return getDefendantIds(envelope, defenceAssociations);
        }
        return emptyDefendants(envelope);
    }

    private boolean isDefenceAssociationEmpty(final DefenceAssociationDefendant defenceAssociationDefendant) {
        return defenceAssociationDefendant == null ||
                defenceAssociationDefendant.getDefenceAssociations() == null ||
                defenceAssociationDefendant.getDefenceAssociations().isEmpty();
    }

    private JsonEnvelope formResponseWithAssociationDetails(final JsonEnvelope envelope, final DefenceAssociation defenceAssociation) {
        return JsonEnvelope.envelopeFrom(
                envelope.metadata(),
                formDefenceAssociationPayload(defenceAssociation));
    }

    private JsonEnvelope getDefendantIds(final JsonEnvelope envelope, List<DefenceAssociation> defenceAssociations) {
        return JsonEnvelope.envelopeFrom(
                envelope.metadata(),
                getDefendants(defenceAssociations));
    }

    private DefenceAssociation extractCurrentDefenceAssociation(final DefenceAssociationDefendant defenceAssociationDefendant) {
        if (isDefenceAssociationEmpty(defenceAssociationDefendant)) {
            return null;
        }
        final List<DefenceAssociation> defenceAssociations = defenceAssociationDefendant.getDefenceAssociations()
                .stream()
                .filter(d -> d.getEndDate() == null)
                .collect(Collectors.toList());

        if (defenceAssociations.size() > 1) {
            throw new IllegalStateException("Cannot have more than one Organisation Associated at any point in time");
        }

        return !defenceAssociations.isEmpty() ? defenceAssociations.get(0) : null;
    }

    private List<DefenceAssociation> extractDefenceAssociations(final DefenceAssociationDefendant defenceAssociationDefendant) {
        if (isDefenceAssociationEmpty(defenceAssociationDefendant)) {
            return emptyList();
        }
        return defenceAssociationDefendant.getDefenceAssociations().stream()
                .filter(d -> nonNull(d.getOrgId()))
                .sorted(Comparator.comparing(DefenceAssociation::getStartDate))
                .toList();
    }

    private JsonObject formDefenceAssociationsPayload(final List<DefenceAssociation> defenceAssociation) {

        final JsonArrayBuilder associationJsonArray = createArrayBuilder();
        defenceAssociation.forEach(da -> {
            final String status = isNull(da.getEndDate()) ? ASSOCIATED : EMPTY_VALUE;
            final String startDate = ZonedDateTimes.toString(da.getStartDate());
            final Optional<String> endDate = ofNullable(da.getEndDate()).map(ZonedDateTimes::toString);

            final String representationType = nonNull(da.getRepresentationType()) ? da.getRepresentationType() : EMPTY_VALUE;

            final JsonObjectBuilder associationDetailJson = createObjectBuilder()
                    .add(ORGANISATION_ID, da.getOrgId().toString())
                    .add(ORGANISATION_NAME, EMPTY_VALUE)
                    .add(STATUS, status)
                    .add(ADDRESS, createObjectBuilder()
                            .add(ADDRESS_LINE_1, EMPTY_VALUE)
                            .add(ADDRESS_LINE_4, EMPTY_VALUE)
                            .add(ADDRESS_POSTCODE, EMPTY_VALUE)
                            .add(EMAIL, EMPTY_VALUE)
                    )
                    .add(START_DATE, startDate)
                    .add(REPRESENTATION_TYPE, representationType);
            endDate.ifPresent(edt -> associationDetailJson.add(END_DATE, edt));
            associationJsonArray.add(associationDetailJson);
        });

        return createObjectBuilder()
                .add(ASSOCIATIONS, associationJsonArray.build())
                .build();
    }

    private JsonObject formDefenceAssociationPayload(final DefenceAssociation defenceAssociation) {

        String organisationId = EMPTY_VALUE;
        String status = EMPTY_VALUE;
        String startDate = EMPTY_VALUE;
        String representationType = EMPTY_VALUE;
        if (defenceAssociation.getUserId() != null && defenceAssociation.getStartDate() != null) {
            organisationId = defenceAssociation.getOrgId().toString();
            startDate = ZonedDateTimes.toString(defenceAssociation.getStartDate());
            representationType = defenceAssociation.getRepresentationType();
            status = ASSOCIATED;
        }
        return formResponse(organisationId, status, startDate, representationType);
    }

    private JsonObject formResponse(final String organisationId,
                                    final String status,
                                    final String startDate,
                                    final String representationType) {

        return createObjectBuilder()
                .add(ASSOCIATION, createObjectBuilder()
                        .add(ORGANISATION_ID, organisationId)
                        .add(ORGANISATION_NAME, EMPTY_VALUE)
                        .add(STATUS, status)
                        .add(ADDRESS, createObjectBuilder()
                                .add(ADDRESS_LINE_1, EMPTY_VALUE)
                                .add(ADDRESS_LINE_4, EMPTY_VALUE)
                                .add(ADDRESS_POSTCODE, EMPTY_VALUE)
                                .add(EMAIL, EMPTY_VALUE)
                        )
                        .add(START_DATE, startDate)
                        .add(REPRESENTATION_TYPE, representationType)
                )
                .build();
    }

    private JsonEnvelope emptyAssociation(final JsonEnvelope envelope) {
        return JsonEnvelope.envelopeFrom(
                envelope.metadata(),
                createObjectBuilder()
                        .add(ASSOCIATION, createObjectBuilder())
                        .build());
    }

    private JsonEnvelope emptyAssociations(final JsonEnvelope envelope) {
        return JsonEnvelope.envelopeFrom(
                envelope.metadata(),
                createObjectBuilder()
                        .add(ASSOCIATIONS, createArrayBuilder())
                        .build());
    }

    private JsonObject getDefendants(List<DefenceAssociation> defenceAssociations) {

        final JsonArrayBuilder defendantIdsBuilder = createArrayBuilder();
        defenceAssociations.forEach(defenceAssociation ->
                defendantIdsBuilder.add(defenceAssociation.getDefenceAssociationDefendant().getDefendantId().toString())
        );
        return createObjectBuilder()
                .add("defendantIds", defendantIdsBuilder.build())
                .build();
    }

    private JsonEnvelope emptyDefendants(final JsonEnvelope envelope) {
        return JsonEnvelope.envelopeFrom(
                envelope.metadata(),
                createObjectBuilder()
                        .add("defendantIds", createArrayBuilder())
                        .build()
        );
    }
}
