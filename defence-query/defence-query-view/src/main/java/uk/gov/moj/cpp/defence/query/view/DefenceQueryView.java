package uk.gov.moj.cpp.defence.query.view;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static uk.gov.justice.cps.defence.ErrorCode.CASE_NOT_FOUND;
import static uk.gov.justice.cps.defence.ErrorCode.ORGANISATION_NOT_PROSECUTING_AUTHORITY;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.getBoolean;
import static uk.gov.justice.services.messaging.JsonObjects.getString;

import uk.gov.justice.cps.defence.Allegations;
import uk.gov.justice.cps.defence.CaseDefendantsOrganisations;
import uk.gov.justice.cps.defence.CaseForProsecutorAssignment;
import uk.gov.justice.cps.defence.DefenceClientId;
import uk.gov.justice.cps.defence.Error;
import uk.gov.justice.cps.defence.ErrorCode;
import uk.gov.justice.cps.defence.SearchCaseByUrn;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.common.Defendant;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.IdpcDetails;
import uk.gov.moj.cpp.defence.service.DefendantAllocationService;
import uk.gov.moj.cpp.defence.service.ProgressionService;
import uk.gov.moj.cpp.defence.service.ReferenceDataService;
import uk.gov.moj.cpp.defence.service.UsersGroupQueryService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.InternalServerErrorException;

@ServiceComponent(Component.QUERY_VIEW)
public class DefenceQueryView {

    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");
    private static final String DEFENCE_CLIENT_ID = "defenceClientId";
    private static final String USER_ID = "userId";
    private static final String ORGANISATION_ID = "organisationId";
    private static final String IDPC_DETAILS_ID = "idpcDetailsId";
    private static final String MATERIAL_ID = "materialId";
    private static final String IDPC_DOCUMENT_NAME = "idpcDocumentName";
    private static final String ACCESS_TIMESTAMP = "accessTimestamp";
    public static final String CASE_ID = "caseId";
    public static final String CASE_URN = "caseUrn";
    public static final String PROSECUTION_AUTHORITY = "prosecutionAuthorityCode";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String IDPC_ACCESSING_ORGANISATIONS = "idpcAccessingOrganisations";
    public static final String INSTRUCTING_ORGANISATIONS = "instructingOrganisations";
    public static final String ASSOCIATED_ORGANISATION = "associatedOrganisation";
    public static final String LAST_ASSOCIATED_ORGANISATION = "lastAssociatedOrganisation";
    public static final String ORDER = "order";
    public static final String INSTRUCTION_DATE = "instructionDate";
    public static final String INSTRUCTION_ID = "instructionId";
    public static final String NAME = "name";
    public static final String LOCKED_BY_REP_ORDER = "lockedByRepOrder";
    public static final String CPS_FLAG = "cpsFlag";
    public static final String POLICE_FLAG = "policeFlag";
    public static final String SHORT_NAME = "shortName";
    public static final String UNKNOWN = "unknown";
    public static final String NON_CPS_PROSECUTORS = "Non CPS Prosecutors";
    public static final String ORGANISATION_MIS_MATCH = "OrganisationMisMatch";

    @Inject
    private DefenceQueryService defenceQueryService;

    @Inject
    private DefendantAllocationService pleaService;

    @Inject
    private UsersGroupQueryService usersGroupQueryService;

    @Inject
    private DefenceClientRepository defenceClientRepository;

    @Inject
    private ProgressionService progressionService;

    @Inject
    private ReferenceDataService referenceDataService;

    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String DOB = "dateOfBirth";
    private static final String URN = "urn";
    private static final String IS_CIVIL = "isCivil";
    private static final String IS_GROUP_MEMBER = "isGroupMember";
    private static final String ORGANISATION_NAME = "organisationName";
    public static final String DEFENCE_CLIENT_COUNT= "defenceClientCount";
    private static final String HEARING_DATE = "hearingDate";
    private static final String PROSECUTION_CASES = "prosecutionCases";

