package uk.gov.moj.defence.it;


import static com.jayway.jsonassert.JsonAssert.with;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.String.format;
import static java.time.LocalDate.parse;
import static java.time.Period.ofYears;
import static java.util.UUID.randomUUID;
import static java.util.regex.Pattern.compile;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;
import static uk.gov.justice.services.test.utils.core.random.DateGenerator.Direction.FUTURE;
import static uk.gov.moj.defence.domain.common.UrnRegex.URN_PATTERN;
import static uk.gov.moj.defence.helper.AllegationsHelper.pollForAllegations;
import static uk.gov.moj.defence.helper.AllegationsHelper.pollForAllegationsForbidden;
import static uk.gov.moj.defence.helper.CreateProsecutionCaseHelper.FIRST_NAME;
import static uk.gov.moj.defence.helper.CreateProsecutionCaseHelper.LAST_NAME;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControl;
import static uk.gov.moj.defence.util.HttpHeaders.createHttpHeaders;
import static uk.gov.moj.defence.util.MaterialQueryStub.stubForMaterialQueryPerson;
import static uk.gov.moj.defence.util.ProsecutionCaseQueryStub.stubForProsecutionCaseQuery;
import static uk.gov.moj.defence.util.ReferenceDataOffencesQueryStub.stubForReferenceDataQueryOffence;
import static uk.gov.moj.defence.util.RestQueryUtil.pollIDPCMetadata;
import static uk.gov.moj.defence.util.RestQueryUtil.queryIDPCMetadataForDefendant;
import static uk.gov.moj.defence.util.TestUtils.getPayloadForCreatingRequest;
import static uk.gov.moj.defence.util.TestUtils.postMessageToTopic;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetailsForUser;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationNamesForIds;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUserPermissions;
import static uk.gov.moj.defence.util.WiremockHelper.resetWiremock;

