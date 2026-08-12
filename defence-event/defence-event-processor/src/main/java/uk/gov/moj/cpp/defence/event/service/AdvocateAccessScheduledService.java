package uk.gov.moj.cpp.defence.event.service;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView.EXPIRED_ASSIGNMENTS_SELECT_COUNT;

import uk.gov.justice.cps.defence.ExpiredProsecutorAssignments;
import uk.gov.justice.cps.defence.ExpiredProsecutorOrganisationAssignments;
import uk.gov.justice.services.common.configuration.Value;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@SuppressWarnings("squid:S1135")
public class AdvocateAccessScheduledService {

    public static final String DEFENCE_COMMAND_ADVOCATE_REMOVE_CASE_ASSIGNMENT = "defence.command.handler.advocate.remove-case-assignment";
    public static final String DEFENCE_QUERY_EXPIRED_PROSECUTOR_ASSIGNMENTS = "advocate.query.expired-prosecutor-assignments";
    public static final String DEFENCE_QUERY_EXPIRED_ORGANISATION_ASSIGNMENTS = "advocate.query.expired-prosecutor-organisation-assignments";

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Sender sender;

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private Requester requester;

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    @ServiceComponent(EVENT_PROCESSOR)
    private CpsCaseAccessQueryView cpsCaseAccessQueryView;

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvocateAccessScheduledService.class);

    @Inject
    @Value(key = "defence.expired.prosecutor.assignments.select.count", defaultValue = "50")
    private String expiredProsecutorAssignmentsSelectCount;

    public void unassignExpiredAssignments() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("AdvocateAccessScheduledService scheduler triggered.");
        }

        final UUID metaDataId = randomUUID();
        JsonEnvelope requestEnvelope = envelopeFrom(
                metadataBuilder().withId(metaDataId).withName(DEFENCE_QUERY_EXPIRED_PROSECUTOR_ASSIGNMENTS).build(),
                createObjectBuilder().add(EXPIRED_ASSIGNMENTS_SELECT_COUNT, expiredProsecutorAssignmentsSelectCount).build());
        final MetadataBuilder metadataBuilder = metadataBuilder().withId(metaDataId).withName(DEFENCE_COMMAND_ADVOCATE_REMOVE_CASE_ASSIGNMENT);

        final ExpiredProsecutorAssignments expiredProsecutorAssignments = cpsCaseAccessQueryView.queryExpiredProsecutorAssignments(requestEnvelope).payload();
        final JsonObject response = objectToJsonObjectConverter.convert(expiredProsecutorAssignments);
        LOGGER.info("Expired prosecutor assignments: {}", expiredProsecutorAssignments.getProsecutorAssignments().size());

        // 1. Query and process expired advocate prosecutor assignments
        final JsonArray prosecutorAssignments = response.getJsonArray("prosecutorAssignments");
        if (prosecutorAssignments != null) {
            sendCommandToRemoveCaseAssignment(prosecutorAssignments, metadataBuilder);
        }

        // 2. Query and process expired organisation assignments
        requestEnvelope = envelopeFrom(
                metadataBuilder().withId(metaDataId).withName(DEFENCE_QUERY_EXPIRED_ORGANISATION_ASSIGNMENTS).build(),
                createObjectBuilder().add(EXPIRED_ASSIGNMENTS_SELECT_COUNT, expiredProsecutorAssignmentsSelectCount).build());
        final ExpiredProsecutorOrganisationAssignments expiredProsecutorOrganisationAssignments = cpsCaseAccessQueryView.queryExpiredProsecutorOrganisationAssignments(requestEnvelope).payload();
        LOGGER.info("Expired organisation assignments: {}", expiredProsecutorOrganisationAssignments.getOrganisationAssignments().size());

        final JsonArray organisationAssignments = objectToJsonObjectConverter.convert(expiredProsecutorOrganisationAssignments).getJsonArray("organisationAssignments");
        if (organisationAssignments != null) {
            sendCommandToRemoveCaseAssignment(organisationAssignments, metadataBuilder);
        }
    }

    private void sendCommandToRemoveCaseAssignment(final JsonArray caseAssignments, final MetadataBuilder metadataBuilder) {
        for (int i = 0; i < caseAssignments.size(); i++) {
            final String caseId = caseAssignments.getJsonObject(i).getString("caseId");
            final String assigneeUserId = caseAssignments.getJsonObject(i).getString("assigneeUserId");
            sender.sendAsAdmin(Envelope.envelopeFrom(metadataBuilder, createObjectBuilder()
                    .add("caseId", caseId)
                    .add("assigneeUserId", assigneeUserId)
                    .add("isAutomaticUnassignment", true)
                    .build()));
        }
    }

}
