package uk.gov.moj.cpp.defence.query.api;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.toBoolean;
import static uk.gov.justice.cps.defence.IsAdvocateDefendingOrProsecuting.DEFENDING;
import static uk.gov.justice.cps.defence.IsAdvocateDefendingOrProsecuting.PROSECUTING;
import static uk.gov.justice.services.core.enveloper.Enveloper.envelop;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_1;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_2;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_3;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_4;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_LINE_1;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_LINE_2;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_LINE_3;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_LINE_4;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_POSTCODE;
import static uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView.ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY;

import uk.gov.justice.cps.defence.CaseAdvocateAccess;
import uk.gov.justice.cps.defence.ExpiredProsecutorAssignments;
import uk.gov.justice.cps.defence.ExpiredProsecutorOrganisationAssignments;
import uk.gov.justice.cps.defence.IsAdvocateDefendingOrProsecuting;
import uk.gov.justice.cps.defence.Prosecutioncase;
import uk.gov.justice.cps.defence.SearchCaseByUrn;
import uk.gov.justice.cps.defence.caag.CaseDetails;
import uk.gov.justice.cps.defence.caag.Defendants;
import uk.gov.justice.cps.defence.caag.ProsecutioncaseCaag;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.query.api.hearing.AssignedProsecutor;
import uk.gov.moj.cpp.defence.query.api.hearing.CourtRoom;
import uk.gov.moj.cpp.defence.query.api.hearing.Defendant;
import uk.gov.moj.cpp.defence.query.api.hearing.ProsecutionCase;
import uk.gov.moj.cpp.defence.query.api.service.UsersAndGroupsService;
import uk.gov.moj.cpp.defence.query.hearing.api.HearingSummary;
import uk.gov.moj.cpp.defence.query.hearing.api.ProsecutionCaseSummary;
import uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView;
import uk.gov.moj.cpp.defence.query.view.DefenceQueryService;
import uk.gov.moj.cpp.defence.refdata.ProsecutorDetails;
import uk.gov.moj.cpp.defence.service.ProgressionService;
import uk.gov.moj.cpp.defence.service.ReferenceDataService;
import uk.gov.moj.cpp.defence.service.UsersGroupQueryService;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

@ServiceComponent(Component.QUERY_API)
public class CpsCaseAccessQueryApi {

    public static final String IS_ADVOCATE_DEFENDING_OR_PROSECUTING = "isAdvocateDefendingOrProsecuting";
    public static final String USER_HAS_NO_PERMISSION_FOR_PROSECUTOR_VIEW = "User has no permission for prosecutor view!";

    @Inject
    private UsersAndGroupsService usersAndGroupsService;
    @Inject
    private ProgressionService progressionService;
    @Inject
    private DefenceQueryService defenceService;
    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private CpsCaseAccessQueryView cpsCaseAccessQueryView;

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Inject
    private UsersGroupQueryService usersGroupQueryService;

    public static final String NON_CPS_PROSECUTORS = "Non CPS Prosecutors";
    public static final String SHORT_NAME = "shortName";

    @Handles("defence.query.case-cps-assignees")
    public JsonEnvelope getAssigneesToTheCase(final JsonEnvelope caseCpsAssigneesEnvelope) {
        final JsonEnvelope viewResponseEnvelope = cpsCaseAccessQueryView.getAssignedUsersToTheCase(caseCpsAssigneesEnvelope);

        return envelopeFrom(
                viewResponseEnvelope.metadata(),
                addAddressDetails(viewResponseEnvelope));
    }

    @Handles("defence.query.case-organisation-assignees")
    public JsonEnvelope getAdvocatesAssignedToTheCaseAndOrganisation(final JsonEnvelope query) {
        return cpsCaseAccessQueryView.getAssignedAdvocatesToTheCaseAndOrganisation(query);
    }

