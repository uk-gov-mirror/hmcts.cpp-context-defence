package uk.gov.moj.cpp.defence.query.view;


import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.disjoint;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.UUID.fromString;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.core.annotation.Component.QUERY_VIEW;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.getJsonArray;
import static uk.gov.justice.services.messaging.JsonObjects.getString;

import uk.gov.justice.core.courts.AssociatedPerson;
import uk.gov.justice.core.courts.CourtApplicationParty;
import uk.gov.justice.core.courts.Defendant;
import uk.gov.justice.core.courts.ProsecutionCase;
import uk.gov.justice.cps.defence.ExpiredProsecutorAssignments;
import uk.gov.justice.cps.defence.ExpiredProsecutorOrganisationAssignments;
import uk.gov.justice.cps.defence.Prosecutioncase;
import uk.gov.justice.cps.defence.SearchCaseByUrn;
import uk.gov.justice.cps.defence.caag.Defendants;
import uk.gov.justice.cps.defence.caag.LinkedApplications;
import uk.gov.justice.cps.defence.caag.ProsecutioncaseCaag;
import uk.gov.justice.cps.defence.progression.ApplicationSummary;
import uk.gov.justice.cps.defence.progression.CourtOrder;
import uk.gov.justice.cps.defence.progression.DefendantHearings;
import uk.gov.justice.cps.defence.progression.HearingsAtAGlance;
import uk.gov.justice.cps.defence.progression.RelatedCase;
import uk.gov.justice.json.schemas.hearing.HearingSummaries;
import uk.gov.justice.json.schemas.hearing.Timeline;
import uk.gov.justice.listing.events.CourtApplication;
import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ListToJsonArrayConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.OrganisationAssignment;
import uk.gov.moj.cpp.defence.ProsecutorAssignment;
import uk.gov.moj.cpp.defence.persistence.AdvocateAccessRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceGrantAccessRepository;
import uk.gov.moj.cpp.defence.persistence.OrganisationAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.AssignmentUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceGrantAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionAdvocateAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.query.hearing.api.Hearings;
import uk.gov.moj.cpp.defence.service.HearingService;
import uk.gov.moj.cpp.defence.service.ListingService;
import uk.gov.moj.cpp.defence.service.ProgressionService;
import uk.gov.moj.cpp.defence.service.ReferenceDataService;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.defence.service.UsersGroupQueryService;
import uk.gov.moj.cpp.hearing.Application;
import uk.gov.moj.cpp.hearing.Person;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.persistence.NoResultException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings({"squid:S1168"})
@ServiceComponent(QUERY_VIEW)
public class CpsCaseAccessQueryView {

    public static final String DEFENCE = "Defence";
    public static final String DEFENCE_LAWYERS = "Defence Lawyers";
    public static final String IS_ADVOCATE_DEFENDING_OR_PROSECUTING = "isAdvocateDefendingOrProsecuting";
    public static final String BOTH = "both";
    public static final String PROSECUTING = "prosecuting";
    public static final String DEFENDING = "defending";
    public static final String ASSIGNEE_ID = "assigneeId";
    public static final String CASE_URN = "caseUrn";
    public static final String ACCESS_GRANTED_BY = "Access granted by";
    public static final String APPLICATION_ID = "applicationIds";
    public static final String HEARING_SUMMARIES = "hearingSummaries";
    public static final String UNALLOCATED_HEARINGS = "unallocatedHearings";
    public static final String COMMA = ",";
    public static final String ADVOCATE_ROLE = "advocateRole";
    public static final String AUTHORIZED_DEFENDANT_IDS = "authorizedDefendantIds";
    static final String ASSIGNEES = "assignees";
    static final String CASE_ID = "caseId";
    static final String ORGANISATION_ID = "organisationId";
    static final String NAME_STR = "%s %s";
    private static final String NAME_STR_STATUS = "%s %s %s";
    public static final String EXPIRED_ASSIGNMENTS_SELECT_COUNT = "expiredAssignmentsSelectCount";
    public static final String ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY = "activeProsecutingAssignmentsOnly";
    public static final String USER_ID = "userId";
    public static final String PROSECUTION_CASE = "prosecutionCase";
    public static final String NON_CPS_PROSECUTORS = "Non CPS Prosecutors";
    public static final String SHORT_NAME = "shortName";
    public static final String ORGANISATION_MIS_MATCH = "OrganisationMisMatch";
    public static final String USER_HAS_NO_PERMISSION_FOR_THE_S_VIEW = "User has no permission for the %s view";

    @Inject
    DefenceAssociationRepository defenceAssociationRepository;
    @Inject
    private AdvocateAccessRepository advocateAssignmentRepository;
    @Inject
    private DefenceGrantAccessRepository defenceGrantAccessRepository;
    @Inject
    private OrganisationAccessRepository organisationAccessRepository;
    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Inject
    private DefenceQueryService defenceQueryService;
    @Inject
    private UserGroupService userGroupService;

    @Inject
    private HearingService hearingService;

    @Inject
    private ListingService listingService;

    @Inject
    private ListToJsonArrayConverter listToJsonArrayConverter;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    @ServiceComponent(QUERY_API)
    private Requester requester;
    @Inject
    private ProgressionService progressionService;
    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Inject
    private UsersGroupQueryService usersGroupQueryService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProducer().objectMapper();

    @Handles("defence.query.case-cps-assignees")
    public JsonEnvelope getAssignedUsersToTheCase(final JsonEnvelope envelope) {

        final UUID caseId = fromString(envelope.payloadAsJsonObject().getString(CASE_ID));
        final List<ProsecutionOrganisationAccess> assigneeOrganisationList = organisationAccessRepository.findByCaseId(caseId);

        final JsonArrayBuilder assigneesJson = createArrayBuilder();

        assigneeOrganisationList.forEach(assigneeOrganisation -> {
            if (isNotEmpty(assigneeOrganisation.getProsecutionAdvocatesWithAccess())) {
                assigneeOrganisation.getProsecutionAdvocatesWithAccess().forEach(prosecutionAdvocateAccess -> {
                    final ProsecutionCaseAssigneeVO prosecutionAdvocateCaseAssignment = toProsecutionCaseAssigneeVO(assigneeOrganisation, prosecutionAdvocateAccess);
                    assigneesJson.add(objectToJsonObjectConverter.convert(prosecutionAdvocateCaseAssignment));
                });
            } else {
                final ProsecutionCaseAssigneeVO prosecutionOrganisationCaseAssignment = toProsecutionCaseAssigneeVO(assigneeOrganisation);
                assigneesJson.add(objectToJsonObjectConverter.convert(prosecutionOrganisationCaseAssignment));
            }
        });

        return envelopeFrom(
                envelope.metadata(),
                createObjectBuilder()
                        .add(ASSIGNEES, assigneesJson)
                        .build());
    }

