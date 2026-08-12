package uk.gov.moj.defence.helper;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.defence.util.RestHelper.pollForResponse;
import static uk.gov.moj.defence.util.RestHelper.postCommand;

import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import com.jayway.jsonpath.ReadContext;
import org.apache.http.HttpStatus;
import org.hamcrest.Matcher;

public class PleasHelper {

    private static final String PLEAS_ALLOCATION_QUERY_URI_PARAMETERS = "/pleasallocation/%s";
    private static final String PLEAS_ALLOCATION_CONTENT_TYPE = "application/vnd.defence.query.pleas-and-allocation+json";

    private static final String ADD_PLEA_COMMAND_PATH = "/defendants/%s/pleaallocation";
    private static final String UPDATE_PLEA_COMMAND_PATH = "/defendants/%s/pleaallocation/%s";
    private static final String ADD_PLEA_ALLOCATION_MEDIA_TYPE = "application/vnd.defence.add-offence-pleas+json";
    private static final String UPDATE_PLEA_ALLOCATION_MEDIA_TYPE = "application/vnd.defence.update-offence-pleas+json";

    public static String pollForDefencePleas(final String caseId, final UUID userId, List<Matcher<? super ReadContext>> matchers) {
        return pollForResponse(getQueryPath(caseId), PLEAS_ALLOCATION_CONTENT_TYPE, userId.toString(), matchers);

    }

    public static void addPleas(final String payload, final String defendantId, final String userId) {
        try (Response response = postCommand(userId, payload, ADD_PLEA_ALLOCATION_MEDIA_TYPE, getAddPleaCommandPath(defendantId))) {
            assertThat(response.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));
        }
    }


    public static void updatePleas(final String payload, final String defendantId, final String allocationId, final String userId) {
        try (Response response = postCommand(userId, payload, UPDATE_PLEA_ALLOCATION_MEDIA_TYPE, getUpdatePleaCommandPath(defendantId, allocationId))) {
            assertThat(response.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));
        }
    }

    private static String getQueryPath(final String caseId) {
        return format(PLEAS_ALLOCATION_QUERY_URI_PARAMETERS, caseId);
    }


    private static String getAddPleaCommandPath(final String defendantId) {
        return format(ADD_PLEA_COMMAND_PATH, defendantId);
    }

    private static String getUpdatePleaCommandPath(final String defendantId, final String allocationId) {
        return format(UPDATE_PLEA_COMMAND_PATH, defendantId, allocationId);
    }
}
