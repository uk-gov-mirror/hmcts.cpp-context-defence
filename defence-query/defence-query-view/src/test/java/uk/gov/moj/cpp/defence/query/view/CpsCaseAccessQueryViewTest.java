package uk.gov.moj.cpp.defence.query.view;

import static com.google.common.collect.ImmutableList.of;
import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView.ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY;
import static uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView.ASSIGNEES;
import static uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView.CASE_ID;
import static uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView.IS_ADVOCATE_DEFENDING_OR_PROSECUTING;
import static uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView.ORGANISATION_ID;

import uk.gov.justice.core.courts.AssociatedPerson;
import uk.gov.justice.cps.defence.Prosecutioncase;
import uk.gov.justice.cps.defence.SearchCaseByUrn;
import uk.gov.justice.cps.defence.caag.ProsecutioncaseCaag;
import uk.gov.justice.json.schemas.hearing.HearingSummaries;
import uk.gov.justice.json.schemas.hearing.Timeline;
import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ListToJsonArrayConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.common.json.DefaultJsonParser;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.persistence.AdvocateAccessRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceGrantAccessRepository;
import uk.gov.moj.cpp.defence.persistence.OrganisationAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.AssignmentUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociationDefendant;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionAdvocateAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;
import uk.gov.moj.cpp.defence.persistence.entity.RepresentationType;
import uk.gov.moj.cpp.defence.query.hearing.api.HearingSummary;
import uk.gov.moj.cpp.defence.query.hearing.api.Hearings;
import uk.gov.moj.cpp.defence.service.HearingService;
import uk.gov.moj.cpp.defence.service.ListingService;
import uk.gov.moj.cpp.defence.service.ProgressionService;
import uk.gov.moj.cpp.defence.service.ReferenceDataService;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.defence.service.UsersGroupQueryService;
import uk.gov.moj.cpp.hearing.Application;
import uk.gov.moj.cpp.hearing.Defendant;
import uk.gov.moj.cpp.hearing.Person;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CpsCaseAccessQueryViewTest {

    private static final String ASSIGNEE_ORG_NAME = "ABC & Co.";
    private final ZonedDateTime assignedDate = ZonedDateTime.now();
    @Mock
    private DefenceAssociationRepository defenceAssociationRepository;
    @Mock
    private AdvocateAccessRepository advocateAssignmentRepository;
    @Mock
    private OrganisationAccessRepository organisationAccessRepository;
    @Spy
    private final ObjectMapper mapper = new ObjectMapperProducer().objectMapper();
    @Spy
    private final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(mapper);
    @Spy
    private final JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(mapper);
    @Mock
    private JsonEnvelope envelope;

    @Mock
    private Envelope<SearchCaseByUrn> searchCaseByUrnEnvelope;

    @InjectMocks
    private CpsCaseAccessQueryView advocateAccessQueryView;
    @Mock
    private JsonObject jsonPayload;
    @Mock
    private DefenceGrantAccessRepository defenceGrantAccessRepository;
    @Mock
    private DefenceQueryService defenceQueryService;
    @Mock
    private ProgressionService progressionService;
    @Mock
    private ReferenceDataService referenceDataService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private UsersGroupQueryService usersGroupQueryService;
    @Mock
    private Requester requester;
    @Spy
    private ListToJsonArrayConverter listToJsonArrayConverter;
    @Mock
    private HearingService hearingService;
    @Mock
    private ListingService listingService;

    @BeforeEach
    public void initMocks() {
        setField(this.listToJsonArrayConverter, "stringToJsonObjectConverter", new StringToJsonObjectConverter());
        setField(this.listToJsonArrayConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }


    @Test
    public void shouldFindAssociatedPersonsForExistingAssPer() {

        final UUID defendantId = randomUUID();
        final String role = "B";
        final List<uk.gov.justice.cps.defence.caag.Defendants> caagDefendants = asList(buildCaagDefendant(defendantId, role));

        final List<uk.gov.justice.core.courts.Defendant> caseDefendants = asList(buildCaseDefendant(randomUUID(), "A"),
                buildCaseDefendant(randomUUID(), "A"),
                buildCaseDefendant(defendantId, role));
        final List<uk.gov.justice.cps.defence.caag.Defendants> updatedDefendants = advocateAccessQueryView.getEnrichedDefendants(caagDefendants, caseDefendants);

        assertThat(updatedDefendants.size(), is(1));
        assertThat(updatedDefendants.get(0).getAssociatedPersons().size(), is(1));
        assertThat(updatedDefendants.get(0).getAssociatedPersons().get(0).getRole(), is(role));

    }

    @Test
    public void shouldFindAssociatedPersonsForNonExistingAssPer() {
        final UUID defendantId = randomUUID();
        final String role = "B";
        final List<uk.gov.justice.cps.defence.caag.Defendants> caagDefendants = asList(buildCaagDefendant(defendantId, role));

        final List<uk.gov.justice.core.courts.Defendant> caseDefendants = asList(buildCaseDefendant(randomUUID(), "A"),
                buildCaseDefendant(randomUUID(), "A"),
                buildCaseDefendant(defendantId, null));
        final List<uk.gov.justice.cps.defence.caag.Defendants> updatedDefendants = advocateAccessQueryView.getEnrichedDefendants(caagDefendants, caseDefendants);

        assertThat(updatedDefendants.size(), is(1));
        assertThat(updatedDefendants.get(0).getAssociatedPersons(), nullValue());
    }

    private uk.gov.justice.core.courts.Defendant buildCaseDefendant(final UUID defendantId, final String role) {
        uk.gov.justice.core.courts.Defendant.Builder defenceBuilder = uk.gov.justice.core.courts.Defendant.defendant();
        defenceBuilder.withId(defendantId);
        if (nonNull(role)) {
            defenceBuilder.withAssociatedPersons(asList(AssociatedPerson.associatedPerson().withRole(role).build()));
        }
        return defenceBuilder.build();
    }

    private uk.gov.justice.cps.defence.caag.Defendants buildCaagDefendant(final UUID defendantId, final String role) {
        return uk.gov.justice.cps.defence.caag.Defendants.defendants()
                .withId(defendantId)
                .build();
    }

    @Test
    public void shouldGetAllOrganisationsAssignedToTheCase() {

        final UUID caseId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        when(jsonPayload.getString(CASE_ID)).thenReturn(caseId.toString());

        final ProsecutionOrganisationAccess prosecutionOrganisationOneAccess = getProsecutionOrganisationAccessEntity(caseId);
        final ProsecutionOrganisationAccess prosecutionOrganisationTwoAccess = getProsecutionOrganisationAccessEntity(caseId);
        when(organisationAccessRepository.findByCaseId(caseId)).thenReturn(asList(prosecutionOrganisationOneAccess, prosecutionOrganisationTwoAccess));

        final JsonEnvelope assignedUsersToTheCase = advocateAccessQueryView.getAssignedUsersToTheCase(envelope);

        assertThat(assignedUsersToTheCase, is(notNullValue()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).size(), is(2));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).getJsonObject(0).getString("assigneeOrganisationId"), is(prosecutionOrganisationOneAccess.getId().getAssigneeOrganisationId().toString()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).getJsonObject(1).getString("assigneeOrganisationId"), is(prosecutionOrganisationTwoAccess.getId().getAssigneeOrganisationId().toString()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).getJsonObject(0).getString("status"), is("Access granted by John Smith"));

    }

    @Test
    public void shouldGetProsecutionAdvocateAssignedToTheCase() {
        final UUID caseId = randomUUID();
        final UUID advocateUserId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        when(jsonPayload.getString(CASE_ID)).thenReturn(caseId.toString());

        final ProsecutionOrganisationAccess prosecutionOrganisationAccess = getProsecutionOrganisationAccessEntity(caseId, getProsecutionAdvocateAccess(advocateUserId));
        when(organisationAccessRepository.findByCaseId(caseId)).thenReturn(singletonList(prosecutionOrganisationAccess));

        final JsonEnvelope assignedUsersToTheCase = advocateAccessQueryView.getAssignedUsersToTheCase(envelope);

        assertThat(assignedUsersToTheCase, is(notNullValue()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).size(), is(1));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).getJsonObject(0).getString("assigneeOrganisationId"), is(prosecutionOrganisationAccess.getId().getAssigneeOrganisationId().toString()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).getJsonObject(0).getString("assigneeOrganisationName"), is(prosecutionOrganisationAccess.getAssigneeOrganisationName()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).getJsonObject(0).getString("assigneeName"), is("Patesh Singh"));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).getJsonObject(0).getString("status"), is("Access granted by John Smith"));
    }

    @Test
    public void shouldGetEmptyListOfAssigneesWhenCaseIdNotMatch() {

        final UUID caseId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        when(jsonPayload.getString(CASE_ID)).thenReturn(caseId.toString());

        when(organisationAccessRepository.findByCaseId(caseId)).thenReturn(emptyList());

        final JsonEnvelope assignedUsersToTheCase = advocateAccessQueryView.getAssignedUsersToTheCase(envelope);

        assertThat(assignedUsersToTheCase, is(notNullValue()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray(ASSIGNEES).isEmpty(), is(true));
    }

    @Test
    public void shouldGetAdvocatesAssignedToCaseAndOrganisation() {
        final UUID caseId = randomUUID();
        final UUID organisationId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        when(jsonPayload.getString(CASE_ID)).thenReturn(caseId.toString());
        when(jsonPayload.getString(ORGANISATION_ID)).thenReturn(organisationId.toString());

        final ProsecutionOrganisationAccess prosecutionOrganisationAccess = getProsecutionOrganisationAccessEntity(caseId, organisationId);
        prosecutionOrganisationAccess.getProsecutionAdvocatesWithAccess().add(getProsecutionAdvocateAccess(randomUUID()));

        when(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseId, organisationId)).thenReturn(singletonList(prosecutionOrganisationAccess));

        final JsonEnvelope assignedAdvocatesToTheCaseAndOrganisation = advocateAccessQueryView.getAssignedAdvocatesToTheCaseAndOrganisation(envelope);

        assertThat(assignedAdvocatesToTheCaseAndOrganisation, is(notNullValue()));
        assertThat(assignedAdvocatesToTheCaseAndOrganisation.payloadAsJsonObject().getJsonArray(ASSIGNEES).size(), is(1));
        assertThat(assignedAdvocatesToTheCaseAndOrganisation.payloadAsJsonObject().getJsonArray(ASSIGNEES).getJsonObject(0).getString("assigneeOrganisationId"), is(prosecutionOrganisationAccess.getId().getAssigneeOrganisationId().toString()));
    }

    @Test
    public void shouldGetRoleOfAdvocateInCaseAsBothDefendingAndProsecuting() {

        final String urn = "55DP0028116";
        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        final UUID defendantId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.role-in-case")
                .createdAt(now()).build();
        when(envelope.metadata()).thenReturn(metadata);
        when(jsonPayload.getString("caseUrn")).thenReturn(urn);
        when(jsonPayload.getBoolean(ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY, false)).thenReturn(true);
        when(defenceQueryService.getCaseId(urn)).thenReturn(caseId);

        DefenceAssociation da = new DefenceAssociation();
        DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(defendantId);
        da.setDefenceAssociationDefendant(defenceAssociationDefendant);
        when(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(of(da));
        when(userGroupService.getGroupNamesForUser(userId, metadata, requester)).thenReturn(of("Defence Lawyers"));
        when(organisationAccessRepository.findActiveByCaseIdAndAssigneeOrganisationId(caseId, orgId)).thenReturn(of(new ProsecutionOrganisationAccess()));
        when(advocateAssignmentRepository.findActiveByCaseIdAndAssigneeId(caseId, userId)).thenReturn(emptyList());
        final JsonEnvelope assignedUsersToTheCase = advocateAccessQueryView.findAdvocatesRoleInCase(envelope);

        assertThat(assignedUsersToTheCase, is(notNullValue()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getString(IS_ADVOCATE_DEFENDING_OR_PROSECUTING), is("both"));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray("authorizedDefendantIds").getString(0), is(defendantId.toString()));
    }

    @Test
    public void shouldQueryProsecutioncaseDefenceCaagAndVerifyDefendantIsFiltered() throws IOException {
        final String urn = "55DP0028116";
        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        final UUID authorisedDefendantId = randomUUID();
        final UUID masterDefendantId = randomUUID();
        final SearchCaseByUrn searchCaseByUrn = SearchCaseByUrn.searchCaseByUrn()
                .withCaseId(caseId)
                .withCaseUrn(urn)
                .build();
        when(searchCaseByUrnEnvelope.payload()).thenReturn(searchCaseByUrn);

        final Metadata roleInCaseMetadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.role-in-case")
                .createdAt(now()).build();

        when(userGroupService.getOrganisationDetailsForUser(userId, roleInCaseMetadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(roleInCaseMetadata);
        when(defenceQueryService.getCaseId(urn)).thenReturn(caseId);
        when(progressionService.getProsecutionCaseDetails(any(), any())).thenReturn(getProsecutionCaseQueryResponsePayload(authorisedDefendantId));
        when(progressionService.getProsecutionCaseDetailsForCaag(any(), any())).thenReturn(getProsecutionCaseCaagQueryResponsePayload(authorisedDefendantId, masterDefendantId));

        DefenceAssociation da = new DefenceAssociation();
        DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(authorisedDefendantId);
        da.setDefenceAssociationDefendant(defenceAssociationDefendant);
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(of(da));

        final Envelope<ProsecutioncaseCaag> prosecutioncaseCaagEnvelope = advocateAccessQueryView.queryProsecutioncaseDefenceCaag(searchCaseByUrnEnvelope);
        final ProsecutioncaseCaag prosecutioncaseCaag = prosecutioncaseCaagEnvelope.payload();
        assertThat(prosecutioncaseCaagEnvelope, is(notNullValue()));
        //verify defendant filtering
        assertThat(prosecutioncaseCaag.getDefendants().size(), is(1));
        assertThat(prosecutioncaseCaag.getDefendants().get(0).getId(), is(authorisedDefendantId));
        assertThat(prosecutioncaseCaag.getDefendants().get(0).getAssociatedPersons(), notNullValue());
        //verify application filtering
        assertThat(prosecutioncaseCaag.getLinkedApplications().size(), is(2));
        assertThat(prosecutioncaseCaag.getLinkedApplications().get(0).getApplicantId(), is(authorisedDefendantId));
        assertThat(prosecutioncaseCaag.getLinkedApplications().get(1).getApplicantId(), is(masterDefendantId));

        assertThat(prosecutioncaseCaag.getDefendants().get(0).getDefendantCaseJudicialResults(), is(emptyList()));
        assertThat(prosecutioncaseCaag.getDefendants().get(0).getDefendantJudicialResults(), hasSize(1));
        assertThat(prosecutioncaseCaag.getDefendants().get(0).getDefendantJudicialResults().get(0).getJudicialResultPrompts(), hasSize(2));
        assertThat(prosecutioncaseCaag.getDefendants().get(0).getCaagDefendantOffences().get(0).getCaagResults(), hasSize(1));
        assertThat(prosecutioncaseCaag.getDefendants().get(0).getCaagDefendantOffences().get(0).getCaagResults().get(0).getCaagResultPrompts(), hasSize(1));
        assertThat(prosecutioncaseCaag.getDefendants().get(0).getCaagDefendantOffences().get(1).getCaagResults(), hasSize(0));
    }

    @Test
    public void shouldQueryProsecutioncaseForDefenceAndVerifyDefendantIsFiltered() throws IOException {
        final String urn = "55DP0028116";
        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        final UUID authorisedDefendantId = randomUUID();
        final SearchCaseByUrn searchCaseByUrn = SearchCaseByUrn.searchCaseByUrn()
                .withCaseId(caseId)
                .withCaseUrn(urn)
                .build();
        when(searchCaseByUrnEnvelope.payload()).thenReturn(searchCaseByUrn);

        final Metadata roleInCaseMetadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.role-in-case")
                .createdAt(now()).build();

        when(userGroupService.getOrganisationDetailsForUser(userId, roleInCaseMetadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(roleInCaseMetadata);
        when(progressionService.getProsecutionCaseDetailsAsJson(any(), any())).thenReturn(getProsecutionCaseQueryResponsePayload(authorisedDefendantId));

        DefenceAssociation da = new DefenceAssociation();
        DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(authorisedDefendantId);
        da.setDefenceAssociationDefendant(defenceAssociationDefendant);
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(of(da));

        final Envelope<Prosecutioncase> prosecutioncaseEnvelope = advocateAccessQueryView.queryProsecutioncaseDefence(searchCaseByUrnEnvelope);
        final Prosecutioncase prosecutioncase = prosecutioncaseEnvelope.payload();
        assertThat(prosecutioncaseEnvelope, is(notNullValue()));
        //verify defendant filtering
        assertThat(prosecutioncase.getProsecutionCase().getDefendants().size(), is(1));
        assertThat(prosecutioncase.getProsecutionCase().getDefendants().get(0).getId(), is(authorisedDefendantId));
        assertThat(prosecutioncase.getProsecutionCase().getDefendants().get(0).getAssociatedPersons(), notNullValue());
    }

    @Test
    public void shouldQueryProsecutioncaseProsecutorCaagAndVerifyDefendantIsNotFiltered() throws IOException {

        final UUID caseId = randomUUID();
        final UUID authorisedDefendantId = randomUUID();
        final UUID masterDefendantId = randomUUID();
        final SearchCaseByUrn searchCaseByUrn = SearchCaseByUrn.searchCaseByUrn()
                .withCaseId(caseId)
                .withAuthorisedDefendantIds(singletonList(authorisedDefendantId))
                .build();
        when(searchCaseByUrnEnvelope.payload()).thenReturn(searchCaseByUrn);
        final Metadata metadata = metadataBuilder()
                .withId(randomUUID())
                .withUserId(randomUUID().toString())
                .withName("defence.query.prosecutioncase-defence-caag")
                .createdAt(now()).build();
        when(searchCaseByUrnEnvelope.metadata()).thenReturn(metadata);

        when(progressionService.getProsecutionCaseDetails(any(), any())).thenReturn(getProsecutionCaseQueryResponsePayload(authorisedDefendantId));
        when(progressionService.getProsecutionCaseDetailsForCaag(any(), any())).thenReturn(getProsecutionCaseCaagQueryResponsePayload(authorisedDefendantId, masterDefendantId));


        final Envelope<ProsecutioncaseCaag> prosecutioncaseProsecutorCaagEnvelope = advocateAccessQueryView.queryProsecutioncaseProsecutorCaag(searchCaseByUrnEnvelope);
        final ProsecutioncaseCaag prosecutioncaseCaag = prosecutioncaseProsecutorCaagEnvelope.payload();
        assertThat(prosecutioncaseProsecutorCaagEnvelope, is(notNullValue()));
        //verify defendants are not filtered
        assertThat(prosecutioncaseCaag.getDefendants().size(), is(3));
        //verify defendants are not filtered
        assertThat(prosecutioncaseCaag.getLinkedApplications().size(), is(2));
    }

    private JsonObject getProsecutionCaseCaagQueryResponsePayload(final UUID authorisedDefendantId, final UUID masterDefendantId) throws IOException {
        final String payload = readFileToString(new File(this.getClass().getClassLoader().getResource("defence.query.prosecutioncase-caag.json").getFile()))
                .replace("DEFENDANT_ID", authorisedDefendantId.toString())
                .replace("MASTER_DEF_ID", masterDefendantId.toString());
        return new DefaultJsonParser().toObject(payload, JsonObject.class);
    }

    private JsonObject getProsecutionCaseQueryResponsePayload(final UUID authorisedDefendantId) throws IOException {
        final String payload = readFileToString(new File(this.getClass().getClassLoader().getResource("defence.query.prosecutioncase.json").getFile())).replace("DEFENDANT_ID", authorisedDefendantId.toString());
        return new DefaultJsonParser().toObject(payload, JsonObject.class);
    }

    @Test
    public void shouldGetRoleOfAdvocateAsProsecuting() {

        final String urn = "55DP0028116";
        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.role-in-case")
                .createdAt(now()).build();
        when(envelope.metadata()).thenReturn(metadata);
        when(jsonPayload.getString("caseUrn")).thenReturn(urn);
        when(jsonPayload.getBoolean(ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY, false)).thenReturn(true);
        when(defenceQueryService.getCaseId(urn)).thenReturn(caseId);

        when(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(emptyList());
        when(userGroupService.getGroupNamesForUser(userId, metadata, requester)).thenReturn(of("Defence Lawyers"));
        when(organisationAccessRepository.findActiveByCaseIdAndAssigneeOrganisationId(caseId, orgId)).thenReturn(of(new ProsecutionOrganisationAccess()));
        when(advocateAssignmentRepository.findActiveByCaseIdAndAssigneeId(caseId, userId)).thenReturn(emptyList());
        final JsonEnvelope assignedUsersToTheCase = advocateAccessQueryView.findAdvocatesRoleInCase(envelope);

        assertThat(assignedUsersToTheCase, is(notNullValue()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getString(IS_ADVOCATE_DEFENDING_OR_PROSECUTING), is("prosecuting"));
    }

    @Test
    public void shouldGetRoleOfAdvocateAsDefending() {

        final String urn = "55DP0028116";
        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        final UUID defendantId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.role-in-case")
                .createdAt(now()).build();
        when(envelope.metadata()).thenReturn(metadata);
        when(jsonPayload.getString("caseUrn")).thenReturn(urn);
        when(jsonPayload.getBoolean(ACTIVE_PROSECUTING_ASSIGNMENTS_ONLY, false)).thenReturn(false);
        when(defenceQueryService.getCaseId(urn)).thenReturn(caseId);

        when(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        DefenceAssociation da = new DefenceAssociation();
        DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(defendantId);
        da.setDefenceAssociationDefendant(defenceAssociationDefendant);
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(of(da));
        when(userGroupService.getGroupNamesForUser(userId, metadata, requester)).thenReturn(of("Defence Lawyers"));
        when(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseId, orgId)).thenReturn(emptyList());
        when(advocateAssignmentRepository.findByCaseIdAndAssigneeId(caseId, userId)).thenReturn(emptyList());
        final JsonEnvelope assignedUsersToTheCase = advocateAccessQueryView.findAdvocatesRoleInCase(envelope);

        assertThat(assignedUsersToTheCase, is(notNullValue()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getString(IS_ADVOCATE_DEFENDING_OR_PROSECUTING), is("defending"));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getJsonArray("authorizedDefendantIds").getString(0), is(defendantId.toString()));
    }

    @Test
    public void shouldGetRoleInCaseByIdOfAdvocateAsProsecuting() {

        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.role-in-case-by-caseid")
                .createdAt(now()).build();
        when(envelope.metadata()).thenReturn(metadata);
        when(jsonPayload.getString("caseId")).thenReturn(caseId.toString());

        when(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(emptyList());
        when(userGroupService.getGroupNamesForUser(userId, metadata, requester)).thenReturn(of("Defence Lawyers"));
        when(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseId, orgId)).thenReturn(of(new ProsecutionOrganisationAccess()));
        when(advocateAssignmentRepository.findByCaseIdAndAssigneeId(caseId, userId)).thenReturn(emptyList());
        final JsonEnvelope assignedUsersToTheCase = advocateAccessQueryView.findAdvocatesRoleInCaseByCaseId(envelope);

        assertThat(assignedUsersToTheCase, is(notNullValue()));
        assertThat(assignedUsersToTheCase.payloadAsJsonObject().getString(IS_ADVOCATE_DEFENDING_OR_PROSECUTING), is("prosecuting"));
    }

    @Test
    public void shouldGetCaseAndHearingTimelineWhenAdvocateIsProsecuting() throws IOException, JSONException {

        final UUID orgId = randomUUID();
        final UUID caseId = fromString("9310e4c1-2ed4-46ed-b796-936938526877");
        final String applicationId = "9510e4c1-2ed4-46ed-b796-936938526877";
        final UUID userId = fromString("7f8c79e7-fb85-464d-8fd1-3d58c9622197");
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();
        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(false, true);


        JsonObject jsonPayload = createObjectBuilder().add("caseId", caseId.toString()).add("applicationId", applicationId).add("advocateRole", "prosecuting").build();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.hearing.timeline")
                .createdAt(now()).build();
        when(envelope.metadata()).thenReturn(metadata);
        when(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(emptyList());
        when(userGroupService.getGroupNamesForUser(userId, metadata, requester)).thenReturn(of("Defence Lawyers"));
        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.empty());
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));
        when(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseId, orgId)).thenReturn(of(new ProsecutionOrganisationAccess()));

        when(hearingService.getHearingTimelineByCaseId(metadata, caseId)).thenReturn(
                Timeline.timeline()
                        .withHearingSummaries(of(HearingSummaries.hearingSummaries()
                                .withHearingId(fromString("9810e4c1-2ed4-46ed-b796-936938526877"))
                                .withDefendants(of(Defendant.defendant().withId(fromString("2510e4c1-2ed4-46ed-b796-936938526877")).build()))
                                .withApplications(of(Application.application().withApplicants(of(Person.person().withId(fromString("9510e4c1-2ed4-46ed-b796-936938526877")).build())).build()))
                                .build()))
                        .build());
        when(listingService.getHearings(metadata, caseId.toString())).thenReturn(of(Hearing.hearing().withId(fromString("1210e4c1-2ed4-46ed-b796-936938526877")).build()));
        final JsonEnvelope caseAndApplicationTimelines = advocateAccessQueryView.getCaseAndApplicationTimelines(envelope);
        assertThat(caseAndApplicationTimelines, is(notNullValue()));
        final String expectedPayload = readFileToString(new File(this.getClass().getClassLoader().getResource("hearing-timeline.json").getFile()));
        assertEquals(expectedPayload, caseAndApplicationTimelines.payloadAsJsonObject().toString(), true);
    }

    private JsonObject getProsecutorQueryResponse(final boolean cpsFlag, final boolean policeFlag) {
        return createObjectBuilder()
                .add("cpsFlag", cpsFlag)
                .add("policeFlag", policeFlag)
                .add("shortName", "DVLA")
                .build();
    }
    @Test
    public void shouldGetHearings() {

        final String date = "02/02/2022";
        final String courtCentreId = "5";
        JsonObject jsonPayload = createObjectBuilder().add("date", date).add("courtCentreId", courtCentreId).build();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(randomUUID().toString())
                .withName("defence.query.hearings")
                .createdAt(now()).build();
        when(envelope.metadata()).thenReturn(metadata);

        when(hearingService.getHearings(metadata, date, courtCentreId)).thenReturn(
                Hearings.hearings()
                        .withHearingSummaries(singletonList(HearingSummary.hearingSummary()
                                .withId(randomUUID())
                                .build()))
                        .build());
        final Envelope<Hearings> hearings = advocateAccessQueryView.getHearings(envelope);
        assertThat(hearings.payload(), is(notNullValue()));
        assertThat(hearings.payload().getHearingSummaries().size(), is(1));
    }

    @Test
    public void shouldGetCaseAndHearingTimelineWhenAdvocateIsDefending() throws IOException, JSONException {

        final UUID orgId = randomUUID();
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();
        final UUID caseId = fromString("9310e4c1-2ed4-46ed-b796-936938526877");
        final String applicationId = "9510e4c1-2ed4-46ed-b796-936938526877";
        final UUID userId = fromString("7f8c79e7-fb85-464d-8fd1-3d58c9622197");
        final UUID defendantId = fromString("cef1e81b-aaec-4ce4-b61a-b2397b1a048f");
        JsonObject jsonPayload = createObjectBuilder().add("caseId", caseId.toString()).add("applicationId", applicationId).add("advocateRole", "defending").build();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.hearing.timeline")
                .createdAt(now()).build();
        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(false, true);
        when(envelope.metadata()).thenReturn(metadata);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));
        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.empty());
        when(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        DefenceAssociation da = new DefenceAssociation();
        DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(defendantId);
        da.setDefenceAssociationDefendant(defenceAssociationDefendant);
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(of(da));
        when(userGroupService.getGroupNamesForUser(userId, metadata, requester)).thenReturn(of("Defence Lawyers"));
        when(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseId, orgId)).thenReturn(of(new ProsecutionOrganisationAccess()));

        when(hearingService.getHearingTimelineByCaseId(metadata, caseId)).thenReturn(
                Timeline.timeline()
                        .withHearingSummaries(of(HearingSummaries.hearingSummaries()
                                .withHearingId(fromString("9810e4c1-2ed4-46ed-b796-936938526877"))
                                .withDefendants(of(Defendant.defendant().withId(defendantId).build()))
                                .withApplications(of(Application.application().withApplicants(of(Person.person().withId(fromString("9510e4c1-2ed4-46ed-b796-936938526877")).build())).build()))
                                .build()))
                        .build());
        when(listingService.getHearings(metadata, caseId.toString())).thenReturn(of(Hearing.hearing().withId(fromString("1210e4c1-2ed4-46ed-b796-936938526877")).build()));
        final JsonEnvelope caseAndApplicationTimelines = advocateAccessQueryView.getCaseAndApplicationTimelines(envelope);
        assertThat(caseAndApplicationTimelines, is(notNullValue()));
        final String expectedPayload = readFileToString(new File(this.getClass().getClassLoader().getResource("hearing-timeline-defence.json").getFile()));
        assertEquals(expectedPayload, caseAndApplicationTimelines.payloadAsJsonObject().toString(), true);
    }

    @Test
    public void shouldGetCaseAndHearingTimelineWhenAdvocateIsProsecutingForNonCPSProsecutors() throws IOException, JSONException {

        final UUID orgId = randomUUID();
        final UUID caseId = fromString("9310e4c1-2ed4-46ed-b796-936938526877");
        final String applicationId = "9510e4c1-2ed4-46ed-b796-936938526877";
        final UUID userId = fromString("7f8c79e7-fb85-464d-8fd1-3d58c9622197");
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();
        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(false, true);


        JsonObject jsonPayload = createObjectBuilder().add("caseId", caseId.toString()).add("applicationId", applicationId).add("advocateRole", "prosecuting").build();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.hearing.timeline")
                .createdAt(now()).build();
        when(envelope.metadata()).thenReturn(metadata);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));
        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.of("OrganisationMatch"));
        when(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(emptyList());
        when(userGroupService.getGroupNamesForUser(userId, metadata, requester)).thenReturn(of("Defence Lawyers"));
        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.empty());
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));
        when(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseId, orgId)).thenReturn(of(new ProsecutionOrganisationAccess()));

        when(hearingService.getHearingTimelineByCaseId(metadata, caseId)).thenReturn(
                Timeline.timeline()
                        .withHearingSummaries(of(HearingSummaries.hearingSummaries()
                                .withHearingId(fromString("9810e4c1-2ed4-46ed-b796-936938526877"))
                                .withDefendants(of(Defendant.defendant().withId(fromString("2510e4c1-2ed4-46ed-b796-936938526877")).build()))
                                .withApplications(of(Application.application().withApplicants(of(Person.person().withId(fromString("9510e4c1-2ed4-46ed-b796-936938526877")).build())).build()))
                                .build()))
                        .build());
        when(listingService.getHearings(metadata, caseId.toString())).thenReturn(of(Hearing.hearing().withId(fromString("1210e4c1-2ed4-46ed-b796-936938526877")).build()));
        final JsonEnvelope caseAndApplicationTimelines = advocateAccessQueryView.getCaseAndApplicationTimelines(envelope);
        assertThat(caseAndApplicationTimelines, is(notNullValue()));
        final String expectedPayload = readFileToString(new File(this.getClass().getClassLoader().getResource("hearing-timeline.json").getFile()));
        assertEquals(expectedPayload, caseAndApplicationTimelines.payloadAsJsonObject().toString(), true);
    }

    @Test
    public void shouldGetCaseAndHearingTimelineWhenAdvocateIsNonCpsProsecutorWithOrgMismatch() {

        final UUID orgId = randomUUID();
        final UUID prosecutorOrProsecutionCaseAuthorityID = randomUUID();
        final UUID caseId = fromString("9310e4c1-2ed4-46ed-b796-936938526877");
        final String applicationId = "9510e4c1-2ed4-46ed-b796-936938526877";
        final UUID userId = fromString("7f8c79e7-fb85-464d-8fd1-3d58c9622197");
        final UUID defendantId = fromString("cef1e81b-aaec-4ce4-b61a-b2397b1a048f");
        JsonObject jsonPayload = createObjectBuilder().add("caseId", caseId.toString()).add("applicationId", applicationId).add("advocateRole", "defending").build();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);
        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withUserId(userId.toString())
                .withName("advocate.query.hearing.timeline")
                .createdAt(now()).build();
        final JsonObject prosecutorJsonObject = getProsecutorQueryResponse(false, true);
        when(envelope.metadata()).thenReturn(metadata);
        when(progressionService.getProsecutorOrProsecutionCaseAuthorityID(metadata, caseId)).thenReturn(prosecutorOrProsecutionCaseAuthorityID);
        when(referenceDataService.getProsecutor(metadata, prosecutorOrProsecutionCaseAuthorityID)).thenReturn(Optional.of(prosecutorJsonObject));
        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.of("OrganisationMisMatch"));
        when(userGroupService.getOrganisationDetailsForUser(userId, metadata, requester)).thenReturn(Organisation.organisation().withOrgId(orgId).build());
        DefenceAssociation da = new DefenceAssociation();
        DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
        defenceAssociationDefendant.setDefendantId(defendantId);
        da.setDefenceAssociationDefendant(defenceAssociationDefendant);
        when(defenceAssociationRepository.findByOrganisationIdAndCaseId(orgId, caseId)).thenReturn(of(da));
        when(userGroupService.getGroupNamesForUser(userId, metadata, requester)).thenReturn(of("Defence Lawyers"));
        when(organisationAccessRepository.findByCaseIdAndAssigneeOrganisationId(caseId, orgId)).thenReturn(of(new ProsecutionOrganisationAccess()));

        assertThrows(ForbiddenRequestException.class, () -> advocateAccessQueryView.getCaseAndApplicationTimelines(envelope));
     }


    private ProsecutionOrganisationAccess getProsecutionOrganisationAccessEntity(UUID caseId, ProsecutionAdvocateAccess prosecutionAdvocateAccess) {
        ProsecutionOrganisationAccess prosecutionOrganisationAccessEntity = getProsecutionOrganisationAccessEntity(caseId);
        prosecutionOrganisationAccessEntity.getProsecutionAdvocatesWithAccess().add(prosecutionAdvocateAccess);
        return prosecutionOrganisationAccessEntity;
    }

    private ProsecutionAdvocateAccess getProsecutionAdvocateAccess(UUID advocateUserId) {
        ProsecutionAdvocateAccess prosecutionAdvocateAccess = new ProsecutionAdvocateAccess();
        prosecutionAdvocateAccess.setAssigneeDetails(new AssignmentUserDetails(randomUUID(), advocateUserId, "Patesh", "Singh"));
        prosecutionAdvocateAccess.setAssignorDetails(new AssignmentUserDetails(randomUUID(), randomUUID(), "John", "Smith"));
        return prosecutionAdvocateAccess;
    }

    private ProsecutionOrganisationAccess getProsecutionOrganisationAccessEntity(UUID caseId) {

        ProsecutionOrganisationAccess prosecutionOrganisationAccess = new ProsecutionOrganisationAccess();
        prosecutionOrganisationAccess.setId(new ProsecutionOrganisationCaseKey(caseId, UUID.randomUUID()));
        prosecutionOrganisationAccess.setAssigneeOrganisationName(ASSIGNEE_ORG_NAME);
        prosecutionOrganisationAccess.setAssigneeDetails(new AssignmentUserDetails(randomUUID(), randomUUID(), "Jhon", "Rambo"));
        prosecutionOrganisationAccess.setRepresentationType(RepresentationType.PROSECUTION);
        prosecutionOrganisationAccess.setRepresenting("CPS");
        prosecutionOrganisationAccess.setAssignorDetails(new AssignmentUserDetails(randomUUID(), randomUUID(), "John", "Smith"));

        prosecutionOrganisationAccess.setAssignedDate(assignedDate);
        return prosecutionOrganisationAccess;
    }

    private ProsecutionOrganisationAccess getProsecutionOrganisationAccessEntity(UUID caseId, UUID organisationId) {

        ProsecutionOrganisationAccess prosecutionOrganisationAccess = new ProsecutionOrganisationAccess();
        prosecutionOrganisationAccess.setId(new ProsecutionOrganisationCaseKey(caseId, organisationId));
        prosecutionOrganisationAccess.setAssigneeOrganisationName(ASSIGNEE_ORG_NAME);
        prosecutionOrganisationAccess.setRepresentationType(RepresentationType.PROSECUTION);
        prosecutionOrganisationAccess.setAssignedDate(assignedDate);
        return prosecutionOrganisationAccess;
    }

}