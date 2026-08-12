package uk.gov.moj.cpp.defence.aggregate;

import static java.time.ZonedDateTime.now;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.common.util.DefencePermission.UPLOAD_DOCUMENT_PERMISSION;
import static uk.gov.moj.cpp.defence.common.util.DefencePermission.VIEW_DEFENDANT_PERMISSION;
import static uk.gov.moj.cpp.defence.common.util.DefencePermission.VIEW_DOCUMENT_PERMISSION;
import static uk.gov.moj.cpp.defence.common.util.ErrorType.UNAUTHORIZED_REMOVE_GRANTING;
import static uk.gov.moj.cpp.defence.common.util.GrantAccessUtil.preparePermissionList;

import uk.gov.justice.cps.defence.AllegationsReceivedAgainstADefenceClient;
import uk.gov.justice.cps.defence.AssigneeForDefenceIsProsecutingCase;
import uk.gov.justice.cps.defence.DefendantDetails;
import uk.gov.justice.cps.defence.GranteeUserNotInAllowedGroups;
import uk.gov.justice.cps.defence.Offence;
import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.cps.defence.UserAlreadyGranted;
import uk.gov.justice.cps.defence.UserNotFound;
import uk.gov.justice.cps.defence.event.ReceiveDefendantUpdateFailed;
import uk.gov.justice.cps.defence.events.AccessGrantRemoved;
import uk.gov.justice.cps.defence.events.AccessGranted;
import uk.gov.justice.cps.defence.events.GrantAccessFailed;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.event.listener.events.AddedOffences;
import uk.gov.moj.cpp.defence.event.listener.events.DefendantOffencesUpdated;
import uk.gov.moj.cpp.defence.event.listener.events.DefendantUpdateReceived;
import uk.gov.moj.cpp.defence.event.listener.events.DeletedOffences;
import uk.gov.moj.cpp.defence.events.DefenceClientDoesNotExist;
import uk.gov.moj.cpp.defence.events.DefenceClientReceived;
import uk.gov.moj.cpp.defence.events.DefenceClientUrnAdded;
import uk.gov.moj.cpp.defence.events.IdpcAccessByOrganisationRecorded;
import uk.gov.moj.cpp.defence.events.IdpcAccessRecorded;
import uk.gov.moj.cpp.defence.events.IdpcDetailsRecorded;
import uk.gov.moj.cpp.defence.events.IdpcReceivedBeforeCase;
import uk.gov.moj.cpp.defence.events.InstructionDetailsRecorded;
import uk.gov.moj.cpp.defence.service.UserGroupService;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.JsonObject;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class DefenceClientTest {

    private static final UUID DEFENCE_CLIENT_ID = randomUUID();
    private static final String EMPTY_STRING = "";
    private static final LocalDate DOB = LocalDate.of(1985, 10, 23);
    private static final String FIRST_NAME = "BRIAN";
    private static final String LAST_NAME = "BURGLAR";
    private static final UUID DEFENDANT_ID = randomUUID();
    private static final UUID ENVELOPE_ID = UUID.randomUUID();
    final ZonedDateTime dateCreate = ZonedDateTime.now();
    @Mock
    private Requester requester;
    @Mock
    private UserGroupService usersGroupService;
    @InjectMocks
    private DefenceClient defenceClient;

    @Test
    public void shouldHandleRecordInstructionDetails() {
        final DefenceClient defenceClient = new DefenceClient();
        final DefendantDetails defendantDetails = DefendantDetails.defendantDetails()
                .withId(randomUUID())
                .withLastName("BADDUN")
                .build();
        defenceClient.receiveADefenceClient(DEFENCE_CLIENT_ID, EMPTY_STRING, defendantDetails, null, null);

        final UUID userId = randomUUID();
        final UUID organisationId = randomUUID();
        final UUID instructionId = randomUUID();
        final LocalDate instructionDate = LocalDate.parse("2018-01-01");

        final Stream<Object> eventStream = defenceClient.recordInstructionDetails(instructionDate, userId, organisationId, instructionId, DEFENCE_CLIENT_ID);

        final List<?> eventList = eventStream.toList();
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(InstructionDetailsRecorded.class.getName()));

        final InstructionDetailsRecorded instructionDetailsRecorded = (InstructionDetailsRecorded) eventList.get(0);

        assertThat(instructionDetailsRecorded.getDefenceClientId(), is(DEFENCE_CLIENT_ID));
        assertThat(instructionDetailsRecorded.getInstructionId(), is(instructionId));
        assertThat(instructionDetailsRecorded.getUserId(), is(userId));
        assertThat(instructionDetailsRecorded.getOrganisationId(), is(organisationId));
        assertThat(instructionDetailsRecorded.getInstructionDate(), is(instructionDate));
    }

    @Test
    public void shouldIndicateFailureWhenRecordingInstructionsOnUnkownDefenceClient() {
        final DefenceClient defenceClient = new DefenceClient();

        final UUID userId = randomUUID();
        final UUID organisationId = randomUUID();
        final UUID instructionId = randomUUID();
        final LocalDate instructionDate = LocalDate.parse("2018-01-01");

        final Stream<Object> eventStream = defenceClient.recordInstructionDetails(instructionDate, userId, organisationId, instructionId, DEFENCE_CLIENT_ID);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(DefenceClientDoesNotExist.class.getName()));

        final DefenceClientDoesNotExist event = (DefenceClientDoesNotExist) eventList.get(0);

        assertThat(event.getDefenceClientId(), is(DEFENCE_CLIENT_ID));
        assertThat(event.getDefendantId(), nullValue());
    }

    @Test
    public void shouldHandleReceiveUpdateClient() {
        final DefenceClient defenceClient = new DefenceClient();

        final UUID caseId = randomUUID();
        final DefendantDetails defendantDetails = DefendantDetails.defendantDetails()
                .withFirstName(FIRST_NAME)
                .withLastName(LAST_NAME)
                .withDateOfBirth(DOB.toString())
                .withCaseId(caseId)
                .withId(DEFENDANT_ID)
                .build();

        defenceClient.apply(DefenceClientReceived.defenceClientReceived()
                .withDefendantDetails(DefendantDetails.defendantDetails()
                        .withCaseId(caseId)
                        .withLastName(LAST_NAME)
                        .build()).build());

        final Stream<Object> eventStream = defenceClient.receiveUpdateClient(defendantDetails, DEFENDANT_ID);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(DefendantUpdateReceived.class.getName()));

        final DefendantUpdateReceived defendantUpdateReceivedEvent = (DefendantUpdateReceived) eventList.get(0);

        assertThat(defendantUpdateReceivedEvent.getDefendantDetails().getFirstName(), is(FIRST_NAME));
        assertThat(defendantUpdateReceivedEvent.getDefendantDetails().getLastName(), is(LAST_NAME));
        assertThat(defendantUpdateReceivedEvent.getDefendantDetails().getDateOfBirth(), is(DOB.toString()));
        assertThat(defendantUpdateReceivedEvent.getDefendantDetails().getId(), is(DEFENDANT_ID));
        assertThat(defendantUpdateReceivedEvent.getDefendantDetails().getCaseId(), is(caseId));
    }

    @Test
    public void shouldHandleReceiveUpdateClientBeforeCaseCreatedAndRaiseFailEvent() {
        final DefenceClient defenceClient = new DefenceClient();

        final UUID caseId = randomUUID();
        final DefendantDetails defendantDetails = DefendantDetails.defendantDetails()
                .withFirstName(FIRST_NAME)
                .withLastName(LAST_NAME)
                .withDateOfBirth(DOB.toString())
                .withCaseId(caseId)
                .withId(DEFENDANT_ID)
                .build();

        final Stream<Object> eventStream = defenceClient.receiveUpdateClient(defendantDetails, DEFENDANT_ID);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(ReceiveDefendantUpdateFailed.class.getName()));

        final ReceiveDefendantUpdateFailed receiveDefendantUpdateFailed = (ReceiveDefendantUpdateFailed) eventList.get(0);

        assertThat(receiveDefendantUpdateFailed.getDefendantId(), is(DEFENDANT_ID));
        assertThat(receiveDefendantUpdateFailed.getCaseId(), is(caseId));
    }

    @Test
    public void shouldHandleReceiveUpdateOffences() {
        final DefenceClient defenceClient = new DefenceClient();

        final UUID defenceClientId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID prosecutionCaseId = randomUUID();

        final List<AddedOffences> addedOffences = Collections.singletonList(AddedOffences.addedOffences()
                .withDefenceClientId(defenceClientId)
                .withOffences(Collections.singletonList(Offence.offence().build()))
                .withDefendantId(defendantId)
                .withProsecutionCaseId(prosecutionCaseId)
                .build());
        final List<DeletedOffences> deletedOffences = Collections.singletonList(DeletedOffences.deletedOffences()
                .withDefenceClientId(defenceClientId)
                .withDefendantId(defendantId)
                .withOffences(Collections.singletonList(UUID.randomUUID()))
                .withProsecutionCaseId(prosecutionCaseId)
                .build());

        final Stream<Object> eventStream = defenceClient.receiveUpdateOffences(DOB, addedOffences, deletedOffences);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(DefendantOffencesUpdated.class.getName()));

        final DefendantOffencesUpdated defendantOffencesUpdated = (DefendantOffencesUpdated) eventList.get(0);

        assertThat(defendantOffencesUpdated.getModifiedDate(), is(DOB));
        assertThat(defendantOffencesUpdated.getDeletedOffences().get(0).getDefenceClientId(), is(defenceClientId));
        assertThat(defendantOffencesUpdated.getDeletedOffences().get(0).getDefendantId(), is(defendantId));
        assertThat(defendantOffencesUpdated.getDeletedOffences().get(0).getProsecutionCaseId(), is(prosecutionCaseId));
        assertThat(defendantOffencesUpdated.getAddedOffences().get(0).getDefenceClientId(), is(defenceClientId));
        assertThat(defendantOffencesUpdated.getAddedOffences().get(0).getDefendantId(), is(defendantId));
        assertThat(defendantOffencesUpdated.getAddedOffences().get(0).getProsecutionCaseId(), is(prosecutionCaseId));
    }

    @Test
    public void shouldHandleReceiveUpdateOffencesWithNoDeletedOffences() {
        final DefenceClient defenceClient = new DefenceClient();

        final UUID defenceClientId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID prosecutionCaseId = randomUUID();

        final List<AddedOffences> addedOffences = Collections.singletonList(AddedOffences.addedOffences()
                .withDefenceClientId(defenceClientId)
                .withOffences(Collections.singletonList(Offence.offence().build()))
                .withDefendantId(defendantId)
                .withProsecutionCaseId(prosecutionCaseId)
                .build());

        final Stream<Object> eventStream = defenceClient.receiveUpdateOffences(DOB, addedOffences, null);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(DefendantOffencesUpdated.class.getName()));

        final DefendantOffencesUpdated defendantOffencesUpdated = (DefendantOffencesUpdated) eventList.get(0);

        assertThat(defendantOffencesUpdated.getModifiedDate(), is(DOB));
        assertThat(defendantOffencesUpdated.getAddedOffences().get(0).getDefenceClientId(), is(defenceClientId));
        assertThat(defendantOffencesUpdated.getAddedOffences().get(0).getDefendantId(), is(defendantId));
        assertThat(defendantOffencesUpdated.getAddedOffences().get(0).getProsecutionCaseId(), is(prosecutionCaseId));
    }

    @Test
    public void shouldRaiseUserNotFoundWhenGrantingAccessToUnknownUser() {

        final UUID defenceClientId = UUID.randomUUID();
        final UUID target = defenceClientId;
        final String granteeEmail = "not-exist-email@hmcts.net";

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        when(usersGroupService.metadataBuilderWithNewActionName(any(), eq("usersgroups.search-users"))).thenReturn(buildMetadataWithActionName("usersgroups.search-users"));
        when(requester.requestAsAdmin(any(), any())).thenReturn(null);
        when(usersGroupService.getUserDetailsWithEmail(any(), any(), any())).thenCallRealMethod();

        final PersonDetails granteeDetails = usersGroupService.getUserDetailsWithEmail(granteeEmail, metadata, requester);
        final List<String> granteeGroupList = null;
        final List<String> granterGroupList = null;
        final Organisation granteeOrganisation = null;
        final Organisation granterOrganisation = null;
        final UUID associatedOrganisationId = null;

        final PersonDetails granterDetails = null;


        final Stream<Object> eventStream = defenceClient.grantAccessToUser(target, granteeEmail, granteeDetails, granteeGroupList, granterGroupList, granteeOrganisation, granterOrganisation, granterDetails, associatedOrganisationId, false);


        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(UserNotFound.class.getName()));

        final UserNotFound userNotFoundEvent = (UserNotFound) eventList.get(0);
        assertThat(userNotFoundEvent.getEmail(), is(granteeEmail));
    }

    @Test
    public void shouldRaiseAssigneeForDefenceIsProsecutingCaseWhenUserInProsecutorRole() {

        final UUID granteeUserId = randomUUID();
        final String granteeEmail = "email@hmcts.net";

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        final PersonDetails granteePersonDetails = PersonDetails.personDetails()
                .withFirstName("grantee_name")
                .withLastName("grantee_lastname")
                .withUserId(granteeUserId)
                .build();

        when(usersGroupService.getUserDetailsWithEmail(any(), any(), any())).thenReturn(granteePersonDetails);

        final PersonDetails granteeDetails = usersGroupService.getUserDetailsWithEmail(granteeEmail, metadata, requester);
        final List<String> granteeGroupList = null;
        final List<String> granterGroupList = null;
        final Organisation granteeOrganisation = null;
        final Organisation granterOrganisation = null;
        final UUID associatedOrganisationId = null;

        final PersonDetails granterDetails = null;


        final Stream<Object> eventStream = defenceClient.grantAccessToUser(randomUUID(), granteeEmail, granteeDetails, granteeGroupList, granterGroupList, granteeOrganisation, granterOrganisation, granterDetails, associatedOrganisationId, true);


        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(AssigneeForDefenceIsProsecutingCase.class.getName()));

        final AssigneeForDefenceIsProsecutingCase assigneeForDefenceIsProsecutingCase = (AssigneeForDefenceIsProsecutingCase) eventList.get(0);
        assertThat(assigneeForDefenceIsProsecutingCase.getEmail(), is(granteeEmail));
    }

    @Test
    public void shouldRaiseGranteeUserNotInAllowedGroupsWhenGranteeOrganisationIsNull() {

        final UUID granteeUserId = randomUUID();
        final String granteeEmail = "email@hmcts.net";

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        final PersonDetails granteePersonDetails = PersonDetails.personDetails()
                .withFirstName("grantee_name")
                .withLastName("grantee_lastname")
                .withUserId(granteeUserId)
                .build();

        when(usersGroupService.getUserDetailsWithEmail(any(), any(), any())).thenReturn(granteePersonDetails);

        final PersonDetails granteeDetails = usersGroupService.getUserDetailsWithEmail(granteeEmail, metadata, requester);
        final List<String> granteeGroupList = null;
        final List<String> granterGroupList = null;
        final Organisation granteeOrganisation = null;
        final Organisation granterOrganisation = null;
        final UUID associatedOrganisationId = null;

        final PersonDetails granterDetails = null;


        final Stream<Object> eventStream = defenceClient.grantAccessToUser(randomUUID(), granteeEmail, granteeDetails, granteeGroupList, granterGroupList, granteeOrganisation, granterOrganisation, granterDetails, associatedOrganisationId, false);


        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(GranteeUserNotInAllowedGroups.class.getName()));

        final GranteeUserNotInAllowedGroups granteeUserNotInAllowedGroups = (GranteeUserNotInAllowedGroups) eventList.get(0);
        assertThat(granteeUserNotInAllowedGroups.getEmail(), is(granteeEmail));
    }

    @Test
    public void shouldHandleGrantAccessToUserAndRaiseGranteeUserNotInAllowedGroups() {

        final UUID defenceClientId = UUID.randomUUID();
        final UUID target = defenceClientId;
        final String granteeEmail = "email@hmcts.net";
        final UUID granteeUserId = UUID.randomUUID();
        final PersonDetails granteePersonDetails = PersonDetails.personDetails()
                .withFirstName("grantee_name")
                .withLastName("grantee_lastname")
                .withUserId(granteeUserId)
                .build();

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        when(usersGroupService.getUserDetailsWithEmail(any(), any(), any())).thenReturn(granteePersonDetails);

        when(usersGroupService.metadataBuilderWithNewActionName(any(), eq("usersgroups.get-groups-by-user"))).thenReturn(buildMetadataWithActionName("usersgroups.get-groups-by-user"));
        when(requester.requestAsAdmin(any(), any())).thenReturn(getGroupsMock("non existing group name"));
        when(usersGroupService.getGroupNamesForUser(any(), any(), any())).thenCallRealMethod();

        final PersonDetails granteeDetails = usersGroupService.getUserDetailsWithEmail(granteeEmail, metadata, requester);
        final List<String> granteeGroupList = usersGroupService.getGroupNamesForUser(granteeDetails.getUserId(), metadata, requester);
        final UUID associatedOrganisationId = UUID.randomUUID();
        final List<String> granterGroupList = null;
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(UUID.randomUUID()).build();
        final Organisation granterOrganisation = Organisation.organisation().withOrgId(associatedOrganisationId).build();
        final PersonDetails granterDetails = null;


        final Stream<Object> eventStream = defenceClient.grantAccessToUser(target, granteeEmail, granteeDetails, granteeGroupList, granterGroupList, granteeOrganisation, granterOrganisation, granterDetails, associatedOrganisationId, false);


        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(GranteeUserNotInAllowedGroups.class.getName()));

        final GranteeUserNotInAllowedGroups granteeUserNotInAllowedGroupsEvent = (GranteeUserNotInAllowedGroups) eventList.get(0);
        assertThat(granteeUserNotInAllowedGroupsEvent.getEmail(), is(granteeEmail));
    }

    @Test
    public void shouldHandleGrantAccessToUserAndRaiseUserAlreadyGranted() {

        final UUID defenceClientId = UUID.randomUUID();
        final UUID target = defenceClientId;
        final String granteeEmail = "email@hmcts.net";
        final UUID granteeUserId = UUID.randomUUID();
        final UUID granterUserId = UUID.randomUUID();
        final String granteeName = "grantee_name";
        final String granteeLastName = "grantee_lastname";
        final String granterName = "granter_name";
        final String granterLastName = "granter_lastname";

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        final PersonDetails granteePersonDetails = PersonDetails.personDetails()
                .withFirstName(granteeName)
                .withLastName(granteeLastName)
                .withUserId(granteeUserId)
                .build();

        final PersonDetails granterPersonDetails = PersonDetails.personDetails()
                .withFirstName(granterName)
                .withLastName(granterLastName)
                .withUserId(granterUserId)
                .build();

        when(usersGroupService.getUserDetailsWithEmail(any(), any(), any())).thenReturn(granteePersonDetails);

        when(usersGroupService.metadataBuilderWithNewActionName(any(), eq("usersgroups.get-groups-by-user"))).thenReturn(buildMetadataWithActionName("usersgroups.get-groups-by-user"));
        when(requester.requestAsAdmin(any(), any())).thenReturn(getGroupsMock("Chambers Clerk"));
        when(usersGroupService.getGroupNamesForUser(any(), any(), any())).thenCallRealMethod();
        when(usersGroupService.getUserDetailsWithUserId(any(), any(), any())).thenReturn(granterPersonDetails);
        when(usersGroupService.getUserDetailsWithEmail(any(), any(), any())).thenReturn(granteePersonDetails);

        final PersonDetails granteeDetails = usersGroupService.getUserDetailsWithEmail(granteeEmail, metadata, requester);
        final PersonDetails granterDetails = usersGroupService.getUserDetailsWithUserId(granterUserId, metadata, requester);
        final List<String> granteeGroupList = usersGroupService.getGroupNamesForUser(granteeDetails.getUserId(), metadata, requester);
        final List<String> granterGroupList = usersGroupService.getGroupNamesForUser(granterDetails.getUserId(), metadata, requester);
        final UUID associatedOrganisationId = UUID.randomUUID();
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(UUID.randomUUID()).build();
        final Organisation granterOrganisation = Organisation.organisation().withOrgId(associatedOrganisationId).build();

        Stream<Object> eventStream = defenceClient.grantAccessToUser(target, granteeEmail, granteeDetails, granteeGroupList, granterGroupList, granteeOrganisation, granterOrganisation, granterDetails, associatedOrganisationId, false);

        List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(AccessGranted.class.getName()));

        eventStream = defenceClient.grantAccessToUser(target, granteeEmail, granteeDetails, granteeGroupList, granterGroupList, granteeOrganisation, granterOrganisation, granterDetails, associatedOrganisationId, false);
        eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(UserAlreadyGranted.class.getName()));
    }

    @Test
    public void shouldReturnReceiveAllegations() {
        final UUID defenceClientId = randomUUID();
        final UUID policeDefendantId = randomUUID();
        final UUID defendantId = defenceClientId;
        final DefendantDetails defendantDetails = DefendantDetails.defendantDetails().withId(randomUUID()).withLastName("BADDUN").build();

        final List<Object> eventStream = defenceClient.receiveAllegations(
                defenceClientId,
                defendantId,
                defendantDetails,
                policeDefendantId.toString(),
                null, null).collect(toList());
        assertThat(eventStream.size(), is(1));
        assertThat(eventStream.get(0).getClass(), is(CoreMatchers.equalTo(AllegationsReceivedAgainstADefenceClient.class)));

        final AllegationsReceivedAgainstADefenceClient allegationsReceivedAgainstADefenceClient = (AllegationsReceivedAgainstADefenceClient) eventStream.get(0);
        assertThat(allegationsReceivedAgainstADefenceClient.getDefenceClientId(), is(defenceClientId));
        assertThat(allegationsReceivedAgainstADefenceClient.getDefendantId(), is(defenceClientId));

    }

    @Test
    public void shouldReturnDefenceClientUrnAdded() {
        final UUID defenceClientId = randomUUID();
        final String urn = "urn1234";

        final List<Object> eventStream = defenceClient.receiveUrn(
                defenceClientId,
                urn).collect(toList());
        assertThat(eventStream.size(), is(1));
        assertThat(eventStream.get(0).getClass(), is(CoreMatchers.equalTo(DefenceClientUrnAdded.class)));

        final DefenceClientUrnAdded defenceClientUrnAdded = (DefenceClientUrnAdded) eventStream.get(0);
        assertThat(defenceClientUrnAdded.getDefenceClientId(), is(defenceClientId));
        assertThat(defenceClientUrnAdded.getUrn(), is(urn));

    }

    @Test
    public void shouldReturnIdpcReceivedBeforeCase() {
        final UUID defenceClientId = randomUUID();

        final List<Object> eventStream = defenceClient.recordIdpcDetails(
                null,
                defenceClientId).collect(toList());
        assertThat(eventStream.size(), is(1));
        assertThat(eventStream.get(0).getClass(), is(CoreMatchers.equalTo(IdpcReceivedBeforeCase.class)));

        final IdpcReceivedBeforeCase idpcReceivedBeforeCase = (IdpcReceivedBeforeCase) eventStream.get(0);
        assertThat(idpcReceivedBeforeCase.getDefenceClientId(), is(defenceClientId));
        assertThat(idpcReceivedBeforeCase.getIdpcDetails(), nullValue());

    }

    @Test
    public void shouldReturnIdpcDetailsRecorded() {
        final UUID defenceClientId = randomUUID();

        defenceClient.apply(DefenceClientReceived.defenceClientReceived()
                .withDefendantDetails(DefendantDetails.defendantDetails()
                        .withCaseId(randomUUID())
                        .withLastName(LAST_NAME)
                        .build())
                .withDefenceClientId(defenceClientId).build());

        final List<Object> eventStream = defenceClient.recordIdpcDetails(
                null,
                defenceClientId).collect(toList());
        assertThat(eventStream.size(), is(1));
        assertThat(eventStream.get(0).getClass(), is(CoreMatchers.equalTo(IdpcDetailsRecorded.class)));

        final IdpcDetailsRecorded idpcDetailsRecorded = (IdpcDetailsRecorded) eventStream.get(0);
        assertThat(idpcDetailsRecorded.getDefenceClientId(), is(defenceClientId));
        assertThat(idpcDetailsRecorded.getIdpcDetails(), nullValue());

    }

    @Test
    public void shouldReturnIdpcAccessRecorded() {
        final UUID defenceClientId = randomUUID();

        defenceClient.apply(DefenceClientReceived.defenceClientReceived()
                .withDefendantDetails(DefendantDetails.defendantDetails()
                        .withCaseId(randomUUID())
                        .withLastName(LAST_NAME)
                        .build())
                .withDefenceClientId(defenceClientId).build());

        final List<Object> eventStream = defenceClient.recordIdpcAccess(
                null,
                randomUUID(),
                randomUUID(),
                randomUUID(),
                randomUUID()
        ).collect(toList());
        assertThat(eventStream.size(), is(2));
        assertThat(eventStream.get(0).getClass(), is(CoreMatchers.equalTo(IdpcAccessRecorded.class)));
        assertThat(eventStream.get(1).getClass(), is(CoreMatchers.equalTo(IdpcAccessByOrganisationRecorded.class)));

        final IdpcAccessRecorded idpcAccessRecorded = (IdpcAccessRecorded) eventStream.get(0);
        assertThat(idpcAccessRecorded.getDefenceClientId(), is(defenceClientId));

        final IdpcAccessByOrganisationRecorded idpcAccessByOrganisationRecorded = (IdpcAccessByOrganisationRecorded) eventStream.get(1);
        assertThat(idpcAccessByOrganisationRecorded.getDefenceClientId(), is(defenceClientId));

    }


    @Test
    public void shouldHandleGrantAccessToUserAndRaiseAccessGranted() {

        final UUID defenceClientId = UUID.randomUUID();
        final UUID target = defenceClientId;
        final String granteeEmail = "email@hmcts.net";
        final UUID granteeUserId = UUID.randomUUID();
        final UUID granterUserId = UUID.randomUUID();
        final String granteeName = "grantee_name";
        final String granteeLastName = "grantee_lastname";
        final String granterName = "granter_name";
        final String granterLastName = "granter_lastname";

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        final PersonDetails granteePersonDetails = PersonDetails.personDetails()
                .withFirstName(granteeName)
                .withLastName(granteeLastName)
                .withUserId(granteeUserId)
                .build();

        final PersonDetails granterPersonDetails = PersonDetails.personDetails()
                .withFirstName(granterName)
                .withLastName(granterLastName)
                .withUserId(granterUserId)
                .build();

        when(usersGroupService.getUserDetailsWithEmail(any(), any(), any())).thenReturn(granteePersonDetails);

        when(usersGroupService.metadataBuilderWithNewActionName(any(), eq("usersgroups.get-groups-by-user"))).thenReturn(buildMetadataWithActionName("usersgroups.get-groups-by-user"));
        when(requester.requestAsAdmin(any(), any())).thenReturn(getGroupsMock("Chambers Clerk"));
        when(usersGroupService.getGroupNamesForUser(any(), any(), any())).thenCallRealMethod();
        when(usersGroupService.getUserDetailsWithUserId(any(), any(), any())).thenReturn(granterPersonDetails);
        when(usersGroupService.getUserDetailsWithEmail(any(), any(), any())).thenReturn(granteePersonDetails);

        final PersonDetails granteeDetails = usersGroupService.getUserDetailsWithEmail(granteeEmail, metadata, requester);
        final PersonDetails granterDetails = usersGroupService.getUserDetailsWithUserId(granterUserId, metadata, requester);
        final List<String> granteeGroupList = usersGroupService.getGroupNamesForUser(granteeDetails.getUserId(), metadata, requester);
        final List<String> granterGroupList = usersGroupService.getGroupNamesForUser(granterDetails.getUserId(), metadata, requester);
        final UUID associatedOrganisationId = UUID.randomUUID();
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(UUID.randomUUID()).build();
        final Organisation granterOrganisation = Organisation.organisation().withOrgId(associatedOrganisationId).build();


        final Stream<Object> eventStream = defenceClient.grantAccessToUser(target, granteeEmail, granteeDetails, granteeGroupList, granterGroupList, granteeOrganisation, granterOrganisation, granterDetails, associatedOrganisationId, false);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(AccessGranted.class.getName()));

        final AccessGranted accessGranted = (AccessGranted) eventList.get(0);
        assertThat(accessGranted.getGranteeDetails().getUserId(), is(granteeUserId));
        assertThat(accessGranted.getGranteeDetails().getFirstName(), is(granteeName));
        assertThat(accessGranted.getGranteeDetails().getLastName(), is(granteeLastName));
        assertThat(accessGranted.getGranterDetails().getUserId(), is(granterUserId));
        assertThat(accessGranted.getGranterDetails().getFirstName(), is(granterName));
        assertThat(accessGranted.getGranterDetails().getLastName(), is(granterLastName));
        assertThat(accessGranted.getPermissions().size(), is(1));

        assertThat(accessGranted.getPermissions().get(0).getAction(), is(VIEW_DEFENDANT_PERMISSION.getActionType()));
        assertThat(accessGranted.getPermissions().get(0).getObject(), is(VIEW_DEFENDANT_PERMISSION.getObjectType()));
        assertThat(accessGranted.getPermissions().get(0).getSource(), is(granteeUserId));
        assertThat(accessGranted.getPermissions().get(0).getTarget(), is(defenceClientId));
        assertThat(accessGranted.getPermissions().get(0).getId(), notNullValue());

        assertThat(accessGranted.getGranteeOrganisation().getOrgId(), notNullValue());
    }


    @Test
    public void shouldHandleRemoveGrantAccessWhenUserAssociatedThenCanRemoveOtherOrgGrants() {

        final UUID granteeUserId = UUID.randomUUID();
        final UUID loggedInUserId = UUID.randomUUID();
        final UUID associatedOrganisationId = UUID.randomUUID();
        final UUID defenceClientId = UUID.randomUUID();

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        when(usersGroupService.getGroupNamesForUser(any(), any(), any())).thenReturn(Arrays.asList("Advocates"));

        Map<UUID, List<Permission>> permissionMap = new HashMap<>();
        permissionMap.put(granteeUserId, preparePermissionList(defenceClientId, granteeUserId, true, empty()));

        Map<UUID, Set<UUID>> granterGranteeMap = new HashMap<>();
        granterGranteeMap.put(granteeUserId, Collections.emptySet());

        ReflectionUtil.setField(defenceClient, "userPermissionMap", permissionMap);

        final Organisation loggedInUserOrganisation = Organisation.organisation().withOrgId(associatedOrganisationId).build();
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(associatedOrganisationId).build();
        final List<String> loggedInUserGroupList = usersGroupService.getGroupNamesForUser(loggedInUserId, metadata, requester);

        final Stream<Object> eventStream = defenceClient.removeGrantAccessToUser(granteeUserId, loggedInUserId, associatedOrganisationId, loggedInUserOrganisation, granteeOrganisation, loggedInUserGroupList);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(AccessGrantRemoved.class.getName()));

        final AccessGrantRemoved accessGrantRemoved = (AccessGrantRemoved) eventList.get(0);
        assertThat(accessGrantRemoved.getPermissions().size(), is(3));
        assertThat(accessGrantRemoved.getPermissions().get(0).getAction(), is(VIEW_DOCUMENT_PERMISSION.getActionType()));
        assertThat(accessGrantRemoved.getPermissions().get(0).getObject(), is(VIEW_DOCUMENT_PERMISSION.getObjectType()));
        assertThat(accessGrantRemoved.getPermissions().get(0).getSource(), is(granteeUserId));
        assertThat(accessGrantRemoved.getPermissions().get(0).getTarget(), is(defenceClientId));
        assertThat(accessGrantRemoved.getPermissions().get(0).getId(), notNullValue());

        assertThat(accessGrantRemoved.getPermissions().get(1).getAction(), is(UPLOAD_DOCUMENT_PERMISSION.getActionType()));
        assertThat(accessGrantRemoved.getPermissions().get(1).getObject(), is(UPLOAD_DOCUMENT_PERMISSION.getObjectType()));
        assertThat(accessGrantRemoved.getPermissions().get(1).getSource(), is(granteeUserId));
        assertThat(accessGrantRemoved.getPermissions().get(1).getTarget(), is(defenceClientId));
        assertThat(accessGrantRemoved.getPermissions().get(1).getId(), notNullValue());

        assertThat(accessGrantRemoved.getPermissions().get(2).getAction(), is(VIEW_DEFENDANT_PERMISSION.getActionType()));
        assertThat(accessGrantRemoved.getPermissions().get(2).getObject(), is(VIEW_DEFENDANT_PERMISSION.getObjectType()));
        assertThat(accessGrantRemoved.getPermissions().get(2).getSource(), is(granteeUserId));
        assertThat(accessGrantRemoved.getPermissions().get(2).getTarget(), is(defenceClientId));
        assertThat(accessGrantRemoved.getPermissions().get(2).getId(), notNullValue());

    }

    @Test
    public void shouldHandleRemoveAllGrantAccess() {

        final List<UUID> userIdList = Arrays.asList(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        final UUID granteeUserId = UUID.randomUUID();

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        final Map<UUID, List<Permission>> permissionMap = userIdList.stream().map(uuid ->
                preparePermissionList(uuid)
        ).collect(Collectors.toMap(permissions -> permissions.get(0).getSource(), Function.identity()));


        Map<UUID, Set<UUID>> granterGranteeMap = new HashMap<>();
        granterGranteeMap.put(granteeUserId, Collections.emptySet());

        ReflectionUtil.setField(defenceClient, "userPermissionMap", permissionMap);

        final Stream<Object> eventStream = defenceClient.removeAllGrantees();

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(userIdList.size()));
        assertThat(eventList.get(0).getClass().getName(), is(AccessGrantRemoved.class.getName()));


        final AccessGrantRemoved accessGrantRemoved = (AccessGrantRemoved) eventList.get(0);
        assertThat(accessGrantRemoved.getPermissions().get(0).getSource(), anyOf(is(userIdList.get(0)), is(userIdList.get(1)), is(userIdList.get(2)), is(userIdList.get(3))));
        assertThat(permissionMap.size(), is(0));

    }

    @Test
    public void shouldHandleRemoveGrantAccessWhenUserNotAssociatedAndAdvocatesThenCanRemoveOwnGrant() {

        final UUID granteeUserId = UUID.randomUUID();
        final UUID loggedInUserId = granteeUserId;
        final UUID associatedOrganisationId = UUID.randomUUID();
        final UUID chambersOrganisationId = UUID.randomUUID();
        final UUID defenceClientId = UUID.randomUUID();

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        final List<String> userGroupList = Arrays.asList("Advocates");

        when(usersGroupService.getGroupNamesForUser(any(), any(), any())).thenReturn(userGroupList);

        Map<UUID, List<Permission>> permissionMap = new HashMap<>();
        permissionMap.put(granteeUserId, preparePermissionList(defenceClientId, granteeUserId, false, of(userGroupList)));

        Map<UUID, Set<UUID>> granterGranteeMap = new HashMap<>();
        granterGranteeMap.put(granteeUserId, Collections.emptySet());

        ReflectionUtil.setField(defenceClient, "userPermissionMap", permissionMap);

        final Organisation loggedInUserOrganisation = Organisation.organisation().withOrgId(chambersOrganisationId).build();
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(chambersOrganisationId).build();
        final List<String> loggedInUserGroupList = usersGroupService.getGroupNamesForUser(loggedInUserId, metadata, requester);

        final Stream<Object> eventStream = defenceClient.removeGrantAccessToUser(granteeUserId, loggedInUserId, associatedOrganisationId, loggedInUserOrganisation, granteeOrganisation, loggedInUserGroupList);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(AccessGrantRemoved.class.getName()));

        final AccessGrantRemoved accessGrantRemoved = (AccessGrantRemoved) eventList.get(0);
        assertThat(accessGrantRemoved.getPermissions().size(), is(3));
        assertThat(accessGrantRemoved.getPermissions().get(0).getAction(), is(VIEW_DOCUMENT_PERMISSION.getActionType()));
        assertThat(accessGrantRemoved.getPermissions().get(0).getObject(), is(VIEW_DOCUMENT_PERMISSION.getObjectType()));
        assertThat(accessGrantRemoved.getPermissions().get(0).getSource(), is(granteeUserId));
        assertThat(accessGrantRemoved.getPermissions().get(0).getTarget(), is(defenceClientId));
        assertThat(accessGrantRemoved.getPermissions().get(0).getId(), notNullValue());

        assertThat(accessGrantRemoved.getPermissions().get(1).getAction(), is(UPLOAD_DOCUMENT_PERMISSION.getActionType()));
        assertThat(accessGrantRemoved.getPermissions().get(1).getObject(), is(UPLOAD_DOCUMENT_PERMISSION.getObjectType()));
        assertThat(accessGrantRemoved.getPermissions().get(1).getSource(), is(granteeUserId));
        assertThat(accessGrantRemoved.getPermissions().get(1).getTarget(), is(defenceClientId));
        assertThat(accessGrantRemoved.getPermissions().get(1).getId(), notNullValue());

        assertThat(accessGrantRemoved.getPermissions().get(2).getAction(), is(VIEW_DEFENDANT_PERMISSION.getActionType()));
        assertThat(accessGrantRemoved.getPermissions().get(2).getObject(), is(VIEW_DEFENDANT_PERMISSION.getObjectType()));
        assertThat(accessGrantRemoved.getPermissions().get(2).getSource(), is(granteeUserId));
        assertThat(accessGrantRemoved.getPermissions().get(2).getTarget(), is(defenceClientId));
        assertThat(accessGrantRemoved.getPermissions().get(2).getId(), notNullValue());
    }

    @Test
    public void shouldHandleRemoveGrantAccessWhenUserNotAssociatedAndAdvocatesThenCanNotRemoveOthersGrant() {

        final UUID granteeUserId = UUID.randomUUID();
        final UUID loggedInUserId = UUID.randomUUID();
        final UUID associatedOrganisationId = UUID.randomUUID();
        final UUID chambersOrganisationId = UUID.randomUUID();
        final UUID defenceClientId = UUID.randomUUID();

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        final List<String> userGroupList = Arrays.asList("Advocates");

        when(usersGroupService.getGroupNamesForUser(any(), any(), any())).thenReturn(userGroupList);

        Map<UUID, List<Permission>> permissionMap = new HashMap<>();
        permissionMap.put(granteeUserId, preparePermissionList(defenceClientId, granteeUserId, false, of(userGroupList)));

        Map<UUID, Set<UUID>> granterGranteeMap = new HashMap<>();
        granterGranteeMap.put(granteeUserId, Collections.emptySet());

        ReflectionUtil.setField(defenceClient, "userPermissionMap", permissionMap);

        final Organisation loggedInUserOrganisation = Organisation.organisation().withOrgId(chambersOrganisationId).build();
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(chambersOrganisationId).build();
        final List<String> loggedInUserGroupList = usersGroupService.getGroupNamesForUser(loggedInUserId, metadata, requester);

        final Stream<Object> eventStream = defenceClient.removeGrantAccessToUser(granteeUserId, loggedInUserId, associatedOrganisationId, loggedInUserOrganisation, granteeOrganisation, loggedInUserGroupList);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(GrantAccessFailed.class.getName()));

        final GrantAccessFailed grantAccessFailed = (GrantAccessFailed) eventList.get(0);
        assertThat(grantAccessFailed.getErrorCode(), is(UNAUTHORIZED_REMOVE_GRANTING.getCode()));
        assertThat(grantAccessFailed.getErrorMessage(), is(UNAUTHORIZED_REMOVE_GRANTING.getMessage()));
        assertThat(grantAccessFailed.getUserId(), is(loggedInUserId));
    }


    @Test
    public void shouldHandleRemoveGrantAccessWhenUserNotAssociatedAndChambersAdminThenCanRemoveOthersGrant() {

        final UUID granteeUserId = UUID.randomUUID();
        final UUID loggedInUserId = UUID.randomUUID();
        final UUID associatedOrganisationId = UUID.randomUUID();
        final UUID chambersOrganisationId = UUID.randomUUID();
        final UUID defenceClientId = UUID.randomUUID();

        final Metadata metadata = metadataBuilder().withId(randomUUID())
                .withName("defence.command.grant-defence-access")
                .createdAt(now()).build();

        final List<String> userGroupList = Arrays.asList("Chambers Admin");

        when(usersGroupService.getGroupNamesForUser(any(), any(), any())).thenReturn(userGroupList);

        Map<UUID, List<Permission>> permissionMap = new HashMap<>();
        permissionMap.put(granteeUserId, preparePermissionList(defenceClientId, granteeUserId, false, of(userGroupList)));

        Map<UUID, Set<UUID>> granterGranteeMap = new HashMap<>();
        granterGranteeMap.put(granteeUserId, Collections.emptySet());

        ReflectionUtil.setField(defenceClient, "userPermissionMap", permissionMap);

        final Organisation loggedInUserOrganisation = Organisation.organisation().withOrgId(chambersOrganisationId).build();
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(chambersOrganisationId).build();
        final List<String> loggedInUserGroupList = usersGroupService.getGroupNamesForUser(loggedInUserId, metadata, requester);

        assertThat(defenceClient.isAlreadyGranted(granteeUserId, associatedOrganisationId, granteeOrganisation), is(true));

        final Stream<Object> eventStream = defenceClient.removeGrantAccessToUser(granteeUserId, loggedInUserId, associatedOrganisationId, loggedInUserOrganisation, granteeOrganisation, loggedInUserGroupList);

        final List<?> eventList = eventStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(1));
        assertThat(eventList.get(0).getClass().getName(), is(AccessGrantRemoved.class.getName()));

        final AccessGrantRemoved accessGrantRemoved = (AccessGrantRemoved) eventList.get(0);
        assertThat(accessGrantRemoved.getPermissions().size(), is(1));

        assertThat(accessGrantRemoved.getPermissions().get(0).getAction(), is(VIEW_DEFENDANT_PERMISSION.getActionType()));
        assertThat(accessGrantRemoved.getPermissions().get(0).getObject(), is(VIEW_DEFENDANT_PERMISSION.getObjectType()));
        assertThat(accessGrantRemoved.getPermissions().get(0).getSource(), is(granteeUserId));
        assertThat(accessGrantRemoved.getPermissions().get(0).getTarget(), is(defenceClientId));
        assertThat(accessGrantRemoved.getPermissions().get(0).getId(), notNullValue());

        assertThat(defenceClient.isAlreadyGranted(granteeUserId, associatedOrganisationId, granteeOrganisation), is(false));
    }

    @Test
    public void testIsAlreadyGrantedForNotGrantedAndFromAssociatedOrgReturnTrue() {

        final UUID userId = UUID.randomUUID();

        final Map<UUID, Set<UUID>> grantAccessUserMap = new HashMap<>();
        grantAccessUserMap.put(userId, Collections.emptySet());

        final UUID associatedOrganisationId = UUID.randomUUID();
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(associatedOrganisationId).build();

        assertThat(defenceClient.isAlreadyGranted(UUID.randomUUID(), associatedOrganisationId, granteeOrganisation), is(true));
    }

    @Test
    public void testIsAlreadyGrantedForNotGrantedAndNotFromAssociatedOrgReturnFalse() {
        final UUID userId = UUID.randomUUID();

        final Map<UUID, Set<UUID>> grantAccessUserMap = new HashMap<>();
        grantAccessUserMap.put(userId, Collections.emptySet());

        final UUID associatedOrganisationId = UUID.randomUUID();
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(UUID.randomUUID()).build();

        assertThat(defenceClient.isAlreadyGranted(UUID.randomUUID(), associatedOrganisationId, granteeOrganisation), is(false));
    }

    @Test
    public void testIsAlreadyGrantedForGrantedAndNotFromAssociatedOrgReturnTrue() {
        final UUID userId = UUID.randomUUID();

        final Map<UUID, Permission> grantAccessUserMap = new HashMap<>();
        grantAccessUserMap.put(userId, Permission.permission().build());

        ReflectionUtil.setField(defenceClient, "userPermissionMap", grantAccessUserMap);

        final UUID associatedOrganisationId = UUID.randomUUID();
        final Organisation granteeOrganisation = Organisation.organisation().withOrgId(UUID.randomUUID()).build();

        assertThat(defenceClient.isAlreadyGranted(userId, associatedOrganisationId, granteeOrganisation), is(true));
    }

    public MetadataBuilder buildMetadataWithActionName(final String actionName) {
        return Envelope.metadataBuilder().withId(ENVELOPE_ID)
                .withName(actionName)
                .createdAt(dateCreate);
    }

    private Envelope getGroupsMock(final String groupName) {
        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName("defence.grant-defence-access")
                .createdAt(now()).build();
        return Envelope.envelopeFrom(metadata, getGroupsResponseJson(groupName));
    }

    private JsonObject getGroupsResponseJson(final String groupName) {
        return createObjectBuilder()
                .add("groups", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("groupName", groupName)
                                .build()
                        )
                ).build();
    }


}
