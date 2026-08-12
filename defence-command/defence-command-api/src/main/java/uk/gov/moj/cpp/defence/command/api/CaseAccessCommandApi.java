package uk.gov.moj.cpp.defence.command.api;


import static java.lang.Integer.parseInt;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.command.helper.JsonHelper.removeProperty;
import static uk.gov.moj.cpp.defence.common.util.ErrorType.CASE_NOT_FOUND;
import static uk.gov.moj.cpp.defence.common.util.ErrorType.ORGANISATION_NOT_PROSECUTING_AUTHORITY;

import uk.gov.justice.cps.defence.AssignCase;
import uk.gov.justice.cps.defence.AssignCaseByHearing;
import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.command.error.CaseNotFoundException;
import uk.gov.moj.cpp.defence.command.error.OrganisationNotProsecutingAuthorityException;
import uk.gov.moj.cpp.defence.command.service.DefenceService;
import uk.gov.moj.cpp.defence.query.view.ProsecutionCaseAuthority;
import uk.gov.moj.cpp.defence.service.ProgressionService;
import uk.gov.moj.cpp.defence.service.ReferenceDataService;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.defence.service.UsersGroupQueryService;

import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

@ServiceComponent(COMMAND_API)
public class CaseAccessCommandApi {
    public static final String DEFENCE_COMMAND_HANDLER_ADVOCATE_ASSIGN_CASE = "defence.command.handler.advocate.assign-case";
    public static final String DEFENCE_COMMAND_HANDLER_ADVOCATE_REMOVE_CASE_ASSIGNMENT = "defence.command.handler.advocate.remove-case-assignment";
    public static final String DEFENCE_COMMAND_HANDLER_ADVOCATE_ASSIGN_CASE_BY_HEARING_LISTING = "defence.command.handler.advocate.assign-case-by-hearing-listing";
    public static final String DEFENCE_COMMAND_HANDLER_SYSTEM_SCHEDULE_ADVOCATE_ACCESS = "defence.command.handler.system-schedule-advocate-access";
    public static final String IS_ASSIGNEE_DEFENDING_CASE = "isAssigneeDefendingCase";
    public static final String CASE_ID = "caseId";
    public static final String HEARING_ID = "hearingId";
    public static final String PROSECUTION_AUTHORITY_ID = "prosecutionAuthorityId";
    public static final String REPRESENTING_ORGANISATION = "representingOrganisation";
    public static final String ASSIGNEE_ID = "assigneeId";
    public static final String CPS_FLAG = "cpsFlag";
    public static final String POLICE_FLAG = "policeFlag";
    public static final String CASE_IDS = "caseIds";
    public static final String CASE_HEARINGS = "caseHearings";
    public static final String IS_CPS = "isCps";
    public static final String IS_POLICE = "isPolice";
    public static final String FAILURE_REASON = "failureReason";
    public static final String ERROR_CODE = "errorCode";
    public static final String NON_CPS_PROSECUTORS = "Non CPS Prosecutors";
    public static final String SHORT_NAME = "shortName";
    public static final String ORGANISATION_MATCH = "OrganisationMatch";

    @Inject
    ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Inject
    private Sender sender;

    @Inject
    private DefenceService defenceService;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    @ServiceComponent(QUERY_API)
    private UserGroupService usersGroupService;

    @Inject
    private ProgressionService progressionService;

    @Inject
    private UsersGroupQueryService usersGroupQueryService;

    @Inject
    @ServiceComponent(QUERY_API)
    private Requester requester;

