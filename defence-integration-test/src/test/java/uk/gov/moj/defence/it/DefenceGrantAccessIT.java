package uk.gov.moj.defence.it;

import static java.time.LocalDate.parse;
import static java.time.Period.ofYears;
import static java.util.Arrays.asList;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static java.util.regex.Pattern.compile;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.cps.defence.Permission.permission;
import static uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClientBuilder.aMessageConsumerClient;
import static uk.gov.justice.services.test.utils.core.random.DateGenerator.Direction.FUTURE;
import static uk.gov.moj.cpp.defence.common.util.ActionTypes.VIEW;
import static uk.gov.moj.cpp.defence.common.util.ObjectTypes.DEFENCE_CLIENT;
import static uk.gov.moj.defence.domain.common.UrnRegex.URN_PATTERN;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.associateOrganisation;
import static uk.gov.moj.defence.helper.DefenceAssociationHelper.verifyDefenceOrganisationAssociatedDataPersisted;
import static uk.gov.moj.defence.helper.TopicNames.PUBLIC_EVENT;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControl;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControlForAllUsers;
import static uk.gov.moj.defence.util.ProsecutionCaseQueryStub.stubForProsecutionCaseQuery;
import static uk.gov.moj.defence.util.ReferenceDataOffencesQueryStub.stubForReferenceDataQueryOffence;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClient;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientGrantee;
import static uk.gov.moj.defence.util.RestQueryUtil.pollDefenceClientGranteeForRemove;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetails;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetailsForUser;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationNamesForIds;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationQuery;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetUsersAndGroupsQueryForDefenceUsers;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUserPermissions;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsPermission;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsPermissionsForUser;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsPermissionsQuery;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsSearchUsersForEmail;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsSearchUsersForUserId;
import static uk.gov.moj.defence.util.WiremockHelper.resetWiremock;