    @Handles("defence.query.pleas-and-allocation")
    public JsonEnvelope findPleasAndAllocationByCaseId(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        final String caseId = payload.getString(CASE_ID);

        return pleaService.getPleasByCaseId(caseId);
    }
    @Handles("defence.defence-client-idpc-access-orgs")
    public JsonEnvelope findClientByCriteria(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        final String firstName = payload.getString(FIRST_NAME);
        final String lastName = payload.getString(LAST_NAME);
        final String dateOfBirth = payload.getString(DOB, EMPTY);
        final String hearingDate = payload.getString(HEARING_DATE);
        final Optional<String> caseUrn = getString(payload, URN);
        final Optional<Boolean> isCivil = getBoolean(payload, IS_CIVIL);

        if(!caseUrn.isPresent()){
            final List<UUID> caseList= defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(firstName, lastName, dateOfBirth, isCivil);
            if(caseList.size() == 1){
                return getClientAndIDPCAccessOrganisations(request, firstName, lastName, dateOfBirth, null, isCivil);
            }else if (caseList.size() > 1 && nonNull(hearingDate)) {
                return processByGettingCaseByPersonDefendantAndHearingDate(request, firstName, lastName, dateOfBirth, hearingDate, caseList, isCivil);
            }else{
                return createEmptyPayload(request);
            }
        } else{
            return getClientAndIDPCAccessOrganisations(request, firstName, lastName, dateOfBirth, caseUrn.get(), isCivil);
        }
    }

    private JsonEnvelope processByGettingCaseByPersonDefendantAndHearingDate(final JsonEnvelope request, final String firstName, final String lastName,
                                                           final String dateOfBirth, final String hearingDate, final List<UUID> caseList, final Optional<Boolean> isCivil){


        final List<UUID> defendants = defenceQueryService.getPersonDefendant(firstName, lastName, dateOfBirth, isCivil);
        if (!defendants.isEmpty()) {
            final JsonObject casesByHearingDateResponse = defenceQueryService.getCaseDetailsByPersonDefendantAndHearingDate(request, firstName, lastName, dateOfBirth, hearingDate, isCivil);

            final JsonArray cases = casesByHearingDateResponse.getJsonArray(PROSECUTION_CASES);
            if (cases.size() == 1) {
                final String urn = cases.getJsonObject(0).getString(URN);
                return getClientAndIDPCAccessOrganisations(request, firstName, lastName, dateOfBirth, urn, isCivil);
            } else if (cases.size() > 1) {
                return envelopeFrom(request.metadata(),
                        createObjectBuilder().add(DEFENCE_CLIENT_COUNT, cases.size()).build());
            } else {
                return envelopeFrom(request.metadata(),
                        createObjectBuilder().add(DEFENCE_CLIENT_COUNT, caseList.size()).build());
            }
        } else {
            return createEmptyPayload(request);
        }
    }

    private JsonEnvelope getClientAndIDPCAccessOrganisations(final JsonEnvelope request, final String firstName, final String lastName, final String dateOfBirth, final String caseUrn, final Optional<Boolean> isCivil){
        final DefenceClientIdpcAccessOrganisations clientAndIDPCAccessOrganisations =
                defenceQueryService.getClientAndIDPCAccessOrganisations(firstName, lastName, dateOfBirth, caseUrn, isCivil);
        return getDefenceClient(request, clientAndIDPCAccessOrganisations);
    }

    private JsonEnvelope getDefenceClient(final JsonEnvelope request, final DefenceClientIdpcAccessOrganisations clientAndIDPCAccessOrganisations) {
        if (clientAndIDPCAccessOrganisations != null) {
            final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder();
            jsonObjectBuilder.add(DEFENCE_CLIENT_ID, clientAndIDPCAccessOrganisations.getDefenceClientId().toString());
            jsonObjectBuilder.add(CASE_ID, clientAndIDPCAccessOrganisations.getCaseId().toString());
            jsonObjectBuilder.add(CASE_URN, clientAndIDPCAccessOrganisations.getCaseUrn());
            ofNullable(clientAndIDPCAccessOrganisations.getProsecutionAuthorityCode()).ifPresent(authorityCode -> jsonObjectBuilder.add(PROSECUTION_AUTHORITY, authorityCode));
            jsonObjectBuilder.add(DEFENDANT_ID, clientAndIDPCAccessOrganisations.getDefendantId().toString());
            jsonObjectBuilder.add(IDPC_ACCESSING_ORGANISATIONS,
                    buildOrgList(clientAndIDPCAccessOrganisations.getIdpcAccessingOrganisations()));
            jsonObjectBuilder.add(ASSOCIATED_ORGANISATION,
                    buildAssociatedOrganisation(clientAndIDPCAccessOrganisations.getAssociatedOrganisationVO()));
            jsonObjectBuilder.add(LAST_ASSOCIATED_ORGANISATION,
                    buildAssociatedOrganisation(clientAndIDPCAccessOrganisations.getLastAssociatedOrganisationVO()));
            jsonObjectBuilder.add(INSTRUCTING_ORGANISATIONS,
                    buildInstructionList(clientAndIDPCAccessOrganisations.getInstructionHistory()));
            jsonObjectBuilder.add(LOCKED_BY_REP_ORDER, clientAndIDPCAccessOrganisations.isLockedByRepOrder());

            return JsonEnvelope.envelopeFrom(
                    request.metadata(),
                    jsonObjectBuilder.build());
        }
        return JsonEnvelope.envelopeFrom(
                request.metadata(),
                createObjectBuilder().build());
    }