    @Handles("advocate.query.role-in-case")
    public JsonEnvelope getAdvocateRole(final JsonEnvelope query) {
        final JsonEnvelope queryWithActiveProsecutingAssignmentsOnly = envelopeFrom(
                query.metadata(),
                createObjectBuilder(query.payloadAsJsonObject()).add(ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY, true).build()
        );

        final JsonEnvelope jsonEnvelope = cpsCaseAccessQueryView.findAdvocatesRoleInCase(queryWithActiveProsecutingAssignmentsOnly);
        if (!jsonEnvelope.payloadAsJsonObject().containsKey(IS_ADVOCATE_DEFENDING_OR_PROSECUTING)) {
            return envelopeFrom(
                    jsonEnvelope.metadata(),
                    null
            );
        }
        return jsonEnvelope;
    }

    @Handles("advocate.query.role-in-case-by-caseid")
    public JsonEnvelope getAdvocateRoleByCaseId(final JsonEnvelope query) {
        final JsonEnvelope jsonEnvelope = cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(query);
        if (!jsonEnvelope.payloadAsJsonObject().containsKey(IS_ADVOCATE_DEFENDING_OR_PROSECUTING)) {
            return envelopeFrom(
                    jsonEnvelope.metadata(),
                    null
            );
        }
        return jsonEnvelope;
    }

    @Handles("defence.query.prosecutioncase-defence-caag")
    public Envelope<ProsecutioncaseCaag> queryProsecutioncaseDefenceCaag(final Envelope<SearchCaseByUrn> request) {
        if (isNotInDefenceRole(request)) {
            throw new ForbiddenRequestException("User has no permission for defence view!");
        }

        final Envelope<ProsecutioncaseCaag> responseEnvelop = cpsCaseAccessQueryView.queryProsecutioncaseDefenceCaag(request);
        final ProsecutioncaseCaag prosecutioncaseCaag = removeUnAuthorisedDataForDefence(responseEnvelop.payload());

        return envelopeFrom(
                responseEnvelop.metadata(),
                prosecutioncaseCaag
        );
    }

    @Handles("advocate.query.prosecutioncase-defence")
    public Envelope<Prosecutioncase> queryProsecutioncaseDefence(final Envelope<SearchCaseByUrn> request) throws IOException {
        if (isNotInDefenceRoleByCaseId(request)) {
            throw new ForbiddenRequestException("User has no permission for defence view!");
        }

        final Envelope<Prosecutioncase> responseEnvelop = cpsCaseAccessQueryView.queryProsecutioncaseDefence(request);
        final Prosecutioncase prosecutioncase = removeUnAuthorisedDataForDefence(responseEnvelop.payload());

        return envelopeFrom(
                responseEnvelop.metadata(),
                prosecutioncase
        );
    }


    @Handles("advocate.query.prosecutioncase-prosecutor")
    public Envelope<Prosecutioncase> queryProsecutioncaseProsecutor(final Envelope<SearchCaseByUrn> request) throws IOException {
        if (isNotInAdvocateRoleByCaseId(request)) {
            throw new ForbiddenRequestException(USER_HAS_NO_PERMISSION_FOR_PROSECUTOR_VIEW);
        }
        final Envelope<Prosecutioncase> responseEnvelop = cpsCaseAccessQueryView.queryProsecutioncaseProsecutor(request);
        return envelopeFrom(
                responseEnvelop.metadata(),
                responseEnvelop.payload()
        );
    }

