package uk.gov.moj.defence.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.text.MessageFormat.format;
import static java.util.UUID.randomUUID;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.defence.util.TestUtils.getPayloadForCreatingRequest;

import uk.gov.justice.cps.defence.Permission;

import java.util.List;
import java.util.UUID;

import org.apache.http.HttpHeaders;

public class UsersGroupStub {


    public static final String BASE_QUERY = "/usersgroups-service/query/api/rest/usersgroups";
    public static final String COMMAND_BASE = "/usersgroups-service/command/api/rest/usersgroups";
    public static final String ORGANISATION = "/users/{0}/organisation";
    public static final String GET_ORGANISATION_QUERY = BASE_QUERY + ORGANISATION;
    public static final String GET_ORGANISATION_QUERY_MEDIA_TYPE = "application/vnd.usersgroups.get-organisation-name-for-user+json";
    public static final String GROUPS = "/users/{0}/groups";
    public static final String GET_GROUPS_QUERY = BASE_QUERY + GROUPS;
    public static final String ORGANISATION_DETAIL = "/organisations/{0}";
    public static final String GET_ORGANISATION_DETAIL_QUERY = BASE_QUERY + ORGANISATION_DETAIL;
    public static final String PERMISSION = "/permissions";
    public static final String PERMISSIONS = "/users/logged-in-user/permissions";
    public static final String SEARCH_USERS_QUERY = BASE_QUERY + "/users";
    public static final String COMMAND_PERMISSION = COMMAND_BASE + PERMISSION;
    public static final String SEARCH_PERMISSION_FOR_LOGGED_IN_USER_QUERY = BASE_QUERY + PERMISSIONS;
    public static final String SEARCH_PERMISSION_QUERY = BASE_QUERY + PERMISSION;

    public static void stubGetOrganisationDetailsForUser(final UUID userId, final UUID organisationId) {

        final String organisationDetailsForUser = getPayloadForCreatingRequest("stub-data/usersgroup-service/organisation-details.json")
                .replace("ORG-ID", organisationId.toString());

        stubFor(get(urlPathMatching("/usersgroups-query-api/query/api/rest/usersgroups/users/[^/]*/organisation"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, userId.toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(organisationDetailsForUser)));


        stubFor(get(urlPathMatching("/usersgroups-service/query/api/rest/usersgroups/users/[^/]*/organisation"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, userId.toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(organisationDetailsForUser)));
    }

    public static void stubGetOrganisationNamesForIds(final UUID userId, final List<UUID> organisationIds) {

        String organisationForIds = getPayloadForCreatingRequest("stub-data/usersgroup-service/organisation-forids.json");

        int n = 0;

        for (final UUID id : organisationIds) {
            organisationForIds = organisationForIds.replaceAll("ORG-ID-" + (n++), id.toString());
        }

        stubFor(get(urlPathMatching("/usersgroups-query-api/query/api/rest/usersgroups/organisations"))
                .withQueryParam("ids", matching(".*"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, userId.toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(organisationForIds)));


        stubFor(get(urlPathMatching("/usersgroups-service/query/api/rest/usersgroups/organisations"))
                .withQueryParam("ids", matching(".*"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, userId.toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(organisationForIds)));
    }

    public static void stubGetOrganisationNamesForGivenId(final UUID organisationId) {

        String organisationForIds = getPayloadForCreatingRequest("stub-data/usersgroup-service/organisation-forids.json");
        organisationForIds = organisationForIds.replaceAll("ORG-ID-0", organisationId.toString());

        stubFor(get(urlPathMatching("/usersgroups-query-api/query/api/rest/usersgroups/organisations?ids=" + organisationId))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, UUID.randomUUID().toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(organisationForIds)));


        stubFor(get(urlPathMatching("/usersgroups-service/query/api/rest/usersgroups/organisations?ids=" + organisationId))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, UUID.randomUUID().toString())
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(organisationForIds)));
    }

    public static void stubGetUsersAndGroupsQueryForDefenceUsers(final String userId) {
        stubEndpoint("usersgroups-service",
                GET_GROUPS_QUERY,
                GET_ORGANISATION_QUERY_MEDIA_TYPE,
                userId,
                "stub-data/usersgroup-service/usersgroups.get-defenceuser-groups-by-user.json");
    }

    public static void stubEndpoint(final String serviceName, final String query,
                                    final String queryMediaType,
                                    final String userId,
                                    final String responseBodyPath) {
        stubFor(get(urlPathEqualTo(format(query, userId)))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(getPayloadForCreatingRequest(responseBodyPath))));
    }

