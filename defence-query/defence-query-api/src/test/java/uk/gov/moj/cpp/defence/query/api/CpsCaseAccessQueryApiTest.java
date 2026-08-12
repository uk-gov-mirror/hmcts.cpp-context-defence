package uk.gov.moj.cpp.defence.query.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import uk.gov.justice.core.courts.CourtCentre;
import uk.gov.justice.core.courts.Marker;
import uk.gov.justice.core.courts.ProsecutionCase;
import uk.gov.justice.core.courts.ProsecutionCaseIdentifier;
import uk.gov.justice.cps.defence.Assignees;
import uk.gov.justice.cps.defence.CaseAdvocateAccess;
import uk.gov.justice.cps.defence.Prosecutioncase;
import uk.gov.justice.cps.defence.SearchCaseByUrn;
import uk.gov.justice.cps.defence.caag.CaseDetails;
import uk.gov.justice.cps.defence.caag.ProsecutioncaseCaag;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.common.json.DefaultJsonParser;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory;
import uk.gov.moj.cpp.defence.query.api.service.UsersAndGroupsService;
import uk.gov.moj.cpp.defence.query.hearing.api.Defendants;
import uk.gov.moj.cpp.defence.query.hearing.api.HearingSummary;
import uk.gov.moj.cpp.defence.query.hearing.api.Hearings;
import uk.gov.moj.cpp.defence.query.hearing.api.ProsecutionCaseSummary;
import uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView;
import uk.gov.moj.cpp.defence.query.view.DefenceQueryService;
import uk.gov.moj.cpp.defence.refdata.ProsecutorDetails;
import uk.gov.moj.cpp.defence.service.ProgressionService;
import uk.gov.moj.cpp.defence.service.ReferenceDataService;
import uk.gov.moj.cpp.defence.service.UsersGroupQueryService;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static jakarta.json.JsonValue.NULL;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView.ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY;

@ExtendWith(MockitoExtension.class)
public class CpsCaseAccessQueryApiTest {

    public static final String ADDRESS = "address";
    public static final String ADDRESS_1 = "address1";
    public static final String ADDRESS_2 = "address2";
    public static final String ADDRESS_3 = "address3";
    public static final String ADDRESS_4 = "address4";
    public static final String ADDRESS_POSTCODE = "addressPostcode";
    public static final String ASSIGNEE_NAME = "assigneeName";
    public static final String ASSIGNEE_ORGANISATION_ID = "assigneeOrganisationId";

    public static final String IS_ADVOCATE_DEFENDING_OR_PROSECUTING = "isAdvocateDefendingOrProsecuting";
    public static final String AUTHORIZED_DEFENDANT_IDS = "authorizedDefendantIds";
    public static final String DEFENDING = "defending";
    public static final String PROSECUTING = "prosecuting";
    public static final String CASE_URN = "99AB21233";
    public static final String ASSIGNEES = "assignees";
    public static final String ADDRESS_LINE_1 = "addressLine1";
    public static final String ADDRESS_LINE_2 = "addressLine2";
    public static final String ADDRESS_LINE_3 = "addressLine3";
    public static final String ADDRESS_LINE_4 = "addressLine4";
    public static final String ASSIGNEE_NAME_1 = "assigneeName1";
    public static final String ASSIGNEE_NAME_2 = "assigneeName2";
    public static final String CASE_2_URN = "case2urn";
    public static final String CASE_4_URN = "case4urn";
    public static final String CASE_6_URN = "case6urn";
    public static final String DEFENDANT_4_LAST_NAME = "defendant4LastName";
    public static final String DEFENDANT_5_LAST_NAME = "defendant5LastName";
    public static final String DEFENDANT_4_MIDDLE_NAME = "defendant4MiddleName";
    public static final String DEFENDANT_5_MIDDLE_NAME = "defendant5MiddleName";
    public static final String DEFENDANT_4_FIRST_NAME = "defendant4FirstName";
    public static final String DEFENDANT_3_LAST_NAME = "defendant3LastName";
    public static final String DEFENDANT_5_FIRST_NAME = "defendant5FirstName";
    public static final String DEFENDANT_3_MIDDLE_NAME = "defendant3MiddleName";
    public static final String DEFENDANT_3_FIRST_NAME = "defendant3FirstName";
    public static final String CASE_1_URN = "case1urn";
    public static final String CASE_3_URN = "case3urn";
    public static final String CASE_5_URN = "case5urn";
    public static final String ROOM_2 = "room 2";
    public static final String ROOM_3 = "room 3";
    public static final String ROOM_1 = "room 1";
    public static final String ROOM_7 = "room 7";
    public static final String DEFENCE_QUERY_HEARINGS = "defence.query.hearings";
    public static final String DATE = "date";
    public static final String COURT_CENTRE_ID = "courtCentreId";
    public static final String DEFENDANT_1_FIRST_NAME = "defendant1FirstName";
    public static final String DEFENDANT_1_MIDDLE_NAME = "defendant1MiddleName";
    public static final String DEFENDANT_1_LAST_NAME = "defendant1LastName";
    public static final String DEFENDANT_2_FIRST_NAME = "defendant2FirstName";
    public static final String DEFENDANT_2_MIDDLE_NAME = "defendant2MiddleName";
    public static final String DEFENDANT_2_LAST_NAME = "defendant2LastName";
    public static final String PROSECUTION_AUTHORITY_REFERENCE = "prosecutionAuthorityReference";
    public static final String DEFENDANT_7_FIRST_NAME = "defendant7FirstName";
    public static final String DEFENDANT_7_MIDDLE_NAME = "defendant7MiddleName";
    public static final String DEFENDANT_7_LAST_NAME = "defendant7LastName";
    public static final String ADVOCATE_QUERY_ROLE_IN_CASE = "advocate.query.role-in-case";
    public static final String DEFENCE_QUERY_CASE_CPS_ASSIGNEES = "defence.query.case-cps-assignees";

    private final UUID authorizedDefendantId1 = randomUUID();
    private final UUID authorizedDefendantId2 = randomUUID();
    private final UUID userId = randomUUID();
    private Envelope<SearchCaseByUrn> requestEnvelopeForApiView;

    @InjectMocks
    private CpsCaseAccessQueryApi cpsCaseAccessQueryApi;

    @Mock
    private JsonEnvelope query;

    @Mock
    private Requester requester;

    @Mock
    private UsersAndGroupsService usersAndGroupsService;

    @Mock
    private ProgressionService progressionService;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private CpsCaseAccessQueryView cpsCaseAccessQueryView;

    @Mock
    DefenceQueryService defenceQueryService;

    @Mock
    private Envelope<SearchCaseByUrn> searchCaseByUrnEnvelope;

    @Mock
    private UsersGroupQueryService usersGroupQueryService;

    @Spy
    private final ObjectMapper mapper = new ObjectMapperProducer().objectMapper();

    @Spy
    private final JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(mapper);

    @BeforeEach
    public void setUp() {
        final MetadataBuilder metadataBuilder = stubbedMetadataBuilderDefenceQuery(userId);
        SearchCaseByUrn searchCaseByUrn = getSearchCaseByUrn();
        requestEnvelopeForApiView = Envelope.envelopeFrom(metadataBuilder, searchCaseByUrn);
    }