    @SuppressWarnings({"squid:S1168", "squid:S3655"})
    @Handles("advocate.query.prosecutioncase-prosecutor-caag")
    public Envelope<ProsecutioncaseCaag> queryProsecutioncaseProsecutorCaag(final Envelope<SearchCaseByUrn> request) {

        final UUID userId = request.metadata().userId().isPresent() ? fromString(request.metadata().userId().get()) : null;
        final UUID caseId = defenceService.getCaseId(request.payload().getCaseUrn());
        final UUID prosecutorId = progressionService.getProsecutorOrProsecutionCaseAuthorityID(request.metadata(), caseId);
        final Optional<JsonObject> prosecutorJsonObjectOptional = referenceDataService.getProsecutor(request.metadata(), prosecutorId);
        final Boolean isNonCpsProsecutor = usersGroupQueryService.validateNonCPSUser(request.metadata(), userId, NON_CPS_PROSECUTORS, prosecutorJsonObjectOptional.get().getString(SHORT_NAME));
        if (isNotInAdvocateRole(request) && !isNonCpsProsecutor) {
            throw new ForbiddenRequestException(USER_HAS_NO_PERMISSION_FOR_PROSECUTOR_VIEW);
        }
        final Envelope<ProsecutioncaseCaag> responseEnvelop = cpsCaseAccessQueryView.queryProsecutioncaseProsecutorCaag(request);
        final ProsecutioncaseCaag prosecutioncaseCaag = removeUnAuthorisedDataForProsecutor(responseEnvelop);
        return envelopeFrom(
                responseEnvelop.metadata(),
                prosecutioncaseCaag
        );
    }

    @Handles("defence.query.hearings-timeline")
    public JsonEnvelope getHearingsTimeline(final JsonEnvelope query) {
        return cpsCaseAccessQueryView.getCaseAndApplicationTimelines(query);
    }

    @Handles("advocate.query.expired-prosecutor-assignments")
    public Envelope<ExpiredProsecutorAssignments> queryExpiredProsecutorAssignments(final JsonEnvelope envelope) {
        return cpsCaseAccessQueryView.queryExpiredProsecutorAssignments(envelope);
    }

    @Handles("advocate.query.expired-prosecutor-organisation-assignments")
    public Envelope<ExpiredProsecutorOrganisationAssignments> queryExpiredProsecutorOrganisationAssignments(final JsonEnvelope envelope) {
        return cpsCaseAccessQueryView.queryExpiredProsecutorOrganisationAssignments(envelope);
    }


    @Handles("defence.query.hearings")
    public Envelope<uk.gov.moj.cpp.defence.query.api.Hearings> getHearings(final JsonEnvelope query) {
        final uk.gov.moj.cpp.defence.query.hearing.api.Hearings hearings = filterHearings(query.metadata(), getUnFilteredHearings(query));
        if (isEmpty(hearings.getHearingSummaries())) {
            return emptyPayload(query.metadata());
        }

        final List<UUID> distinctCourtRoomIds = hearings.getHearingSummaries().stream()
                .map(hearingSummary -> hearingSummary.getCourtCentre().getRoomId())
                .distinct()
                .collect(toList());

        final Hearings responseHearings = Hearings.hearings()
                .withCourtRooms(getCourtRooms(query.metadata(), distinctCourtRoomIds, hearings))
                .build();

        if (isEmpty(responseHearings.getCourtRooms())) {
            return emptyPayload(query.metadata());
        }

        return envelopeFrom(
                query.metadata(),
                responseHearings
        );
    }

    private uk.gov.moj.cpp.defence.query.hearing.api.Hearings getUnFilteredHearings(final JsonEnvelope query) {
        return cpsCaseAccessQueryView.getHearings(query).payload();
    }

    private uk.gov.moj.cpp.defence.query.hearing.api.Hearings filterHearings(final Metadata metadata, final uk.gov.moj.cpp.defence.query.hearing.api.Hearings payload) {
        return uk.gov.moj.cpp.defence.query.hearing.api.Hearings.hearings()
                .withHearingSummaries(filterHearingSummaries(metadata, payload))
                .build();
    }

