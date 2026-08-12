package uk.gov.moj.cpp.defence.service;

import static com.google.common.io.Resources.getResource;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUIDAndName;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.services.common.converter.ListToJsonArrayConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.io.IOException;
import java.util.List;
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
import org.skyscreamer.jsonassert.JSONAssert;

@ExtendWith(MockitoExtension.class)
public class ListingServiceTest {

    private static final ListToJsonArrayConverter listToJsonArrayConverter = new ListToJsonArrayConverter();
    @Mock
    private Requester requester;
    @InjectMocks
    private ListingService listingService;


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
    public void shouldGetListOfHearingsWhenCaseIdAndApplicationIdIsProvided() throws JSONException {

        final Metadata metadata = metadataWithRandomUUIDAndName().build();
        final UUID caseId = randomUUID();

        final JsonObject expectedHearingsJsonObject = readJson("hearings.json", JsonObject.class);
        when(requester.requestAsAdmin(any(JsonEnvelope.class), any()))
                .thenReturn(envelopeFrom(metadataWithRandomUUIDAndName(), expectedHearingsJsonObject));

        List<Hearing> hearings = listingService.getHearings(metadata, caseId.toString());
        JsonArray jsonArray = listToJsonArrayConverter.convert(hearings);
        JSONAssert.assertEquals(jsonArray.toString(), expectedHearingsJsonObject.getJsonArray("hearings").toString(), false);

    }
}