    @Handles("defence.defence-organisation-client-idpc-access-orgs")
    public JsonEnvelope findOrganisationClientByCriteria(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        final String organisationName = payload.getString(ORGANISATION_NAME);
        final String hearingDate = payload.getString(HEARING_DATE);
        final Optional<String> caseUrn = getString(payload, URN);
        final Optional<Boolean> isCivil = getBoolean(payload, IS_CIVIL);
        if(!caseUrn.isPresent()){
            final List<UUID> caseList = defenceQueryService.getCasesAssociatedWithDefenceClientByOrganisationDefendant(organisationName, isCivil);
            if(caseList.size() == 1){
                return getClientAndIDPCAccessOrganisations(request, organisationName, null, isCivil);
            }else if (caseList.size() > 1 && nonNull(hearingDate)) {
                return processByGettingCaseByOrganisationDefendantAndHearingDate(request, organisationName, hearingDate, caseList, isCivil);
            }else{
                return createEmptyPayload(request);
            }
        } else{
            return getClientAndIDPCAccessOrganisations(request, organisationName, caseUrn.get(), isCivil);
        }
    }

    private JsonEnvelope processByGettingCaseByOrganisationDefendantAndHearingDate(final JsonEnvelope request, final String organisationName,
                                                                                   final String hearingDate, final List<UUID> caseList, final Optional<Boolean> isCivil) {

        final List<UUID> defendants = defenceQueryService.getOrganisationDefendant(organisationName, isCivil);

        if (!defendants.isEmpty()) {
            final JsonObject casesByHearingDateResponse = defenceQueryService.getCaseDetailsByOrganisationDefendantAndHearingDate(request, organisationName, hearingDate, isCivil);

            final JsonArray cases = casesByHearingDateResponse.getJsonArray(PROSECUTION_CASES);
            if (cases.size() == 1) {
                final String urn = cases.getJsonObject(0).getString(URN);
                return getClientAndIDPCAccessOrganisations(request, organisationName, urn, isCivil);
            } else if (cases.size() > 1) {
                return JsonEnvelope.envelopeFrom(request.metadata(),
                        createObjectBuilder().add(DEFENCE_CLIENT_COUNT, cases.size()).build());
            } else {
                return JsonEnvelope.envelopeFrom(request.metadata(),
                        createObjectBuilder().add(DEFENCE_CLIENT_COUNT, caseList.size()).build());
            }
        } else {
            return createEmptyPayload(request);
        }
    }

    private JsonEnvelope getClientAndIDPCAccessOrganisations(final JsonEnvelope request, final String organisationName, final String caseUrn, final  Optional<Boolean> isCivil){
        final DefenceClientIdpcAccessOrganisations clientAndIDPCAccessOrganisations =
                defenceQueryService.getClientAndIDPCAccessOrganisations(organisationName, caseUrn, isCivil);
        return getDefenceClient(request, clientAndIDPCAccessOrganisations);
    }

    private JsonEnvelope createEmptyPayload(final JsonEnvelope request){
       return JsonEnvelope.envelopeFrom(request.metadata(),
                createObjectBuilder().build());
    }

    private boolean isAssociatedOrganisationExist(final AssociatedOrganisationVO associatedOrganisationVO) {
        return associatedOrganisationVO != null && associatedOrganisationVO.getOrganisationId() != null;
    }

