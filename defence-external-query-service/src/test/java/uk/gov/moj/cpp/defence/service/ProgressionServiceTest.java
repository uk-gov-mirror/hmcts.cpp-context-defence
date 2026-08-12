package uk.gov.moj.cpp.defence.service;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUIDAndName;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.defence.service.ProgressionService.DELIMITER;
import static uk.gov.moj.cpp.defence.service.ProgressionService.PROSECUTOR_ID;

import uk.gov.justice.services.common.json.DefaultJsonParser;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.progression.Prosecutor;
import uk.gov.moj.cpp.defence.query.view.ProsecutionCaseAuthority;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public class ProgressionServiceTest {

    public static final String PROSECUTOR = "prosecutor";
    public static final String CASE_ID = "caseId";
    public static final String PROSECUTION_AUTHORITY_ID = "prosecutionAuthorityId";
    public static final String PROSECUTORS = "prosecutors";
    @Mock
    private Requester requester;
    @InjectMocks
    private ProgressionService progressionService;

    @BeforeEach
    public void setUpTest() {
        setField(progressionService, "progressionQueryCaseLimit", 2);
    }

    @Test
    public void shouldGetProsecutionCaseDetailsForCases() {
        final UUID caseId1 = randomUUID();
        final UUID caseId2 = randomUUID();
        final UUID caseId3 = randomUUID();
        final UUID caseId4 = randomUUID();
        final UUID caseId5 = randomUUID();

        final Metadata metadata = metadataWithRandomUUIDAndName().build();

        when(requester.requestAsAdmin(any(), any())).thenAnswer(prepareAnswer(caseId1, caseId2, caseId3, caseId4, caseId5));

        final List<Prosecutor> prosecutors = progressionService.getProsecutionCaseDetailsForCases(metadata, asList(caseId1, caseId2, caseId3, caseId4, caseId5));
        assertThat(prosecutors.size(), is(5));

    }

    @Test
    public void shouldGetProsecutionCaseAuthority() {
        final UUID caseId = randomUUID();
        final UUID prosecutionAuthorityId = randomUUID();

        final Metadata metadata = metadataWithRandomUUIDAndName().build();

        when(requester.requestAsAdmin(any(), any())).thenAnswer(
                invocationOnMock -> {
                    final Envelope envelope = (Envelope) invocationOnMock.getArguments()[0];
                    JsonObject responsePayload = createObjectBuilder()
                            .add("prosecutionCase", createObjectBuilder()
                                    .add("prosecutionCaseIdentifier", createObjectBuilder()
                                            .add("prosecutionAuthorityId", prosecutionAuthorityId.toString())
                                            .build())
                                    .build())
                            .build();
                    return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
                }
        );

        final ProsecutionCaseAuthority prosecutionCaseAuthority = progressionService.getProsecutionCaseAuthority(metadata, caseId);
        assertThat(prosecutionCaseAuthority.getProsecutionAuthorityId(), is(prosecutionAuthorityId));

    }

    @Test
    public void shouldReturnNullForProsecutionCaseAuthorityWhenCaseIdentifierIsNull() {
        final UUID caseId = randomUUID();
        final Metadata metadata = metadataWithRandomUUIDAndName().build();

        when(requester.requestAsAdmin(any(), any())).thenAnswer(
                invocationOnMock -> {
                    final Envelope envelope = (Envelope) invocationOnMock.getArguments()[0];
                    JsonObject responsePayload = createObjectBuilder()
                            .add("prosecutionCase", createObjectBuilder()
                                    .build())
                            .build();
                    return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
                }
        );

        final ProsecutionCaseAuthority prosecutionCaseAuthority = progressionService.getProsecutionCaseAuthority(metadata, caseId);
        assertThat(prosecutionCaseAuthority, nullValue());

    }

    @Test
    public void shouldGetProsecutionCaseDetailsAsJson() {
        final UUID caseId = fromString("e53b10a1-4302-4148-879a-7ed397ff2c5d");
        final Metadata metadata = metadataWithRandomUUIDAndName().build();

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final Envelope envelope = (Envelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = getPayloadFromFile("defence.query.prosecutioncase.json");
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final JsonObject prosecutors = (JsonObject) progressionService.getProsecutionCaseDetailsAsJson(metadata, caseId);
        assertThat(prosecutors.getJsonObject("prosecutionCase").getString("id"), is(caseId.toString()));

    }

    @Test
    public void shouldGetProsecutionCaseDetailsForCaag() {
        final UUID caseId = fromString("de197db7-f0cd-46c6-a588-2f266793a612");
        final Metadata metadata = metadataWithRandomUUIDAndName().build();

        when(requester.requestAsAdmin(any(JsonEnvelope.class), any())).thenAnswer(invocationOnMock -> {
            final Envelope envelope = (Envelope) invocationOnMock.getArguments()[0];
            JsonObject responsePayload = getPayloadFromFile("defence.query.prosecutioncase-caag.json");
            return JsonEnvelope.envelopeFrom(envelope.metadata(), responsePayload);
        });

        final JsonObject prosecutors = progressionService.getProsecutionCaseDetailsForCaag(metadata, caseId);
        assertThat(prosecutors.getString("caseId"), is(caseId.toString()));

    }

    private JsonObject getPayloadFromFile(final String fileName) throws IOException {
        final String payload = readFileToString(new File(Objects.requireNonNull(this.getClass().getClassLoader().getResource(fileName)).getFile()));
        return new DefaultJsonParser().toObject(payload, JsonObject.class);
    }

    @Test
    public void shouldGetProsecutionAuthorityIdMap() {
        final UUID caseId1 = randomUUID();
        final UUID caseId2 = randomUUID();
        final UUID caseId3 = randomUUID();
        final UUID caseId4 = randomUUID();
        final UUID caseId5 = randomUUID();

        final Metadata metadata = metadataWithRandomUUIDAndName().build();

        when(requester.requestAsAdmin(any(), any())).thenAnswer(prepareAnswer(caseId1, caseId2, caseId3, caseId4, caseId5));

        final Map<UUID, UUID> prosecutorsMap = progressionService.getProsecutionAuthorityIdMap(metadata, asList(caseId1, caseId2, caseId3, caseId4, caseId5));
        assertThat(prosecutorsMap.size(), is(5));

    }

    private Answer<?> prepareAnswer(final UUID caseId1, final UUID caseId2, final UUID caseId3, final UUID caseId4, final UUID caseId5) {
        return (Answer<Object>) invocationOnMock -> {
            final JsonEnvelope queryEnvelope = (JsonEnvelope) invocationOnMock.getArguments()[0];
            if (queryEnvelope.payloadAsJsonObject().getString("caseIds").equals(getCaseIdsAsString(asList(caseId1, caseId2)))) {
                return envelopeFrom(metadataWithRandomUUIDAndName(), getReturnProsecutorList(caseId1, caseId2));
            }
            if (queryEnvelope.payloadAsJsonObject().getString("caseIds").equals(getCaseIdsAsString(asList(caseId3, caseId4)))) {
                return envelopeFrom(metadataWithRandomUUIDAndName(), getReturnProsecutorList(caseId3, caseId4));
            }
            if (queryEnvelope.payloadAsJsonObject().getString("caseIds").equals(getCaseIdsAsString(singletonList(caseId5)))) {
                return envelopeFrom(metadataWithRandomUUIDAndName(), getReturnProsecutorListWithProsecutorId(caseId5));
            }
            return null;
        };
    }


    private JsonObject getReturnProsecutorList(final UUID caseId1, final UUID caseId2) {
        return createObjectBuilder()
                .add(PROSECUTORS, createArrayBuilder()
                        .add(buildProsecutor(caseId1))
                        .add(buildProsecutor(caseId2))
                        .build())
                .build();
    }

    private JsonObject buildProsecutor(final UUID caseId1) {
        return createObjectBuilder()
                .add(PROSECUTOR, createObjectBuilder()
                        .add(CASE_ID, caseId1.toString())
                        .add(PROSECUTION_AUTHORITY_ID, randomUUID().toString())
                        .build())
                .build();
    }

    private JsonObject buildProsecutorWithprosecutorId(final UUID caseId1) {
        return createObjectBuilder()
                .add(PROSECUTOR, createObjectBuilder()
                        .add(CASE_ID, caseId1.toString())
                        .add(PROSECUTOR_ID, randomUUID().toString())
                        .build())
                .build();
    }

    private JsonObject getReturnProsecutorList(final UUID caseId1) {
        return createObjectBuilder()
                .add(PROSECUTORS, createArrayBuilder()
                        .add(buildProsecutor(caseId1))
                        .build())
                .build();
    }

    private JsonObject getReturnProsecutorListWithProsecutorId(final UUID caseId5) {
        return createObjectBuilder()
                .add(PROSECUTORS, createArrayBuilder()
                        .add(buildProsecutorWithprosecutorId(caseId5))
                        .build())
                .build();
    }

    private String getCaseIdsAsString(final List<UUID> caseIds) {
        return caseIds.stream().map(Object::toString).collect(Collectors.joining(DELIMITER));
    }

}
