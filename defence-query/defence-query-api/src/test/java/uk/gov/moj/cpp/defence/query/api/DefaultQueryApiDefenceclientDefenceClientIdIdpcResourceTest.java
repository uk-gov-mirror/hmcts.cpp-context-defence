package uk.gov.moj.cpp.defence.query.api;

import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.mock;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.api.resource.DefaultQueryApiDefenceclientDefenceClientIdIdpcResource;
import uk.gov.justice.services.common.converter.ZonedDateTimes;
import uk.gov.justice.services.core.interceptor.InterceptorChainProcessor;
import uk.gov.justice.services.core.interceptor.InterceptorContext;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.material.client.MaterialClient;
import uk.gov.moj.cpp.systemusers.ServiceContextSystemUserProvider;

import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.json.JsonObject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefaultQueryApiDefenceclientDefenceClientIdIdpcResourceTest {

    @Mock
    private MaterialClient materialClient;

    @Mock
    private InterceptorChainProcessor interceptorChainProcessor;

    @Mock
    private ServiceContextSystemUserProvider serviceContextSystemUserProvider;

    @InjectMocks
    DefaultQueryApiDefenceclientDefenceClientIdIdpcResource resource;

    @Test
    public void verifyHappyPath() {

        final UUID defenceClientId = randomUUID();
        final UUID userId = randomUUID();
        final UUID orgId = randomUUID();
        final UUID systemUserId = randomUUID();
        final UUID idpcDetatilsId = randomUUID();
        final UUID materialId = randomUUID();
        final String idpcDocumentName = randomUUID().toString() + ".pdf";


        when(serviceContextSystemUserProvider.getContextSystemUserId()).thenReturn(of(systemUserId));

        when(interceptorChainProcessor.process(any(InterceptorContext.class))).thenAnswer(invocationOnMock ->
        {
            final InterceptorContext ic = (InterceptorContext) invocationOnMock.getArguments()[0];
            JsonEnvelope envelope = ic.inputEnvelope();
            JsonObject request = envelope.payloadAsJsonObject();
            return of(JsonEnvelope.envelopeFrom(envelope.metadata(),
                    createObjectBuilder()
                            .add("defenceClientId", request.getString("defenceClientId"))
                            .add("userId", request.getString("userId"))
                            .add("organisationId", orgId.toString())
                            .add("materialId", materialId.toString())
                            .add("idpcDetailsId", idpcDetatilsId.toString())
                            .add("idpcDocumentName", idpcDocumentName)
                            .add("accessTimestamp", ZonedDateTimes.toString(ZonedDateTime.now()))
                            .build()
            ));
        });

        Response mockResponse = mock(Response.class);

        when(materialClient.getMaterialAsPdfAttachment(materialId.toString(), systemUserId.toString())).thenReturn(mockResponse);
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.readEntity(String.class)).thenReturn("filelocation");

        final JsonObject jsonObject = createObjectBuilder()
                .add("url", "filelocation")
                .build();


        Response response = resource.getDefenceclientByDefenceClientIdIdpc(defenceClientId.toString(), userId.toString());

        assertThat(response.getStatus(), is(200));
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_TYPE), is("application/json"));
        assertThat(response.getEntity(), is(jsonObject));
    }
}