    private JsonObject buildAssociatedOrganisation(final AssociatedOrganisationVO associatedOrganisation) {
        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder();
        if (isAssociatedOrganisationExist(associatedOrganisation)) {
            jsonObjectBuilder.add(ORGANISATION_ID, associatedOrganisation.getOrganisationId().toString());
        }
        return jsonObjectBuilder.build();
    }

    private JsonArray buildOrgList(final List<OrderedOrganisationDetailsVO> orgList) {
        final JsonArrayBuilder jsonArrayBuilder = createArrayBuilder();
        for (final OrderedOrganisationDetailsVO organisationDetailsVO : orgList) {
            final JsonObjectBuilder objectBuilder = createObjectBuilder();
            objectBuilder.add(ORDER, organisationDetailsVO.getOrder());
            objectBuilder.add(ORGANISATION_ID, organisationDetailsVO.getOrganisationId().toString());
            if (organisationDetailsVO.getName() != null) {
                objectBuilder.add(NAME, organisationDetailsVO.getName());
            }
            jsonArrayBuilder.add(objectBuilder);
        }
        return jsonArrayBuilder.build();
    }

    private JsonArray buildInstructionList(final List<DefenceClientInstructionHistoryVO> instructionHistories) {
        final JsonArrayBuilder jsonArrayBuilder = createArrayBuilder();
        for (final DefenceClientInstructionHistoryVO instructionHistory : instructionHistories) {
            final JsonObjectBuilder objectBuilder = createObjectBuilder();
            objectBuilder.add(USER_ID, instructionHistory.getUserId().toString());
            objectBuilder.add(ORGANISATION_ID, instructionHistory.getOrganisationId().toString());
            objectBuilder.add(INSTRUCTION_ID, instructionHistory.getId().toString());
            objectBuilder.add(INSTRUCTION_DATE, instructionHistory.getInstructionDate().toString());
            jsonArrayBuilder.add(objectBuilder);
        }
        return jsonArrayBuilder.build();
    }

    @Handles("defence.defence-client-allegations")
    public Envelope<Allegations> findAllegationsByDefenceClientId(final Envelope<DefenceClientId> query) {
        final UUID userId = fromString(query.metadata().userId().orElse(null));
        final UUID defenceClientId = query.payload().getDefenceClientId();
        final boolean instructionDeclared = defenceQueryService.hasUserDeclaredAnInstructionForDefenceClient(userId, defenceClientId);
        if (!instructionDeclared) {
            final UUID organisationId = fromString(usersGroupQueryService.getOrganisationForUser(userId, query.metadata()));
            final boolean declarationExists = defenceQueryService.hasSomeUserDeclaredAnInstructionForDefenceClientForOrganisation(defenceClientId, organisationId);
            final boolean isAssociated = isAlreadyAssociated(defenceClientId);
            if (!isAssociated && !declarationExists) {
                throw new ForbiddenRequestException("User has no instructions associated for the organisation");
            }
        }
        return defenceQueryService.findAllegationsByDefenceClientId(query);
    }

    private boolean isAlreadyAssociated(UUID defenceClientId) {
        final DefenceClient defenceClient = defenceClientRepository.findBy(defenceClientId);
        if (defenceClient != null) {
            return null != defenceClient.getAssociatedOrganisation();
        }
        return false;
    }

    @Handles("defence.defence-client-idpc-metadata")
    public Envelope<DefenceClientIdpcMetadata> findIdpcMetdataByDefenceClientId(final Envelope<DefenceClientId> query) {
        return defenceQueryService.findIdpcMetadataForDefenceClient(query);
    }


    @Handles("defence.query.get-case-by-person-defendant")
    public JsonEnvelope getCasesByPersonDefendant(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        final String firstName = payload.getString(FIRST_NAME);
        final String lastName = payload.getString(LAST_NAME);
        final String dateOfBirth = payload.getString(DOB, EMPTY);
        final Optional<Boolean> isCivil = getBoolean(payload, IS_CIVIL);
        final Optional<Boolean> isGroupMember = getBoolean(payload, IS_GROUP_MEMBER);

        final List<UUID> caseList= defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(firstName, lastName, dateOfBirth, isCivil, isGroupMember);
        final List<UUID> defendants = defenceQueryService.getPersonDefendant(firstName, lastName, dateOfBirth, isCivil, isGroupMember);
        return createCaseByDefendantResponse(caseList, defendants, request);
    }