    @Test
    public void verifyAllPassThroughQueryHandlerMethods() {
        final UUID assigneeOrganisationId1 = randomUUID();
        final UUID assigneeOrganisationId2 = randomUUID();
        final String addressPrefixForOrg1 = "org1";
        final String addressPrefixForOrg2 = "org2";
        final JsonEnvelope responseJsonEnvelopForViewQuery = getMockResponseForAssigneeQuery(assigneeOrganisationId1, assigneeOrganisationId2);
        when(cpsCaseAccessQueryView.getAssignedUsersToTheCase((any(JsonEnvelope.class)))).thenReturn(responseJsonEnvelopForViewQuery);
        when(usersAndGroupsService.getOrganisationDetails(responseJsonEnvelopForViewQuery, assigneeOrganisationId1)).thenReturn(getMockedAddressData(addressPrefixForOrg1));
        when(usersAndGroupsService.getOrganisationDetails(responseJsonEnvelopForViewQuery, assigneeOrganisationId2)).thenReturn(getMockedAddressData(addressPrefixForOrg2));

        final JsonEnvelope enrichedQueryResponseEnvelope = cpsCaseAccessQueryApi.getAssigneesToTheCase(query);

        //Then
        final JsonArray assigneesJsonArray = enrichedQueryResponseEnvelope.payloadAsJsonObject().getJsonArray(ASSIGNEES);
        assertThat(assigneesJsonArray.size(), is(2));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getString(ASSIGNEE_NAME), is(ASSIGNEE_NAME_1));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getString(ASSIGNEE_ORGANISATION_ID), is(assigneeOrganisationId1.toString()));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_1), is(addressPrefixForOrg1 + "-addressLine1"));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_2), is(addressPrefixForOrg1 + "-addressLine2"));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_3), is(addressPrefixForOrg1 + "-addressLine3"));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_4), is(addressPrefixForOrg1 + "-addressLine4"));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_POSTCODE), is(addressPrefixForOrg1 + "-addressPostcode"));

        assertThat(((JsonObject) assigneesJsonArray.get(1)).getString(ASSIGNEE_NAME), is(ASSIGNEE_NAME_2));
        assertThat(((JsonObject) assigneesJsonArray.get(1)).getString(ASSIGNEE_ORGANISATION_ID), is(assigneeOrganisationId2.toString()));
        assertThat(((JsonObject) assigneesJsonArray.get(1)).getJsonObject(ADDRESS).getString(ADDRESS_1), is(addressPrefixForOrg2 + "-addressLine1"));
        assertThat(((JsonObject) assigneesJsonArray.get(1)).getJsonObject(ADDRESS).getString(ADDRESS_2), is(addressPrefixForOrg2 + "-addressLine2"));
        assertThat(((JsonObject) assigneesJsonArray.get(1)).getJsonObject(ADDRESS).getString(ADDRESS_3), is(addressPrefixForOrg2 + "-addressLine3"));
        assertThat(((JsonObject) assigneesJsonArray.get(1)).getJsonObject(ADDRESS).getString(ADDRESS_4), is(addressPrefixForOrg2 + "-addressLine4"));
        assertThat(((JsonObject) assigneesJsonArray.get(1)).getJsonObject(ADDRESS).getString(ADDRESS_POSTCODE), is(addressPrefixForOrg2 + "-addressPostcode"));
    }

    @Test
    public void verifyAllPassThroughQueryHandlerMethodsWhenOrganisationIsMissingInUsersGroups() {
        final UUID assigneeOrganisationId1 = randomUUID();
        final UUID assigneeOrganisationId2 = randomUUID();
        final String addressPrefixForOrg1 = "org1";
        final JsonEnvelope responseJsonEnvelopForViewQuery = getMockResponseForAssigneeQuery(assigneeOrganisationId1, assigneeOrganisationId2);
        when(cpsCaseAccessQueryView.getAssignedUsersToTheCase((any(JsonEnvelope.class)))).thenReturn(responseJsonEnvelopForViewQuery);
        when(usersAndGroupsService.getOrganisationDetails(responseJsonEnvelopForViewQuery, assigneeOrganisationId1)).thenReturn(getMockedAddressData(addressPrefixForOrg1));
        when(usersAndGroupsService.getOrganisationDetails(responseJsonEnvelopForViewQuery, assigneeOrganisationId2)).thenReturn(null);

        final JsonEnvelope enrichedQueryResponseEnvelope = cpsCaseAccessQueryApi.getAssigneesToTheCase(query);

        //Then
        final JsonArray assigneesJsonArray = enrichedQueryResponseEnvelope.payloadAsJsonObject().getJsonArray(ASSIGNEES);
        assertThat(assigneesJsonArray.size(), is(2));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getString(ASSIGNEE_NAME), is(ASSIGNEE_NAME_1));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getString(ASSIGNEE_ORGANISATION_ID), is(assigneeOrganisationId1.toString()));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_1), is(addressPrefixForOrg1 + "-addressLine1"));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_2), is(addressPrefixForOrg1 + "-addressLine2"));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_3), is(addressPrefixForOrg1 + "-addressLine3"));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_4), is(addressPrefixForOrg1 + "-addressLine4"));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getJsonObject(ADDRESS).getString(ADDRESS_POSTCODE), is(addressPrefixForOrg1 + "-addressPostcode"));

        assertThat(((JsonObject) assigneesJsonArray.get(1)).getString(ASSIGNEE_NAME), is(ASSIGNEE_NAME_2));

    }

    private JsonObject getMockedAddressData(final String prefix) {
        return createObjectBuilder()
                .add(ADDRESS_LINE_1, prefix + "-addressLine1")
                .add(ADDRESS_LINE_2, prefix + "-addressLine2")
                .add(ADDRESS_LINE_3, prefix + "-addressLine3")
                .add(ADDRESS_LINE_4, prefix + "-addressLine4")
                .add(ADDRESS_POSTCODE, prefix + "-addressPostcode")
                .build();
    }

    private JsonEnvelope getMockResponseForAssigneeQuery(final UUID assigneeOrganisationId1, final UUID assigneeOrganisationId2) {
        final JsonObject jsonObject = createObjectBuilder()
                .add(ASSIGNEES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(ASSIGNEE_NAME, ASSIGNEE_NAME_1)
                                .add(ASSIGNEE_ORGANISATION_ID, assigneeOrganisationId1.toString())
                                .build())
                        .add(createObjectBuilder()
                                .add(ASSIGNEE_NAME, ASSIGNEE_NAME_2)
                                .add(ASSIGNEE_ORGANISATION_ID, assigneeOrganisationId2.toString())
                                .build())
                        .build())
                .build();

        return envelopeFrom(
                stubbedMetadataBuilder(randomUUID()),
                jsonObject);
    }

    @Test
    public void queryProsecutionCaseDefenceCaag_forDefenceRole() {
        when(query.metadata()).thenReturn(MetadataBuilderFactory.metadataWithDefaults().withName("advocate.query.role-in-case").build());
        when(query.payloadAsJsonObject()).thenReturn(createObjectBuilder().add("ANOTHER_FIELD", "xyz").build());

        when(cpsCaseAccessQueryView.findAdvocatesRoleInCase(
                argThat(arg ->
                        arg.payloadAsJsonObject().containsKey(ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY) &&
                                arg.payloadAsJsonObject().getBoolean(ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY) &&
                                arg.payloadAsJsonObject().containsKey("ANOTHER_FIELD")
                )))
                .thenReturn(getProsecutionCaseDefenceCaagJsonMock(true));

        final Envelope defenceCaagEnvelope = cpsCaseAccessQueryApi.getAdvocateRole(query);

        JsonObject payload = (JsonObject) defenceCaagEnvelope.payload();
        assertThat(payload.getString(IS_ADVOCATE_DEFENDING_OR_PROSECUTING), equalTo(DEFENDING));
        assertThat(payload.getJsonArray(AUTHORIZED_DEFENDANT_IDS).size(), is(2));
    }


    private SearchCaseByUrn getSearchCaseByUrnPayload(final String urn) {
        return SearchCaseByUrn.searchCaseByUrn()
                .withCaseUrn(urn)
                .build();
    }

    @Test
    public void queryProsecutionCaseDefenceCaagAsNullPayload_forDefenceRole() {
        when(query.metadata()).thenReturn(MetadataBuilderFactory.metadataWithDefaults().withName("advocate.query.role-in-case").build());
        when(query.payloadAsJsonObject()).thenReturn(createObjectBuilder().build());
        when(cpsCaseAccessQueryView.findAdvocatesRoleInCase(argThat(arg ->
                arg.payloadAsJsonObject().containsKey(ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY))))
                .thenReturn(getProsecutionCaseDefenceCaagWithEmptyPayloadJsonMock());

        final Envelope defenceCaagEnvelope = cpsCaseAccessQueryApi.getAdvocateRole(query);

        final JsonValue payload = (JsonValue) defenceCaagEnvelope.payload();
        assertThat(payload, is(NULL));
    }

    @Test
    public void shouldFailQueryCaagWhenNotInDefenceRole() {

        when(cpsCaseAccessQueryView.findAdvocatesRoleInCase(any())).thenReturn(getProsecutionCaseDefenceCaagJsonMock(false));
        assertThrows(ForbiddenRequestException.class, () -> cpsCaseAccessQueryApi.queryProsecutioncaseDefenceCaag(requestEnvelopeForApiView));
    }

    @Test
    public void shouldQueryCaagWhenInDefenceRole() {

        final ProsecutioncaseCaag prosecutioncaseCaag = ProsecutioncaseCaag.prosecutioncaseCaag()
                .withCaseDetails(CaseDetails.caseDetails()
                        .withCaseURN("caseUrn")
                        .withCaseStatus("ACTIVE")
                        .withInitiationCode("J")
                        .withRemovalReason("any reason")
                        .build()).build();

        final Envelope responseEnvelopForCaagQuery = Envelope.envelopeFrom(
                stubbedMetadataBuilder(randomUUID()),
                prosecutioncaseCaag);

        when(cpsCaseAccessQueryView.findAdvocatesRoleInCase(
                argThat(arg -> !arg.payloadAsJsonObject().containsKey(ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY))))
                .thenReturn(getProsecutionCaseDefenceCaagJsonMock(true));
        when(cpsCaseAccessQueryView.queryProsecutioncaseDefenceCaag(any())).thenReturn(responseEnvelopForCaagQuery);
        final Envelope<ProsecutioncaseCaag> responseEnvelope = cpsCaseAccessQueryApi.queryProsecutioncaseDefenceCaag(requestEnvelopeForApiView);
        assertThat(responseEnvelope.payload().getCaseDetails().getCaseURN(), is(prosecutioncaseCaag.getCaseDetails().getCaseURN()));
        assertThat(responseEnvelope.payload().getCaseDetails().getCaseStatus(), is(prosecutioncaseCaag.getCaseDetails().getCaseStatus()));
        assertThat(responseEnvelope.payload().getCaseDetails().getRemovalReason(), is(prosecutioncaseCaag.getCaseDetails().getRemovalReason()));
        assertThat(responseEnvelope.payload().getCaseDetails().getInitiationCode(), is(prosecutioncaseCaag.getCaseDetails().getInitiationCode()));

    }

    @Test
    public void shouldFailQueryWhenNotInDefenceRole() {

        final UUID caseId = randomUUID();
        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName("advocate.query.prosecutioncase-defence")
                .createdAt(now()).build();
        final Envelope<SearchCaseByUrn> queryEnvelope = Envelope.envelopeFrom(metadata, SearchCaseByUrn.searchCaseByUrn().withCaseId(caseId).build());

        when(cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(any())).thenReturn(getProsecutionCaseDefenceCaagJsonMock(false));
        assertThrows(ForbiddenRequestException.class, () -> cpsCaseAccessQueryApi.queryProsecutioncaseDefence(queryEnvelope));
    }

    @Test
    public void shouldQueryProsecutionCaseWhenInDefenceRoleAndRemoveUnAuthorisedData() throws IOException {

        final UUID caseId = randomUUID();
        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName("advocate.query.prosecutioncase-defence")
                .createdAt(now()).build();
        final Envelope<SearchCaseByUrn> queryEnvelope = Envelope.envelopeFrom(metadata, SearchCaseByUrn.searchCaseByUrn().withCaseId(caseId).build());

        final Envelope<Prosecutioncase> prosecutionCaseResponseEnvelope = Envelope.envelopeFrom(metadata, Prosecutioncase.prosecutioncase()
                .withProsecutionCase(ProsecutionCase.prosecutionCase()
                        .withCaseMarkers(asList(Marker.marker()
                                .withId(randomUUID())
                                .build()))
                        .build())
                .build());
        when(cpsCaseAccessQueryView.queryProsecutioncaseDefence(any())).thenReturn(prosecutionCaseResponseEnvelope);
        when(cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(any())).thenReturn(getProsecutionCaseDefenceCaagJsonMock(true));
        final Envelope<Prosecutioncase> prosecutioncaseEnvelope = cpsCaseAccessQueryApi.queryProsecutioncaseDefence(queryEnvelope);

        assertThat(prosecutioncaseEnvelope.payload().getProsecutionCase().getCaseMarkers(), nullValue());
    }

    @Test
    public void shouldFailQueryWhenNotInProsecutorRole() {

        final UUID caseId = randomUUID();
        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName("advocate.query.prosecutioncase-prosecutor")
                .createdAt(now()).build();
        final Envelope<SearchCaseByUrn> queryEnvelope = Envelope.envelopeFrom(metadata, SearchCaseByUrn.searchCaseByUrn().withCaseId(caseId).build());

        when(cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(any())).thenReturn(getProsecutionCaseDefenceCaagJsonMock(true));
        assertThrows(ForbiddenRequestException.class, () -> cpsCaseAccessQueryApi.queryProsecutioncaseProsecutor(queryEnvelope));
    }


    @Test
    public void queryHearings() {

        final UUID validCaseId1 = randomUUID();
        final UUID validCaseId2 = randomUUID();
        final UUID validCaseId3 = randomUUID();
        final UUID validCaseId4 = randomUUID();
        final UUID invalidCaseId1 = randomUUID();
        final UUID invalidCaseId2 = randomUUID();
        final UUID invalidCaseId3 = randomUUID();
        final UUID invalidCaseId4 = randomUUID();
        final UUID courtRoomId1 = randomUUID();
        final UUID courtRoomId2 = randomUUID();
        final UUID courtRoomId3 = randomUUID();
        final UUID courtRoomId7 = randomUUID();
        final UUID validProsecutingAuthorityId = randomUUID();
        final UUID invalidProsecutingAuthorityId = randomUUID();
        final UUID hearingId1 = randomUUID();
        final UUID hearingId2 = randomUUID();
        final UUID hearingId3 = randomUUID();
        final UUID hearingId4 = randomUUID();
        final UUID hearingId7 = randomUUID();

        final JsonEnvelope queryEnvelope = envelopeFrom(getQueryHearingsMetadata(), getQueryHearingsPayload());
        final Map<UUID, UUID> prosecutionAuthorityIdMap = new HashMap<UUID, UUID>() {{
            put(validCaseId1, validProsecutingAuthorityId);
            put(validCaseId2, validProsecutingAuthorityId);
            put(validCaseId3, validProsecutingAuthorityId);
            put(validCaseId4, validProsecutingAuthorityId);
            put(invalidCaseId1, invalidProsecutingAuthorityId);
            put(invalidCaseId2, invalidProsecutingAuthorityId);
            put(invalidCaseId3, invalidProsecutingAuthorityId);
            put(invalidCaseId4, invalidProsecutingAuthorityId);
        }};

        final Map<UUID, ProsecutorDetails> prosecutorDetailsMap = new HashMap<UUID, ProsecutorDetails>() {{
            put(validProsecutingAuthorityId, ProsecutorDetails.prosecutorDetails()
                    .withProsecutionAuthorityId(validProsecutingAuthorityId)
                    .withIsPolice(true)
                    .withIsCps(false)
                    .build());
            put(invalidProsecutingAuthorityId, ProsecutorDetails.prosecutorDetails()
                    .withProsecutionAuthorityId(invalidProsecutingAuthorityId)
                    .withIsCps(false)
                    .build());
        }};


        uk.gov.moj.cpp.defence.query.hearing.api.Hearings hearings = Hearings.hearings()
                .withHearingSummaries(asList(
                        getMochHearingObject(hearingId1, courtRoomId2, ROOM_2, validCaseId1, CASE_1_URN, validCaseId2, DEFENDANT_3_FIRST_NAME, DEFENDANT_3_MIDDLE_NAME, DEFENDANT_3_LAST_NAME, DEFENDANT_4_FIRST_NAME, DEFENDANT_4_MIDDLE_NAME, DEFENDANT_4_LAST_NAME, CASE_2_URN),
                        getMochHearingObject(hearingId2, courtRoomId3, ROOM_3, invalidCaseId1, CASE_1_URN, invalidCaseId2, DEFENDANT_3_FIRST_NAME, DEFENDANT_3_MIDDLE_NAME, DEFENDANT_3_LAST_NAME, DEFENDANT_4_FIRST_NAME, DEFENDANT_4_MIDDLE_NAME, DEFENDANT_4_LAST_NAME, CASE_2_URN),
                        getMochHearingObject(hearingId3, courtRoomId2, ROOM_2, validCaseId3, CASE_3_URN, invalidCaseId3, DEFENDANT_4_FIRST_NAME, DEFENDANT_4_MIDDLE_NAME, DEFENDANT_4_LAST_NAME, DEFENDANT_5_FIRST_NAME, DEFENDANT_5_MIDDLE_NAME, DEFENDANT_5_LAST_NAME, CASE_4_URN),
                        getMochHearingObject(hearingId4, courtRoomId1, ROOM_1, validCaseId4, CASE_5_URN, invalidCaseId4, DEFENDANT_4_FIRST_NAME, DEFENDANT_4_MIDDLE_NAME, DEFENDANT_4_LAST_NAME, DEFENDANT_5_FIRST_NAME, DEFENDANT_5_MIDDLE_NAME, DEFENDANT_5_LAST_NAME, CASE_6_URN),
                        getHearingObjectWithProsecutionAuthorityReference(hearingId7, courtRoomId7, ROOM_7, validCaseId1)
                ))
                .build();

        when(progressionService.getProsecutionAuthorityIdMap(any(), any())).thenReturn(prosecutionAuthorityIdMap);
        when(referenceDataService.getProsecutorsAsMap(any())).thenReturn(prosecutorDetailsMap);
        when(cpsCaseAccessQueryView.getHearings(any())).thenAnswer(getMockForRequester(validCaseId1, validCaseId2, validCaseId3, queryEnvelope.metadata(), hearings));

        final JsonEnvelope responseJsonEnvelopForViewQuery = getMockResponseForAssigneeQuery(randomUUID(), randomUUID());
        when(cpsCaseAccessQueryView.getAssignedUsersToTheCase(any())).thenReturn(responseJsonEnvelopForViewQuery);

        final Envelope<uk.gov.moj.cpp.defence.query.api.Hearings> hearingQueryResponseEnvelope = cpsCaseAccessQueryApi.getHearings(queryEnvelope);

        final uk.gov.moj.cpp.defence.query.api.Hearings responseHearings = hearingQueryResponseEnvelope.payload();
        assertThat(responseHearings.getCourtRooms().size(), is(3));

        assertThat(responseHearings.getCourtRooms().get(0).getId(), is(courtRoomId1));
        assertThat(responseHearings.getCourtRooms().get(0).getName(), is(ROOM_1));

        assertThat(responseHearings.getCourtRooms().get(1).getId(), is(courtRoomId2));
        assertThat(responseHearings.getCourtRooms().get(1).getName(), is(ROOM_2));

        assertThat(responseHearings.getCourtRooms().get(0).getProsecutionCases().size(), is(1));
        assertThat(responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getCaseId(), is(validCaseId4));
        assertThat(responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getCaseUrn(), is(CASE_5_URN));
        assertThat(responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getDefendants().size(), is(2));
        assertThat(responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getHearingId(), is(hearingId4));
        assertThat(responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getAssignedProsecutors().size(), is(2));

        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().size(), is(3));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getCaseId(), is(validCaseId1));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getCaseUrn(), is(CASE_1_URN));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getDefendants().size(), is(2));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getHearingId(), is(hearingId1));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getAssignedProsecutors().size(), is(2));

        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getCaseId(), is(validCaseId2));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getCaseUrn(), is(CASE_2_URN));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getDefendants().size(), is(2));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getHearingId(), is(hearingId1));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getAssignedProsecutors().size(), is(2));

        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getCaseId(), is(validCaseId3));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getCaseUrn(), is(CASE_3_URN));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getDefendants().size(), is(2));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getHearingId(), is(hearingId3));
        assertThat(responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getAssignedProsecutors().size(), is(2));

        assertThat(responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getCaseUrn(), is(PROSECUTION_AUTHORITY_REFERENCE));
        assertThat(responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getCaseId(), is(validCaseId1));
        assertThat(responseHearings.getCourtRooms().get(2).getName(), is(ROOM_7));
        assertThat(responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getAssignedProsecutors().size(), is(2));
        assertThat(responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getDefendants().size(), is(1));
        assertThat(responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getHearingId(), is(hearingId7));

    }

    @Test
    public void queryHearingsForNonCpsUsers() {

        final UUID validCaseId1 = randomUUID();
        final UUID validCaseId2 = randomUUID();
        final UUID validCaseId3 = randomUUID();
        final UUID validCaseId4 = randomUUID();
        final UUID invalidCaseId1 = randomUUID();
        final UUID invalidCaseId2 = randomUUID();
        final UUID invalidCaseId3 = randomUUID();
        final UUID invalidCaseId4 = randomUUID();
        final UUID courtRoomId1 = randomUUID();
        final UUID courtRoomId2 = randomUUID();
        final UUID courtRoomId3 = randomUUID();
        final UUID courtRoomId7 = randomUUID();
        final UUID validProsecutingAuthorityId = randomUUID();
        final UUID invalidProsecutingAuthorityId = randomUUID();
        final UUID hearingId1 = randomUUID();
        final UUID hearingId2 = randomUUID();
        final UUID hearingId3 = randomUUID();
        final UUID hearingId4 = randomUUID();
        final UUID hearingId7 = randomUUID();

        final JsonEnvelope queryEnvelope = envelopeFrom(getQueryHearingsMetadata(), getQueryHearingsPayload());
        final Map<UUID, UUID> prosecutionAuthorityIdMap = new HashMap<UUID, UUID>() {{
            put(validCaseId1, validProsecutingAuthorityId);
            put(validCaseId2, validProsecutingAuthorityId);
            put(validCaseId3, validProsecutingAuthorityId);
            put(validCaseId4, validProsecutingAuthorityId);
            put(invalidCaseId1, invalidProsecutingAuthorityId);
            put(invalidCaseId2, invalidProsecutingAuthorityId);
            put(invalidCaseId3, invalidProsecutingAuthorityId);
            put(invalidCaseId4, invalidProsecutingAuthorityId);
        }};

        final Map<UUID, ProsecutorDetails> prosecutorDetailsMap = new HashMap<UUID, ProsecutorDetails>() {{
            put(validProsecutingAuthorityId, ProsecutorDetails.prosecutorDetails()
                    .withProsecutionAuthorityId(validProsecutingAuthorityId)
                    .withIsPolice(false)
                    .withIsCps(false)
                    .withShortName("DVLA")
                    .build());
            put(invalidProsecutingAuthorityId, ProsecutorDetails.prosecutorDetails()
                    .withProsecutionAuthorityId(invalidProsecutingAuthorityId)
                    .withIsPolice(false)
                    .withIsCps(false)
                    .withShortName("TVL")
                    .build());
        }};


        uk.gov.moj.cpp.defence.query.hearing.api.Hearings hearings = Hearings.hearings()
                .withHearingSummaries(asList(
                        getMochHearingObject(hearingId1, courtRoomId2, ROOM_2, validCaseId1, CASE_1_URN, validCaseId2, DEFENDANT_3_FIRST_NAME, DEFENDANT_3_MIDDLE_NAME, DEFENDANT_3_LAST_NAME, DEFENDANT_4_FIRST_NAME, DEFENDANT_4_MIDDLE_NAME, DEFENDANT_4_LAST_NAME, CASE_2_URN),
                        getMochHearingObject(hearingId2, courtRoomId3, ROOM_3, invalidCaseId1, CASE_1_URN, invalidCaseId2, DEFENDANT_3_FIRST_NAME, DEFENDANT_3_MIDDLE_NAME, DEFENDANT_3_LAST_NAME, DEFENDANT_4_FIRST_NAME, DEFENDANT_4_MIDDLE_NAME, DEFENDANT_4_LAST_NAME, CASE_2_URN),
                        getMochHearingObject(hearingId3, courtRoomId2, ROOM_2, validCaseId3, CASE_3_URN, invalidCaseId3, DEFENDANT_4_FIRST_NAME, DEFENDANT_4_MIDDLE_NAME, DEFENDANT_4_LAST_NAME, DEFENDANT_5_FIRST_NAME, DEFENDANT_5_MIDDLE_NAME, DEFENDANT_5_LAST_NAME, CASE_4_URN),
                        getMochHearingObject(hearingId4, courtRoomId1, ROOM_1, validCaseId4, CASE_5_URN, invalidCaseId4, DEFENDANT_4_FIRST_NAME, DEFENDANT_4_MIDDLE_NAME, DEFENDANT_4_LAST_NAME, DEFENDANT_5_FIRST_NAME, DEFENDANT_5_MIDDLE_NAME, DEFENDANT_5_LAST_NAME, CASE_6_URN),
                        getHearingObjectWithProsecutionAuthorityReference(hearingId7, courtRoomId7, ROOM_7, validCaseId1)
                ))
                .build();

        when(progressionService.getProsecutionAuthorityIdMap(any(), any())).thenReturn(prosecutionAuthorityIdMap);
        when(referenceDataService.getProsecutorsAsMap(any())).thenReturn(prosecutorDetailsMap);
        when(cpsCaseAccessQueryView.getHearings(any())).thenAnswer(getMockForRequester(validCaseId1, validCaseId2, validCaseId3, queryEnvelope.metadata(), hearings));
        final JsonObject userGroups = createObjectBuilder().add("groups", createArrayBuilder()).build();
        when(usersGroupQueryService.getUserGroups(queryEnvelope.metadata(),userId)).thenReturn(userGroups);
        when(usersGroupQueryService.isNonCpsUserGroup(userGroups, "Non CPS Prosecutors")).thenReturn(true);
        when(usersGroupQueryService.isNonCPSProsecutorWithValidProsecutingAuthority(userGroups, "Non CPS Prosecutors", "DVLA" )).thenReturn(true);

        final JsonEnvelope responseJsonEnvelopForViewQuery = getMockResponseForAssigneeQuery(randomUUID(), randomUUID());
        when(cpsCaseAccessQueryView.getAssignedUsersToTheCase(any())).thenReturn(responseJsonEnvelopForViewQuery);

        final Envelope<uk.gov.moj.cpp.defence.query.api.Hearings> hearingQueryResponseEnvelope = cpsCaseAccessQueryApi.getHearings(queryEnvelope);

        final uk.gov.moj.cpp.defence.query.api.Hearings responseHearings = hearingQueryResponseEnvelope.payload();
        assertThat(responseHearings.getCourtRooms().size(), is(3));

        assertThat(responseHearings.getCourtRooms().get(0).getId(), is(courtRoomId1));
        assertThat(responseHearings.getCourtRooms().get(0).getName(), is(ROOM_1));

        assertThat(responseHearings.getCourtRooms().get(1).getId(), is(courtRoomId2));
        assertThat(responseHearings.getCourtRooms().get(1).getName(), is(ROOM_2));
        assertThat(responseHearings.getCourtRooms().get(2).getName(), is(ROOM_7));

        assertFirstProsecutionCaseFromCourtRoomOne(responseHearings, validCaseId4, hearingId4);
        assertFirstProsecutionCaseFromCourtRoomTwo(responseHearings, validCaseId1, hearingId1);
        assertSecondProsecutionCaseFromCourtRoomTwo(responseHearings, validCaseId2, hearingId1);
        assertThirdProsecutionCaseFromCourtRoomTwo(responseHearings, validCaseId3, hearingId3);
        assertFirstProsecutionCaseFromCourtRoomThree(responseHearings, validCaseId1, hearingId7);

    }

    private static void assertFirstProsecutionCaseFromCourtRoomOne(uk.gov.moj.cpp.defence.query.api.Hearings responseHearings, UUID validCaseId4, UUID hearingId4) {
        assertAll("Checking First Prosecution Case in Court room One",
                () -> assertEquals(1, responseHearings.getCourtRooms().get(0).getProsecutionCases().size()),
                () -> assertEquals(validCaseId4, responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getCaseId()),
                () -> assertEquals(CASE_5_URN, responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getCaseUrn()),
                () -> assertEquals(2, responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getDefendants().size()),
                () -> assertEquals(hearingId4, responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getHearingId()),
                () -> assertEquals(2, responseHearings.getCourtRooms().get(0).getProsecutionCases().get(0).getAssignedProsecutors().size())
        );
    }

    private static void assertFirstProsecutionCaseFromCourtRoomTwo(uk.gov.moj.cpp.defence.query.api.Hearings responseHearings, UUID validCaseId1, UUID hearingId1) {

        assertAll("Checking First Prosecution Case in Court room Two",
                () -> assertEquals(3, responseHearings.getCourtRooms().get(1).getProsecutionCases().size()),
                () -> assertEquals(validCaseId1, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getCaseId()),
                () -> assertEquals(CASE_1_URN, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getCaseUrn()),
                () -> assertEquals(2, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getDefendants().size()),
                () -> assertEquals(hearingId1, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getHearingId()),
                () -> assertEquals(2, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(0).getAssignedProsecutors().size())
        );
    }
    private static void assertSecondProsecutionCaseFromCourtRoomTwo(uk.gov.moj.cpp.defence.query.api.Hearings responseHearings, UUID validCaseId2, UUID hearingId1) {

        assertAll("Checking Second Prosecution Case in Court room Two",
                () -> assertEquals(validCaseId2, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getCaseId()),
                () -> assertEquals(CASE_2_URN, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getCaseUrn()),
                () -> assertEquals(2, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getDefendants().size()),
                () -> assertEquals(hearingId1, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getHearingId()),
                () -> assertEquals(2, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(1).getAssignedProsecutors().size())
        );
    }
    private static void assertThirdProsecutionCaseFromCourtRoomTwo(uk.gov.moj.cpp.defence.query.api.Hearings responseHearings, UUID validCaseId3, UUID hearingId3) {

        assertAll("Checking Third Prosecution Case in Court room Two",
                () -> assertEquals(validCaseId3, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getCaseId()),
                () -> assertEquals(CASE_3_URN, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getCaseUrn()),
                () -> assertEquals(2, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getDefendants().size()),
                () -> assertEquals(hearingId3, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getHearingId()),
                () -> assertEquals(2, responseHearings.getCourtRooms().get(1).getProsecutionCases().get(2).getAssignedProsecutors().size())
        );
    }
    private static void assertFirstProsecutionCaseFromCourtRoomThree(uk.gov.moj.cpp.defence.query.api.Hearings responseHearings, UUID validCaseId1, UUID hearingId7) {

        assertAll("Checking First Prosecution Case in Court room Three",
                () -> assertEquals(validCaseId1, responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getCaseId()),
                () -> assertEquals(PROSECUTION_AUTHORITY_REFERENCE, responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getCaseUrn()),
                () -> assertEquals(1, responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getDefendants().size()),
                () -> assertEquals(hearingId7, responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getHearingId()),
                () -> assertEquals(2, responseHearings.getCourtRooms().get(2).getProsecutionCases().get(0).getAssignedProsecutors().size())
        );
    }

    @Test
    public void verifyAllPassThroughQueryHandlerMethodsForCaseOrganisationAssignees() {
        final UUID assigneeOrganisationId1 = randomUUID();
        final UUID assigneeOrganisationId2 = randomUUID();
        final JsonEnvelope responseJsonEnvelopForViewQuery = getMockResponseForAssigneeQuery(assigneeOrganisationId1, assigneeOrganisationId2);
        when(cpsCaseAccessQueryView.getAssignedAdvocatesToTheCaseAndOrganisation((any(JsonEnvelope.class)))).thenReturn(responseJsonEnvelopForViewQuery);

        final JsonEnvelope enrichedQueryResponseEnvelope = cpsCaseAccessQueryApi.getAdvocatesAssignedToTheCaseAndOrganisation(query);

        //Then
        final JsonArray assigneesJsonArray = enrichedQueryResponseEnvelope.payloadAsJsonObject().getJsonArray(ASSIGNEES);
        assertThat(assigneesJsonArray.size(), is(2));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getString(ASSIGNEE_NAME), is(ASSIGNEE_NAME_1));
        assertThat(((JsonObject) assigneesJsonArray.get(0)).getString(ASSIGNEE_ORGANISATION_ID), is(assigneeOrganisationId1.toString()));

        assertThat(((JsonObject) assigneesJsonArray.get(1)).getString(ASSIGNEE_NAME), is(ASSIGNEE_NAME_2));
        assertThat(((JsonObject) assigneesJsonArray.get(1)).getString(ASSIGNEE_ORGANISATION_ID), is(assigneeOrganisationId2.toString()));
    }

    @Test
    public void verifyAllPassThroughQueryHandlerMethodsForGetAdvocateRoleByCaseId() {
        final JsonEnvelope responseJsonEnvelopForViewQuery = envelopeFrom(
                stubbedMetadataBuilder(randomUUID()),
                getProsecutionCaseDefenceCaagResponseJson(true));
        when(cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(any(JsonEnvelope.class))).thenReturn(responseJsonEnvelopForViewQuery);

        final JsonEnvelope enrichedQueryResponseEnvelope = cpsCaseAccessQueryApi.getAdvocateRoleByCaseId(query);

        //Then
        final JsonObject payloadAsJsonObject = enrichedQueryResponseEnvelope.payloadAsJsonObject();
        assertThat(payloadAsJsonObject.getString("isAdvocateDefendingOrProsecuting"), is("defending"));
        assertThat(payloadAsJsonObject.getJsonArray("authorizedDefendantIds").size(), is(2));
    }

    @Test
    public void verifyAllPassThroughQueryHandlerMethodsForGetAdvocateRoleByCaseIdWithEmptyResponsePayload() {
        final JsonEnvelope responseJsonEnvelopForViewQuery = envelopeFrom(
                stubbedMetadataBuilder(randomUUID()),
                createObjectBuilder().build());
        when(cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(any(JsonEnvelope.class))).thenReturn(responseJsonEnvelopForViewQuery);

        final JsonEnvelope enrichedQueryResponseEnvelope = cpsCaseAccessQueryApi.getAdvocateRoleByCaseId(query);

        //Then
        final JsonValue payload = enrichedQueryResponseEnvelope.payload();
        assertThat(payload, is(NULL));
    }

    @Test
    public void shouldQueryCaagWhenInProsecutorCaag() throws IOException {

        final UUID caseId = randomUUID();
        final UUID prosecutingAuthorityId = randomUUID();
        final UUID authorisedDefendantId = randomUUID();

        final ProsecutioncaseCaag prosecutioncaseCaag = jsonObjectToObjectConverter.convert(getProsecutionCaseCaagQueryResponsePayload(authorisedDefendantId), ProsecutioncaseCaag.class);

        final Envelope responseEnvelopForCaagQuery = Envelope.envelopeFrom(
                stubbedMetadataBuilder(randomUUID()),
                prosecutioncaseCaag);

        when(defenceQueryService.getCaseId(any())).thenReturn(caseId);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(any(), any())).thenReturn(prosecutingAuthorityId);
        when(referenceDataService.getProsecutor(any(), any())).thenReturn(Optional.of(createObjectBuilder().add("cpsFlag", true).add("policeFlag", true).add("shortName", "DVLA").build()));
        when(usersGroupQueryService.validateNonCPSUser(any(),any(),any(),any())).thenReturn(true);
        when(cpsCaseAccessQueryView.findAdvocatesRoleInCase(any())).thenReturn(getProsecutionCaseDefenceCaagJsonMock(true));
        when(cpsCaseAccessQueryView.queryProsecutioncaseProsecutorCaag(any())).thenReturn(responseEnvelopForCaagQuery);


        final Envelope<ProsecutioncaseCaag> responseEnvelope = cpsCaseAccessQueryApi.queryProsecutioncaseProsecutorCaag(requestEnvelopeForApiView);
        assertThat(responseEnvelope.payload().getCaseDetails().getCaseURN(), is(prosecutioncaseCaag.getCaseDetails().getCaseURN()));
        assertThat(responseEnvelope.payload().getCaseDetails().getCaseStatus(), is(prosecutioncaseCaag.getCaseDetails().getCaseStatus()));
        assertThat(responseEnvelope.payload().getCaseDetails().getRemovalReason(), is(prosecutioncaseCaag.getCaseDetails().getRemovalReason()));
        assertThat(responseEnvelope.payload().getCaseDetails().getInitiationCode(), is(prosecutioncaseCaag.getCaseDetails().getInitiationCode()));
    }

    @Test
    public void shouldQueryCaagWhenInProsecutorCaagForProsecuting() throws IOException {

        final UUID caseId = randomUUID();
        final UUID prosecutingAuthorityId = randomUUID();
        final UUID authorisedDefendantId = randomUUID();

        final ProsecutioncaseCaag prosecutioncaseCaag = jsonObjectToObjectConverter.convert(getProsecutionCaseCaagQueryResponsePayload(authorisedDefendantId), ProsecutioncaseCaag.class);

        final Envelope responseEnvelopForCaagQuery = Envelope.envelopeFrom(
                stubbedMetadataBuilder(randomUUID()),
                prosecutioncaseCaag);

        when(defenceQueryService.getCaseId(any())).thenReturn(caseId);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(any(), any())).thenReturn(prosecutingAuthorityId);
        when(referenceDataService.getProsecutor(any(), any())).thenReturn(Optional.of(createObjectBuilder().add("cpsFlag", true).add("policeFlag", true).add("shortName", "DVLA").build()));
        when(usersGroupQueryService.validateNonCPSUser(any(),any(),any(),any())).thenReturn(true);
        when(cpsCaseAccessQueryView.findAdvocatesRoleInCase(any())).thenReturn(getProsecutionCaseDefenceCaagJsonMock(false));
        when(cpsCaseAccessQueryView.queryProsecutioncaseProsecutorCaag(any())).thenReturn(responseEnvelopForCaagQuery);


        final Envelope<ProsecutioncaseCaag> responseEnvelope = cpsCaseAccessQueryApi.queryProsecutioncaseProsecutorCaag(requestEnvelopeForApiView);
        assertThat(responseEnvelope.payload().getCaseDetails().getCaseURN(), is(prosecutioncaseCaag.getCaseDetails().getCaseURN()));
        assertThat(responseEnvelope.payload().getCaseDetails().getCaseStatus(), is(prosecutioncaseCaag.getCaseDetails().getCaseStatus()));
        assertThat(responseEnvelope.payload().getCaseDetails().getRemovalReason(), is(prosecutioncaseCaag.getCaseDetails().getRemovalReason()));
        assertThat(responseEnvelope.payload().getCaseDetails().getInitiationCode(), is(prosecutioncaseCaag.getCaseDetails().getInitiationCode()));
    }

    @Test
    public void shouldQueryCaagWhenInProsecutorCaagForNonCpsIfProsecutorMismatch() throws IOException {

        final UUID caseId = randomUUID();
        final UUID prosecutingAuthorityId = randomUUID();
        final UUID authorisedDefendantId = randomUUID();

        when(defenceQueryService.getCaseId(any())).thenReturn(caseId);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(any(), any())).thenReturn(prosecutingAuthorityId);
        when(referenceDataService.getProsecutor(any(), any())).thenReturn(Optional.of(createObjectBuilder().add("cpsFlag", true).add("policeFlag", true).add("shortName", "DVLA").build()));
        when(usersGroupQueryService.validateNonCPSUser(any(),any(),any(),any())).thenReturn(false);
        when(cpsCaseAccessQueryView.findAdvocatesRoleInCase(any())).thenReturn(getProsecutionCaseDefenceCaagJsonMock(true));

        assertThrows(ForbiddenRequestException.class, ()-> cpsCaseAccessQueryApi.queryProsecutioncaseProsecutorCaag(requestEnvelopeForApiView));
    }


    private JsonObject getProsecutionCaseCaagQueryResponsePayload(final UUID authorisedDefendantId) throws IOException {
        final String payload = readFileToString(new File(this.getClass().getClassLoader().getResource("defence.query.prosecutioncase-caag.json").getFile())).replace("DEFENDANT_ID", authorisedDefendantId.toString());
        return new DefaultJsonParser().toObject(payload, JsonObject.class);
    }

    private Metadata getQueryHearingsMetadata() {
        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName(DEFENCE_QUERY_HEARINGS)
                .withUserId(userId.toString())
                .createdAt(now()).build();
        return metadata;
    }

    private JsonObject getQueryHearingsPayload() {
        final JsonObject payload = createObjectBuilder()
                .add(DATE, "12/12/2022")
                .add(COURT_CENTRE_ID, "5")
                .build();
        return payload;
    }

    private HearingSummary getMochHearingObject(final UUID hearingId1, final UUID courtRoomId1, final String roomName, final UUID validCaseId1, final String case1urn, final UUID validCaseId2, final String defendant3FirstName, final String defendant3MiddleName, final String defendant3LastName, final String defendant4FirstName, final String defendant4MiddleName, final String defendant4LastName, final String case2urn) {
        return HearingSummary.hearingSummary()
                .withId(hearingId1)
                .withCourtCentre(CourtCentre.courtCentre()
                        .withRoomId(courtRoomId1)
                        .withRoomName(roomName)
                        .build())
                .withProsecutionCaseSummaries(asList(
                        getProsecutionCaseSummary(validCaseId1, DEFENDANT_1_FIRST_NAME, DEFENDANT_1_MIDDLE_NAME, DEFENDANT_1_LAST_NAME, DEFENDANT_2_FIRST_NAME, DEFENDANT_2_MIDDLE_NAME, DEFENDANT_2_LAST_NAME, case1urn),
                        getProsecutionCaseSummary(validCaseId2, defendant3FirstName, defendant3MiddleName, defendant3LastName, defendant4FirstName, defendant4MiddleName, defendant4LastName, case2urn)
                ))
                .build();
    }

    private HearingSummary getHearingObjectWithProsecutionAuthorityReference(final UUID hearingId7, final UUID courtRoomId7, final String roomName, final UUID validCaseId7) {
        return HearingSummary.hearingSummary()
                .withId(hearingId7)
                .withCourtCentre(CourtCentre.courtCentre()
                        .withRoomId(courtRoomId7)
                        .withRoomName(roomName)
                        .build())
                .withProsecutionCaseSummaries(asList(
                        getProsecutionCaseSummaryWithProsecutionCaseReference(validCaseId7, DEFENDANT_7_FIRST_NAME, DEFENDANT_7_MIDDLE_NAME, DEFENDANT_7_LAST_NAME, PROSECUTION_AUTHORITY_REFERENCE)
                ))
                .build();
    }

    private Answer<?> getMockForRequester(final UUID validCaseId1, final UUID validCaseId2, final UUID validCaseId3, final Metadata metadata, final Hearings hearings) {
        return (Answer<Object>) invocationOnMock -> {
            final String queryName = ((JsonEnvelope) invocationOnMock.getArguments()[0]).metadata().name();
            if (queryName.equals(DEFENCE_QUERY_CASE_CPS_ASSIGNEES)) {
                final JsonEnvelope jsonEnvelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
                final JsonObject payload = jsonEnvelope.payloadAsJsonObject();
                final String caseId = payload.getJsonString("caseId").getString();
                if (caseId.equals(validCaseId1.toString())) {
                    return getMockForAssigneeQuery(metadata, ASSIGNEE_NAME_1, true);
                }
                if (caseId.equals(validCaseId2.toString())) {
                    return getMockForAssigneeQuery(metadata, null, false);
                }
                if (caseId.equals(validCaseId3.toString())) {
                    return getMockForAssigneeQuery(metadata, "assigneeName3", true);
                }
                return getMockForAssigneeQuery(metadata, null, false);
            } else if (queryName.equals(DEFENCE_QUERY_HEARINGS)) {
                return Envelope.envelopeFrom(metadata, hearings);
            }
            return null;
        };
    }

    private Envelope<CaseAdvocateAccess> getMockForAssigneeQuery(final Metadata metadata, final String assigneeName, final boolean withData) {
        if (withData) {
            return Envelope.envelopeFrom(metadata, CaseAdvocateAccess.caseAdvocateAccess()
                    .withAssignees(singletonList(Assignees.assignees()
                            .withAssigneeUserId(randomUUID()).withAssigneeName(assigneeName).build()))
                    .build());
        } else {
            return Envelope.envelopeFrom(metadata, CaseAdvocateAccess.caseAdvocateAccess()
                    .build());
        }

    }


    private ProsecutionCaseSummary getProsecutionCaseSummary(final UUID caseId1, final String defendant1FirstName, final String defendant1MiddleName, final String defendant1LastName, final String defendant2FirstName, final String defendant2MiddleName, final String defendant2LastName, final String case1urn) {
        return ProsecutionCaseSummary.prosecutionCaseSummary()
                .withId(caseId1)
                .withDefendants(asList(
                        Defendants.defendants()
                                .withId(randomUUID())
                                .withFirstName(defendant1FirstName)
                                .withMiddleName(defendant1MiddleName)
                                .withLastName(defendant1LastName)
                                .build(),
                        Defendants.defendants()
                                .withId(randomUUID())
                                .withFirstName(defendant2FirstName)
                                .withMiddleName(defendant2MiddleName)
                                .withLastName(defendant2LastName)
                                .build()))
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier()
                        .withCaseURN(case1urn)
                        .build())
                .build();
    }

    private ProsecutionCaseSummary getProsecutionCaseSummaryWithProsecutionCaseReference(final UUID caseId, final String defendantFirstName, final String defendantMiddleName, final String defendantLastName, final String prosecutionAuthorityReference) {
        return ProsecutionCaseSummary.prosecutionCaseSummary()
                .withId(caseId)
                .withDefendants(asList(
                        Defendants.defendants()
                                .withId(randomUUID())
                                .withFirstName(defendantFirstName)
                                .withMiddleName(defendantMiddleName)
                                .withLastName(defendantLastName)
                                .build()))
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier()
                        .withProsecutionAuthorityReference(prosecutionAuthorityReference)
                        .build())
                .build();
    }

    private SearchCaseByUrn getSearchCaseByUrn() {
        return SearchCaseByUrn.searchCaseByUrn()
                .withCaseUrn(CASE_URN)
                .withAuthorisedDefendantIds(asList(authorizedDefendantId1, authorizedDefendantId2))
                .build();
    }

    private JsonEnvelope getProsecutionCaseDefenceCaagJsonMock(boolean isDefending) {
        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName(ADVOCATE_QUERY_ROLE_IN_CASE)
                .createdAt(now()).build();
        return envelopeFrom(metadata, getProsecutionCaseDefenceCaagResponseJson(isDefending));
    }

    private JsonEnvelope getProsecutionCaseDefenceCaagWithEmptyPayloadJsonMock() {
        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName(ADVOCATE_QUERY_ROLE_IN_CASE)
                .createdAt(now()).build();
        return envelopeFrom(metadata, createObjectBuilder().build());
    }

    private JsonObject getProsecutionCaseDefenceCaagResponseJson(boolean isDefending) {
        return createObjectBuilder()
                .add(IS_ADVOCATE_DEFENDING_OR_PROSECUTING, isDefending ? DEFENDING : PROSECUTING)
                .add(AUTHORIZED_DEFENDANT_IDS, createArrayBuilder()
                        .add(authorizedDefendantId1.toString())
                        .add(authorizedDefendantId2.toString())
                        .build())
                .build();
    }

    private MetadataBuilder stubbedMetadataBuilder(final UUID userId) {
        return metadataBuilder()
                .withId(randomUUID())
                .withName(DEFENCE_QUERY_CASE_CPS_ASSIGNEES)
                .withCausation(randomUUID())
                .withClientCorrelationId(randomUUID().toString())
                .withStreamId(randomUUID())
                .withUserId(userId.toString());
    }

    private MetadataBuilder stubbedMetadataBuilderDefenceQuery(final UUID userId) {
        return metadataBuilder()
                .withId(randomUUID())
                .withName("defence.query.prosecutioncase-defence-caag")
                .withStreamId(randomUUID())
                .withUserId(userId.toString());
    }
}