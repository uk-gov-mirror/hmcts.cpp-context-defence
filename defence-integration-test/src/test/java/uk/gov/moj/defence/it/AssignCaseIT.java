package uk.gov.moj.defence.it;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;
import static org.skyscreamer.jsonassert.JSONCompareMode.LENIENT;
import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;
import static uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClient.TIMEOUT_IN_MILLIS;
import static uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClientBuilder.aMessageConsumerClient;
import static uk.gov.moj.defence.helper.AdvocateAccessHelper.verifyUserAssignmentToTheCase;
import static uk.gov.moj.defence.helper.CaseAssignmentHelper.assignCaseToAdvocate;
import static uk.gov.moj.defence.helper.CaseAssignmentHelper.removeCaseAssignmentForAdvocate;
import static uk.gov.moj.defence.stub.HearingServiceStub.stubHearingServiceForApplicationTimeline;
import static uk.gov.moj.defence.stub.HearingServiceStub.stubHearingServiceForCaseTimeline;
import static uk.gov.moj.defence.stub.ListingServiceStub.stubListingService;
import static uk.gov.moj.defence.stub.ProgressionServiceStub.stubProgressionService;
import static uk.gov.moj.defence.stub.ReferenceDataStub.stubQueryProsecutorData;
import static uk.gov.moj.defence.stub.ReferenceDataStub.stubQueryProsecutorDataForNonCps;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControl;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControlForAllUsers;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControlNonCps;
import static uk.gov.moj.defence.util.HttpHeaders.createHttpHeaders;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetails;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetailsForUser;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationQuery;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsSearchUsersForEmail;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsSearchUsersForUserId;
import static uk.gov.moj.defence.util.WiremockHelper.resetWiremock;

import uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClient;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.ReadContext;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

public class AssignCaseIT {

    public static final String ADVOCATES_ROLE = "Advocates";
    public static final String CHAMBERS_ADMIN_ROLE = "Chambers Admin";
    public static final String DEFENCE_LAWYERS_ROLE = "Defence Lawyers";
    public static final String SMITH_ASSOCIATES_LTD = "Smith Associates Ltd.";
    public static final String ORGANISATION_NAME_1 = "organisationName1";
    public static final String PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR = "public.defence.event.case-assigned-to-prosecutor";
    public static final String ASSIGNEE_EMAIL_ID = "assigneeEmailId";
    public static final String ASSIGNOR_ID = "assignorId";
    public static final String ASSIGNOR_ORGANISATION_ID = "assignorOrganisationId";
    public static final String CASE_IDS = "caseIds";
    public static final String ASSIGNMENT_TIMESTAMP = "assignmentTimestamp";
    public static final String PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENT_TO_PROSECUTOR_REMOVED = "public.defence.event.case-assignment-to-prosecutor-removed";
    public static final String PUBLIC_EVENT = "jms.topic.public.event";
    public static final String CASE_ID_FIELD = "caseId";
    public static final String START_DATE = "START_DATE";
    public static final String APPLICATION_VND_DEFENCE_QUERY_HEARINGS_TIMELINE_JSON = "application/vnd.defence.query.hearings-timeline+json";
    public static final String ASSIGNEE_ID = "ASSIGNEE_ID";
    public static final String ASSIGNOR_ID_FIELD = "ASSIGNOR_ID";
    public static final String CASE_ID_LABEL = "CASE_ID";
    public static final String ORG_ID = "ORG_ID";
    public static final String ASSIGNEE_USER_ID = "assigneeUserId";
    private UUID organisationId = randomUUID();
    private static final String assigneeAdvocateOneEmailId = "assigneeAdvocate@hmcts.net";
    private static final String assigneeAdvocateTwoEmailId = "assigneeAdvocate2@hmcts.net";
    private UUID assignerUserId = randomUUID();
    private UUID assigneeAdvocateOneUserId = randomUUID();
    private final UUID assigneeAdvocateTwoUserId = randomUUID();
    private UUID assigneeDefenceLawyerUserId = randomUUID();
    private final UUID assigneeDefenceLawyerTwoUserId = randomUUID();
    private static final String assigneeDefenceLawyerEmailId = "assigneeDefenceLawyer@hmcts.net";
    private static final String assigneeDefenceLawyerTwoEmailId = "assigneeDefenceLawyer2@hmcts.net";
    private UUID CASE_ID = randomUUID();
    private static final UUID userId = randomUUID();