    @Handles("defence.query.get-case-by-organisation-defendant")
    public JsonEnvelope getCasesByOrganisationDefendant(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        final String organisationName = payload.getString(ORGANISATION_NAME);
        final Optional<Boolean> isCivil = getBoolean(payload, IS_CIVIL);
        final Optional<Boolean> isGroupMember = getBoolean(payload, IS_GROUP_MEMBER);

        final List<UUID> caseList = defenceQueryService.getCasesAssociatedWithDefenceClientByOrganisationDefendant(organisationName, isCivil, isGroupMember);
        final List<UUID> defendants = defenceQueryService.getOrganisationDefendant(organisationName, isCivil, isGroupMember);
        return createCaseByDefendantResponse(caseList, defendants, request);
    }

    private JsonEnvelope createCaseByDefendantResponse(final List<UUID> caseList, final List<UUID> defendants, final JsonEnvelope request){
        final JsonArrayBuilder caseArrayBuilder = createArrayBuilder();
        caseList.forEach(caseId -> caseArrayBuilder.add(caseId.toString()));

        final JsonArrayBuilder defendantsArrayBuilder = createArrayBuilder();
        defendants.forEach(defendantId -> defendantsArrayBuilder.add(defendantId.toString()));

        return envelopeFrom(request.metadata(),
                createObjectBuilder()
                        .add("caseIds", caseArrayBuilder.build())
                        .add("defendants", defendantsArrayBuilder.build())
                        .build());
    }

    @SuppressWarnings("squid:S3655")
    @Handles("defence.case-for-prosecutor-assignment")
    public Envelope<CaseForProsecutorAssignment> findCaseForProsecutorAssignment(final Envelope<SearchCaseByUrn> query) {
        final String caseUrn = query.payload().getCaseUrn()!=null &&
                !query.payload().getCaseUrn().isEmpty()?query.payload().getCaseUrn().toUpperCase():query.payload().getCaseUrn();
        final UUID caseId = defenceQueryService.getCaseId(caseUrn);
        if (isNull(caseId)) {
            return prepareErrorResponseForProsecutorAssignment(query, CASE_NOT_FOUND, null);
        }
        final UUID userId = query.metadata().userId().isPresent() ? fromString(query.metadata().userId().get()) : null;
        final UUID prosecutorId = progressionService.getProsecutorOrProsecutionCaseAuthorityID(query.metadata(), caseId);
        if (nonNull(prosecutorId)) {
            final Optional<JsonObject> prosecutorJsonObjectOptional = referenceDataService.getProsecutor(query.metadata(), prosecutorId);
            if (prosecutorJsonObjectOptional.isPresent()) {
                final JsonObject prosecutorJsonObject = prosecutorJsonObjectOptional.get();
                final Optional<String> isNonCpsUser = usersGroupQueryService.validateNonCPSUserOrg(query.metadata(), userId, NON_CPS_PROSECUTORS, prosecutorJsonObject.getString(SHORT_NAME));
                if (getCaseForProsecutorAssignmentForCpsOrNonCps(prosecutorJsonObject, isNonCpsUser)) {
                    return prepareErrorResponseForProsecutorAssignment(query, ORGANISATION_NOT_PROSECUTING_AUTHORITY, getShortName(prosecutorJsonObject));
                }
                return prepareSuccessResponseForProsecutorAssignment(query, caseId);
            }
            return prepareErrorResponseForProsecutorAssignment(query, ORGANISATION_NOT_PROSECUTING_AUTHORITY, UNKNOWN);
        }
        return prepareErrorResponseForProsecutorAssignment(query, CASE_NOT_FOUND, null);
    }

    private boolean getCaseForProsecutorAssignmentForCpsOrNonCps(final JsonObject prosecutorJsonObject, final Optional<String> isNonCpsUser) {
        if(isNonCpsUser.isPresent()) {
            if(ORGANISATION_MIS_MATCH.equals(isNonCpsUser.get())) {
                return true;
            }
        } else {
            final boolean cpsFlag = getCpsFlag(prosecutorJsonObject);
            final boolean policeFlag = getPoliceFlag(prosecutorJsonObject);
            if (!cpsFlag && !policeFlag) {
                return true;
            }
        }
        return false;
    }

