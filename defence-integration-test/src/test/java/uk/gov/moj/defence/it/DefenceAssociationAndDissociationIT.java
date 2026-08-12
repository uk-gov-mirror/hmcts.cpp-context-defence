package uk.gov.moj.defence.it;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withoutJsonPath;
import static java.lang.String.format;
import static java.time.LocalDate.parse;
import static java.time.Period.ofYears;
import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static java.util.regex.Pattern.compile;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClientBuilder.aMessageConsumerClient;
import static uk.gov.justice.services.test.utils.core.random.DateGenerator.Direction.FUTURE;
import static uk.gov.moj.defence.domain.common.UrnRegex.URN_PATTERN;
import static uk.gov.moj.defence.helper.CreateProsecutionCaseHelper.PROSECUTING_AUTHORITY_TFL;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.associateAdvocateAssignCase;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.associateOrganisation;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.invokeDisassociateOrganisation;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.verifyDefenceAssociationUsingGetAllAssociationsEndpoint;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.verifyDefenceOrganisationAssociatedDataPersisted;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.verifyDefenceOrganisationDissociatedDataPersisted;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.verifyProsecutionOrganizationAndAdvocateAccessDataPersisted;
import static uk.gov.moj.defence.stub.ProgressionServiceStub.stubProgressionService;
import static uk.gov.moj.defence.stub.ReferenceDataStub.stubQueryProsecutorData;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControl;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControlForAllUsers;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControlForLoggedInUser;
import static uk.gov.moj.defence.util.ProsecutionCaseQueryStub.stubForProsecutionCaseQuery;
import static uk.gov.moj.defence.util.ReferenceDataOffencesQueryStub.stubForReferenceDataQueryOffence;
import static uk.gov.moj.defence.util.RestHelper.pollForResponse;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientForLockedByRepOrder;
import static uk.gov.moj.defence.util.RestQueryUtil.pollIndividualDefenceClient;
import static uk.gov.moj.defence.util.RestQueryUtil.queryDefenceCase;
import static uk.gov.moj.defence.util.RestQueryUtil.queryForAdvocateViewAndVerifyFailure;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetails;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetailsForUser;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationNamesForIds;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationQuery;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetUserDetailsWithEmail;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetUsersAndGroupsQueryForDefenceUsers;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetUsersAndGroupsQueryForHMCTSUsers;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetUsersAndGroupsQueryForSystemUsers;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUserPermissions;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsPermission;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsSearchUsersForUserId;
import static uk.gov.moj.defence.util.WiremockHelper.resetWiremock;

