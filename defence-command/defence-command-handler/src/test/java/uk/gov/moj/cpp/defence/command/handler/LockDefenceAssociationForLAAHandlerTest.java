package uk.gov.moj.cpp.defence.command.handler;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.helper.EventStreamMockHelper.verifyAppendAndGetArgumentFrom;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMatcher.jsonEnvelope;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMetadataMatcher.metadata;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeStreamMatcher.streamContaining;

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
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.events.DefenceAssociationFailed;
import uk.gov.moj.cpp.defence.events.DefendantDefenceAssociationLockedForLaa;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.progression.command.AssociateDefenceOrganisation;
import uk.gov.moj.cpp.progression.command.RepresentationType;

import java.util.Optional;
import java.util.UUID;
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
class LockDefenceAssociationForLAAHandlerTest {
    @Mock
    private EventSource eventSource;

    @Mock
    private EventStream lockEventStream;

    @Mock
    private EventStream associationEventStream;

    @Mock
    private AggregateService aggregateService;

    @Mock
    private UserGroupService usersGroupService;

    @Spy
    private final Enveloper enveloper = EnveloperFactory.createEnveloperWithEvents(
            DefendantDefenceAssociationLockedForLaa.class, DefenceAssociationFailed.class);

    @InjectMocks
    private LockDefenceAssociationForLAAHandler lockDefenceAssociationForLAAHandler;

    @InjectMocks
    private AssociateDefenceOrganisationHandler associateDefenceOrganisationHandler;

    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Mock
    private JsonObject jsonObject;

    private DefenceAssociation aggregate;


    private static final UUID defendantId = randomUUID();
    private static final String LAACONTRACTNUMBER = "ABC123";


    private static final String DEFENDANT_ID = "defendantId";

    private static final String LAA_CONTRACT_NUMBER = "laaContractNumber";

    @BeforeEach
    public void setup() {
        aggregate = new DefenceAssociation();

    }

    @Test
    void shouldHandleCommand() {
        assertThat(new LockDefenceAssociationForLAAHandler(), isHandler(COMMAND_HANDLER)
                .with(method("handleLockDefenceAssociationForLaa")
                        .thatHandles("defence.command.handler.lock-defence-association-for-laa")
                ));
    }

    @Test
    void shouldProcessCommand_whenCaseLockedForDefendant_AssociationShouldFail() throws EventStreamException {
        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder().withUserId(randomUUID().toString()).withId(randomUUID()).withName("defence.command.handler.lock-defence-association-for-laa").build(),
                createPayloadForLockDefendantAssociation());

        when(eventSource.getStreamById(any())).thenReturn(lockEventStream);
        when(aggregateService.get(lockEventStream, DefenceAssociation.class)).thenReturn(aggregate);

        lockDefenceAssociationForLAAHandler.handleLockDefenceAssociationForLaa(jsonEnvelope);

        Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(lockEventStream);

        Optional<JsonEnvelope> associatedOrDisAssociatedEnvelope = envelopeStream.filter
                        (a -> a.metadata().name().equals("defence.event.defendant-defence-association-locked-for-laa"))
                .findAny();

        assertTrue(associatedOrDisAssociatedEnvelope.isPresent());

        final UUID userId = UUID.randomUUID();
        final OrganisationDetails organisationDetails = createOrganisation(UUID.randomUUID());
        final AssociateDefenceOrganisation associateDefenceOrganisation
                = generateAssociateDefenceOrganisationCommand();
        final Envelope<AssociateDefenceOrganisation> envelope = createDefenceAssociationEnvelope(userId, associateDefenceOrganisation);
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        when(usersGroupService.getUserOrgDetails(any(), any())).thenReturn(organisationDetails);
        when(eventSource.getStreamById(any())).thenReturn(associationEventStream);
        when(aggregateService.get(associationEventStream, DefenceAssociation.class)).thenReturn(aggregate);

        associateDefenceOrganisationHandler.handleAssociateDefenceOrganisation(envelope);

        //Then
        envelopeStream = verifyAppendAndGetArgumentFrom(associationEventStream);
        assertThat(envelopeStream, streamContaining(
                        jsonEnvelope(
                                metadata()
                                        .withName("defence.event.defence-association-failed"),
                                JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                        withJsonPath("$.defendantId", notNullValue()))
                                ))
                )
        );
    }

    private OrganisationDetails createOrganisation(final UUID orgId) {
        return OrganisationDetails.organisationDetails().withId(orgId).withName("ORGANISATION_NAME").build();
    }

    private AssociateDefenceOrganisation generateAssociateDefenceOrganisationCommand() {
        return AssociateDefenceOrganisation.associateDefenceOrganisation()
                .withDefendantId(randomUUID())
                .withRepresentationType(RepresentationType.PRIVATE)
                .build();
    }

    private Envelope<AssociateDefenceOrganisation> createDefenceAssociationEnvelope(final UUID userId, final AssociateDefenceOrganisation associateDefenceOrganisation) {
        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName("defence.command.associate-defence-organisation")
                .withId(randomUUID())
                .withUserId(userId.toString())
                .build();

        return envelopeFrom(metadata, associateDefenceOrganisation);
    }


    private static JsonObject createPayloadForLockDefendantAssociation() {
        return createObjectBuilder()
                .add(DEFENDANT_ID, defendantId.toString())
                .add(LAA_CONTRACT_NUMBER, LAACONTRACTNUMBER)
                .build();

    }

}