    private List<HearingSummary> filterHearingSummaries(final Metadata metadata, final uk.gov.moj.cpp.defence.query.hearing.api.Hearings hearings) {
        if (isNull(hearings) || isNull(hearings.getHearingSummaries())) {
            return emptyList();
        }

        final Map<UUID, UUID> prosecutionAuthorityIdMap = progressionService.getProsecutionAuthorityIdMap(metadata, getCaseIdsAsList(hearings.getHearingSummaries()));
        final Map<UUID, ProsecutorDetails> prosecutorDetailsMap = referenceDataService.getProsecutorsAsMap(metadata);
        final UUID userId = fromString(metadata.userId().orElseThrow(() -> new IllegalArgumentException("No UserId Supplied")));
        final JsonObject userGroups = usersGroupQueryService.getUserGroups(metadata, userId);

        return hearings.getHearingSummaries().stream()
                .map(hearingSummary -> HearingSummary.hearingSummary()
                        .withValuesFrom(hearingSummary)
                        .withProsecutionCaseSummaries(getFilteredProsecutionCases(hearingSummary.getProsecutionCaseSummaries(), prosecutionAuthorityIdMap, prosecutorDetailsMap, userGroups))
                        .build())
                .filter(hearingSummary -> isNotEmpty((hearingSummary.getProsecutionCaseSummaries())))
                .collect(toList());
    }

    private List<ProsecutionCaseSummary> getFilteredProsecutionCases(final List<ProsecutionCaseSummary> prosecutionCaseSummaries, final Map<UUID, UUID> prosecutionAuthorityIdMap, final Map<UUID, ProsecutorDetails> prosecutorDetailsMap, final JsonObject userGroups) {
        if (isNull(prosecutionCaseSummaries)) {
            return emptyList();
        }

        return prosecutionCaseSummaries.stream()
                .filter(prosecutionCaseSummary -> isValidProsecutionAuthority(prosecutionCaseSummary.getId(), prosecutionAuthorityIdMap, prosecutorDetailsMap, userGroups))
                .collect(toList());
    }

    private List<UUID> getCaseIdsAsList(final List<HearingSummary> hearingSummaries) {
        return hearingSummaries.stream()
                .filter(hearingSummary -> nonNull(hearingSummary.getProsecutionCaseSummaries()))
                .flatMap(hearingSummary -> hearingSummary.getProsecutionCaseSummaries().stream())
                .map(ProsecutionCaseSummary::getId)
                .collect(toList());
    }

    private List<CourtRoom> getCourtRooms(final Metadata metadata, final List<UUID> distinctCourtRoomIds, final uk.gov.moj.cpp.defence.query.hearing.api.Hearings hearings) {

        final List<CourtRoom> courtRooms = distinctCourtRoomIds.stream()
                .map(courtRoomId -> hearings.getHearingSummaries().stream()
                        .filter(hearingSummary -> courtRoomId.equals(hearingSummary.getCourtCentre().getRoomId()))
                        .filter(hearingSummary -> nonNull(hearingSummary.getProsecutionCaseSummaries()))
                        .map(hearingSummary -> CourtRoom.courtRoom()
                                .withId(hearingSummary.getCourtCentre().getRoomId())
                                .withName(hearingSummary.getCourtCentre().getRoomName())
                                .withProsecutionCases(getProsecutionCases(metadata, hearingSummary))
                                .build())
                        .collect(toList())).flatMap(Collection::stream).collect(toList());

        return distinctCourtRoomIds.stream()
                .map(courtRoomId -> {
                    final CourtRoom foundCourtRoom = courtRooms.stream()
                            .filter(courtRoom -> courtRoom.getId().equals(courtRoomId))
                            .findAny().orElse(CourtRoom.courtRoom().build());
                    return CourtRoom.courtRoom()
                            .withId(foundCourtRoom.getId())
                            .withName(foundCourtRoom.getName())
                            .withProsecutionCases(getMergedProsecutionCases(courtRooms, courtRoomId))
                            .build();
                }).sorted(comparing(CourtRoom::getName))
                .collect(toList());

    }

    private List<ProsecutionCase> getMergedProsecutionCases(final List<CourtRoom> courtRooms, final UUID courtRoomId) {
        return courtRooms.stream()
                .filter(courtRoom -> courtRoom.getId().equals(courtRoomId))
                .flatMap(courtRoom -> courtRoom.getProsecutionCases().stream())
                .collect(toList());
    }