    private String getShortName(final JsonObject prosecutorJsonObject) {
        return prosecutorJsonObject.getString(SHORT_NAME);
    }

    private boolean getCpsFlag(final JsonObject prosecutorJsonObject) {
        if (prosecutorJsonObject.containsKey(CPS_FLAG)) {
            return prosecutorJsonObject.getBoolean(CPS_FLAG);
        }
        return false;
    }

    private boolean getPoliceFlag(final JsonObject prosecutorJsonObject) {
        if (prosecutorJsonObject.containsKey(POLICE_FLAG)) {
            return prosecutorJsonObject.getBoolean(POLICE_FLAG);
        }
        return false;
    }

    private Envelope<CaseForProsecutorAssignment> prepareErrorResponseForProsecutorAssignment(final Envelope<SearchCaseByUrn> query, final ErrorCode errorCode, final String organisationName) {
        return envelopeFrom(query.metadata(), CaseForProsecutorAssignment.caseForProsecutorAssignment().withError(Error.error()
                .withErrorCode(errorCode)
                .withOrganisationName(organisationName)
                .build()).build());
    }

    private Envelope<CaseForProsecutorAssignment> prepareSuccessResponseForProsecutorAssignment(final Envelope<SearchCaseByUrn> query, final UUID caseId) {
        return envelopeFrom(query.metadata(), CaseForProsecutorAssignment.caseForProsecutorAssignment()
                .withCaseId(caseId)
                .withCaseURN(query.payload().getCaseUrn())
                .build());
    }

    @Handles("defence.query.defendant-idpc-metadata")
    public Envelope<DefenceClientIdpcMetadata> findIdpcMetdataByDefendant(final Envelope<Defendant> query) {
        return defenceQueryService.findIdpcMetadataForDefendant(query);
    }


    @Handles("defence.query.view.defence-client-idpc")
    public JsonEnvelope getDefenceClientIdpc(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        final String defenceClientId = payload.getString(DEFENCE_CLIENT_ID);
        final String userId = payload.getString(USER_ID);
        final String organisationId = payload.getString(ORGANISATION_ID);

        //check if instruction has been provided
        final boolean intructionDeclared = defenceQueryService.hasSomeUserDeclaredAnInstructionForDefenceClientForOrganisation(fromString(defenceClientId), fromString(organisationId));
        final boolean isAssociated = isAlreadyAssociated(fromString(defenceClientId));
        if (!isAssociated && !intructionDeclared) {
            throw new ForbiddenRequestException("User has no instructions associated for the organisation");
        }

        //instruction has been provided
        final IdpcDetails idpcDetails = defenceQueryService.getIDPCDetailsForDefenceClient(fromString(defenceClientId));
        if (idpcDetails == null) {
            throw new InternalServerErrorException();
        }

        final ZonedDateTime now = ZonedDateTime.now(UTC_ZONE_ID);

        return JsonEnvelope.envelopeFrom(
                request.metadata(),
                createObjectBuilder()
                        .add(DEFENCE_CLIENT_ID, defenceClientId)
                        .add(USER_ID, userId)
                        .add(ORGANISATION_ID, organisationId)
                        .add(IDPC_DETAILS_ID, idpcDetails.getId().toString())
                        .add(MATERIAL_ID, idpcDetails.getMaterialId().toString())
                        .add(IDPC_DOCUMENT_NAME, defenceQueryService.getIdpcFileName(fromString(defenceClientId)))
                        .add(ACCESS_TIMESTAMP, ZonedDateTimes.toString(now))
                        .build()
        );
    }


    @Handles("defence.query.defence-client-defendantId")
    public Envelope<DefenceClient> getDefenceClientByDefendantId(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        final String defendantId = payload.getString(DEFENDANT_ID);
        final DefenceClient defenceClient = defenceClientRepository.findDefenceClientByCriteria(UUID.fromString(defendantId));
        return envelopeFrom(request.metadata(), defenceClient);
    }

    @Handles("defence.query.case-defendants-organisation")
    public Envelope<CaseDefendantsOrganisations> findDefendantsWithOrganisationsByCaseId(final JsonEnvelope query) {
        return defenceQueryService.getCaseDefendantsWithOrganisations(query);
    }
}
