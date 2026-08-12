package uk.gov.moj.defence.helper;

import static com.jayway.jsonassert.JsonAssert.with;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.String.format;
import static java.util.UUID.fromString;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClientBuilder.aMessageConsumerClient;
import static uk.gov.moj.defence.helper.AllegationsHelper.pollForAllegations;
import static uk.gov.moj.defence.helper.TopicNames.PUBLIC_EVENT;
import static uk.gov.moj.defence.util.RestHelper.pollForResponse;
import static uk.gov.moj.defence.util.RestHelper.postCommand;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClient;
import static uk.gov.moj.defence.util.RestQueryUtil.pollIDPCMetadata;
import static uk.gov.moj.defence.util.TestUtils.getPayloadForCreatingRequest;
import static uk.gov.moj.defence.util.TestUtils.postMessageToTopic;
import static uk.gov.moj.defence.util.TestUtils.postMessageToTopicAndVerify;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.json.JsonObject;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.ReadContext;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsEqual;

public class OrganisationHelper {

    private static final String PUBLIC_PROGRESSION_DEFENDANTS_ADDED = "public.progression.defendants-added-to-case";
    private static final String PUBLIC_DEFENCE_EVENT_IDPC_ACCESSED_BY_ORGANISATION = "public.defence.event.idpc-accessed-by-organisation";
    private static final String PUBLIC_DEFENCE_EVENT_RECORD_INSTRUCTION_DETAILS = "public.defence.event.record-instruction-details";

    private static final String GET_DEFENCE_CLIENT_IDPC = "/defenceclient/%s/idpc";
    private static final String DEFENCE_QUERY_CONTENT_TYPE = "application/vnd.defence.query.defence-client-idpc+json";
    private static final String GET_DEFENCE_INSTRUCTION = "/defenceclients/%s/instruction";
    private static final String DEFENCE_IDPC_RECEIVED_BEFORE_CASE = "defence.event.idpc-received-before-case";


    public void downloadIDPC(final String defenceClientId, final String userId) {

        pollForResponse(format(GET_DEFENCE_CLIENT_IDPC, defenceClientId),
                DEFENCE_QUERY_CONTENT_TYPE,
                userId,
                List.of(withJsonPath("$.url", is("http://filestorage.com/myfile.pdf"))));
    }

    public void downloadIDPCAndVerifyAllegationStatus(final String defenceClientId, final UUID userId, final UUID organisationId, final String materialId) {
        try (final MessageConsumerClient idpcAccessedByOrganisationConsumer = aMessageConsumerClient().build()) {
            idpcAccessedByOrganisationConsumer.startConsumer(PUBLIC_DEFENCE_EVENT_IDPC_ACCESSED_BY_ORGANISATION, PUBLIC_EVENT);

            downloadIDPC(defenceClientId, userId.toString());

            final Optional<String> idpcAccessedByOrganisationOptional = idpcAccessedByOrganisationConsumer.retrieveMessage();
            assertThat(idpcAccessedByOrganisationOptional.isPresent(), is(true));
            final JsonObject idpcAccessedByOrganisation = new StringToJsonObjectConverter().convert(idpcAccessedByOrganisationOptional.get());
            assertThat(idpcAccessedByOrganisation.getJsonObject("_metadata").getString("name"), is(PUBLIC_DEFENCE_EVENT_IDPC_ACCESSED_BY_ORGANISATION));
            assertThat(idpcAccessedByOrganisation.getString("defenceClientId"), is(defenceClientId));
            assertThat(idpcAccessedByOrganisation.getString("userId"), is(userId.toString()));
            assertThat(idpcAccessedByOrganisation.getString("materialId"), is(materialId));
            assertThat(idpcAccessedByOrganisation.getString("organisationId"), is(organisationId.toString()));
        }

        pollForAllegations(defenceClientId, userId, List.of(withJsonPath("$.idpcPublished", is(true))));
    }


    public void verifyAllegations(final String defenceClientId, final boolean isIdpcPublished, final UUID userId) {
        final List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.defenceClientId", IsEqual.equalTo(defenceClientId)))
                .add(withJsonPath("$.idpcPublished", is(isIdpcPublished)))
                .add(withJsonPath("$.allegationDetail[0].chargedDate", is("2010-08-01")))
                .add(withJsonPath("$.allegationDetail[0].legislation", is("REFDATA LEGISLATION CODE OF61131: Contrary to regulations 32(a) & 34(3) of the Animals and Animal Products (Import and Export) (England) Regulations 2006")))
                .add(withJsonPath("$.allegationDetail[0].title", is("REFDATA TITLE CODE OF61131: Obstruct person acting in the execution of these Regulations")))
                .build();

        pollForAllegations(defenceClientId, userId, matchers);

    }