    private List<ProsecutionCase> getProsecutionCases(final Metadata metadata, final HearingSummary hearingSummary) {
        return hearingSummary.getProsecutionCaseSummaries().stream()
                .map(prosecutionCaseSummary -> ProsecutionCase.prosecutionCase()
                        .withHearingId(hearingSummary.getId())
                        .withCaseId(prosecutionCaseSummary.getId())
                        .withCaseUrn(
                                null != prosecutionCaseSummary.getProsecutionCaseIdentifier().getCaseURN()
                                        ? prosecutionCaseSummary.getProsecutionCaseIdentifier().getCaseURN()
                                        : prosecutionCaseSummary.getProsecutionCaseIdentifier().getProsecutionAuthorityReference())
                        .withDefendants(getDefendants(prosecutionCaseSummary.getDefendants()))
                        .withAssignedProsecutors(getAssignedProsecutors(metadata, prosecutionCaseSummary.getId()))
                        .build())
                .collect(toList());

    }

    @SuppressWarnings("squid:S1168")
    private List<AssignedProsecutor> getAssignedProsecutors(final Metadata metadata, final UUID caseId) {

        final JsonEnvelope queryEnvelope = envelopeFrom(metadataFrom(metadata)
                        .withName("defence.query.case-cps-assignees"),
                createObjectBuilder().
                        add("caseId", caseId.toString()).build());
        final JsonObject payload = cpsCaseAccessQueryView.getAssignedUsersToTheCase(queryEnvelope).payloadAsJsonObject();
        final CaseAdvocateAccess caseAdvocateAccess = this.jsonObjectToObjectConverter.convert(payload, CaseAdvocateAccess.class);

        if (isNull(caseAdvocateAccess) || isEmpty(caseAdvocateAccess.getAssignees())) {
            return null;
        }

        return caseAdvocateAccess.getAssignees().stream()
                .map(assignees -> AssignedProsecutor.assignedProsecutor()
                        .withId(assignees.getAssigneeUserId())
                        .withFullName(assignees.getAssigneeName())
                        .build())
                .collect(toList());

    }

    private List<Defendant> getDefendants(final List<uk.gov.moj.cpp.defence.query.hearing.api.Defendants> defendants) {
        return defendants.stream()
                .map(defendant ->
                        Defendant.defendant()
                                .withId(defendant.getId())
                                .withFirstName(defendant.getFirstName())
                                .withMiddleName(defendant.getMiddleName())
                                .withLastName(defendant.getLastName())
                                .withOrganisationName(defendant.getOrganisationName())
                                .build())
                .collect(toList());
    }

    private Envelope<Hearings> emptyPayload(final Metadata metadata) {
        return Envelope.envelopeFrom(
                metadata,
                Hearings.hearings()
                        .withCourtRooms(emptyList())
                        .build()
        );
    }

    private boolean isNotInAdvocateRole(final Envelope<SearchCaseByUrn> request) {
        final JsonObject roleInCaseJsonObject = queryRoleInCase(request);
        return isNotInAdvocateRole(roleInCaseJsonObject);
    }

    private boolean isNotInAdvocateRoleByCaseId(final Envelope<SearchCaseByUrn> request) {
        final JsonObject roleInCaseJsonObject = queryRoleInCaseByCaseId(request);
        return isNotInAdvocateRole(roleInCaseJsonObject);
    }

    private boolean isNotInAdvocateRole(final JsonObject roleInCaseJsonObject) {
        return !roleInCaseJsonObject.containsKey(IS_ADVOCATE_DEFENDING_OR_PROSECUTING) || DEFENDING == IsAdvocateDefendingOrProsecuting.valueFor(roleInCaseJsonObject.getJsonString(IS_ADVOCATE_DEFENDING_OR_PROSECUTING).getString()).orElse(null);
    }

    private boolean isNotInDefenceRole(final Envelope<SearchCaseByUrn> request) {
        final JsonObject roleInCaseJsonObject = queryRoleInCase(request);
        return isNotInDefenceRole(roleInCaseJsonObject);
    }


    private boolean isNotInDefenceRoleByCaseId(final Envelope<SearchCaseByUrn> request) {
        final JsonObject roleInCaseJsonObject = queryRoleInCaseByCaseId(request);
        return isNotInDefenceRole(roleInCaseJsonObject);
    }

