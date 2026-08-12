package uk.gov.moj.defence.it;


import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.String.format;
import static java.time.LocalDate.parse;
import static java.time.Period.ofYears;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static java.util.regex.Pattern.compile;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClientBuilder.aMessageConsumerClient;
import static uk.gov.justice.services.test.utils.core.random.DateGenerator.Direction.FUTURE;
import static uk.gov.moj.defence.domain.common.UrnRegex.URN_PATTERN;
import static uk.gov.moj.defence.helper.AllegationsHelper.pollForAllegations;
import static uk.gov.moj.defence.helper.TopicNames.PUBLIC_EVENT;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControl;
import static uk.gov.moj.defence.util.ProsecutionCaseQueryStub.stubForProsecutionCaseQuery;
import static uk.gov.moj.defence.util.ReferenceDataOffencesQueryStub.stubForReferenceDataQueryOffence;
import static uk.gov.moj.defence.util.RestHelper.postCommand;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClient;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetailsForUser;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationNamesForIds;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUserPermissions;
import static uk.gov.moj.defence.util.WiremockHelper.resetWiremock;

import uk.gov.justice.json.generator.value.string.RegexGenerator;
import uk.gov.justice.json.generator.value.string.SimpleStringGenerator;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClient;
import uk.gov.justice.services.test.utils.core.random.LocalDateGenerator;
import uk.gov.moj.defence.helper.CreateProsecutionCaseHelper;
import uk.gov.moj.defence.helper.DefendantOperationsHelper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProgressionDefendantOperationsIT {

    private static final String PUBLIC_DEFENCE_EVENT_RECORD_INSTRUCTION_DETAILS = "public.defence.event.record-instruction-details";
    private static final UUID userId = randomUUID();
    private static final RegexGenerator regexGenerator = new RegexGenerator(compile(URN_PATTERN));

    private String firstName;
    private String lastName;
    private String dateOfBirth;

    private DefendantOperationsHelper defendantOperationsHelper = new DefendantOperationsHelper();
    private CreateProsecutionCaseHelper createProsecutionCaseHelper = new CreateProsecutionCaseHelper();

    private UUID caseId;
    private String urn;


    @BeforeAll
    public static void setupClass() {
        resetWiremock();
        stubForReferenceDataQueryOffence();
        stubAccessControl(true, userId, "Defence Lawyers");
        UUID organisationId = randomUUID();
        stubGetOrganisationDetailsForUser(userId, organisationId);
        stubGetOrganisationNamesForIds(userId, asList(organisationId));
        stubUserPermissions();
        stubForProsecutionCaseQuery();
    }

    @BeforeEach
    public void setupTests() {
        caseId = randomUUID();
        urn = regexGenerator.next();
        firstName = new SimpleStringGenerator(5, 15).next();
        lastName = new SimpleStringGenerator(5, 15).next();
        dateOfBirth = new LocalDateGenerator(ofYears(10), parse("1983-04-20"), FUTURE).next().toString();
    }

    @Test
    public void testEndToEndUpdateFlowWithPublicProgressionCaseDefendantChangedForIndividualDefendant() {

        final String defendantId = randomUUID().toString();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, userId);

        defendantOperationsHelper.updateIndividualDefendant(defendantId, caseId.toString());
        verifyDefenceClientForIndividual(caseId, urn, defendantId, "updatedName", "updatedLastName", "2010-01-01");
    }

    @Test
    public void testEndToEndUpdateFlowWithPublicProgressionCaseDefendantChangedForCorporateDefendant() {

        final String defendantId = randomUUID().toString();
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, userId);

        defendantOperationsHelper.updateCorporateDefendant(defendantId, caseId.toString());
        verifyDefenceClientForCorporate(caseId, urn, defendantId, firstName, lastName, dateOfBirth);
    }

    @Test
    public void testEndToEndFlowForPublicProgressionDefendantOffencesChangedWithDeleteAndAddNewOffence() {
        final UUID offenceId1 = UUID.randomUUID();
        final UUID offenceId2 = UUID.randomUUID();

        final String defenceClientIdString = randomUUID().toString();

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defenceClientIdString, firstName, lastName, dateOfBirth, offenceId1, offenceId2, userId);

        final String generatedDefenceDefendantId = recordInstructionDetails(urn, firstName, lastName, dateOfBirth);

        verifyAllegations(generatedDefenceDefendantId);

        defendantOperationsHelper.updateOffenderDetails(defenceClientIdString, caseId.toString(), offenceId1.toString(), offenceId2.toString());

        verifyAllegationsAfterUpdate(generatedDefenceDefendantId, 1, userId);
    }

    @Test
    public void testEndToEndFlowForPublicProgressionDefendantOffencesChangedWithUpdateOffence() {
        final UUID offenceId1 = UUID.randomUUID();
        final UUID offenceId2 = UUID.randomUUID();

        final String defenceClientIdString = randomUUID().toString();

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defenceClientIdString, firstName, lastName, dateOfBirth, offenceId1, offenceId2, userId);

        final String generatedDefenceDefendantId = recordInstructionDetails(urn, firstName, lastName, dateOfBirth);

        verifyAllegations(generatedDefenceDefendantId);

        defendantOperationsHelper.updateOffenderDetails(defenceClientIdString, caseId.toString(), offenceId1.toString(), offenceId2.toString());
        verifyAllegationsAfterUpdate(generatedDefenceDefendantId, 1, userId);
        defendantOperationsHelper.updateExistOffender(defenceClientIdString, caseId.toString(), offenceId1.toString());
        verifyAllegationsAfterUpdate(generatedDefenceDefendantId, 1, userId);
    }

    private String recordInstructionDetails(final String urn, final String firstName, final String lastName, final String dob) {
        final String defenceClientIdStringValue = getGeneratedDefendantId(urn, firstName, lastName, dob);
        postInstructionByUserForDefenceClient(fromString(defenceClientIdStringValue), userId.toString());
        return defenceClientIdStringValue;
    }


    private void postInstructionByUserForDefenceClient(final UUID defenceClientId, final String userId) {
        try (final MessageConsumerClient messageConsumerClient = aMessageConsumerClient().build()) {

            messageConsumerClient.startConsumer(PUBLIC_DEFENCE_EVENT_RECORD_INSTRUCTION_DETAILS, PUBLIC_EVENT);

            final String urlPath = format("/defenceclients/%s/instruction", defenceClientId);
            final String payload = createObjectBuilder()
                    .add("instructionDate", LocalDate.now().minusYears(1L).toString())
                    .build()
                    .toString();


            postCommand(userId, payload, "application/vnd.defence.record-instruction-details+json", urlPath);

            final Optional<String> optional = messageConsumerClient.retrieveMessage();
            assertTrue(optional.isPresent());
            final JsonObject jsonObject = new StringToJsonObjectConverter().convert(optional.get());
            assertThat(jsonObject.getJsonObject("_metadata").getString("name"), is(PUBLIC_DEFENCE_EVENT_RECORD_INSTRUCTION_DETAILS));
        }
    }


    private String getGeneratedDefendantId(final String urn, final String firstName, final String lastName, final String dob) {
        final ResponseData response = pollDefenceClient(urn, firstName, lastName, dob, userId);
        final JsonObject jsonResponse = new StringToJsonObjectConverter().convert(response.getPayload());
        return jsonResponse.getString("defenceClientId");
    }

    private void verifyAllegations(final String defenceClientId) {
        pollForAllegations(defenceClientId, userId,
                List.of(
                        withJsonPath("$.idpcPublished", is(false)),
                        withJsonPath("$.allegationDetail", hasSize(2)),
                        withJsonPath("$.allegationDetail[0].legislation", is("REFDATA LEGISLATION CODE OF61131: Contrary to regulations 32(a) & 34(3) of the Animals and Animal Products (Import and Export) (England) Regulations 2006")),
                        withJsonPath("$.allegationDetail[0].title", is("REFDATA TITLE CODE OF61131: Obstruct person acting in the execution of these Regulations")),
                        withJsonPath("$.allegationDetail[0].chargedDate", is("2010-08-01")),
                        withJsonPath("$.allegationDetail[1].legislation", is("REFDATA LEGISLATION CODE OF61131: Contrary to regulations 32(a) & 34(3) of the Animals and Animal Products (Import and Export) (England) Regulations 2006")),
                        withJsonPath("$.allegationDetail[1].title", is("REFDATA TITLE CODE OF61131: Obstruct person acting in the execution of these Regulations")),
                        withJsonPath("$.allegationDetail[1].chargedDate", is("2010-08-01"))
                ));
    }

    private void verifyAllegationsAfterUpdate(final String defenceClientId, final int expectedListSize, final UUID userId) {
        pollForAllegations(defenceClientId, userId,
                List.of(
                        withJsonPath("$.allegationDetail[0].legislation", is("REFDATA: Contrary to regulations 32(a) & 34(3) of the Animals and Animal Products (Import and Export) (England) Regulations 2006")),
                        withJsonPath("$.allegationDetail[0].title", is("REFDATA: Obstruct person acting in the execution of these Regulations")),
                        withJsonPath("$.idpcPublished", is(false)),
                        withJsonPath("$.allegationDetail", hasSize(expectedListSize))
                ));

    }

    private void verifyDefenceClientForIndividual(final UUID caseId, final String urn, final String defendantId,
                                                  final String firstName, final String lastName, final String dateOfBirth) {

        final ResponseData response = pollDefenceClient(urn, firstName, lastName, dateOfBirth, userId);
        final String responseEntity = response.getPayload();
        assertThat(responseEntity, containsString(defendantId));
        assertThat(responseEntity, containsString(caseId.toString()));
    }

    private void verifyDefenceClientForCorporate(final UUID caseId, final String urn, final String defendantId,
                                                 final String firstName, final String lastName, final String dateOfBirth) {

        final ResponseData response = pollDefenceClient(urn, firstName, lastName, dateOfBirth, userId);
        final String responseEntity = response.getPayload();
        assertThat(responseEntity, containsString(defendantId));
        assertThat(responseEntity, containsString(caseId.toString()));
    }

}