import uk.gov.justice.json.generator.value.string.RegexGenerator;
import uk.gov.justice.json.generator.value.string.SimpleStringGenerator;
import uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClient;
import uk.gov.justice.services.test.utils.core.random.LocalDateGenerator;
import uk.gov.moj.defence.helper.CreateProsecutionCaseHelper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.ReadContext;
import org.apache.http.HttpStatus;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefenceAssociationAndDissociationIT {

    private static final String ORGANISATION_ID = randomUUID().toString();
    private static final String NEW_ORGANISATION_ID = randomUUID().toString();
    private static final String ORGANISATION_NAME = new SimpleStringGenerator(5, 15).next();
    private static final String NEW_ORGANISATION_NAME = new SimpleStringGenerator(5, 15).next();
    private static final RegexGenerator REGEX_GENERATOR = new RegexGenerator(compile(URN_PATTERN));

    private static final String PUBLIC_DEFENCE_ASSOCIATION = "public.defence.defence-organisation-associated";
    private static final String PUBLIC_DEFENCE_DISASSOCIATION = "public.defence.defence-organisation-disassociated";
    private static final String PUBLIC_DEFENCE_DISASSOCIATION_FAILED = "public.defence.defence-organisation-disassociation-failed";

    private static final String PUBLIC_EVENT_TOPIC = "jms.topic.public.event";

    private final CreateProsecutionCaseHelper createProsecutionCaseHelper = new CreateProsecutionCaseHelper();
    private UUID caseId;
    private String urn;
    private String firstName;
    private String lastName;
    private String defendantId;
    private String dateOfBirth;
    private static final UUID USER_ID = randomUUID();

    @BeforeAll
    public static void setupClass() {
        resetWiremock();

        stubQueryProsecutorData(true, true);
        stubForReferenceDataQueryOffence();
        stubAccessControlForAllUsers(true, USER_ID, "Defence Lawyers", "System Users");
        stubUsersGroupsPermission();
        stubGetOrganisationDetailsForUser(USER_ID, UUID.fromString(ORGANISATION_ID));
        stubGetUsersAndGroupsQueryForDefenceUsers(USER_ID.toString());
        stubGetOrganisationNamesForIds(USER_ID, asList(UUID.fromString(ORGANISATION_ID), UUID.fromString(NEW_ORGANISATION_ID)));
        stubGetOrganisationDetails(ORGANISATION_ID, ORGANISATION_NAME);
        stubGetOrganisationDetails(NEW_ORGANISATION_ID, NEW_ORGANISATION_NAME);
        stubForProsecutionCaseQuery();
        stubUserPermissions();
        stubProgressionService();

    }

    @BeforeEach
    public void setupTests() {
        caseId = randomUUID();
        urn = REGEX_GENERATOR.next();
        firstName = new SimpleStringGenerator(5, 15).next();
        lastName = new SimpleStringGenerator(5, 15).next();
        dateOfBirth = new LocalDateGenerator(ofYears(10), parse("1983-04-20"), FUTURE).next().toString();
        defendantId = randomUUID().toString();
    }

    @Test
    public void shouldPerformAssociationAndDissociationForDefenceUser() throws Exception {
        stubAccessControlForLoggedInUser(true, USER_ID, "TFL", "CPS");

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, USER_ID);
        pollIndividualDefenceClient(urn, firstName, lastName, dateOfBirth, USER_ID, payload().isJson(allOf(
                withJsonPath("$.caseId", is(caseId.toString())),
                withJsonPath("$.defendantId", is(defendantId)),
                withoutJsonPath("$.associatedOrganisation.organisationId")
        )));

        //Perform association to defence user and verify
        try (final MessageConsumerClient defenceAssociation = aMessageConsumerClient().build()) {
            defenceAssociation.startConsumer(PUBLIC_DEFENCE_ASSOCIATION, PUBLIC_EVENT_TOPIC);

            associateOrganisation(defendantId, USER_ID.toString());

            final Optional<String> defenceAssociationRet = defenceAssociation.retrieveMessage();
            assertThat(defenceAssociationRet.isPresent(), is(true));

            verifyDefenceOrganisationAssociatedDataPersisted(defendantId, ORGANISATION_ID, USER_ID.toString());
        }


        pollIndividualDefenceClient(urn, firstName, lastName, dateOfBirth, USER_ID, payload().isJson(allOf(
                withJsonPath("$.caseId", is(caseId.toString())),
                withJsonPath("$.defendantId", is(defendantId)),
                withJsonPath("$.associatedOrganisation.organisationId", is(ORGANISATION_ID)),
                withJsonPath("$.prosecutionAuthorityCode", is(PROSECUTING_AUTHORITY_TFL))
        )));

        userBelongingToAnotherOrganisationShouldNotBeAbleToViewTheCase();

        stubGetOrganisationDetailsForUser(USER_ID, UUID.fromString(ORGANISATION_ID));
        queryAndVerifyUserRole();
        queryAndVerifyUserRoleByCaseId();
        verifyDefenceOrganisationAssociatedQueriedByHMCTSUser(defendantId, ORGANISATION_ID);
        queryForAdvocateViewAndVerifyFailure(urn, USER_ID);

        // dissociate the defence user and verify
        try (final MessageConsumerClient defenceDisassociation = aMessageConsumerClient().build()) {
            defenceDisassociation.startConsumer(PUBLIC_DEFENCE_DISASSOCIATION, PUBLIC_EVENT_TOPIC);

            try (Response response = invokeDisassociateOrganisation(defendantId, USER_ID.toString(), ORGANISATION_ID, caseId.toString())) {
                assertThat(response.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));
            }

            final Optional<String> defenceDisassociationRet = defenceDisassociation.retrieveMessage();
            assertTrue(defenceDisassociationRet.isPresent());

            //Then
            verifyDefenceOrganisationDissociatedDataPersisted(defendantId, USER_ID.toString());
        }

        // duplicate dissociation should raise failed event
        try (final MessageConsumerClient defenceSecondDisassociation = aMessageConsumerClient().build()) {
            defenceSecondDisassociation.startConsumer(PUBLIC_DEFENCE_DISASSOCIATION_FAILED, PUBLIC_EVENT_TOPIC);

            try (Response secondResponse = invokeDisassociateOrganisation(defendantId, USER_ID.toString(), ORGANISATION_ID, caseId.toString())) {
                assertThat(secondResponse.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));
            }

            final Optional<String> defenceDisassociationRetSecond = defenceSecondDisassociation.retrieveMessage();
            assertTrue(defenceDisassociationRetSecond.isPresent());
        }

        shouldAllowHMCTSAndSystemUserToDissociateOrganisation();
    }

    @Test
    public void shouldPerformAssociationAndDisassociationForLAA() {

        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, USER_ID);

        createProsecutionCaseHelper.createDefenceAssociationForLAA(defendantId, ORGANISATION_ID);
        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, ORGANISATION_ID, USER_ID.toString());
        verifyDefenceAssociationUsingGetAllAssociationsEndpoint(defendantId, ORGANISATION_ID, USER_ID.toString());

        // should be able to replace LAA org with another
        createProsecutionCaseHelper.createDefenceAssociationWithOrganisationIdForLAA(defendantId, NEW_ORGANISATION_ID);
        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, NEW_ORGANISATION_ID, USER_ID.toString());

        createProsecutionCaseHelper.createDefenceAssociationForLAA(defendantId, ORGANISATION_ID);
        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, ORGANISATION_ID, USER_ID.toString());

        stubGetOrganisationDetailsForUser(USER_ID, UUID.fromString(ORGANISATION_ID));
        stubGetUsersAndGroupsQueryForDefenceUsers(USER_ID.toString());

        createProsecutionCaseHelper.createDefenceDissociationForLAA(caseId.toString(), defendantId, ORGANISATION_ID);
        verifyDefenceOrganisationDissociatedDataPersisted(defendantId, USER_ID.toString());
    }

    @Test
    public void shouldPerformFailedAssociationAfterCaseLockedByLAAContractAssociated() {
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, USER_ID);
        createProsecutionCaseHelper.createLAAContractAssociationLock(defendantId);
        pollDefenceClientForLockedByRepOrder(urn, firstName, lastName, dateOfBirth, USER_ID);
    }

    @Test
    public void shouldReturnCaseIdWhenProsecutingAuthIsPolice() {
        //GIVEN
        stubAccessControlForLoggedInUser(true, USER_ID, "TFL", "CPS");
        stubQueryProsecutorData(true, false);
        stubGetOrganisationDetailsForUser(USER_ID, UUID.fromString(ORGANISATION_ID));
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, USER_ID);

        //When
        queryDefenceCase(format("/case/%s", urn), USER_ID, List.of(
                withJsonPath("$.caseId", is(caseId.toString()))
        ));

    }

    @Test
    public void shouldReturnCaseIdWhenProsecutingAuthIsCpsAndFireCommandSuccessEvent() throws IOException {
        //GIVEN
        stubAccessControlForLoggedInUser(true, USER_ID, "TFL", "Defence Lawyers");
        stubQueryProsecutorData(false, true);
        stubGetOrganisationDetailsForUser(USER_ID, UUID.fromString(ORGANISATION_ID));
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, USER_ID);

        queryDefenceCase(format("/case/%s", urn), USER_ID, List.of(
                withJsonPath("$.caseId", is(caseId.toString()))
        ));

        //GIVEN
        UUID assigneeUserId = randomUUID();
        stubAccessControl(true, assigneeUserId, "CPS", "Advocates");
        stubGetUserDetailsWithEmail(assigneeUserId.toString(), "eligible-mail-notassociated-admin@hmcts.net", "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForUserId(assigneeUserId.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");

        //WHEN
        associateAdvocateAssignCase(assigneeUserId.toString(), this.caseId.toString());

        //THEN
        verifyProsecutionOrganizationAndAdvocateAccessDataPersisted(this.caseId.toString(), ORGANISATION_ID, assigneeUserId.toString());
    }

    @Test
    public void shouldNotReturnCaseIdWhenProsecutingAuthIsNotCpsOrPolice() {
        //GIVEN
        stubQueryProsecutorData(false, false);
        stubGetOrganisationDetailsForUser(USER_ID, UUID.fromString(ORGANISATION_ID));
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, USER_ID);

        queryDefenceCase(format("/case/%s", urn), USER_ID, List.of(
                withJsonPath("$.error.errorCode", is("ORGANISATION_NOT_PROSECUTING_AUTHORITY"))
        ));

    }

    private void queryAndVerifyUserRole() {
        List<Matcher<? super ReadContext>> matchers = List.of(
                withJsonPath("$.isAdvocateDefendingOrProsecuting", is("defending")));

        pollForResponse(format("/case/%s", urn),
                "application/vnd.advocate.query.role-in-case+json",
                USER_ID.toString(),
                matchers);
    }

    private void queryAndVerifyUserRoleByCaseId() {
        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.isAdvocateDefendingOrProsecuting", is("defending")))
                .build();

        pollForResponse(format("/cases/%s", caseId),
                "application/vnd.advocate.query.role-in-case-by-caseid+json",
                USER_ID.toString(),
                matchers);

    }

    private void verifyDefenceOrganisationAssociatedQueriedByHMCTSUser(final String defendantId,
                                                                       final String organisationId) {
        final String hmctsOrganisationName = new SimpleStringGenerator(5, 25).next();
        final String hmctsUserId = randomUUID().toString();
        stubGetUsersAndGroupsQueryForHMCTSUsers(hmctsUserId);
        stubGetOrganisationQuery(hmctsUserId, organisationId, hmctsOrganisationName);

        //Then making sure that the Association can be Queried using a HMCTS User.....
        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, organisationId, hmctsUserId);
    }

    private void userBelongingToAnotherOrganisationShouldNotBeAbleToViewTheCase() {
        final UUID userBelongingToAnotherOrg = randomUUID();
        final UUID anotherOrgId = randomUUID();

        stubGetOrganisationDetailsForUser(userBelongingToAnotherOrg, anotherOrgId);
        stubGetUsersAndGroupsQueryForDefenceUsers(userBelongingToAnotherOrg.toString());

        pollIndividualDefenceClient(urn, firstName, lastName, dateOfBirth, userBelongingToAnotherOrg,
                payload().isJson(allOf(
                        withJsonPath("$.error", is(format("%s already associated", ORGANISATION_NAME)))
                )),
                FORBIDDEN);
    }

    private void shouldAllowHMCTSAndSystemUserToDissociateOrganisation() throws Exception {
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, USER_ID);

        //Associate organisation
        associateOrganisation(defendantId, USER_ID.toString());
        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, ORGANISATION_ID, USER_ID.toString());

        // dissociate as HMCTS user

        final String hmctsUserId = randomUUID().toString();
        stubGetUsersAndGroupsQueryForHMCTSUsers(hmctsUserId);
        stubGetOrganisationQuery(hmctsUserId, ORGANISATION_ID, ORGANISATION_NAME);

        final Response response = invokeDisassociateOrganisation(defendantId, hmctsUserId, ORGANISATION_ID, caseId.toString());
        assertThat(response.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));
        verifyDefenceOrganisationDissociatedDataPersisted(defendantId, hmctsUserId);

        //Associate organisation again
        associateOrganisation(defendantId, USER_ID.toString());
        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, ORGANISATION_ID, USER_ID.toString());

        // dissociate as system user

        final String systemUserId = randomUUID().toString();
        stubGetUsersAndGroupsQueryForSystemUsers(systemUserId);
        stubGetOrganisationQuery(systemUserId, ORGANISATION_ID, ORGANISATION_NAME);

        final Response secondResponse = invokeDisassociateOrganisation(defendantId, systemUserId, ORGANISATION_ID, caseId.toString());
        assertThat(secondResponse.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));
        verifyDefenceOrganisationDissociatedDataPersisted(defendantId, systemUserId);

    }
}