    private boolean isNotInDefenceRole(final JsonObject roleInCaseJsonObject) {
        return !roleInCaseJsonObject.containsKey(IS_ADVOCATE_DEFENDING_OR_PROSECUTING) || PROSECUTING == IsAdvocateDefendingOrProsecuting.valueFor(roleInCaseJsonObject.getJsonString(IS_ADVOCATE_DEFENDING_OR_PROSECUTING).getString()).orElse(null);
    }

    private JsonObject queryRoleInCase(final Envelope<SearchCaseByUrn> request) {
        final JsonObject roleInCasePayload = createObjectBuilder()
                .add("caseUrn", request.payload().getCaseUrn())
                .build();
        final Envelope<JsonObject> requestEnvelope = envelop(roleInCasePayload)
                .withName("advocate.query.role-in-case").withMetadataFrom(request);

        return cpsCaseAccessQueryView.findAdvocatesRoleInCase(envelopeFrom(requestEnvelope.metadata(), requestEnvelope.payload())).payloadAsJsonObject();

    }

    private JsonObject queryRoleInCaseByCaseId(final Envelope<SearchCaseByUrn> request) {
        final JsonObject roleInCasePayload = createObjectBuilder()
                .add("caseId", request.payload().getCaseId().toString())
                .build();
        final Envelope<JsonObject> requestEnvelope = envelop(roleInCasePayload)
                .withName("advocate.query.role-in-case-by-caseid").withMetadataFrom(request);
        final JsonEnvelope roleInCaseRequestEnvelope = JsonEnvelope.envelopeFrom(requestEnvelope.metadata(), requestEnvelope.payload());
        final JsonEnvelope response = cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(roleInCaseRequestEnvelope);
        return response.payloadAsJsonObject();

    }

    private ProsecutioncaseCaag removeUnAuthorisedDataForDefence(final ProsecutioncaseCaag prosecutioncaseCaag) {
        return ProsecutioncaseCaag.prosecutioncaseCaag()
                .withValuesFrom(prosecutioncaseCaag)
                .withCaseDetails(CaseDetails.caseDetails()
                        .withCaseStatus(prosecutioncaseCaag.getCaseDetails().getCaseStatus())
                        .withCaseURN(prosecutioncaseCaag.getCaseDetails().getCaseURN())
                        .withInitiationCode(prosecutioncaseCaag.getCaseDetails().getInitiationCode())
                        .withRemovalReason(prosecutioncaseCaag.getCaseDetails().getRemovalReason())
                        .withIsCivil(prosecutioncaseCaag.getCaseDetails().getIsCivil())
                        .withGroupId(prosecutioncaseCaag.getCaseDetails().getGroupId())
                        .withIsGroupMaster(prosecutioncaseCaag.getCaseDetails().getIsGroupMaster())
                        .withIsGroupMember(prosecutioncaseCaag.getCaseDetails().getIsGroupMember())
                        .build())
                .build();
    }

    private Prosecutioncase removeUnAuthorisedDataForDefence(final Prosecutioncase prosecutioncase) {
        return Prosecutioncase.prosecutioncase()
                .withValuesFrom(prosecutioncase)
                .withProsecutionCase(uk.gov.justice.core.courts.ProsecutionCase.prosecutionCase()
                        .withValuesFrom(prosecutioncase.getProsecutionCase())
                        .withCaseMarkers(null)
                        .build())
                .build();
    }

    private ProsecutioncaseCaag removeUnAuthorisedDataForProsecutor(final Envelope<ProsecutioncaseCaag> responseEnvelop) {
        return ProsecutioncaseCaag.prosecutioncaseCaag()
                .withValuesFrom(responseEnvelop.payload())
                .withDefendants(removeDefendantMarkers(responseEnvelop.payload().getDefendants()))
                .build();
    }

    private List<Defendants> removeDefendantMarkers(final List<Defendants> defendants) {
        return defendants.stream()
                .map(defendant -> Defendants.defendants()
                        .withValuesFrom(defendant)
                        .withDefendantMarkers(null)
                        .build())
                .collect(toList());
    }


