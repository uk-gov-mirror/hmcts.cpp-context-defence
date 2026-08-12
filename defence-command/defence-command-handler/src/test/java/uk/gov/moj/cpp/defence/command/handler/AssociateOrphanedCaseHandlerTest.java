package uk.gov.moj.cpp.defence.command.handler;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.helper.EventStreamMockHelper.verifyAppendAndGetArgumentFrom;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;

import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociated;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationDisassociated;
import uk.gov.moj.cpp.defence.events.DefendantLaaAssociated;

import java.util.Optional;
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
public class AssociateOrphanedCaseHandlerTest {

    private static final UUID defendantId = randomUUID();
    private static final String organisationId = randomUUID().toString();
    private static final String laaContractNumber = "LAA1234";
    private static final String organisationName = "Greg Inc";

    private static final String LAA_CONTRACT_NUMBER = "laaContractNumber";
    private static final String ORGANISATION_ID = "organisationId";
    private static final String ORGANISATION_NAME = "organisationName";
    private static final String DEFENDANT_ID = "defendantId";

    @Mock
    private EventSource eventSource;

    @Mock
    private EventStream eventStream;

    @Mock
    private AggregateService aggregateService;

    @Spy
    private final Enveloper enveloper = EnveloperFactory.createEnveloperWithEvents(
            DefenceOrganisationDisassociated.class,
            DefenceOrganisationAssociated.class,
            DefendantLaaAssociated.class);

    @InjectMocks
    private AssociateOrphanedCaseHandler associateOrphanedCaseHandler;

    @Test
    public void shouldHandleCommand() {
        assertThat(new AssociateOrphanedCaseHandler(), isHandler(COMMAND_HANDLER)
                .with(method("handleAssociateOrphanedCase")
                        .thatHandles("defence.command.handler.associate-orphaned-case")
                ));
    }

    @Test
    public void shouldProcessCommand_whenOrganisationIsSetupAndNoAssoicatedOrgExist_expectAssociationEvent() throws EventStreamException {
        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder().withUserId(randomUUID().toString()).withId(randomUUID()).withName("defence.command.handler.associate-orphaned-case").build(),
                createPayloadForOrphanedDefendantAssociation());
        final var aggregate = new DefenceAssociation();
        when(eventSource.getStreamById(any())).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(aggregate);
        associateOrphanedCaseHandler.handleAssociateOrphanedCase(jsonEnvelope);

        final Stream<JsonEnvelope> envelopeStream = verifyAppendAndGetArgumentFrom(eventStream);

        Optional<JsonEnvelope> associatedOrDisAssociatedEnvelope = envelopeStream.filter
                        (a -> a.metadata().name().equals("defence.event.defence-organisation-associated") || a.metadata().name().equals("defence.event.defendant-laa-associated"))
                .findAny();

        assertThat(associatedOrDisAssociatedEnvelope.isPresent(), is(true));
    }


    private static JsonObject createPayloadForOrphanedDefendantAssociation() {
        return createObjectBuilder()
                .add(DEFENDANT_ID, defendantId.toString())
                .add(ORGANISATION_ID, organisationId)
                .add(LAA_CONTRACT_NUMBER, laaContractNumber)
                .add(ORGANISATION_NAME, organisationName)
                .build();

    }

}
