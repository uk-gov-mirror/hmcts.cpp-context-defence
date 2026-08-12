package uk.gov.moj.cpp.defence.command.handler;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
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

import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory;
import uk.gov.justice.services.test.utils.core.matchers.JsonEnvelopePayloadMatcher;
import uk.gov.moj.cpp.defence.OrganisationDetails;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociated;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.progression.command.AssociateDefenceOrganisation;
import uk.gov.moj.cpp.progression.command.RepresentationType;

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
class AssociateDefenceOrganisationHandlerTest {

    private static final String COMMAND_HANDLER_DEFENCE_ASSOCIATION_NAME = "defence.command.associate-defence-organisation";
    private static final String ORGANISATION_NAME = "CompanyZ";

    @Spy
    private final Enveloper enveloper = EnveloperFactory.createEnveloperWithEvents(
            DefenceOrganisationAssociated.class);

    @Mock
    private UserGroupService usersGroupService;

    @Mock
    private EventSource eventSource;

    @Mock
    private EventStream eventStream;

    @Mock
    private Requester requester;

    @Mock
    private AggregateService aggregateService;

    @InjectMocks
    private AssociateDefenceOrganisationHandler associateDefenceOrganisationHandler;

    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Mock
    private JsonObject jsonObject;

    @Test
    void shouldHandleCommand() {
        assertThat(new AssociateDefenceOrganisationHandler(), isHandler(COMMAND_HANDLER)
                .with(method("handleAssociateDefenceOrganisation")
                        .thatHandles(COMMAND_HANDLER_DEFENCE_ASSOCIATION_NAME)
                ));
    }

    @Test
     void shouldProcessCommandSucessfully() throws Exception {

        //Given
        final UUID userId = UUID.randomUUID();
        final OrganisationDetails organisation = createOrganisation(UUID.randomUUID());
        final AssociateDefenceOrganisation associateDefenceOrganisation
                = generateAssociateDefenceOrganisationCommand();
        final Envelope<AssociateDefenceOrganisation> envelope = createDefenceAssociationEnvelope(userId, associateDefenceOrganisation);
        final var aggregate = new DefenceAssociation();
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);
        when(usersGroupService.getUserOrgDetails(any(), any())).thenReturn(organisation);

        //When
        associateDefenceOrganisationHandler.handleAssociateDefenceOrganisation(envelope);

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

    private OrganisationDetails createOrganisation(final UUID orgId) {
        return OrganisationDetails.organisationDetails().withId(orgId).withName(ORGANISATION_NAME).build();
    }

    private Envelope<AssociateDefenceOrganisation> createDefenceAssociationEnvelope(final UUID userId, final AssociateDefenceOrganisation associateDefenceOrganisation) {
        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName(COMMAND_HANDLER_DEFENCE_ASSOCIATION_NAME)
                .withId(randomUUID())
                .withUserId(userId.toString())
                .build();

        return envelopeFrom(metadata, associateDefenceOrganisation);
    }

    private AssociateDefenceOrganisation generateAssociateDefenceOrganisationCommand() {
        return AssociateDefenceOrganisation.associateDefenceOrganisation()
                .withDefendantId(randomUUID())
                .withRepresentationType(RepresentationType.PRIVATE)
                .build();
    }

}