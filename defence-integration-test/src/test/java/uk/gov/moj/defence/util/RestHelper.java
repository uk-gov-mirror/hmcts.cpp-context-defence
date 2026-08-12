package uk.gov.moj.defence.util;

import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.allOf;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.justice.services.test.utils.core.matchers.ResponseStatusMatcher.status;
import static uk.gov.moj.defence.util.HttpHeaders.createHttpHeaders;

import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import com.google.common.base.Joiner;
import com.jayway.jsonpath.ReadContext;
import org.hamcrest.Matcher;

public class RestHelper {

    private static final String BASE_URI = getBaseUri();
    private static final String READ_BASE_URL = "/defence-service/query/api/rest/defence";
    private static final String WRITE_BASE_URL = "/defence-service/command/api/rest/defence";

    public static final int TIMEOUT = 20;
    private static final int POLL_INTERVAL = 1;

    public static String pollForResponse(final String path,
                                         final String mediaType,
                                         final String userId,
                                         List<Matcher<? super ReadContext>> matchers) {
        return pollForResponse(path, mediaType, userId, matchers, OK);

    }

    public static String pollForResponse(final String path,
                                         final String mediaType,
                                         final String userId,
                                         final List<Matcher<? super ReadContext>> matchers,
                                         final Status status) {
        return poll(requestParams(getReadUrl(path), mediaType)
                .withHeader(USER_ID, userId))
                .pollInterval(POLL_INTERVAL, TimeUnit.SECONDS)
                .timeout(TIMEOUT, TimeUnit.SECONDS)
                .until(
                        status().is(status),
                        payload().isJson(allOf(matchers)))
                .getPayload();

    }

    public static Response postCommand(
            final String userId,
            final String body,
            final String mediaType,
            final String url) {

        final RestClient restClient = new RestClient();
        return restClient.postCommand(getWriteUrl(url),
                mediaType,
                body,
                createHttpHeaders(userId)
        );
    }

    public static String getReadUrl(final String resource) {
        return Joiner.on("").join(BASE_URI, READ_BASE_URL, resource);
    }

    public static String getWriteUrl(final String resource) {
        return Joiner.on("").join(BASE_URI, WRITE_BASE_URL, resource);
    }
}
