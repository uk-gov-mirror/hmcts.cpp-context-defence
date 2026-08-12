package uk.gov.moj.defence.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;

import java.util.UUID;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

public class AccessControlStub {

    public static void stubAccessControlForAllUsers(final boolean grantAccess, final UUID userId, final String... groupNames) {

        final JsonArrayBuilder groupsArray = createArrayBuilder();

        if (grantAccess) {
            for (String groupName : groupNames) {
                groupsArray.add(createObjectBuilder()
                        .add("groupId", randomUUID().toString())
                        .add("groupName", groupName)
                );
            }
        }

        final JsonObject response = createObjectBuilder()
                .add("groups", groupsArray).build();

        stubFor(get(urlPathMatching("/usersgroups-service/query/api/rest/usersgroups/users/[^/]*/groups"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, userId.toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(response.toString())));
    }

    public static void stubAccessControl(final boolean grantAccess, final UUID userId, final String... groupNames) {

        final JsonArrayBuilder groupsArray = createArrayBuilder();

        if (grantAccess) {
            for (String groupName : groupNames) {
                groupsArray.add(createObjectBuilder()
                        .add("groupId", randomUUID().toString())
                        .add("groupName", groupName)
                );
            }
        }

        final JsonObject response = createObjectBuilder()
                .add("groups", groupsArray).build();

        stubFor(get(urlPathMatching(format("/usersgroups-service/query/api/rest/usersgroups/users/%s/groups",userId.toString())))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, userId.toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(response.toString())));
    }

    public static void stubAccessControlForLoggedInUser(final boolean grantAccess, final UUID userId, final String prosecutingAuthority, final String... groupNames) {

        final JsonArrayBuilder groupsArray = createArrayBuilder();

        if (grantAccess) {
            for (String groupName : groupNames) {
                groupsArray.add(createObjectBuilder()
                        .add("groupId", randomUUID().toString())
                        .add("groupName", groupName)
                        .add("prosecutingAuthority", prosecutingAuthority)
                );
            }
        }

        final JsonObject response = createObjectBuilder()
                .add("groups", groupsArray).build();

        stubFor(get(urlPathMatching("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/groups"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, userId.toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(response.toString())));
    }


    public static void stubAccessControlNonCps(final boolean grantAccess, final UUID userId, final String... groupNames) {

        final JsonArrayBuilder groupsArray = createArrayBuilder();

        if (grantAccess) {
            for (String groupName : groupNames) {
                groupsArray.add(createObjectBuilder()
                        .add("groupId", randomUUID().toString())
                        .add("groupName", groupName)
                        .add("prosecutingAuthority", "DVLA")
                );
                groupsArray.add(createObjectBuilder()
                        .add("groupId", randomUUID().toString())
                        .add("groupName", "Non CPS Prosecutors")
                );
            }
        }

        final JsonObject response = createObjectBuilder()
                .add("groups", groupsArray).build();

        stubFor(get(urlPathMatching("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/groups"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, userId.toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(response.toString())));
    }

}
