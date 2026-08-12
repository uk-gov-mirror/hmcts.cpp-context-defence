package uk.gov.moj.defence.it;

import static java.lang.String.format;
import static java.time.LocalDate.now;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;
import static uk.gov.moj.defence.util.AccessControlStub.stubAccessControl;
import static uk.gov.moj.defence.util.AuthorisationServiceStub.stubCapabilities;
import static uk.gov.moj.defence.util.HttpHeaders.createHttpHeaders;
import static uk.gov.moj.defence.util.UsersGroupStub.stubGetOrganisationDetailsForUser;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUserPermissions;
import static uk.gov.moj.defence.util.WiremockHelper.resetWiremock;

import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This class only tests negative scenarios. The positive flow is tested by DefenceEndToEndIT.
 */
public class DefenceInstructionQueryIT {
    private final UUID userId = randomUUID();
    private final UUID organisationId = UUID.randomUUID();
    private static final String DEFENCE_QUERY_URI_PARAMETERS =
            "/defence-command-api/command/api/rest/defence/defenceclients/%s/instruction";

    @BeforeEach
    public void setup() {
        setupExternalInterfaceMocks();
        stubUserPermissions();

    }

    protected void setupExternalInterfaceMocks() {
        resetWiremock();
        stubGetOrganisationDetailsForUser(userId, organisationId);
        stubCapabilities();
        stubAccessControl(true, userId, "Defence Lawyers");
    }

    @Test
    public void shouldReturn403WhenUserDoesNotBelongToDefenceUserGroup() {

        stubAccessControl(true, userId, "Other group");
        final Response response = postCommand(randomUUID(), now().toString());
        assertThat(response.getStatus(), is(403));
    }

    @Test
    public void shouldReturn400WhenInstructionDateInFuture() {

        final String instructionDate = now().plusDays(1).toString();
        final Response response = postCommand(randomUUID(), instructionDate);
        assertThat(response.getStatus(), is(400));
        assertThat(response.readEntity(String.class), is(format("{\"error\":\"Date is in future. Date : %s\"}", instructionDate)));
    }

    @Test
    public void shouldReturn400WhenInvalidDate() {

        final Response response = postCommand(randomUUID(), "2018-03-XX");
        assertThat(response.getStatus(), is(400));
        assertThat(response.readEntity(String.class), is("{\"error\":\"Invalid date format. Input date string: 2018-03-XX\"}"));
    }

    private Response postCommand(final UUID defenceClientId, final String instructionDate) {
        final String queryString = format(DEFENCE_QUERY_URI_PARAMETERS, defenceClientId);
        final String payload = createObjectBuilder()
                .add("instructionDate", instructionDate)
                .build()
                .toString();
        final RestClient restClient = new RestClient();

        return restClient.postCommand(getBaseUri() + queryString,
                "application/vnd.defence.record-instruction-details+json",
                payload,
                createHttpHeaders(userId.toString())
        );

    }

}
