package uk.gov.moj.defence.it;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import jakarta.ws.rs.core.Response;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;
import static org.skyscreamer.jsonassert.JSONCompareMode.LENIENT;
import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;
import static uk.gov.moj.defence.util.DBUtils.insertProsecutionAdvocateAccessRecords;
import static uk.gov.moj.defence.util.HttpHeaders.createHttpHeaders;

public class AutomaticUnassignmentIT {
    private final UUID userId = randomUUID();

    @Test
    public void shouldReturnExpiredProsecutorAssignmens() throws Exception {

        final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        final LocalDateTime currentDatePlusOne = LocalDateTime.now().plusDays(1);
        LocalDateTime currentDateMinusOne = ZonedDateTime.now().minusDays(1).toLocalDateTime();

        insertProsecutionAdvocateAccessRecords(currentDatePlusOne.format(dateTimeFormatter), currentDateMinusOne.format(dateTimeFormatter));
        final String queryString = "/defence-query-api/query/api/rest/defence/advocate-access";
        Response response = new RestClient().query(
                getBaseUri() + queryString,
                "application/vnd.advocate.query.expired-prosecutor-assignments+json", createHttpHeaders(userId.toString())
        );
        assertThat(response.getStatus(), is(200));
        String responseEntity = response.readEntity(String.class);
        String expectedPayload = readFileToString(new File(this.getClass().getClassLoader()
                .getResource("defence.query-advocate-assignments.json").getFile()));
        assertEquals(expectedPayload, responseEntity, new CustomComparator(LENIENT,
                new Customization("prosecutorAssignments[assigneeUserId=02f551c8-e516-4f7e-b650-8fe997259d8e].assignmentExpiryDate", (o1, o2) -> ZonedDateTime.parse(o1.toString()).toLocalDate().isEqual(currentDateMinusOne.toLocalDate())),
                new Customization("prosecutorAssignments[assigneeUserId=038d0500-3436-4320-bc68-b465534931c8].assignmentExpiryDate", (o1, o2) -> ZonedDateTime.parse(o1.toString()).toLocalDate().isEqual(currentDateMinusOne.toLocalDate()))
        ));

        // Query expired ogranisation assignments
        response = new RestClient().query(
                getBaseUri() + queryString,
                "application/vnd.advocate.query.expired-prosecutor-organisation-assignments+json", createHttpHeaders(userId.toString())
        );


        expectedPayload = readFileToString(new File(this.getClass().getClassLoader()
                .getResource("defence.query-organisation-assignments.json").getFile()));

        assertEquals(expectedPayload, response.readEntity(String.class), new CustomComparator(LENIENT,
                new Customization("organisationAssignments[assigneeOrganisationId=815db89f-c2d9-42c8-927c-af7c41d14b98].assignmentExpiryDate", (o1, o2) -> ZonedDateTime.parse(o1.toString()).toLocalDate().isEqual(currentDateMinusOne.toLocalDate()))
        ));
    }

}