import uk.gov.justice.json.generator.value.string.RegexGenerator;
import uk.gov.justice.json.generator.value.string.SimpleStringGenerator;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.justice.services.test.utils.core.random.LocalDateGenerator;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.moj.defence.helper.CreateProsecutionCaseHelper;
import uk.gov.moj.defence.helper.OrganisationHelper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import com.jayway.jsonpath.ReadContext;
import org.apache.http.HttpStatus;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefenceEndToEndIT {

    private static final String MATERIAL_ID = randomUUID().toString();
    private static final String MATERIAL_ID_2 = randomUUID().toString();
    private static final UUID USER_ID = randomUUID();
    private static final UUID ORGANISATION_ID = randomUUID();
    private static final RegexGenerator regexGenerator = new RegexGenerator(compile(URN_PATTERN));

    private final SimpleStringGenerator simpleStringGenerator = new SimpleStringGenerator(5, 15);
    private final LocalDateGenerator localDateGenerator = new LocalDateGenerator(ofYears(10), parse("1983-04-20"), FUTURE);
    private String firstName;
    private String lastName;
    private String dateOfBirth;
    private String defenceClientId;
    private String organisationName;
    private CreateProsecutionCaseHelper createProsecutionCaseHelper;
    private OrganisationHelper organisationHelper;
    private UUID caseId;
    private String urn;

    @BeforeAll
    public static void setupClass() {
        resetWiremock();
        stubForReferenceDataQueryOffence();
        stubForMaterialQueryPerson();
        stubAccessControl(true, USER_ID, "Defence Lawyers");
        stubGetOrganisationDetailsForUser(USER_ID, ORGANISATION_ID);
        stubGetOrganisationNamesForIds(USER_ID, asList(ORGANISATION_ID));
        stubUserPermissions();
        stubForProsecutionCaseQuery();
    }

    @BeforeEach
    public void setupTests() {
        createProsecutionCaseHelper = new CreateProsecutionCaseHelper();
        organisationHelper = new OrganisationHelper();
        caseId = randomUUID();
        urn = regexGenerator.next();
        firstName = simpleStringGenerator.next();
        lastName = simpleStringGenerator.next();
        defenceClientId = randomUUID().toString();
        dateOfBirth = new LocalDateGenerator(ofYears(10), parse("1983-04-20"), FUTURE).next().toString();

    }

    @Test
    public void shouldWorkEndToEndWithDefendantAddedPublicEvent() {
        createProsecutionCaseHelper.createAndVerifyProsecutionCase(caseId, urn, "MCC", dateOfBirth, USER_ID);
        final String generatedDefenceDefendantId = organisationHelper.addDefendant(urn, getDefendantAddedData(caseId.toString()), firstName, lastName, dateOfBirth, organisationName, USER_ID, ORGANISATION_ID, false);

        //instruction details are not yet recorded and hence idpc download will result in error
        organisationHelper.publishIdpcEvent(caseId.toString(), defenceClientId, generatedDefenceDefendantId, urn, MATERIAL_ID, USER_ID, format("%s %s", lastName, firstName));
        downloadIDPCWithAnError(generatedDefenceDefendantId, urn);

        //now record instruction details
        organisationHelper.recordInstructionDetails(urn, firstName, lastName, dateOfBirth, generatedDefenceDefendantId, USER_ID, ORGANISATION_ID, organisationName, false);
        organisationHelper.verifyAllegations(generatedDefenceDefendantId, true, USER_ID);

        //IDPC should be downloadable now
        organisationHelper.publishIdpcEvent(caseId.toString(), defenceClientId, generatedDefenceDefendantId, urn, MATERIAL_ID, USER_ID, format("%s %s", lastName, firstName));
        organisationHelper.downloadIDPCAndVerifyAllegationStatus(generatedDefenceDefendantId, USER_ID, ORGANISATION_ID, MATERIAL_ID);
    }

    @Test
    public void testEndToEndFlowWithCCCaseReceivedEvent() {
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defenceClientId, firstName, lastName, dateOfBirth, null, null, USER_ID);

        final String defenceClientIdStringValue = getGeneratedDefendantId(urn, firstName, lastName, dateOfBirth);
        organisationHelper.recordInstructionDetails(urn, firstName, lastName, dateOfBirth, defenceClientIdStringValue, USER_ID, ORGANISATION_ID, organisationName, false);

        final String generatedDefenceDefendantId = getGeneratedDefendantId(urn, firstName, lastName, dateOfBirth);

        organisationHelper.verifyAllegations(generatedDefenceDefendantId, false, USER_ID);
        organisationHelper.publishIdpcEvent(caseId.toString(), defenceClientId, generatedDefenceDefendantId, urn, MATERIAL_ID, USER_ID, format("%s %s", lastName, firstName));
        organisationHelper.downloadIDPCAndVerifyAllegationStatus(generatedDefenceDefendantId, USER_ID, ORGANISATION_ID, MATERIAL_ID);
        organisationHelper.verifyAllegations(generatedDefenceDefendantId, true, USER_ID);

    }


    @Test
    public void testEndToEndFlowWithCCCaseReceivedEventWithMultipleIDPC() {

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defenceClientId, firstName, lastName, dateOfBirth, null, null, USER_ID);

        final String defenceClientIdStringValue = getGeneratedDefendantId(urn, firstName, lastName, dateOfBirth);
        organisationHelper.recordInstructionDetails(urn, firstName, lastName, dateOfBirth, defenceClientIdStringValue, USER_ID, ORGANISATION_ID, organisationName, false);
        final String generatedDefenceDefendantId = getGeneratedDefendantId(urn, firstName, lastName, dateOfBirth);
        organisationHelper.verifyAllegations(generatedDefenceDefendantId, false, USER_ID);
        publishMultipleIdpcEvent(caseId.toString(), defenceClientId, generatedDefenceDefendantId, urn);
        organisationHelper.downloadIDPCAndVerifyAllegationStatus(generatedDefenceDefendantId, USER_ID, ORGANISATION_ID, MATERIAL_ID_2);
        organisationHelper.verifyAllegations(generatedDefenceDefendantId, true, USER_ID);

        final Response responseIDPCMetadataForDefendant = queryIDPCMetadataForDefendant(defenceClientIdStringValue, USER_ID);
        assertThat(responseIDPCMetadataForDefendant.getStatus(), is(HttpStatus.SC_OK));
    }

    @Test
    public void shouldReturnForbiddenWhenInstructionNotFoundForAllegations() {

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defenceClientId, firstName, lastName, dateOfBirth, null, null, USER_ID);
        final String defenceClientId = getGeneratedDefendantId(urn, firstName, lastName, dateOfBirth);
        pollForAllegationsForbidden(defenceClientId, USER_ID);
    }

    @Test
    public void testEndToEndFlowWithCCCaseReceivedEventWithoutASN() {

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendantNotHavingASN(caseId, urn, defenceClientId, firstName, lastName, dateOfBirth, USER_ID);

        final String defenceClientIdStringValue = getGeneratedDefendantId(urn, firstName, lastName, dateOfBirth);
        organisationHelper.recordInstructionDetails(urn, firstName, lastName, dateOfBirth, defenceClientIdStringValue, USER_ID, ORGANISATION_ID, organisationName, false);

        final String generatedDefenceDefendantId = getGeneratedDefendantId(urn, firstName, lastName, dateOfBirth);

        organisationHelper.verifyAllegations(generatedDefenceDefendantId, false, USER_ID);
        organisationHelper.publishIdpcEvent(caseId.toString(), defenceClientId, generatedDefenceDefendantId, urn, MATERIAL_ID, USER_ID, format("%s %s", lastName, firstName));
        organisationHelper.downloadIDPCAndVerifyAllegationStatus(generatedDefenceDefendantId, USER_ID, ORGANISATION_ID, MATERIAL_ID);
    }


    @Test
    public void testEndToEndFlowWithIDPCReceivedBeforeCase() {

        organisationHelper.publishIdpcEventBeforeCaseReceived(caseId.toString(), defenceClientId, MATERIAL_ID);
        createProsecutionCaseHelper.createAndVerifyProsecutionCase(caseId, urn, "CPPI", defenceClientId, dateOfBirth, USER_ID);
        organisationHelper.pollAndVerifyIdpcMetadata(defenceClientId, urn, USER_ID, format("%s %s", LAST_NAME, FIRST_NAME));
        organisationHelper.recordInstructionDetails(urn, FIRST_NAME, LAST_NAME, dateOfBirth, defenceClientId, USER_ID, ORGANISATION_ID, organisationName, false);
        organisationHelper.downloadIDPCAndVerifyAllegationStatus(defenceClientId, USER_ID, ORGANISATION_ID, MATERIAL_ID);

    }

    @Test
    public void shouldRaiseEventAndInvokeCommandForMultipleOffences() {
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendantWithTwoOffences(caseId, urn, defenceClientId, firstName, lastName, dateOfBirth, USER_ID);
    }

    @Test
    public void shouldRaiseEventProsecutionCaseFileReceivedAndInvokeCommandForMultipleDefendants() {

        final String secondDefendantFirstName = simpleStringGenerator.next();
        final String secondDefendantLastName = simpleStringGenerator.next();
        final LocalDate secondDefendantDob = localDateGenerator.next();
        final String secondDefendantIdString = randomUUID().toString();

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithTwoDefendants(caseId, urn, defenceClientId, secondDefendantIdString, firstName, lastName, parse(dateOfBirth),
                secondDefendantFirstName, secondDefendantLastName, secondDefendantDob, USER_ID);

        final String generatedDefenceDefendantId = getGeneratedDefendantId(urn, firstName, lastName, dateOfBirth);
        organisationHelper.recordInstructionDetails(urn, firstName, lastName, dateOfBirth, generatedDefenceDefendantId, USER_ID, ORGANISATION_ID, organisationName, false);
        organisationHelper.verifyAllegations(generatedDefenceDefendantId, false, USER_ID);

        assertThat(generatedDefenceDefendantId, is(notNullValue()));

        organisationHelper.publishIdpcEvent(caseId.toString(), defenceClientId, generatedDefenceDefendantId, urn, MATERIAL_ID, USER_ID, format("%s %s", lastName, firstName));

        organisationHelper.downloadIDPCAndVerifyAllegationStatus(generatedDefenceDefendantId, USER_ID, ORGANISATION_ID, MATERIAL_ID);
    }

    @Test
    public void shouldHandleEventSpiProsecutionCaseFileDefendantAdded() {

        final String secondDefendantFirstName = simpleStringGenerator.next();
        final String secondDefendantLastName = simpleStringGenerator.next();
        final LocalDate secondDefendantDob = localDateGenerator.next();
        final String secondDefenceClientIdString = randomUUID().toString();

        createProsecutionCaseHelper.createAndVerifySPIProsecutionCaseWithDefendantAdded(caseId, defenceClientId, secondDefenceClientIdString,
                firstName, lastName, parse(dateOfBirth), secondDefendantFirstName, secondDefendantLastName, secondDefendantDob, USER_ID);
    }

    @Test
    public void shouldHandleEventSpiProsecutionCaseFileDefendantAddedWithNullFirstNameAndDateOfBirth() {
        createProsecutionCaseHelper.createAndVerifySPIProsecutionCaseWithDefendantAddedWithNullFirstName(caseId, defenceClientId, lastName);
    }

    @Test
    public void shouldRaiseDefenceClientReceivedEventAndPersistDefenceClientDataWhenCcCaseReceivedEventOccurred() {

        final String defendantId = randomUUID().toString();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, USER_ID);
        organisationHelper.verifyDefenceClient(caseId, urn, firstName, lastName, dateOfBirth, defendantId, USER_ID, organisationName, false);
    }

    private String getDefendantAddedData(final String caseId) {
        String defendantAddedData = getPayloadForCreatingRequest("stub-data/public-events/public.progression.defendants-added-to-case.json");
        defendantAddedData = defendantAddedData.replace("CASE-ID", caseId);
        defendantAddedData = defendantAddedData.replace("DEFENDANT-ID", defenceClientId);
        defendantAddedData = defendantAddedData.replace("FIRST-NAME", firstName);
        defendantAddedData = defendantAddedData.replace("LAST-NAME", lastName);
        defendantAddedData = defendantAddedData.replace("DOB", dateOfBirth);
        return defendantAddedData;

    }

    private String getGeneratedDefendantId(final String urn, final String firstName, final String lastName, final String dob) {
        return organisationHelper.getGeneratedDefendantId(urn, firstName, lastName, dob, organisationName, USER_ID, false);
    }

    private void downloadIDPCWithAnError(final String defenceClientId, final String userId) {

        final String queryString = format("/defence-query-api/query/api/rest/defence/defenceclient/%s/idpc", defenceClientId);
        final RestClient restClient = new RestClient();

        final Response response = restClient.query(getBaseUri() + queryString,
                "application/vnd.defence.query.defence-client-idpc+json", createHttpHeaders(userId));
        assertThat(response.getStatusInfo(), is(Response.Status.FOUND));
    }

    private void publishMultipleIdpcEvent(final String caseId, final String defenceClientIdString, final String defenceDefendantId, final String urn) {
        final String idpcPublishedData = getPayloadForCreatingRequest("stub-data/public-events/public.progression.defendant-idpc-added.json")
                .replace("CASE-ID", caseId)
                .replace("DEFENDANT_ID", defenceClientIdString)
                .replace("MATERIAL_ID", MATERIAL_ID);

        postMessageToTopic(idpcPublishedData, "public.progression.idpc-document-received");

        final String idpcPublishedData1 = getPayloadForCreatingRequest("stub-data/public-events/public.progression.defendant-idpc-added.json")
                .replace("CASE-ID", caseId)
                .replace("DEFENDANT_ID", defenceClientIdString)
                .replace("MATERIAL_ID", MATERIAL_ID_2);

        postMessageToTopic(idpcPublishedData1, "public.progression.idpc-document-received");

        final ResponseData response = pollIDPCMetadata(defenceDefendantId, USER_ID);
        String content = response.getPayload();
        with(content).assertThat("idpcMetadata.documentName", is(format("%s %s %s IDPC", lastName, firstName, urn)));

        final List<Matcher<? super ReadContext>> allegationMatchers = List.of(withJsonPath("$.idpcPublished", is(true)));
        pollForAllegations(defenceDefendantId, USER_ID, allegationMatchers);
    }

}
