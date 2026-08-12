package uk.gov.moj.cpp.defence.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.cps.defence.PleasAllocationDetails;
import uk.gov.justice.services.common.converter.ListToJsonArrayConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.persistence.DefendantAllocationRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocation;
import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocationPlea;

import jakarta.json.JsonObject;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

@ExtendWith(MockitoExtension.class)
public class DefendantAllocationServiceTest {

    @InjectMocks
    private DefendantAllocationService defendantAllocationService;
    @Mock
    private DefendantAllocationRepository repository;
    @Spy
    private ListToJsonArrayConverter<PleasAllocationDetails> listToJsonArrayConverter;

    @BeforeEach
    public void initMocks() {
        setField(this.listToJsonArrayConverter, "stringToJsonObjectConverter", new StringToJsonObjectConverter());
        setField(this.listToJsonArrayConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }

    @Test
    @SuppressWarnings("squid:S1607")
    public void shouldGetPleasByCaseId() {
        final String caseId=UUID.randomUUID().toString();
        List<DefendantAllocation> list = this.getMockDefendantAllocationList(true);
        when(repository.findDefendantAllocationByCaseId(UUID.fromString(caseId))).thenReturn(list);

        final JsonEnvelope resultPleasByCaseId = defendantAllocationService.getPleasByCaseId(caseId);

        assertNotNull(resultPleasByCaseId);
        verify(repository, times(1)).findDefendantAllocationByCaseId(UUID.fromString(caseId));
        assertEquals("true",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).get("acknowledgement").toString());
        assertEquals("Y",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("crownCourtObjection"));
        assertEquals("consentToMagistrateTrial", resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("consentToMagistratesCourtTrial"));
        assertTrue(resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getBoolean("electingCrownCourtTrial"));
        assertEquals("elect-crown-court-trial-details", resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("electingCrownCourtTrialDetails"));
        assertEquals("Y",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("sentencingIndication"));
        assertFalse(resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getBoolean("disputeOffenceValue"));
        assertEquals("offence_value_representations",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("disputeOffenceValueDetails"));
        assertTrue(resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getBoolean("youthAcknowledgement"));
        assertTrue(resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getBoolean("representationsOnGraveCrime"));
        assertEquals("representations_on_grave_crime_details",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("representationsOnGraveCrimeDetails"));
        assertTrue(resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getBoolean("defendantNameDobConfirmation"));
        assertEquals(LocalDate.now().minusYears(35).toString(),resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getJsonObject("defendantDetails").getString("dob"));
        assertEquals("John",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getJsonObject("defendantDetails").getString("firstName"));
        assertEquals("Smith",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getJsonObject("defendantDetails").getString("surname"));
        assertEquals("Company",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getJsonObject("defendantDetails").getString("organisationName"));
        assertEquals("adultIndictableOnly",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("offenceType"));
        assertEquals("additional-information",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("additionalInformation"));
        assertEquals("def_turning_eighteen_details",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("defendantTurningEighteenDetails"));
        assertEquals("Y",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("theftFromShop"));
        assertEquals("theft_from_shop_details",resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getString("theftFromShopDetails"));
    }

    private List<DefendantAllocation> getMockDefendantAllocationList(final boolean setDefendantDetails) {
        DefendantAllocation defendantAllocation = new DefendantAllocation();
        defendantAllocation.setId(UUID.randomUUID());
        defendantAllocation.setDefendantId(UUID.randomUUID());
        defendantAllocation.setCrownCourtObjection("Y");
        defendantAllocation.setConsentToMagistrateTrail("consentToMagistrateTrial");
        defendantAllocation.setElectCrownCourtTrail(true);
        defendantAllocation.setElectCrownCourtTrailDetails("elect-crown-court-trial-details");
        defendantAllocation.setSentencingIndicationRequested("Y");
        defendantAllocation.setOffenceValueDisputed(false);
        defendantAllocation.setOffenceValueRepresentations("offence_value_representations");
        defendantAllocation.setAcknowledgement(true);
        defendantAllocation.setGuardianConsentProvided(true);
        defendantAllocation.setRepresentationsOnGraveCrime(true);
        defendantAllocation.setRepresentationsOnGraveCrimeDetails("representations_on_grave_crime_details");
        defendantAllocation.setDefendantNameDobConfirmation(true);
        defendantAllocation.setOffenceType("adultIndictableOnly");
        defendantAllocation.setAdditionalInformation("additional-information");
        defendantAllocation.setDefendantTurningEighteenDetails("def_turning_eighteen_details");
        defendantAllocation.setTheftFromShop("Y");
        defendantAllocation.setTheftFromShopDetails("theft_from_shop_details");

        if(setDefendantDetails){
            defendantAllocation.setDefendantFirstName("John");
            defendantAllocation.setDefendantSurname("Smith");
            defendantAllocation.setDefendantOrganisationName("Company");
            defendantAllocation.setDefendantDateOfBirth(LocalDate.now().minusYears(35));
        }
        defendantAllocation.setDefendantAllocationPleas(Stream.of(new
                DefendantAllocationPlea(UUID.randomUUID(), LocalDate.now().plusDays(10),"indicated-plea",defendantAllocation)).collect(Collectors.toList()));
        return Stream.of(defendantAllocation).collect(Collectors.toList());
    }

    @Test
    public void shouldGetPleasByCaseId_WhenDefendantDetailsNotPresent() {
        final String caseId=UUID.randomUUID().toString();
        List<DefendantAllocation> list = this.getMockDefendantAllocationList(false);
        when(repository.findDefendantAllocationByCaseId(UUID.fromString(caseId))).thenReturn(list);

        final JsonEnvelope resultPleasByCaseId = defendantAllocationService.getPleasByCaseId(caseId);

        assertNotNull(resultPleasByCaseId);
        verify(repository, times(1)).findDefendantAllocationByCaseId(UUID.fromString(caseId));

        assertTrue(resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getBoolean("defendantNameDobConfirmation"));

        final JsonObject jsonObject = resultPleasByCaseId.payloadAsJsonObject()
                .getJsonArray("pleasAllocation").getJsonObject(0).getJsonObject("defendantDetails");

        assertNull(jsonObject);
    }
}
