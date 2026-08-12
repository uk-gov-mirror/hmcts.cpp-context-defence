package uk.gov.moj.cpp.defence.event.processor;


import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;

import uk.gov.justice.core.courts.Address;
import uk.gov.justice.core.courts.Defendant;
import uk.gov.justice.core.courts.IndicatedPleaValue;
import uk.gov.justice.core.courts.LegalEntityDefendant;
import uk.gov.justice.core.courts.Offence;
import uk.gov.justice.core.courts.Organisation;
import uk.gov.justice.core.courts.Person;
import uk.gov.justice.core.courts.PersonDefendant;
import uk.gov.justice.core.courts.ProsecutionCase;
import uk.gov.justice.core.courts.ProsecutionCaseIdentifier;
import uk.gov.justice.cps.defence.OffencePleaDetails;
import uk.gov.justice.cps.defence.OffenceType;
import uk.gov.justice.cps.defence.PleasAllocationDetails;
import uk.gov.justice.cps.defence.YesNoNa;
import uk.gov.justice.cps.defence.plea.PleaDefendantDetails;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.event.service.DefenceService;
import uk.gov.moj.cpp.defence.event.service.DocumentGeneratorService;
import uk.gov.moj.cpp.defence.event.service.ProgressionService;
import uk.gov.moj.cpp.defence.event.service.UserDetails;
import uk.gov.moj.cpp.defence.event.service.UsersGroupService;
import uk.gov.moj.cpp.defence.events.AllocationPleasAdded;
import uk.gov.moj.cpp.defence.events.AllocationPleasUpdated;
import uk.gov.moj.cpp.defence.events.OpaTaskRequested;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PleaAllocationEventProcessorTest {

    @Captor
    private ArgumentCaptor<Envelope<?>> argumentCaptor;

    @Captor
    private ArgumentCaptor<JsonObject> jsonObjectArgumentCaptor;

    @InjectMocks
    private PleaAllocationEventProcessor pleaAllocationEventProcessor;

    @Mock
    private Sender sender;

    @Mock
    private UsersGroupService usersGroupService;

    @Mock
    private DefenceService defenceService;

    @Mock
    private DocumentGeneratorService documentGeneratorService;

    @Mock
    private ProgressionService progressionService;

    @Spy
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Spy
    private final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(objectMapper);

    @Spy
    private final JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(objectMapper);

    private static final String DEFENDANT_ON_OPA = "defendantOnOpa";
    private static final String DEFENDANT_ON_CASE = "defendantsOnCase";


    @Test
    public void shouldRaisePublicAllocationPleaAdded_AdultEitherWay_Guilty() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final UUID defendantId2 = randomUUID();
        final UUID offenceId2 = randomUUID();
        final Envelope<AllocationPleasAdded> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-added"),
                AllocationPleasAdded.allocationPleasAdded()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(false)
                                        .withOffenceType(OffenceType.ADULTEITHERWAY)
                                        .withAdditionalInformation("Additional Information")
                                        .withCrownCourtObjection(YesNoNa.Y)
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );
        final AllocationPleasAdded allocationPleasAdded = envelope.payload();
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withDefendants(Arrays.asList(Defendant.defendant()
                                .withId(defendantId)
                                .withPersonDefendant(PersonDefendant.personDefendant()
                                        .withPersonDetails(Person.person()
                                                .withFirstName("First")
                                                .withLastName("Last")
                                                .withDateOfBirth("1981-11-11")
                                                .withAddress(Address.address()
                                                        .withAddress1("address 1")
                                                        .withPostcode("SN1 1AP")
                                                        .build())
                                                .build())
                                        .build())

                                .withOffences(Arrays.asList(Offence.offence()
                                        .withId(offenceId)
                                        .withOffenceTitle("Offence Title 1")
                                        .withWording("Wording 1")
                                        .build()))
                                .build(),
                        Defendant.defendant()
                                .withId(defendantId2)
                                .withLegalEntityDefendant(LegalEntityDefendant.legalEntityDefendant()
                                        .withOrganisation(Organisation.organisation()
                                                .withName("Organisation Name")
                                                .withAddress(Address.address()
                                                        .withAddress1("Org address 1")
                                                        .withAddress2("Org address 2")
                                                        .withAddress3("Org address 3")
                                                        .withPostcode("SW1 9FP")
                                                        .build())
                                                .build())
                                        .build())
                                .withOffences(Arrays.asList(Offence.offence()
                                        .withId(offenceId2)
                                        .withOffenceTitle("Offence Title 2")
                                        .withWording("Wording 2")
                                        .build()))
                                .build()))
                .build();
        final JsonArray pleasAllocationDetailsOnCaseJsonArray = createArrayBuilder().add(createObjectBuilder()
                        .add("caseUrn", "URN1")
                        .add("caseId", caseId.toString())
                        .add("defendantId", defendantId2.toString())
                        .add("additionalInformation", "Additional Information")
                        .add("crownCourtObjection", YesNoNa.Y.toString())
                        .add("offencePleas", createArrayBuilder().add(createObjectBuilder()
                                .add("offenceId", offenceId.toString())
                                .add("indicatedPlea", IndicatedPleaValue.INDICATED_GUILTY.toString())))
                        .add("defendantDetails", createObjectBuilder().add("organisationName", "Corrected Organisation Name").build())
                        .add("defendantNameDobConfirmation", "false")
                        .add("offenceType", "youthGraveCrime")
                        .add("additionalInformation", "Test additional Info")
                        .add("defendantTurningEighteenDetails", "Defendant turning 18")
                        .add("theftFromShop", "Y")
                        .add("theftFromShopDetails", "Theft From Shop Details"))
                .build();

        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));
        when(defenceService.getPleaAndAllocationDetailsForACase(envelope.metadata(), caseId)).thenReturn(pleasAllocationDetailsOnCaseJsonArray);
        pleaAllocationEventProcessor.handleAllocationPleasAdded(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasAdded> publicEventEnvelope = (Envelope<AllocationPleasAdded>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> addCourtDocumentEnvelope = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) addCourtDocumentEnvelope;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasAdded, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult either-way offences"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getString("defendantOrganisationName")
                , is("Organisation Name"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getJsonObject("address").getString("address1")
                , is("Org address 1"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getJsonObject("address").getString("address2")
                , is("Org address 2"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getJsonObject("address").getString("address3")
                , is("Org address 3"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getJsonObject("address").getString("postcode")
                , is("SW1 9FP"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getString("offenceType")
                , is("Youth grave crime"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).get("defendantNameDobConfirmation").toString()
                , is("false"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getString("defendantCorrectedName")
                , is("Corrected Organisation Name"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getString("additionalInformation")
                , is("Test additional Info"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getString("defendantTurningEighteenDetails")
                , is("Defendant turning 18"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getString("theftFromShop")
                , is("I agree, they are low-value shoplifting offences"));
        assertThat(payloadForDocument.getJsonArray(DEFENDANT_ON_CASE).getJsonObject(0).getString("theftFromShopDetails")
                , is("Theft From Shop Details"));
        PleasAllocationDetails pleasAllocation = publicEventEnvelope.payload().getPleasAllocation();
        assertPleaAllocationFields(pleasAllocation, caseId, offenceId);

    }

    private void assertPleaAllocationFields(PleasAllocationDetails pleasAllocation, UUID caseId, UUID offenceId) {
        assertThat(pleasAllocation.getCaseId(), is(caseId));
        assertThat(pleasAllocation.getDefendantNameDobConfirmation(), is(false));
        assertThat(pleasAllocation.getOffenceType(), is(OffenceType.ADULTEITHERWAY));
        assertThat(pleasAllocation.getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(pleasAllocation.getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_GUILTY.toString()));
        assertThat(pleasAllocation.getAdditionalInformation(), is("Additional Information"));
        assertThat(pleasAllocation.getCrownCourtObjection(), is(YesNoNa.Y));
    }

    @Test
    public void shouldRaisePublicAllocationPleaAdded_AdultIndictableOnly_Guilty_DefendantDetailsCorrect() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasAdded> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-added"),
                AllocationPleasAdded.allocationPleasAdded()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(true)
                                        .withOffenceType(OffenceType.ADULTINDICTABLEONLY)
                                        .withAdditionalInformation("Additional Information")
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );
        final AllocationPleasAdded allocationPleasAdded = envelope.payload();
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withPersonDefendant(PersonDefendant.personDefendant()
                                .withPersonDetails(Person.person()
                                        .withFirstName("First")
                                        .withLastName("Last")
                                        .withDateOfBirth("1981-11-11")
                                        .withAddress(Address.address()
                                                .withAddress1("address 1")
                                                .withPostcode("SN1 1AP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationPleasAdded(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasAdded> publicEventEnvelope = (Envelope<AllocationPleasAdded>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> addCourtDocumentEnvelope = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) addCourtDocumentEnvelope;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasAdded, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult indictable only offences"));

        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(true));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.ADULTINDICTABLEONLY));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
    }

    @Test
    public void shouldRaisePublicAllocationPleaAdded_AdultIndictableOnly_Guilty_DefendantDetailsNotCorrect() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasAdded> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-added"),
                AllocationPleasAdded.allocationPleasAdded()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(false)
                                        .withDefendantDetails(PleaDefendantDetails.pleaDefendantDetails()
                                                .withFirstName("John")
                                                .withMiddleName("B")
                                                .withSurname("Rambo")
                                                .withDob(LocalDate.now().minusYears(20))
                                                .build())
                                        .withOffenceType(OffenceType.ADULTINDICTABLEONLY)
                                        .withAdditionalInformation("Additional Information")
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );
        final AllocationPleasAdded allocationPleasAdded = envelope.payload();
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withPersonDefendant(PersonDefendant.personDefendant()
                                .withPersonDetails(Person.person()
                                        .withFirstName("John")
                                        .withLastName("Wick")
                                        .withDateOfBirth("1981-11-11")
                                        .withAddress(Address.address()
                                                .withAddress1("address 1")
                                                .withPostcode("SN1 1AP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationPleasAdded(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasAdded> publicEventEnvelope = (Envelope<AllocationPleasAdded>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> addCourtDocumentEnvelope = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) addCourtDocumentEnvelope;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasAdded, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("defendantCorrectedName"), is("John B Rambo"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("defendantCorrectedDob"), is(DateTimeFormatter.ofPattern("dd-MMM-yyyy").format(
                LocalDate.now().minusYears(20))));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult indictable only offences"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(false));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantDetails().getFirstName(), is("John"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantDetails().getMiddleName(), is("B"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantDetails().getSurname(), is("Rambo"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantDetails().getDob(), is(LocalDate.now().minusYears(20)));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.ADULTINDICTABLEONLY));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
    }

    @Test
    public void shouldRaisePublicAllocationPleaAdded_AdultIndictableOnly_NotGuilty() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasAdded> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-added"),
                AllocationPleasAdded.allocationPleasAdded()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(true)
                                        .withOffenceType(OffenceType.ADULTINDICTABLEONLY)
                                        .withAdditionalInformation("Additional Information")
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );
        final AllocationPleasAdded allocationPleasAdded = envelope.payload();
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withPersonDefendant(PersonDefendant.personDefendant()
                                .withPersonDetails(Person.person()
                                        .withFirstName("First")
                                        .withLastName("Last")
                                        .withDateOfBirth("1981-11-11")
                                        .withAddress(Address.address()
                                                .withAddress1("address 1")
                                                .withPostcode("SN1 1AP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationPleasAdded(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasAdded> publicEventEnvelope = (Envelope<AllocationPleasAdded>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> addCourtDocumentEnvelope = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) addCourtDocumentEnvelope;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasAdded, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult indictable only offences"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(true));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.ADULTINDICTABLEONLY));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
    }

    @Test
    public void shouldRaisePublicAllocationPleaAdded_YouthGraveCrime_NotGuilty() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasAdded> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-added"),
                AllocationPleasAdded.allocationPleasAdded()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(true)
                                        .withOffenceType(OffenceType.YOUTHGRAVECRIME)
                                        .withAdditionalInformation("Additional Information")
                                        .withDefendantTurningEighteenDetails("Defendant turning 18")
                                        .withTheftFromShop(YesNoNa.Y)
                                        .withTheftFromShopDetails("Theft from shop")
                                        .withSentencingIndication(YesNoNa.Y)
                                        .withRepresentationsOnGraveCrime(false)
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );
        final AllocationPleasAdded allocationPleasAdded = envelope.payload();
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withPersonDefendant(PersonDefendant.personDefendant()
                                .withPersonDetails(Person.person()
                                        .withFirstName("First")
                                        .withLastName("Last")
                                        .withDateOfBirth("1981-11-11")
                                        .withAddress(Address.address()
                                                .withAddress1("address 1")
                                                .withPostcode("SN1 1AP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationPleasAdded(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasAdded> publicEventEnvelope = (Envelope<AllocationPleasAdded>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> addCourtDocumentEnvelope = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) addCourtDocumentEnvelope;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasAdded, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Youth grave crime"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(true));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.YOUTHGRAVECRIME));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantTurningEighteenDetails(), is("Defendant turning 18"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getTheftFromShop(), is(YesNoNa.Y));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getTheftFromShopDetails(), is("Theft from shop"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getSentencingIndication(), is(YesNoNa.Y));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getRepresentationsOnGraveCrime(), is(false));
    }

    @Test
    public void shouldRaisePublicOpaTaskRequested() {

        final Envelope<OpaTaskRequested> envelope = envelopeFrom(metadataWithRandomUUID("defence.opa-task-requested"), OpaTaskRequested.opaTaskRequested().withCaseUrn("CASEURN")
                .build());
        final OpaTaskRequested opaTaskRequested = envelope.payload();

        pleaAllocationEventProcessor.handleOpaTaskRequested(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());


        final Envelope<?> value = argumentCaptor.getValue();
        final Envelope<OpaTaskRequested> opaTaskRequestedEnvelope = (Envelope<OpaTaskRequested>) value;

        assertThat(opaTaskRequested.getCaseUrn(), is(opaTaskRequestedEnvelope.payload().getCaseUrn()));
    }

    @Test
    public void shouldRaisePublicPleaAllocationUpdated_AdultEitherWay_Guilty_DefendantDetailsCorrect() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasUpdated> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-updated"),
                AllocationPleasUpdated.allocationPleasUpdated()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(true)
                                        .withOffenceType(OffenceType.ADULTEITHERWAY)
                                        .withAdditionalInformation("Additional Information")
                                        .withCrownCourtObjection(YesNoNa.Y)
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );

        final AllocationPleasUpdated allocationPleasUpdated = envelope.payload();

        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier()
                        .withCaseURN("SERDFXRD")
                        .build())
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withPersonDefendant(PersonDefendant.personDefendant()
                                .withPersonDetails(Person.person()
                                        .withFirstName("First")
                                        .withLastName("Last")
                                        .withDateOfBirth("1981-11-11")
                                        .withAddress(Address.address()
                                                .withAddress1("address 1")
                                                .withPostcode("SN1 1AP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationUpdated(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasUpdated> publicEventEnvelope = (Envelope<AllocationPleasUpdated>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> value1 = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) value1;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasUpdated, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult either-way offences"));

        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(true));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.ADULTEITHERWAY));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCrownCourtObjection(), is(YesNoNa.Y));
    }

    @Test
    public void shouldRaisePublicPleaAllocationUpdated_AdultEitherWay_Guilty_DefendantDetailsNotCorrect() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasUpdated> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-updated"),
                AllocationPleasUpdated.allocationPleasUpdated()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(false)
                                        .withDefendantDetails(PleaDefendantDetails.pleaDefendantDetails()
                                                .withFirstName("John")
                                                .withMiddleName("B")
                                                .withSurname("Rambo")
                                                .withDob(LocalDate.now().minusYears(20))
                                                .build())
                                        .withOffenceType(OffenceType.ADULTEITHERWAY)
                                        .withAdditionalInformation("Additional Information")
                                        .withCrownCourtObjection(YesNoNa.Y)
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );

        final AllocationPleasUpdated allocationPleasUpdated = envelope.payload();

        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier()
                        .withCaseURN("SERDFXRD")
                        .build())
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withPersonDefendant(PersonDefendant.personDefendant()
                                .withPersonDetails(Person.person()
                                        .withFirstName("John")
                                        .withLastName("Wick")
                                        .withDateOfBirth("1981-11-11")
                                        .withAddress(Address.address()
                                                .withAddress1("address 1")
                                                .withPostcode("SN1 1AP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationUpdated(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasUpdated> publicEventEnvelope = (Envelope<AllocationPleasUpdated>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> value1 = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) value1;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasUpdated, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("defendantCorrectedName"), is("John B Rambo"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("defendantCorrectedDob"), is(DateTimeFormatter.ofPattern("dd-MMM-yyyy").format(
                LocalDate.now().minusYears(20))));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult either-way offences"));

        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(false));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.ADULTEITHERWAY));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCrownCourtObjection(), is(YesNoNa.Y));
    }

    @Test
    public void shouldRaisePublicPleaAllocationUpdated_AdultIndictableOnly_Guilty() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasUpdated> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-updated"),
                AllocationPleasUpdated.allocationPleasUpdated()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(true)
                                        .withOffenceType(OffenceType.ADULTINDICTABLEONLY)
                                        .withAdditionalInformation("Additional Information")
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );

        final AllocationPleasUpdated allocationPleasUpdated = envelope.payload();

        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier()
                        .withCaseURN("SERDFXRD")
                        .build())
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withPersonDefendant(PersonDefendant.personDefendant()
                                .withPersonDetails(Person.person()
                                        .withFirstName("First")
                                        .withLastName("Last")
                                        .withDateOfBirth("1981-11-11")
                                        .withAddress(Address.address()
                                                .withAddress1("address 1")
                                                .withPostcode("SN1 1AP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationUpdated(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasUpdated> publicEventEnvelope = (Envelope<AllocationPleasUpdated>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> value1 = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) value1;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasUpdated, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult indictable only offences"));

        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(true));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.ADULTINDICTABLEONLY));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
    }

    @Test
    public void shouldRaisePublicPleaAllocationUpdated_AdultIndictableOnly_NotGuilty() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasUpdated> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-updated"),
                AllocationPleasUpdated.allocationPleasUpdated()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(true)
                                        .withOffenceType(OffenceType.ADULTINDICTABLEONLY)
                                        .withAdditionalInformation("Additional Information")
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );

        final AllocationPleasUpdated allocationPleasUpdated = envelope.payload();

        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier()
                        .withCaseURN("SERDFXRD")
                        .build())
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withPersonDefendant(PersonDefendant.personDefendant()
                                .withPersonDetails(Person.person()
                                        .withFirstName("First")
                                        .withLastName("Last")
                                        .withDateOfBirth("1981-11-11")
                                        .withAddress(Address.address()
                                                .withAddress1("address 1")
                                                .withPostcode("SN1 1AP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationUpdated(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasUpdated> publicEventEnvelope = (Envelope<AllocationPleasUpdated>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> value1 = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) value1;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasUpdated, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult indictable only offences"));

        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(true));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.ADULTINDICTABLEONLY));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
    }

    @Test
    public void shouldRaisePublicPleaAllocationUpdated_YouthGraveCrime_NotGuilty() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasUpdated> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-updated"),
                AllocationPleasUpdated.allocationPleasUpdated()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(true)
                                        .withOffenceType(OffenceType.YOUTHGRAVECRIME)
                                        .withAdditionalInformation("Additional Information")
                                        .withDefendantTurningEighteenDetails("Defendant turning 18")
                                        .withTheftFromShop(YesNoNa.Y)
                                        .withTheftFromShopDetails("Theft from shop")
                                        .withSentencingIndication(YesNoNa.Y)
                                        .withRepresentationsOnGraveCrime(false)
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );

        final AllocationPleasUpdated allocationPleasUpdated = envelope.payload();

        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier()
                        .withCaseURN("SERDFXRD")
                        .build())
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withPersonDefendant(PersonDefendant.personDefendant()
                                .withPersonDetails(Person.person()
                                        .withFirstName("First")
                                        .withLastName("Last")
                                        .withDateOfBirth("1981-11-11")
                                        .withAddress(Address.address()
                                                .withAddress1("address 1")
                                                .withPostcode("SN1 1AP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationUpdated(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasUpdated> publicEventEnvelope = (Envelope<AllocationPleasUpdated>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> value1 = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) value1;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasUpdated, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Youth grave crime"));

        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(true));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.YOUTHGRAVECRIME));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantTurningEighteenDetails(), is("Defendant turning 18"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getTheftFromShop(), is(YesNoNa.Y));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getTheftFromShopDetails(), is("Theft from shop"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getSentencingIndication(), is(YesNoNa.Y));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getRepresentationsOnGraveCrime(), is(false));
    }

    @Test
    public void shouldRaisePublicAllocationPleaAdded_organisation_AdultEitherWay_NotGuilty() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasAdded> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-added"),
                AllocationPleasAdded.allocationPleasAdded()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantDetails(PleaDefendantDetails.pleaDefendantDetails()
                                                .withOrganisationName("Organisation Name")
                                                .build())
                                        .withOffenceType(OffenceType.ADULTEITHERWAY)
                                        .withDisputeOffenceValue(true)
                                        .withDisputeOffenceValueDetails("Dispute Offence details")
                                        .withTheftFromShop(YesNoNa.Y)
                                        .withTheftFromShopDetails("Theft from shop")
                                        .withElectingCrownCourtTrial(false)
                                        .withSentencingIndication(YesNoNa.Y)
                                        .withConsentToMagistratesCourtTrial("Consents to summary trial")
                                        .withAdditionalInformation("Additional Information")
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );
        final AllocationPleasAdded allocationPleasAdded = envelope.payload();
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withLegalEntityDefendant(LegalEntityDefendant.legalEntityDefendant()
                                .withOrganisation(Organisation.organisation()
                                        .withName("Organisation Name")
                                        .withAddress(Address.address()
                                                .withAddress1("Org address 1")
                                                .withAddress2("Org address 2")
                                                .withAddress3("Org address 3")
                                                .withPostcode("SW1 9FP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationPleasAdded(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasAdded> publicEventEnvelope = (Envelope<AllocationPleasAdded>) argumentCaptor.getValue();

        assertThat(publicEventEnvelope.payload(), is(allocationPleasAdded));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));

        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("defendantOrganisationName")
                , is("Organisation Name"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("address1")
                , is("Org address 1"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("address2")
                , is("Org address 2"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("address3")
                , is("Org address 3"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("postcode")
                , is("SW1 9FP"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult either-way offences"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.ADULTEITHERWAY));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_NOT_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDisputeOffenceValue(), is(true));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDisputeOffenceValueDetails(), is("Dispute Offence details"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getTheftFromShop(), is(YesNoNa.Y));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getTheftFromShopDetails(), is("Theft from shop"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getElectingCrownCourtTrial(), is(false));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getSentencingIndication(), is(YesNoNa.Y));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getConsentToMagistratesCourtTrial(), is("Consents to summary trial"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getSentencingIndication(), is(YesNoNa.Y));
    }

    @Test
    public void shouldRaisePublicAllocationPleaAdded_organisation_YouthGraveCrime_Guilty() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasAdded> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-added"),
                AllocationPleasAdded.allocationPleasAdded()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantDetails(PleaDefendantDetails.pleaDefendantDetails()
                                                .withOrganisationName("Organisation Name")
                                                .build())
                                        .withOffenceType(OffenceType.YOUTHGRAVECRIME)
                                        .withAdditionalInformation("Additional Information")
                                        .withDefendantTurningEighteenDetails("Defendant turning 18")
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );
        final AllocationPleasAdded allocationPleasAdded = envelope.payload();
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withLegalEntityDefendant(LegalEntityDefendant.legalEntityDefendant()
                                .withOrganisation(Organisation.organisation()
                                        .withName("Organisation Name")
                                        .withAddress(Address.address()
                                                .withAddress1("Org address 1")
                                                .withAddress2("Org address 2")
                                                .withAddress3("Org address 3")
                                                .withPostcode("SW1 9FP")
                                                .build())
                                        .build())
                                .build())

                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationPleasAdded(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasAdded> publicEventEnvelope = (Envelope<AllocationPleasAdded>) argumentCaptor.getValue();

        assertThat(publicEventEnvelope.payload(), is(allocationPleasAdded));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));

        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("defendantOrganisationName")
                , is("Organisation Name"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("address1")
                , is("Org address 1"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("address2")
                , is("Org address 2"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("address3")
                , is("Org address 3"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("postcode")
                , is("SW1 9FP"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Youth grave crime"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.YOUTHGRAVECRIME));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantTurningEighteenDetails(), is("Defendant turning 18"));
    }

    @Test
    public void shouldRaisePublicAllocationPleaAdded_organisation_AdultIndictableOnly_Guilty() {

        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasAdded> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-added"),
                AllocationPleasAdded.allocationPleasAdded()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantDetails(PleaDefendantDetails.pleaDefendantDetails()
                                                .withOrganisationName("Organisation Name")
                                                .build())
                                        .withOffenceType(OffenceType.ADULTINDICTABLEONLY)
                                        .withAdditionalInformation("Additional Information")
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );
        final AllocationPleasAdded allocationPleasAdded = envelope.payload();
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withLegalEntityDefendant(LegalEntityDefendant.legalEntityDefendant()
                                .withOrganisation(Organisation.organisation()
                                        .withName("Organisation Name")
                                        .withAddress(Address.address()
                                                .withAddress1("Org address 1")
                                                .withAddress2("Org address 2")
                                                .withAddress3("Org address 3")
                                                .withPostcode("SW1 9FP")
                                                .build())
                                        .build())
                                .build())
                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationPleasAdded(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasAdded> publicEventEnvelope = (Envelope<AllocationPleasAdded>) argumentCaptor.getValue();

        assertThat(publicEventEnvelope.payload(), is(allocationPleasAdded));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));

        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("defendantOrganisationName")
                , is("Organisation Name"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("address1")
                , is("Org address 1"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("address2")
                , is("Org address 2"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("address3")
                , is("Org address 3"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getJsonObject("address").getString("postcode")
                , is("SW1 9FP"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult indictable only offences"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffenceType(), is(OffenceType.ADULTINDICTABLEONLY));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getOffenceId(), is(offenceId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getOffencePleas().get(0).getIndicatedPlea(), is(IndicatedPleaValue.INDICATED_GUILTY.toString()));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getAdditionalInformation(), is("Additional Information"));
    }

    @Test
    public void shouldRaisePublicPleaAllocationUpdated_WhenDefendantDetailsNotPresent() {
        final UUID caseId = randomUUID();
        final UUID defendantId = randomUUID();
        final UUID offenceId = randomUUID();
        final Envelope<AllocationPleasUpdated> envelope = envelopeFrom(metadataWithRandomUUID("defence.event.allocation-pleas-updated"),
                AllocationPleasUpdated.allocationPleasUpdated()
                        .withPleasAllocation(
                                PleasAllocationDetails.pleasAllocationDetails()
                                        .withCaseUrn("URN1")
                                        .withCaseId(caseId)
                                        .withDefendantId(defendantId)
                                        .withDefendantNameDobConfirmation(false)
                                        .withDefendantDetails(PleaDefendantDetails.pleaDefendantDetails()
                                                .withFirstName("First")
                                                .withMiddleName("Middle")
                                                .withSurname("Last")
                                                .withDob(LocalDate.now().minusYears(20))
                                                .build())
                                        .withOffenceType(OffenceType.ADULTEITHERWAY)
                                        .withAdditionalInformation("Additional Information")
                                        .withCrownCourtObjection(YesNoNa.Y)
                                        .withOffencePleas(Arrays.asList(OffencePleaDetails.offencePleaDetails()
                                                .withOffenceId(offenceId)
                                                .withIndicatedPlea(IndicatedPleaValue.INDICATED_GUILTY.toString())
                                                .build()))
                                        .build()
                        )
                        .build()
        );

        final AllocationPleasUpdated allocationPleasUpdated = envelope.payload();
        final ProsecutionCase prosecutionCase = ProsecutionCase.prosecutionCase()
                .withId(caseId)
                .withProsecutionCaseIdentifier(ProsecutionCaseIdentifier.prosecutionCaseIdentifier()
                        .withCaseURN("SERDFXRD")
                        .build())
                .withDefendants(Arrays.asList(Defendant.defendant()
                        .withId(defendantId)
                        .withOffences(Arrays.asList(Offence.offence()
                                .withId(offenceId)
                                .withOffenceTitle("Offence Title 1")
                                .withWording("Wording 1")
                                .build()))
                        .build()))
                .build();
        final JsonObject prosecutionCaseJson = objectToJsonObjectConverter.convert(prosecutionCase);
        when(progressionService.getProsecutionCaseByCaseId(any(), any())).thenReturn(prosecutionCaseJson);
        when(documentGeneratorService.generateOpaDocument(any(), any(), any(), any(), any())).thenReturn("");
        when(usersGroupService.getUserDetails(any())).thenReturn(new UserDetails("first", "last"));

        pleaAllocationEventProcessor.handleAllocationUpdated(envelope);
        verify(sender, times(1)).send(argumentCaptor.capture());
        verify(documentGeneratorService, times(1)).generateOpaDocument(any(), jsonObjectArgumentCaptor.capture(), any(), any(), any());

        final JsonObject payloadForDocument = jsonObjectArgumentCaptor.getValue();
        final Envelope<AllocationPleasUpdated> publicEventEnvelope = (Envelope<AllocationPleasUpdated>) argumentCaptor.getValue();

        verify(sender, times(1)).sendAsAdmin(argumentCaptor.capture());
        final Envelope<?> value1 = argumentCaptor.getValue();
        final Envelope<JsonObject> envelope1 = (Envelope<JsonObject>) value1;
        assertThat(envelope1.payload().getJsonObject("courtDocument").getBoolean("sendToCps"), is(true));
        assertThat(envelope1.payload().getJsonObject("courtDocument").getString("notificationType"), is("opa-form-submitted"));

        assertThat(allocationPleasUpdated, is(publicEventEnvelope.payload()));
        assertThat(payloadForDocument.getString("caseUrn"), is("URN1"));
        assertThat(payloadForDocument.getString("submittedDate"), is(notNullValue()));
        assertThat(payloadForDocument.getString("submittedBy"), is("first last"));
        assertThat(payloadForDocument.getJsonObject(DEFENDANT_ON_OPA).getString("offenceType")
                , is("Adult either-way offences"));

        assertThat(publicEventEnvelope.payload().getPleasAllocation().getCaseId(), is(caseId));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantNameDobConfirmation(), is(false));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantDetails().getFirstName(), is("First"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantDetails().getMiddleName(), is("Middle"));
        assertThat(publicEventEnvelope.payload().getPleasAllocation().getDefendantDetails().getSurname(), is("Last"));
    }
}