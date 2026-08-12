package uk.gov.moj.defence.helper;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.defence.util.RestHelper.postCommand;

import jakarta.ws.rs.core.Response;

import org.apache.http.HttpStatus;

public class CaseAssignmentHelper {

    private static final String PATH_ADD_REMOVE_CASE_ASSIGNMENT = "/advocate";

    public static final String ADVOCATE_ASSIGN_CASE_MEDIA_TYPE = "application/vnd.defence.command.advocate.assign-case+json";
    public static final String ADVOCATE_REMOVE_ASSIGNMENT_MEDIA_TYPE = "application/vnd.defence.command.advocate.remove-case-assignment+json";

    public static void assignCaseToAdvocate(final String payload, final String userId) {
        try (Response response = postCommand(userId, payload, ADVOCATE_ASSIGN_CASE_MEDIA_TYPE, PATH_ADD_REMOVE_CASE_ASSIGNMENT)) {
            assertThat(response.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));
        }
    }

    public static void removeCaseAssignmentForAdvocate(final String payload, final String userId) {
        try (Response response = postCommand(userId, payload, ADVOCATE_REMOVE_ASSIGNMENT_MEDIA_TYPE, PATH_ADD_REMOVE_CASE_ASSIGNMENT)) {
            assertThat(response.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));
        }
    }
}
