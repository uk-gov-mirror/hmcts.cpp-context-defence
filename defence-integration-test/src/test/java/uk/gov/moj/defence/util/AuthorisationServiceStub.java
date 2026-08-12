package uk.gov.moj.defence.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import java.util.UUID;

public class AuthorisationServiceStub {

    private static final String CAPABILITIES_QUERY_ENDPOINT = "/authorisation-service-server/rest/capabilities";
    private static final String CAPABILITY_CONTENT_TYPE = "application/vnd.authorisation.capability+json";

    public static void stubCapabilities() {
        final String resource = CAPABILITIES_QUERY_ENDPOINT + "/.*";
        stubFor(get(urlMatching(resource))
                .withHeader(ACCEPT, equalTo(CAPABILITY_CONTENT_TYPE))
                .willReturn(aResponse()
                        .withStatus(OK.getStatusCode())
                        .withHeader(ID, UUID.randomUUID().toString())
                        .withHeader(CONTENT_TYPE, CAPABILITY_CONTENT_TYPE)
                        .withBody(createObjectBuilder().add("enabled", true).build().toString()))
        );
    }
}
