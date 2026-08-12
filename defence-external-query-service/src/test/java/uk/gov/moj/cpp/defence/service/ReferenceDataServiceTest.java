package uk.gov.moj.cpp.defence.service;

import static com.google.common.io.Resources.getResource;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUIDAndName;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.refdata.ProsecutorDetails;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReferenceDataServiceTest {
    @Mock
    private Requester requester;
    @InjectMocks
    private ReferenceDataService referenceDataService;


    @Test
    public void shouldGetProsecutionCaseDetailsForCases() {
        final UUID testAuthorityId = fromString("1c2a2913-8908-33fb-8833-0b6198fa9dc2");

        final JsonObject allProsecutorsJsonResponse = readJson("all-prosecutors.json", JsonObject.class);
        final Metadata metadata = metadataWithRandomUUIDAndName().build();

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelopeFrom(metadataWithRandomUUIDAndName(), allProsecutorsJsonResponse));

        final Map<UUID, ProsecutorDetails> prosecutorsMap = referenceDataService.getProsecutorsAsMap(metadata);
        assertThat(prosecutorsMap.size(), is(108));
        final ProsecutorDetails prosecutorDetails = prosecutorsMap.get(testAuthorityId);
        assertThat(prosecutorDetails.getProsecutionAuthorityId(), is(testAuthorityId));
        assertThat(prosecutorDetails.getIsCps(), is(true));
        assertThat(prosecutorDetails.getIsPolice(), is(false));
        assertThat(prosecutorDetails.getShortName(), is("CPS-CYN"));
    }

    @Test
    public void shouldGetProsecutor() {
        final UUID prosecutorId = fromString("1c2a2913-8908-33fb-8833-0b6198fa9dc2");

        final JsonObject allProsecutorsJsonResponse = readJson("prosecutor.json", JsonObject.class);
        final Metadata metadata = metadataWithRandomUUIDAndName().build();

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelopeFrom(metadataWithRandomUUIDAndName(), allProsecutorsJsonResponse));

        final Optional<JsonObject> prosecutor = referenceDataService.getProsecutor(metadata, prosecutorId);
        assertThat(prosecutor.get().getString("id"), is(prosecutorId.toString()));
    }

    public static <T> T readJson(final String jsonPath, final Class<T> clazz) {
        try {
            final ObjectMapper OBJECT_MAPPER = new ObjectMapperProducer().objectMapper();
            return OBJECT_MAPPER.readValue(getResource(jsonPath), clazz);
        } catch (final IOException e) {
            throw new IllegalStateException("Resource " + jsonPath + " inaccessible: " + e.getMessage());
        }
    }

}
