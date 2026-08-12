package uk.gov.moj.cpp.defence.query.api;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.getLong;
import static uk.gov.justice.services.messaging.JsonObjects.getString;
import static uk.gov.moj.cpp.defence.common.util.DateValidator.validateDateString;
import static uk.gov.moj.cpp.defence.common.util.DefencePermission.VIEW_DEFENDANT_PERMISSION;
import static uk.gov.moj.cpp.defence.service.PermissionService.getPermissions;
import static uk.gov.moj.cpp.defence.service.PermissionService.getUserPermissions;

import uk.gov.justice.cps.defence.Allegations;
import uk.gov.justice.cps.defence.Association;
import uk.gov.justice.cps.defence.CaseDefendantsOrganisations;
import uk.gov.justice.cps.defence.CaseForProsecutorAssignment;
import uk.gov.justice.cps.defence.DefenceClientId;
import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.cps.defence.SearchAllegationsByClientId;
import uk.gov.justice.cps.defence.SearchCaseByUrn;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.FeatureControl;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.common.Defendant;
import uk.gov.moj.cpp.defence.common.util.GenericEnveloper;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.query.api.service.CalendarService;
import uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper;
import uk.gov.moj.cpp.defence.query.api.service.OrganisationNameVO;
import uk.gov.moj.cpp.defence.query.api.service.OrganisationQueryService;
import uk.gov.moj.cpp.defence.query.api.service.ProgressionQueryService;
import uk.gov.moj.cpp.defence.query.api.service.UsersAndGroupsService;
import uk.gov.moj.cpp.defence.query.view.DefenceClientIdpcMetadata;
import uk.gov.moj.cpp.defence.query.view.DefenceQueryView;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(Component.QUERY_API)
public class DefenceQueryApi {

    public static final String ORGANISATION_ID = "organisationId";
    public static final String ASSOCIATED_ORGANISATION = "associatedOrganisation";
    public static final String LAST_ASSOCIATED_ORGANISATION = "lastAssociatedOrganisation";
    public static final String ORGANISATION_NAME = "organisationName";
    public static final String CASE_ID = "caseId";
    public static final String PROSECUTION_AUTHORITY_CODE = "prosecutionAuthorityCode";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String IDPC_ACCESSING_ORGANISATIONS = "idpcAccessingOrganisations";
    public static final String ORDER = "order";
    public static final String NAME = "name";
    public static final String DEFENCE_DEFENCE_CLIENT_IDPC_ACCESS_ORGS = "defence.defence-client-idpc-access-orgs";
    public static final String DEFENCE_DEFENCE_ORGANISATION_CLIENT_IDPC_ACCESS_ORGS = "defence.defence-organisation-client-idpc-access-orgs";
    public static final String INSTRUCTION_DATE = "instructionDate";
    public static final String INSTRUCTION_ID = "instructionId";
    public static final String INSTRUCTING_ORGANISATIONS = "instructingOrganisations";
    public static final String ORG_ALREADY_ASSOCIATED_ERROR = "%s already associated";
    public static final String LOCKED_BY_REP_ORDER = "lockedByRepOrder";
    public static final String ASSOCIATION = "association";
    public static final String DEFENCE_QUERY_ASSOCIATED_ORGANISATION = "defence.query.associated-organisation";
    public static final String USER_DOES_NOT_HAVE_ACCESS_TO_CASE = "User does not have access to case";
    private static final String DEFENCE_CLIENT_ID = "defenceClientId";
    private static final String USER_ID = "userId";
    private static final String DOB = "dateOfBirth";
    private static final String URN = "urn";
    private static final String PROSECUTION_CASE = "prosecutionCase";
    private static final Logger LOGGER = LoggerFactory.getLogger(DefenceQueryApi.class);
    private static final String PROSECUTOR = "prosecutor";
    public static final String ASSOCIATED_PERSONS = "associatedPersons";
    public static final String WITH_ADDRESS = "withAddress";
    private static final String HEARING_DATE = "hearingDate";
    private static final String DEFENCE_CLIENT_COUNT = "defenceClientCount";
    private static final String CASE_URN = "caseUrn";
    private static final String MODE_OF_TRIAL = "modeOfTrial";
    private static final String EITHER_WAY = "Either Way";
    private static final String INDICTABLE = "Indictable";
    private static final String FIRST_HEARING = "First hearing";
    private static final String HEARING_ID = "hearingId";
    private static final String HEARING_TYPES = "hearingTypes";
    private static final String TYPE = "type";
    private static final String DEFENDANTS = "defendants";
    private static final int EIGHTEEN = 18;
    private static final String OFFENCES = "offences";
    private static final String HEARING = "hearing";
    private static final String HEARING_DAYS = "hearingDays";
    private static final String SITTING_DAY = "sittingDay";
    public static final String IS_CIVIL = "isCivil";

    @Inject
    private Requester requester;

    @Inject
    private Sender sender;

    @Inject
    private OrganisationQueryService organisationQueryService;

    @Inject
    private GenericEnveloper genericEnveloper;

