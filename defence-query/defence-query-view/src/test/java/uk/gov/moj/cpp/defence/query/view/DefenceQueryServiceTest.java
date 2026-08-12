package uk.gov.moj.cpp.defence.query.view;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.cps.defence.Allegations;
import uk.gov.justice.cps.defence.CaseDefendantsOrganisations;
import uk.gov.justice.cps.defence.DefenceClientId;
import uk.gov.justice.services.common.json.DefaultJsonParser;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.defence.common.Defendant;
import uk.gov.moj.cpp.defence.persistence.DefenceCaseRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.IdpcAccessHistoryRepository;
import uk.gov.moj.cpp.defence.persistence.IdpcDetailsRepository;
import uk.gov.moj.cpp.defence.persistence.entity.Allegation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceCase;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.IdpcDetails;
import uk.gov.moj.cpp.defence.persistence.entity.Instruction;

import jakarta.json.JsonObject;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.LocalDate.now;
import static java.time.LocalDate.parse;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import java.util.HashSet;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.spi.DefaultJsonMetadata.metadataBuilder;

@ExtendWith(MockitoExtension.class)
public class DefenceQueryServiceTest {

    private static final String FIRSTNAME = "FIRSTNAME";
    private static final String ORGANISATION_NAME = "Test Ltd";
    private static final String LASTNAME = "LASTNAME";
    private static final String URN = "ABCEDF";
    private static final String PROSECUTING_AUTHORITY = "TFL";
    private static final LocalDate DOB = LocalDate.now();
    private static final Optional<Boolean> IS_CIVIL = Optional.of(FALSE);

    @Mock
    IdpcDetailsRepository idpcDetailsRepository;

    @Mock
    IdpcAccessHistoryRepository idpcAccessHistoryRepository;

    @Mock
    DefenceClientRepository defenceClientRepository;

    @Mock
    DefenceCaseRepository defenceCaseRepository;

    @InjectMocks
    DefenceQueryService defenceQueryService;
    @Mock
    private Envelope envelope;
    @Mock
    private JsonEnvelope jsonEnvelope;
    @Mock
    private Requester requester;

    @Captor
    private ArgumentCaptor<JsonEnvelope> requesterCaptor;

    @Test
    public void getIdpcFileName() {
        final UUID defenceClientId = randomUUID();
        IdpcDetails idpcDetails = new IdpcDetails(null, null, getIdpcDetailsVo(),
                "SURNAME firstname 11DD0304617 IDPC");
        when(idpcDetailsRepository.findOptionalByDefenceClientId(defenceClientId)).thenReturn(idpcDetails);

        String idpcFileName = defenceQueryService.getIdpcFileName(defenceClientId);
        assertThat(idpcFileName, is("SURNAME_firstname_11DD0304617_IDPC.pdf"));

        // negative scenario, should never happen
        idpcFileName = defenceQueryService.getIdpcFileName(randomUUID());
        assertThat(idpcFileName, is("IDPC.pdf"));
    }

    @Test
    public void shouldGetClientAndIDPCAccessOrganisationsNoInstruction() {
        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(FIRSTNAME, LASTNAME, DOB, URN, IS_CIVIL.get())).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        //When
        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(
                FIRSTNAME,
                LASTNAME,
                DOB.toString(),
                URN, IS_CIVIL);

