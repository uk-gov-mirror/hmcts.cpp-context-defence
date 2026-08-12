package uk.gov.moj.cpp.defence.query.view;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.UUID.fromString;
import static uk.gov.justice.services.core.annotation.Component.QUERY_VIEW;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.cps.defence.Grantee;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.persistence.DefenceGrantAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceGrantAccess;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.OrganisationDetails;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.json.JsonArrayBuilder;


@ServiceComponent(QUERY_VIEW)
public class DefenceGrantAccessQueryView {

    private static final String DEFENDANT_CLIENT_ID = "defendantClientId";
    private static final String CASE_ID = "caseId";
    private static final String GRANTEE_USER_ID = "granteeUserId";
    private static final String GRANTED_ACCESS_BY = "Granted access by %s %s";
    private static final String GRANTEES = "grantees";


    @Inject
    private DefenceGrantAccessRepository defenceGrantAccessRepository;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Handles("defence.query.grantees-organisation")
    public JsonEnvelope getDefenceClientGrantees(final JsonEnvelope envelope) {

        final UUID defendantClientId = fromString(envelope.payloadAsJsonObject().getString(DEFENDANT_CLIENT_ID));
        final List<DefenceGrantAccess> defenceGrantAccess = defenceGrantAccessRepository.findByDefenceClient(defendantClientId);

        return getJsonEnvelopeWithListOfGrantees(envelope, defenceGrantAccess);
    }

    @SuppressWarnings("squid:S1166")
    @Handles("defence.query.defence-access-for-case")
    public JsonEnvelope getCaseGrantee(final JsonEnvelope envelope) {

        final UUID caseId = fromString(envelope.payloadAsJsonObject().getString(CASE_ID));
        final UUID granteeUserId = fromString(envelope.payloadAsJsonObject().getString(GRANTEE_USER_ID));
        final List<DefenceGrantAccess> defenceGrantAccesses = defenceGrantAccessRepository.findByGranteeAndCaseId(caseId, granteeUserId);

        return getJsonEnvelopeWithListOfGrantees(envelope, defenceGrantAccesses);
    }

    private JsonEnvelope getJsonEnvelopeWithListOfGrantees(final JsonEnvelope envelope, final List<DefenceGrantAccess> defenceGrantAccess) {
        if (isNull(defenceGrantAccess) || defenceGrantAccess.isEmpty()) {
            return emptyAssociation(envelope, GRANTEES);
        }

        final List<Grantee> grantees = defenceGrantAccess.stream().map(this::mapToResponse).collect(Collectors.toList());
        final JsonArrayBuilder granteesJson = createArrayBuilder();
        for (final Grantee grantee : grantees) {
            granteesJson.add(objectToJsonObjectConverter.convert(grantee));

        }
        return JsonEnvelope.envelopeFrom(
                envelope.metadata(),
                createObjectBuilder()
                        .add(GRANTEES, granteesJson)
                        .build());
    }

    private Grantee mapToResponse(final DefenceGrantAccess defenceGrantAccess) {
        final DefenceUserDetails granteeDefenceUserDetails = defenceGrantAccess.getGranteeDefenceUserDetails();
        final DefenceUserDetails grantorDefenceUserDetails = defenceGrantAccess.getGrantorDefenceUserDetails();
        final OrganisationDetails granteeOrganisationDetails = defenceGrantAccess.getGranteeOrganisationDetails();
        return Grantee.grantee()
                .withUserId(granteeDefenceUserDetails.getUserId())
                .withFirstName(granteeDefenceUserDetails.getFirstName())
                .withLastName(granteeDefenceUserDetails.getLastName())
                .withGranteeStatus(format(GRANTED_ACCESS_BY, grantorDefenceUserDetails.getFirstName(), grantorDefenceUserDetails.getLastName()))
                .withOrganisationId(granteeOrganisationDetails.getOrganisationId())
                .withOrganisationName(granteeOrganisationDetails.getOrganisationName())
                .withStartDate(defenceGrantAccess.getStartDate()).build();
    }


    private JsonEnvelope emptyAssociation(final JsonEnvelope envelope, final String fieldName) {
        return JsonEnvelope.envelopeFrom(
                envelope.metadata(),
                createObjectBuilder()
                        .add(fieldName, createArrayBuilder())
                        .build());
    }


}
