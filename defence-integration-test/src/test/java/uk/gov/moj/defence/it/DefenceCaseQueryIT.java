package uk.gov.moj.defence.it;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.isJson;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.String.format;
import static java.time.LocalDate.parse;
import static java.time.Period.ofYears;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static java.util.regex.Pattern.compile;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.justice.services.test.utils.core.random.DateGenerator.Direction.FUTURE;
import static uk.gov.moj.defence.domain.common.UrnRegex.URN_PATTERN;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.associateOrganisation;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.verifyDefenceOrganisationAssociatedDataPersisted;
import static uk.gov.moj.defence.stub.ListingServiceStub.stubListingServiceForCasesByOrganisationDefendantAndHearingDate;
import static uk.gov.moj.defence.stub.ListingServiceStub.stubListingServiceForCasesByPersonDefendantAndHearingDate;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControl;
import static uk.gov.moj.defence.util.MaterialQueryStub.stubForMaterialQueryPerson;
import static uk.gov.moj.defence.util.ProsecutionCaseQueryStub.stubForProsecutionCaseQuery;
import static uk.gov.moj.defence.util.ReferenceDataOffencesQueryStub.stubForReferenceDataQueryOffence;
import static uk.gov.moj.defence.util.RestQueryUtil.pollCaseByOrganisationDefendant;
import static uk.gov.moj.defence.util.RestQueryUtil.pollCaseByOrganisationDefendantForCivil;
import static uk.gov.moj.defence.util.RestQueryUtil.pollCaseByPersonDefendant;
import static uk.gov.moj.defence.util.RestQueryUtil.pollCaseByPersonDefendantForCivil;
import static uk.gov.moj.defence.util.RestQueryUtil.pollCaseByPersonDefendantForCivilWithOutDob;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClient;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientForCivil;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientForDefenceClientCountByPersonNoDob;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientForOrganisationWithoutUrnFor404;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientForOrganisationWithoutUrnForCivil;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientWithoutUrn;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientForDefenceClientCountByOrganisation;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientForDefenceClientCountByPerson;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientForOrganisationWithoutUrn;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientWithMatcher;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientWithoutUrnForCivil;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceOrganisationWithMatcher;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientWithoutUrnWithOutDobForCivil;
import static uk.gov.moj.defence.util.RestQueryUtil.queryDefenceClient;
import static uk.gov.moj.defence.util.RestQueryUtil.queryIDPCMetadataForDefendant;
import static uk.gov.moj.defence.util.UrnGeneratorUtil.generateUrn;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetails;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetailsForUser;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationNamesForGivenId;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationNamesForIds;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetUsersAndGroupsQueryForDefenceUsers;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUserPermissions;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsPermission;
import static uk.gov.moj.defence.util.WiremockHelper.resetWiremock;

