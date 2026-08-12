package uk.gov.moj.cpp.defence.service;

import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

public class DefenceService {

    static final String DEFENCE_GET_ADVOCATES_BY_CASE_ORGANISATION_QUERY = "defence.query.case-organisation-assignees";
    static final String ASSIGNEES = "assignees";
    public static final String IS_ADVOCATE_DEFENDING_OR_PROSECUTING = "isAdvocateDefendingOrProsecuting";
    public static final String BOTH = "both";
    public static final String PROSECUTING = "prosecuting";

    @Inject
    @ServiceComponent(QUERY_API)
    private Requester requester;

    @Inject
    private CpsCaseAccessQueryView cpsCaseAccessQueryView;

    public boolean hasAdvocatesAssignedToTheCase(final JsonEnvelope envelope, final String caseId, String organisationId) {
        final Metadata metadata = Envelope.metadataFrom(envelope.metadata()).withName(DEFENCE_GET_ADVOCATES_BY_CASE_ORGANISATION_QUERY).build();

        final JsonEnvelope requestEnvelope = JsonEnvelope.envelopeFrom(
                metadata,
                createObjectBuilder().add("caseId", caseId).add("organisationId", organisationId).build());
        final JsonEnvelope response = cpsCaseAccessQueryView.getAssignedAdvocatesToTheCaseAndOrganisation(requestEnvelope);
        return !response.asJsonObject().getJsonArray(ASSIGNEES).isEmpty();
    }

    public boolean isInProsecutorRole(final Envelope request, final UUID caseId, final UUID userId) {
        final JsonObject roleInCaseJsonObject = queryRoleInCase(request, caseId, userId);
        return roleInCaseJsonObject.containsKey(IS_ADVOCATE_DEFENDING_OR_PROSECUTING) &&
                (PROSECUTING.equals(roleInCaseJsonObject.getJsonString(IS_ADVOCATE_DEFENDING_OR_PROSECUTING).getString()) ||
                        BOTH.equals(roleInCaseJsonObject.getJsonString(IS_ADVOCATE_DEFENDING_OR_PROSECUTING).getString())
                );
    }

    private JsonObject queryRoleInCase(final Envelope request, final UUID caseId, final UUID userId) {
        final JsonObject roleInCasePayload = createObjectBuilder()
                .add("caseId", caseId.toString())
                .add("userId", userId.toString())
                .build();
        final Envelope<JsonObject> requestEnvelope = Enveloper.envelop(roleInCasePayload)
                .withName("advocate.query.role-in-case-by-caseid").withMetadataFrom(request);

        final JsonEnvelope response = cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(JsonEnvelope.envelopeFrom(requestEnvelope.metadata(), roleInCasePayload));
        return response.payloadAsJsonObject();

    }
}