import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.json.generator.value.string.RegexGenerator;
import uk.gov.justice.json.generator.value.string.SimpleStringGenerator;
import uk.gov.justice.services.common.json.DefaultJsonParser;
import uk.gov.justice.services.common.json.JsonParser;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.justice.services.test.utils.core.messaging.MessageConsumerClient;
import uk.gov.justice.services.test.utils.core.random.LocalDateGenerator;
import uk.gov.moj.defence.helper.CreateProsecutionCaseHelper;
import uk.gov.moj.defence.helper.DefenceAssociationHelper;
import uk.gov.moj.defence.helper.DefenceGrantAccessHelper;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefenceGrantAccessIT {

    private static final RegexGenerator regexGenerator = new RegexGenerator(compile(URN_PATTERN));
    private static final UUID associatedOrganisationId = randomUUID();
    private static final UUID disAssociatedOrganisationId_A = randomUUID();
    private static final UUID disAssociatedOrganisationId_B = randomUUID();
    private static final String existingEmailWithNonExistingGroup = "mail-with-invalid-group@hmcts.net";
    private static final String existingEmailWithExistingGroupAlreadyGranted = "mail-with-valid-group-granted@hmcts.net";
    private static final String existingEmailEligibleNotAssociatedAndAdmin = "eligible-mail-notassociated-admin@hmcts.net";
    private static final String existingEmailEligibleNotAssociatedAndNotAdmin = "eligible-mail-notassociated-notadmin@hmcts.net";
    private static final String existingEmailEligibleNotAssociatedAndNotAdmin_OrgB = "eligible-mail-notassociated-notadmin-orgB@hmcts.net";
    private static final String nonExistingEmail = "not-exist-mail@hmcts.net";
    private static final UUID granterUserIdAssociatedOrganisation = randomUUID();
    private static final UUID granterUserIdNotAssociatedOrganisationAdmin = randomUUID();
    private static final UUID granterUserIdNotAssociatedOrganisationNotAdmin = randomUUID();
    private static final UUID granteeUserIdWithNonExistingGroup = randomUUID();
    private static final UUID granteeUserIdWithExistingGroupAlreadyGranted = randomUUID();
    private static final UUID granteeUserIdEligibleNotAssociatedAndAdmin = randomUUID();
    private static final UUID granteeUserIdEligibleNotAssociatedAndNotAdmin = randomUUID();
    private static final UUID granteeUserIdEligibleNotAssociatedAndNotAdmin_OrgB = randomUUID();
    private String firstName;
    private String lastName;
    private String dateOfBirth;
    private DefenceGrantAccessHelper defenceGrantAccessHelper = new DefenceGrantAccessHelper();
    private CreateProsecutionCaseHelper createProsecutionCaseHelper = new CreateProsecutionCaseHelper();
    private UUID caseId;
    private String urn;

    @BeforeAll
    public static void setupClass() {
        resetWiremock();
        stubForReferenceDataQueryOffence();

        stubAccessControlForAllUsers(true, granterUserIdAssociatedOrganisation, "System Users");

        //granterGroupList mocks
        stubAccessControl(true, granterUserIdAssociatedOrganisation, "Defence Users", "Chambers Clerk");
        stubAccessControl(true, granterUserIdNotAssociatedOrganisationAdmin, "Defence Users", "Chambers Admin");
        stubAccessControl(true, granterUserIdNotAssociatedOrganisationNotAdmin, "Defence Users", "Defence Lawyers");

        //granteeGroupList mocks
        stubAccessControl(true, granteeUserIdEligibleNotAssociatedAndAdmin, "Defence Users", "Chambers Admin");
        stubAccessControl(true, granteeUserIdEligibleNotAssociatedAndNotAdmin, "Defence Users", "Defence Lawyers");
        stubAccessControl(true, granteeUserIdEligibleNotAssociatedAndNotAdmin_OrgB, "Defence Users", "Defence Lawyers");
        stubAccessControl(true, granteeUserIdWithNonExistingGroup, "Non existing group name");
        stubAccessControl(true, granteeUserIdWithExistingGroupAlreadyGranted, "Chambers Clerk");

        stubGetOrganisationDetailsForUser(granterUserIdAssociatedOrganisation, associatedOrganisationId);
        stubGetOrganisationNamesForIds(granterUserIdAssociatedOrganisation, asList(associatedOrganisationId, disAssociatedOrganisationId_A));

        //mock for association
        final String organisationName = "Smith Associates Ltd.";
        stubGetUsersAndGroupsQueryForDefenceUsers(granterUserIdAssociatedOrganisation.toString());
        stubGetOrganisationDetails(associatedOrganisationId.toString(), organisationName);

        //granteeDetails mocks
        stubUsersGroupsSearchUsersForEmail(null, nonExistingEmail, "stub-data/usersgroup-service/user-for-non-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(granteeUserIdWithNonExistingGroup.toString(), existingEmailWithNonExistingGroup, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(granteeUserIdWithExistingGroupAlreadyGranted.toString(), existingEmailWithExistingGroupAlreadyGranted, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(granteeUserIdEligibleNotAssociatedAndAdmin.toString(), existingEmailEligibleNotAssociatedAndAdmin, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(granteeUserIdEligibleNotAssociatedAndNotAdmin.toString(), existingEmailEligibleNotAssociatedAndNotAdmin, "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForEmail(granteeUserIdEligibleNotAssociatedAndNotAdmin_OrgB.toString(), existingEmailEligibleNotAssociatedAndNotAdmin_OrgB, "stub-data/usersgroup-service/user-for-exist-email.json");

        //granterDetails mock
        stubUsersGroupsSearchUsersForUserId(granterUserIdAssociatedOrganisation.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForUserId(granterUserIdNotAssociatedOrganisationAdmin.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForUserId(granterUserIdNotAssociatedOrganisationNotAdmin.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");

        stubUsersGroupsSearchUsersForUserId(granteeUserIdEligibleNotAssociatedAndAdmin.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForUserId(granteeUserIdEligibleNotAssociatedAndNotAdmin.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");
        stubUsersGroupsSearchUsersForUserId(granteeUserIdEligibleNotAssociatedAndNotAdmin_OrgB.toString(), "stub-data/usersgroup-service/user-for-exist-email.json");


        //granterOrganisation
        stubGetOrganisationQuery(granterUserIdAssociatedOrganisation.toString(), associatedOrganisationId.toString(), organisationName);
        stubGetOrganisationQuery(granterUserIdNotAssociatedOrganisationAdmin.toString(), disAssociatedOrganisationId_A.toString(), organisationName);
        stubGetOrganisationQuery(granterUserIdNotAssociatedOrganisationNotAdmin.toString(), disAssociatedOrganisationId_A.toString(), organisationName);

        //granteeOrganisation
        stubGetOrganisationQuery(granteeUserIdEligibleNotAssociatedAndAdmin.toString(), disAssociatedOrganisationId_A.toString(), organisationName);
        stubGetOrganisationQuery(granteeUserIdEligibleNotAssociatedAndNotAdmin.toString(), disAssociatedOrganisationId_A.toString(), organisationName);
        stubGetOrganisationQuery(granteeUserIdWithNonExistingGroup.toString(), disAssociatedOrganisationId_A.toString(), organisationName);
        stubGetOrganisationQuery(granteeUserIdEligibleNotAssociatedAndNotAdmin_OrgB.toString(), disAssociatedOrganisationId_B.toString(), organisationName);


        stubUsersGroupsPermissionsForUser(granteeUserIdEligibleNotAssociatedAndAdmin.toString(), getPermission(granteeUserIdEligibleNotAssociatedAndAdmin, UUID.randomUUID()), "stub-data/usersgroup-service/user-permission.json");
        stubUsersGroupsPermission();
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
    public void testGrantAccessFail_AssociatedUser_GrantsANotAdminFromOtherOrganisation_NotAdminGrantsSomeoneInTheSameChamber_ShouldRaise_GrantAccessFailedEvent() throws Exception {

        final String defendantId = randomUUID().toString();
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndNotAdmin, granterUserIdAssociatedOrganisation.toString(), "public.defence.event.defence-access-granted");

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndNotAdmin, granteeUserIdEligibleNotAssociatedAndNotAdmin.toString(), "public.defence.events.grant-access-failed");

    }


    @Test
    public void testGrantAccessFail_AssociatedUser_GrantsAnAdminFromOtherOrganisation_AdminCanNotGrantsSomeoneInOtherChamber_ShouldRaise_GrantAccessFailedEvent() throws Exception {

        final String defendantId = randomUUID().toString();
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndAdmin, granterUserIdAssociatedOrganisation.toString(), "public.defence.event.defence-access-granted");

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndNotAdmin_OrgB, granteeUserIdEligibleNotAssociatedAndAdmin.toString(), "public.defence.events.grant-access-failed");

    }


    @Test
    public void testGrantAccessSuccess_AssociatedUser_GrantsAnAdminFromOtherOrganisation_AdminGrantsSomeoneInTheSameChamber_AndRemoveGrantAccess_ShouldRaise_AccessGrantedEvent() throws Exception {

        final String defendantId = randomUUID().toString();
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndAdmin, granterUserIdAssociatedOrganisation.toString(), "public.defence.event.defence-access-granted");

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndNotAdmin, granteeUserIdEligibleNotAssociatedAndAdmin.toString(), "public.defence.event.defence-access-granted");

        pollDefenceClientGranteeForRemove(defenceClientId, granterUserIdAssociatedOrganisation, 2);

        defenceGrantAccessHelper.postRemoveGrantDefenceAccessAndVerifyPublicEventEvent(UUID.fromString(defenceClientId), granteeUserIdEligibleNotAssociatedAndNotAdmin, granteeUserIdEligibleNotAssociatedAndAdmin, "public.defence.event.defence-access-grant-removed");

        pollDefenceClientGranteeForRemove(defenceClientId, granterUserIdAssociatedOrganisation, 1);

    }

    @Test
    public void testGrantAccessSuccess_AssociatedUser_GrantsAnAdminFromOtherOrganisation_AdminGrantsSomeoneInTheSameChamber_ShouldAccessDefence() throws Exception {
        final String defendantId = randomUUID().toString();
        stubUsersGroupsPermissionsQuery(granteeUserIdEligibleNotAssociatedAndAdmin.toString(), getPermission(granteeUserIdEligibleNotAssociatedAndAdmin, fromString(defendantId)), "stub-data/usersgroup-service/user-permission.json");
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndAdmin, granterUserIdAssociatedOrganisation.toString(), "public.defence.event.defence-access-granted");

        final ResponseData response = pollDefenceClient(urn, firstName, lastName, dateOfBirth, granteeUserIdEligibleNotAssociatedAndAdmin);
        final String responseEntity = response.getPayload();
        assertThat(responseEntity, containsString(defendantId));
        assertThat(responseEntity, containsString(caseId.toString()));
    }


    @Test
    public void testGrantAccessFail_GranterNotAssociated_GranterNotInAllowedGroups_ShouldRaise_GrantAccessFailedEvent() throws Exception {

        final String defendantId = randomUUID().toString();
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndAdmin, granterUserIdNotAssociatedOrganisationNotAdmin.toString(), "public.defence.events.grant-access-failed");
    }

    @Test
    public void testGrantAccessSuccess_GranterNotAssociated_GranterInAllowedGroups_And_NotAdminCanNotRemoveGrant_ShouldRaise_AccessGrantedEvent() throws Exception {

        final String defendantId = randomUUID().toString();
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndAdmin, granterUserIdNotAssociatedOrganisationAdmin.toString(), "public.defence.event.defence-access-granted");

        pollDefenceClientGranteeForRemove(defenceClientId, granterUserIdAssociatedOrganisation, 1);

        defenceGrantAccessHelper.postRemoveGrantDefenceAccessAndVerifyPublicEventEvent(UUID.fromString(defenceClientId), granteeUserIdEligibleNotAssociatedAndAdmin, granteeUserIdEligibleNotAssociatedAndNotAdmin, "public.defence.events.grant-access-failed");

        pollDefenceClientGranteeForRemove(defenceClientId, granterUserIdAssociatedOrganisation, 1);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(UUID.fromString(defenceClientId), existingEmailEligibleNotAssociatedAndNotAdmin, granteeUserIdEligibleNotAssociatedAndAdmin.toString(), "public.defence.event.defence-access-granted");

        pollDefenceClientGranteeForRemove(defenceClientId, granterUserIdAssociatedOrganisation, 2);


        try (final MessageConsumerClient defenceDisassociation = aMessageConsumerClient().build()) {

            defenceDisassociation.startConsumer("public.defence.defence-organisation-disassociated", PUBLIC_EVENT);

            final Response response = DefenceAssociationHelper.invokeDisassociateOrganisation(defendantId, granterUserIdAssociatedOrganisation.toString(), associatedOrganisationId.toString(), caseId.toString());

            assertThat(response.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));

            final Optional<String> defenceDisassociationRet = defenceDisassociation.retrieveMessage();

            assertTrue(defenceDisassociationRet.isPresent());

            //Then
            DefenceAssociationHelper.verifyDefenceOrganisationDissociatedDataPersisted(defendantId, granterUserIdAssociatedOrganisation.toString());

        }


        pollDefenceClientGranteeForRemove(defenceClientId, granterUserIdAssociatedOrganisation, 0);

    }


    @Test
    public void testGrantAccessFail_GranteeNotDefinedInUsersGroups_ShouldRaise_UserNotFoundEventEvent() throws Exception {

        final String defendantId = randomUUID().toString();
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), nonExistingEmail, granterUserIdAssociatedOrganisation.toString(), "public.defence.event.user-not-found");
    }

    @Test
    public void testGrantAccessFail_GranterAssociated_GranteeNotInAllowedGroups_ShouldRaise_UserNotInAllowedGroupsEvent() throws Exception {

        final String defendantId = randomUUID().toString();
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);


        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailWithNonExistingGroup, granterUserIdAssociatedOrganisation.toString(), "public.defence.event.grantee-user-not-in-allowed-groups");
    }

    @Test
    public void testGrantAccessSuccess_GranterAssociated_GranteeInAllowedGroups_ShouldRaise_AccessGrantedEvent() throws Exception {

        final String defendantId = randomUUID().toString();
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndAdmin, granterUserIdAssociatedOrganisation.toString(), "public.defence.event.defence-access-granted");

        pollDefenceClientGrantee(defenceClientId, granterUserIdAssociatedOrganisation, granteeUserIdEligibleNotAssociatedAndAdmin);
    }


    @Test
    public void testGrantAccessFail_GranterAssociated_GranteeAlreadyGranted_ShouldRaise_UserAlreadyGrantedEvent() throws Exception {

        final String defendantId = randomUUID().toString();
        final String defenceClientId = createCaseAndVerifyDefenceClientForIndividual(defendantId);

        associateOrganisationAndVerify(defendantId);

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndAdmin, granterUserIdAssociatedOrganisation.toString(), "public.defence.event.defence-access-granted");

        defenceGrantAccessHelper.postGrantDefenceAccessAndVerifyPublicEventEvent(fromString(defenceClientId), existingEmailEligibleNotAssociatedAndAdmin, granterUserIdAssociatedOrganisation.toString(), "public.defence.event.user-already-granted");


    }

    private String verifyDefenceClientForIndividualAndReturnDefenceClientId(final UUID caseId, final String urn, final String defendantId,
                                                                            final String firstName, final String lastName, final String dateOfBirth) {

        final ResponseData response = pollDefenceClient(urn, firstName, lastName, dateOfBirth, granterUserIdAssociatedOrganisation);
        final String responseEntity = response.getPayload();
        assertThat(responseEntity, containsString(defendantId));
        assertThat(responseEntity, containsString(caseId.toString()));
        JsonParser parser = new DefaultJsonParser();
        JsonObject jsonResponse = parser.toObject(responseEntity, JsonObject.class);
        return jsonResponse.getJsonString("defenceClientId").getString();
    }


    private String createCaseAndVerifyDefenceClientForIndividual(final String defendantId) {
        createProsecutionCaseHelper.createAndVerifyProsecutionCaseWithDefendant(caseId, urn, defendantId, firstName, lastName, dateOfBirth, null, null, granterUserIdAssociatedOrganisation);
        return verifyDefenceClientForIndividualAndReturnDefenceClientId(caseId, urn, defendantId, firstName, lastName, dateOfBirth);
    }


    private void associateOrganisationAndVerify(final String defendantId) throws IOException {
        associateOrganisation(defendantId, granterUserIdAssociatedOrganisation.toString());

        verifyDefenceOrganisationAssociatedDataPersisted(defendantId, associatedOrganisationId.toString(), granterUserIdAssociatedOrganisation.toString());
    }

    private static Permission getPermission(final UUID source, final UUID defenceClientId) {
        return permission()
                .withSource(source)
                .withAction(VIEW.getActionName())
                .withObject(DEFENCE_CLIENT.getObjectName())
                .withTarget(defenceClientId)
                .build();
    }


}