        //Then
        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
        assertTrue(defenceClientIdpcAccessOrganisations.getInstructionHistory().isEmpty());
    }

    @Test
    public void shouldFindAllegationsByDefenceClientId() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final UUID organisationId = randomUUID();
        final UUID lastOrganisation = randomUUID();
        final LocalDate chargedDate = now();
        final Metadata metadata = getMetaData(uuid, userId);
        final Allegation allegation = getAllegation(defenceClientId, chargedDate);

        when(envelope.metadata()).thenReturn(metadata);
        when(envelope.payload()).thenReturn(DefenceClientId.defenceClientId().withDefenceClientId(defenceClientId).build());

        final DefenceClient defenceClient = generateDefenceClient(defenceClientId, defenceClientId);
        defenceClient.setAssociatedOrganisation(organisationId);
        defenceClient.setLastAssociatedOrganisation(lastOrganisation);
        defenceClient.setInstructionHistory(singletonList(getInstruction(organisationId.toString(), defenceClient)));
        defenceClient.setCaseId(caseId);
        defenceClient.setAllegationList(new HashSet<>(asList(allegation)));
        when(defenceClientRepository.findBy(defenceClientId)).thenReturn(defenceClient);

        //When
        final Envelope<Allegations> allegationsEnvelope = defenceQueryService.findAllegationsByDefenceClientId(envelope);

        //Then
        final Allegations allegations = allegationsEnvelope.payload();
        assertThat(allegations.getDefenceClientId(), is(defenceClientId));
        assertThat(allegations.getAllegationDetail().size(), is(1));
        assertThat(allegations.getAllegationDetail().get(0).getChargedDate(), is(chargedDate.toString()));

    }

    @Test
    public void shouldFindIdpcMetadataForDefenceClient() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final UUID defenceClientId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(envelope.metadata()).thenReturn(metadata);
        when(envelope.payload()).thenReturn(DefenceClientId.defenceClientId().withDefenceClientId(defenceClientId).build());

        final IdpcDetails idpcDetails = new IdpcDetails();
        idpcDetails.setDefenceClientId(defenceClientId);
        idpcDetails.setId(randomUUID());
        idpcDetails.setMaterialId(randomUUID());
        idpcDetails.setDocumentName("documentName");
        idpcDetails.setPublishedDate(now());
        when(idpcDetailsRepository.findOptionalByDefenceClientId(defenceClientId)).thenReturn(idpcDetails);

        //When
        final Envelope<DefenceClientIdpcMetadata> defenceClientIdpcMetadataResponse = defenceQueryService.findIdpcMetadataForDefenceClient(envelope);

        //Then
        final DefenceClientIdpcMetadata defenceClientIdpcMetadata = defenceClientIdpcMetadataResponse.payload();
        assertThat(defenceClientIdpcMetadata.getIdpcMetadata().getDocumentName(), is(idpcDetails.getDocumentName()));

    }

    @Test
    public void shouldFindIdpcMetadataForDefendant() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final UUID defenceClientId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(envelope.metadata()).thenReturn(metadata);
        when(envelope.payload()).thenReturn(Defendant.defendant().withDefendantId(defenceClientId).build());

        final IdpcDetails idpcDetails = new IdpcDetails();
        idpcDetails.setDefenceClientId(defenceClientId);
        idpcDetails.setId(randomUUID());
        idpcDetails.setMaterialId(randomUUID());
        idpcDetails.setDocumentName("documentName");
        idpcDetails.setPublishedDate(now());
        when(idpcDetailsRepository.findIdpcDetailsForDefendantId(defenceClientId)).thenReturn(idpcDetails);

        //When
        final Envelope<DefenceClientIdpcMetadata> defenceClientIdpcMetadataResponse = defenceQueryService.findIdpcMetadataForDefendant(envelope);

        //Then
        final DefenceClientIdpcMetadata defenceClientIdpcMetadata = defenceClientIdpcMetadataResponse.payload();
        assertThat(defenceClientIdpcMetadata.getIdpcMetadata().getDocumentName(), is(idpcDetails.getDocumentName()));

    }

    @Test
    public void shouldGetCaseDefendantsWithOrganisations() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final UUID caseId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(jsonEnvelope.metadata()).thenReturn(metadata);
        when(jsonEnvelope.payloadAsJsonObject()).thenReturn(createObjectBuilder().add("caseId", caseId.toString()).build());

        final DefenceCase defenceCase = new DefenceCase();
        defenceCase.setId(caseId);
        defenceCase.setUrn("urn123");
        defenceCase.setProsecutionAuthorityCode(randomUUID().toString());

        final DefenceClient defenceClient = new DefenceClient();
        defenceClient.setCaseId(caseId);

        when(defenceCaseRepository.findBy(caseId)).thenReturn(defenceCase);
        when(defenceClientRepository.findByCaseId(caseId)).thenReturn(singletonList(defenceClient));

        //When
        final Envelope<CaseDefendantsOrganisations> caseDefendantsOrganisations = defenceQueryService.getCaseDefendantsWithOrganisations(jsonEnvelope);

        //Then
        final CaseDefendantsOrganisations caseDefendantsOrganisation = caseDefendantsOrganisations.payload();
        assertThat(caseDefendantsOrganisation.getCaseDefendantOrganisation().getCaseId(), is(caseId));

    }

    @Test
    public void shouldGetCaseDetailsByPersonDefendant() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(jsonEnvelope.metadata()).thenReturn(metadata);

        when(requester.request(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final Envelope envelope = (Envelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = getPayloadFromFile("prosecution-cases.json");
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        //When
        final JsonObject caseDefendantsOrganisations = defenceQueryService.getCaseDetailsByPersonDefendantAndHearingDate(jsonEnvelope, "firstName", "lastName", now().toString(), now().toString(), Optional.empty());

        //Then
        assertThat(caseDefendantsOrganisations.getJsonArray("prosecutionCases").size(), is(2));

    }


    @Test
    public void shouldGetCaseDetailsByOrganisationDefendant() {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();
        final Metadata metadata = getMetaData(uuid, userId);

        when(jsonEnvelope.metadata()).thenReturn(metadata);

        when(requester.request(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final Envelope envelope = (Envelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = getPayloadFromFile("prosecution-cases.json");
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        //When
        final JsonObject caseDefendantsOrganisations = defenceQueryService.getCaseDetailsByOrganisationDefendantAndHearingDate(jsonEnvelope, "organisationName", now().toString(), Optional.empty());

        //Then
        assertThat(caseDefendantsOrganisations.getJsonArray("prosecutionCases").size(), is(2));

    }


    private JsonObject getPayloadFromFile(final String fileName) throws IOException {
        final String payload = readFileToString(new File(Objects.requireNonNull(this.getClass().getClassLoader().getResource(fileName)).getFile()));
        return new DefaultJsonParser().toObject(payload, JsonObject.class);
    }


    private Allegation getAllegation(final UUID defenceClientId, final LocalDate chargedDate) {
        final Allegation allegation = new Allegation();
        allegation.setId(randomUUID());
        allegation.setDefenceClientId(defenceClientId);
        allegation.setChargeDate(chargedDate);
        return allegation;
    }


    @Test
    public void shouldGetCaseIdByUrn() {
        final UUID caseId = randomUUID();
        when(defenceCaseRepository.findOptionalByUrn(URN)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        final UUID foundCaseId = defenceQueryService.getCaseId(URN);
        assertThat(foundCaseId, is(caseId));
    }


    @Test
    public void shouldGetClientAndIDPCAccessOrganisations() {
        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        Instruction instruction = getInstruction(organisationId, defenceClient);
        defenceClient.setInstructionHistory(asList(instruction));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(FIRSTNAME, LASTNAME, DOB, URN)).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        //When
        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(
                FIRSTNAME,
                LASTNAME,
                DOB.toString(),
                URN, Optional.empty());

        //Then
        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(instruction.getOrganisationId(), defenceClientIdpcAccessOrganisations.getInstructionHistory().get(0).getOrganisationId());
    }

    @Test
    public void shouldGetClientAndIDPCAccessOrganisationsWithIsCivil() {
        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        Instruction instruction = getInstruction(organisationId, defenceClient);
        defenceClient.setInstructionHistory(asList(instruction));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(FIRSTNAME, LASTNAME, DOB, URN, IS_CIVIL.get())).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        //When
        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(
                FIRSTNAME,
                LASTNAME,
                DOB.toString(),
                URN, IS_CIVIL);

        //Then
        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(instruction.getOrganisationId(), defenceClientIdpcAccessOrganisations.getInstructionHistory().get(0).getOrganisationId());
    }

    @Test
    public void shouldGetClientAndIDPCAccessOrganisationsWithoutUrn() {
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        Instruction instruction = getInstruction(organisationId, defenceClient);
        defenceClient.setInstructionHistory(asList(instruction));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(any(), any(), any())).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(FIRSTNAME,
                LASTNAME, DOB.toString(), null, Optional.empty());

        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(instruction.getOrganisationId(), defenceClientIdpcAccessOrganisations.getInstructionHistory().get(0).getOrganisationId());
    }

    @Test
    public void shouldGetClientAndIDPCAccessOrganisationsWithoutUrnAndWithIsCivil() {
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        Instruction instruction = getInstruction(organisationId, defenceClient);
        defenceClient.setInstructionHistory(asList(instruction));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(anyString(), anyString(), any(), anyBoolean())).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(FIRSTNAME,
                LASTNAME, DOB.toString(), null, IS_CIVIL);

        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(instruction.getOrganisationId(), defenceClientIdpcAccessOrganisations.getInstructionHistory().get(0).getOrganisationId());
    }


    @Test
    public void shouldGetClientAndIDPCAccessOrganisationsWithMultipleInstruction() {
        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        Instruction instruction = getInstruction(organisationId, defenceClient);
        Instruction instruction1 = getInstruction(randomUUID().toString(), defenceClient);
        defenceClient.setInstructionHistory(asList(instruction, instruction1));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(FIRSTNAME, LASTNAME, DOB, URN)).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        //When
        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(
                FIRSTNAME,
                LASTNAME,
                DOB.toString(),
                URN, Optional.empty());

        //Then
        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(instruction.getOrganisationId(), defenceClientIdpcAccessOrganisations.getInstructionHistory().get(0).getOrganisationId());
        assertEquals(instruction1.getOrganisationId(), defenceClientIdpcAccessOrganisations.getInstructionHistory().get(1).getOrganisationId());
    }

    private Instruction getInstruction(final String organisationId, final DefenceClient defenceClient) {
        Instruction instruction = new Instruction();
        instruction.setDefenceClient(defenceClient);
        instruction.setId(randomUUID());
        instruction.setOrganisationId(fromString(organisationId));
        instruction.setUserId(randomUUID());
        return instruction;
    }

    @Test
    public void shouldGetClientAndIDPCAccessOrganisationsForOrganisation() {
        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        final Optional<Boolean> isCivil = Optional.empty();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(ORGANISATION_NAME, URN)).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        //When
        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(
                ORGANISATION_NAME,
                URN, isCivil);

        //Then
        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
    }

    @Test
    public void shouldGetClientAndIDPCAccessOrganisationsForOrganisationWithIsCivil() {
        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(ORGANISATION_NAME, URN, IS_CIVIL.get())).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        //When
        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(
                ORGANISATION_NAME,
                URN, IS_CIVIL);

        //Then
        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
    }

    @Test
    public void shouldGetClientAndIDPCAccessOrganisationsForOrganisationWithoutUrn() {
        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        final Optional<Boolean> isCivil = Optional.empty();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(any(String.class))).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(ORGANISATION_NAME, null, isCivil);

        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
    }

    @Test
    public void shouldGetClientAndIDPCAccessOrganisationsForOrganisationWithoutUrnAndWithIsCivil() {
        //Given
        final UUID defenceClientId = randomUUID();
        final UUID caseId = randomUUID();
        final String defendantId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        final String lastOrganisation = randomUUID().toString();
        setupIdpc(defenceClientId);
        DefenceClient defenceClient = generateDefenceClient(defenceClientId, fromString(defendantId));
        defenceClient.setAssociatedOrganisation(fromString(organisationId));
        defenceClient.setLastAssociatedOrganisation(fromString(lastOrganisation));
        defenceClient.setCaseId(caseId);
        when(defenceClientRepository.findDefenceClientByCriteria(anyString(), anyBoolean())).thenReturn(singletonList(defenceClient));
        when(defenceCaseRepository.findBy(caseId)).thenReturn(new DefenceCase(caseId, URN, PROSECUTING_AUTHORITY, FALSE, FALSE));

        final DefenceClientIdpcAccessOrganisations defenceClientIdpcAccessOrganisations = defenceQueryService.getClientAndIDPCAccessOrganisations(ORGANISATION_NAME, null, IS_CIVIL);

        assertEquals(defendantId, defenceClientIdpcAccessOrganisations.getDefendantId().toString());
        assertEquals(organisationId, defenceClientIdpcAccessOrganisations.getAssociatedOrganisationVO().getOrganisationId().toString());
        assertEquals(lastOrganisation, defenceClientIdpcAccessOrganisations.getLastAssociatedOrganisationVO().getOrganisationId().toString());
    }

    @Test
    public void shouldGetDefendantIdByPersonDefendantDetails() {
        final UUID defendantId = randomUUID();
        when(defenceClientRepository.getPersonDefendant(anyString(), anyString(), any())).thenReturn(asList(defendantId));
        final List<UUID> defendantIdFromDB = defenceQueryService.getPersonDefendant("John", "Thomas", now().toString());
        assertThat(defendantId, is(defendantIdFromDB.get(0)));
    }

    @Test
    public void shouldGetDefendantIdByPersonDefendantDetailsWithIsCivil() {
        final UUID defendantId = randomUUID();
        when(defenceClientRepository.getPersonDefendant(anyString(), anyString(), any(), anyBoolean(), anyBoolean())).thenReturn(asList(defendantId));
        final List<UUID> defendantIdFromDB = defenceQueryService.getPersonDefendant("John", "Thomas", now().toString(), Optional.of(TRUE));
        assertThat(defendantId, is(defendantIdFromDB.get(0)));
    }

    @Test
    public void shouldGetDefendantIdByPersonDefendantDetailsWithIsCivilWithoutDob() {
        final UUID defendantId = randomUUID();
        when(defenceClientRepository.getPersonDefendantWithOutDob(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(asList(defendantId));
        final List<UUID> defendantIdFromDB = defenceQueryService.getPersonDefendant("John", "Thomas", EMPTY, Optional.of(TRUE));
        assertThat(defendantId, is(defendantIdFromDB.get(0)));
    }

    @Test
    public void shouldGetCasesAssociatedWithDefenceClientByPersonDefendantWithIsCivil() {
        final UUID caseId = randomUUID();
        when(defenceClientRepository.findCasesAssociatedWithDefenceClientByPersonDefendant(anyString(), anyString(), any(), anyBoolean(), anyBoolean())).thenReturn(asList(caseId));
        final List<UUID> caseIdFromDB = defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant("John", "Thomas", now().toString(), Optional.of(TRUE));
        assertThat(caseId, is(caseIdFromDB.get(0)));
    }

    @Test
    public void shouldGetCasesAssociatedWithDefenceClientByPersonDefendantWithIsCivilWithoutDob() {
        final UUID caseId = randomUUID();
        when(defenceClientRepository.findCasesAssociatedWithDefenceClientByPersonDefendantWithoutDob(anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn(asList(caseId));
        final List<UUID> caseIdFromDB = defenceQueryService.getCasesAssociatedWithDefenceClientByPersonDefendant("John", "Thomas", EMPTY, Optional.of(TRUE));
        assertThat(caseId, is(caseIdFromDB.get(0)));
    }

    @Test
    public void shouldGetDefendantIdByOrganisationDefendantDetails(){
        final UUID defendantId = randomUUID();
        when(defenceClientRepository.getOrganisationDefendant(anyString())).thenReturn(asList(defendantId));
        final List<UUID> defendantIdFromDB = defenceQueryService.getOrganisationDefendant("MJ Ltd");
        assertThat(defendantId, is(defendantIdFromDB.get(0)));
    }

    @Test
    public void shouldGetDefendantIdByOrganisationDefendantDetailsWithIsCivil() {
        final UUID defendantId = randomUUID();
        when(defenceClientRepository.getOrganisationDefendant(anyString(), anyBoolean(), anyBoolean())).thenReturn(asList(defendantId));
        final List<UUID> defendantIdFromDB = defenceQueryService.getOrganisationDefendant("MJ Ltd", IS_CIVIL);
        assertThat(defendantId, is(defendantIdFromDB.get(0)));
    }

    @Test
    public void shouldGetCaseDetailsByPersonDefendantAndHearingDate(){

        final MetadataBuilder metadataBuilder = metadataBuilder()
                .withId(randomUUID())
                .withName("listing.get.cases-by-person-defendant");

        final String date = now().toString();

        final JsonObject requestPayload  = createObjectBuilder()
                .add("firstName", "Tom")
                .add("lastName", "John")
                .add("dateOfBirth", date)
                .add("hearingDate", date).build();

        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(metadataBuilder.build(),requestPayload);

        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(),  createCaseDetailRespone());
        when(requester.request(any(), any())).thenReturn(envelope);

        defenceQueryService.getCaseDetailsByPersonDefendantAndHearingDate(jsonEnvelope, "Tom", "John", LocalDate.now().toString(), LocalDate.now().toString(), Optional.empty());

        verify(requester).request(requesterCaptor.capture(), any());

        final JsonObject jsonObject = requesterCaptor.getValue().payloadAsJsonObject();

        assertThat(jsonObject.getString("firstName"), is("Tom"));
        assertThat(jsonObject.getString("lastName"), is("John"));
        assertThat(jsonObject.getString("dateOfBirth"), is(date));
        assertThat(jsonObject.getString("hearingDate"), is(date));

        verify(requester, times(1)).request(any(), any());
    }

    @Test
    public void shouldGetCaseDetailsByPersonDefendantAndHearingDateWhenIsCivilTrue() {

        final MetadataBuilder metadataBuilder = metadataBuilder()
                .withId(randomUUID())
                .withName("listing.get.cases-by-person-defendant");

        final String date = now().toString();

        final JsonObject requestPayload = createObjectBuilder()
                .add("firstName", "Tom")
                .add("lastName", "John")
                .add("dateOfBirth", date)
                .add("hearingDate", date)
                .add("isCivil", true)
                .build();

        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(metadataBuilder.build(), requestPayload);

        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), createCaseDetailRespone());
        when(requester.request(any(), any())).thenReturn(envelope);

        defenceQueryService.getCaseDetailsByPersonDefendantAndHearingDate(jsonEnvelope, "Tom", "John", LocalDate.now().toString(), LocalDate.now().toString(), Optional.of(TRUE));

        verify(requester).request(requesterCaptor.capture(), any());

        final JsonObject jsonObject = requesterCaptor.getValue().payloadAsJsonObject();

        assertThat(jsonObject.getString("firstName"), is("Tom"));
        assertThat(jsonObject.getString("lastName"), is("John"));
        assertThat(jsonObject.getString("dateOfBirth"), is(date));
        assertThat(jsonObject.getString("hearingDate"), is(date));
        assertThat(jsonObject.getBoolean("isCivil"), is(true));
        assertThat(jsonObject.getBoolean("isGroupMember"), is(false));

        verify(requester, times(1)).request(any(), any());
    }

    @Test
    public void shouldGetCaseDetailsByOrganisationDefendantAndHearingDate(){

        final MetadataBuilder metadataBuilder = metadataBuilder()
                .withId(randomUUID())
                .withName("listing.get.cases-by-organisation-defendant");

        final String date = now().toString();

        final JsonObject requestPayload  = createObjectBuilder()
                .add("organisationName", "cpp")
                .add("hearingDate", date).build();

        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(metadataBuilder.build(),requestPayload);


        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), createCaseDetailRespone());
        when(requester.request(any(), any())).thenReturn(envelope);

        defenceQueryService.getCaseDetailsByOrganisationDefendantAndHearingDate(jsonEnvelope, "cpp", LocalDate.now().toString(), Optional.empty());

        verify(requester).request(requesterCaptor.capture(), any());

        final JsonObject jsonObject = requesterCaptor.getValue().payloadAsJsonObject();

        assertThat(jsonObject.getString("organisationName"), is("cpp"));
        assertThat(jsonObject.getString("hearingDate"), is(date));

        verify(requester).request(any(), any());
    }

    @Test
    public void shouldGetCaseDetailsByOrganisationDefendantAndHearingDateWhenIsCivilTrue() {

        final MetadataBuilder metadataBuilder = metadataBuilder()
                .withId(randomUUID())
                .withName("listing.get.cases-by-organisation-defendant");

        final String date = now().toString();

        final JsonObject requestPayload = createObjectBuilder()
                .add("organisationName", "cpp")
                .add("hearingDate", date)
                .add("isCivil", true).build();

        final JsonEnvelope jsonEnvelope = JsonEnvelope.envelopeFrom(metadataBuilder.build(), requestPayload);


        final Envelope envelope = Envelope.envelopeFrom(metadataBuilder.build(), createCaseDetailRespone());
        when(requester.request(any(), any())).thenReturn(envelope);

        defenceQueryService.getCaseDetailsByOrganisationDefendantAndHearingDate(jsonEnvelope, "cpp", LocalDate.now().toString(), Optional.of(TRUE));

        verify(requester).request(requesterCaptor.capture(), any());

        final JsonObject jsonObject = requesterCaptor.getValue().payloadAsJsonObject();

        assertThat(jsonObject.getString("organisationName"), is("cpp"));
        assertThat(jsonObject.getString("hearingDate"), is(date));
        assertThat(jsonObject.getBoolean("isCivil"), is(true));
        assertThat(jsonObject.getBoolean("isGroupMember"), is(false));

        verify(requester).request(any(), any());
    }

    private JsonObject createCaseDetailRespone(){
        return createObjectBuilder()
                .add("prosecutionCases", createArrayBuilder()
                        .add(createObjectBuilder().add("caseId", randomUUID().toString())
                                .add("urn", "caseUrn").build()).build()).build();
    }

    protected void setupIdpc(final UUID defenceClientId) {
        IdpcDetails idpcDetails = new IdpcDetails(null, null, getIdpcDetailsVo(),
                "SURNAME firstname 11DD0304617 IDPC");
    }

    private uk.gov.moj.cpp.defence.IdpcDetails getIdpcDetailsVo() {
        return uk.gov.moj.cpp.defence.IdpcDetails.idpcDetails()
                .withMaterialId(randomUUID())
                .withPageCount(11)
                .withSize("11MB")
                .withPublishedDate(now())
                .build();
    }

    private DefenceClient generateDefenceClient(final UUID defenceClientId, final UUID defendantId) {
        return new DefenceClient(
                defenceClientId,
                FIRSTNAME,
                LASTNAME,
                randomUUID(),
                DOB,
                defendantId);
    }

    private Metadata getMetaData(final UUID uuid, final UUID userId) {
        return metadataBuilder()
                .withName("anyEventName")
                .withId(uuid)
                .withUserId(userId.toString())
                .build();
    }
}