    @Inject
    private DefenceAssociationQueryApi defenceAssociationQueryApi;

    @Inject
    private ProgressionQueryService progressionQueryService;

    @Inject
    private UsersAndGroupsService usersAndGroupsService;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    private DefenceQueryView defenceQueryView;

    @Inject
    private CalendarService calendarService;


    @Handles("defence.query.defence-client-allegations")
    public Envelope<Allegations> findAllegationsByDefenceClientId(final Envelope<SearchAllegationsByClientId> query) {
        final Metadata metadata = query.metadata();

        final DefenceClientId defenceClientId = DefenceClientId.defenceClientId().withDefenceClientId(query.payload().getDefenceClientId()).build();
        final Envelope<DefenceClientId> defenceClientIdEnvelope = Envelope.envelopeFrom(metadataFrom(metadata).withName("defence.defence-client-allegations"), defenceClientId);
        checkIfUserAuthorisedToViewClientInfo(query.payload().getDefenceClientId().toString(), metadata);
        return defenceQueryView.findAllegationsByDefenceClientId(defenceClientIdEnvelope);
    }

    @Handles("defence.query.defence-client-idpc-metadata")
    public Envelope<uk.gov.moj.cpp.defence.query.view.DefenceClientIdpcMetadata> findIdpcMetdataByDefenceClientId(final Envelope<DefenceClientId> envelope) {
        final Metadata metadata = envelope.metadata();
        final Envelope<DefenceClientId> envelopeWithNewMetadata = genericEnveloper.envelopeWithNewActionName(envelope.payload(), metadata, "defence.defence-client-idpc-metadata");
        return defenceQueryView.findIdpcMetdataByDefenceClientId(envelopeWithNewMetadata);
    }

    @Handles("defence.query.defence-client-id")
    public JsonEnvelope findClientByCriteria(final JsonEnvelope request) {
        validateInputParams(request);
        final JsonEnvelope response = processRequestForIndividual(request);
        final boolean isCivil = request.payloadAsJsonObject().getBoolean(IS_CIVIL, false);
        return processQueryResultConsolidation(response, isCivil);

    }

    @Handles("defence.query.get-case-by-person-defendant")
    public JsonEnvelope getCasesByPersonDefendant(final JsonEnvelope request) {
        validateInputParams(request);
        return defenceQueryView.getCasesByPersonDefendant(request);
    }

    @Handles("defence.query.get-case-by-organisation-defendant")
    public JsonEnvelope getCasesByOrganisationDefendant(final JsonEnvelope request) {
        return defenceQueryView.getCasesByOrganisationDefendant(request);
    }

    @Handles("defence.query.case-for-prosecutor-assignment")
    public Envelope<CaseForProsecutorAssignment> findCaseForProsecutorAssignment(final Envelope<SearchCaseByUrn> request) {
        final Metadata metadata = request.metadata();
        final Envelope<SearchCaseByUrn> envelopeWithNewMetadata = genericEnveloper.envelopeWithNewActionName(request.payload(),
                metadata, "defence.case-for-prosecutor-assignment");
        return defenceQueryView.findCaseForProsecutorAssignment(envelopeWithNewMetadata);
    }

    @Handles("defence.query.defence-client-organisation-id")
    public JsonEnvelope findOrganisationClientByCriteria(final JsonEnvelope request) {

        final JsonEnvelope response = processRequestForOrganisation(request);
        final boolean isCivil = request.payloadAsJsonObject().getBoolean(IS_CIVIL, false);
        return processQueryResultConsolidation(response, isCivil);

    }

    @Handles("defence.query.defence-client-defendantId")
    public Envelope<DefenceClient> findDefenceClientByDefendantId(final JsonEnvelope request) {
        return defenceQueryView.getDefenceClientByDefendantId(request);
    }

    @Handles("defence.query.case-defendants-organisation")
    public JsonEnvelope findDefendantsByCaseId(final JsonEnvelope request) {
        final Envelope<CaseDefendantsOrganisations> caseDefendantsOrganisationsEnvelope = defenceQueryView.findDefendantsWithOrganisationsByCaseId(request);
        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                caseDefendantsOrganisationsEnvelope.metadata(), objectToJsonObjectConverter.convert(caseDefendantsOrganisationsEnvelope.payload()));

        final boolean withAddress = ofNullable(request.payloadAsJsonObject()
                .get(WITH_ADDRESS))
                .map(JsonValue::toString)
                .map(Boolean::valueOf)
                .orElse(false);

        if (!withAddress) {
            return jsonEnvelope;
        }


        final JsonObject caseDefendantOrganisation = jsonEnvelope.payloadAsJsonObject().getJsonObject(CaseDefendantOrganisationHelper.CASE_DEFENDANT_ORGANISATION);
        final JsonArray caseDefendantJsonArray = caseDefendantOrganisation.getJsonArray(CaseDefendantOrganisationHelper.DEFENDANTS);
        final JsonArrayBuilder defendantsJsonArray = createArrayBuilder();
        final Map<String, JsonObject> organisationDetailMap = new HashMap<>();