    @Handles("defence.command.advocate.assign-case")
    public void assignCase(final Envelope<AssignCase> envelope) {
        final JsonObject enrichedPayload = enrichAssignCasePayload(envelope.payload(), envelope.metadata());
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName(DEFENCE_COMMAND_HANDLER_ADVOCATE_ASSIGN_CASE),
                enrichedPayload));
    }

    @Handles("defence.command.advocate.remove-case-assignment")
    public void removeCaseAssignment(final JsonEnvelope envelope) {
        sender.send(Enveloper.envelop(envelope.payloadAsJsonObject())
                .withName(DEFENCE_COMMAND_HANDLER_ADVOCATE_REMOVE_CASE_ASSIGNMENT)
                .withMetadataFrom(envelope));
    }

    @Handles("defence.command.advocate.assign-case-by-hearing-listing")
    public void assignCaseByHearingListing(final Envelope<AssignCaseByHearing> envelope) {
        final JsonObject enrichedPayload = enrichAssignCasePayload(envelope.payload(), envelope.metadata());
        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName(DEFENCE_COMMAND_HANDLER_ADVOCATE_ASSIGN_CASE_BY_HEARING_LISTING),
                enrichedPayload));
    }

    @Handles("defence.command.system-schedule-advocate-access")
    public void scheduleSystemAdvocateAccess(final JsonEnvelope envelope) {
        sender.send(Enveloper.envelop(envelope.payloadAsJsonObject())
                .withName(DEFENCE_COMMAND_HANDLER_SYSTEM_SCHEDULE_ADVOCATE_ACCESS)
                .withMetadataFrom(envelope));

    }

    private JsonObject enrichAssignCasePayload(final AssignCaseByHearing assignCaseByHearing, final Metadata metadata) {
        final JsonArrayBuilder caseHearingAssignmentDetails = createArrayBuilder();
        final PersonDetails assigneeDetails = usersGroupService.getUserDetailsWithEmail(assignCaseByHearing.getAssigneeEmailId(), metadata, requester);

        assignCaseByHearing.getCaseHearings().forEach(caseHearing -> {
            final UUID caseId = caseHearing.getCaseId();
            final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder()
                    .add(CASE_ID, caseId.toString())
                    .add(HEARING_ID, caseHearing.getHearingId().toString());
            final ProsecutionCaseAuthority prosecutionCaseAuthority = progressionService.getProsecutionCaseAuthority(metadata, caseId);
            if (nonNull(prosecutionCaseAuthority)) {
                validateProsecutionAuthorityAndCaptureErrors(metadata, prosecutionCaseAuthority.getProsecutionAuthorityId(), jsonObjectBuilder);
                jsonObjectBuilder
                        .add(IS_ASSIGNEE_DEFENDING_CASE, isAssigneeDefending(metadata, caseId, assigneeDetails))
                        .add(PROSECUTION_AUTHORITY_ID, prosecutionCaseAuthority.getProsecutionAuthorityId().toString());
                ofNullable(assigneeDetails).ifPresent(assigneeDetailsObject -> jsonObjectBuilder.add(ASSIGNEE_ID, assigneeDetailsObject.getUserId().toString()));
            } else {
                jsonObjectBuilder
                        .add(FAILURE_REASON, CASE_NOT_FOUND.name())
                        .add(ERROR_CODE, parseInt(CASE_NOT_FOUND.getCode()));
            }

            caseHearingAssignmentDetails.add(jsonObjectBuilder.build());
        });

        final JsonObject payload = removeProperty(objectToJsonObjectConverter.convert(assignCaseByHearing), CASE_HEARINGS);
        final JsonObjectBuilder enrichedAssignCaseBuilder = createObjectBuilder(payload);
        enrichedAssignCaseBuilder.add("caseHearingAssignmentDetails", caseHearingAssignmentDetails.build());
        return enrichedAssignCaseBuilder.build();
    }

    private JsonObject enrichAssignCasePayload(final AssignCase assignCase, final Metadata metadata) {
        final JsonArrayBuilder caseAssignmentDetails = createArrayBuilder();
        final PersonDetails assigneeDetails = usersGroupService.getUserDetailsWithEmail(assignCase.getAssigneeEmailId(), metadata, requester);
        assignCase.getCaseIds().forEach(caseId -> {
            final ProsecutionCaseAuthority prosecutionCaseAuthority = progressionService.getProsecutionCaseAuthority(metadata, UUID.fromString(caseId.toString()));
            if (nonNull(prosecutionCaseAuthority)) {
                final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder();
                validateProsecutionAuthority(metadata, prosecutionCaseAuthority.getProsecutionAuthorityId(), jsonObjectBuilder);
                jsonObjectBuilder.add(IS_ASSIGNEE_DEFENDING_CASE, isAssigneeDefending(metadata, caseId, assigneeDetails))
                        .add(CASE_ID, caseId.toString())
                        .add(PROSECUTION_AUTHORITY_ID, prosecutionCaseAuthority.getProsecutionAuthorityId().toString());
                ofNullable(assigneeDetails).ifPresent(assigneeDetailsObject -> jsonObjectBuilder.add(ASSIGNEE_ID, assigneeDetailsObject.getUserId().toString()));
                caseAssignmentDetails.add(jsonObjectBuilder.build());
            } else {
                throw new CaseNotFoundException(CASE_NOT_FOUND.getMessage() + caseId);
            }
        });

        final JsonObject payload = removeProperty(objectToJsonObjectConverter.convert(assignCase), CASE_IDS);
        final JsonObjectBuilder enrichedAssignCaseBuilder = createObjectBuilder(payload);
        enrichedAssignCaseBuilder.add("caseAssignmentDetails", caseAssignmentDetails.build());
        return enrichedAssignCaseBuilder.build();

    }

    private boolean isAssigneeDefending(final Metadata metadata, final UUID caseId, final PersonDetails assigneeDetails) {
        if (isNull(assigneeDetails)) {
            return false;
        }
        return defenceService.isAssigneeDefendingTheCase(metadata, caseId, assigneeDetails.getUserId());

    }

    @SuppressWarnings("squid:S3655")
    private void validateProsecutionAuthority(final Metadata metadata, final UUID prosecutionAuthorityId, final JsonObjectBuilder jsonObjectBuilder) {
        final UUID userId = metadata.userId().isPresent() ? fromString(metadata.userId().get()) : null;
        final Optional<JsonObject> prosecutorJsonObjectOptional = referenceDataService.getProsecutor(metadata, prosecutionAuthorityId);
        final Optional<String> isNonCPSUserWithValidProsecutingAuthority = usersGroupQueryService.validateNonCPSUserOrg(metadata, userId, NON_CPS_PROSECUTORS, prosecutorJsonObjectOptional.get().getString(SHORT_NAME));
        final JsonObject prosecutorJsonObject = prosecutorJsonObjectOptional.get();
        final boolean cpsFlag = getBooleanValue(prosecutorJsonObject, CPS_FLAG);
        final boolean policeFlag = getBooleanValue(prosecutorJsonObject, POLICE_FLAG);
        jsonObjectBuilder.add(IS_CPS, cpsFlag);
        jsonObjectBuilder.add(IS_POLICE, policeFlag);
        if (isNonCPSUserWithValidProsecutingAuthority.isPresent()) {
            if (!ORGANISATION_MATCH.equals(isNonCPSUserWithValidProsecutingAuthority.get())) {
                throw new OrganisationNotProsecutingAuthorityException();
            } else {
                jsonObjectBuilder
                        .add(REPRESENTING_ORGANISATION, prosecutorJsonObjectOptional.get().getString(SHORT_NAME));
            }
        } else {
            if ((!cpsFlag && !policeFlag)) {
                throw new OrganisationNotProsecutingAuthorityException();
            }
        }
    }

    @SuppressWarnings("squid:S3655")
    private void validateProsecutionAuthorityAndCaptureErrors(final Metadata metadata, final UUID prosecutionAuthorityId, final JsonObjectBuilder jsonObjectBuilder) {
        final UUID userId = metadata.userId().isPresent() ? fromString(metadata.userId().get()) : null;
        final Optional<JsonObject> prosecutorJsonObjectOptional = referenceDataService.getProsecutor(metadata, prosecutionAuthorityId);
        final Optional<String> isNonCPSUserWithValidProsecutingAuthority = usersGroupQueryService.validateNonCPSUserOrg(metadata, userId, NON_CPS_PROSECUTORS, prosecutorJsonObjectOptional.get().getString(SHORT_NAME));
        prosecutorJsonObjectOptional
                .ifPresent(pJson -> {
                    final JsonObject prosecutorJsonObject = prosecutorJsonObjectOptional.get();
                    final boolean cpsFlag = getBooleanValue(prosecutorJsonObject, CPS_FLAG);
                    final boolean policeFlag = getBooleanValue(prosecutorJsonObject, POLICE_FLAG);
                    jsonObjectBuilder.add(IS_CPS, cpsFlag);
                    jsonObjectBuilder.add(IS_POLICE, policeFlag);
                    if (isNonCPSUserWithValidProsecutingAuthority.isPresent()) {
                        validateNonCpsProsecutor(jsonObjectBuilder, prosecutorJsonObjectOptional, isNonCPSUserWithValidProsecutingAuthority);
                    } else {
                        if (!cpsFlag && !policeFlag) {
                            jsonObjectBuilder
                                    .add(FAILURE_REASON, ORGANISATION_NOT_PROSECUTING_AUTHORITY.name())
                                    .add(ERROR_CODE, parseInt(ORGANISATION_NOT_PROSECUTING_AUTHORITY.getCode()));
                        }
                    }
                });
    }

    @SuppressWarnings("squid:S3655")
    private void validateNonCpsProsecutor(final JsonObjectBuilder jsonObjectBuilder, final Optional<JsonObject> prosecutorJsonObjectOptional, final Optional<String> isNonCPSUserWithValidProsecutingAuthority) {
        if (!ORGANISATION_MATCH.equals(isNonCPSUserWithValidProsecutingAuthority.get())) {
            jsonObjectBuilder
                    .add(FAILURE_REASON, ORGANISATION_NOT_PROSECUTING_AUTHORITY.name())
                    .add(ERROR_CODE, parseInt(ORGANISATION_NOT_PROSECUTING_AUTHORITY.getCode()));
        } else {
            jsonObjectBuilder
                    .add(REPRESENTING_ORGANISATION, prosecutorJsonObjectOptional.get().getString(SHORT_NAME));
        }
    }

    private boolean getBooleanValue(final JsonObject jsonObject, final String key) {
        if (jsonObject.containsKey(key)) {
            return jsonObject.getBoolean(key);
        }
        return false;
    }

}
