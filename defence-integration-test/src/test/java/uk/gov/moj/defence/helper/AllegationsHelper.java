package uk.gov.moj.defence.helper;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static uk.gov.moj.defence.util.RestHelper.pollForResponse;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.ReadContext;
import org.hamcrest.Matcher;

public class AllegationsHelper {

    private static final String DEFENCE_ALLEGATION_QUERY_URI_PARAMETERS = "/allegations?defenceClientId=%s";

    private static final String DEFENCE_QUERY_DEFENCE_ALLEGATIONS_CONTENT_TYPE = "application/vnd.defence.query.defence-client-allegations+json";

    public static String pollForAllegations(final String defenceClientId, final UUID userId, List<Matcher<? super ReadContext>> matchers) {
        return pollForResponse(getUrlPath(defenceClientId), DEFENCE_QUERY_DEFENCE_ALLEGATIONS_CONTENT_TYPE, userId.toString(), matchers);
    }

    public static String pollForAllegationsForbidden(final String defenceClientId, final UUID userId) {
        return pollForResponse(getUrlPath(defenceClientId), DEFENCE_QUERY_DEFENCE_ALLEGATIONS_CONTENT_TYPE, userId.toString(), emptyList(), FORBIDDEN);
    }

    private static String getUrlPath(final String defenceClientId) {
        return format(DEFENCE_ALLEGATION_QUERY_URI_PARAMETERS, defenceClientId);
    }
}