        for (JsonValue defendant : caseDefendantJsonArray) {
            final JsonObject enrichedDefendant = enrichDefendant((JsonObject) defendant, jsonEnvelope, organisationDetailMap);
            defendantsJsonArray.add(enrichedDefendant);
        }

        return CaseDefendantOrganisationHelper.getJsonEnvelope(jsonEnvelope.metadata(), caseDefendantOrganisation, defendantsJsonArray);
    }

    private JsonObject enrichDefendant(JsonObject defendant, JsonEnvelope envelope, Map<String, JsonObject> cache) {
        if (!defendant.containsKey(ASSOCIATED_ORGANISATION)) {
            return CaseDefendantOrganisationHelper.toDefendantOrganisationWithNoAddressJson(defendant);
        }

        final String organisationId = defendant.getString(ASSOCIATED_ORGANISATION);
        JsonObject organisationDetails = cache.get(organisationId);

        if (isNull(organisationDetails)) {
            organisationDetails = usersAndGroupsService.getOrganisationDetails(
                    CaseDefendantOrganisationHelper.getUsersAndGroupsRequestEnvelope(envelope, defendant)
            );
            if (nonNull(organisationDetails)) {
                cache.put(organisationId, organisationDetails);
            }
        }

        return nonNull(organisationDetails)
                ? CaseDefendantOrganisationHelper.toDefendantOrganisationWithAddressJson(defendant, organisationDetails)
                : CaseDefendantOrganisationHelper.toDefendantOrganisationWithNoAddressJson(defendant);
    }

    private JsonEnvelope processQueryResultConsolidation(final JsonEnvelope response, final boolean isCivil) {

        final JsonObject responsePayload = response.payloadAsJsonObject();
        //If no response received, return null
        if (responsePayload == null || responsePayload.size() == 0) {
            return JsonEnvelope.envelopeFrom(response.metadata(), null);
        }

        final Optional<Long> defenceClientCount = getLong(responsePayload, DEFENCE_CLIENT_COUNT);
        if (defenceClientCount.isPresent()) {
            final JsonObject responseWithDefenceClientCount = createObjectBuilder()
                    .add(DEFENCE_CLIENT_COUNT, defenceClientCount.get())
                    .add(IS_CIVIL, isCivil)
                    .build();
            return JsonEnvelope.envelopeFrom(response.metadata(), responseWithDefenceClientCount);
        }

        final String defendantId = responsePayload.getString(DEFENCE_CLIENT_ID);
        final JsonObject associatedOrganisationJsonObject = getAssociatedOrganisation(response);


        checkIfUserAuthorisedToViewClientInfo(defendantId, response.metadata());

        //populate last associated organisation details if present
        final JsonObject lastAssociatedOrganisation = getLastAssociatedOrganisation(response);
        final String actualDefendantId = responsePayload.getString(DEFENDANT_ID);
        final UUID caseId = fromString(response.payloadAsJsonObject().getString(CASE_ID));
        final Optional<JsonArray> defendants = getDefendants(response, caseId);
        final Optional<JsonArray> associatedPersons = getAssociatedPersons(actualDefendantId, defendants);

        LOGGER.info("defendants  {} ", defendants);

        final String caseUrn = responsePayload.getString(CASE_URN);

        //populate idpc access organisation details if present and return final response
        return createResponseWithIdpcAccessOrganisation(response, associatedOrganisationJsonObject, lastAssociatedOrganisation, associatedPersons, caseUrn, isCivil);
    }

    @Handles("defence.query.defendant-idpc-metadata")
    public Envelope<DefenceClientIdpcMetadata> findIdpcMetdataByDefendant(final Envelope<Defendant> query) {
        return defenceQueryView.findIdpcMetdataByDefendant(query);
    }

    /*
     * Check if defence client is associated with organisation
     *   if yes,
     *      then check
     *              Logged in user belongs to associated organisation
     *          then check
     *                  Logged in user has view permission for defendant
     *
     *   if no( i.e defence client not associted with org),
     *     then check
     *          if association locked by rep order,
     *              if yes,
     *                 then throw ForbiddenRequestException
     *              else,
     *                check if user has been granted ABAC permission
     */
    private void checkIfUserAuthorisedToViewClientInfo(String defendantId, Metadata metadata) {

        final Association associatedOrganisation = getAssociatedOrganisationIdForDefendant(defendantId, metadata);
        final UUID associatedOrganisationId = associatedOrganisation != null ? associatedOrganisation.getOrganisationId() : null;
        final boolean isDefendantAssociatedWIthOrganisation = nonNull(associatedOrganisationId);

        // check if given defence client has associated organisation
        if (isDefendantAssociatedWIthOrganisation) {
            // check if logged in user belongs to associated organisation
            if (!associatedOrganisationId.toString().equals(organisationQueryService.getOrganisationOfLoggedInUser(metadata))) {

                // if logged in user not associated with defence client, check for grant permissions
                final String userId = metadata.userId().orElse(null);
                final List<Permission> permissions = getPermissions(metadata, requester, defendantId);
                final Optional<Permission> defenceClientPermission = permissions.stream().filter(p -> p.getTarget().toString().equals(defendantId)).filter(p -> p.getSource().toString().equals(userId)).filter(p -> VIEW_DEFENDANT_PERMISSION.getObjectType().equals(p.getObject())).filter(p -> VIEW_DEFENDANT_PERMISSION.getActionType().equals(p.getAction())).findAny();

                if (!defenceClientPermission.isPresent()) {
                    final String organisationName = associatedOrganisation.getOrganisationName();
                    throw new ForbiddenRequestException(format(ORG_ALREADY_ASSOCIATED_ERROR, organisationName));
                }
            }
        } else { // no ogranisation is associated

            // check if defence association locked by rep order
            if (isAssociationLockedByRepOrder(defendantId, metadata)) {
                throw new ForbiddenRequestException(USER_DOES_NOT_HAVE_ACCESS_TO_CASE);
            } else {
                final List<Permission> permissions = getUserPermissions(metadata, requester);
                final Optional<Permission> defenceClientPermission = permissions.stream().filter(p -> p.getTarget() == null).filter(p -> p.getSource() == null).filter(p -> VIEW_DEFENDANT_PERMISSION.getObjectType().equals(p.getObject())).filter(p -> VIEW_DEFENDANT_PERMISSION.getActionType().equals(p.getAction())).findAny();

                if (!defenceClientPermission.isPresent()) {
                    throw new ForbiddenRequestException(USER_DOES_NOT_HAVE_ACCESS_TO_CASE);
                }
            }

        }

    }

    @Handles("defence.query.defence-client-idpc")
    public JsonEnvelope getDefenceClientIdpc(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        final String defenceClientId = payload.getString(DEFENCE_CLIENT_ID);
        final String userId = payload.getString(USER_ID);
        //find out what is the user's organisation
        final String organisationId = getUsersOrganisationId(userId, request);
        if (organisationId == null) {
            //if undefined then stop processing and return
            throw new ForbiddenRequestException("Unable to determine organisation");
        }
        return defenceQueryView.getDefenceClientIdpc(JsonEnvelope.envelopeFrom(
                metadataFrom(request.metadata()).withName("defence.query.view.defence-client-idpc").build(), createObjectBuilder().add(DEFENCE_CLIENT_ID, defenceClientId).add(USER_ID, userId).add(ORGANISATION_ID, organisationId).build()));
    }

    @Handles("defence.query.record-idpc-access")
    public void issueRecordIdpcAccess(final JsonEnvelope request) {
        sender.send(envelopeFrom(metadataFrom(request.metadata()).withName("defence.command.record-access-to-idpc").build(), request.payloadAsJsonObject()));
    }

    @Handles("defence.query.pleas-and-allocation")
    public JsonEnvelope findPleasAndAllocationByCaseId(final JsonEnvelope request) {
        return defenceQueryView.findPleasAndAllocationByCaseId(request);
    }

    @Handles("defence.query.eligible-for-online-plea")
    @FeatureControl("OPA")
    public JsonEnvelope getEligibleDefendants(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        final String caseId = payload.getString(CASE_ID);

        final Optional<JsonObject> prosecutionCaseOptional = progressionQueryService.getProsecutionCaseDetailById(envelope, caseId);
        if (!prosecutionCaseOptional.isPresent()) {
            LOGGER.info("Prosecution case with id '{}' is not present.", caseId);
            return JsonEnvelope.envelopeFrom(envelope.metadata(), createObjectBuilder().add(DEFENDANTS, createArrayBuilder().build()).build());
        }

        final Optional<JsonObject> hearingsForProsecutionCaseOptional = progressionQueryService.getHearingsForProsecutionCase(envelope, caseId);
        if (!hearingsForProsecutionCaseOptional.isPresent()) {
            LOGGER.info("No hearings available for prosecution case with id '{}'", caseId);
            return JsonEnvelope.envelopeFrom(envelope.metadata(), createObjectBuilder().add(DEFENDANTS, createArrayBuilder().build()).build());
        }

        final UUID firstHearingId = getFirstHearingId(hearingsForProsecutionCaseOptional.get());
        if (isNull(firstHearingId)) {
            LOGGER.info("No first hearing available for prosecution case with id '{}'", caseId);
            return JsonEnvelope.envelopeFrom(envelope.metadata(), createObjectBuilder().add(DEFENDANTS, createArrayBuilder().build()).build());
        }

        final Optional<JsonObject> firstHearingOptional = progressionQueryService.getHearing(envelope, firstHearingId.toString());
        if (!firstHearingOptional.isPresent()) {
            LOGGER.info("Hearing with id '{}' is not present.", firstHearingId);
            return JsonEnvelope.envelopeFrom(envelope.metadata(), createObjectBuilder().add(DEFENDANTS, createArrayBuilder().build()).build());
        }

        final ZonedDateTime earliestHearingDay = firstHearingOptional.get().getJsonObject(HEARING).getJsonArray(HEARING_DAYS).stream()
                .map(jsonValue -> ZonedDateTimes.fromString(((JsonObject) jsonValue).getString(SITTING_DAY)))
                .sorted()
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
        final long noOfDaysBetweenTodayAndEarliestHearingDay = calendarService.daysBetweenExcludeHolidays(LocalDate.now(), earliestHearingDay.toLocalDate(), requester);
        final JsonArrayBuilder defendantsBuilder = createArrayBuilder();

        prosecutionCaseOptional.get().getJsonObject(PROSECUTION_CASE).getJsonArray(DEFENDANTS).stream()
                .map(JsonObject.class::cast)
                .forEach(defendant -> defendant.getJsonArray(OFFENCES).stream()
                        .map(JsonObject.class::cast).toList()
                        .forEach(offence -> {
                            if (EITHER_WAY.equalsIgnoreCase(offence.getString(MODE_OF_TRIAL)) || INDICTABLE.equalsIgnoreCase(offence.getString(MODE_OF_TRIAL))) {
                                defendantsBuilder.add(defendant.getString("id"));
                            }
                        })
                );

        final JsonArray defendantIds = defendantsBuilder.build();
        if (earliestHearingDay.isAfter(ZonedDateTime.now()) && noOfDaysBetweenTodayAndEarliestHearingDay >= EIGHTEEN && !defendantIds.isEmpty()) {
            return JsonEnvelope.envelopeFrom(envelope.metadata(), createObjectBuilder()
                    .add(DEFENDANTS, defendantIds)
                    .build());
        }
        return JsonEnvelope.envelopeFrom(envelope.metadata(), createObjectBuilder().add(DEFENDANTS, createArrayBuilder().build()).build());

    }

    private UUID getFirstHearingId(final JsonObject hearingsForProsecutionCase) {
        final JsonArray hearingTypes = hearingsForProsecutionCase.getJsonArray(HEARING_TYPES);
        if (isNotEmpty(hearingTypes)) {
            return hearingTypes.stream()
                    .filter(jsonValue -> ((JsonObject) jsonValue).getString(TYPE).equals(FIRST_HEARING))
                    .map(jsonValue -> fromString(((JsonObject) jsonValue).getString(HEARING_ID)))
                    .findFirst().orElse(null);
        }
        return null;
    }

    private String getUsersOrganisationId(final String userId, final JsonEnvelope envelope) {

        final Metadata metadataBuilder = metadataFrom(envelope.metadata()).withName("usersgroups.get-organisation-name-for-user").build();

        final JsonEnvelope getOrganisationName = JsonEnvelope.envelopeFrom(metadataBuilder, createObjectBuilder().add(USER_ID, userId).build());

        final JsonObject organisationObject = requester.requestAsAdmin(getOrganisationName, JsonObject.class).payload();
        return organisationObject.getString(ORGANISATION_ID, null);
    }

    private JsonEnvelope processRequestForIndividual(final JsonEnvelope request) {
        return defenceQueryView.findClientByCriteria(request);
    }

    private JsonEnvelope processRequestForOrganisation(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        return defenceQueryView.findOrganisationClientByCriteria(JsonEnvelope.envelopeFrom(
                metadataFrom(request.metadata()).withName(DEFENCE_DEFENCE_ORGANISATION_CLIENT_IDPC_ACCESS_ORGS).build(), prepareDefendantAsOrganisation(payload)));
    }

    private JsonObject prepareDefendantAsOrganisation(JsonObject payload) {
        final String organizationName = payload.getString(ORGANISATION_NAME);
        final Optional<String> urn = getString(payload, URN);
        final String hearingDate = payload.getString(HEARING_DATE);
        final boolean isCivil = payload.getBoolean(IS_CIVIL, false);
        final JsonObjectBuilder builder = createObjectBuilder()
                .add(ORGANISATION_NAME, organizationName)
                .add(HEARING_DATE, hearingDate)
                .add(IS_CIVIL, isCivil);
        if (urn.isPresent()) {
            builder.add(URN, urn.get());
        }
        return builder.build();
    }

    private JsonObject getAssociatedOrganisation(final JsonEnvelope response) {
        final JsonObject responsePayload = response.payloadAsJsonObject();
        final JsonObject associatedOrganisation = responsePayload.getJsonObject(ASSOCIATED_ORGANISATION);
        final JsonObjectBuilder associatedOrganisationJsonObjectBuilder = createObjectBuilder();

        if (associatedOrganisationExists(associatedOrganisation)) {
            final String associatedOrganisationId = associatedOrganisation.getString(ORGANISATION_ID);
            associatedOrganisationJsonObjectBuilder.add(ORGANISATION_ID, associatedOrganisationId);
            associatedOrganisationJsonObjectBuilder.add(ORGANISATION_NAME, getAssociatedOrganisationName(response.metadata(), associatedOrganisationId));
        }
        return associatedOrganisationJsonObjectBuilder.build();
    }

    private String getAssociatedOrganisationName(final Metadata metadata, String associatedOrganisationId) {
        final List<String> ids = new ArrayList<>();
        ids.add(associatedOrganisationId);
        final List<OrganisationNameVO> organisationNamesForOrganisationIds = getOrganisationNamesForOrganisationIds(metadata, ids);
        return !organisationNamesForOrganisationIds.isEmpty() ? organisationNamesForOrganisationIds.get(0).getOrganisationName() : "";
    }

    private boolean associatedOrganisationExists(JsonObject associatedOrganisation) {
        return (associatedOrganisation != null && associatedOrganisation.size() > 0 && associatedOrganisation.getString(ORGANISATION_ID) != null);
    }

    private JsonObject getLastAssociatedOrganisation(final JsonEnvelope response) {
        final JsonObject responsePayload = response.payloadAsJsonObject();
        final JsonObjectBuilder associatedOrganisationJsonObjectBuilder = createObjectBuilder();
        final JsonObject lastAssociatedOrganisation = responsePayload.getJsonObject(LAST_ASSOCIATED_ORGANISATION);
        if (associatedOrganisationExists(lastAssociatedOrganisation)) {
            final String lastAssociatedOrganisationId = lastAssociatedOrganisation.getString(ORGANISATION_ID);
            final List<OrganisationNameVO> organisationNamesForIds = organisationQueryService.getOrganisationNamesForIds(Collections.singletonList(lastAssociatedOrganisationId), response.metadata());
            final String lastOrganisationName = organisationNamesForIds.isEmpty() ? "" : organisationNamesForIds.get(0).getOrganisationName();
            associatedOrganisationJsonObjectBuilder.add(ORGANISATION_ID, lastAssociatedOrganisationId);
            associatedOrganisationJsonObjectBuilder.add(ORGANISATION_NAME, lastOrganisationName);
        }
        return associatedOrganisationJsonObjectBuilder.build();
    }

    private JsonEnvelope createResponseWithIdpcAccessOrganisation(final JsonEnvelope response, final JsonObject associatedOrganisation, final JsonObject lastAssociatedOrganisation,
                                                                  final Optional<JsonArray> associatedPersons, final String caseUrn, final boolean isCivil) {

        final JsonObject responsePayload = response.payloadAsJsonObject();
        final JsonArrayBuilder associatedPersonsArray = createArrayBuilder();
        //Add associated persons if present
        if (associatedPersons.isPresent()) {
            for (int i = 0; i < associatedPersons.get().size(); i++) {
                final JsonObject jsonObject = associatedPersons.get().getJsonObject(i);
                associatedPersonsArray.add(jsonObject);
            }
        }
        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder().add(DEFENCE_CLIENT_ID, responsePayload.getString(DEFENCE_CLIENT_ID)).add(CASE_ID, responsePayload.getString(CASE_ID)).add(DEFENDANT_ID, responsePayload.getString(DEFENDANT_ID)).add(LOCKED_BY_REP_ORDER, responsePayload.getBoolean(LOCKED_BY_REP_ORDER)).add(ASSOCIATED_ORGANISATION, associatedOrganisation).add(LAST_ASSOCIATED_ORGANISATION, lastAssociatedOrganisation).add(ASSOCIATED_PERSONS, associatedPersonsArray);

        if (responsePayload.containsKey(PROSECUTION_AUTHORITY_CODE)) {
            jsonObjectBuilder.add(PROSECUTION_AUTHORITY_CODE, responsePayload.getString(PROSECUTION_AUTHORITY_CODE));
        }

        final JsonArray idpcAccessingOrganisations = responsePayload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        final ArrayList<String> orgIdsToLookup = getOrgIdsToLookUp(idpcAccessingOrganisations);
        if (!orgIdsToLookup.isEmpty()) {
            final List<OrderedOrganisationDetailsVO> orderedOrganisationDetailsVO = getOrgDetailsWithOrgNames(response.metadata(), idpcAccessingOrganisations, orgIdsToLookup);
            jsonObjectBuilder.add(IDPC_ACCESSING_ORGANISATIONS, buildOrgList(orderedOrganisationDetailsVO));
        } else {
            jsonObjectBuilder.add(IDPC_ACCESSING_ORGANISATIONS, createArrayBuilder().build());
        }
        jsonObjectBuilder.add(INSTRUCTING_ORGANISATIONS, prepareInstructionHistory(response));

        final Optional<JsonObject> prosecutor = getProsecutor(responsePayload.getString(CASE_ID), response);
        prosecutor.ifPresent(jsonObject -> jsonObjectBuilder.add(PROSECUTOR, jsonObject));

        jsonObjectBuilder.add(CASE_URN, caseUrn);
        jsonObjectBuilder.add(IS_CIVIL, isCivil);

        return JsonEnvelope.envelopeFrom(response.metadata(), jsonObjectBuilder.build());
    }

    private Optional<JsonObject> getProsecutor(final String caseId, final JsonEnvelope response) {
        final Optional<JsonObject> prosecutionCaseDetailById = progressionQueryService.getProsecutionCaseDetailById(response, caseId);
        return prosecutionCaseDetailById.map(jsonObject -> jsonObject.getJsonObject(PROSECUTION_CASE).getJsonObject(PROSECUTOR));
    }

    private JsonArray prepareInstructionHistory(final JsonEnvelope response) {
        final JsonObject responsePayload = response.payloadAsJsonObject();
        final JsonArray instructingOrganisation = responsePayload.getJsonArray(INSTRUCTING_ORGANISATIONS);
        final ArrayList<String> orgIdsToLookup = getOrgIdsToLookUp(instructingOrganisation);
        if (!orgIdsToLookup.isEmpty()) {
            final List<DefenceClientInstructionHistoryVO> instructingOrgDetailsWithOrgNames = getInstructingOrgDetailsWithOrgNames(response.metadata(), instructingOrganisation, orgIdsToLookup);
            return buildInstructingOrgList(instructingOrgDetailsWithOrgNames);
        } else {
            return createArrayBuilder().build();
        }
    }

    private JsonArray buildInstructingOrgList(List<DefenceClientInstructionHistoryVO> instructingOrganisations) {
        final JsonArrayBuilder jsonArrayBuilder = createArrayBuilder();
        for (final DefenceClientInstructionHistoryVO InstructingOrganisation : instructingOrganisations) {
            final JsonObjectBuilder objectBuilder = createObjectBuilder();
            objectBuilder.add(USER_ID, InstructingOrganisation.getUserId().toString());
            objectBuilder.add(ORGANISATION_ID, InstructingOrganisation.getOrganisationId().toString());
            objectBuilder.add(INSTRUCTION_ID, InstructingOrganisation.getId().toString());
            objectBuilder.add(INSTRUCTION_DATE, InstructingOrganisation.getInstructionDate());
            objectBuilder.add(NAME, nonNull(InstructingOrganisation.getName()) ? InstructingOrganisation.getName() : "");
            jsonArrayBuilder.add(objectBuilder);
        }
        return jsonArrayBuilder.build();
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

    private List<OrderedOrganisationDetailsVO> getOrgDetailsWithOrgNames(final Metadata metadata, final JsonArray idpcAccessingOrganisations, final ArrayList<String> orgIdsToLookup) {
        final List<OrganisationNameVO> organisationNameVOS = getOrganisationNamesForOrganisationIds(metadata, orgIdsToLookup);
        return organisationNameVOS.stream().map(i -> mapNameToOrganisation(i, getOrgDetailsList(idpcAccessingOrganisations))).toList();
    }

    private List<DefenceClientInstructionHistoryVO> getInstructingOrgDetailsWithOrgNames(final Metadata metadata, final JsonArray instructingOrganisation, final ArrayList<String> orgIdsToLookup) {
        final List<OrganisationNameVO> organisationNameVOS = getOrganisationNamesForOrganisationIds(metadata, orgIdsToLookup);
        final Map<String, String> organisationNames = organisationNameVOS.stream().collect(Collectors.toMap(OrganisationNameVO::getOrganisationId, OrganisationNameVO::getOrganisationName));

        return getInstructingOrgDetailsList(instructingOrganisation, organisationNames);
    }

    private List<OrganisationNameVO> getOrganisationNamesForOrganisationIds(final Metadata metadata, final List<String> orgIdsToLookup) {
        return organisationQueryService.getOrganisationNamesForIds(orgIdsToLookup, metadata);
    }

    @SuppressWarnings("squid:S3655")
    private OrderedOrganisationDetailsVO mapNameToOrganisation(OrganisationNameVO organisationNameVO, List<OrderedOrganisationDetailsVO> idpcAccessingOrganisations) {
        return idpcAccessingOrganisations.stream().filter(i -> i.getOrganisationId().toString().equals(organisationNameVO.getOrganisationId())).map(i -> new OrderedOrganisationDetailsVO(i.getOrder(), i.getOrganisationId(), organisationNameVO.getOrganisationName())).findFirst().get();
    }

    private List<OrderedOrganisationDetailsVO> getOrgDetailsList(final JsonArray idpcAccessingOrganisations) {
        final List<OrderedOrganisationDetailsVO> idpcAccessingOrganisationList = new ArrayList<>();
        if (idpcAccessingOrganisations != null) {
            final int len = idpcAccessingOrganisations.size();
            OrderedOrganisationDetailsVO orderedOrganisationDetailsVO = null;
            for (int i = 0; i < len; i++) {
                final JsonObject jsonValue = (JsonObject) idpcAccessingOrganisations.get(i);
                orderedOrganisationDetailsVO = new OrderedOrganisationDetailsVO(jsonValue.getInt(ORDER), fromString(jsonValue.getString(ORGANISATION_ID)), null);
                idpcAccessingOrganisationList.add(orderedOrganisationDetailsVO);
            }
        }
        return idpcAccessingOrganisationList;
    }

    private List<DefenceClientInstructionHistoryVO> getInstructingOrgDetailsList(final JsonArray instructingOrganisationsJson, Map<String, String> organisationNames) {
        final List<DefenceClientInstructionHistoryVO> instructingOrganisationList = new ArrayList<>();
        if (instructingOrganisationsJson != null) {
            final int len = instructingOrganisationsJson.size();
            DefenceClientInstructionHistoryVO instructingOrganisation = null;
            for (int i = 0; i < len; i++) {
                final JsonObject jsonValue = (JsonObject) instructingOrganisationsJson.get(i);
                instructingOrganisation = new DefenceClientInstructionHistoryVO(fromString(jsonValue.getString(INSTRUCTION_ID)), fromString(jsonValue.getString(USER_ID)), fromString(jsonValue.getString(ORGANISATION_ID)), jsonValue.getString(INSTRUCTION_DATE), organisationNames.get(jsonValue.getString(ORGANISATION_ID)));
                instructingOrganisationList.add(instructingOrganisation);
            }
        }
        return instructingOrganisationList;
    }

    private ArrayList<String> getOrgIdsToLookUp(final JsonArray organisation) {
        final ArrayList<String> orgIdsToLookup = new ArrayList<>();
        if (organisation != null) {
            final int len = organisation.size();
            for (int i = 0; i < len; i++) {
                final JsonObject jsonValue = (JsonObject) organisation.get(i);
                final String organisationId = jsonValue.getString(ORGANISATION_ID);
                orgIdsToLookup.add(organisationId);
            }
        }
        return orgIdsToLookup;
    }

    private void validateInputParams(final JsonEnvelope request) {
        final JsonObject payload = request.payloadAsJsonObject();
        if (payload.getBoolean(IS_CIVIL, false)) {
            if (payload.containsKey(DOB)) {
                final String dateOfBirth = payload.getString(DOB);
                validateDateString(dateOfBirth);
            }
            return;
        }
        final String dateOfBirth = payload.getString(DOB, EMPTY);
        validateDateString(dateOfBirth);
    }

    private Association getAssociatedOrganisationIdForDefendant(final String defendantId, final Metadata metadata) {
        final Metadata metadataWithActionName = metadataFrom(metadata).withName(DEFENCE_QUERY_ASSOCIATED_ORGANISATION).build();
        final JsonEnvelope requestEnvelope = JsonEnvelope.envelopeFrom(metadataWithActionName, createObjectBuilder().add(DEFENDANT_ID, defendantId).build());
        final JsonObject jsonObject = defenceAssociationQueryApi.getAssociatedOrganisation(requestEnvelope).payloadAsJsonObject();
        final JsonObject associatedOrganisationJsonObject = jsonObject.getJsonObject(ASSOCIATION);

        try {

            return new ObjectMapperProducer().objectMapper().readValue(associatedOrganisationJsonObject.toString(), Association.class);

        } catch (IOException e) {
            LOGGER.error("Unable to unmarshal AssociatedOrganisation. Payload :{}", associatedOrganisationJsonObject, e);
            return null;
        }
    }


    private boolean isAssociationLockedByRepOrder(final String defendantId, final Metadata metadata) {
        final Metadata metadataBuilder = metadataFrom(metadata).withName("defence.query.defence-client-defendantId").build();

        final JsonEnvelope defenceClientbyDefendantIdEnvelop = JsonEnvelope.envelopeFrom(metadataBuilder, createObjectBuilder().add(DEFENDANT_ID, defendantId).build());

        final DefenceClient defenceClient = defenceQueryView.getDefenceClientByDefendantId(defenceClientbyDefendantIdEnvelop).payload();
        if (null == defenceClient.isLockedByRepOrder()) {
            defenceClient.setLockedByRepOrder(false);
        }
        return "true".equals(defenceClient.isLockedByRepOrder().toString());

    }

    private Optional<JsonArray> getAssociatedPersons(final String defendantId, final Optional<JsonArray> defendants) {
        if (defendants.isPresent()) {
            for (int i = 0; i < defendants.get().size(); i++) {
                final JsonObject jsonObject = defendants.get().getJsonObject(i);
                final String currentDefendantId = jsonObject.getString("id");
                if (currentDefendantId.equalsIgnoreCase(defendantId)) {
                    LOGGER.info("Associated persons mapped {} ", jsonObject.getJsonArray(ASSOCIATED_PERSONS));
                    return Optional.ofNullable(jsonObject.getJsonArray(ASSOCIATED_PERSONS));
                }
            }
        }
        return Optional.of(createArrayBuilder().build());
    }

    private Optional<JsonArray> getDefendants(final JsonEnvelope response, final UUID caseId) {
        final Optional<JsonObject> prosecutionCaseDetailById = progressionQueryService.getProsecutionCaseDetailById(response, caseId.toString());
        return prosecutionCaseDetailById.map(jsonObject -> jsonObject.getJsonObject(PROSECUTION_CASE).getJsonArray(CaseDefendantOrganisationHelper.DEFENDANTS));
    }

}
