package uk.gov.moj.cpp.defence.command.service;

import static java.util.Objects.nonNull;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;

import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.query.view.DefenceGrantAccessQueryView;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

public class DefenceService {

    public static final String GRANTEES = "grantees";
    public static final String GRANTEE_USER_ID = "granteeUserId";
    private static final String DEFENCE_QUERY_DEFENCE_ACCESS_FOR_CASE = "defence.query.defence-access-for-case";
    private static final String CASE_ID = "caseId";
    @Inject
    private DefenceGrantAccessQueryView defenceGrantAccessQueryView;

    @SuppressWarnings("squid:S1166")
    public boolean isAssigneeDefendingTheCase(final Metadata metadata, final UUID caseId, final UUID granteeUserId) {
        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName(DEFENCE_QUERY_DEFENCE_ACCESS_FOR_CASE),
                createObjectBuilder().
                        add(CASE_ID, caseId.toString())
                        .add(GRANTEE_USER_ID, granteeUserId.toString()));

        final JsonObject response = defenceGrantAccessQueryView.getCaseGrantee(queryEnvelope).payloadAsJsonObject();
        return nonNull(response) && hasGranteeDetailsFound(response);
    }

    private boolean hasGranteeDetailsFound(final JsonObject response) {
        return response.containsKey(GRANTEES) && isNotEmpty(response.getJsonArray(GRANTEES));
    }


}
