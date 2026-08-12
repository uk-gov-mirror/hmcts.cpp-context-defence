package uk.gov.moj.defence.it;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;
import static org.skyscreamer.jsonassert.JSONCompareMode.LENIENT;
import static uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClient.TIMEOUT_IN_MILLIS;
import static uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClientBuilder.aMessageConsumerClient;
import static uk.gov.moj.defence.helper.AdvocateAccessHelper.verifyUserAssignmentToTheCase;
import static uk.gov.moj.defence.helper.CaseAssignmentHelper.removeCaseAssignmentForAdvocate;
import static uk.gov.moj.defence.helper.TopicNames.PUBLIC_EVENT;
import static uk.gov.moj.defence.stub.ProgressionServiceStub.stubProgressionService;
import static uk.gov.moj.defence.stub.ReferenceDataStub.stubQueryProsecutorData;
import static uk.gov.moj.defence.stub.ReferenceDataStub.stubQueryProsecutorDataForNonCps;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControl;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControlForAllUsers;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControlForLoggedInUser;
import static uk.gov.moj.defence.util.RestHelper.postCommand;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetails;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetailsForUser;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationQuery;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsSearchUsersForEmail;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsSearchUsersForUserId;
import static uk.gov.moj.defence.util.WiremockHelper.resetWiremock;

import uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClient;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.ReadContext;
import org.apache.http.HttpStatus;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

public class AssignCaseByHearingIT {

    public static final String DEFENCE_COMMAND_ADVOCATE_ASSIGN_CASE_BY_HEARING_LISTING = "application/vnd.defence.command.advocate.assign-case-by-hearing-listing+json";
    private UUID organisationId = randomUUID();
    private static final String assigneeAdvocateOneEmailId = "assigneeAdvocate@hmcts.net";
    private static final String assigneeAdvocateTwoEmailId = "assigneeAdvocate2@hmcts.net";
    private UUID assignerUserId = randomUUID();
    private UUID nonCpsAssignerUserId;
    private UUID assigneeAdvocateOneUserId = randomUUID();
    private final UUID assigneeAdvocateTwoUserId = randomUUID();
    private UUID assigneeDefenceLawyerUserId = randomUUID();
    private final UUID assigneeDefenceLawyerTwoUserId = randomUUID();
    private static final String assigneeDefenceLawyerEmailId = "assigneeDefenceLawyer@hmcts.net";
    private static final String assigneeInvalidEmailId = "invalidEmail@hmcts.net";
    private static final String assigneeDefenceLawyerTwoEmailId = "assigneeDefenceLawyer2@hmcts.net";
    private UUID caseId = randomUUID();
    private UUID hearingId = randomUUID();

