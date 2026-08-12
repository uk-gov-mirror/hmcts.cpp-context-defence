package uk.gov.moj.cpp.defence.command.handler;


import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.helper.EventStreamMockHelper.verifyAppendAndGetArgumentFrom;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMatcher.jsonEnvelope;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMetadataMatcher.metadata;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeStreamMatcher.streamContaining;
import static uk.gov.moj.cpp.defence.common.util.UserGroupTypes.CHAMBERS_ADMIN;
import static uk.gov.moj.cpp.defence.common.util.UserGroupTypes.DEFENCE_LAWYERS;

import uk.gov.justice.cps.defence.AssigneeForDefenceIsProsecutingCase;
import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.cps.defence.UserNotFound;
import uk.gov.justice.cps.defence.events.AccessGrantRemoved;
import uk.gov.justice.cps.defence.events.AccessGranted;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory;
import uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopePayloadMatcher;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.aggregate.DefenceClient;
import uk.gov.moj.cpp.defence.commands.GrantDefenceAccess;
import uk.gov.moj.cpp.defence.commands.RemoveAllGrantDefenceAccess;
import uk.gov.moj.cpp.defence.commands.RemoveGrantDefenceAccess;
import uk.gov.moj.cpp.defence.common.util.GrantAccessUtil;
import uk.gov.moj.cpp.defence.events.RepresentationType;
import uk.gov.moj.cpp.defence.service.DefenceService;
import uk.gov.moj.cpp.defence.service.UserGroupService;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GrantAccessCommandHandlerTest {
    private static final String COMMAND_HANDLER_GRANT_ACCESS_NAME = "defence.command.grant-defence-access";
    private static final String COMMAND_HANDLER_REMOVE_GRANT_ACCESS_NAME = "defence.command.remove-grant-defence-access";
    private static final String COMMAND_HANDLER_REMOVE_ALL_GRANT_ACCESS_NAME = "defence.command.remove-all-grant-defence-access";
    private static final String ORGANISATION_NAME = "CompanyZ";
    private static final String EMAIL_ID = "email@hmcts.net";
    private static final UUID DEFENCE_CLIENT_ID = randomUUID();
    private static final UUID USER_ID = randomUUID();
    @Spy
    private final Enveloper enveloper = EnveloperFactory.createEnveloperWithEvents(
            AccessGranted.class, UserNotFound.class, AccessGrantRemoved.class, AssigneeForDefenceIsProsecutingCase.class);
    @Mock
    DefenceAssociation defenceAssociationAggregate;
    @Mock
    private UserGroupService usersGroupService;
    @Mock
    private DefenceService defenceService;
    @Mock
    private EventSource eventSource;
    @Mock
    private EventStream eventStream;
    @Mock
    private AggregateService aggregateService;
    @InjectMocks
    private GrantAccessCommandHandler grantAccessCommandHandler;
    private DefenceClient aggregate;


    @BeforeEach
    public void setup() {
        aggregate = new DefenceClient();
        defenceAssociationAggregate = new DefenceAssociation();
        defenceAssociationAggregate.associateOrganisation(DEFENCE_CLIENT_ID, randomUUID(), ORGANISATION_NAME, RepresentationType.PRIVATE.toString(), "", USER_ID.toString());
    }

    @Test
    public void shouldHandleCommandGrantAccess() {
        assertThat(new GrantAccessCommandHandler(), isHandler(COMMAND_HANDLER)
                .with(method("receiveGrantDefenceAccess")
                        .thatHandles(COMMAND_HANDLER_GRANT_ACCESS_NAME)
                ));
    }

    @Test
    public void shouldHandleCommandRemoveAccess() {
        assertThat(new GrantAccessCommandHandler(), isHandler(COMMAND_HANDLER)
                .with(method("receiveRemoveGrantDefenceAccess")
                        .thatHandles(COMMAND_HANDLER_REMOVE_GRANT_ACCESS_NAME)
                ));
    }

    @Test
    public void shouldHandleCommandRemoveAllAccess() {
        assertThat(new GrantAccessCommandHandler(), isHandler(COMMAND_HANDLER)
                .with(method("receiveRemoveAllGrantDefenceAccess")
                        .thatHandles(COMMAND_HANDLER_REMOVE_ALL_GRANT_ACCESS_NAME)
                ));
    }

    @Test
    public void shouldReceiveGrantAccessSucessfully() throws Exception {

        final UUID userId = UUID.randomUUID();
        final UUID granteeUserId = UUID.randomUUID();
        final Envelope<GrantDefenceAccess> envelope = createGrantAccessEnvelope(userId);

        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceClient.class)).thenReturn(aggregate);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(defenceAssociationAggregate);
        when(usersGroupService.getUserDetailsWithEmail(eq(EMAIL_ID), any(), any())).thenReturn(createPersonDetails(userId));
        when(usersGroupService.getGroupNamesForUser(eq(userId), any(), any())).thenReturn(getGroupNames());
        when(usersGroupService.getUserDetailsWithUserId(eq(userId), any(), any())).thenReturn(createGranterDetails(userId));
        when(usersGroupService.getOrganisationDetailsForUser(eq(userId), any(), any())).thenReturn(createOrganisation(userId));

        grantAccessCommandHandler.receiveGrantDefenceAccess(envelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        assertThat(envelopeStream, streamContaining(
                jsonEnvelope(
                        metadata()
                                .withName("defence.event.access-granted"),
                        JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                        withJsonPath("$.permissions[0].status", is("Added")),
                                        withJsonPath("$.granteeDetails.firstName", is("grantee_name")),
                                        withJsonPath("$.granterDetails.firstName", is("granter_name")),
                                        withJsonPath("$.granterDetails.firstName", is("granter_name")),
                                        withJsonPath("$.granteeOrganisation.organisationName", is("CompanyZ"))
                                )
                        ))
        ));
    }

    @Test
    public void shouldRaiseAssigneeForDefenceIsProsecutingCaseEvent() throws Exception {
        final UUID userId = UUID.randomUUID();
        final UUID granteeUserId = UUID.randomUUID();
        final Envelope<GrantDefenceAccess> envelope = createGrantAccessEnvelope(userId);

        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceClient.class)).thenReturn(aggregate);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(defenceAssociationAggregate);
        when(usersGroupService.getUserDetailsWithEmail(eq(EMAIL_ID), any(), any())).thenReturn(createPersonDetails(granteeUserId));
        when(usersGroupService.getGroupNamesForUser(eq(userId), any(), any())).thenReturn(getGroupNames());
        when(usersGroupService.getGroupNamesForUser(eq(granteeUserId), any(), any())).thenReturn(getGroupNames());
        when(usersGroupService.getUserDetailsWithUserId(eq(userId), any(), any())).thenReturn(createGranterDetails(userId));
        when(usersGroupService.getOrganisationDetailsForUser(eq(userId), any(), any())).thenReturn(createOrganisation(userId));
        when(usersGroupService.getOrganisationDetailsForUser(eq(granteeUserId), any(), any())).thenReturn(createOrganisation(granteeUserId));
        when(defenceService.isInProsecutorRole(any(), any(), any())).thenReturn(true);

        grantAccessCommandHandler.receiveGrantDefenceAccess(envelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        assertThat(envelopeStream, streamContaining(
                jsonEnvelope(
                        metadata()
                                .withName("defence.event.assignee-for-defence-is-prosecuting-case"),
                        JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                        withJsonPath("$.userId", is(granteeUserId.toString())),
                                        withJsonPath("$.email", is(EMAIL_ID))
                                )
                        ))
        ));
    }

    @Test
    public void shouldReceiveUserNotFoundWhenInvalidGranteeDetails() throws Exception {
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceClient.class)).thenReturn(aggregate);

        final UUID userId = UUID.randomUUID();
        final Envelope<GrantDefenceAccess> envelope = createGrantAccessEnvelope(userId);

        grantAccessCommandHandler.receiveGrantDefenceAccess(envelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        assertThat(envelopeStream, streamContaining(
                jsonEnvelope(
                        metadata()
                                .withName("defence.event.user-not-found"),
                        JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                withJsonPath("$.failureReason", is("User not found for the given e-mail.")))
                        ))
        ));
    }

    @Test
    public void shouldRemoveAccessSuccessfullyForDefenceLawyers() throws Exception {

        final Envelope<RemoveGrantDefenceAccess> envelope = createRemoveAccessEnvelope(USER_ID);
        final List<UUID> userIdList = Arrays.asList(USER_ID);
        grantAccess(userIdList, DEFENCE_LAWYERS.getRoleName());
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceClient.class)).thenReturn(aggregate);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(defenceAssociationAggregate);
        when(usersGroupService.getGroupNamesForUser(eq(USER_ID), any(), any())).thenReturn(getGroupNames());
        when(usersGroupService.getOrganisationDetailsForUser(eq(USER_ID), any(), any())).thenReturn(createOrganisation(USER_ID));

        grantAccessCommandHandler.receiveRemoveGrantDefenceAccess(envelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        final List<Permission> permissions = preparePermissionListForAssociation(USER_ID);
        assertThat(envelopeStream, streamContaining(
                jsonEnvelope(
                        metadata()
                                .withName("defence.event.access-grant-removed"),
                        JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                        withJsonPath("$.permissions.size()", is(3)),
                                        withJsonPath("$.permissions[0].source", is(permissions.get(0).getSource().toString())),
                                        withJsonPath("$.permissions[0].status", is(permissions.get(0).getStatus().toString())),
                                        withJsonPath("$.permissions[0].action", is(permissions.get(0).getAction())),
                                        withJsonPath("$.permissions[1].source", is(permissions.get(1).getSource().toString())),
                                        withJsonPath("$.permissions[1].status", is(permissions.get(1).getStatus().toString())),
                                        withJsonPath("$.permissions[1].action", is(permissions.get(1).getAction())),
                                        withJsonPath("$.permissions[2].source", is(permissions.get(2).getSource().toString())),
                                        withJsonPath("$.permissions[2].status", is(permissions.get(2).getStatus().toString())),
                                        withJsonPath("$.permissions[2].action", is(permissions.get(2).getAction()))
                                )
                        ))
        ));
    }

    @Test
    public void shouldRemoveAccessSuccessfullyForChambersAdmin() throws Exception {

        final Envelope<RemoveGrantDefenceAccess> envelope = createRemoveAccessEnvelope(USER_ID);
        final List<UUID> userIdList = Arrays.asList(USER_ID);
        grantAccess(userIdList, CHAMBERS_ADMIN.getRoleName());
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceClient.class)).thenReturn(aggregate);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(defenceAssociationAggregate);
        when(usersGroupService.getGroupNamesForUser(eq(USER_ID), any(), any())).thenReturn(getGroupNames());
        when(usersGroupService.getOrganisationDetailsForUser(eq(USER_ID), any(), any())).thenReturn(createOrganisation(USER_ID));

        grantAccessCommandHandler.receiveRemoveGrantDefenceAccess(envelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        final List<Permission> permissions = preparePermissionListForNonAssociation(USER_ID);
        assertThat(envelopeStream, streamContaining(
                jsonEnvelope(
                        metadata()
                                .withName("defence.event.access-grant-removed"),
                        JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                        withJsonPath("$.permissions.size()", is(1)),
                                        withJsonPath("$.permissions[0].source", is(permissions.get(0).getSource().toString())),
                                        withJsonPath("$.permissions[0].status", is(permissions.get(0).getStatus().toString())),
                                        withJsonPath("$.permissions[0].action", is(permissions.get(0).getAction()))
                                )
                        ))
        ));
    }

    @Test
    public void shouldRemoveAllAccessSuccessfully() throws Exception {
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceClient.class)).thenReturn(aggregate);

        final UUID userId = UUID.randomUUID();
        final Envelope<RemoveAllGrantDefenceAccess> envelope = createRemoveAllAccessEnvelope(userId);
        final List<UUID> userIdList = Arrays.asList(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        grantAccess(userIdList, DEFENCE_LAWYERS.getRoleName());

        grantAccessCommandHandler.receiveRemoveAllGrantDefenceAccess(envelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);

        final List<JsonEnvelope> eventList = envelopeStream.collect(Collectors.toList());
        assertThat(eventList.size(), is(userIdList.size()));
        eventList.forEach(
                event -> {
                    assertThat(event.metadata().name(), is("defence.event.access-grant-removed"));
                    final JsonObject jsonResponse = new StringToJsonObjectConverter().convert(event.payload().toString());
                    assertThat(jsonResponse.getJsonArray("permissions").getJsonObject(0).getString("source"), anyOf(is(userIdList.get(0).toString()), is(userIdList.get(1).toString()), is(userIdList.get(2).toString()), is(userIdList.get(3).toString())));
                }
        );
    }


    private List<String> getGroupNames() {
        return Arrays.asList("Chambers Admin", "System Users");
    }

    private PersonDetails createPersonDetails(final UUID userId) {
        return PersonDetails.personDetails()
                .withFirstName("grantee_name")
                .withLastName("grantee_lastname")
                .withUserId(userId)
                .build();
    }

    private PersonDetails createGranterDetails(final UUID userId) {
        return PersonDetails.personDetails()
                .withFirstName("granter_name")
                .withLastName("granter_lastname")
                .withUserId(userId)
                .build();
    }

    private Organisation createOrganisation(final UUID orgId) {
        return Organisation.organisation().withOrgId(orgId).withOrganisationName(ORGANISATION_NAME).build();
    }

    private Envelope<GrantDefenceAccess> createGrantAccessEnvelope(final UUID userId) {
        GrantDefenceAccess grantDefenceAccess = GrantDefenceAccess.grantDefenceAccess()
                .withDefenceClientId(randomUUID())
                .withGranteeEmailId(EMAIL_ID + " ")
                .build();
        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName(COMMAND_HANDLER_GRANT_ACCESS_NAME)
                .withId(randomUUID())
                .withUserId(userId.toString())
                .build();
        return envelopeFrom(metadata, grantDefenceAccess);
    }

    private Envelope<RemoveGrantDefenceAccess> createRemoveAccessEnvelope(final UUID userId) {
        RemoveGrantDefenceAccess removeGrantDefenceAccess = RemoveGrantDefenceAccess.removeGrantDefenceAccess()
                .withDefenceClientId(randomUUID())
                .withGranteeUserId(userId)
                .build();
        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName(COMMAND_HANDLER_REMOVE_GRANT_ACCESS_NAME)
                .withId(randomUUID())
                .withUserId(userId.toString())
                .build();
        return envelopeFrom(metadata, removeGrantDefenceAccess);
    }

    private Envelope<RemoveAllGrantDefenceAccess> createRemoveAllAccessEnvelope(final UUID userId) {
        RemoveAllGrantDefenceAccess removeGrantDefenceAccess = RemoveAllGrantDefenceAccess.removeAllGrantDefenceAccess()
                .withDefenceClientId(randomUUID())
                .build();
        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName(COMMAND_HANDLER_REMOVE_ALL_GRANT_ACCESS_NAME)
                .withId(randomUUID())
                .withUserId(userId.toString())
                .build();
        return envelopeFrom(metadata, removeGrantDefenceAccess);
    }

    private void grantAccess(List<UUID> userIds, final String granteeGroupName) {
        final List<String> granteeGroupList = Arrays.asList(granteeGroupName);
        userIds.forEach(userId ->
                {
                    final PersonDetails granteeDetails = createPersonDetails(userId);
                    final UUID associatedOrganisationId = UUID.randomUUID();
                    final Organisation granteeOrganisation = Organisation.organisation().withOrgId(UUID.randomUUID()).build();
                    final Organisation granterOrganisation = Organisation.organisation().withOrgId(associatedOrganisationId).build();
                    final PersonDetails granterDetails = createGranterDetails(userId);
                    aggregate.grantAccessToUser(DEFENCE_CLIENT_ID, EMAIL_ID, granteeDetails, granteeGroupList, null, granteeOrganisation, granterOrganisation, granterDetails, associatedOrganisationId, false);

                }
        );

    }

    private static List<Permission> preparePermissionListForAssociation(final UUID source) {
        return GrantAccessUtil.preparePermissionList(UUID.randomUUID(), source, true, Optional.empty());
    }

    private static List<Permission> preparePermissionListForNonAssociation(final UUID source) {
        return GrantAccessUtil.preparePermissionList(UUID.randomUUID(), source, false, of(Arrays.asList(CHAMBERS_ADMIN.getRoleName())));
    }

}
