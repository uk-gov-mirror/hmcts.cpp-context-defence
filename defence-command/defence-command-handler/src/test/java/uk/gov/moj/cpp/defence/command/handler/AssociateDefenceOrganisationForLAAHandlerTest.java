package uk.gov.moj.cpp.defence.command.handler;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.helper.EventStreamMockHelper.verifyAppendAndGetArgumentFrom;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMatcher.jsonEnvelope;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeMetadataMatcher.metadata;
import static uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopeStreamMatcher.streamContaining;

import uk.gov.justice.cps.defence.Permission;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory;
import uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopePayloadMatcher;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.aggregate.DefenceClient;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociated;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationDisassociated;
import uk.gov.moj.cpp.progression.command.RepresentationType;

import java.util.Arrays;
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
public class AssociateDefenceOrganisationForLAAHandlerTest {

    private static final String COMMAND_HANDLER_DEFENCE_ASSOCIATION_NAME_FOR_LAA = "defence.command.associate-defence-organisation-for-laa";
    private static final String ORGANISATION_NAME = "CompanyZ";
    private static final String LAA_CONTRACT_NUMEBR = "LAA1234";

    @Spy
    private final Enveloper enveloper = EnveloperFactory.createEnveloperWithEvents(
            DefenceOrganisationAssociated.class,
            DefenceOrganisationDisassociated.class);

    @Mock
    private EventSource eventSource;

    @Mock
    private EventStream eventStream;


    @Mock
    private AggregateService aggregateService;

    @InjectMocks
    private AssociateDefenceOrganisationForLAAHandler associateDefenceOrganisationForLAAHandler;

    @Test
    public void shouldHandleCommand() {
        assertThat(new AssociateDefenceOrganisationForLAAHandler(), isHandler(COMMAND_HANDLER)
                .with(method("handleAssociateDefenceOrganisationForLaa")
                        .thatHandles(COMMAND_HANDLER_DEFENCE_ASSOCIATION_NAME_FOR_LAA)
                ));
    }

    @Test
    public void shouldProcessCommandSucessfully() throws Exception {

        //Given
        final UUID userId = UUID.randomUUID();
        final String defendantId = UUID.randomUUID().toString();
        final String organisationId = UUID.randomUUID().toString();
        final JsonEnvelope envelope = createDefenceAssociationEnvelope(userId, defendantId, organisationId, ORGANISATION_NAME);
        final var aggregate = new DefenceAssociation();
        final var defenceClientAggregate = new DefenceClient();
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);
        when(aggregateService.get(eventStream, DefenceClient.class)).thenReturn(defenceClientAggregate);

        //When
        associateDefenceOrganisationForLAAHandler.handleAssociateDefenceOrganisationForLaa(envelope);

        //Then
        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        assertThat(envelopeStream, streamContaining(
                        jsonEnvelope(
                                metadata()
                                        .withName("defence.event.defence-organisation-associated"),
                                JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                        withJsonPath("$.organisationId", notNullValue()))
                                ))
                )
        );
    }

    @Test
    public void shouldNotProcessCommandSuccessfullyWhenOldAndNewOrganisationsAreTheSame() throws Exception {

        //Given
        final UUID userId = UUID.randomUUID();
        final String defendantId = UUID.randomUUID().toString();
        final UUID organisationId = UUID.randomUUID();
        final JsonEnvelope envelope = createDefenceAssociationEnvelope(userId, defendantId, organisationId.toString(), ORGANISATION_NAME);
        final var aggregate = new DefenceAssociation();
        final var defenceClientAggregate = new DefenceClient();
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);
        when(aggregateService.get(eventStream, DefenceClient.class)).thenReturn(defenceClientAggregate);

        aggregate.apply(DefenceOrganisationAssociated.defenceOrganisationAssociated().withOrganisationId(organisationId)
                .withPermissions(Arrays.asList(Permission.permission().build())).withIsLAA(true).build());


        //When
        associateDefenceOrganisationForLAAHandler.handleAssociateDefenceOrganisationForLaa(envelope);

        //Then
        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        assertThat(envelopeStream.count(), is(0L));
    }

    @Test
    public void shouldProcessCommandSuccessfullyWhenOldAndNewOrganisationsAreNotTheSame() throws Exception {

        //Given
        final UUID userId = UUID.randomUUID();
        final String defendantId = UUID.randomUUID().toString();
        final UUID oldOrganisationId = UUID.randomUUID();
        final UUID newOrganisationId = UUID.randomUUID();
        final JsonEnvelope envelope = createDefenceAssociationEnvelope(userId, defendantId, newOrganisationId.toString(), ORGANISATION_NAME);
        final var aggregate = new DefenceAssociation();
        final var defenceClientAggregate = new DefenceClient();
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);
        when(aggregateService.get(eventStream, DefenceClient.class)).thenReturn(defenceClientAggregate);

        aggregate.apply(DefenceOrganisationAssociated.defenceOrganisationAssociated().withOrganisationId(oldOrganisationId)
                .withPermissions(Arrays.asList(Permission.permission().build())).build());


        //When
        associateDefenceOrganisationForLAAHandler.handleAssociateDefenceOrganisationForLaa(envelope);

        //Then
        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);
        assertThat(envelopeStream, streamContaining(
                        jsonEnvelope(
                                metadata()
                                        .withName("defence.event.defence-organisation-disassociated"),
                                JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                        withJsonPath("$.organisationId", is(oldOrganisationId.toString())))
                                )),
                        jsonEnvelope(
                                metadata()
                                        .withName("defence.event.defence-organisation-associated"),
                                JsonEnvelopePayloadMatcher.payload().isJson(allOf(
                                        withJsonPath("$.organisationId", is(newOrganisationId.toString())))
                                ))
                )
        );
    }

    private JsonEnvelope createDefenceAssociationEnvelope(final UUID userId, final String defendantId, final String orgId, final String orgName) {
        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName(COMMAND_HANDLER_DEFENCE_ASSOCIATION_NAME_FOR_LAA)
                .withId(randomUUID())
                .withUserId(userId.toString())
                .build();
        final JsonObject payload = createObjectBuilder()
                .add("defendantId", defendantId)
                .add("organisationId", orgId)
                .add("organisationName", orgName)
                .add("representationType", RepresentationType.REPRESENTATION_ORDER.toString())
                .add("laaContractNumber", LAA_CONTRACT_NUMEBR)
                .build();

        return JsonEnvelope.envelopeFrom(metadata, payload);
    }
}
