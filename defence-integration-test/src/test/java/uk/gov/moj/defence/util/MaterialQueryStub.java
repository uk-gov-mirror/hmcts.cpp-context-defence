package uk.gov.moj.defence.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;

import java.util.UUID;

public class MaterialQueryStub {

    private static final String USER_ID = UUID.randomUUID().toString();

    public static void stubForMaterialQueryPerson() {
        final String materialContent = "http://filestorage.com/myfile.pdf";

        stubFor(get(urlMatching("/material-query-api/query/api/rest/material/material/.*"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, USER_ID)
                        .withHeader(CONTENT_TYPE, "text/uri-list")
                        .withBody(materialContent)));

        stubFor(get(urlMatching("/material-service/query/api/rest/material/material/.*"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, USER_ID)
                        .withHeader(CONTENT_TYPE, "text/uri-list")
                        .withBody(materialContent)));
    }
}