    @BeforeEach
    public void setup() {
        organisationId = randomUUID();
        assignerUserId = randomUUID();
        assigneeAdvocateOneUserId = randomUUID();
        assigneeDefenceLawyerUserId = randomUUID();
        CASE_ID = randomUUID();

        resetWiremock();
        stubProgressionService(CASE_ID);
        stubQueryProsecutorData(true, true);
        //assignerGroupList mocks
        stubAccessControlForAllUsers(true, assignerUserId, "CPS");
        //assigneeGroupList mocks
        stubAccessControl(true, assigneeAdvocateOneUserId, ADVOCATES_ROLE, CHAMBERS_ADMIN_ROLE);
        stubAccessControl(true, assigneeAdvocateTwoUserId, ADVOCATES_ROLE, CHAMBERS_ADMIN_ROLE);
        stubAccessControl(true, assigneeDefenceLawyerUserId, DEFENCE_LAWYERS_ROLE, CHAMBERS_ADMIN_ROLE);
        stubAccessControl(true, assigneeDefenceLawyerTwoUserId, DEFENCE_LAWYERS_ROLE, CHAMBERS_ADMIN_ROLE);
        //assigneeDetails mocks
        stubUsersGroupsSearchUsersForEmail(assigneeAdvocateOneUserId.toString(), assigneeAdvocateOneEmailId, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(assigneeAdvocateTwoUserId.toString(), assigneeAdvocateTwoEmailId, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(assigneeDefenceLawyerUserId.toString(), assigneeDefenceLawyerEmailId, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(assigneeDefenceLawyerTwoUserId.toString(), assigneeDefenceLawyerTwoEmailId, "stub-data/usersgroup-service/user-for-exist-email.json");
        //assignerDetails mock
        stubUsersGroupsSearchUsersForUserId(assignerUserId.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");
        //assignerOrganisation
        stubGetOrganisationQuery(assignerUserId.toString(), organisationId.toString(), SMITH_ASSOCIATES_LTD);
        //assignee Organisation
        stubGetOrganisationDetailsForUser(randomUUID(), organisationId);
        stubGetOrganisationDetails(organisationId.toString(), ORGANISATION_NAME_1);
    }

    @Test
    public void shouldAssignAndRemoveAssignmentToCaseForTheAdvocate() throws Exception {

        final String expectedPayload = getExpectedPayload(assigneeAdvocateOneUserId, "public.defence.event.case-assigned-to-advocate.json");

        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeAdvocateOneEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expectedPayload);
        queryAndVerifyHearingsTimelineWhenAdvocateIsProsecuting();

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees[0].assigneeUserId", IsEqual.equalTo(assigneeAdvocateOneUserId.toString())))
                .add(withJsonPath("$.assignees[0].assigneeOrganisationId", IsEqual.equalTo(organisationId.toString())))
                .build();

        //and
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assigneeAdvocateOneUserId.toString(), matchers);

        //when
        postRemoveCaseAssignmentCommandAndVerifyPublicEvent(CASE_ID.toString(), assigneeAdvocateOneUserId.toString(), organisationId.toString(), assignerUserId.toString());

        List<Matcher<? super ReadContext>> noAssignmentMatchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(0)))
                .build();

        //then
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assigneeAdvocateOneUserId.toString(), noAssignmentMatchers);
    }

    @Test
    public void shouldAssignAndRemoveAssignmentToCaseForNonCpsProsecutors() throws Exception {
        stubAccessControlNonCps(true, userId, "Non CPS Prosecutors");
        stubQueryProsecutorDataForNonCps(false, false);

        final String expectedPayload = getExpectedPayload(assigneeAdvocateOneUserId, "public.defence.event.case-assigned-to-advocate-noncps.json");

        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeAdvocateOneEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expectedPayload);
        queryAndVerifyHearingsTimelineWhenAdvocateIsProsecuting();

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees[0].assigneeUserId", IsEqual.equalTo(assigneeAdvocateOneUserId.toString())))
                .add(withJsonPath("$.assignees[0].assigneeOrganisationId", IsEqual.equalTo(organisationId.toString())))
                .build();

        //and
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assigneeAdvocateOneUserId.toString(), matchers);

        //when
        postRemoveCaseAssignmentCommandAndVerifyPublicEvent(CASE_ID.toString(), assigneeAdvocateOneUserId.toString(), organisationId.toString(), assignerUserId.toString());

        List<Matcher<? super ReadContext>> noAssignmentMatchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(0)))
                .build();

        //then
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assigneeAdvocateOneUserId.toString(), noAssignmentMatchers);
    }

    @Test
    public void shouldAssignAndRemoveAssignmentToCaseForOrganisation() throws Exception {
        final String expectedPayload = getExpectedPayload(assigneeDefenceLawyerUserId, "public.defence.event.case-assigned-to-advocate.json");

        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeDefenceLawyerEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expectedPayload);

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees[0].assigneeUserId", IsEqual.equalTo(assigneeDefenceLawyerUserId.toString())))
                .add(withJsonPath("$.assignees[0].assigneeOrganisationId", IsEqual.equalTo(organisationId.toString())))
                .build();

        //and
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assignerUserId.toString(), matchers);

        //when
        postRemoveCaseAssignmentCommandAndVerifyPublicEvent(CASE_ID.toString(), assigneeDefenceLawyerUserId.toString(), organisationId.toString(), assignerUserId.toString());

        List<Matcher<? super ReadContext>> noAssignmentMatchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(0)))
                .build();

        //then
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assignerUserId.toString(), noAssignmentMatchers);
    }

    @Test
    public void shouldAssignAndRemoveAssignmentToCaseForNonCPSOrganisation() throws Exception {
        stubAccessControlNonCps(true, userId, "Non CPS Prosecutors");
        stubQueryProsecutorDataForNonCps(false, false);
        final String expectedPayload = getExpectedPayload(assigneeDefenceLawyerUserId, "public.defence.event.case-assigned-to-advocate-noncps.json");

        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeDefenceLawyerEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expectedPayload);

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees[0].assigneeUserId", IsEqual.equalTo(assigneeDefenceLawyerUserId.toString())))
                .add(withJsonPath("$.assignees[0].assigneeOrganisationId", IsEqual.equalTo(organisationId.toString())))
                .build();

        //and
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assignerUserId.toString(), matchers);

        //when
        postRemoveCaseAssignmentCommandAndVerifyPublicEvent(CASE_ID.toString(), assigneeDefenceLawyerUserId.toString(), organisationId.toString(), assignerUserId.toString());

        List<Matcher<? super ReadContext>> noAssignmentMatchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(0)))
                .build();

        //then
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assignerUserId.toString(), noAssignmentMatchers);
    }

    @Test
    public void shouldRemoveCaseAssignmentForTheAdvocateWhenMultipleAdvocatesGivenAccessInTheOrganisation() throws Exception {

        //given
        final String expectedPayloadTwo = getExpectedPayload(assigneeAdvocateTwoUserId, "public.defence.event.case-assigned-to-advocate.json");
        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeAdvocateTwoEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expectedPayloadTwo);

        //and
        final String expectedPayloadOne = getExpectedPayload(assigneeAdvocateOneUserId, "public.defence.event.case-assigned-to-advocate.json");
        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeAdvocateOneEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expectedPayloadOne);

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(2)))
                .add(withJsonPath("$.assignees[0].assigneeUserId", IsEqual.equalTo(assigneeAdvocateOneUserId.toString())))
                .add(withJsonPath("$.assignees[0].assigneeOrganisationId", IsEqual.equalTo(organisationId.toString())))
                .add(withJsonPath("$.assignees[1].assigneeUserId", IsEqual.equalTo(assigneeAdvocateTwoUserId.toString())))
                .add(withJsonPath("$.assignees[1].assigneeOrganisationId", IsEqual.equalTo(organisationId.toString())))
                .build();

        //and
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assigneeAdvocateOneUserId.toString(), matchers);

        //when
        postRemoveCaseAssignmentCommandAndVerifyPublicEvent(CASE_ID.toString(), assigneeAdvocateOneUserId.toString(), organisationId.toString(), assignerUserId.toString());

        List<Matcher<? super ReadContext>> noAssignmentMatchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(1)))
                .add(withJsonPath("$.assignees[0].assigneeUserId", IsEqual.equalTo(assigneeAdvocateTwoUserId.toString())))
                .add(withJsonPath("$.assignees[0].assigneeOrganisationId", IsEqual.equalTo(organisationId.toString())))
                .build();

        //then
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assigneeAdvocateOneUserId.toString(), noAssignmentMatchers);
    }

    @Test
    public void shouldAssignMultipleDefenceLawyersFromTheSameOrganisation() throws Exception {

        //given
        final String expectedPayload = getExpectedPayload(assigneeDefenceLawyerUserId, "public.defence.event.case-assigned-to-advocate.json");
        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeDefenceLawyerEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expectedPayload);

        final String expected2Payload = getExpectedPayload(assigneeDefenceLawyerTwoUserId, "public.defence.event.case-assigned-to-advocate.json");
        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeDefenceLawyerTwoEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expected2Payload);

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(1)))
                .build();

        //and
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assignerUserId.toString(), matchers);
    }

    @Test
    public void shouldNotRemoveCaseAssignmentForTheOrganisationWhenOrganisationHasOtherAdvocateAssignments() throws Exception {

        //given
        final String expectedPayload = getExpectedPayload(assigneeDefenceLawyerUserId, "public.defence.event.case-assigned-to-advocate.json");
        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeDefenceLawyerEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expectedPayload);

        final String expectedAdvocatePayload = getExpectedPayload(assigneeAdvocateOneUserId, "public.defence.event.case-assigned-to-advocate.json");
        postAssignCaseCommandAndVerifyPublicEventEvent(assigneeAdvocateOneEmailId, assignerUserId.toString(), PUBLIC_DEFENCE_EVENT_CASE_ASSIGNED_TO_PROSECUTOR, CASE_ID.toString(), expectedAdvocatePayload);

        //and

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(1)))
                .build();

        //and
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assignerUserId.toString(), matchers);

        //when
        postRemoveCaseAssignmentCommand(CASE_ID.toString(), assigneeDefenceLawyerUserId.toString(), assignerUserId.toString());

        List<Matcher<? super ReadContext>> noAssignmentMatchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.assignees.size()", IsEqual.equalTo(1)))
                .build();

        //then
        verifyUserAssignmentToTheCase(CASE_ID.toString(), assignerUserId.toString(), noAssignmentMatchers);
    }

    public void postAssignCaseCommandAndVerifyPublicEventEvent(final String assigneeEmail, final String assignerUserId, final String expectedEventName, final String caseId, final String expectedPayload) throws Exception {
        try (final MessageConsumerClient messageConsumerClient = aMessageConsumerClient().build()) {

            messageConsumerClient.startConsumer(expectedEventName, PUBLIC_EVENT);

            final String payload = createObjectBuilder()
                    .add(ASSIGNEE_EMAIL_ID, assigneeEmail)
                    .add(ASSIGNOR_ID, assignerUserId)
                    .add(ASSIGNOR_ORGANISATION_ID, randomUUID().toString())
                    .add(CASE_IDS, createArrayBuilder().add(caseId))
                    .build()
                    .toString();

            assignCaseToAdvocate(payload, assignerUserId);

            Optional<String> eventPayload = messageConsumerClient.retrieveMessage(TIMEOUT_IN_MILLIS);
            assertThat(expectedEventName + " message is not found in defence.event topic", eventPayload.isPresent(), is(true));
            assertEquals(expectedPayload, eventPayload.get(), new CustomComparator(LENIENT,
                    new Customization(ASSIGNMENT_TIMESTAMP, (o1, o2) -> true)
            ));
        }
    }

    public void postRemoveCaseAssignmentCommandAndVerifyPublicEvent(final String caseId, final String assigneeUserId, final String organisationId, final String assignerUserId) {

        try (final MessageConsumerClient messageConsumerClient = aMessageConsumerClient().build()) {

            messageConsumerClient.startConsumer(PUBLIC_DEFENCE_EVENT_CASE_ASSIGNMENT_TO_PROSECUTOR_REMOVED, PUBLIC_EVENT);

            postRemoveCaseAssignmentCommand(caseId, assigneeUserId, assignerUserId);

            Optional<String> eventPayload = messageConsumerClient.retrieveMessage(TIMEOUT_IN_MILLIS);
            assertThat(eventPayload.isPresent(), is(true));
            assertThat(eventPayload.get(), containsString(caseId));
            assertThat(eventPayload.get(), containsString(organisationId));
        }

    }

    private void postRemoveCaseAssignmentCommand(String caseId, String assigneeUserId, String assignerUserId) {
        final String payload = createObjectBuilder()
                .add(CASE_ID_FIELD, caseId)
                .add(ASSIGNEE_USER_ID, assigneeUserId)
                .build()
                .toString();

        removeCaseAssignmentForAdvocate(payload, assignerUserId);
    }

    private String getExpectedPayload(final UUID assigneeUserId, final String expectedPayloadFileName) throws IOException {
        return readFileToString(new File(this.getClass().getClassLoader()
                .getResource(expectedPayloadFileName).getFile()))
                .replace(ASSIGNEE_ID, assigneeUserId.toString())
                .replace(ASSIGNOR_ID_FIELD, assignerUserId.toString())
                .replace(CASE_ID_LABEL, CASE_ID.toString())
                .replace(ORG_ID, organisationId.toString());
    }

    private void queryAndVerifyHearingsTimelineWhenAdvocateIsProsecuting() throws Exception {
        UUID applicationId = randomUUID();
        stubListingService();
        stubHearingServiceForCaseTimeline(CASE_ID);
        stubHearingServiceForApplicationTimeline(applicationId);
        final String queryString = format("/defence-query-api/query/api/rest/defence/hearings/timeline?caseId=%s&applicationIds=%s&advocateRole=prosecuting", CASE_ID.toString(), applicationId.toString());
        final RestClient restClient = new RestClient();
        Response response = restClient.query(
                getBaseUri() + queryString,
                APPLICATION_VND_DEFENCE_QUERY_HEARINGS_TIMELINE_JSON, createHttpHeaders(assigneeAdvocateOneUserId.toString())
        );
        assertThat(response.getStatus(), is(200));
        String responseEntity = response.readEntity(String.class);
        final String expectedPayload = readFileToString(new File(this.getClass().getClassLoader()
                .getResource("defence.query.hearings-timeline.json").getFile()))
                .replace(START_DATE, LocalDate.now().plusDays(2).toString());
        assertEquals(expectedPayload, responseEntity, false);
    }

}