    public void postInstructionByUserForDefenceClient(final UUID defenceClientId, final String userId) {
        try (final MessageConsumerClient messageConsumerClient = aMessageConsumerClient().build()) {

            messageConsumerClient.startConsumer(PUBLIC_DEFENCE_EVENT_RECORD_INSTRUCTION_DETAILS, PUBLIC_EVENT);

            final String queryString = format(GET_DEFENCE_INSTRUCTION, defenceClientId);
            final String payload = createObjectBuilder()
                    .add("instructionDate", LocalDate.now().minusYears(1L).toString())
                    .build()
                    .toString();
            postCommand(userId, payload, "application/vnd.defence.record-instruction-details+json", queryString);

            final Optional<String> optional = messageConsumerClient.retrieveMessage();
            assertThat(optional.isPresent(), is(true));
            final JsonObject jsonObject = new StringToJsonObjectConverter().convert(optional.get());
            assertThat(jsonObject.getJsonObject("_metadata").getString("name"), is(PUBLIC_DEFENCE_EVENT_RECORD_INSTRUCTION_DETAILS));
        }
    }

    public void verifyDefenceClientWithInstruction(final String urn,
                                                   final String firstName, final String lastName, final String dateOfBirth, final String organisationName, final UUID userId, final UUID organisationId, final boolean pollByOrganisation) {

        final ResponseData response = getDefenceClient(urn, firstName, lastName, dateOfBirth, organisationName, userId, pollByOrganisation);
        final String responseEntity = response.getPayload();
        assertThat(responseEntity, containsString("instructingOrganisations"));
        assertThat(responseEntity, containsString(organisationId.toString()));

    }

    public String recordInstructionDetails(final String urn, final String firstName, final String lastName, final String dob, final String defenceClientIdStringValue, final UUID userId, final UUID organisationId, final String organisationName, final boolean pollByOrganisation) {
        postInstructionByUserForDefenceClient(fromString(defenceClientIdStringValue), userId.toString());
        verifyDefenceClientWithInstruction(urn, firstName, lastName, dob, organisationName, userId, organisationId, pollByOrganisation);
        return defenceClientIdStringValue;
    }

    public void publishIdpcEvent(final String caseId, final String defenceClientIdString, final String defenceDefendantId, final String urn, final String materialId, final UUID userId, final String name) {
        final String idpcPublishedData = getPayloadForCreatingRequest("stub-data/public-events/public.progression.defendant-idpc-added.json")
                .replace("CASE-ID", caseId)
                .replace("DEFENDANT_ID", defenceClientIdString)
                .replace("MATERIAL_ID", materialId);

        postMessageToTopic(idpcPublishedData, "public.progression.idpc-document-received");
        pollAndVerifyIdpcMetadata(defenceDefendantId, urn, userId, name);

    }

    public void pollAndVerifyIdpcMetadata(final String defenceDefendantId, final String urn, final UUID userId, final String name) {
        final ResponseData response = pollIDPCMetadata(defenceDefendantId, userId);
        final String content = response.getPayload();
        with(content).assertThat("idpcMetadata.documentName", is(format("%s %s IDPC", name, urn)));
        with(content).assertThat("idpcMetadata.size", is("34 kb"));
        with(content).assertThat("idpcMetadata.pageCount", is(3));
    }

    public void publishIdpcEventBeforeCaseReceived(final String caseId, final String defenceClientIdString, final String materialId) {
        final String idpcPublishedData = getPayloadForCreatingRequest("stub-data/public-events/public.progression.defendant-idpc-added.json")
                .replace("CASE-ID", caseId)
                .replace("DEFENDANT_ID", defenceClientIdString)
                .replace("MATERIAL_ID", materialId);

        postMessageToTopicAndVerify(idpcPublishedData, DEFENCE_IDPC_RECEIVED_BEFORE_CASE, "public.progression.idpc-document-received");

    }

    public String getGeneratedDefendantId(final String urn, final String firstName, final String lastName, final String dob, final String organisationName, final UUID userId, final boolean pollByOrganisation) {

        final JsonObject jsonResponse = new StringToJsonObjectConverter().convert(getDefenceClient(urn, firstName, lastName, dob, organisationName, userId, pollByOrganisation).getPayload());
        return jsonResponse.getString("defenceClientId");
    }

    public String addDefendant(final String urn, final String defendantAddedData, final String firstName, final String lastName, final String dateOfBirth, final String organisationName, final UUID userId, final UUID organisationId, final boolean pollByOrganisation) {
        postMessageToTopic(defendantAddedData, PUBLIC_PROGRESSION_DEFENDANTS_ADDED);
        return getGeneratedDefendantId(urn, firstName, lastName, dateOfBirth, organisationName, userId, pollByOrganisation);
    }

    public void verifyDefenceClient(final UUID caseId, final String urn, final String firstName, final String lastName, final String dob, final String defendantId,
                                    final UUID userId, final String organisationName, final boolean pollByOrganisation) {
        final String responseEntity = getDefenceClient(urn, firstName, lastName, dob, organisationName, userId, pollByOrganisation).getPayload();
        assertThat(responseEntity, containsString(defendantId));
        assertThat(responseEntity, containsString(caseId.toString()));
    }

    public ResponseData getDefenceClient(final String urn, final String firstName, final String lastName, final String dob, final String organisationName, final UUID userId, final boolean pollByOrganisation) {
        final ResponseData response;
        if (pollByOrganisation) {
            response = pollDefenceClient(urn, organisationName, userId);
        } else {
            response = pollDefenceClient(urn, firstName, lastName, dob, userId);
        }
        return response;

    }
}
