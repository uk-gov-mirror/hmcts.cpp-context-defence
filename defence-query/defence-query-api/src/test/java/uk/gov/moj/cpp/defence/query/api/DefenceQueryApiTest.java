package uk.gov.moj.cpp.defence.query.api;

import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static jakarta.json.JsonValue.NULL;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static uk.gov.justice.services.core.annotation.Component.QUERY_API;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;
import static uk.gov.moj.cpp.defence.common.util.DefencePermission.VIEW_DEFENDANT_PERMISSION;
import static uk.gov.moj.cpp.defence.query.api.CpsCaseAccessQueryApiTest.ADDRESS_2;
import static uk.gov.moj.cpp.defence.query.api.CpsCaseAccessQueryApiTest.ADDRESS_3;
import static uk.gov.moj.cpp.defence.query.api.CpsCaseAccessQueryApiTest.ADDRESS_4;
import static uk.gov.moj.cpp.defence.query.api.CpsCaseAccessQueryApiTest.ADDRESS_LINE_2;
import static uk.gov.moj.cpp.defence.query.api.CpsCaseAccessQueryApiTest.ADDRESS_LINE_3;
import static uk.gov.moj.cpp.defence.query.api.CpsCaseAccessQueryApiTest.ADDRESS_LINE_4;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_1;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_LINE_1;
import static uk.gov.moj.cpp.defence.query.api.DefenceAssociationQueryApi.ADDRESS_POSTCODE;
import static uk.gov.moj.cpp.defence.query.api.DefenceQueryApi.ASSOCIATED_PERSONS;
import static uk.gov.moj.cpp.defence.query.api.DefenceQueryApi.LAST_ASSOCIATED_ORGANISATION;
import static uk.gov.moj.cpp.defence.query.api.DefenceQueryApi.PROSECUTION_AUTHORITY_CODE;
import static uk.gov.moj.cpp.defence.query.api.DefenceQueryApi.WITH_ADDRESS;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.CASE_DEFENDANT_ORGANISATION;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelper.DEFENDANTS;
import static uk.gov.moj.cpp.defence.query.api.service.CaseDefendantOrganisationHelperTest.ORGANISATION_ADDRESS;
import static uk.gov.moj.cpp.defence.service.PermissionService.getUserPermissions;

