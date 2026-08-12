package uk.gov.moj.cpp.defence.query.view;

import static java.lang.Boolean.FALSE;
import static java.util.Arrays.asList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.justice.cps.defence.ErrorCode.CASE_NOT_FOUND;
import static uk.gov.justice.cps.defence.ErrorCode.ORGANISATION_NOT_PROSECUTING_AUTHORITY;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.cps.defence.CaseForProsecutorAssignment;
import uk.gov.justice.cps.defence.SearchCaseByUrn;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.IdpcDetails;
import uk.gov.moj.cpp.defence.service.ProgressionService;
import uk.gov.moj.cpp.defence.service.ReferenceDataService;
import uk.gov.moj.cpp.defence.service.UsersGroupQueryService;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.ws.rs.InternalServerErrorException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefenceQueryViewTest {

    private static final String DOB = "dateOfBirth";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String URN = "urn";
    private static final String IS_CIVIL = "isCivil";
    private static final String ORGANISATION_NAME = "organisationName";
    private static final String DEFENCE_CLIENT_ID = "defenceClientId";
    private static final String ORGANISATION_ID = "organisationId";
    private static final String PROSECUTING_AUTHORITY = "TFL";
    public static final String CASE_ID = "caseId";
    public static final String DEFENDANT_ID = "defendantId";
    public static final String IDPC_ACCESSING_ORGANISATIONS = "idpcAccessingOrganisations";
    public static final String ASSOCIATED_ORGANISATION = "associatedOrganisation";
    public static final String LAST_ASSOCIATED_ORGANISATION = "lastAssociatedOrganisation";
    private static final String HEARING_DATE = "hearingDate";

    @Mock
    DefenceQueryService defenceQueryService;

    @InjectMocks
    DefenceQueryView defenceQueryView;

    @Mock
    DefenceClientRepository defenceClientRepository;
    @Mock
    private ProgressionService progressionService;
    @Mock
    private ReferenceDataService referenceDataService;
    @Mock
    private UsersGroupQueryService usersGroupQueryService;
    @Mock
    private Envelope<SearchCaseByUrn> searchCaseByUrnEnvelope;
    @Mock
    private Metadata metadata;

    @Test
    public void getIdpcForDefenceClient() {

        final UUID defenceClientId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        final UUID idpcDetailsId = randomUUID();
        final UUID materialId = randomUUID();
        final String idpcDocumentName = randomUUID().toString() + ".pdf";


        final JsonEnvelope requestEnvelope = JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withUserId(userId.toString())
                        .withName("defence.query.view.defence-client-idpc")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add("defenceClientId", defenceClientId.toString())
                        .add("userId", userId.toString())
                        .add("organisationId", orgId.toString())
                        .build());

        DefenceClient defenceClient = generateDefenceClient(defenceClientId);
        when(defenceQueryService.hasSomeUserDeclaredAnInstructionForDefenceClientForOrganisation(defenceClientId, orgId)).thenReturn(true);
        when(defenceQueryService.getIDPCDetailsForDefenceClient(defenceClientId)).thenReturn(new IdpcDetails(idpcDetailsId, defenceClientId, new uk.gov.moj.cpp.defence.IdpcDetails(materialId, 12, LocalDate.now(), "12 Kb"), idpcDocumentName));
        when(defenceQueryService.getIdpcFileName(defenceClientId)).thenReturn(idpcDocumentName);
        when(defenceClientRepository.findBy(defenceClientId)).thenReturn(defenceClient);

        final ZonedDateTime accessTimestampBaseline = ZonedDateTime.now();

        final JsonEnvelope response = defenceQueryView.getDefenceClientIdpc(requestEnvelope);

        final JsonObject payload = response.asJsonObject();
        assertThat(payload.getString("userId"), is(userId.toString()));
        assertThat(payload.getString("defenceClientId"), is(defenceClientId.toString()));
        assertThat(payload.getString("materialId"), is(materialId.toString()));
        assertThat(payload.getString("idpcDetailsId"), is(idpcDetailsId.toString()));
        assertThat(payload.getString("idpcDocumentName"), is(idpcDocumentName.toString()));
        assertThat(payload.getString("organisationId"), is(orgId.toString()));

        final ZonedDateTime accessTimestamp = ZonedDateTimes.fromString(payload.getString("accessTimestamp"));
        long seconds = ChronoUnit.SECONDS.between(accessTimestampBaseline, accessTimestamp);
        //likely for the difference between then to be less than 100ms but 2 secs prevents a brittle test
        assertTrue(seconds < 2);
    }

    @Test
    public void shouldGetSuccessReturnForFindCaseForProsecutorAssignmentWhenPoliceFlagIsTrue() {
        final UUID caseId = randomUUID();
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();
        final UUID userId = UUID.randomUUID();
        final String urn = randomUUID().toString().toUpperCase();
        final ProsecutionCaseAuthority prosecutionCaseAuthority = getProsecutionCaseAuthority();
        when(searchCaseByUrnEnvelope.payload()).thenReturn(getSearchCaseByUrnPayload(urn));
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("defence.case-for-prosecutor-assignment").build();
        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(false, true);
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);
        when(usersGroupQueryService.validateNonCPSUserOrg(any(), any(), any(), any())).thenReturn(Optional.empty());

        when(defenceQueryService.getCaseId(urn)).thenReturn(caseId);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));

        final Envelope<CaseForProsecutorAssignment> response = defenceQueryView.findCaseForProsecutorAssignment(searchCaseByUrnEnvelope);

        final CaseForProsecutorAssignment caseForProsecutorAssignment = response.payload();
        assertThat(caseForProsecutorAssignment.getCaseId(), is(caseId));
        assertThat(caseForProsecutorAssignment.getCaseURN(), is(urn));
    }

    @Test
    public void shouldGetSuccessReturnForFindCaseForProsecutorAssignmentWhenNonCpsUser() {
        final UUID caseId = randomUUID();
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();
        final UUID userId = UUID.randomUUID();
        final String urn = randomUUID().toString().toUpperCase();
        when(searchCaseByUrnEnvelope.payload()).thenReturn(getSearchCaseByUrnPayload(urn));
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("defence.case-for-prosecutor-assignment").build();
        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(false, true);
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);
        when(usersGroupQueryService.validateNonCPSUserOrg(any(), any(), any(), any())).thenReturn(Optional.of("OrgMatch"));

        when(defenceQueryService.getCaseId(urn)).thenReturn(caseId);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));

        final Envelope<CaseForProsecutorAssignment> response = defenceQueryView.findCaseForProsecutorAssignment(searchCaseByUrnEnvelope);

        final CaseForProsecutorAssignment caseForProsecutorAssignment = response.payload();
        assertThat(caseForProsecutorAssignment.getCaseId(), is(caseId));
        assertThat(caseForProsecutorAssignment.getCaseURN(), is(urn));
    }


    @Test
    public void shouldGetFailureReturnForFindCaseForProsecutorAssignmentWhenNonCpsUser() {
        final UUID caseId = randomUUID();
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();
        final UUID userId = UUID.randomUUID();
        final String urn = randomUUID().toString().toUpperCase();
        final String organisationShortName = "DVLA";

        when(searchCaseByUrnEnvelope.payload()).thenReturn(getSearchCaseByUrnPayload(urn));
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("defence.case-for-prosecutor-assignment").build();
        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(false, true);
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);
        when(usersGroupQueryService.validateNonCPSUserOrg(any(), any(), any(), any())).thenReturn(Optional.of("OrganisationMisMatch"));

        when(defenceQueryService.getCaseId(urn)).thenReturn(caseId);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));

        final Envelope<CaseForProsecutorAssignment> response = defenceQueryView.findCaseForProsecutorAssignment(searchCaseByUrnEnvelope);

        final CaseForProsecutorAssignment caseForProsecutorAssignment = response.payload();
        assertThat(caseForProsecutorAssignment.getError().getErrorCode(), is(ORGANISATION_NOT_PROSECUTING_AUTHORITY));
        assertThat(caseForProsecutorAssignment.getError().getOrganisationName(), is(organisationShortName));
    }

    private JsonObject getProsecutorQueryResponse(final boolean cpsFlag, final boolean policeFlag) {
        return createObjectBuilder()
                .add("cpsFlag", cpsFlag)
                .add("policeFlag", policeFlag)
                .add("shortName", "DVLA")
                .build();
    }

    private ProsecutionCaseAuthority getProsecutionCaseAuthority() {
        return ProsecutionCaseAuthority.prosecutionCaseAuthority()
                .withProsecutionAuthorityId(randomUUID())
                .build();
    }

    @Test
    public void shouldGetSuccessReturnForFindCaseForProsecutorAssignmentWhenCPSFlagIsTrue() {
        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();
        final String urn = randomUUID().toString().toUpperCase();
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();

        when(searchCaseByUrnEnvelope.payload()).thenReturn(getSearchCaseByUrnPayload(urn));
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("defence.case-for-prosecutor-assignment").build();
        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(true, false);

        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);
        when(usersGroupQueryService.validateNonCPSUserOrg(any(), any(), any(), any())).thenReturn(Optional.empty());

        when(defenceQueryService.getCaseId(urn)).thenReturn(caseId);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));

        final Envelope<CaseForProsecutorAssignment> response = defenceQueryView.findCaseForProsecutorAssignment(searchCaseByUrnEnvelope);

        final CaseForProsecutorAssignment caseForProsecutorAssignment = response.payload();
        assertThat(caseForProsecutorAssignment.getCaseId(), is(caseId));
        assertThat(caseForProsecutorAssignment.getCaseURN(), is(urn));
    }

    private SearchCaseByUrn getSearchCaseByUrnPayload(final String urn) {
        return SearchCaseByUrn.searchCaseByUrn()
                .withCaseUrn(urn)
                .build();
    }

    @Test
    public void shouldGetFailReturnForFindCaseForProsecutorAssignmentWhenCaseIsNotPoliceOrCPS() {
        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();
        final String urn = randomUUID().toString().toUpperCase();
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();
        final String organisationShortName = "DVLA";
        when(searchCaseByUrnEnvelope.payload()).thenReturn(getSearchCaseByUrnPayload(urn));
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("defence.case-for-prosecutor-assignment").build();
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);
        when(usersGroupQueryService.validateNonCPSUserOrg(any(), any(), any(), any())).thenReturn(Optional.of("OrganisationMisMatch"));

        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(false, false);

        when(searchCaseByUrnEnvelope.payload()).thenReturn(getSearchCaseByUrnPayload(urn));
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);
        when(defenceQueryService.getCaseId(urn)).thenReturn(caseId);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));

        final Envelope<CaseForProsecutorAssignment> response = defenceQueryView.findCaseForProsecutorAssignment(searchCaseByUrnEnvelope);

        final CaseForProsecutorAssignment caseForProsecutorAssignment = response.payload();
        assertThat(caseForProsecutorAssignment.getCaseId(), nullValue());
        assertThat(caseForProsecutorAssignment.getCaseURN(), nullValue());
        assertThat(caseForProsecutorAssignment.getError().getErrorCode(), is(ORGANISATION_NOT_PROSECUTING_AUTHORITY));
        assertThat(caseForProsecutorAssignment.getError().getOrganisationName(), is(organisationShortName));
    }

    @Test
    public void shouldGetFailReturnForFindCaseForProsecutorAssignmentWhenCaseNotFound() {
        final String urn = randomUUID().toString();
        final UUID userId = randomUUID();
        when(searchCaseByUrnEnvelope.payload()).thenReturn(getSearchCaseByUrnPayload(urn));
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("defence.case-for-prosecutor-assignment").build();

        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);

        final Envelope<CaseForProsecutorAssignment> response = defenceQueryView.findCaseForProsecutorAssignment(searchCaseByUrnEnvelope);

        final CaseForProsecutorAssignment caseForProsecutorAssignment = response.payload();
        assertThat(caseForProsecutorAssignment.getCaseId(), nullValue());
        assertThat(caseForProsecutorAssignment.getCaseURN(), nullValue());
        assertThat(caseForProsecutorAssignment.getError().getErrorCode(), is(CASE_NOT_FOUND));
    }

    @Test
    public void shouldGetSuccesslReturnForFindCaseForProsecutorAssignmentWhenNonCps() {
        final String urn = "CC19602397";
        final UUID userId = randomUUID();
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();

        when(searchCaseByUrnEnvelope.payload()).thenReturn(getSearchCaseByUrnPayload(urn));
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("defence.case-for-prosecutor-assignment").build();
        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(false, true);
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);

        when(usersGroupQueryService.validateNonCPSUserOrg(any(), any(), any(), any())).thenReturn(Optional.of("OrgMatch"));


        when(searchCaseByUrnEnvelope.payload()).thenReturn(getSearchCaseByUrnPayload(urn));
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(any(), any())).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));

        when(defenceQueryService.getCaseId(any())).thenReturn(randomUUID());

        final Envelope<CaseForProsecutorAssignment> response = defenceQueryView.findCaseForProsecutorAssignment(searchCaseByUrnEnvelope);

        final CaseForProsecutorAssignment caseForProsecutorAssignment = response.payload();
        assertThat(caseForProsecutorAssignment.getCaseId(), notNullValue());
        assertThat(caseForProsecutorAssignment.getCaseURN(), notNullValue());
    }

    @Test
    public void getIdpcForDefenceClient_throwErrorAsNoInstructionFound() {

        final UUID defenceClientId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();

        final JsonEnvelope requestEnvelope = JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withUserId(userId.toString())
                        .withName("defence.query.view.defence-client-idpc")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add("defenceClientId", defenceClientId.toString())
                        .add("userId", userId.toString())
                        .add("organisationId", orgId.toString())
                        .build());
        when(defenceQueryService.hasSomeUserDeclaredAnInstructionForDefenceClientForOrganisation(defenceClientId, orgId)).thenReturn(false);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId);
        when(defenceClientRepository.findBy(defenceClientId)).thenReturn(defenceClient);

        assertThrows(ForbiddenRequestException.class, () -> defenceQueryView.getDefenceClientIdpc(requestEnvelope));
    }

    @Test
    public void getIdpcForDefenceClient_throwErrorAsIDPCDetailsNotFound() {

        final UUID defenceClientId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();

        final JsonEnvelope requestEnvelope = JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withUserId(userId.toString())
                        .withName("defence.query.view.defence-client-idpc")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add("defenceClientId", defenceClientId.toString())
                        .add("userId", userId.toString())
                        .add("organisationId", orgId.toString())
                        .build());
        when(defenceQueryService.hasSomeUserDeclaredAnInstructionForDefenceClientForOrganisation(defenceClientId, orgId)).thenReturn(true);
        when(defenceQueryService.getIDPCDetailsForDefenceClient(defenceClientId)).thenReturn(null);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId);
        when(defenceClientRepository.findBy(defenceClientId)).thenReturn(defenceClient);

        assertThrows(InternalServerErrorException.class, () -> defenceQueryView.getDefenceClientIdpc(requestEnvelope));
    }

    @Test
    public void shouldReturnIdpcAccessOrganisationAndAssociatedOrganisation() {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final String fName = "Donald";
        final String lName = "Knuth";
        final String urn = "55DP0028117";
        final String dob = "1983-04-20";
        final Optional<Boolean> isCivil = Optional.empty();

        when(defenceQueryService.getClientAndIDPCAccessOrganisations(fName, lName, dob, urn, isCivil))
                .thenReturn(stubbedDefenceClientIdpcAccessOrganisationsDetails(defenceClientId, caseId, defendantid, associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, urn));

        //When
        final JsonEnvelope clientByCriteria = defenceQueryView.findClientByCriteria(createRequestEnvelope(empty()));

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantid.toString()));
        assertAssociatedOrgDetails(associatedOrganisationId, payload, ASSOCIATED_ORGANISATION);
        assertAssociatedOrgDetails(lastAssociatedOrganisationId, payload, LAST_ASSOCIATED_ORGANISATION);
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(1));
    }

    @Test
    public void shouldReturnIdpcAccessOrganisationAndAssociatedOrganisationWithIsCivilTrue() {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final String fName = "Donald";
        final String lName = "Knuth";
        final String urn = "55DP0028117";
        final String dob = "1983-04-20";
        final Optional<Boolean> isCivil = Optional.of(true);

        when(defenceQueryService.getClientAndIDPCAccessOrganisations(fName, lName, dob, urn, isCivil))
                .thenReturn(stubbedDefenceClientIdpcAccessOrganisationsDetails(defenceClientId, caseId, defendantid, associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, urn));

        //When
        final JsonEnvelope clientByCriteria = defenceQueryView.findClientByCriteria(createRequestEnvelope(isCivil));

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantid.toString()));
        assertAssociatedOrgDetails(associatedOrganisationId, payload, ASSOCIATED_ORGANISATION);
        assertAssociatedOrgDetails(lastAssociatedOrganisationId, payload, LAST_ASSOCIATED_ORGANISATION);
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(1));
    }

    @Test
    public void shouldReturnIdpcAccessOrganisationAndAssociatedOrganisation_WithIsCivilTrue_DobAbsent() {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final String fName = "Donald";
        final String lName = "Knuth";
        final String urn = "55DP0028117";
        final String dob = EMPTY;
        final Optional<Boolean> isCivil = Optional.of(true);

        when(defenceQueryService.getClientAndIDPCAccessOrganisations(fName, lName, dob, urn, isCivil))
                .thenReturn(stubbedDefenceClientIdpcAccessOrganisationsDetails(defenceClientId, caseId, defendantid, associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, urn));

        //When
        final JsonEnvelope clientByCriteria = defenceQueryView.findClientByCriteria(createRequestEnvelopeWithoutDob(isCivil));

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantid.toString()));
        assertAssociatedOrgDetails(associatedOrganisationId, payload, ASSOCIATED_ORGANISATION);
        assertAssociatedOrgDetails(lastAssociatedOrganisationId, payload, LAST_ASSOCIATED_ORGANISATION);
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(1));
    }

    @Test
    public void shouldReturnIdpcAccessOrganisationAndAssociatedOrganisationWhileOnlyOneCaseIsAssociated() {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final String urn = "TVL123DX";

        when(defenceQueryService.getClientAndIDPCAccessOrganisations(any(), any(), any(), any(), any()))
                .thenReturn(stubbedDefenceClientIdpcAccessOrganisationsDetails(defenceClientId, caseId, defendantId, associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, urn));
        when(defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(anyString(), anyString(), anyString(), any())).thenReturn(asList(randomUUID()));

        final JsonEnvelope clientByCriteria = defenceQueryView.findClientByCriteria(createRequestEnvelopeWithoutUrn());

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantId.toString()));
        assertAssociatedOrgDetails(associatedOrganisationId, payload, ASSOCIATED_ORGANISATION);
        assertAssociatedOrgDetails(lastAssociatedOrganisationId, payload, LAST_ASSOCIATED_ORGANISATION);
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(1));
    }

    @Test
    public void shouldReturnIdpcAccessOrganisationAndAssociatedOrganisationWhileMultipleCaseAreAssociatedAndOneHearingRecord() {

        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final String urn = "TVL123DX";

        when(defenceQueryService.getClientAndIDPCAccessOrganisations(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(stubbedDefenceClientIdpcAccessOrganisationsDetails(defenceClientId, caseId, defendantId, associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, urn));
        when(defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(anyString(), anyString(), anyString(), any())).thenReturn(asList(randomUUID(), randomUUID()));
        when(defenceQueryService.getCaseDetailsByPersonDefendantAndHearingDate(any(), any(), any(), any(), any(), any())).thenReturn(createHearingResponseWithOneCase());
        when(defenceQueryService.getPersonDefendant(anyString(), anyString(), anyString(), any())).thenReturn(asList(randomUUID()));

        final JsonEnvelope clientByCriteria = defenceQueryView.findClientByCriteria(createRequestEnvelopeWithoutUrn());

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantId.toString()));
        assertAssociatedOrgDetails(associatedOrganisationId, payload, ASSOCIATED_ORGANISATION);
        assertAssociatedOrgDetails(lastAssociatedOrganisationId, payload, LAST_ASSOCIATED_ORGANISATION);
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(1));
    }

    @Test
    public void shouldReturnDefenceClientCountWhileMultipleCaseAreAssociatedAndMultipleHearingRecord() {

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(anyString(), anyString(), anyString(), any())).thenReturn(asList(randomUUID(), randomUUID()));
        when(defenceQueryService.getCaseDetailsByPersonDefendantAndHearingDate(any(), any(), any(), any(), any(), any())).thenReturn(createHearingResponseWithMultipleCase());
        when(defenceQueryService.getPersonDefendant(anyString(), anyString(), anyString(), any())).thenReturn(asList(randomUUID()));

        final JsonEnvelope clientByCriteria = defenceQueryView.findClientByCriteria(createRequestEnvelopeWithoutUrn());

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getInt("defenceClientCount"), is(2));
    }

    @Test
    public void shouldReturnDefenceClientCountWhenMultipleUrnButNoMatchingRecordWithPersonDetailsAndHearingDate() {

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(anyString(), anyString(), anyString(), any())).thenReturn(asList(randomUUID(), randomUUID()));
        when(defenceQueryService.getCaseDetailsByPersonDefendantAndHearingDate(any(), any(), any(), any(), any(), any())).thenReturn(createObjectBuilder()
                .add("prosecutionCases", createArrayBuilder().build()).build());
        when(defenceQueryService.getPersonDefendant(anyString(), anyString(), anyString(), any())).thenReturn(asList(randomUUID()));

        final JsonEnvelope clientByCriteria = defenceQueryView.findClientByCriteria(createRequestEnvelopeWithoutUrn());

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getInt("defenceClientCount"), is(2));
    }

    @Test
    public void shouldReturnIdpcAccessOrganisationAndAssociatedOrganisationForOrganisationClient() {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final String organisationName = "Donald ltd";
        final String urn = "55DP0028117";
        final Optional<Boolean> isCivil = Optional.empty();

        when(defenceQueryService.getClientAndIDPCAccessOrganisations(organisationName, urn, isCivil))
                .thenReturn(stubbedDefenceClientIdpcAccessOrganisationsDetails(defenceClientId, caseId, defendantid, associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, urn));
        //When
        final JsonEnvelope clientByCriteria = defenceQueryView.findOrganisationClientByCriteria(createRequestEnvelopeForOrganisation());

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantid.toString()));
        assertAssociatedOrgDetails(associatedOrganisationId, payload, ASSOCIATED_ORGANISATION);
        assertAssociatedOrgDetails(lastAssociatedOrganisationId, payload, LAST_ASSOCIATED_ORGANISATION);
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(1));
    }

    @Test
    public void shouldReturnIdpcAccessOrganisationAndAssociatedOrganisationForOrganisationClientWhileOnlyOneCaseIsAssociated() {

        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final String urn = "TVL123DX";

        when(defenceQueryService.getClientAndIDPCAccessOrganisations(any(), any(), any()))
                .thenReturn(stubbedDefenceClientIdpcAccessOrganisationsDetails(defenceClientId, caseId, defendantId, associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, urn));

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByOrganisationDefendant(any(), any())).thenReturn(asList(randomUUID()));

        final JsonEnvelope clientByCriteria = defenceQueryView.findOrganisationClientByCriteria(createRequestEnvelopeForOrganisationWithoutUrn());

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantId.toString()));
        assertAssociatedOrgDetails(associatedOrganisationId, payload, ASSOCIATED_ORGANISATION);
        assertAssociatedOrgDetails(lastAssociatedOrganisationId, payload, LAST_ASSOCIATED_ORGANISATION);
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(1));
    }

    @Test
    public void shouldReturnIdpcAccessOrganisationAndAssociatedOrganisationForOrganisationClientWhileMultipleCasesAreAssociatedAndOneHearingRecord() {

        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID defendantid = randomUUID();
        final UUID associatedOrganisationId = randomUUID();
        final UUID lastAssociatedOrganisationId = randomUUID();
        final UUID idpcAccessingOrganisationId_1 = randomUUID();
        final String urn = "TVL123DX";

        when(defenceQueryService.getClientAndIDPCAccessOrganisations(any(), any(), any()))
                .thenReturn(stubbedDefenceClientIdpcAccessOrganisationsDetails(defenceClientId, caseId, defendantid, associatedOrganisationId, lastAssociatedOrganisationId, idpcAccessingOrganisationId_1, urn));

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByOrganisationDefendant(any(), any())).thenReturn(asList(randomUUID(), randomUUID()));
        when(defenceQueryService.getCaseDetailsByOrganisationDefendantAndHearingDate(any(), any(), any(), any())).thenReturn(createHearingResponseWithOneCase());
        when(defenceQueryService.getOrganisationDefendant(any(), any())).thenReturn(asList(randomUUID()));

        final JsonEnvelope clientByCriteria = defenceQueryView.findOrganisationClientByCriteria(createRequestEnvelopeForOrganisationWithoutUrn());

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getString(DEFENCE_CLIENT_ID), is(defenceClientId.toString()));
        assertThat(payload.getString(CASE_ID), is(caseId.toString()));
        assertThat(payload.getString(DEFENDANT_ID), is(defendantid.toString()));
        assertAssociatedOrgDetails(associatedOrganisationId, payload, ASSOCIATED_ORGANISATION);
        assertAssociatedOrgDetails(lastAssociatedOrganisationId, payload, LAST_ASSOCIATED_ORGANISATION);
        final JsonArray idpcAccessingOrganisations = payload.getJsonArray(IDPC_ACCESSING_ORGANISATIONS);
        assertThat(idpcAccessingOrganisations.size(), is(1));
    }

    @Test
    public void shouldReturnDefenceClientCountForOrganisationClientWhileMultipleCasesAreAssociatedAndMultipleHearingRecord() {

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByOrganisationDefendant(any(), any())).thenReturn(asList(randomUUID(), randomUUID()));
        when(defenceQueryService.getCaseDetailsByOrganisationDefendantAndHearingDate(any(), any(), any(), any())).thenReturn(createHearingResponseWithMultipleCase());
        when(defenceQueryService.getOrganisationDefendant(any(), any())).thenReturn(asList(randomUUID()));

        final JsonEnvelope clientByCriteria = defenceQueryView.findOrganisationClientByCriteria(createRequestEnvelopeForOrganisationWithoutUrn());

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getInt("defenceClientCount"), is(2));
    }

    @Test
    public void shouldReturnDefenceClientCountWhenMultipleUrnButNoMatchingRecordWithOrganisationDetailsAndHearingDate() {

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByOrganisationDefendant(any(), any())).thenReturn(asList(randomUUID(), randomUUID()));
        when(defenceQueryService.getCaseDetailsByOrganisationDefendantAndHearingDate(any(), any(), any(), any())).thenReturn(createObjectBuilder()
                .add("prosecutionCases", createArrayBuilder().build()).build());
        when(defenceQueryService.getOrganisationDefendant(any(), any())).thenReturn(asList(randomUUID()));

        final JsonEnvelope clientByCriteria = defenceQueryView.findOrganisationClientByCriteria(createRequestEnvelopeForOrganisationWithoutUrn());

        final JsonObject payload = clientByCriteria.asJsonObject();
        assertThat(payload.getInt("defenceClientCount"), is(2));
    }


    @Test
    public void shouldReturnEmptyValueWhenNoMatchingCriteriaFound() {

        //Given
        final String fName = "Donald";
        final String lName = "Knuth";
        final String urn = "55DP0028117";
        final String dob = "1983-04-20";
        final Optional<Boolean> isCivil = Optional.of(FALSE);

        when(defenceQueryService.getClientAndIDPCAccessOrganisations(fName, lName, dob, urn, isCivil))
                .thenReturn(null);

        //When
        final JsonEnvelope clientByCriteria = defenceQueryView.findClientByCriteria(createRequestEnvelope(of(FALSE)));

        //Then
        final JsonObject payload = clientByCriteria.payloadAsJsonObject();

        assertThat(payload.size(), is(0));
    }

    @Test
    public void shouldReturnEmptyValueWhenNoMatchingRecordWithPersonDetails() {

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(anyString(), anyString(), anyString(), any())).thenReturn(new ArrayList());

        final JsonEnvelope clientByCriteria = defenceQueryView.findClientByCriteria(createRequestEnvelopeWithoutUrn());

        final JsonObject payload = clientByCriteria.payloadAsJsonObject();
        assertThat(payload.size(), is(0));
    }

    @Test
    public void shouldReturnEmptyValueWhenNoMatchingRecordWithOrganisationDetails() {
        when(defenceQueryService.getCasesAssociatedWithDefenceClientByOrganisationDefendant(any(), any())).thenReturn(new ArrayList());

        final JsonEnvelope clientByCriteria = defenceQueryView.findOrganisationClientByCriteria(createRequestEnvelopeForOrganisationWithoutUrn());

        final JsonObject payload = clientByCriteria.payloadAsJsonObject();
        assertThat(payload.size(), is(0));
    }

    @Test
    public void shouldReturnDefenceClientByDefendantId() {

        //Given
        final UUID defenceClientId = randomUUID();
        final UUID defendantId = randomUUID();

        when(defenceClientRepository.findDefenceClientByCriteria(defendantId))
                .thenReturn(generateDefenceClient(defenceClientId));

        //When
        final Envelope<DefenceClient> defenceClientByDefendantId = defenceQueryView.getDefenceClientByDefendantId(createRequestEnvelopeForDefenceClient(defendantId));

        final DefenceClient defenceClient = defenceClientByDefendantId.payload();
        assertThat(defenceClient.getCaseId(), is(notNullValue()));
        assertThat(defenceClient.getDefendantId(), is(notNullValue()));
    }

    @Test
    public void shouldGetCasesByPersonDefendant() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(any(), any(), any(), any(), any())).thenReturn(asList(caseId));
        when(defenceQueryService.getPersonDefendant(any(), any(), any(), any(), any())).thenReturn(asList(defendantId));

        final JsonEnvelope response = defenceQueryView.getCasesByPersonDefendant(createRequestEnvelopeForCaseByPersonDefendant());

        final JsonObject payload = response.asJsonObject();

        assertThat(payload.getJsonArray("caseIds").size(), is(1));
        assertThat(payload.getJsonArray("defendants").size(), is(1));

        final List<UUID> caseIds = payload.getJsonArray("caseIds").stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        final List<UUID> defendants = payload.getJsonArray("defendants").stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        assertThat(caseIds.get(0), is(caseId));
        assertThat(defendants.get(0), is(defendantId));
    }

    @Test
    public void shouldGetCasesByPersonDefendantForCivil() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(any(), any(), any(), any(), any())).thenReturn(asList(caseId));
        when(defenceQueryService.getPersonDefendant(any(), any(), any(), any(), any())).thenReturn(asList(defendantId));

        final JsonEnvelope response = defenceQueryView.getCasesByPersonDefendant(createRequestEnvelopeForCaseByPersonDefendantForCivil());

        final JsonObject payload = response.asJsonObject();

        assertThat(payload.getJsonArray("caseIds").size(), is(1));
        assertThat(payload.getJsonArray("defendants").size(), is(1));

        final List<UUID> caseIds = payload.getJsonArray("caseIds").stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        final List<UUID> defendants = payload.getJsonArray("defendants").stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        assertThat(caseIds.get(0), is(caseId));
        assertThat(defendants.get(0), is(defendantId));
    }

    @Test
    public void shouldGetCasesByPersonDefendantWithDobForCivil() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant(any(), any(), any(), any(), any())).thenReturn(asList(caseId));
        when(defenceQueryService.getPersonDefendant(any(), any(), any(), any(), any())).thenReturn(asList(defendantId));

        final JsonEnvelope response = defenceQueryView.getCasesByPersonDefendant(createRequestEnvelopeForCaseByPersonDefendantWithoutDobForCivil());

        final JsonObject payload = response.asJsonObject();

        assertThat(payload.getJsonArray("caseIds").size(), is(1));
        assertThat(payload.getJsonArray("defendants").size(), is(1));

        final List<UUID> caseIds = payload.getJsonArray("caseIds").stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        final List<UUID> defendants = payload.getJsonArray("defendants").stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        assertThat(caseIds.get(0), is(caseId));
        assertThat(defendants.get(0), is(defendantId));
    }

    @Test
    public void shouldGetCasesByOrganisationDefendant() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();

        when(defenceQueryService.getCasesAssociatedWithDefenceClientByOrganisationDefendant(any(), any(), any())).thenReturn(asList(caseId));
        when(defenceQueryService.getOrganisationDefendant(any(), any(), any())).thenReturn(asList(defendantId));

        final JsonEnvelope response = defenceQueryView.getCasesByOrganisationDefendant(createRequestEnvelopeForCaseByOrganisationDefendant());

        final JsonObject payload = response.asJsonObject();

        assertThat(payload.getJsonArray("caseIds").size(), is(1));
        assertThat(payload.getJsonArray("defendants").size(), is(1));

        final List<UUID> caseIds = payload.getJsonArray("caseIds").stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        final List<UUID> defendants = payload.getJsonArray("defendants").stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(UUID::fromString)
                .collect(Collectors.toList());

        assertThat(caseIds.get(0), is(caseId));
        assertThat(defendants.get(0), is(defendantId));
    }

    private void assertAssociatedOrgDetails(final UUID associatedOrganisationId, final JsonObject payload, final String associationType) {
        final JsonObject associatedOrganisation = payload.getJsonObject(associationType);
        assertThat(associatedOrganisation.getString(ORGANISATION_ID), is(associatedOrganisationId.toString()));
    }

    private DefenceClientIdpcAccessOrganisations stubbedDefenceClientIdpcAccessOrganisationsDetails(
            final UUID defenceClientId,
            final UUID caseId,
            final UUID defendantid,
            final UUID associatedOrganisationId,
            final UUID lastAssociatedOrganisationId,
            final UUID idpcAccessingOrganisationId_1,
            final String urn) {
        AssociatedOrganisationVO associatedOrganisationVO = new AssociatedOrganisationVO(associatedOrganisationId, null);
        AssociatedOrganisationVO lastAssociatedOrganisationVO = new AssociatedOrganisationVO(lastAssociatedOrganisationId, null);
        List<OrderedOrganisationDetailsVO> idpcAccessingOrganisations = new ArrayList<>();
        List<DefenceClientInstructionHistoryVO> listOfInstruction = new ArrayList<>();
        OrderedOrganisationDetailsVO organisationDetailsVO = new OrderedOrganisationDetailsVO(1, idpcAccessingOrganisationId_1, "IDPC_ORG_NAME");
        idpcAccessingOrganisations.add(organisationDetailsVO);
        return new DefenceClientIdpcAccessOrganisations(defenceClientId, caseId, defendantid, associatedOrganisationVO, lastAssociatedOrganisationVO, idpcAccessingOrganisations, listOfInstruction, false, PROSECUTING_AUTHORITY, urn);
    }

    private JsonEnvelope createRequestEnvelope(Optional<Boolean> isCivil) {

        JsonObjectBuilder objectBuilder = createObjectBuilder()
                .add(FIRST_NAME, "Donald")
                .add(LAST_NAME, "Knuth")
                .add(DOB, "1983-04-20")
                .add(HEARING_DATE, "2022-10-11")
                .add(URN, "55DP0028117");

        isCivil.ifPresent(aBoolean -> objectBuilder.add(IS_CIVIL, aBoolean));

        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.query.defence-client-id")
                        .withId(randomUUID())
                        .build(),
                objectBuilder.build());
    }

    private JsonEnvelope createRequestEnvelopeWithoutDob(Optional<Boolean> isCivil) {

        JsonObjectBuilder objectBuilder = createObjectBuilder()
                .add(FIRST_NAME, "Donald")
                .add(LAST_NAME, "Knuth")
                .add(HEARING_DATE, "2022-10-11")
                .add(URN, "55DP0028117");

        isCivil.ifPresent(aBoolean -> objectBuilder.add(IS_CIVIL, aBoolean));

        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.query.defence-client-id")
                        .withId(randomUUID())
                        .build(),
                objectBuilder.build());
    }

    private JsonEnvelope createRequestEnvelopeForOrganisation() {
        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.defence-organisation-client-idpc-access-orgs")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(ORGANISATION_NAME, "Donald ltd")
                        .add(URN, "55DP0028117")
                        .add(HEARING_DATE, "2022-10-11")
                        .build());
    }

    private JsonEnvelope createRequestEnvelopeWithoutUrn() {
        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.query.defence-client-id")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(FIRST_NAME, "Donald")
                        .add(LAST_NAME, "Knuth")
                        .add(DOB, "1983-04-20")
                        .add(HEARING_DATE, "2022-10-11")
                        .build());
    }

    private JsonEnvelope createRequestEnvelopeForOrganisationWithoutUrn() {
        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.defence-organisation-client-idpc-access-orgs")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(ORGANISATION_NAME, "Donald ltd")
                        .add(HEARING_DATE, "2022-10-11")
                        .build());
    }

    private JsonObject createHearingResponseWithOneCase() {
        return createObjectBuilder()
                .add("prosecutionCases", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("caseId", randomUUID().toString())
                                .add("urn", "TVL123X")
                                .build()
                        ).build())
                .build();

    }

    private JsonObject createHearingResponseWithMultipleCase() {
        return createObjectBuilder()
                .add("prosecutionCases", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("caseId", randomUUID().toString())
                                .add("urn", "TVL123X")
                                .build())
                        .add(createObjectBuilder()
                                .add("caseId", randomUUID().toString())
                                .add("urn", "TFL123Y")
                                .build()
                        ).build())
                .build();

    }


    private JsonEnvelope createRequestEnvelopeForDefenceClient(final UUID defendantId) {
        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.query.defence-client-defendantId")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add("defendantId", defendantId.toString())
                        .build());


    }

    private DefenceClient generateDefenceClient(final UUID defenceClientId) {
        return new DefenceClient(
                defenceClientId,
                "FIRSTNAME",
                "LASTNAME",
                randomUUID(),
                LocalDate.now(),
                randomUUID());
    }

    private JsonEnvelope createRequestEnvelopeForCaseByPersonDefendant() {
        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.query.get-case-by-person-defendant")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(FIRST_NAME, "Donald")
                        .add(LAST_NAME, "Knuth")
                        .add(DOB, "1983-04-20")
                        .build());
    }

    private JsonEnvelope createRequestEnvelopeForCaseByPersonDefendantForCivil() {
        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.query.get-case-by-person-defendant")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(FIRST_NAME, "Donald")
                        .add(LAST_NAME, "Knuth")
                        .add(DOB, "1983-04-20")
                        .add(IS_CIVIL, true)
                        .build());
    }

    private JsonEnvelope createRequestEnvelopeForCaseByPersonDefendantWithoutDobForCivil() {
        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.query.get-case-by-person-defendant")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(FIRST_NAME, "Donald")
                        .add(LAST_NAME, "Knuth")
                        .add(IS_CIVIL, true)
                        .build());
    }

    private JsonEnvelope createRequestEnvelopeForCaseByOrganisationDefendant() {
        return JsonEnvelope.envelopeFrom(JsonEnvelope.metadataBuilder()
                        .withName("defence.query.get-case-by-organisation-defendant")
                        .withId(randomUUID())
                        .build(),
                createObjectBuilder()
                        .add(ORGANISATION_NAME, "Donald ltd")
                        .build());
    }

}