import org.junit.jupiter.api.Assertions;
import uk.gov.justice.json.generator.value.string.RegexGenerator;
import uk.gov.justice.json.generator.value.string.SimpleStringGenerator;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher;
import uk.gov.justice.services.test.utils.core.random.LocalDateGenerator;
import uk.gov.moj.defence.helper.CreateProsecutionCaseHelper;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefenceCaseQueryIT {

    private static final UUID userId = randomUUID();
    private static final UUID userId_org2 = randomUUID();
    private static final String organisationName = "Smith Associates Ltd.";
    private static final RegexGenerator regexGenerator = new RegexGenerator(compile(URN_PATTERN));
    private static UUID organisationId = randomUUID();
    private static UUID organisationId_2 = randomUUID();
    private final SimpleStringGenerator simpleStringGenerator = new SimpleStringGenerator(5, 15);
    private final LocalDateGenerator localDateGenerator = new LocalDateGenerator(ofYears(10), parse("1983-04-20"), FUTURE);
    private static final String resource1 = "stub-data/listing-service/hearing-cases-by-defendant-with-one-case.json";
    private static final String resource2 = "stub-data/listing-service/hearing-cases-by-defendant-with-multiple-cases.json";
    private static final String resource3 = "stub-data/listing-service/hearing-cases-by-defendant-with-no-cases.json";

    private CreateProsecutionCaseHelper createProsecutionCaseHelper;

    private UUID caseId;
    private String urn;
    private String firstName;
    private String lastName;
    private String defendantId;
    private String dateOfBirth;
    private UUID caseId1;
    private String urn1;
    private String defendantId1;

    @BeforeAll
    public static void setupClass() {
        resetWiremock();
        stubForReferenceDataQueryOffence();
        stubForMaterialQueryPerson();
        stubAccessControl(true, userId, "Defence Lawyers");
        stubGetOrganisationDetailsForUser(userId, organisationId);
        stubGetOrganisationNamesForIds(userId, Arrays.asList(organisationId));
        stubUserPermissions();
        stubUsersGroupsPermission();
        stubGetUsersAndGroupsQueryForDefenceUsers(userId.toString());
        stubGetOrganisationDetails(organisationId.toString(), organisationName);
        stubForProsecutionCaseQuery();
    }

    @BeforeEach
    public void setupTests() {
        createProsecutionCaseHelper = new CreateProsecutionCaseHelper();
        caseId = randomUUID();
        urn = regexGenerator.next();
        firstName = simpleStringGenerator.next();
        lastName = simpleStringGenerator.next();
        dateOfBirth = localDateGenerator.next().toString();
        defendantId = randomUUID().toString();
        defendantId1 = randomUUID().toString();
        caseId1 = randomUUID();
        urn1 = regexGenerator.next();
    }

    @Test
    public void shouldReturnDefenceClientId() {
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, userId);
        final ResponseData response = pollDefenceClient(urn, firstName, lastName, dateOfBirth, userId);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, containsString(defendantId));
        assertThat(responseEntity, containsString(caseId.toString()));
        assertThat(responseEntity, containsString("prosecutionAuthorityCode"));

        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.prosecutor", notNullValue()),
                withJsonPath("$.prosecutor.prosecutorCode", is("CPS")),
                withJsonPath("$.prosecutor.prosecutorId", notNullValue()),
                withJsonPath("$.isCivil", is(false)),
                withJsonPath("$.caseUrn", is(urn))

        )));

    }

    @Test
    public void shouldReturnDefenceClientIdForCivil() {
        createProsecutionCaseHelper.createAndVerifyCivilProsecutionCase(caseId, urn, defendantId, firstName, lastName, dateOfBirth, userId);
        final ResponseData response = pollDefenceClientForCivil(urn, firstName, lastName, dateOfBirth, userId, true);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, containsString(defendantId));
        assertThat(responseEntity, containsString(caseId.toString()));
        assertThat(responseEntity, containsString("prosecutionAuthorityCode"));

        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.prosecutor", notNullValue()),
                withJsonPath("$.prosecutor.prosecutorCode", is("CPS")),
                withJsonPath("$.prosecutor.prosecutorId", notNullValue()),
                withJsonPath("$.isCivil", is(true)),
                withJsonPath("$.caseUrn", is(urn))

        )));
    }


    @Test
    public void shouldReturnDefenceClientDetailsWhileMultipleUrnInDefenceButOneHearingRecord() {

        final String hearingDate = LocalDate.now().toString();

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, userId);
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId1, urn1, defendantId1, firstName, lastName, dateOfBirth, null, null, userId);

        stubListingServiceForCasesByPersonDefendantAndHearingDate(urn, resource1);
        stubUserPermissions();
        stubForProsecutionCaseQuery();
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollDefenceClientWithoutUrn(firstName, lastName, dateOfBirth, hearingDate, userId);


        final String responseEntity = response.getPayload();
        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.defendantId", is(defendantId)),
                withJsonPath("$.caseId", is(caseId.toString())),
                withJsonPath("$.caseUrn", is(urn)),
                withJsonPath("$.prosecutionAuthorityCode", is("TFL")),
                withJsonPath("$.prosecutor", notNullValue()),
                withJsonPath("$.prosecutor.prosecutorCode", is("CPS")),
                withJsonPath("$.prosecutor.prosecutorId", notNullValue())
        )));

    }

    @Test
    public void shouldReturnDefenceClientDetailsWhileMultipleUrnInDefenceButOneHearingRecordForCivil() {
        final String hearingDate = LocalDate.now().toString();

        createProsecutionCaseHelper.createAndVerifyCivilProsecutionCase(caseId, urn, defendantId, firstName, lastName, dateOfBirth, userId);
        createProsecutionCaseHelper.createAndVerifyCivilProsecutionCase(caseId1, urn1, defendantId1, firstName, lastName, dateOfBirth, userId);

        stubListingServiceForCasesByPersonDefendantAndHearingDate(urn, resource1);
        stubUserPermissions();
        stubForProsecutionCaseQuery();
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollDefenceClientWithoutUrnForCivil(firstName, lastName, dateOfBirth, hearingDate, userId);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.defendantId", is(defendantId)),
                withJsonPath("$.caseId", is(caseId.toString())),
                withJsonPath("$.caseUrn", is(urn)),
                withJsonPath("$.prosecutionAuthorityCode", is("TFL")),
                withJsonPath("$.prosecutor", notNullValue()),
                withJsonPath("$.prosecutor.prosecutorCode", is("CPS")),
                withJsonPath("$.prosecutor.prosecutorId", notNullValue())
        )));
    }

    @Test
    public void shouldReturnDefenceClientDetailsWhileMultipleUrnInDefenceButOneHearingRecordForCivil_NoDobForDefendant() {
        final String hearingDate = LocalDate.now().toString();

        createProsecutionCaseHelper.createAndVerifyCivilProsecutionCaseWithoutDob(caseId, urn, defendantId, firstName, lastName);
        createProsecutionCaseHelper.createAndVerifyCivilProsecutionCaseWithoutDob(caseId1, urn1, defendantId1, firstName, lastName);

        stubListingServiceForCasesByPersonDefendantAndHearingDate(urn, resource1);
        stubUserPermissions();
        stubForProsecutionCaseQuery();
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response =  pollDefenceClientWithoutUrnWithOutDobForCivil(firstName, lastName, hearingDate, userId);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.defendantId", is(defendantId)),
                withJsonPath("$.caseId", is(caseId.toString())),
                withJsonPath("$.caseUrn", is(urn)),
                withJsonPath("$.prosecutionAuthorityCode", is("TFL")),
                withJsonPath("$.prosecutor", notNullValue()),
                withJsonPath("$.prosecutor.prosecutorCode", is("CPS")),
                withJsonPath("$.prosecutor.prosecutorId", notNullValue())
        )));
    }



    @Test
    public void shouldReturnDefenceClientDetailsWhileMultipleUrnInDefenceAndHearing() {
        final String hearingDate = LocalDate.now().toString();

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, userId);
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId1, urn1, defendantId1, firstName, lastName, dateOfBirth, null, null, userId);

        stubListingServiceForCasesByPersonDefendantAndHearingDate(urn, resource2);
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollDefenceClientForDefenceClientCountByPerson(firstName, lastName, dateOfBirth, hearingDate, userId);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.defenceClientCount", is(2)))));
    }

    @Test
    public void shouldReturnDefenceClientDetailsWhileMultipleUrnInDefenceAndNoHearingRecord() {
        final String hearingDate = LocalDate.now().toString();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, userId);
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId1, urn1, defendantId1, firstName, lastName, dateOfBirth, null, null, userId);

        stubListingServiceForCasesByPersonDefendantAndHearingDate(urn, resource3);
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollDefenceClientForDefenceClientCountByPerson(firstName, lastName, dateOfBirth, hearingDate, userId);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.defenceClientCount", is(2)))));
    }

    @Test
    public void shouldReturnDefenceClientDetailsWhileMultipleUrnInDefenceAndNoHearingRecord_ForCivilNoDob() {
        final String hearingDate = LocalDate.now().toString();
        createProsecutionCaseHelper.createAndVerifyCivilProsecutionCaseWithoutDob(caseId, urn, defendantId, firstName, lastName);
        createProsecutionCaseHelper.createAndVerifyCivilProsecutionCaseWithoutDob(caseId1, urn1, defendantId1, firstName, lastName);

        stubListingServiceForCasesByPersonDefendantAndHearingDate(urn, resource3);
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response =  pollDefenceClientForDefenceClientCountByPersonNoDob(firstName, lastName, hearingDate, userId);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.defenceClientCount", is(2)))));
    }


    @Test
    public void shouldReturnDefenceClientDetailsByOrganisationWhileMultipleUrnInDefenceButOneHearingRecord() {
        final String hearingDate = LocalDate.now().toString();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendant(caseId, urn, defendantId, organisationName, userId);
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendant(caseId1, urn1, defendantId1, organisationName, userId);

        stubListingServiceForCasesByOrganisationDefendantAndHearingDate(urn, resource1);
        stubUserPermissions();
        stubForProsecutionCaseQuery();
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollDefenceClientForOrganisationWithoutUrn(organisationName, hearingDate, userId);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.defendantId", is(defendantId)),
                withJsonPath("$.caseId", is(caseId.toString())),
                withJsonPath("$.caseUrn", is(urn)),
                withJsonPath("$.prosecutionAuthorityCode", is("DVL2")),
                withJsonPath("$.prosecutor", notNullValue()),
                withJsonPath("$.prosecutor.prosecutorCode", is("CPS")),
                withJsonPath("$.prosecutor.prosecutorId", notNullValue()),
                withJsonPath("$.caseUrn", is(urn))

        )));

    }


    @Test
    public void shouldReturnDefenceClientDetailsByOrganisationWhileMultipleUrnInDefenceButOneHearingRecordForCivil() {
        final String hearingDate = LocalDate.now().toString();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendantForCivil(caseId, urn, defendantId, organisationName, userId);
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendantForCivil(caseId1, urn1, defendantId1, organisationName, userId);

        stubListingServiceForCasesByOrganisationDefendantAndHearingDate(urn, resource1);
        stubUserPermissions();
        stubForProsecutionCaseQuery();
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollDefenceClientForOrganisationWithoutUrnForCivil(organisationName, hearingDate, userId, true);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.defendantId", is(defendantId)),
                withJsonPath("$.caseId", is(caseId.toString())),
                withJsonPath("$.caseUrn", is(urn)),
                withJsonPath("$.prosecutionAuthorityCode", is("DVL2")),
                withJsonPath("$.prosecutor", notNullValue()),
                withJsonPath("$.prosecutor.prosecutorCode", is("CPS")),
                withJsonPath("$.isCivil", is(true)),
                withJsonPath("$.prosecutor.prosecutorId", notNullValue())
        )));
    }

    @Test
    public void shouldNotReturnCivilDefenceClientDetailsByOrganisationForCriminalDefendant() {
        final String hearingDate = LocalDate.now().toString();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendantForCivil(caseId, urn, defendantId, organisationName, userId);

        stubListingServiceForCasesByOrganisationDefendantAndHearingDate(urn, resource1);
        stubUserPermissions();
        stubForProsecutionCaseQuery();
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response =  pollDefenceClientForOrganisationWithoutUrnFor404(organisationName, hearingDate, userId, false);

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND));
    }

    @Test
    public void shouldNotReturnCriminalDefenceClientDetailsByOrganisationForCivilRespondent() {
        final String hearingDate = LocalDate.now().toString();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendant(caseId, urn, defendantId, organisationName, userId);

        stubListingServiceForCasesByOrganisationDefendantAndHearingDate(urn, resource1);
        stubUserPermissions();
        stubForProsecutionCaseQuery();
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response =  pollDefenceClientForOrganisationWithoutUrnFor404(organisationName, hearingDate, userId, true);

        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND));
    }

    @Test
    public void shouldReturnDefenceClientDetailsByOrganisationWhileMultipleUrnInDefenceAndHearing() {
        final String hearingDate = LocalDate.now().toString();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendant(caseId, urn, defendantId, organisationName, userId);
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendant(caseId1, urn1, defendantId1, organisationName, userId);

        stubListingServiceForCasesByOrganisationDefendantAndHearingDate(urn, resource2);
        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollDefenceClientForDefenceClientCountByOrganisation(organisationName, hearingDate, userId);

        final String responseEntity = response.getPayload();
        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.defenceClientCount", is(2)))));

    }

    @Test
    public void shouldReturnNotFound() {
        final Response response = queryDefenceClient("55DP0028117", "Joe", "Bloggs", "1983-04-20", userId);
        assertThat(response.getStatus(), is(HttpStatus.SC_NOT_FOUND));
    }

    @Test
    public void shouldReturnBadRequestWhenDobIsInvalid() {
        final String dob = "1983-04-20X";
        final Response response = queryDefenceClient("55DP0028117", "Joe", "Bloggs", dob, userId);
        assertThat(response.getStatus(), is(HttpStatus.SC_BAD_REQUEST));
        assertThat(response.readEntity(String.class), containsString(dob));
    }

    @Test
    public void shouldReturnNotFoundWhenUrnIsInvalid() {
        final String urn = "TVL12MX";
        final Response response = queryDefenceClient(urn, "Joe", "Bloggs", "1983-04-20", userId);
        assertThat(response.getStatus(), is(HttpStatus.SC_NOT_FOUND));
        String value = null;
        try {
            value = response.readEntity(String.class);
        } catch (Exception e) {
            // ignore
        }
        Assertions.assertNull(value);
    }

    @Test
    public void shouldReturnBadRequestWhenLastNameNotSend() {
        final String urn = generateUrn();
        final Response response = queryDefenceClient(format("/defence-query-api/query/api/rest/defence/defenceclient/individual?firstName=%s&dateOfBirth=%s&hearingDate=%s&urn=%s", "Joe", "1983-04-20", "2022-10-20", urn), userId);
        assertThat(response.getStatus(), is(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldReturnABadRequestWhenFirstNameNotSend() {
        final String urn = generateUrn();
        final Response response = queryDefenceClient(format("/defence-query-api/query/api/rest/defence/defenceclient/individual?lastName=%s&dateOfBirth=%s&hearingDate=%s&urn=%s", "Joe", "1983-04-20", "2022-10-20", urn), userId);
        assertThat(response.getStatus(), is(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldReturnABadRequestWhenDateOfBirthNotSend() {
        final String urn = generateUrn();
        final Response response = queryDefenceClient(format("/defence-query-api/query/api/rest/defence/defenceclient/individual?firstName=%s&lastName=%s&hearingDate=%s&urn=%s", "Joe", "Taylor", "2022-10-20", urn), userId);
        assertThat(response.getStatus(), is(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldReturnBadRequestWhenJustSendCaseURN() {
        final String urn = generateUrn();
        final Response response = queryDefenceClient(format("/defence-query-api/query/api/rest/defence/defenceclient/organisation?urn=%s", urn), userId);
        assertThat(response.getStatus(), is(HttpStatus.SC_BAD_REQUEST));
    }

    @Test
    public void shouldReturnAssociatedOrganisation() throws Exception {

        final String organisationName = "A and A LLP";
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, userId);

        final ResponseData response = pollDefenceClient(urn, firstName, lastName, dateOfBirth, userId);
        assertThat(response.getStatus(), is(Response.Status.OK));

        associateOrganisation(defendantId, userId.toString());
        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, organisationId.toString(), userId.toString());

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(Matchers.allOf(
                                withJsonPath("defenceClientId", equalTo(defendantId)),
                                withJsonPath("caseId", equalTo(caseId.toString())),
                                withJsonPath("$.associatedOrganisation.organisationName", equalTo(organisationName)),
                                withJsonPath("$.associatedOrganisation.organisationId", equalTo(organisationId.toString()))
                        )

                );

        pollDefenceClientWithMatcher(urn, firstName, lastName, dateOfBirth, userId, payloadMatcher);

    }

    @Test
    public void shouldReturnAssociatedOrganisationAndLastAssociationOrganisation() throws Exception {

        final String organisationName = "A and A LLP";
        stubForReferenceDataQueryOffence();
        stubGetOrganisationDetailsForUser(userId, organisationId);
        stubGetOrganisationNamesForIds(userId, singletonList(organisationId));
        stubUsersGroupsPermission();
        stubGetOrganisationDetails(organisationId.toString(), organisationName);

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, userId);

        pollDefenceClient(urn, firstName, lastName, dateOfBirth, userId);

        associateOrganisation(defendantId, userId.toString());
        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, organisationId.toString(), userId.toString());

        stubGetOrganisationDetails(organisationId_2.toString(), organisationName);
        stubAccessControl(true, userId_org2, "Defence Lawyers", "System Users");
        stubGetOrganisationDetailsForUser(userId_org2, organisationId_2);
        stubGetOrganisationNamesForGivenId(organisationId);
        stubGetOrganisationNamesForGivenId(organisationId_2);

        createProsecutionCaseHelper.createDefenceAssociationWithOrganisationIdForLAA(defendantId, organisationId_2.toString());
        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, organisationId_2.toString(), userId_org2.toString());

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(Matchers.allOf(
                                withJsonPath("defenceClientId", equalTo(defendantId)),
                                withJsonPath("caseId", equalTo(caseId.toString())),
                                withJsonPath("$.lastAssociatedOrganisation.organisationName", equalTo(organisationName)),
                                withJsonPath("$.lastAssociatedOrganisation.organisationId", equalTo(organisationId.toString()))
                        )
                );


        pollDefenceClientWithMatcher(urn, firstName, lastName, dateOfBirth, userId_org2, payloadMatcher);
        pollDefenceOrganisationWithMatcher(caseId, userId_org2, new ResponsePayloadMatcher());

    }

    @Test
    public void shouldNotReturnIDPCMetadataWhenIDPCForDefendantNotFound() {
        final Response response = queryIDPCMetadataForDefendant(defendantId, userId);
        assertThat(response.getStatus(), is(HttpStatus.SC_NOT_FOUND));
    }

    @Test
    public void shouldGetCaseByPersonDefendant() {

        firstName = " " + simpleStringGenerator.next() + " ";
        lastName = " " + simpleStringGenerator.next() + " ";
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, userId);

        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response =  pollCaseByPersonDefendant(firstName.trim(), lastName.trim(), dateOfBirth, userId);
        final String responseEntity = response.getPayload();

        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.caseIds[0]", is(caseId.toString())),
                withJsonPath("$.defendants[0]", is(defendantId))
        )));
    }

    @Test
    public void shouldGetCaseByPersonDefendantForCivil() {

        createProsecutionCaseHelper.createAndVerifyCivilProsecutionCase(caseId, urn, defendantId, firstName, lastName, dateOfBirth, userId);

        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollCaseByPersonDefendantForCivil(firstName, lastName, dateOfBirth, userId);
        final String responseEntity = response.getPayload();

        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.caseIds[0]", is(caseId.toString())),
                withJsonPath("$.defendants[0]", is(defendantId))
        )));
    }

    @Test
    public void shouldGetCaseByPersonDefendantForCivilWithOutDob() {

        createProsecutionCaseHelper.createAndVerifyCivilProsecutionCaseWithoutDob(caseId, urn, defendantId, firstName, lastName);

        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response =  pollCaseByPersonDefendantForCivilWithOutDob(caseId, firstName, lastName, userId);
        final String responseEntity = response.getPayload();

        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.caseIds[0]", is(caseId.toString())),
                withJsonPath("$.defendants[0]", is(defendantId))
        )));
    }

    @Test
    public void shouldGetCaseByOrganisationDefendant() {
        final String organisationName = simpleStringGenerator.next();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendant(caseId, urn, defendantId, organisationName, userId);

        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollCaseByOrganisationDefendant(organisationName, userId);
        final String responseEntity = response.getPayload();

        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.caseIds[0]", is(caseId.toString())),
                withJsonPath("$.defendants[0]", is(defendantId))
        )));

    }

    @Test
    public void shouldGetCaseByOrganisationDefendantForCivil() {
        final String organisationName = simpleStringGenerator.next();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithOrganisationDefendantForCivil(caseId, urn, defendantId, organisationName, userId);

        stubAccessControl(true, userId, "Defence Lawyers");

        final ResponseData response = pollCaseByOrganisationDefendantForCivil(organisationName, userId);
        final String responseEntity = response.getPayload();

        assertThat(responseEntity, isJson(Matchers.allOf(
                withJsonPath("$.caseIds[0]", is(caseId.toString())),
                withJsonPath("$.defendants[0]", is(defendantId))
        )));

    }
}