import uk.gov.justice.cps.defence.CaseDefendantsOrganisations;
import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.common.json.DefaultJsonParser;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultEnvelope;
import uk.gov.moj.cpp.accesscontrol.common.providers.UserAndGroupProvider;
import uk.gov.moj.cpp.defence.CaseDefendantsWithOrganisation;
import uk.gov.moj.cpp.defence.common.Defendant;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.query.api.service.CalendarService;
import uk.gov.moj.cpp.defence.query.api.service.OrganisationNameVO;
import uk.gov.moj.cpp.defence.query.api.service.OrganisationQueryService;
import uk.gov.moj.cpp.defence.query.api.service.ProgressionQueryService;
import uk.gov.moj.cpp.defence.query.api.service.UsersAndGroupsService;
import uk.gov.moj.cpp.defence.query.view.DefenceQueryView;
import uk.gov.moj.cpp.defence.service.PermissionService;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefenceQueryApiTest {

    public static final String ORGANISATION_NAME = "organisationName";
    public static final String CASE_ID = "caseId";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String PROSECUTOR = "prosecutor";
    public static final String IDPC_ACCESSING_ORGANISATIONS = "idpcAccessingOrganisations";
    public static final String ASSOCIATED_ORGANISATION = "associatedOrganisation";
    public static final String ORDER = "order";
    public static final String NAME = "name";
    public static final String INSTRUCTION_DATE = "instructionDate";
    public static final String INSTRUCTION_ID = "instructionId";
    public static final String INSTRUCTING_ORGANISATIONS = "instructingOrganisations";
    public static final String LOCKED_BY_REP_ORDER = "lockedByRepOrder";
    private static final String DOB = "dateOfBirth";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String IS_ORGANISATION = "isOrganisation";
    private static final String URN = "urn";
    private static final String DEFENCE_CLIENT_ID = "defenceClientId";
    private static final String ORGANISATION_ID = "organisationId";
    private static final String USER_ID = "userId";
    private static final String HEARING_DATE = "hearingDate";
    public static final String CASE_URN = "caseUrn";
    public static final String IS_CIVIL = "isCivil";

    @Mock
    private Requester requester;

    @Mock
    private Sender sender;

    @Captor
    private ArgumentCaptor<DefaultEnvelope> senderCaptor;

    @InjectMocks
    private DefenceQueryApi defenceQueryApi;

    @Mock
    private OrganisationQueryService organisationQueryService;

    @Mock
    private UserAndGroupProvider userAndGroupProvider;

    @Mock
    private DefenceAssociationQueryApi defenceAssociationQueryApi;

    @Mock
    private ProgressionQueryService progressionQueryService;

    @Mock
    private UsersAndGroupsService usersAndGroupsService;

    @Mock
    private DefenceQueryView defenceQueryView;

    @Mock
    private Envelope<DefenceClient> defenceClientEnvelope;

    @Mock
    private Envelope<CaseDefendantsOrganisations> caseDefendantsOrganisationsEnvelope;

    @Spy
    private ObjectMapper mapper = new ObjectMapperProducer().objectMapper();
    @Spy
    private ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(mapper);

    @Mock
    private CalendarService calendarService;

    @Captor
    ArgumentCaptor<JsonEnvelope> captorArg;

    @Test
    public void verifyAllPassThroughQueryHandlerMethods() {
        assertThat(new DefenceQueryApi(), isHandler(QUERY_API)
                .with(method("findClientByCriteria")
                        .thatHandles("defence.query.defence-client-id")));
        assertThat(new DefenceQueryApi(), isHandler(QUERY_API)
                .with(method("findAllegationsByDefenceClientId")
                        .thatHandles("defence.query.defence-client-allegations")));
        assertThat(new DefenceQueryApi(), isHandler(QUERY_API)
                .with(method("getDefenceClientIdpc")
                        .thatHandles("defence.query.defence-client-idpc")));
        assertThat(new DefenceQueryApi(), isHandler(QUERY_API)
                .with(method("issueRecordIdpcAccess")
                        .thatHandles("defence.query.record-idpc-access")));
    }


    @Test
    public void shouldReturnIdpcAccessOrganisationsDetailsWithoutProsecutor() {
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final String associatedOrgName = "ASSOCIATED_ORG_NAME";
        final String lastAssociatedOrgName = "LAST_ASSOCIATED_ORG_NAME";
        final String additionalInstructingOrgName = "ADD LTD";
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final UUID idpcAccessingOrganisationId_2 = randomUUID();
        final UUID additionalInstructingOrganisation = randomUUID();
        final String idpcOrgName_1 = "IDPC_ORG_NAME_1";
        final String idpcOrgName_2 = "IDPC_ORG_NAME_2";
        final String caseUrn = "TVL123MXC";

        final JsonEnvelope requestEnvelope = createRequestEnvelope();
        final JsonEnvelope responseFromView = stubbedSuccessResponse(defenceClientId, caseId, defendantid,
                associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2, asList(associatedOrganisationId, additionalInstructingOrganisation),
                requestEnvelope, false, caseUrn);
        when(defenceQueryView.findClientByCriteria(any(JsonEnvelope.class))).thenReturn(responseFromView);
        when(defenceClientEnvelope.payload()).thenReturn(new DefenceClient());
        when(defenceQueryView.getDefenceClientByDefendantId(any())).thenReturn(defenceClientEnvelope);


        when(organisationQueryService.getOrganisationNamesForIds(createOrgIdList(idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2), responseFromView.metadata()))
                .thenReturn(stubbedIdpcOrganisationNameDetails(idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2, idpcOrgName_1, idpcOrgName_2));
        when(organisationQueryService.getOrganisationNamesForIds(createOrgIdList(lastAssociatedOrganisationId), responseFromView.metadata()))
                .thenReturn(stubbedAssociatedOrganisationNameDetail(lastAssociatedOrganisationId, lastAssociatedOrgName));
        when(organisationQueryService.getOrganisationNamesForIds(createOrgIdList(associatedOrganisationId, additionalInstructingOrganisation), responseFromView.metadata()))
                .thenReturn(stubbedIdpcOrganisationNameDetails(associatedOrganisationId, additionalInstructingOrganisation, associatedOrgName, additionalInstructingOrgName));
        when(organisationQueryService.getOrganisationNamesForIds(createOrgIdList(associatedOrganisationId), responseFromView.metadata()))
                .thenReturn(stubbedIdpcOrganisationNameDetails(associatedOrganisationId, additionalInstructingOrganisation, associatedOrgName, additionalInstructingOrgName));
        stubGetAssociatedOrganisationAndPermissions("defence.query.associated-organisation-details-empty.json", associatedOrganisationId.toString());
        stubUserPermissions();
        when(progressionQueryService.getProsecutionCaseDetailById(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getProsecutionCase(false, defendantid.toString())));

        JsonEnvelope response = defenceQueryApi.findClientByCriteria(requestEnvelope);

        //Then
        final JsonObject payload = response.asJsonObject();
        assertThat(payload.getJsonObject(PROSECUTOR), is(CoreMatchers.nullValue()));
        assertThat(payload.getBoolean(IS_CIVIL), is(true));
    }

    @Test
    public void shouldReturnDefenceClientCountIfMultipleDefenceRecordFound() {
        final JsonEnvelope requestEnvelope = createRequestEnvelope();
        final JsonEnvelope responseFromView =   envelopeFrom(requestEnvelope.metadata(), createObjectBuilder().add("defenceClientCount",2).build());
        when(defenceQueryView.findClientByCriteria(any(JsonEnvelope.class))).thenReturn(responseFromView);
        JsonEnvelope response = defenceQueryApi.findClientByCriteria(requestEnvelope);
        final JsonObject payload = response.asJsonObject();
        assertThat(payload.getInt("defenceClientCount"), is(2));

    }

    @Test
    public void shouldReturnIdpcAccessOrganisationsDetailsNoInstructionAndAssociatedOrganisationDetails() {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final String caseUrn = "TVL123NXC";

        final JsonEnvelope requestEnvelope = createRequestEnvelope();
        final JsonEnvelope responseFromView = stubbedSuccessResponseWithNoAssociation(defenceClientId, caseId, defendantid,
                requestEnvelope, caseUrn);
        lenient().when(requester.request(any())).thenReturn(responseFromView);
        when(defenceQueryView.findClientByCriteria(any(JsonEnvelope.class))).thenReturn(responseFromView);
        when(defenceClientEnvelope.payload()).thenReturn(new DefenceClient());
        when(defenceQueryView.getDefenceClientByDefendantId(any())).thenReturn(defenceClientEnvelope);

        stubGetAssociatedOrganisationAndPermissions("defence.query.associated-organisation-details-empty.json", "");
        stubUserPermissions();
        //When
        JsonEnvelope response = defenceQueryApi.findClientByCriteria(requestEnvelope);

        //Then
        final JsonObject payload = response.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantid.toString()));
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(0));
        final JsonArray instructingOrganisation = payload.getJsonArray(INSTRUCTING_ORGANISATIONS);
        assertThat(instructingOrganisation.size(), is(0));
    }

    private void assetInstructingOrganisation(final List<UUID> organisationId, final JsonObject payload) {
        final JsonArray instructingOrganisation = payload.getJsonArray(INSTRUCTING_ORGANISATIONS);
        assertThat(instructingOrganisation.size(), is(organisationId.size()));

        for (int counter = 0; counter < instructingOrganisation.size(); counter++) {
            final JsonObject organisation = instructingOrganisation.getJsonObject(counter);
            assertTrue(organisationId.contains(fromString(organisation.getString(ORGANISATION_ID))));

        }
    }


    @Test
    public void shouldReturnIdpcAccessOrganisationsDetailsAndAssociatedOrganisationDetailsForOrganisationDefendent() {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final String associatedOrgName = "ASSOCIATED_ORG_NAME";
        final String lastAssociatedOrgName = "LAST_ASSOCIATED_ORG_NAME";
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final UUID idpcAccessingOrganisationId_2 = randomUUID();
        final String idpcOrgName_1 = "IDPC_ORG_NAME_1";
        final String idpcOrgName_2 = "IDPC_ORG_NAME_2";
        final UUID additionalInstructingOrganisation = randomUUID();
        final String additionalInstructingOrgName = "ADD LTD";
        final String caseUrn = "TVL123MXC";

        final JsonEnvelope requestEnvelope = createRequestEnvelopeForOrganisationCriminalDefendent();
        final JsonEnvelope responseFromView = stubbedSuccessResponse(defenceClientId, caseId, defendantid,
                associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2, asList(associatedOrganisationId, additionalInstructingOrganisation),
                requestEnvelope, false, caseUrn);
        when(defenceQueryView.findOrganisationClientByCriteria((any(JsonEnvelope.class)))).thenReturn(responseFromView);

        stubGetAssociatedOrganisationAndPermissions("defence.query.associated-organisation-details.json", associatedOrganisationId.toString());
        when(organisationQueryService.getOrganisationOfLoggedInUser(any())).thenReturn(associatedOrganisationId.toString());
        when(organisationQueryService.getOrganisationNamesForIds(createOrgIdList(idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2), responseFromView.metadata()))
                .thenReturn(stubbedIdpcOrganisationNameDetails(idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2, idpcOrgName_1, idpcOrgName_2));
        when(organisationQueryService.getOrganisationNamesForIds(createOrgIdList(lastAssociatedOrganisationId), responseFromView.metadata()))
                .thenReturn(stubbedAssociatedOrganisationNameDetail(lastAssociatedOrganisationId, lastAssociatedOrgName));

        when(organisationQueryService.getOrganisationNamesForIds(createOrgIdList(associatedOrganisationId), responseFromView.metadata()))
                .thenReturn(stubbedAssociatedOrganisationNameDetail(associatedOrganisationId, associatedOrgName));

        when(organisationQueryService.getOrganisationNamesForIds(createOrgIdList(associatedOrganisationId, additionalInstructingOrganisation), responseFromView.metadata()))
                .thenReturn(stubbedIdpcOrganisationNameDetails(associatedOrganisationId, additionalInstructingOrganisation, associatedOrgName, additionalInstructingOrgName));


        //When
        JsonEnvelope response = defenceQueryApi.findOrganisationClientByCriteria(requestEnvelope);

        //Then
        final JsonObject payload = response.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantid.toString()));
        assertThat(payload.getBoolean(IS_CIVIL), is(false));
        assertAssociatedOrganisation(associatedOrganisationId, associatedOrgName, payload);
        assertLastAssociatedOrganisation(lastAssociatedOrganisationId, lastAssociatedOrgName, payload);
        assertIdpcAccessingOrganisationDetails(idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2, idpcOrgName_1, idpcOrgName_2, payload);
        assetInstructingOrganisation(asList(associatedOrganisationId, additionalInstructingOrganisation), payload);
    }

    @Test
    public void shouldReturnOrganisationNameEmptyWhenOrganizationNotFound() {


        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final UUID idpcAccessingOrganisationId_2 = randomUUID();
        final UUID additionalInstructingOrganisation = randomUUID();
        final String caseUrn = "TVL123MXC";

        final JsonEnvelope requestEnvelope = createRequestEnvelope();
        final JsonEnvelope responseFromView = stubbedSuccessResponse(defenceClientId, caseId, defendantid,
                associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2, asList(associatedOrganisationId, additionalInstructingOrganisation),
                requestEnvelope, false, caseUrn);
        when(defenceQueryView.findClientByCriteria(any(JsonEnvelope.class))).thenReturn(responseFromView);
        when(organisationQueryService.getOrganisationNamesForIds(any(List.class), any(Metadata.class))).thenReturn(Collections.emptyList());
        when(defenceClientEnvelope.payload()).thenReturn(new DefenceClient());
        when(defenceQueryView.getDefenceClientByDefendantId(any())).thenReturn(defenceClientEnvelope);
        stubGetAssociatedOrganisationAndPermissions("defence.query.associated-organisation-details-empty.json", associatedOrganisationId.toString());

        //mock static method
        final MockedStatic<PermissionService> mockPermissionService = mockStatic(PermissionService.class);
        mockPermissionService.when(() -> getUserPermissions(any(), any()))
                .thenReturn(singletonList(Permission.permission().withObject(VIEW_DEFENDANT_PERMISSION.getObjectType()).withAction(VIEW_DEFENDANT_PERMISSION.getActionType()).build()));

        //When
        JsonEnvelope response = defenceQueryApi.findClientByCriteria(requestEnvelope);

        //Then
        final JsonObject payload = response.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantid.toString()));
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(0));
        final JsonArray instructingOrganisation = payload.getJsonArray(INSTRUCTING_ORGANISATIONS);
        assertThat(instructingOrganisation.size(), is(2));
        assertThat(payload.getJsonObject(ASSOCIATED_ORGANISATION).getString(ORGANISATION_ID), is(associatedOrganisationId.toString()));
        assertThat(payload.getJsonObject(ASSOCIATED_ORGANISATION).getString(ORGANISATION_NAME), is(""));

        //close static mocking
        mockPermissionService.close();
    }

    @Test
    public void shouldReturnNotFoundIdpcAccessCriminalOrganisationsDetailsAndAssociatedOrganisationDetailsForCivilRespondent() {

        final JsonEnvelope requestEnvelope = createRequestEnvelopeForOrganisationCivilRespondent();
        when(defenceQueryView.findOrganisationClientByCriteria((any(JsonEnvelope.class)))).thenReturn(JsonEnvelope.envelopeFrom(requestEnvelope.metadata(),
                createObjectBuilder().build()));
        //When
        JsonEnvelope response = defenceQueryApi.findOrganisationClientByCriteria(requestEnvelope);
        verify(defenceQueryView).findOrganisationClientByCriteria(captorArg.capture());
        final JsonObject jsonObject = captorArg.getValue().asJsonObject();
        assertTrue(response.payloadIsNull());
        assertThat(jsonObject.getBoolean("isCivil"), is(true));
    }

    @Test
    public void shouldReturnNullWhenNoCriteriaMatchFound() {
        //Given
        final JsonEnvelope requestEnvelope = createRequestEnvelope();
        when(defenceQueryView.findClientByCriteria((any(JsonEnvelope.class)))).thenReturn(envelopeFrom(
                requestEnvelope.metadata(),
                createObjectBuilder().build()));


        //When
        JsonEnvelope response = defenceQueryApi.findClientByCriteria(requestEnvelope);

        //Then
        assertThat(response.payload(), is(NULL));
    }

    @Test
    public void shouldReturnNullWhenNoCriteriaMatchFoundForCivilCase_AndDobIsNotProvidedInQueryParam() {
        //Given
        final JsonEnvelope requestEnvelope = createRequestEnvelopeForCivilWithoutDob();
        when(defenceQueryView.findClientByCriteria((any(JsonEnvelope.class)))).thenReturn(envelopeFrom(
                requestEnvelope.metadata(),
                createObjectBuilder().build()));


        //When
        JsonEnvelope response = defenceQueryApi.findClientByCriteria(requestEnvelope);

        //Then
        assertThat(response.payload(), is(NULL));
    }

    @Test
    void shouldReturnEmptyOrganizationAddressWhenOrganizationDoesntExists() {
        //Given
        final JsonEnvelope requestEnvelope = createRequestEnvelopeForOrganisationCivilRespondent();
        final CaseDefendantsOrganisations caseDefendantsOrganisations = CaseDefendantsOrganisations.caseDefendantsOrganisations()
                .withCaseDefendantOrganisation(CaseDefendantsWithOrganisation.caseDefendantsWithOrganisation()
                        .withDefendants(Collections.singletonList(Defendant.defendant()
                                .withDefendantId(randomUUID())
                                .withAssociatedOrganisation(randomUUID())
                                .build()))
                        .build())
                .build();
        final Metadata metadata = Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName("defence.query.case-defendants-organisation")
                .withUserId(randomUUID().toString())
                .createdAt(now()).build();


        when(caseDefendantsOrganisationsEnvelope.metadata()).thenReturn(metadata);
        when(caseDefendantsOrganisationsEnvelope.payload()).thenReturn(caseDefendantsOrganisations);
        when(defenceQueryView.findDefendantsWithOrganisationsByCaseId((any(JsonEnvelope.class)))).thenReturn(caseDefendantsOrganisationsEnvelope);
        when(usersAndGroupsService.getOrganisationDetails(any(JsonEnvelope.class))).thenReturn(null);

        //When
        JsonEnvelope response = defenceQueryApi.findDefendantsByCaseId(requestEnvelope);

        //Then
        assertFalse(response.payloadAsJsonObject().getJsonObject(CASE_DEFENDANT_ORGANISATION)
                .getJsonArray(DEFENDANTS)
                .get(0)
                .asJsonObject()
                .containsKey(ORGANISATION_ADDRESS));
    }

    @Test
    void shouldReturnOrganizationAddressWhenOrganizationExists() {
        //Given
        final JsonEnvelope requestEnvelope = createRequestEnvelopeForOrganisationCivilRespondent();
        final CaseDefendantsOrganisations caseDefendantsOrganisations = CaseDefendantsOrganisations.caseDefendantsOrganisations()
                .withCaseDefendantOrganisation(CaseDefendantsWithOrganisation.caseDefendantsWithOrganisation()
                        .withDefendants(Collections.singletonList(Defendant.defendant()
                                .withDefendantId(randomUUID())
                                .withAssociatedOrganisation(randomUUID())
                                .build()))
                        .build())
                .build();
        final Metadata metadata = Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName("defence.query.case-defendants-organisation")
                .withUserId(randomUUID().toString())
                .createdAt(now()).build();

        final JsonObjectBuilder organisationJsonObject = createObjectBuilder();
        organisationJsonObject.add(ORGANISATION_NAME, "Organisation Name from usergroups")
                .add(ADDRESS_LINE_1, "Address1")
                .add(ADDRESS_LINE_2, "Address2")
                .add(ADDRESS_LINE_3, "Address3")
                .add(ADDRESS_LINE_4, "Address4")
                .add(ADDRESS_POSTCODE, "AddressPostCode");

        when(caseDefendantsOrganisationsEnvelope.metadata()).thenReturn(metadata);
        when(caseDefendantsOrganisationsEnvelope.payload()).thenReturn(caseDefendantsOrganisations);
        when(defenceQueryView.findDefendantsWithOrganisationsByCaseId((any(JsonEnvelope.class)))).thenReturn(caseDefendantsOrganisationsEnvelope);
        when(usersAndGroupsService.getOrganisationDetails(any(JsonEnvelope.class))).thenReturn(organisationJsonObject.build());

        //When
        JsonObject defendant = defenceQueryApi.findDefendantsByCaseId(requestEnvelope).payloadAsJsonObject().getJsonObject(CASE_DEFENDANT_ORGANISATION)
                .getJsonArray(DEFENDANTS)
                .get(0)
                .asJsonObject();

        //Then
        assertThat(defendant.getString(ORGANISATION_NAME), is("Organisation Name from usergroups"));
        final JsonObject address = defendant.get(ORGANISATION_ADDRESS).asJsonObject();
        assertThat(address.getString(ADDRESS_1), is("Address1"));
        assertThat(address.getString(ADDRESS_2), is("Address2"));
        assertThat(address.getString(ADDRESS_3), is("Address3"));
        assertThat(address.getString(ADDRESS_4), is("Address4"));
        assertThat(address.getString(ADDRESS_POSTCODE), is("AddressPostCode"));
    }

    @Test
    public void shouldReturnErrorMessageIfOtherOrganisationIsAlreadyAssociated() throws IOException {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID organisationIdOfLoggedInUser = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final String associatedOrgName = "ASSOCIATED_ORG_NAME";
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final UUID idpcAccessingOrganisationId_2 = randomUUID();
        final String caseUrn = "TVL123MXC";


        final JsonEnvelope requestEnvelope = createRequestEnvelope();
        final JsonEnvelope responseFromView = stubbedSuccessResponse(defenceClientId, caseId, defendantid,
                associatedOrganisationId, randomUUID(), idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2, asList(associatedOrganisationId, organisationIdOfLoggedInUser),
                requestEnvelope, false, caseUrn);
        when(defenceQueryView.findClientByCriteria((any(JsonEnvelope.class)))).thenReturn(responseFromView);
        when(organisationQueryService.getOrganisationOfLoggedInUser(responseFromView.metadata()))
                .thenReturn(ORGANISATION_ID);
        when(organisationQueryService.getOrganisationNamesForIds(createAssociatedOrganisationId(associatedOrganisationId), responseFromView.metadata()))
                .thenReturn(stubbedAssociatedOrganisationNameDetail(associatedOrganisationId, associatedOrgName));

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.permissions")) {
                responsePayload = createObjectBuilder().build();
            }
            return envelopeFrom(envelope.metadata(), responsePayload);
        });
        stubGetAssociatedOrganisationAndPermissions("defence.query.associated-organisation-details.json", associatedOrganisationId.toString());

        //When
        var e = assertThrows(ForbiddenRequestException.class, () -> defenceQueryApi.findClientByCriteria(requestEnvelope));

        //Then
        assertThat(e.getMessage(), is("Harry & Co LLP already associated"));
    }

    @Test
    public void shouldReturnErrorMessageIfOtherOrganisationIsAlreadyAssociatedForOrganisation() {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID organisationIdOfLoggedInUser = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final String associatedOrgName = "ASSOCIATED_ORG_NAME";
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final UUID idpcAccessingOrganisationId_2 = randomUUID();
        final String caseUrn = "TVL123MXC";


        final JsonEnvelope requestEnvelope = createRequestEnvelopeForOrganisationCriminalDefendent();
        final JsonEnvelope responseFromView = stubbedSuccessResponse(defenceClientId, caseId, defendantid,
                associatedOrganisationId, randomUUID(), idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2, asList(associatedOrganisationId, organisationIdOfLoggedInUser),
                requestEnvelope, false, caseUrn);
        when(defenceQueryView.findOrganisationClientByCriteria((any(JsonEnvelope.class)))).thenReturn(responseFromView);

        when(organisationQueryService.getOrganisationOfLoggedInUser(responseFromView.metadata()))
                .thenReturn(ORGANISATION_ID);
        when(organisationQueryService.getOrganisationNamesForIds(createAssociatedOrganisationId(associatedOrganisationId), responseFromView.metadata()))
                .thenReturn(stubbedAssociatedOrganisationNameDetail(associatedOrganisationId, associatedOrgName));
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.permissions")) {
                responsePayload = createObjectBuilder().build();
            }
            return envelopeFrom(envelope.metadata(), responsePayload);
        });
        stubGetAssociatedOrganisationAndPermissions("defence.query.associated-organisation-details.json", associatedOrganisationId.toString());

        //When
        var e = assertThrows(ForbiddenRequestException.class, () -> defenceQueryApi.findOrganisationClientByCriteria(requestEnvelope));

        //then
        assertThat(e.getMessage(), is("Harry & Co LLP already associated"));
    }


    @Test
    public void shouldReturnIDPCDetails() {
        final UUID defenceClientId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        final UUID idpcDetailsId = randomUUID();
        final UUID materialId = randomUUID();
        final String idpcDocumentName = randomUUID().toString() + ".pdf";
        final String accessTimestamp = ZonedDateTimes.toString(ZonedDateTime.now());
        final AtomicInteger requestInvocationCount = new AtomicInteger(0);

        when(defenceQueryView.getDefenceClientIdpc(any(JsonEnvelope.class))).thenAnswer(invocationOnMock -> {

            requestInvocationCount.incrementAndGet();
            final Envelope envelope = (Envelope) invocationOnMock.getArguments()[0];
            JsonObject request = (JsonObject) envelope.payload();
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("defence.query.view.defence-client-idpc")) {
                responsePayload = createObjectBuilder()
                        .add("defenceClientId", request.getString("defenceClientId"))
                        .add("userId", request.getString("userId"))
                        .add("organisationId", request.getString("organisationId"))
                        .add("materialId", materialId.toString())
                        .add("idpcDetailsId", idpcDetailsId.toString())
                        .add("idpcDocumentName", idpcDocumentName)
                        .add("accessTimestamp", accessTimestamp)
                        .build();
            }
            return envelopeFrom(envelope.metadata(), responsePayload);
        });
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {

            requestInvocationCount.incrementAndGet();
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            JsonObject request = envelope.payloadAsJsonObject();
            JsonObject responsePayload = null;
            if (envelope.metadata().name().equals("usersgroups.get-organisation-name-for-user")) {
                responsePayload = createObjectBuilder()
                        .add("userId", request.getString("userId"))
                        .add("organisationId", orgId.toString())
                        .build();
            }
            return envelopeFrom(envelope.metadata(), responsePayload);
        });

        final JsonEnvelope requestEnvelope = envelopeFrom(Envelope.metadataBuilder()
                        .withUserId(userId.toString())
                        .withName("defence.query.defence-client-idpc")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add("defenceClientId", defenceClientId.toString())
                        .add("userId", userId.toString())
                        .build());

        JsonEnvelope response = defenceQueryApi.getDefenceClientIdpc(requestEnvelope);

        //requestors should have been invoked twice
        assertThat(requestInvocationCount.intValue(), is(2));

        final JsonObject payload = response.asJsonObject();
        assertThat(payload.getString("userId"), is(userId.toString()));
        assertThat(payload.getString("defenceClientId"), is(defenceClientId.toString()));
        assertThat(payload.getString("materialId"), is(materialId.toString()));
        assertThat(payload.getString("idpcDetailsId"), is(idpcDetailsId.toString()));
        assertThat(payload.getString("idpcDocumentName"), is(idpcDocumentName.toString()));
        assertThat(payload.getString("accessTimestamp"), is(accessTimestamp));
        assertThat(payload.getString("organisationId"), is(orgId.toString()));
    }

    @Test
    public void shouldHandleIssueIDPCAccessed() {
        final UUID defenceClientId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        final UUID idpcDetailsId = randomUUID();
        final String accessTimestamp = ZonedDateTimes.toString(ZonedDateTime.now());

        final JsonEnvelope requestEnvelope = envelopeFrom(Envelope.metadataBuilder()
                        .withUserId(userId.toString())
                        .withName("defence.query.record-idpc-access")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add("defenceClientId", defenceClientId.toString())
                        .add("userId", userId.toString())
                        .add("organisationId", orgId.toString())
                        .add("idpcDetailsId", idpcDetailsId.toString())
                        .add("accessTimestamp", accessTimestamp)
                        .build());

        defenceQueryApi.issueRecordIdpcAccess(requestEnvelope);
        verify(sender, times(1)).send(senderCaptor.capture());

        Envelope sentEnvelope = senderCaptor.getValue();
        Metadata metadata = sentEnvelope.metadata();
        assertThat(metadata.name(), is("defence.command.record-access-to-idpc"));

        final JsonObject payload = (JsonObject) sentEnvelope.payload();
        assertThat(payload.getString("userId"), is(userId.toString()));
        assertThat(payload.getString("defenceClientId"), is(defenceClientId.toString()));
        assertThat(payload.getString("idpcDetailsId"), is(idpcDetailsId.toString()));
        assertThat(payload.getString("accessTimestamp"), is(accessTimestamp));
        assertThat(payload.getString("organisationId"), is(orgId.toString()));
    }

    @Test
    public void shouldHandleGetCaseByPersonDefendant() {
        final JsonEnvelope requestEnvelope = createRequestEnvelopeForgetCaseByPersonDefendant();
        final JsonEnvelope responseFromView =   envelopeFrom(requestEnvelope.metadata(),
                createObjectBuilder().add("caseIds", createArrayBuilder().add(randomUUID().toString()).build())
                        .add("defendants", createArrayBuilder().add(randomUUID().toString()).build()).build());
        when(defenceQueryView.getCasesByPersonDefendant(any(JsonEnvelope.class))).thenReturn(responseFromView);

        JsonEnvelope response = defenceQueryApi.getCasesByPersonDefendant(requestEnvelope);
        final JsonObject payload = response.asJsonObject();
        assertThat(payload.getJsonArray("caseIds").size(), is(1));
        assertThat(payload.getJsonArray("defendants").size(), is(1));

    }

    @Test
    public void shouldHandleGetCaseByOrganisationDefendant() {
        final JsonEnvelope requestEnvelope = createRequestEnvelopeForgetCaseByOrganisationDefendant();
        final JsonEnvelope responseFromView =   envelopeFrom(requestEnvelope.metadata(),
                createObjectBuilder().add("caseIds", createArrayBuilder().add(randomUUID().toString()).build())
                        .add("defendants", createArrayBuilder().add(randomUUID().toString()).build()).build());
        when(defenceQueryView.getCasesByOrganisationDefendant(any(JsonEnvelope.class))).thenReturn(responseFromView);

        JsonEnvelope response = defenceQueryApi.getCasesByOrganisationDefendant(requestEnvelope);
        final JsonObject payload = response.asJsonObject();
        assertThat(payload.getJsonArray("caseIds").size(), is(1));
        assertThat(payload.getJsonArray("defendants").size(), is(1));

    }


    @Test
    public void shouldReturnNoDefendantsWhenProsecutionCaseIsNotPresent() {

        final String caseId = randomUUID().toString();

        when(progressionQueryService.getProsecutionCaseDetailById(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.empty());

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("defence.query.eligible-for-online-plea").build(),
                createObjectBuilder().add("caseId", caseId).build());

        final JsonObject response = defenceQueryApi.getEligibleDefendants(envelope).payloadAsJsonObject();

        MatcherAssert.assertThat(response.getJsonArray("defendants").size(), is(0));
    }

    @Test
    public void shouldReturnNoDefendantsWhenHearingsAreNotAvailableForProsecutionCase() {

        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();
        final String offenceId = randomUUID().toString();

        when(progressionQueryService.getProsecutionCaseDetailById(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getProsecutionCase(caseId, defendantId, offenceId, "Others")));
        when(progressionQueryService.getHearingsForProsecutionCase(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.empty());

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("defence.query.eligible-for-online-plea").build(),
                createObjectBuilder().add("caseId", caseId).build());

        final JsonObject response = defenceQueryApi.getEligibleDefendants(envelope).payloadAsJsonObject();

        MatcherAssert.assertThat(response.getJsonArray("defendants").size(), is(0));
    }

    @Test
    public void shouldReturnNoDefendantsWhenThereIsNoFirstHearingForProsecutionCase() {

        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();
        final String offenceId = randomUUID().toString();

        when(progressionQueryService.getProsecutionCaseDetailById(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getProsecutionCase(caseId, defendantId, offenceId, "Others")));
        when(progressionQueryService.getHearingsForProsecutionCase(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getHearingsForAProsecutionCase(caseId, defendantId, offenceId)));
        when(progressionQueryService.getHearing(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.empty());

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("defence.query.eligible-for-online-plea").build(),
                createObjectBuilder().add("caseId", caseId).build());

        final JsonObject response = defenceQueryApi.getEligibleDefendants(envelope).payloadAsJsonObject();

        MatcherAssert.assertThat(response.getJsonArray("defendants").size(), is(0));
    }

    @Test
    public void shouldReturnNoDefendantsWhenHearingDayInThePast() {

        final String hearingId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();
        final String offenceId = randomUUID().toString();

        when(progressionQueryService.getProsecutionCaseDetailById(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getProsecutionCase(caseId, defendantId, offenceId, "Others")));
        when(progressionQueryService.getHearingsForProsecutionCase(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getHearingsForAProsecutionCase(caseId, defendantId, offenceId)));
        when(progressionQueryService.getHearing(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getHearing(hearingId, ZonedDateTime.now().minusDays(1).toString())));

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("defence.query.eligible-for-online-plea").build(),
                createObjectBuilder().add("caseId", caseId).build());

        final JsonObject response = defenceQueryApi.getEligibleDefendants(envelope).payloadAsJsonObject();

        MatcherAssert.assertThat(response.getJsonArray("defendants").size(), is(0));
    }

    @Test
    public void shouldReturnNoDefendantsWhenHearingDayInTheFutureAndWithinFifteenDays() {

        final String hearingId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();
        final String offenceId = randomUUID().toString();

        when(progressionQueryService.getProsecutionCaseDetailById(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getProsecutionCase(caseId, defendantId, offenceId, "Others")));
        when(progressionQueryService.getHearingsForProsecutionCase(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getHearingsForAProsecutionCase(caseId, defendantId, offenceId)));
        when(progressionQueryService.getHearing(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getHearing(hearingId, ZonedDateTime.now().plusDays(10).toString())));
        when(calendarService.daysBetweenExcludeHolidays(any(), any(), any()))
                .thenReturn(10L);

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("defence.query.eligible-for-online-plea").build(),
                createObjectBuilder().add("caseId", caseId).build());

        final JsonObject response = defenceQueryApi.getEligibleDefendants(envelope).payloadAsJsonObject();

        MatcherAssert.assertThat(response.getJsonArray("defendants").size(), is(0));
    }

    @Test
    public void shouldReturnDefendantsWhenHearingDayInTheFutureAndOverFifteenDaysAndOffenceTypeIsIndictable() {

        final String hearingId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();
        final String offenceId = randomUUID().toString();

        when(progressionQueryService.getProsecutionCaseDetailById(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getProsecutionCase(caseId, defendantId, offenceId, "Indictable")));
        when(progressionQueryService.getHearingsForProsecutionCase(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getHearingsForAProsecutionCase(caseId, defendantId, offenceId)));
        when(progressionQueryService.getHearing(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getHearing(hearingId, ZonedDateTime.now().plusDays(19).toString())));
        when(calendarService.daysBetweenExcludeHolidays(any(), any(), any())).thenReturn(19L);

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("defence.query.eligible-for-online-plea").build(),
                createObjectBuilder().add("caseId", caseId).build());

        final JsonObject response = defenceQueryApi.getEligibleDefendants(envelope).payloadAsJsonObject();

        MatcherAssert.assertThat(response.getJsonArray("defendants").size(), is(1));
    }

    @Test
    public void shouldReturnDefendantsWhenHearingDayInTheFutureAndOverFifteenDaysAndOffenceTypeIsEitherWay() {

        final String hearingId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String defendantId = randomUUID().toString();
        final String offenceId = randomUUID().toString();

        when(progressionQueryService.getProsecutionCaseDetailById(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getProsecutionCase(caseId, defendantId, offenceId, "Either Way")));
        when(progressionQueryService.getHearingsForProsecutionCase(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getHearingsForAProsecutionCase(caseId, defendantId, offenceId)));
        when(progressionQueryService.getHearing(any(JsonEnvelope.class), anyString()))
                .thenReturn(Optional.of(getHearing(hearingId, ZonedDateTime.now().plusDays(19).toString())));
        when(calendarService.daysBetweenExcludeHolidays(any(), any(), any())).thenReturn(19L);

        final JsonEnvelope envelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName("defence.query.eligible-for-online-plea").build(),
                createObjectBuilder().add("caseId", caseId).build());

        final JsonObject response = defenceQueryApi.getEligibleDefendants(envelope).payloadAsJsonObject();

        MatcherAssert.assertThat(response.getJsonArray("defendants").size(), is(1));
    }

    private void assertIdpcAccessingOrganisationDetails(final UUID idpcAccessingOrganisationId_1, final UUID idpcAccessingOrganisationId_2, final String idpcOrgName_1, final String idpcOrgName_2, final JsonObject payload) {
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(2));

        final JsonObject arrayObjectOne = idpcAccessingOrganisations.getJsonObject(0);
        assertThat(arrayObjectOne.getInt(ORDER), CoreMatchers.anyOf(is(1), is(2)));
        assertThat(arrayObjectOne.getString(ORGANISATION_ID), CoreMatchers.anyOf(is(idpcAccessingOrganisationId_1.toString()), is(idpcAccessingOrganisationId_2.toString())));
        assertThat(arrayObjectOne.getString(NAME), CoreMatchers.anyOf(is(idpcOrgName_1), is(idpcOrgName_2)));

        final JsonObject arrayObjectTwo = idpcAccessingOrganisations.getJsonObject(1);
        assertThat(arrayObjectTwo.getInt(ORDER), CoreMatchers.anyOf(is(1), is(2)));
        assertThat(arrayObjectTwo.getString(ORGANISATION_ID), CoreMatchers.anyOf(is(idpcAccessingOrganisationId_1.toString()), is(idpcAccessingOrganisationId_2.toString())));
        assertThat(arrayObjectTwo.getString(NAME), CoreMatchers.anyOf(is(idpcOrgName_1), is(idpcOrgName_2)));
    }

    protected void assertAssociatedOrganisation(final UUID associatedOrganisationId, final String associatedOrgName, final JsonObject payload) {
        assertAssociatedOrgDetails(associatedOrganisationId, associatedOrgName, payload.getJsonObject(ASSOCIATED_ORGANISATION));
    }

    protected void assertLastAssociatedOrganisation(final UUID associatedOrganisationId, final String associatedOrgName, final JsonObject payload) {
        assertAssociatedOrgDetails(associatedOrganisationId, associatedOrgName, payload.getJsonObject(LAST_ASSOCIATED_ORGANISATION));
    }

    private void assertAssociatedOrgDetails(final UUID associatedOrganisationId, final String associatedOrgName, final JsonObject associatedOrganisation) {
        assertThat(associatedOrganisation.getString(ORGANISATION_ID), is(associatedOrganisationId.toString()));
        assertThat(associatedOrganisation.getString(ORGANISATION_NAME), is(associatedOrgName));
    }

    private List<String> createAssociatedOrganisationId(final UUID associatedOrganisationId) {
        List<String> ids = new ArrayList<>();
        ids.add(associatedOrganisationId.toString());
        return ids;
    }

    private List<String> createOrgIdList(final UUID... organisationIds) {
        return Arrays.stream(organisationIds).map(i -> i.toString()).collect(Collectors.toList());
    }

    private JsonEnvelope createRequestEnvelope() {
        return envelopeFrom(Envelope.metadataBuilder()
                        .withName("defence.query.defence-client-id")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(FIRST_NAME, "Donald")
                        .add(LAST_NAME, "Knuth")
                        .add(DOB, "1983-04-20")
                        .add(URN, "55DP0028117")
                        .add(HEARING_DATE, "2022-10-15")
                        .add(IS_CIVIL, true)
                        .build());
    }

    private JsonEnvelope createRequestEnvelopeForCivilWithoutDob() {
        return envelopeFrom(Envelope.metadataBuilder()
                        .withName("defence.query.defence-client-id")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(FIRST_NAME, "Donald")
                        .add(LAST_NAME, "Knuth")
                        .add(URN, "55DP0028117")
                        .add(HEARING_DATE, "2022-10-15")
                        .add(IS_CIVIL, true)
                        .build());
    }

    private JsonEnvelope createRequestEnvelopeForOrganisationCriminalDefendent() {
        return envelopeFrom(Envelope.metadataBuilder()
                        .withName("defence.query.defence-client-id")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(IS_ORGANISATION, true)
                        .add(ORGANISATION_NAME, "Knuth LTD")
                        .add(URN, "55DP0028117")
                        .add(HEARING_DATE, "2022-10-15")
                        .add(IS_CIVIL, false)
                        .build());
    }

    private JsonEnvelope createRequestEnvelopeForOrganisationCivilRespondent() {
        return envelopeFrom(Envelope.metadataBuilder()
                        .withName("defence.query.defence-client-id")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(IS_ORGANISATION, true)
                        .add(ORGANISATION_NAME, "Knuth LTD")
                        .add(URN, "55DP0028117")
                        .add(HEARING_DATE, "2022-10-15")
                        .add(IS_CIVIL, true)
                        .add(WITH_ADDRESS, true)
                        .build());
    }

    private List<OrganisationNameVO> stubbedAssociatedOrganisationNameDetail(final UUID associatedOrganisationId, final String organisationName) {
        List<OrganisationNameVO> organisationNameVOList = new ArrayList<>();
        OrganisationNameVO organisationNameVO = new OrganisationNameVO(associatedOrganisationId.toString(), organisationName);
        organisationNameVOList.add(organisationNameVO);
        return organisationNameVOList;
    }

    private List<OrganisationNameVO> stubbedIdpcOrganisationNameDetails(final UUID idpcAccessingOrganisationId_1,
                                                                        final UUID idpcAccessingOrganisationId_2,
                                                                        final String idpcAccessingOrganisationName_1,
                                                                        final String idpcAccessingOrganisationName_2) {
        List<OrganisationNameVO> organisationNameVOList = new ArrayList<>();
        OrganisationNameVO organisationNameVO = new OrganisationNameVO(idpcAccessingOrganisationId_1.toString(), idpcAccessingOrganisationName_1);
        organisationNameVOList.add(organisationNameVO);
        organisationNameVO = new OrganisationNameVO(idpcAccessingOrganisationId_2.toString(), idpcAccessingOrganisationName_2);
        organisationNameVOList.add(organisationNameVO);
        return organisationNameVOList;
    }


    private JsonEnvelope stubbedSuccessResponseWithNoAssociation(final UUID defenceClientId,
                                                                 final UUID caseId,
                                                                 final UUID defendantid,
                                                                 final JsonEnvelope requestEnvelope,
                                                                 final String caseUrn) {
        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder();
        jsonObjectBuilder.add(DEFENCE_CLIENT_ID, defenceClientId.toString());
        jsonObjectBuilder.add(CASE_ID, caseId.toString());
        jsonObjectBuilder.add(DEFENDANT_ID, defendantid.toString());
        jsonObjectBuilder.add(PROSECUTION_AUTHORITY_CODE, "TFL");
        jsonObjectBuilder.add(LOCKED_BY_REP_ORDER, false);
        jsonObjectBuilder.add(CASE_URN, caseUrn);

        return envelopeFrom(
                requestEnvelope.metadata(),
                jsonObjectBuilder.build());
    }

    private JsonEnvelope stubbedSuccessResponse(final UUID defenceClientId,
                                                final UUID caseId,
                                                final UUID defendantid,
                                                final UUID associatedOrganisationId,
                                                final UUID lastAssociatedOrganisationId,
                                                final UUID idpcAccessingOrganisationId_1,
                                                final UUID idpcAccessingOrganisationId_2,
                                                final List<UUID> instructingOrganisation,
                                                final JsonEnvelope requestEnvelope,
                                                final boolean lockedByRepOrder,
                                                final String caseUrn) {
        final JsonObjectBuilder jsonObjectBuilder = createObjectBuilder();
        jsonObjectBuilder.add(DEFENCE_CLIENT_ID, defenceClientId.toString());
        jsonObjectBuilder.add(CASE_ID, caseId.toString());
        jsonObjectBuilder.add(CASE_URN, caseUrn);
        jsonObjectBuilder.add(DEFENDANT_ID, defendantid.toString());
        jsonObjectBuilder.add(LOCKED_BY_REP_ORDER, lockedByRepOrder);
        jsonObjectBuilder.add(PROSECUTION_AUTHORITY_CODE, "TFL");
        jsonObjectBuilder.add(IDPC_ACCESSING_ORGANISATIONS,
                buildIdpcAccessOrgDetails(idpcAccessingOrganisationId_1, idpcAccessingOrganisationId_2));
        jsonObjectBuilder.add(ASSOCIATED_ORGANISATION,
                buildAssociatedOrganisation(associatedOrganisationId));
        jsonObjectBuilder.add(LAST_ASSOCIATED_ORGANISATION,
                buildAssociatedOrganisation(lastAssociatedOrganisationId));
        jsonObjectBuilder.add(INSTRUCTING_ORGANISATIONS,
                buildInstructions(instructingOrganisation));
        return envelopeFrom(
                requestEnvelope.metadata(),
                jsonObjectBuilder.build());
    }

    private JsonObject buildAssociatedOrganisation(final UUID associatedOrganisationId) {
        return createObjectBuilder()
                .add(ORGANISATION_ID, associatedOrganisationId.toString())
                .build();
    }

    private JsonArray buildIdpcAccessOrgDetails(final UUID idpcAccessingOrganisationId_1,
                                                final UUID idpcAccessingOrganisationId_2) {
        final JsonArrayBuilder jsonArrayBuilder = createArrayBuilder();

        JsonObjectBuilder objectBuilder = createObjectBuilder()
                .add(ORDER, 1)
                .add(ORGANISATION_ID, idpcAccessingOrganisationId_1.toString());
        jsonArrayBuilder.add(objectBuilder);

        objectBuilder = createObjectBuilder()
                .add(ORDER, 2)
                .add(ORGANISATION_ID, idpcAccessingOrganisationId_2.toString());
        jsonArrayBuilder.add(objectBuilder);

        return jsonArrayBuilder.build();
    }

    private JsonArray buildInstructions(final List<UUID> instructingOrganisation) {
        final JsonArrayBuilder jsonArrayBuilder = createArrayBuilder();
        instructingOrganisation.forEach(orgdid -> {
            jsonArrayBuilder.add(this.buildInstruction(orgdid));
        });

        return jsonArrayBuilder.build();
    }

    private JsonObjectBuilder buildInstruction(final UUID organisationId) {
        return createObjectBuilder()
                .add(INSTRUCTION_ID, randomUUID().toString())
                .add(ORGANISATION_ID, organisationId.toString())
                .add(USER_ID, randomUUID().toString())
                .add(NAME, "Test Ltd")
                .add(INSTRUCTION_DATE, LocalDate.now().toString());

    }

    private void stubGetAssociatedOrganisationAndPermissions(final String fileName, final String organisationId) {

        when(defenceAssociationQueryApi.getAssociatedOrganisation(any(JsonEnvelope.class))).thenAnswer(invocationOnMock -> {
            final String payload = readFileToString(new File(this.getClass().getClassLoader().getResource(fileName).getFile())).replace("ORG_ID", organisationId);
            JsonObject associatedOrgPayload = new DefaultJsonParser().toObject(payload, JsonObject.class);
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            return envelopeFrom(envelope.metadata(), associatedOrgPayload);
        });

    }

    private void stubUserPermissions() {
        when(requester.request(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final JsonEnvelope envelope = (JsonEnvelope) invocationOnMock.getArguments()[0];

            final String payload = readFileToString(new File(this.getClass().getClassLoader().getResource("advocate-user-permissions.json").getFile()));
            JsonObject associatedOrgPayload = new DefaultJsonParser().toObject(payload, JsonObject.class);
            return envelopeFrom(envelope.metadata(), associatedOrgPayload);

        });
    }

    private JsonObject getProsecutionCase(boolean withProsecutor, String defendantId) {

        JsonObjectBuilder prosecutionCase = createObjectBuilder()
                .add("id", randomUUID().toString());
        if (withProsecutor) {
            prosecutionCase.add(PROSECUTOR, createObjectBuilder().add("prosecutorCode", "CPS").add("prosecutorId", randomUUID().toString()).build());
        }
        JsonArrayBuilder associatedPersons = createArrayBuilder().add(
                createObjectBuilder().add("person", createObjectBuilder().add("title", "Mr")
                        .build()));

        prosecutionCase.add(DEFENDANTS, createArrayBuilder().add(createObjectBuilder().add(ASSOCIATED_PERSONS, associatedPersons).add(DEFENDANT_ID, defendantId).add("id", defendantId).build()));
        return createObjectBuilder().add("prosecutionCase", prosecutionCase.build()).build();
    }

    private JsonEnvelope createRequestEnvelopeForgetCaseByPersonDefendant() {
        return envelopeFrom(Envelope.metadataBuilder()
                        .withName("defence.query.get-case-by-person-defendant")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(FIRST_NAME, "Donald")
                        .add(LAST_NAME, "Knuth")
                        .add(DOB, "1983-04-20")
                        .add(IS_CIVIL, false)
                        .build());
    }

    private JsonEnvelope createRequestEnvelopeForgetCaseByOrganisationDefendant() {
        return envelopeFrom(Envelope.metadataBuilder()
                        .withName("defence.query.get-case-by-organisation-defendant")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(ORGANISATION_NAME, "Knuth LTD")
                        .add(IS_CIVIL, false)
                        .build());
    }

    private JsonObject getProsecutionCase(final String caseId, final String defendantId, final String offenceId, final String modeOfTrial) {

        return createObjectBuilder().add("prosecutionCase",createObjectBuilder()
                .add("id", caseId)
                .add("defendants", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("id", defendantId)
                                .add("offences", createArrayBuilder()
                                        .add(createObjectBuilder()
                                                .add("id", offenceId)
                                                .add("modeOfTrial", modeOfTrial)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build()).build();

    }

    private JsonObject getHearingsForAProsecutionCase(final String caseId, final String defendantId, final String offenceId) {

        return createObjectBuilder()
                .add("hearingTypes", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("hearingId", randomUUID().toString())
                                .add("type", "First hearing")
                                .build())
                        .add(createObjectBuilder()
                                .add("hearingId", randomUUID().toString())
                                .add("type", "Sentence")
                                .build())
                        .build())
                .build();

    }

    private JsonObject getHearing(final String hearingId, final String hearingDay) {

        return createObjectBuilder()
                .add("hearing", createObjectBuilder()
                        .add("id", hearingId)
                        .add("hearingDays", createArrayBuilder()
                                .add(createObjectBuilder()
                                        .add("sittingDay", hearingDay)
                                        .add("listedDurationMinutes", 0)
                                        .build())
                                .add(createObjectBuilder()
                                        .add("sittingDay", hearingDay)
                                        .add("listedDurationMinutes", 0)
                                        .build())
                                .build())
                        .build())
                .build();

    }

}