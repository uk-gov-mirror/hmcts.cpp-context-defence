package uk.gov.moj.defence.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;

import java.util.UUID;

import jakarta.json.JsonObject;

public class ProsecutionCaseQueryStub {

    private static final String PROGRESSION_PROSECUTION_CASE_QUERY_MEDIA_TYPE = "application/vnd.progression.query.prosecutioncase+json";

    public static void stubForProsecutionCaseQuery() {

        final JsonObject jsonObject = createObjectBuilder().add("prosecutionCase", createObjectBuilder().add("prosecutor",
                createObjectBuilder().add("prosecutorId", UUID.randomUUID().toString())
                        .add("prosecutorCode", "CPS").build())
                .build()).build();

        stubFor(get(urlMatching("/progression-service/query/api/rest/progression/prosecutioncases/.*"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(CONTENT_TYPE, PROGRESSION_PROSECUTION_CASE_QUERY_MEDIA_TYPE)
                        .withBody(jsonObject.toString())));
    }
}