    @Handles("defence.query.case-organisation-assignees")
    public JsonEnvelope getAssignedAdvocatesToTheCaseAndOrganisation(final JsonEnvelope envelope) {

        final UUID caseId = fromString(envelope.payloadAsJsonObject().getString(CASE_ID));
        final UUID organisationId = fromString(envelope.payloadAsJsonObject().getString(ORGANISATION_ID));

        final List<ProsecutionOrganisationAccess> assigneeOrganisationList = organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseId, organisationId);

        final JsonArrayBuilder assigneesJson = createArrayBuilder();

        assigneeOrganisationList.forEach(assigneeOrganisation -> {
            if (isNotEmpty(assigneeOrganisation.getProsecutionAdvocatesWithAccess())) {
                assigneeOrganisation.getProsecutionAdvocatesWithAccess().forEach(prosecutionAdvocateAccess -> {
                    final ProsecutionCaseAssigneeVO prosecutionAdvocateCaseAssignment = toProsecutionCaseAssigneeVO(assigneeOrganisation, prosecutionAdvocateAccess);
                    assigneesJson.add(objectToJsonObjectConverter.convert(prosecutionAdvocateCaseAssignment));
                });
            }
        });

        return envelopeFrom(
                envelope.metadata(),
                createObjectBuilder()
                        .add(ASSIGNEES, assigneesJson)
                        .build());
    }


    @Handles("advocate.query.role-in-case")
    public JsonEnvelope findAdvocatesRoleInCase(final JsonEnvelope envelope) {
        final String urn = envelope.payloadAsJsonObject().getString(CASE_URN);
        final String userId = getUserId(envelope);
        final String caseUrn = urn != null && !urn.isEmpty() ? urn.toUpperCase() : urn;
        final UUID caseId = defenceQueryService.getCaseId(caseUrn);
        final boolean activeProsecutingAssignmentsOnly = envelope.payloadAsJsonObject().getBoolean(ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY, false);
        final JsonObject jsonObject = getAdvocateRole(of(caseUrn), userId, envelope.metadata(), caseId, activeProsecutingAssignmentsOnly);

        return envelopeFrom(
                envelope.metadata(),
                jsonObject);
    }

    @Handles("advocate.query.role-in-case-by-caseid")
    public JsonEnvelope findAdvocatesRoleInCaseByCaseId(final JsonEnvelope envelope) {
        final String userId = envelope.metadata().userId().orElse(null);
        final UUID caseId = fromString(envelope.payloadAsJsonObject().getString(CASE_ID));

        final JsonObject jsonObject = getAdvocateRole(empty(), userId, envelope.metadata(), caseId, false);

        return envelopeFrom(
                envelope.metadata(),
                jsonObject);
    }

    @SuppressWarnings("squid:S2259")
    @Handles("defence.query.hearings-timeline")
    public JsonEnvelope getCaseAndApplicationTimelines(final JsonEnvelope envelope) {
        final String caseId = envelope.payloadAsJsonObject().getString(CASE_ID);
        final List<UUID> applicationIds = getApplicationIds(envelope.payloadAsJsonObject());

        final String advocateRole = getString(envelope.payloadAsJsonObject(), ADVOCATE_ROLE).orElse(null);
        final String userId = envelope.metadata().userId().orElse(null);
        final JsonObject jsonObject = getAdvocateRole(empty(), userId, envelope.metadata(), fromString(caseId), false);
        final JsonArray jsonArray = getJsonArray(jsonObject, AUTHORIZED_DEFENDANT_IDS).orElse(null);
        final List<UUID> defendantIds = jsonArray.stream().map(jsonValue -> fromString(((JsonString) jsonValue).getString())).collect(toList());
        final boolean isDefending = isDefending(advocateRole, getString(jsonObject, IS_ADVOCATE_DEFENDING_OR_PROSECUTING).orElse(null), envelope, fromString(caseId));
        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder();
        final List<HearingSummaries> caseHearingSummaries = getCaseHearingSummaries(isDefending, defendantIds, envelope.metadata(), caseId, applicationIds);
        final List<Hearing> unallocatedHearings = getUnallocatedHearings(envelope.metadata(), caseId, caseHearingSummaries, isDefending, defendantIds, applicationIds);
        of(caseHearingSummaries).ifPresent(caseHearingSummary -> jsonObjectBuilder.add(HEARING_SUMMARIES, listToJsonArrayConverter.convert(caseHearingSummary)));
        of(unallocatedHearings).ifPresent(unallocatedHearing -> jsonObjectBuilder.add(UNALLOCATED_HEARINGS, listToJsonArrayConverter.convert(unallocatedHearing)));
        return envelopeFrom(
                envelope.metadata(),
                jsonObjectBuilder.build());
    }

    @Handles("defence.query.hearings")
    public Envelope<Hearings> getHearings(final JsonEnvelope envelope) {
        final String date = envelope.payloadAsJsonObject().getString("date");
        final String courtCentreId = envelope.payloadAsJsonObject().getString("courtCentreId");
        final Hearings hearings = hearingService.getHearings(envelope.metadata(), date, courtCentreId);
        return envelopeFrom(
                envelope.metadata(),
                hearings);
    }

    @Handles("defence.query.prosecutioncase-defence-caag")
    public Envelope<ProsecutioncaseCaag> queryProsecutioncaseDefenceCaag(final Envelope<SearchCaseByUrn> request) {
        final UUID caseId = defenceQueryService.getCaseId(request.payload().getCaseUrn());
        final String userId = request.metadata().userId().orElse(null);
        final JsonObject roleInCaseJsonObject = getAdvocateRole(empty(), userId, request.metadata(), caseId, false);
        final ProsecutioncaseCaag prosecutioncaseCaag = getProsecutionCaseCaag(request, caseId);
        final SearchCaseByUrn searchCaseByUrn = updateSearchCaseByUrnWithAuthorizedDefendantIds(request, roleInCaseJsonObject, prosecutioncaseCaag.getDefendants());
        final ProsecutioncaseCaag prosecutionCaseDefenceCaag = filterProsecutionCaseForDefenceView(prosecutioncaseCaag, searchCaseByUrn.getAuthorisedDefendantIds(), asList(DEFENCE));
        return envelopeFrom(request.metadata(), prosecutionCaseDefenceCaag);
    }

    @Handles("advocate.query.prosecutioncase-defence")
    public Envelope<Prosecutioncase> queryProsecutioncaseDefence(final Envelope<SearchCaseByUrn> request) throws IOException {
        final UUID caseId = request.payload().getCaseId();
        final String userId = request.metadata().userId().orElse(null);
        final JsonObject roleInCaseJsonObject = getAdvocateRole(empty(), userId, request.metadata(), caseId, false);
        final SearchCaseByUrn searchCaseByUrn = updateSearchCaseByUrnWithAuthorizedDefendantIds(request, roleInCaseJsonObject, null);

        final Prosecutioncase prosecutioncase = filterProsecutionCaseForDefenceView(getProsecutionCase(request, caseId), searchCaseByUrn.getAuthorisedDefendantIds());
        return envelopeFrom(request.metadata(), prosecutioncase);
    }

    @Handles("advocate.query.prosecutioncase-prosecutor-caag")
    public Envelope<ProsecutioncaseCaag> queryProsecutioncaseProsecutorCaag(final Envelope<SearchCaseByUrn> request) {
        final UUID caseId = defenceQueryService.getCaseId(request.payload().getCaseUrn());
        return envelopeFrom(request.metadata(), getProsecutionCaseCaag(request, caseId));
    }

    @Handles("advocate.query.prosecutioncase-prosecutor")
    public Envelope<Prosecutioncase> queryProsecutioncaseProsecutor(final Envelope<SearchCaseByUrn> request) throws IOException {
        return envelopeFrom(request.metadata(), getProsecutionCase(request, request.payload().getCaseId()));
    }

    @Handles("advocate.query.expired-prosecutor-assignments")
    public Envelope<ExpiredProsecutorAssignments> queryExpiredProsecutorAssignments(final JsonEnvelope envelope) {
        final List<ProsecutionAdvocateAccess> expiredProsecutorAssignmentList;
        if (envelope.payloadAsJsonObject().containsKey(EXPIRED_ASSIGNMENTS_SELECT_COUNT)) {
            expiredProsecutorAssignmentList = advocateAssignmentRepository.findExpiredCaseAssignments(Integer.valueOf(envelope.payloadAsJsonObject().getString(EXPIRED_ASSIGNMENTS_SELECT_COUNT)));
        } else {
            expiredProsecutorAssignmentList = advocateAssignmentRepository.findExpiredCaseAssignments();
        }

        return envelopeFrom(envelope.metadata(), getExpiredProsecutorAssignmentsView(expiredProsecutorAssignmentList));
    }

    @Handles("advocate.query.expired-prosecutor-organisation-assignments")
    public Envelope<ExpiredProsecutorOrganisationAssignments> queryExpiredProsecutorOrganisationAssignments(final JsonEnvelope envelope) {
        final List<ProsecutionOrganisationAccess> expiredProsecutorAssignmentList;
        if (envelope.payloadAsJsonObject().containsKey(EXPIRED_ASSIGNMENTS_SELECT_COUNT)) {
            expiredProsecutorAssignmentList = organisationAccessRepository.findExpiredCaseAssignments(Integer.valueOf(envelope.payloadAsJsonObject().getString(EXPIRED_ASSIGNMENTS_SELECT_COUNT)));
        } else {
            expiredProsecutorAssignmentList = organisationAccessRepository.findExpiredCaseAssignments();
        }
        return envelopeFrom(envelope.metadata(), getExpiredOrganisationAssignmentsView(expiredProsecutorAssignmentList));
    }

    private ProsecutioncaseCaag getProsecutionCaseCaag(final Envelope<SearchCaseByUrn> request, final UUID caseId) {
        final JsonObject prosecutionCaseJson = progressionService.getProsecutionCaseDetailsForCaag(request.metadata(), caseId);
        final ProsecutioncaseCaag prosecutioncaseCaag = removeMigrationSourceSystem(prosecutionCaseJson);

        return enrichProsecutionCaseCaag(request.metadata(), prosecutioncaseCaag);
    }

    private ProsecutioncaseCaag removeMigrationSourceSystem(final JsonObject prosecutionCaseJson) {
        try {
            Map<String, Object> objectMap = OBJECT_MAPPER.readValue(prosecutionCaseJson.toString(), new TypeReference<>() {});
            Map<String, Object> caseDetails = OBJECT_MAPPER.convertValue(objectMap.get("caseDetails"), new TypeReference<>() {});
            caseDetails.remove("migrationSourceSystem");
            objectMap.put("caseDetails", caseDetails);
            return OBJECT_MAPPER.convertValue(objectMap, ProsecutioncaseCaag.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    private Prosecutioncase getProsecutionCase(final Envelope<SearchCaseByUrn> request, final UUID caseId) throws IOException {
        final JsonValue prosecutionCaseJson = progressionService.getProsecutionCaseDetailsAsJson(request.metadata(), caseId);
        return OBJECT_MAPPER.readValue(prosecutionCaseJson.toString(), Prosecutioncase.class);
    }

    private ProsecutioncaseCaag enrichProsecutionCaseCaag(final Metadata metadata, final ProsecutioncaseCaag prosecutioncaseCaag) {
        final JsonObject prosecutionCaseJson = progressionService.getProsecutionCaseDetails(metadata, fromString(prosecutioncaseCaag.getCaseId()));
        final ProsecutionCase prosecutionCase = jsonObjectToObjectConverter.convert(prosecutionCaseJson.getJsonObject(PROSECUTION_CASE), ProsecutionCase.class);
        return ProsecutioncaseCaag.prosecutioncaseCaag()
                .withValuesFrom(prosecutioncaseCaag)
                .withDefendants(getEnrichedDefendants(prosecutioncaseCaag.getDefendants(), prosecutionCase.getDefendants()))
                .build();
    }

    public List<Defendants> getEnrichedDefendants(final List<Defendants> caagDefendants, final List<Defendant> caseDefendants) {
        return caagDefendants.stream()
                .map(defendant -> Defendants.defendants()
                        .withValuesFrom(defendant)
                        .withAssociatedPersons(findAssociatedPersons(caseDefendants, defendant.getId()))
                        .build())
                .collect(toList());
    }

    private List<AssociatedPerson> findAssociatedPersons(final List<Defendant> caseDefendants, final UUID defendantId) {
        return caseDefendants.stream()
                .filter(defendant -> defendant.getId().equals(defendantId))
                .filter(defendant -> nonNull(defendant.getAssociatedPersons()))
                .findAny().map(Defendant::getAssociatedPersons).orElse(null);
    }

    private ExpiredProsecutorOrganisationAssignments getExpiredOrganisationAssignmentsView(final List<ProsecutionOrganisationAccess> expiredProsecutorAssignmentList) {
        final List<OrganisationAssignment> organisationAssignments = expiredProsecutorAssignmentList.stream()
                .map(prosecutionOrganisationAccess -> OrganisationAssignment.organisationAssignment()
                        .withAssignmentExpiryDate(prosecutionOrganisationAccess.getAssignmentExpiryDate())
                        .withAssigneeUserId(prosecutionOrganisationAccess.getAssigneeDetails().getUserId())
                        .withCaseId(prosecutionOrganisationAccess.getCaseId())
                        .withAssignorUserId(prosecutionOrganisationAccess.getAssignorDetails().getUserId())
                        .withAssignorOrganisationId(prosecutionOrganisationAccess.getAssignorOrganisationId())
                        .withAssigneeOrganisationId(prosecutionOrganisationAccess.getId().getAssigneeOrganisationId())
                        .withAssigneeOrganisationName(prosecutionOrganisationAccess.getAssigneeOrganisationName())
                        .withAssignorOrganisationName(prosecutionOrganisationAccess.getAssignorOrganisationName())
                        .build())
                .collect(Collectors.toList());
        return ExpiredProsecutorOrganisationAssignments.expiredProsecutorOrganisationAssignments().withOrganisationAssignments(organisationAssignments).build();
    }

    private ExpiredProsecutorAssignments getExpiredProsecutorAssignmentsView(final List<ProsecutionAdvocateAccess> expiredProsecutorAssignmentList) {
        final List<ProsecutorAssignment> prosecutorAssignments = expiredProsecutorAssignmentList.stream()
                .map(prosecutionAdvocateAccess -> ProsecutorAssignment.prosecutorAssignment()
                        .withAssignmentExpiryDate(prosecutionAdvocateAccess.getAssignmentExpiryDate())
                        .withAssigneeUserId(prosecutionAdvocateAccess.getAssigneeDetails().getUserId())
                        .withCaseId(prosecutionAdvocateAccess.getCaseId())
                        .withAssignorUserId(prosecutionAdvocateAccess.getAssignorDetails().getUserId())
                        .withAssignorOrganisationId(prosecutionAdvocateAccess.getAssignorOrganisationId())
                        .build())
                .collect(Collectors.toList());
        return ExpiredProsecutorAssignments.expiredProsecutorAssignments().withProsecutorAssignments(prosecutorAssignments).build();
    }

    private JsonObject getAdvocateRole(Optional<String> urn, String userId, Metadata metadata, final UUID caseId, final boolean activeProsecutingAssignmentsOnly) {
        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder().add(ASSIGNEE_ID, userId);
        urn.ifPresent(urnStr -> jsonObjectBuilder.add(CASE_URN, urnStr));
        final Organisation userOrganisation = userGroupService.getOrganisationDetailsForUser(fromString(userId), metadata, requester);
        final UUID userOrganisationId = nonNull(userOrganisation) ? userOrganisation.getOrgId() : null;
        final List<String> authorizedDefendantIds = getAuthorizedDefendantIds(caseId, userOrganisationId, fromString(userId));
        final boolean isDefending = isNotEmpty(authorizedDefendantIds);
        boolean isProsecuting;
        if (activeProsecutingAssignmentsOnly) {
            isProsecuting = isProsecutingTheCaseWithActiveAssignments(userId, caseId, userOrganisationId, metadata);
        } else {
            isProsecuting = isProsecutingTheCase(userId, caseId, userOrganisationId, metadata);
        }

        if (isProsecuting && isDefending) {
            jsonObjectBuilder.add(IS_ADVOCATE_DEFENDING_OR_PROSECUTING, BOTH);
        } else if (isProsecuting) {
            jsonObjectBuilder.add(IS_ADVOCATE_DEFENDING_OR_PROSECUTING, PROSECUTING);
        } else if (isDefending) {
            jsonObjectBuilder.add(IS_ADVOCATE_DEFENDING_OR_PROSECUTING, DEFENDING);
        }
        final JsonArrayBuilder defendantIdArrayBuilder = createArrayBuilder();
        authorizedDefendantIds.forEach(defendantIdArrayBuilder::add);
        jsonObjectBuilder.add(AUTHORIZED_DEFENDANT_IDS, defendantIdArrayBuilder.build());
        return jsonObjectBuilder.build();
    }

    private ProsecutioncaseCaag filterProsecutionCaseForDefenceView(final ProsecutioncaseCaag prosecutioncaseCaag, final List<UUID> authorisedDefendantList, final List<String> userGroups) {
        return ProsecutioncaseCaag.prosecutioncaseCaag()
                .withCaseId(prosecutioncaseCaag.getCaseId())
                .withDefendants(filterRestrictedResultAndPrompts(filterDefendantsForCaag(prosecutioncaseCaag.getDefendants(), authorisedDefendantList), userGroups))
                .withCaseDetails(prosecutioncaseCaag.getCaseDetails())
                .withLinkedApplications(getLinkedApplicationsSummaryForCaagWithFilteredDefendants(prosecutioncaseCaag.getLinkedApplications(), authorisedDefendantList))
                .withProsecutorDetails(prosecutioncaseCaag.getProsecutorDetails())
                .build();
    }

    private Prosecutioncase filterProsecutionCaseForDefenceView(final Prosecutioncase prosecutioncase, final List<UUID> authorisedDefendantList) {
        return Prosecutioncase.prosecutioncase()
                .withProsecutionCase(getProsecutionCaseWithFilteredDefendants(prosecutioncase.getProsecutionCase(), authorisedDefendantList))
                .withActiveCourtOrders(getFilteredActiveCourtOrders(prosecutioncase.getActiveCourtOrders(), authorisedDefendantList))
                .withHearingsAtAGlance(getFilteredHearingsAtAGlance(prosecutioncase.getHearingsAtAGlance(), authorisedDefendantList))
                .withLinkedApplicationsSummary(getLinkedApplicationsSummaryWithFilteredDefendants(prosecutioncase.getLinkedApplicationsSummary(), authorisedDefendantList))
                .withRelatedCases(getFilteredRelatedCases(prosecutioncase.getRelatedCases(), authorisedDefendantList))
                .build();
    }

    private HearingsAtAGlance getFilteredHearingsAtAGlance(final HearingsAtAGlance hearingsAtAGlance, final List<UUID> authorisedDefendantList) {
        if (isNull(hearingsAtAGlance)) {
            return null;
        }
        return HearingsAtAGlance.hearingsAtAGlance()
                .withHearings(getFilteredHearings(hearingsAtAGlance.getHearings(), authorisedDefendantList))
                .withCourtApplications(filterCourtApplications(hearingsAtAGlance.getCourtApplications(), authorisedDefendantList))
                .withDefendantHearings(filterDefendantHearings(hearingsAtAGlance.getDefendantHearings(), authorisedDefendantList))
                .withId(hearingsAtAGlance.getId())
                .withLatestHearingJurisdictionType(hearingsAtAGlance.getLatestHearingJurisdictionType())
                .withProsecutionCaseIdentifier(hearingsAtAGlance.getProsecutionCaseIdentifier())
                .build();
    }

    private List<uk.gov.justice.core.courts.CourtApplication> filterCourtApplications(final List<uk.gov.justice.core.courts.CourtApplication> courtApplications, final List<UUID> authorisedDefendantList) {
        if (isNull(courtApplications)) {
            return null;
        }
        return courtApplications.stream()
                .filter(courtApplication -> isApplicationRelatedWithOneOfTheDefendants(courtApplication, authorisedDefendantList))
                .collect(toList());
    }


    private boolean isApplicationRelatedWithOneOfTheDefendants(final uk.gov.justice.core.courts.CourtApplication courtApplication, final List<UUID> authorisedDefendantList) {
        return (nonNull(courtApplication.getApplicant()) && isApplicantRelatedWithOneOfTheDefendants(courtApplication.getApplicant().getId(), authorisedDefendantList)) ||
                isApplicationRespondentsRelatedWithOneOfTheDefendants(courtApplication.getRespondents(), authorisedDefendantList) ||
                (nonNull(courtApplication.getSubject()) && isSubjectRelatedWithOneOfTheDefendants(courtApplication.getSubject().getId(), authorisedDefendantList));
    }

    private boolean isApplicationRespondentsRelatedWithOneOfTheDefendants(final List<CourtApplicationParty> respondents, final List<UUID> authorisedDefendantList) {
        if (isNull(respondents)) {
            return false;
        }
        return respondents.stream()
                .map(CourtApplicationParty::getId)
                .anyMatch(authorisedDefendantList::contains);
    }

    private List<uk.gov.justice.cps.defence.progression.Hearing> getFilteredHearings(final List<uk.gov.justice.cps.defence.progression.Hearing> hearings, final List<UUID> authorisedDefendantList) {
        if (isNull(hearings)) {
            return null;
        }
        return hearings.stream()
                .map(hearing -> uk.gov.justice.cps.defence.progression.Hearing.hearing()
                        .withValuesFrom(hearing)
                        .withDefendants(filterDefendantsForHearing(hearing.getDefendants(), authorisedDefendantList))
                        .build())
                .filter(hearing -> isNotEmpty(hearing.getDefendants()))
                .collect(toList());
    }

    private List<uk.gov.justice.cps.defence.progression.HearingDefendant> filterDefendantsForHearing(final List<uk.gov.justice.cps.defence.progression.HearingDefendant> defendants, final List<UUID> authorisedDefendantList) {
        if (isNull(defendants)) {
            return null;
        }
        return defendants.stream()
                .filter(defendant -> authorisedDefendantList.contains(defendant.getId()))
                .collect(toList());
    }

    private List<DefendantHearings> filterDefendantHearings(final List<DefendantHearings> defendantHearings, final List<UUID> authorisedDefendantList) {
        if (isNull(defendantHearings)) {
            return null;
        }
        return defendantHearings.stream()
                .filter(defendantHearing -> authorisedDefendantList.contains(defendantHearing.getDefendantId()))
                .collect(toList());
    }

    private List<RelatedCase> getFilteredRelatedCases(final List<RelatedCase> relatedCases, final List<UUID> authorisedDefendantList) {
        if (isNull(relatedCases)) {
            return null;
        }
        return relatedCases.stream()
                .filter(relatedCase -> authorisedDefendantList.contains(relatedCase.getMasterDefendantId()))
                .collect(toList());
    }

    private List<CourtOrder> getFilteredActiveCourtOrders(final List<CourtOrder> activeCourtOrders, final List<UUID> authorisedDefendantList) {
        if (isNull(activeCourtOrders)) {
            return null;
        }
        return activeCourtOrders.stream()
                .filter(activeCourtOrder -> authorisedDefendantList.contains(activeCourtOrder.getMasterDefendantId()))
                .collect(toList());
    }

    private ProsecutionCase getProsecutionCaseWithFilteredDefendants(final ProsecutionCase prosecutionCase, final List<UUID> authorisedDefendantList) {
        if (isNull(prosecutionCase)) {
            return null;
        }
        return ProsecutionCase.prosecutionCase()
                .withValuesFrom(prosecutionCase)
                .withDefendants(filterDefendants(prosecutionCase.getDefendants(), authorisedDefendantList))
                .build();

    }

    private boolean isDefending(String inputRole, String roleFromDB, final JsonEnvelope envelope, final UUID caseId) {

        final Optional<String> isNonCps = isNonCPSProsecutor(envelope, caseId);

        if (isNonCps.isPresent()) {
            if (ORGANISATION_MIS_MATCH.equals(isNonCps.get())) {
                throw new ForbiddenRequestException(format(USER_HAS_NO_PERMISSION_FOR_THE_S_VIEW, inputRole));
            }
            return false;
        } else {
            if ((inputRole.equals(roleFromDB) || BOTH.equals(roleFromDB))) {
                return inputRole.equals(DEFENDING);
            }
            throw new ForbiddenRequestException(format(USER_HAS_NO_PERMISSION_FOR_THE_S_VIEW, inputRole));
        }
    }

    @SuppressWarnings("squid:S3655")
    private Optional<String> isNonCPSProsecutor(JsonEnvelope request, final UUID caseId) {
        final UUID userId = request.metadata().userId().isPresent() ? fromString(request.metadata().userId().get()) : null;
        final UUID prosecutorId = progressionService.getProsecutorOrProsecutionCaseAuthorityID(request.metadata(), caseId);
        final Optional<JsonObject> prosecutorJsonObjectOptional = referenceDataService.getProsecutor(request.metadata(), prosecutorId);
        return usersGroupQueryService.validateNonCPSUserOrg(request.metadata(), userId, NON_CPS_PROSECUTORS, prosecutorJsonObjectOptional.get().getString(SHORT_NAME));
    }

    private List<UUID> getMasterDefendantIds(List<Person> persons) {
        return ofNullable(persons).orElse(emptyList()).stream()
                .filter(applicant -> nonNull(applicant.getMasterDefendantId()))
                .map(Person::getMasterDefendantId).collect(toList());
    }


    private List<HearingSummaries> getCaseHearingSummaries(boolean isDefending, List<UUID> defendantIds, final Metadata metadata, String caseId, List<UUID> applicationIds) {
        final Timeline timeline = hearingService.getHearingTimelineByCaseId(metadata, fromString(caseId));
        final List<HearingSummaries> caseHearingSummaries = new ArrayList<>();
        if (isDefending) {
            prepareHearingSummariesForDefending(defendantIds, applicationIds, timeline, caseHearingSummaries);
        } else {
            caseHearingSummaries.addAll(timeline.getHearingSummaries());
        }

        return caseHearingSummaries;
    }

    private void prepareHearingSummariesForDefending(final List<UUID> defendantIds, final List<UUID> applicationIds, final Timeline timeline, final List<HearingSummaries> caseHearingSummaries) {
        timeline.getHearingSummaries().forEach(hearingSummary -> {

            if (isNotEmpty(hearingSummary.getDefendants())) {
                prepareHearingSummaryForCase(defendantIds, caseHearingSummaries, hearingSummary);
            } else {
                prepareHearingSummaryFoApplication(defendantIds, applicationIds, caseHearingSummaries, hearingSummary);
            }

        });
    }

    private void prepareHearingSummaryFoApplication(final List<UUID> defendantIds, final List<UUID> applicationIds, final List<HearingSummaries> caseHearingSummaries, final HearingSummaries hearingSummary) {
        final List<Application> applications = hearingSummary.getApplications().stream()
                .filter(application -> applicationIds.contains(application.getApplicationId()))
                .collect(toList());
        if (!disjoint(defendantIds, getMasterDefendantIdsFromApplication(applications))) {
            caseHearingSummaries.add(hearingSummary);
        }
    }

    private void prepareHearingSummaryForCase(final List<UUID> defendantIds, final List<HearingSummaries> caseHearingSummaries, final HearingSummaries hearingSummary) {
        final List<uk.gov.moj.cpp.hearing.Defendant> defendants = ofNullable(hearingSummary.getDefendants()).orElse(emptyList()).stream()
                .filter(defendant -> defendantIds.contains(defendant.getId()))
                .collect(toList());
        if (isNotEmpty(defendants)) {
            final HearingSummaries updatedHearingSummary = HearingSummaries.hearingSummaries().withValuesFrom(hearingSummary)
                    .withDefendants(defendants)
                    .build();
            caseHearingSummaries.add(updatedHearingSummary);
        }
    }

    private List<UUID> getMasterDefendantIdsFromApplication(final List<Application> applications) {
        final List<Person> applicants = applications.stream().flatMap(application -> application.getApplicants().stream()).collect(toList());
        final List<Person> respondents = applications.stream().flatMap(application -> application.getRespondents().stream()).collect(toList());
        final List<Person> subjects = applications.stream().flatMap(application -> application.getSubjects().stream()).collect(toList());
        final List<UUID> masterDefendantIds = getMasterDefendantIds(applicants);
        masterDefendantIds.addAll(getMasterDefendantIds(respondents));
        masterDefendantIds.addAll(getMasterDefendantIds(subjects));
        return masterDefendantIds;
    }

    private List<UUID> getApplicationIds(JsonObject jsonObject) {
        final String applicationIds = getString(jsonObject, APPLICATION_ID).orElse(null);
        return nonNull(applicationIds) ? stream(applicationIds.split(COMMA)).map(UUID::fromString).collect(toList()) : emptyList();
    }

    private List<UUID> getAllocatedHearingsId(List<HearingSummaries> caseHearingSummaries) {
        return caseHearingSummaries.stream().map(HearingSummaries::getHearingId).collect(toList());
    }

    private List<Hearing> getUnallocatedHearings(final Metadata metadata, final String caseId, List<HearingSummaries> caseHearingSummaries, final boolean isDefending, final List<UUID> defendantIds, final List<UUID> applicationIds) {
        final List<UUID> allocatedHearings = getAllocatedHearingsId(caseHearingSummaries);

        final List<Hearing> hearingList = listingService.getHearings(metadata, caseId).stream()
                .filter(hearing -> !allocatedHearings.contains(hearing.getId()))
                .collect(toList());

        if (isDefending) {
            return hearingList.stream().filter(hearing -> {
                boolean isPersonMatched = ofNullable(hearing.getListedCases()).orElse(emptyList()).stream()
                        .flatMap(listedCase -> listedCase.getDefendants().stream())
                        .map(uk.gov.justice.listing.events.Defendant::getId)
                        .anyMatch(defendantIds::contains);
                if (!isPersonMatched) {
                    isPersonMatched = ofNullable(hearing.getCourtApplications()).orElse(emptyList()).stream()
                            .map(CourtApplication::getId)
                            .anyMatch(applicationIds::contains);
                }

                return isPersonMatched;
            }).collect(toList());

        } else {
            return hearingList;
        }


    }

    private List<LinkedApplications> getLinkedApplicationsSummaryForCaagWithFilteredDefendants(final List<LinkedApplications> linkedApplications, final List<UUID> authorisedDefendantList) {
        if (isNull(linkedApplications)) {
            return null;
        }
        return linkedApplications.stream()
                .filter(application -> isApplicationRelatedWithOneOfTheDefendants(application, authorisedDefendantList))
                .collect(toList());
    }

    private List<ApplicationSummary> getLinkedApplicationsSummaryWithFilteredDefendants(final List<ApplicationSummary> linkedApplicationsSummaries, final List<UUID> authorisedDefendantList) {
        if (isNull(linkedApplicationsSummaries)) {
            return null;
        }
        return linkedApplicationsSummaries.stream()
                .filter(linkedApplicationsSummary -> isApplicationRelatedWithOneOfTheDefendants(linkedApplicationsSummary, authorisedDefendantList))
                .collect(toList());
    }

    private boolean isApplicationRelatedWithOneOfTheDefendants(final LinkedApplications linkedApplication, final List<UUID> authorisedDefendantList) {
        return isApplicantRelatedWithOneOfTheDefendants(linkedApplication.getApplicantId(), authorisedDefendantList) ||
                isRespondentsRelatedWithOneOfTheDefendants(linkedApplication.getRespondentIds(), authorisedDefendantList) ||
                isSubjectRelatedWithOneOfTheDefendants(linkedApplication.getSubjectId(), authorisedDefendantList);
    }

    private boolean isApplicationRelatedWithOneOfTheDefendants(final ApplicationSummary linkedApplicationsSummary, final List<UUID> authorisedDefendantList) {
        return isApplicantRelatedWithOneOfTheDefendants(linkedApplicationsSummary.getApplicantId(), authorisedDefendantList) ||
                isRespondentsRelatedWithOneOfTheDefendants(linkedApplicationsSummary.getRespondentIds(), authorisedDefendantList) ||
                isSubjectRelatedWithOneOfTheDefendants(linkedApplicationsSummary.getSubjectId(), authorisedDefendantList);
    }

    private boolean isSubjectRelatedWithOneOfTheDefendants(final UUID subjectId, final List<UUID> authorisedDefendantList) {
        if (isNull(subjectId)) {
            return false;
        }
        return authorisedDefendantList.contains(subjectId);
    }

    private boolean isRespondentsRelatedWithOneOfTheDefendants(final List<UUID> respondentIds, final List<UUID> authorisedDefendantList) {
        if (isNull(respondentIds)) {
            return false;
        }
        return respondentIds.stream().anyMatch(authorisedDefendantList::contains);
    }

    private boolean isApplicantRelatedWithOneOfTheDefendants(final UUID applicantId, final List<UUID> authorisedDefendantList) {
        if (isNull(applicantId)) {
            return false;
        }
        return authorisedDefendantList.contains(applicantId);
    }

    @SuppressWarnings("squid:S1166")
    private List<String> getAuthorizedDefendantIds(final UUID caseId, final UUID orgId, final UUID userId) {
        // check if the organisation is associated
        final List<DefenceAssociation> defenceAssociations = defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId);
        final List<String> authorizedDefendantIds = defenceAssociations.stream()
                .filter(defenceAssociation -> isNull(defenceAssociation.getEndDate()))
                .map(defenceAssociation -> defenceAssociation.getDefenceAssociationDefendant().getDefendantId().toString())
                .collect(toList());
        if (isEmpty(authorizedDefendantIds)) {
            try {
                final List<DefenceGrantAccess> defenceGrantAccesses = getAccessGrants(caseId, userId);
                if (isNamedAdvocateAccessGranted(defenceGrantAccesses)) {
                    authorizedDefendantIds.addAll(getDefendantsAsList(defenceGrantAccesses));
                }
            } catch (NoResultException exception) {
                return emptyList();
            }
        }
        return authorizedDefendantIds;
    }

    private boolean isNamedAdvocateAccessGranted(final List<DefenceGrantAccess> defenceGrantAccesses) {
        return isNotEmpty(defenceGrantAccesses);
    }

    private List<DefenceGrantAccess> getAccessGrants(final UUID caseId, final UUID userId) {
        return defenceGrantAccessRepository.findByGranteeAndCaseId(caseId, userId);
    }

    private Collection<String> getDefendantsAsList(final List<DefenceGrantAccess> defenceGrantAccesses) {
        return defenceGrantAccesses.stream().map(defenceGrantAccess -> defenceGrantAccess.getDefenceClient().getDefendantId().toString()).collect(toList());
    }

    private boolean isProsecutingTheCase(final String userId, final UUID caseId, final UUID orgId, final Metadata metadata) {
        final List<String> userGroupList = userGroupService.getGroupNamesForUser(fromString(userId), metadata, requester);
        final List<AssignmentUserDetails> prosecutionOrganisationAccesses = new ArrayList<>();
        if (userGroupList.contains(DEFENCE_LAWYERS)) {
            prosecutionOrganisationAccesses.addAll(convertOrganisationAccessToUserList(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseId, orgId)));
        }
        prosecutionOrganisationAccesses.addAll(convertAdvocateAccessToUserList(advocateAssignmentRepository.findByCaseIdAndAssigneeId(caseId, fromString(userId))));
        return isNotEmpty(prosecutionOrganisationAccesses);
    }

    private boolean isProsecutingTheCaseWithActiveAssignments(final String userId, final UUID caseId, final UUID orgId, final Metadata metadata) {
        final List<String> userGroupList = userGroupService.getGroupNamesForUser(fromString(userId), metadata, requester);
        final List<AssignmentUserDetails> prosecutionOrganisationAccesses = new ArrayList<>();
        if (userGroupList.contains(DEFENCE_LAWYERS)) {
            prosecutionOrganisationAccesses.addAll(convertOrganisationAccessToUserList(organisationAccessRepository.findActiveByCaseIdAndAssigneeOrganisationId(caseId, orgId)));
        }
        prosecutionOrganisationAccesses.addAll(convertAdvocateAccessToUserList(advocateAssignmentRepository.findActiveByCaseIdAndAssigneeId(caseId, fromString(userId))));
        return isNotEmpty(prosecutionOrganisationAccesses);
    }

    private Collection<? extends AssignmentUserDetails> convertOrganisationAccessToUserList(final List<ProsecutionOrganisationAccess> prosecutionOrganisationAccesses) {
        if (isEmpty(prosecutionOrganisationAccesses)) {
            return emptyList();
        }
        return prosecutionOrganisationAccesses.stream().map(ProsecutionOrganisationAccess::getAssigneeDetails).collect(toList());
    }

    private Collection<? extends AssignmentUserDetails> convertAdvocateAccessToUserList(final List<ProsecutionAdvocateAccess> prosecutionAdvocateAccesses) {
        if (isEmpty(prosecutionAdvocateAccesses)) {
            return emptyList();
        }
        return prosecutionAdvocateAccesses.stream().map(ProsecutionAdvocateAccess::getAssigneeDetails).collect(toList());
    }

    private ProsecutionCaseAssigneeVO toProsecutionCaseAssigneeVO(ProsecutionOrganisationAccess prosecutionOrganisationAccess, ProsecutionAdvocateAccess prosecutionAdvocateAccess) {
        return new ProsecutionCaseAssigneeVO(prosecutionAdvocateAccess.getAssigneeDetails().getUserId(),
                format(NAME_STR, prosecutionAdvocateAccess.getAssigneeDetails().getFirstName(), prosecutionAdvocateAccess.getAssigneeDetails().getLastName()),
                prosecutionOrganisationAccess.getId().getAssigneeOrganisationId(),
                prosecutionOrganisationAccess.getAssigneeOrganisationName(),
                format(NAME_STR_STATUS, ACCESS_GRANTED_BY, prosecutionAdvocateAccess.getAssignorDetails().getFirstName(), prosecutionAdvocateAccess.getAssignorDetails().getLastName()),
                prosecutionOrganisationAccess.getRepresentationType().name(),
                prosecutionOrganisationAccess.getRepresenting(),
                prosecutionAdvocateAccess.getAssignedDate());
    }

    private ProsecutionCaseAssigneeVO toProsecutionCaseAssigneeVO(ProsecutionOrganisationAccess prosecutionOrganisationAccess) {
        return new ProsecutionCaseAssigneeVO(prosecutionOrganisationAccess.getAssigneeDetails().getUserId(),
                format(NAME_STR, prosecutionOrganisationAccess.getAssigneeDetails().getFirstName(), prosecutionOrganisationAccess.getAssigneeDetails().getLastName()),
                prosecutionOrganisationAccess.getId().getAssigneeOrganisationId(),
                prosecutionOrganisationAccess.getAssigneeOrganisationName(),
                format(NAME_STR_STATUS, ACCESS_GRANTED_BY, prosecutionOrganisationAccess.getAssignorDetails().getFirstName(), prosecutionOrganisationAccess.getAssignorDetails().getLastName()),
                prosecutionOrganisationAccess.getRepresentationType().name(),
                prosecutionOrganisationAccess.getRepresenting(),
                prosecutionOrganisationAccess.getAssignedDate());
    }

    private SearchCaseByUrn updateSearchCaseByUrnWithAuthorizedDefendantIds(final Envelope<SearchCaseByUrn> request, JsonObject roleInCaseJsonObject, final List<Defendants> defendants) {
        final Map<UUID, UUID> defendantMasterDefMap = new HashMap<>();
        ofNullable(defendants).ifPresent(defendantsList -> defendantsList.forEach(def -> defendantMasterDefMap.put(def.getId(), def.getMasterDefendantId())));
        final List<UUID> authorizedDefendantIds = roleInCaseJsonObject.getJsonArray(AUTHORIZED_DEFENDANT_IDS).stream()
                .map(id -> ((JsonString) id).getString())
                .map(UUID::fromString)
                .collect(toList());
        if (!defendantMasterDefMap.isEmpty()) {
            final List<UUID> masterDefendantIds = authorizedDefendantIds.stream()
                    .map(defendantMasterDefMap::get)
                    .filter(Objects::nonNull)
                    .toList();
            if (isNotEmpty(masterDefendantIds)) {
                authorizedDefendantIds.addAll(masterDefendantIds);
            }
        }

        return SearchCaseByUrn.searchCaseByUrn().withValuesFrom(request.payload())
                .withAuthorisedDefendantIds(authorizedDefendantIds)
                .build();
    }

    private List<Defendants> filterDefendantsForCaag(final List<Defendants> defendants, final List<UUID> authorisedDefendantList) {
        return defendants.stream()
                .filter(defendant -> authorisedDefendantList.contains(defendant.getId()))
                .collect(toList());
    }


    private List<Defendant> filterDefendants(final List<Defendant> defendants, final List<UUID> authorisedDefendantList) {
        return defendants.stream()
                .filter(defendant -> authorisedDefendantList.contains(defendant.getId()))
                .collect(toList());
    }

    private List<Defendants> filterRestrictedResultAndPrompts(final List<Defendants> defendants, final List<String> userGroups) {
        if (isNotEmpty(userGroups)) {
            defendants.stream()
                    .filter(d -> isNotEmpty(d.getCaagDefendantOffences()))
                    .forEach(d -> d.getCaagDefendantOffences().stream()
                            .filter(o -> isNotEmpty(o.getCaagResults()))
                            .forEach(o -> o.getCaagResults()
                                    .removeIf(r -> isNotEmpty(r.getUsergroups()) && r.getUsergroups().containsAll(userGroups))));

            defendants.stream()
                    .filter(d -> isNotEmpty(d.getCaagDefendantOffences()))
                    .forEach(d -> d.getCaagDefendantOffences().stream()
                            .filter(o -> isNotEmpty(o.getCaagResults()))
                            .forEach(o -> o.getCaagResults().stream()
                                    .filter(c -> isNotEmpty(c.getCaagResultPrompts()))
                                    .forEach(c -> c.getCaagResultPrompts().removeIf(p -> isNotEmpty(p.getUsergroups()) && p.getUsergroups().containsAll(userGroups)))));

            defendants.stream()
                    .filter(d -> isNotEmpty(d.getDefendantCaseJudicialResults()))
                    .forEach(d -> d.getDefendantCaseJudicialResults()
                            .removeIf(r -> isNotEmpty(r.getUsergroups()) && r.getUsergroups().containsAll(userGroups)));

            defendants.stream()
                    .filter(d -> isNotEmpty(d.getDefendantCaseJudicialResults()))
                    .forEach(d -> d.getDefendantCaseJudicialResults().stream()
                            .filter(r -> isNotEmpty(r.getJudicialResultPrompts()))
                            .forEach(r -> r.getJudicialResultPrompts()
                                    .removeIf(p -> isNotEmpty(p.getUsergroups()) && p.getUsergroups().containsAll(userGroups))));

            defendants.stream()
                    .filter(d -> isNotEmpty(d.getDefendantJudicialResults()))
                    .forEach(d -> d.getDefendantJudicialResults()
                            .removeIf(r -> isNotEmpty(r.getUsergroups()) && r.getUsergroups().containsAll(userGroups)));

            defendants.stream()
                    .filter(d -> isNotEmpty(d.getDefendantJudicialResults()))
                    .forEach(d -> d.getDefendantJudicialResults().stream()
                            .filter(r -> isNotEmpty(r.getJudicialResultPrompts()))
                            .forEach(r -> r.getJudicialResultPrompts()
                                    .removeIf(p -> isNotEmpty(p.getUsergroups()) && p.getUsergroups().containsAll(userGroups))));
        } else {
            defendants.stream().forEach(d -> d.getDefendantCaseJudicialResults().clear());
            defendants.stream().forEach(d -> d.getDefendantJudicialResults().clear());
        }
        return defendants;
    }

    private String getUserId(final JsonEnvelope envelope) {
        if (envelope.payloadAsJsonObject().containsKey(USER_ID)) {
            return envelope.payloadAsJsonObject().getString(USER_ID);
        } else {
            return envelope.metadata().userId().orElse(null);
        }
    }

}