    private JsonObject addAddressDetails(final JsonEnvelope viewResponseEnvelope) {
        return createObjectBuilder()
                .add("assignees", getUpdatedAssignees(viewResponseEnvelope))
                .build();
    }

    private JsonArray getUpdatedAssignees(final JsonEnvelope viewResponseEnvelope) {
        final JsonArray assigneesJsonArray = viewResponseEnvelope.payloadAsJsonObject().getJsonArray("assignees");
        final List<JsonObject> addressJsonList = assigneesJsonArray.stream().map(jsonValue -> {
            final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder((JsonObject) jsonValue);
            JsonObject addressJsonObject = getAddressJsonObject(viewResponseEnvelope, (JsonObject) jsonValue);
            if (nonNull(addressJsonObject)) {
                jsonObjectBuilder.add(ADDRESS, addressJsonObject);
            }
            return jsonObjectBuilder.build();
        }).collect(toList());

        final JsonArrayBuilder updatedJsonArrayBuilder = createArrayBuilder();
        addressJsonList.forEach(updatedJsonArrayBuilder::add);
        return updatedJsonArrayBuilder.build();
    }

    private JsonObject getAddressJsonObject(final JsonEnvelope viewResponseEnvelope, final JsonObject jsonValue) {
        final JsonObject organisationDetailsForUserJsonObject = usersAndGroupsService.getOrganisationDetails(viewResponseEnvelope, getOrganisationId(jsonValue));
        String address2 = "";
        String address3 = "";
        if (nonNull(organisationDetailsForUserJsonObject)) {
            if (organisationDetailsForUserJsonObject.toString().contains(ADDRESS_LINE_2)) {
                address2 = organisationDetailsForUserJsonObject.getString(ADDRESS_LINE_2);
            }
            if (organisationDetailsForUserJsonObject.toString().contains(ADDRESS_LINE_3)) {
                address3 = organisationDetailsForUserJsonObject.getString(ADDRESS_LINE_3);
            }
            return createObjectBuilder()
                    .add(ADDRESS_1, organisationDetailsForUserJsonObject.getString(ADDRESS_LINE_1))
                    .add(ADDRESS_2, address2)
                    .add(ADDRESS_3, address3)
                    .add(ADDRESS_4, organisationDetailsForUserJsonObject.getString(ADDRESS_LINE_4))
                    .add(ADDRESS_POSTCODE, organisationDetailsForUserJsonObject.getString(ADDRESS_POSTCODE))
                    .build();
        } else {
            return null;
        }


    }

    private UUID getOrganisationId(final JsonObject jsonValue) {
        return fromString(jsonValue.getString("assigneeOrganisationId"));
    }

    private boolean isValidProsecutionAuthority(final UUID caseId, final Map<UUID, UUID> prosecutionAuthorityIdMap, final Map<UUID, ProsecutorDetails> prosecutorDetailsMap, final JsonObject userGroups) {
        if (usersGroupQueryService.isNonCpsUserGroup(userGroups, NON_CPS_PROSECUTORS)) {
            final Optional<ProsecutorDetails> prosecutorDetailsOptional = ofNullable(prosecutorDetailsMap.get(prosecutionAuthorityIdMap.get(caseId)));
            return prosecutorDetailsOptional.filter(prosecutorDetails -> usersGroupQueryService.isNonCPSProsecutorWithValidProsecutingAuthority(userGroups, NON_CPS_PROSECUTORS, prosecutorDetails.getShortName())).isPresent();
        } else {
            final Optional<ProsecutorDetails> prosecutorDetailsOptional = ofNullable(prosecutorDetailsMap.get(prosecutionAuthorityIdMap.get(caseId)));
            return prosecutorDetailsOptional.filter(prosecutorDetails -> toBoolean(prosecutorDetails.getIsCps()) || toBoolean(prosecutorDetails.getIsPolice())).isPresent();
        }

    }
}