    @BeforeEach
    public void setup() {
        organisationId = randomUUID();
        assignerUserId = randomUUID();
        nonCpsAssignerUserId = randomUUID();
        assigneeAdvocateOneUserId = randomUUID();
        assigneeDefenceLawyerUserId = randomUUID();
        caseId = randomUUID();

        resetWiremock();
        stubProgressionService(caseId);
        stubQueryProsecutorData(true, true);
        //assignerGroupList mocks
        stubAccessControlForAllUsers(true, assignerUserId, "CPS");
        //assigneeGroupList mocks
        stubAccessControl(true, assigneeAdvocateOneUserId, "Advocates", "Chambers Admin");
        stubAccessControl(true, assigneeAdvocateTwoUserId, "Advocates", "Chambers Admin");
        stubAccessControl(true, assigneeDefenceLawyerUserId, "Defence Lawyers", "Chambers Admin");
        stubAccessControl(true, assigneeDefenceLawyerTwoUserId, "Defence Lawyers", "Chambers Admin");
        //assigneeDetails mocks
        stubUsersGroupsSearchUsersForEmail(assigneeAdvocateOneUserId.toString(), assigneeAdvocateOneEmailId, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(assigneeAdvocateTwoUserId.toString(), assigneeAdvocateTwoEmailId, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(assigneeDefenceLawyerUserId.toString(), assigneeDefenceLawyerEmailId, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(assigneeDefenceLawyerTwoUserId.toString(), assigneeDefenceLawyerTwoEmailId, "stub-data/usersgroup-service/user-for-exist-email.json");
        //assignerDetails mock
        stubUsersGroupsSearchUsersForUserId(assignerUserId.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");
        //assignerOrganisation
        stubGetOrganisationQuery(assignerUserId.toString(), organisationId.toString(), "Smith Associates Ltd.");
        //assignee Organisation
        stubGetOrganisationDetailsForUser(randomUUID(), organisationId);
        stubGetOrganisationDetails(organisationId.toString(), "organisationName1");
    }

    @Test
    public void shouldNonCpsUserAssignCaseByHearingListingToAdvocate() throws Exception {
        stubQueryProsecutorDataForNonCps(true, true);
        stubAccessControlForLoggedInUser(true, nonCpsAssignerUserId, "DVLA", "Non CPS Prosecutors");
        stubUsersGroupsSearchUsersForUserId(nonCpsAssignerUserId.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");
        stubGetOrganisationQuery(nonCpsAssignerUserId.toString(), organisationId.toString(), "Bodgit and Scarper LLP");

        final String expectedPayload = getExpectedPayload(assigneeAdvocateOneUserId, nonCpsAssignerUserId, "public.defence.event.case-assigned-to-advocate-noncps-by-hearing.json");
        postAssignCaseByHearingListingCommandAndVerifyPublicEventEvent(assigneeAdvocateOneEmailId, nonCpsAssignerUserId.toString(),
                "public.defence.event.cases-assigned-to-prosecutor", caseId.toString(), hearingId.toString(), expectedPayload);
    }

    @Test
    public void shouldRaiseUserNotFoundWhenInvalidEmailSent() throws Exception {
        final String expectedPayload = getExpectedPayload(assigneeDefenceLawyerUserId, "public.defence.event.case-assignments-failed.json");

        postAssignCaseByHearingListingCommandAndVerifyPublicEventEvent(assigneeInvalidEmailId, assignerUserId.toString(),
                "public.defence.event.case-assignments-failed", caseId.toString(), hearingId.toString(), expectedPayload);
    }

    @Test
    public void shouldAssignAndRemoveCaseAssignmentForTheAdvocate() throws Exception {

        //given
        final String expectedPayload = getExpectedPayload(assigneeAdvocateOneUserId, "public.defence.event.cases-assigned-to-advocate.json");

        //and
        postAssignCaseByHearingListingCommandAndVerifyPublicEventEvent(assigneeAdvocateOneEmailId, assignerUserId.toString(),
                "public.defence.event.cases-assigned-to-prosecutor", caseId.toString(), hearingId.toString(), expectedPayload);

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees[0].assigneeUserId", IsEqual.equalTo(assigneeAdvocateOneUserId.toString())))
                .add(withJsonPath("$.assignees[0].assigneeOrganisationId", IsEqual.equalTo(organisationId.toString())))
                .build();

        //and
        verifyUserAssignmentToTheCase(caseId.toString(), assigneeAdvocateOneUserId.toString(), matchers);

        //when
        postRemoveCaseAssignmentCommandAndVerifyPublicEvent(caseId.toString(), assigneeAdvocateOneUserId.toString(), organisationId.toString(), assignerUserId.toString());

        List<Matcher<? super ReadContext>> noAssignmentMatchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(0)))
                .build();

        //then
        verifyUserAssignmentToTheCase(caseId.toString(), assigneeAdvocateOneUserId.toString(), noAssignmentMatchers);
    }

    public void postAssignCaseByHearingListingCommandAndVerifyPublicEventEvent(final String assigneeEmail, final String assignerUserId,
                                                                               final String expectedEventName, final String caseId, final String hearingId,
                                                                               final String expectedPayload) throws Exception {
        try (final MessageConsumerClient messageConsumerClient = aMessageConsumerClient().build()) {

            messageConsumerClient.startConsumer(expectedEventName, PUBLIC_EVENT);

            final String urlPath = "/advocate";
            final String payload = createObjectBuilder()
                    .add("assigneeEmailId", assigneeEmail)
                    .add("assignorId", assignerUserId)
                    .add("assignorOrganisationId", randomUUID().toString())
                    .add("caseHearings", createArrayBuilder().add(createObjectBuilder()
                            .add("caseId", caseId)
                            .add("hearingId", hearingId)
                            .build()))
                    .build()
                    .toString();

            try (Response response = postCommand(assignerUserId, payload, DEFENCE_COMMAND_ADVOCATE_ASSIGN_CASE_BY_HEARING_LISTING, urlPath)) {
                assertThat(response.getStatus(), is(HttpStatus.SC_ACCEPTED));
            }

            Optional<String> eventPayload = messageConsumerClient.retrieveMessage(TIMEOUT_IN_MILLIS);
            assertThat(expectedEventName + " message is not found in defence.event topic", eventPayload.isPresent(), is(true));
            assertEquals(expectedPayload, eventPayload.get(), new CustomComparator(LENIENT,
                    new Customization("assignmentTimestamp", (o1, o2) -> true)
            ));
        }
    }

    private void postRemoveCaseAssignmentCommandAndVerifyPublicEvent(final String caseId, final String assigneeUserId, final String organisationId, final String assignerUserId) {

        try (final MessageConsumerClient messageConsumerClient = aMessageConsumerClient().build()) {

            messageConsumerClient.startConsumer("public.defence.event.case-assignment-to-prosecutor-removed", PUBLIC_EVENT);

            postRemoveCaseAssignmentCommand(caseId, assigneeUserId, assignerUserId);

            Optional<String> eventPayload = messageConsumerClient.retrieveMessage(TIMEOUT_IN_MILLIS);
            assertThat(eventPayload.isPresent(), is(true));
            assertThat(eventPayload.get(), containsString(caseId));
            assertThat(eventPayload.get(), containsString(organisationId));
        }

    }

    private void postRemoveCaseAssignmentCommand(String caseId, String assigneeUserId, String assignerUserId) {
        final String payload = createObjectBuilder()
                .add("caseId", caseId)
                .add("assigneeUserId", assigneeUserId)
                .build()
                .toString();

        removeCaseAssignmentForAdvocate(payload, assignerUserId);
    }

    private String getExpectedPayload(final UUID assigneeUserId, final String expectedPayloadFileName) throws IOException {
        return getExpectedPayload(assigneeUserId, assignerUserId, expectedPayloadFileName);
    }

    private String getExpectedPayload(final UUID assigneeUserId, final UUID assignerUserId, final String expectedPayloadFileName) throws IOException {
        final File file = new File(this.getClass().getClassLoader().getResource(expectedPayloadFileName).getFile());
        return readFileToString(file)
                .replace("ASSIGNEE_ID", assigneeUserId.toString())
                .replace("ASSIGNOR_ID", assignerUserId.toString())
                .replace("CASE_ID", caseId.toString())
                .replace("HEARING_ID", hearingId.toString())
                .replace("ORG_ID", organisationId.toString());
    }

}