package uk.gov.moj.defence.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;
import static uk.gov.moj.defence.util.TestUtils.getPayloadForCreatingRequest;

import java.util.UUID;

public class ReferenceDataOffencesQueryStub {

    private static final String USER_ID = UUID.randomUUID().toString();

    public static void stubForReferenceDataQueryOffence() {

        final String refdataResponsePayLoadFor0F61131 = getPayloadForCreatingRequest("stub-data/referencedataoffences-service/referencedata.query.0F61131.json");
        final String refdataResponsePayLoadForPS90010 = getPayloadForCreatingRequest("stub-data/referencedataoffences-service/referencedata.query.PS90010.json");

        stubFor(get(urlPathEqualTo("/referencedataoffences-query-api/query/api/rest/referencedataoffences/offences"))
                .withQueryParam("cjsoffencecode", equalTo("OF61131"))
                .withQueryParam("date", equalTo("2010-08-01"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, USER_ID) // Need this ?
                        .withHeader(CONTENT_TYPE, "application/vnd.referencedataoffences.offences-list+json")
                        .withBody(refdataResponsePayLoadFor0F61131)));


        stubFor(get(urlPathEqualTo("/referencedataoffences-query-api/query/api/rest/referencedataoffences/offences"))
                .withQueryParam("cjsoffencecode", equalTo("PS90010"))
                .withQueryParam("date", equalTo("2010-08-01"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, USER_ID) //Need this ?
                        .withHeader(CONTENT_TYPE, "application/vnd.referencedataoffences.offences-list+json")
                        .withBody(refdataResponsePayLoadForPS90010)));


        stubFor(get(urlPathEqualTo("/referencedataoffences-service/query/api/rest/referencedataoffences/offences"))
                .withQueryParam("cjsoffencecode", equalTo("OF61131"))
                .withQueryParam("date", equalTo("2010-08-01"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, USER_ID) // Need this ?
                        .withHeader(CONTENT_TYPE, "application/vnd.referencedataoffences.offences-list+json")
                        .withBody(refdataResponsePayLoadFor0F61131)));


        stubFor(get(urlPathEqualTo("/referencedataoffences-service/query/api/rest/referencedataoffences/offences"))
                .withQueryParam("cjsoffencecode", equalTo("PS90010"))
                .withQueryParam("date", equalTo("2010-08-01"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, USER_ID) //Need this ?
                        .withHeader(CONTENT_TYPE, "application/vnd.referencedataoffences.offences-list+json")
                        .withBody(refdataResponsePayLoadForPS90010)));
    }
}
