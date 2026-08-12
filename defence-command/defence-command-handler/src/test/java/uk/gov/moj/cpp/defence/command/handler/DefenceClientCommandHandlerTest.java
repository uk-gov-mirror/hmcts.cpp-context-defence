package uk.gov.moj.cpp.defence.command.handler;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.cps.defence.DefendantDetails.defendantDetails;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_HANDLER;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.enveloper.EnveloperFactory.createEnveloperWithEvents;
import static uk.gov.justice.services.test.utils.core.helper.EventStreamMockHelper.verifyAppendAndGetArgumentFrom;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMatcher.isHandler;
import static uk.gov.justice.services.test.utils.core.matchers.HandlerMethodMatcher.method;
import static uk.gov.moj.cpp.defence.commands.RecordInstructionDetails.recordInstructionDetails;
import static uk.gov.moj.cpp.defence.test.utils.HandlerTestHelper.matchEvent;
import static uk.gov.moj.cpp.defence.test.utils.HandlerTestHelper.metadataFor;
import static uk.gov.moj.cpp.defence.test.utils.HandlerTestHelper.toList;

import uk.gov.justice.cps.defence.DefenceClientDetails;
import uk.gov.justice.cps.defence.DefenceClientMappedToACase;
import uk.gov.justice.cps.defence.RecordAccessToIdpc;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.aggregate.CaseDefenceClientMap;
import uk.gov.moj.cpp.defence.aggregate.DefenceClient;
import uk.gov.moj.cpp.defence.commands.ReceiveDefenceClient;
import uk.gov.moj.cpp.defence.commands.ReceiveUrnForDefenceClient;
import uk.gov.moj.cpp.defence.commands.RecordIdpcDetails;
import uk.gov.moj.cpp.defence.commands.RecordInstructionDetails;
import uk.gov.moj.cpp.defence.commands.UpdateDefendantOffences;
import uk.gov.moj.cpp.defence.event.listener.events.DefendantOffencesUpdated;
import uk.gov.moj.cpp.defence.events.DefenceClientDoesNotExist;
import uk.gov.moj.cpp.defence.events.DefenceClientReceived;
import uk.gov.moj.cpp.defence.events.DefenceClientUrnAdded;
import uk.gov.moj.cpp.defence.events.IdpcAccessByOrganisationRecorded;
import uk.gov.moj.cpp.defence.events.IdpcAccessRecorded;
import uk.gov.moj.cpp.defence.events.IdpcDetailsRecorded;
import uk.gov.moj.cpp.defence.events.IdpcReceivedBeforeCase;
import uk.gov.moj.cpp.defence.events.InstructionDetailsRecorded;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.defence.service.referencedata.ReferenceDataService;
import uk.gov.moj.cpp.defence.test.utils.FileResourceObjectMapper;
import uk.gov.moj.cpp.referencedata.query.Offences;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefenceClientCommandHandlerTest {

    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Mock
    private JsonObject jsonObject;

    private static final UUID COMMAND_ID = randomUUID();
    private DefenceClient defenceClientAggregate = new DefenceClient();
    private final CaseDefenceClientMap caseDefenceClientMapAggregate = new CaseDefenceClientMap();
    private final FileResourceObjectMapper handlerTestHelper = new FileResourceObjectMapper();
    @Spy
    private final Enveloper enveloper = createEnveloperWithEvents(
            DefenceClientReceived.class,
            DefenceClientDoesNotExist.class,
            InstructionDetailsRecorded.class,
            DefenceClientUrnAdded.class,
            IdpcDetailsRecorded.class,
            IdpcAccessRecorded.class,
            IdpcAccessByOrganisationRecorded.class,
            DefendantOffencesUpdated.class,
            IdpcReceivedBeforeCase.class
    );
    @Mock
    private EventSource eventSourceMock;
    @Mock
    private EventStream eventStreamMock;
    @Mock
    private AggregateService aggregateServiceMock;
    @Mock
    private ReferenceDataService referenceDataServiceMock;
    @Mock
    private UserGroupService usersGroupQueryService;
    @InjectMocks
    private DefenceClientCommandHandler defenceClientCommandHandler;

    @Test
    void shouldHandleReceiveDefenceClientCommand() {
        assertThat(new DefenceClientCommandHandler(), isHandler(COMMAND_HANDLER)
                .with(method("receiveDefenceClient")
                        .thatHandles("defence.command.receive-defence-client")
                ));
    }

    @Test
    void shouldHandleRecordInstructionDetailsCommand() {
        assertThat(new DefenceClientCommandHandler(), isHandler(COMMAND_HANDLER)
                .with(method("recordInstructionDetails")
                        .thatHandles("defence.command.record-instruction-details")
                ));
    }

    @Test
    void shouldHandleDefendantOffencesChangedCommand() {
        assertThat(new DefenceClientCommandHandler(), isHandler(COMMAND_HANDLER)
                .with(method("receiveDefendantOffencesChanged")
                        .thatHandles("defence.command.update-defendant-offences")
                ));
    }

    @Test
    void shouldHandleReceiveUrnForDefenceClient() {
        assertThat(new DefenceClientCommandHandler(), isHandler(COMMAND_HANDLER)
                .with(method("receiveUrnForDefenceClient")
                        .thatHandles("defence.command.receive-urn-for-defence-client")
                ));
    }

    @Test
    void shouldHandleRecordIdpcAccessCommand() {
        assertThat(new DefenceClientCommandHandler(), isHandler(COMMAND_HANDLER)
                .with(method("recordIDPCAccess")
                        .thatHandles("defence.command.record-access-to-idpc")
                ));
    }

    @Test
    void shouldProvideDefenceClientReceivedEvent() throws EventStreamException, IOException {
        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);

        final Envelope<ReceiveDefenceClient> envelope =
                envelopeFrom(metadataFor("defence.command.receive-defence-client",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/receiveADefenceClient.json", ReceiveDefenceClient.class));
        defenceClientCommandHandler.receiveDefenceClient(envelope);
        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.events.defence-client-received",
                handlerTestHelper.convertFromFile("json/defenceClientReceived.json", JsonValue.class));

    }

    @Test
     void shouldProvideDefendantOffencesUpdatedEvent() throws EventStreamException, IOException {
        final UUID defendantId = UUID.fromString("93990b06-5a47-426e-a88e-9372dca5d75e");
        final UUID defenceClientId = defendantId;
        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);
        when(referenceDataServiceMock.getRefDataOffences(any(), any(), any(), any())).thenReturn(getRefDataMock());
        caseDefenceClientMapAggregate.apply(DefenceClientMappedToACase.defenceClientMappedToACase()
                .withDefenceClientId(defenceClientId)
                .withDefendantDetails(defendantDetails().withId(defendantId).build())
                .build());


        final Envelope<UpdateDefendantOffences> envelope =
                envelopeFrom(metadataFor("defence.command.update-defendant-offences",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/receiveOffenceUpdateReceived.json", UpdateDefendantOffences.class));
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        defenceClientCommandHandler.receiveDefendantOffencesChanged(envelope);
        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.event.defendant-offences-updated",
                handlerTestHelper.convertFromFile("json/receiveOffenceUpdateReceivedEvent.json", JsonValue.class));

    }

    @Test
    public void shouldProvideDefendantOffencesUpdatedEventForCivilCase() throws EventStreamException, IOException {
        final UUID defendantId = UUID.fromString("93990b06-5a47-426e-a88e-9372dca5d75e");
        final UUID defenceClientId = defendantId;
        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);
        when(referenceDataServiceMock.getRefDataOffences(any(), any(), any(), any())).thenReturn(getRefDataMock());
        caseDefenceClientMapAggregate.apply(DefenceClientMappedToACase.defenceClientMappedToACase()
                .withDefenceClientId(defenceClientId)
                .withDefendantDetails(defendantDetails()
                        .withId(defendantId)
                        .withIsCivil(true)
                        .build())
                .build());


        final Envelope<UpdateDefendantOffences> envelope =
                envelopeFrom(metadataFor("defence.command.update-defendant-offences",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/receiveOffenceUpdateReceived.json", UpdateDefendantOffences.class));
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        defenceClientCommandHandler.receiveDefendantOffencesChanged(envelope);
        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.event.defendant-offences-updated",
                handlerTestHelper.convertFromFile("json/receiveOffenceUpdateReceivedEvent.json", JsonValue.class));

    }

    @Test
    public void shouldProvideDefendantOffencesUpdatedEventForCivilCase_WhenNoAddedOffences() throws EventStreamException, IOException {
        final UUID defendantId = UUID.fromString("93990b06-5a47-426e-a88e-9372dca5d75e");
        final UUID defenceClientId = defendantId;
        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);
        when(referenceDataServiceMock.getRefDataOffences(any(), any(), any(), any())).thenReturn(getRefDataMock());
        caseDefenceClientMapAggregate.apply(DefenceClientMappedToACase.defenceClientMappedToACase()
                .withDefenceClientId(defenceClientId)
                .withDefendantDetails(defendantDetails()
                        .withId(defendantId)
                        .withIsCivil(true)
                        .build())
                .build());


        final Envelope<UpdateDefendantOffences> envelope =
                envelopeFrom(metadataFor("defence.command.update-defendant-offences",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/receiveOffenceUpdateReceived-noaddedoffences.json", UpdateDefendantOffences.class));
        defenceClientCommandHandler.receiveDefendantOffencesChanged(envelope);
        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.event.defendant-offences-updated",
                handlerTestHelper.convertFromFile("json/receiveOffenceUpdateReceivedEvent-noaddedoffences.json", JsonValue.class));

    }

    @Test
    public void shouldProvideDefendantOffencesUpdatedEventForCivilCase_WhenOnlyDeletedOffences() throws EventStreamException, IOException {
        final UUID defendantId = UUID.fromString("93990b06-5a47-426e-a88e-9372dca5d75e");
        final UUID defenceClientId = defendantId;
        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);
        caseDefenceClientMapAggregate.apply(DefenceClientMappedToACase.defenceClientMappedToACase()
                .withDefenceClientId(defenceClientId)
                .withDefendantDetails(defendantDetails()
                        .withId(defendantId)
                        .withIsCivil(true)
                        .build())
                .build());


        final Envelope<UpdateDefendantOffences> envelope =
                envelopeFrom(metadataFor("defence.command.update-defendant-offences",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/receiveOffenceUpdateReceived-onlydeletedoffences.json", UpdateDefendantOffences.class));
        defenceClientCommandHandler.receiveDefendantOffencesChanged(envelope);
        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.event.defendant-offences-updated",
                handlerTestHelper.convertFromFile("json/receiveOffenceUpdateReceivedEvent-onlydeletedoffences.json", JsonValue.class));

    }

    private Offences getRefDataMock() throws IOException {
        return handlerTestHelper.convertFromFile("json/refDataOffenceQuery.json", Offences.class);
    }

    @Test
    void shouldHandleRecordInstructionDetailsCommandWhenDefenceClientMissing() throws EventStreamException, IOException {
        final UUID defenceClientId = fromString("a4391788-f829-4514-a344-61f1d5d9690c");
        final RecordInstructionDetails recordInstructionDetails = recordInstructionDetails()
                .withInstructionDate(LocalDate.parse("2018-03-03"))
                .withDefenceClientId(defenceClientId)
                .build();

        final Envelope<RecordInstructionDetails> envelope = envelopeFrom(metadataFor("defence.command.record-instruction-details",
                defenceClientId), recordInstructionDetails);

        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);

        defenceClientCommandHandler.recordInstructionDetails(envelope);

        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.event.defence-client-does-not-exist",
                handlerTestHelper.convertFromFile("json/defence-client-does-not-exist.json", JsonValue.class));

    }

    @Test
    void shouldProvideDefenceClientUrnAddedEvent() throws EventStreamException, IOException {
        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);

        final Envelope<ReceiveUrnForDefenceClient> envelope =
                envelopeFrom(metadataFor("defence.command.receive-urn-for-defence-client",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/receiveDefenceClientUrn.json", ReceiveUrnForDefenceClient.class));
        defenceClientCommandHandler.receiveUrnForDefenceClient(envelope);
        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.event.defence-client-urn-added",
                handlerTestHelper.convertFromFile("json/defenceClientUrnReceived.json", JsonValue.class));

    }

    @Test
    void shouldHandleRecordIdpcDetailsBeforeCaseReceived() throws IOException, EventStreamException {

        final Envelope<RecordIdpcDetails> envelope =
                envelopeFrom(metadataFor("defence.command.record-idpc-details",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/recordIdpcDetails.json", RecordIdpcDetails.class));

        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);
        defenceClientCommandHandler.recordIdpcDetails(envelope);

        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.event.idpc-received-before-case",
                handlerTestHelper.convertFromFile("json/idpcReceivedBeforeCase.json", JsonValue.class));
    }

    @Test
    void shouldHandleRecordIdpcDetails() throws IOException, EventStreamException {

        final UUID defenceClientId = fromString("a4391788-f829-4514-a344-61f1d5d9690c");
        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);
        defenceClientAggregate.apply(DefenceClientReceived.defenceClientReceived()
                .withDefenceClientDetails(DefenceClientDetails.defenceClientDetails()
                        .build())
                .withDefenceClientId(defenceClientId)
                .withDefendantDetails(defendantDetails()
                        .withFirstName("John")
                        .withLastName("Smith")
                        .withId(defenceClientId)
                        .withCaseId(randomUUID())
                        .build())
                .withDefendantId(defenceClientId)
                .build());
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);

        final Envelope<RecordIdpcDetails> recordIdpcDetailsEnvelope =
                envelopeFrom(metadataFor("defence.command.record-idpc-details",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/recordIdpcDetails.json", RecordIdpcDetails.class));

        defenceClientCommandHandler.recordIdpcDetails(recordIdpcDetailsEnvelope);

        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.event.idpc-details-recorded",
                handlerTestHelper.convertFromFile("json/idpcDetailsRecorded.json", JsonValue.class));

        defenceClientAggregate = new DefenceClient();
    }

    @Test
    void shouldHandleRecordIdpcAccess() throws IOException, EventStreamException {

        final UUID defenceClientId = fromString("a4391788-f829-4514-a344-61f1d5d9690c");
        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);

        defenceClientAggregate.receiveADefenceClient(
                defenceClientId,
                null,
                defendantDetails().withId(UUID.fromString("a4391788-f829-4514-a344-61f1d5d9690c")).withLastName("smith").build(),
                null, null);

        final Envelope<RecordAccessToIdpc> envelope =
                envelopeFrom(metadataFor("defence.command.record-access-to-idpc",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/recordAccessToIdpc.json", RecordAccessToIdpc.class));

        defenceClientCommandHandler.recordIDPCAccess(envelope);

        final List<JsonEnvelope> jsonEnvelopeList = toList(verifyAppendAndGetArgumentFrom(eventStreamMock));

        matchEvent(jsonEnvelopeList,
                "defence.event.idpc-access-recorded",
                handlerTestHelper.convertFromFile("json/idpcAccessRecorded.json", JsonValue.class));

        matchEvent(jsonEnvelopeList,
                "defence.event.idpc-access-by-organisation-recorded",
                handlerTestHelper.convertFromFile("json/idpcAccessByOrganisationRecorded.json", JsonValue.class));

    }

    @Test
    void shouldProvideDefendantOffencesUpdatedEventWhen() throws EventStreamException, IOException {
        final UUID defendantId = UUID.fromString("93990b06-5a47-426e-a88e-9372dca5d75e");
        final UUID defenceClientId = defendantId;
        when(eventSourceMock.getStreamById(any())).thenReturn(eventStreamMock);
        when(aggregateServiceMock.get(eventStreamMock, DefenceClient.class)).thenReturn(defenceClientAggregate);
        when(referenceDataServiceMock.getRefDataOffences(any(), any(), any(), any())).thenReturn(Offences.offences().build());
        caseDefenceClientMapAggregate.apply(DefenceClientMappedToACase.defenceClientMappedToACase()
                .withDefenceClientId(defenceClientId)
                .withDefendantDetails(defendantDetails().withId(defendantId).build())
                .build());


        final Envelope<UpdateDefendantOffences> envelope =
                envelopeFrom(metadataFor("defence.command.update-defendant-offences",
                                COMMAND_ID),
                        handlerTestHelper.convertFromFile("json/receiveOffenceUpdateReceived.json", UpdateDefendantOffences.class));
        when(objectToJsonObjectConverter.convert(envelope.payload())).thenReturn(jsonObject);
        defenceClientCommandHandler.receiveDefendantOffencesChanged(envelope);
        matchEvent(verifyAppendAndGetArgumentFrom(eventStreamMock),
                "defence.event.defendant-offences-updated",
                handlerTestHelper.convertFromFile("json/notReceiveOffenceUpdateReceivedEvent.json", JsonValue.class));

    }

}
