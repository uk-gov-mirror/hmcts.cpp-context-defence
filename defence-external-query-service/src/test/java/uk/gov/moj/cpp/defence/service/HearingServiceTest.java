package uk.gov.moj.cpp.defence.service;

import static com.google.common.io.Resources.getResource;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUIDAndName;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.justice.json.schemas.hearing.Timeline;
import uk.gov.justice.services.common.converter.ListToJsonArrayConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.query.hearing.api.Hearings;

import java.io.IOException;
import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HearingServiceTest {

    private static final ListToJsonArrayConverter listToJsonArrayConverter = new ListToJsonArrayConverter();
    public static final String HEARING_TIMELINE_EXPECTED_JSON = "hearing-timeline.json";
    public static final String ALL_HEARINGS_EXPECTED_JSON = "all-hearings.json";
    @Mock
    private Requester requester;
    @InjectMocks
    private HearingService hearingService;


    @BeforeAll
    public static void initMocks() {
        setField(listToJsonArrayConverter, "stringToJsonObjectConverter", new StringToJsonObjectConverter());
        setField(listToJsonArrayConverter, "mapper", new ObjectMapperProducer().objectMapper());
    }

    public static <T> T readJson(final String jsonPath, final Class<T> clazz) {
        try {
            final ObjectMapper OBJECT_MAPPER = new ObjectMapperProducer().objectMapper();
            return OBJECT_MAPPER.readValue(getResource(jsonPath), clazz);
        } catch (final IOException e) {
            throw new IllegalStateException("Resource " + jsonPath + " inaccessible: " + e.getMessage());
        }
    }

    @Test
    public void shouldGetHearingTimelineByCaseId() throws JSONException {

        final Metadata metadata = metadataWithRandomUUIDAndName().build();
        final UUID caseId = randomUUID();

        final JsonObject expectedHearingsJsonObject = readJson(HEARING_TIMELINE_EXPECTED_JSON, JsonObject.class);
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any()))
                .thenReturn(envelopeFrom(metadataWithRandomUUIDAndName(), expectedHearingsJsonObject));

        Timeline timeline = hearingService.getHearingTimelineByCaseId(metadata, caseId);
        JsonArray jsonArray = listToJsonArrayConverter.convert(timeline.getHearingSummaries());
        assertEquals(jsonArray.toString(), expectedHearingsJsonObject.getJsonArray("hearingSummaries").toString(), true);

    }

    @Test
    public void shouldGetHearingTimelineByApplicationId() throws JSONException {

        final Metadata metadata = metadataWithRandomUUIDAndName().build();
        final UUID applicationId = randomUUID();

        final JsonObject expectedHearingsJsonObject = readJson(HEARING_TIMELINE_EXPECTED_JSON, JsonObject.class);
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any()))
                .thenReturn(envelopeFrom(metadataWithRandomUUIDAndName(), expectedHearingsJsonObject));

        Timeline timeline = hearingService.getHearingTimelineByApplicationId(metadata, applicationId);
        JsonArray jsonArray = listToJsonArrayConverter.convert(timeline.getHearingSummaries());
        assertEquals(jsonArray.toString(), expectedHearingsJsonObject.getJsonArray("hearingSummaries").toString(), true);

    }


    @Test
    public void shouldGetHearings() throws JSONException {

        final Metadata metadata = metadataWithRandomUUIDAndName().build();
        final String date = "11-02-2022";
        final String courtCentreId = "5";

        final JsonObject expectedHearingsJsonObject = readJson(ALL_HEARINGS_EXPECTED_JSON, JsonObject.class);
        when(requester.request(any(JsonEnvelope.class), any()))
                .thenReturn(envelopeFrom(metadataWithRandomUUIDAndName(), expectedHearingsJsonObject));

        Hearings hearings = hearingService.getHearings(metadata, date, courtCentreId);
        JsonArray jsonArray = listToJsonArrayConverter.convert(hearings.getHearingSummaries());
        assertEquals(jsonArray.toString(), expectedHearingsJsonObject.getJsonArray("hearingSummaries").toString(), true);

    }
}
