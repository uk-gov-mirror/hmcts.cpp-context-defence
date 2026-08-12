package uk.gov.moj.defence.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.lang.String.valueOf;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.moj.defence.stub.StubUtil.resourceToString;

import java.util.UUID;


public class ReferenceDataStub {

    public static void stubQueryProsecutorData(final boolean policeFlagValue, final boolean cpsFlagValue) {
        final String urlPath = "/referencedata-service/query/api/rest/referencedata/prosecutors/.*";
        stubFor(get(urlMatching(urlPath))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", APPLICATION_JSON)
                        .withBody(resourceToString("stub-data/referencedata-service/referencedata.query.prosecutor.json")
                                .replaceAll("POLICE_FLAG", valueOf(policeFlagValue))
                                .replaceAll("CPS_FLAG", valueOf(cpsFlagValue)))));
    }

    public static void stubQueryProsecutorDataForNonCps(final boolean policeFlagValue, final boolean cpsFlagValue) {
        final String urlPath = "/referencedata-service/query/api/rest/referencedata/prosecutors/.*";
        stubFor(get(urlMatching(urlPath))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader("Content-Type", APPLICATION_JSON)
                        .withBody(resourceToString("stub-data/referencedata-service/referencedata.query.prosecutor.dvla.json")
                                .replaceAll("POLICE_FLAG", valueOf(policeFlagValue))
                                .replaceAll("CPS_FLAG", valueOf(cpsFlagValue)))));
    }

}
