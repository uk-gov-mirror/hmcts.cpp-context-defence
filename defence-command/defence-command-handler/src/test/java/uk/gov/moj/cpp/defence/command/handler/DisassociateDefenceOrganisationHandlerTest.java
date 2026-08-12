package uk.gov.moj.cpp.defence.command.handler;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.helper.EventStreamMockHelper.verifyAppendAndGetArgumentFrom;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMatcher.jsonEnvelope;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMetadataMatcher.metadata;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeStreamMatcher.streamContaining;
import static uk.gov.moj.cpp.defence.common.util.DefencePermission.VIEW_DEFENDANT_PERMISSION;

import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory;
import uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopePayloadMatcher;
import uk.gov.moj.cpp.defence.OrganisationDetails;
import uk.gov.moj.cpp.defence.UsergroupDetails;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.events.DefenceDisassociationFailed;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociated;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationDisassociated;
import uk.gov.moj.cpp.defence.events.Status;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.progression.command.DisassociateDefenceOrganisation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
 class DisassociateDefenceOrganisationHandlerTest {
    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Mock
    private JsonObject jsonObject;

    private static final String COMMAND_HANDLER_DEFENCE_DISASSOCIATION_NAME = "defence.command.disassociate-defence-organisation";
    private static final String ORGANISATION_NAME = "CompanyZ";
    private static final String LEGAL_ORGANISATION = "LEGAL_ORGANISATION";
    @Spy
    private final Enveloper enveloper = EnveloperFactory.createEnveloperWithEvents(
            DefenceOrganisationDisassociated.class,
            DefenceDisassociationFailed.class);
    @Mock
    private UserGroupService usersGroupService;
    @Mock
    private EventSource eventSource;
    @Mock
    private EventStream eventStream;
    @Mock
    private AggregateService aggregateService;
    @InjectMocks
    private DisassociateDefenceOrganisationHandler disassociateDefenceOrganisationHandler;

    @InjectMocks
    private AssociateDefenceOrganisationHandler associateDefenceOrganisationHandler;

    @Test
    void shouldHandleDisassociation() {
        assertThat(new DisassociateDefenceOrganisationHandler(), isHandler(COMMAND_HANDLER)
                .with(method("handleDisassociateDefenceOrganisation")
                        .thatHandles(COMMAND_HANDLER_DEFENCE_DISASSOCIATION_NAME)
                ));
    }


    @Test
     void shouldProcessCommandSuccessfullyForTheMatchingOrgIds() throws Exception {

        //Given
        final OrganisationDetails organisation = createOrganisation(randomUUID(), LEGAL_ORGANISATION);
        final var aggregate = new DefenceAssociation();
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);

        DefenceOrganisationAssociated defenceOrganisationAssociated = DefenceOrganisationAssociated.defenceOrganisationAssociated()
                .withOrganisationId(organisation.getId())
                .withDefendantId(randomUUID())
                .withPermissions(Arrays.asList(preparePermission(randomUUID(), randomUUID(), randomUUID(), Status.ADDED)))
                .build();
        aggregate.apply(defenceOrganisationAssociated);
        final Envelope<DisassociateDefenceOrganisation> envelope = prepareDisassociateCommandAndEnvelopeForTest(organisation);
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        //When
        disassociateDefenceOrganisationHandler.handleDisassociateDefenceOrganisation(envelope);
        //Then
        assertDisassociatedEvent();
    }

    @Test
     void shouldProcessCommandSuccessiveleyForTheOrgTypeHMCTS() throws Exception {
        //Given
        final OrganisationDetails organisation = createOrganisation(randomUUID(), LEGAL_ORGANISATION);
        final var aggregate = new DefenceAssociation();
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);

        final UUID defendantId = randomUUID();
        DefenceOrganisationAssociated defenceOrganisationAssociated = DefenceOrganisationAssociated.defenceOrganisationAssociated()
                .withOrganisationId(organisation.getId()).withDefendantId(defendantId)
                .withPermissions(Arrays.asList(preparePermission(randomUUID(), randomUUID(), randomUUID(), Status.ADDED)))
                .build();
        aggregate.apply(defenceOrganisationAssociated);
        final Envelope<DisassociateDefenceOrganisation> envelope = prepareDisassociateCommandAndEnvelopeForTestWithDifferingOrgIdsOrgTypeHMCTS(organisation);
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        //When
        disassociateDefenceOrganisationHandler.handleDisassociateDefenceOrganisation(envelope);

        //Then
        assertDissassociationFailedEvent(envelope.payload().getOrganisationId().toString());
    }

    @Test
     void shouldProcessCommandSuccessiveleyForTheSystemUser() throws Exception {
        //Given
        final var aggregate = new DefenceAssociation();
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);
        final OrganisationDetails organisation = createOrganisation(randomUUID(), LEGAL_ORGANISATION);
        DefenceOrganisationAssociated defenceOrganisationAssociated = DefenceOrganisationAssociated.defenceOrganisationAssociated().withOrganisationId(organisation.getId())
                .withDefendantId(randomUUID())
                .withPermissions(Arrays.asList(preparePermission(randomUUID(), randomUUID(), randomUUID(), Status.ADDED)))
                .build();
        aggregate.apply(defenceOrganisationAssociated);

        final Envelope<DisassociateDefenceOrganisation> envelope = prepareDisassociateCommandAndEnvelopeForTestWithDifferingOrgIdsOrgTypeHMCTS(organisation);
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        //When
        disassociateDefenceOrganisationHandler.handleDisassociateDefenceOrganisation(envelope);
        //Then
        assertDissassociationFailedEvent(envelope.payload().getOrganisationId().toString());
    }


    @Test
     void shouldProcessCommandNegativelyForTheOrgTypeNonHMCTS() throws Exception {
        final var aggregate = new DefenceAssociation();
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);

        //Given
        final Envelope<DisassociateDefenceOrganisation> envelope = prepareDisassociateCommandAndEnvelopeForTestForDifferingOrgIdsOrgTypeNonHMCTS();
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        //When
        disassociateDefenceOrganisationHandler.handleDisassociateDefenceOrganisation(envelope);

        assertHandlerResultWithFailure();
    }

    @Test
     void shouldProcessCommandNegativelyForAHMCTSUserWithAnOrganisationID() throws Exception {
        //Given
        final Envelope<DisassociateDefenceOrganisation> envelope = prepareDisassociateCommandAndEnvelopeForTestForHMCTSUserWithAnErrorUserGroup();
        final var aggregate = new DefenceAssociation();
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        //When
        disassociateDefenceOrganisationHandler.handleDisassociateDefenceOrganisation(envelope);

        assertHandlerResultWithFailure();
    }

    private Permission preparePermission(final UUID permissionId, final UUID defendantId, final UUID organisationId, final Status status) {

        return Permission.permission()
                .withId(permissionId)
                .withTarget(defendantId)
                .withObject(VIEW_DEFENDANT_PERMISSION.getObjectType())
                .withAction(VIEW_DEFENDANT_PERMISSION.getActionType())
                .withSource(organisationId)
                .withStatus(status)
                .build();
    }

    private Envelope<DisassociateDefenceOrganisation> prepareDisassociateCommandAndEnvelopeForTest(final OrganisationDetails organisationDetails) {

        final UUID userId = UUID.randomUUID();
        final DisassociateDefenceOrganisation disassociateDefenceOrganisation
                = generateDisassociateDefenceOrganisationCommand(organisationDetails.getId());
        final Envelope<DisassociateDefenceOrganisation> envelope
                = createDefenceDisassociationEnvelope(userId, disassociateDefenceOrganisation);
        when(usersGroupService.getUserOrgDetails(any(), any())).thenReturn(organisationDetails);
        return envelope;
    }

    private Envelope<DisassociateDefenceOrganisation> prepareDisassociateCommandAndEnvelopeForTestWithDifferingOrgIdsOrgTypeHMCTS(final OrganisationDetails organisationDetails) {

        final UUID userId = UUID.randomUUID();
        final DisassociateDefenceOrganisation disassociateDefenceOrganisation
                = generateDisassociateDefenceOrganisationCommand(UUID.randomUUID());
        final Envelope<DisassociateDefenceOrganisation> envelope
                = createDefenceDisassociationEnvelope(userId, disassociateDefenceOrganisation);
        when(usersGroupService.getUserOrgDetails(any(), any())).thenReturn(organisationDetails);
        when(usersGroupService.getUserGroupsForUser(any(), any())).thenReturn(createSystemUserGroupDetails());
        return envelope;
    }

    private Envelope<DisassociateDefenceOrganisation> prepareDisassociateCommandAndEnvelopeForTestForDifferingOrgIdsOrgTypeNonHMCTS() {

        final UUID userId = UUID.randomUUID();
        final OrganisationDetails organisationDetails = OrganisationDetails.organisationDetails().build();
        final DisassociateDefenceOrganisation disassociateDefenceOrganisation
                = generateDisassociateDefenceOrganisationCommand(UUID.randomUUID());
        final Envelope<DisassociateDefenceOrganisation> envelope
                = createDefenceDisassociationEnvelope(userId, disassociateDefenceOrganisation);
        when(usersGroupService.getUserOrgDetails(any(), any())).thenReturn(organisationDetails);
        return envelope;
    }

    private Envelope<DisassociateDefenceOrganisation> prepareDisassociateCommandAndEnvelopeForTestForHMCTSUserWithAnErrorUserGroup() {

        final UUID userId = UUID.randomUUID();
        final OrganisationDetails organisationDetails = createOrganisation(UUID.randomUUID(), "HMCTS");
        final DisassociateDefenceOrganisation disassociateDefenceOrganisation
                = generateDisassociateDefenceOrganisationCommand(UUID.randomUUID());
        final Envelope<DisassociateDefenceOrganisation> envelope
                = createDefenceDisassociationEnvelope(userId, disassociateDefenceOrganisation);
        when(usersGroupService.getUserOrgDetails(any(), any())).thenReturn(organisationDetails);
        when(usersGroupService.getUserGroupsForUser(any(), any())).thenReturn(createDefenceUserGroupDetails());
        return envelope;
    }

    private Envelope<DisassociateDefenceOrganisation> createDefenceDisassociationEnvelope(final UUID userId,
                                                                                          final DisassociateDefenceOrganisation disassociateDefenceOrganisation) {
        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName(COMMAND_HANDLER_DEFENCE_DISASSOCIATION_NAME)
                .withId(randomUUID())
                .withUserId(userId.toString())
                .build();

        return envelopeFrom(metadata, disassociateDefenceOrganisation);
    }

    private DisassociateDefenceOrganisation generateDisassociateDefenceOrganisationCommand(final UUID orgId) {
        return DisassociateDefenceOrganisation.disassociateDefenceOrganisation()
                .withDefendantId(randomUUID())
                .withOrganisationId(orgId)
                .build();
    }

    private OrganisationDetails createOrganisation(final UUID orgId, final String organisationType) {
        return OrganisationDetails.organisationDetails()
                .withId(orgId)
                .withName(ORGANISATION_NAME)
                .withType(organisationType)
                .build();
    }

    private void assertDisassociatedEvent() throws EventStreamException {
        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        assertThat(envelopeStream, streamContaining(
                        jsonEnvelope(
                                metadata()
                                        .withName("defence.event.defence-organisation-disassociated"),
                                JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                                withJsonPath("$.organisationId", notNullValue())
                                        )
                                ))

                )
        );
    }

    private void assertDissassociationFailedEvent(String organisationId) throws EventStreamException {
        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        assertThat(envelopeStream, streamContaining(
                jsonEnvelope(
                        metadata()
                                .withName("defence.event.defence-disassociation-failed"),
                        JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                withJsonPath("$.failureReason", equalTo(String.format("Organisation id '%s' is not currently associated with Defence client", organisationId))
                                )
                        ))

                )
        ));
    }

    private void assertHandlerResultWithFailure() throws EventStreamException {
        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        assertThat(envelopeStream, streamContaining(
                        jsonEnvelope(
                                metadata()
                                        .withName("defence.event.defence-disassociation-failed"),
                                JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                        withJsonPath("$.defendantId", notNullValue()))
                                ))

                )
        );
    }

    private List<UsergroupDetails> createDefenceUserGroupDetails() {
        UsergroupDetails userGroupDefenceUsers = UsergroupDetails.usergroupDetails().withGroupId(UUID.fromString("7e2f143e-d619-40b3-8611-8015f3a18957")).withGroupName("Defence Lawyers").build();
        List<UsergroupDetails> destinedUserGroups = new ArrayList<>();
        destinedUserGroups.add(userGroupDefenceUsers);
        return destinedUserGroups;
    }

    private List<UsergroupDetails> createSystemUserGroupDetails() {
        UsergroupDetails userSystemUsers = UsergroupDetails.usergroupDetails().withGroupId(UUID.fromString("7e2f143e-d619-40b3-8611-8015f3a18957")).withGroupName("System Users").build();
        List<UsergroupDetails> destinedUserGroups = new ArrayList<>();
        destinedUserGroups.add(userSystemUsers);
        return destinedUserGroups;
    }
}