    public static void stubGetOrganisationQuery(final String userId, final String organisationId, final String organisationName) {
        String body = getPayloadForCreatingRequest("stub-data/usersgroup-service/usersgroups.get-organisation-details-by-user.json");
        body = body.replaceAll("%ORGANISATION_ID%", organisationId);
        body = body.replaceAll("%ORGANISATION_NAME%", organisationName);

        stubFor(get(urlPathEqualTo(format(GET_ORGANISATION_QUERY, userId)))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(body)));
    }


    public static void stubGetOrganisationDetails(final String organisationId, final String organisationName) {

        String body = getPayloadForCreatingRequest("stub-data/usersgroup-service/usersgroups.get-organisation-details-by-user.json");
        body = body.replaceAll("%ORGANISATION_ID%", organisationId);
        body = body.replaceAll("%ORGANISATION_NAME%", organisationName);

        stubFor(get(urlEqualTo(format(GET_ORGANISATION_DETAIL_QUERY, organisationId)))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(body)));
    }

    public static void stubGetUsersAndGroupsQueryForHMCTSUsers(final String userId) {
        stubEndpoint("usersgroups-service",
                GET_GROUPS_QUERY,
                GET_ORGANISATION_QUERY_MEDIA_TYPE,
                userId,
                "stub-data/usersgroup-service/usersgroups.get-hmcts-groups-by-user.json");
    }

    public static void stubGetUsersAndGroupsQueryForSystemUsers(final String userId) {
        stubEndpoint("usersgroups-service",
                GET_GROUPS_QUERY,
                GET_ORGANISATION_QUERY_MEDIA_TYPE,
                userId,
                "stub-data/usersgroup-service/usersgroups.get-systemuser-groups-by-user.json");
    }

    public static void stubUsersGroupsSearchUsersForEmail(final String userId, final String email, final String fileName) {
        String body = getPayloadForCreatingRequest(fileName);
        body = body.replaceAll("USER_ID", userId);

        stubFor(get(urlPathEqualTo(SEARCH_USERS_QUERY))
                .withQueryParam("email", equalTo(email))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(body)));
    }

    public static void stubUsersGroupsSearchUsersForUserId(final String userId, final String fileName) {
        String body = getPayloadForCreatingRequest(fileName);
        body = body.replaceAll("USER_ID", userId);

        stubFor(get(urlPathEqualTo(SEARCH_USERS_QUERY))
                .withQueryParam("userIds", equalTo(userId))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(body)));
    }

    public static void stubUsersGroupsPermissionsForUser(final String userId, final Permission permission, final String fileName) {

        String body = getPayloadForCreatingRequest(fileName);
        body = body
                .replaceAll("ACTION-1", permission.getAction())
                .replaceAll("OBJECT-1", permission.getObject())
                .replaceAll("SOURCE-1", permission.getSource().toString())
                .replaceAll("TARGET-1", permission.getTarget().toString());

        stubFor(get(urlPathEqualTo(SEARCH_PERMISSION_FOR_LOGGED_IN_USER_QUERY))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withHeader(USER_ID, userId)
                        .withBody(body)));
    }

    public static void stubUsersGroupsPermissionsQuery(final String userId, final Permission permission, final String fileName) {
        String body = getPayloadForCreatingRequest(fileName);
        body = body
                .replaceAll("ACTION-1", permission.getAction())
                .replaceAll("OBJECT-1", permission.getObject())
                .replaceAll("SOURCE-1", permission.getSource().toString())
                .replaceAll("TARGET-1", permission.getTarget().toString());

        stubFor(get(urlPathEqualTo(SEARCH_PERMISSION_QUERY))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withHeader(USER_ID, userId)
                        .withBody(body)));
    }

    public static void stubGetUserDetailsWithEmail(final String userId, final String email, final String fileName) {
        String body = getPayloadForCreatingRequest(fileName);
        body = body.replaceAll("USER_ID", userId);

        stubFor(get(urlPathEqualTo(SEARCH_USERS_QUERY))
                .withQueryParam("email", equalTo(email))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(body)));
    }

    public static void stubUsersGroupsPermission() {

        stubFor(post(urlPathMatching(COMMAND_PERMISSION))
                .willReturn(aResponse().withStatus(ACCEPTED.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(createObjectBuilder()
                                .add("id", UUID.randomUUID().toString())
                                .add("metadata", createObjectBuilder().add("name", "usersgroups.create-permission-with-details").build())
                                .build().toString())
                ));

    }

    public static void stubUserPermissions() {
        stubFor(get(urlPathMatching("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/permissions"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(getPayloadForCreatingRequest("stub-data/usersgroup-service/advocate-user-permissions.json"))
                ));

    }


    public static void stubUserWithPermission(final String userId, final String body) {
        stubFor(get(urlPathEqualTo("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/permissions"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, userId)
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(body)));
    }

}
