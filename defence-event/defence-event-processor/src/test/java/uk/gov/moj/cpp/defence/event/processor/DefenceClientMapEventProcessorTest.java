package uk.gov.moj.cpp.defence.event.processor;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.ZonedDateTime.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;
import static uk.gov.moj.cpp.defence.event.processor.TestTemplates.basicCCCaseReceivedTemplate;
import static uk.gov.moj.cpp.defence.event.processor.TestTemplates.createCaseRemovedFromGroupCasesEvent;
import static uk.gov.moj.cpp.defence.event.processor.TestTemplates.createProsecutionCaseCreatedEvent;
import static uk.gov.moj.cpp.defence.event.processor.TestTemplates.getDefendantDetails;

import uk.gov.justice.core.courts.Defendant;
import uk.gov.justice.core.courts.DefendantsAddedToCase;
import uk.gov.justice.core.courts.Offence;
import uk.gov.justice.core.courts.Person;
import uk.gov.justice.core.courts.PersonDefendant;
import uk.gov.justice.core.courts.ProsecutionCase;
import uk.gov.justice.core.courts.ProsecutionCaseIdentifier;
import uk.gov.justice.cps.defence.DefendantDetails;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.CaseDetails;
import uk.gov.moj.cpp.defence.event.converter.DefendantsAddedConverter;
import uk.gov.moj.cpp.defence.event.converter.ProsecutionCaseConverter;
import uk.gov.moj.cpp.defence.event.processor.commands.ProsecutionCaseReceiveDetails;
import uk.gov.moj.cpp.defence.event.processor.events.CaseRemovedFromGroupCases;
import uk.gov.moj.cpp.defence.event.service.ProgressionService;
import uk.gov.moj.cpp.defence.events.CaseCreatedBdf;
import uk.gov.moj.cpp.defence.json.schema.event.DefendantAdded;
import uk.gov.moj.cpp.progression.json.schema.event.ProsecutionCaseCreated;
import uk.gov.moj.cpp.prosecutioncasefile.json.schema.event.PublicCcCaseReceived;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefenceClientMapEventProcessorTest {

    private static final String FIRST_NAME = "FirstName";
    private static final String LAST_NAME = "LastName";
    @Mock
    ProsecutionCaseConverter prosecutionCaseConverter;
    @Mock
    DefendantsAddedConverter defendantsAddedConverter;
    @Captor
    private ArgumentCaptor<Envelope<?>> envelopeArgumentCaptor;
    @Captor
    private ArgumentCaptor<Envelope<DefendantAdded>> envelopeAddDefendantCaptor;
    @Captor
    private ArgumentCaptor<Envelope<?>> argumentCaptor;
    @InjectMocks
    private DefenceClientMapEventProcessor defenceClientMapEventProcessor;
    @Mock
    private Sender sender;
    @Mock
    private List<Offence> offences;

    @Mock
    private ProgressionService progressionService;

    @Spy
    ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());

    @Spy
    JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper());

    @Test
    void shouldRaiseDefenceCommandForProsecutionCaseReceived() {

        final Envelope<ProsecutionCaseCreated> envelope = envelopeFrom(metadataWithRandomUUID("public.progression.prosecution-case-created"), createProsecutionCaseCreatedEvent(FALSE, FALSE, FALSE));
        final ProsecutionCaseCreated prosecutionCaseCreated = envelope.payload();
        when(prosecutionCaseConverter.convertToProsecutionCaseReceiveDetails(any())).thenReturn(ProsecutionCaseReceiveDetails.prosecutionCaseReceiveDetails()
                .withCaseDetails(CaseDetails.caseDetails()
                        .withProsecutorCaseReference(prosecutionCaseCreated.getProsecutionCase().getProsecutionCaseIdentifier().getCaseURN())
                        .withCaseId(prosecutionCaseCreated.getProsecutionCase().getId())
                        .build())
                .build());
        defenceClientMapEventProcessor.handleProsecutionCaseReceived(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());


        final List<Envelope<?>> allValues = argumentCaptor.getAllValues();
        final Envelope<ProsecutionCaseReceiveDetails> prosecutioncaseReceivedEnvelope = (Envelope<ProsecutionCaseReceiveDetails>) allValues.get(0);

        assertThat(prosecutioncaseReceivedEnvelope.metadata().name(), is("defence.command.prosecution-case-receive-details"));
        assertThat(prosecutioncaseReceivedEnvelope.payload().getCaseDetails().getCaseId().toString(), is(prosecutionCaseCreated.getProsecutionCase().getId().toString()));
        assertThat(prosecutioncaseReceivedEnvelope.payload().getCaseDetails().getProsecutorCaseReference(), is(prosecutionCaseCreated.getProsecutionCase().getProsecutionCaseIdentifier().getCaseURN()));

    }

    @Test
    void shouldRaiseCreateCaseCommandForProsecutionCaseReceivedForBDF() {
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(randomUUID())
                .withIsCivil(FALSE)
                .withIsGroupMember(FALSE)
                .withDefendants(getDefendantDetails(TRUE))
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier().withCaseURN("CASEURN").build())
                .build();

        final Envelope<CaseCreatedBdf> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.case_created-bdf"), CaseCreatedBdf.caseCreatedBdf().withProsecutionCaseId(prosecutionCase.getId()).build());
        when(prosecutionCaseConverter.convertToProsecutionCaseReceiveDetails(any())).thenReturn(ProsecutionCaseReceiveDetails.prosecutionCaseReceiveDetails()
                .withCaseDetails(CaseDetails.caseDetails()
                        .withProsecutorCaseReference(prosecutionCase.getProsecutionCaseIdentifier().getCaseURN())
                        .withCaseId(prosecutionCase.getId())
                        .build())
                .build());
        final JsonObject caseJsonObject = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(caseJsonObject);
        defenceClientMapEventProcessor.handleProsecutionCaseCreatedBdf(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());


        final List<Envelope<?>> allValues = argumentCaptor.getAllValues();
        final Envelope<ProsecutionCaseReceiveDetails> prosecutioncaseReceivedEnvelope = (Envelope<ProsecutionCaseReceiveDetails>) allValues.get(0);

        assertThat(prosecutioncaseReceivedEnvelope.metadata().name(), is("defence.command.prosecution-case-receive-details"));
        assertThat(prosecutioncaseReceivedEnvelope.payload().getCaseDetails().getCaseId().toString(), is(prosecutionCase.getId().toString()));
        assertThat(prosecutioncaseReceivedEnvelope.payload().getCaseDetails().getProsecutorCaseReference(), is(prosecutionCase.getProsecutionCaseIdentifier().getCaseURN()));

    }

    @Test
    void shouldRaiseAddDefendantCommandForProsecutionCaseReceivedForBDF() {
        final UUID defendantId= randomUUID();
        final UUID caseId = randomUUID();
        final String asn = randomUUID().toString();
        final LocalDate dateOfBirth = LocalDate.now();

        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withIsCivil(FALSE)
                .withIsGroupMember(FALSE)
                .withDefendants(getDefendantDetails(TRUE))
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier().withCaseURN("CASEURN").build())
                .withDefendants(Collections.singletonList(Defendant.defendant()
                        .withId(defendantId)
                        .withProsecutionAuthorityReference("reference")
                        .build()))
                .build();

        final Envelope<CaseCreatedBdf> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.case_created-bdf"), CaseCreatedBdf.caseCreatedBdf()
                .withProsecutionCaseId(prosecutionCase.getId())
                .withDefendantId(defendantId).build());


        when(defendantsAddedConverter.convert(any())).thenReturn(DefendantAdded.defendantAdded()
                .withDefendantId(defendantId)
                .withPoliceDefendantId(asn)
                .withDefendantDetails(DefendantDetails.defendantDetails()
                        .withFirstName(FIRST_NAME)
                        .withLastName(LAST_NAME)
                        .withDateOfBirth(dateOfBirth.toString())
                        .withCaseId(caseId)
                        .build())
                .build());

        final JsonObject caseJsonObject = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(caseJsonObject);
        defenceClientMapEventProcessor.handleProsecutionCaseCreatedBdf(envelope);
        verify(sender, times(1)).send(envelopeAddDefendantCaptor.capture());

        final List<Envelope<DefendantAdded>> addDefendantEnvelopes = envelopeAddDefendantCaptor.getAllValues();

        assertThat(addDefendantEnvelopes.size(), is(1));

        for (final Envelope<DefendantAdded> defendantAddedEnvelope : addDefendantEnvelopes) {
            assertThat(defendantAddedEnvelope.payload().getDefendantDetails().getCaseId(), is(caseId));
            assertThat(defendantAddedEnvelope.payload().getDefendantDetails().getFirstName(), is(FIRST_NAME));
            assertThat(defendantAddedEnvelope.payload().getDefendantDetails().getLastName(), is(LAST_NAME));
            assertThat(defendantAddedEnvelope.payload().getDefendantDetails().getDateOfBirth(), is(dateOfBirth.toString()));
            assertThat(defendantAddedEnvelope.payload().getDefendantId(), is(defendantId));
            assertThat(defendantAddedEnvelope.payload().getPoliceDefendantId(), is(asn));
            assertThat(defendantAddedEnvelope.metadata().name(), is("defence.command.add-defendant"));
        }

    }

    @Test
    void shouldRaiseDefenceCommandForCaseRemovedFromGroupCases() {

        final Envelope<CaseRemovedFromGroupCases> envelope = envelopeFrom(metadataWithRandomUUID("public.progression.case-removed-from-group-cases"),
                createCaseRemovedFromGroupCasesEvent());
        final CaseRemovedFromGroupCases caseRemovedFromGroupCases = envelope.payload();
        when(prosecutionCaseConverter.convertToProsecutionCaseReceiveDetails(any()))
                .thenReturn(ProsecutionCaseReceiveDetails.prosecutionCaseReceiveDetails()
                        .withCaseDetails(CaseDetails.caseDetails()
                                .withProsecutorCaseReference(caseRemovedFromGroupCases.getRemovedCase().getProsecutionCaseIdentifier().getCaseURN())
                                .withCaseId(caseRemovedFromGroupCases.getRemovedCase().getId())
                                .build())
                        .build());
        defenceClientMapEventProcessor.handleCaseRemovedFromGroupCases(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());


        final List<Envelope<?>> allValues = argumentCaptor.getAllValues();
        final Envelope<ProsecutionCaseReceiveDetails> prosecutioncaseReceivedEnvelope = (Envelope<ProsecutionCaseReceiveDetails>) allValues.get(0);

        assertThat(prosecutioncaseReceivedEnvelope.metadata().name(), is("defence.command.prosecution-case-receive-details"));
        assertThat(prosecutioncaseReceivedEnvelope.payload().getCaseDetails().getCaseId().toString(), is(caseRemovedFromGroupCases.getRemovedCase().getId().toString()));
        assertThat(prosecutioncaseReceivedEnvelope.payload().getCaseDetails().getProsecutorCaseReference(), is(caseRemovedFromGroupCases.getRemovedCase().getProsecutionCaseIdentifier().getCaseURN()));
    }

    @Test
    void shouldSendDefendantAddedCommandForSpiProsecutionDefendants() {

        final UUID defendantId = randomUUID();
        final String asn = randomUUID().toString();
        final LocalDate dateOfBirth = LocalDate.now();
        final UUID caseId = randomUUID();

        final Defendant defendant = Defendant.defendant()
                .withId(defendantId)
                .withOffences(offences)
                .withProsecutionAuthorityReference(asn)
                .withPersonDefendant(PersonDefendant.personDefendant()
                        .withPersonDetails(Person.person()
                                .withFirstName(FIRST_NAME)
                                .withLastName(LAST_NAME)
                                .withDateOfBirth(dateOfBirth.toString())
                                .build())
                        .build())
                .withProsecutionCaseId(caseId)
                .build();

        final List<Defendant> defendants = new ArrayList<>();
        defendants.add(defendant);

        final DefendantsAddedToCase spiProsecutionDefendantsAdded = DefendantsAddedToCase.defendantsAddedToCase()
                .withDefendants(defendants)
                .build();

        final Envelope<DefendantsAddedToCase> envelope = createTypedEnvelope(spiProsecutionDefendantsAdded);

        when(defendantsAddedConverter.convert(any())).thenReturn(DefendantAdded.defendantAdded()
                .withDefendantId(defendantId)
                .withPoliceDefendantId(asn)
                .withDefendantDetails(DefendantDetails.defendantDetails()
                        .withFirstName(FIRST_NAME)
                        .withLastName(LAST_NAME)
                        .withDateOfBirth(dateOfBirth.toString())
                        .withCaseId(caseId)
                        .build())
                .build());

        defenceClientMapEventProcessor.handleSpiProsecutionDefendantsAdded(envelope);

        verify(sender).send(envelopeAddDefendantCaptor.capture());

        final List<Envelope<DefendantAdded>> addDefendantEnvelopes = envelopeAddDefendantCaptor.getAllValues();

        assertThat(addDefendantEnvelopes.size(), is(defendants.size()));

        for (final Envelope<DefendantAdded> defendantAddedEnvelope : addDefendantEnvelopes) {
            assertThat(defendantAddedEnvelope.payload().getDefendantDetails().getCaseId(), is(caseId));
            assertThat(defendantAddedEnvelope.payload().getDefendantDetails().getFirstName(), is(FIRST_NAME));
            assertThat(defendantAddedEnvelope.payload().getDefendantDetails().getLastName(), is(LAST_NAME));
            assertThat(defendantAddedEnvelope.payload().getDefendantDetails().getDateOfBirth(), is(dateOfBirth.toString()));
            assertThat(defendantAddedEnvelope.payload().getDefendantId(), is(defendantId));
            assertThat(defendantAddedEnvelope.payload().getPoliceDefendantId(), is(asn));
            assertThat(defendantAddedEnvelope.metadata().name(), is("defence.command.add-defendant"));
        }
    }

    private <T> Envelope<T> createTypedEnvelope(final T t) {

        final Metadata metadata = metadataBuilder()
                .withId(randomUUID())
                .withName("actionName")
                .createdAt(now())
                .build();
        return envelopeFrom(metadata, t);
    }

    private Envelope<PublicCcCaseReceived> createCcCaseReceivedEnvelope() {
        final PublicCcCaseReceived ccCaseReceivedMessage = basicCCCaseReceivedTemplate();
        return envelopeFrom(metadataWithRandomUUID("public.prosecutioncasefile.cc-case-received"), ccCaseReceivedMessage);
    }


}