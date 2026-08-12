package uk.gov.moj.defence.util;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.String.format;
import static java.time.LocalDate.now;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.justice.services.test.utils.core.matchers.ResponseStatusMatcher.status;
import static uk.gov.moj.defence.util.HttpHeaders.createHttpHeaders;
import static uk.gov.moj.defence.util.RestHelper.pollForResponse;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.justice.services.test.utils.core.http.RestPoller;
import uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher;
import uk.gov.justice.services.test.utils.core.matchers.ResponseStatusMatcher;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.Response;

import com.jayway.jsonpath.ReadContext;
import org.hamcrest.Matcher;

public class RestQueryUtil {

    private static final String DEFENCE_QUERY_URI_PARAMETERS = "/defence-query-api/query/api/rest/defence/defenceclient/individual?firstName=%s&lastName=%s&dateOfBirth=%s&hearingDate=%s&urn=%s";
    private static final String DEFENCE_QUERY_URI_PARAMETERS_FOR_CIVIL = "/defence-query-api/query/api/rest/defence/defenceclient/individual?firstName=%s&lastName=%s&dateOfBirth=%s&hearingDate=%s&urn=%s&isCivil=%s";
    private static final String DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN = "/defence-query-api/query/api/rest/defence/defenceclient/individual?firstName=%s&lastName=%s&dateOfBirth=%s&hearingDate=%s";
    private static final String DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN_FOR_CIVIL = "/defence-query-api/query/api/rest/defence/defenceclient/individual?firstName=%s&lastName=%s&dateOfBirth=%s&hearingDate=%s&isCivil=%s";
    private static final String DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN_WITHOUT_DOB_FOR_CIVIL = "/defence-query-api/query/api/rest/defence/defenceclient/individual?firstName=%s&lastName=%s&hearingDate=%s&isCivil=%s";
    private static final String DEFENCE_CASE_QUERY_URI_PARAMETERS = "/defence-query-api/query/api/rest/defence/cases/%s/organisations/%s/assignees";
    private static final String DEFENCE_QUERY_API_QUERY_API_REST_DEFENCE_CASE = "/defence-query-api/query/api/rest/defence/case/%s";

    private static final String ORGANISATION_DEFENCE_QUERY_URI_PARAMETERS = "/defence-query-api/query/api/rest/defence/defenceclient/organisation?organisationName=%s&hearingDate=%s&urn=%s";
    private static final String ORGANISATION_DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN = "/defence-query-api/query/api/rest/defence/defenceclient/organisation?organisationName=%s&hearingDate=%s";
    private static final String ORGANISATION_DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN_FOR_CIVIL = "/defence-query-api/query/api/rest/defence/defenceclient/organisation?organisationName=%s&hearingDate=%s&isCivil=%s";

    private static final String IPDC_METADATA_QUERY_URI = "/defence-query-api/query/api/rest/defence/defenceclients/%s/idpc/metadata";

    private static final String IPDC_METADATA_FOR_DEFENDANT_QUERY_URI = "/defence-query-api/query/api/rest/defence/defendants/%s/idpc/metadata";

    private static final String IDPC_METADATA_QUERY_MEDIA_TYPE = "application/vnd.defence.query.defence-client-idpc-metadata+json";

    private static final String DEFENCE_CLIENT_QUERY_MEDIA_TYPE = "application/vnd.defence.query.defence-client-id+json";
    private static final String DEFENCE_CASE_QUERY_MEDIA_TYPE = "application/vnd.defence.query.case-organisation-assignees+json";

    private static final String DEFENCE_CLIENT_GRANTEE_PARAMETERS = "/defence-query-api/query/api/rest/defence/defenceclient/%s/grantees";

    private static final String DEFENCE_CLIENT_GRANTEE_QUERY_MEDIA_TYPE = "application/vnd.defence.query.grantees-organisation+json";

    private static final String DEFENCE_QUERY_DEFENDANT_ORGANISATIONS_BY_CASE_ID_URI_PARAMETERS = "/defence-query-api/query/api/rest/defence/cases/%s";

    private static final String DEFENCE_QUERY_DEFENDANT_ORGANISATIONS_BY_CASE_ID_MEDIA_TYPE = "application/vnd.defence.query.case-defendants-organisation+json";
    private static final String DEFENCE_QUERY_CASE_FOR_PROSECUTOR_ASSIGNMENT_MEDIA_TYPE = "application/vnd.defence.query.case-for-prosecutor-assignment+json";

    private static final String DEFENCE_CASE_QUERY_BY_PERSON_DEFENDANT = "/defence-query-api/query/api/rest/defence/case/person-defendant?firstName=%s&lastName=%s&dateOfBirth=%s";
    private static final String DEFENCE_CASE_QUERY_BY_PERSON_DEFENDANT_FOR_CIVIL = "/defence-query-api/query/api/rest/defence/case/person-defendant?firstName=%s&lastName=%s&dateOfBirth=%s&isCivil=%s&isGroupMember=%s";
    private static final String DEFENCE_CASE_QUERY_BY_PERSON_DEFENDANT_FOR_CIVIL_WITHOUT_DOB = "/defence-query-api/query/api/rest/defence/case/person-defendant?firstName=%s&lastName=%s&isCivil=%s&isGroupMember=%s";
    private static final String DEFENCE_CASE_QUERY_BY_ORGANISATION_DEFENDANT = "/defence-query-api/query/api/rest/defence/case/organisation-defendant?organisationName=%s";
    private static final String DEFENCE_CASE_QUERY_BY_ORGANISATION_DEFENDANT_FOR_CIVIL = "/defence-query-api/query/api/rest/defence/case/organisation-defendant?organisationName=%s&isCivil=%s&isGroupMember=%s";

    private static final String DEFENCE_QUERY_BY_PERSON_DEFENDANT_MEDIA_TYPE = "application/vnd.defence.query.get-case-by-person-defendant+json";
    private static final String DEFENCE_QUERY_BY_ORGANISATION_DEFENDANT_MEDIA_TYPE = "application/vnd.defence.query.get-case-by-organisation-defendant+json";

    public static final long POLL_TIMEOUT_IN_SECONDS = 20;
    private static final int POLL_INTERVAL_IN_SECONDS = 1;

    public static final ResponseStatusMatcher responseMatcher = status().is(Response.Status.OK);
    public static final ResponseStatusMatcher responseMatcherNotFound = status().is(Response.Status.NOT_FOUND);
    public static final ResponseStatusMatcher forbiddenResponseMatcher = status().is(Response.Status.FORBIDDEN);


    private RestQueryUtil() {
    }

    public static ResponseData pollIndividualDefenceClient(final String urn, final String firstName, final String lastName, final String dob, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        return pollIndividualDefenceClient(urn, firstName, lastName, dob, userId, payloadMatcher, Response.Status.OK);
    }

    public static ResponseData pollIndividualDefenceClient(final String urn, final String firstName, final String lastName, final String dob, final UUID userId, final ResponsePayloadMatcher payloadMatcher, final Response.Status status) {
        final String queryString = format(DEFENCE_QUERY_URI_PARAMETERS, firstName, lastName, dob, now(), urn);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                "application/vnd.defence.query.defence-client-id+json",
                userId.toString(),
                status().is(status),
                payloadMatcher);
    }

    public static Response queryDefenceClient(final String urn, final String firstName, final String lastName, final String dob, final UUID userId) {
        final String queryString = format(DEFENCE_QUERY_URI_PARAMETERS, firstName, lastName, dob, now(), urn);
        final RestClient restClient = new RestClient();
        return restClient.query(
                getBaseUri() + queryString,
                "application/vnd.defence.query.defence-client-id+json", createHttpHeaders(userId.toString())
        );
    }

    public static void queryForAdvocateViewAndVerifyFailure(final String urn, final UUID userId) {
        final String queryString = format(DEFENCE_QUERY_API_QUERY_API_REST_DEFENCE_CASE, urn);
        final RestClient restClient = new RestClient();
        final Response response = restClient.query(
                getBaseUri() + queryString,
                "application/vnd.advocate.query.prosecutioncase-prosecutor-caag+json", createHttpHeaders(userId.toString())
        );

        assertThat(response.getStatus(), is(403));
    }

    public static Response queryDefenceClient(final String queryString, final UUID userId) {
        final RestClient restClient = new RestClient();
        return restClient.query(
                getBaseUri() + queryString,
                "application/vnd.defence.query.defence-client-id+json", createHttpHeaders(userId.toString())
        );
    }

    public static String queryDefenceCase(final String queryString, final UUID userId, List<Matcher<? super ReadContext>> matchers) {
        return pollForResponse(queryString, DEFENCE_QUERY_CASE_FOR_PROSECUTOR_ASSIGNMENT_MEDIA_TYPE, userId.toString(), matchers);
    }

    public static Response queryIDPCMetadataForDefendant(final String defendantId, final UUID userId) {
        final String queryString = format(IPDC_METADATA_FOR_DEFENDANT_QUERY_URI, defendantId);
        final RestClient restClient = new RestClient();
        return restClient.query(
                getBaseUri() + queryString,
                "application/vnd.defence.query.defendant-idpc-metadata+json", createHttpHeaders(userId.toString())
        );
    }

    public static ResponseData pollDefenceClient(final String urn, final String firstName, final String lastName, final String dob, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defendantId", notNullValue())
                );

        return pollDefenceClientWithMatcher(urn, firstName, lastName, dob, userId, payloadMatcher);
    }

    public static ResponseData pollDefenceClientForCivil(final String urn, final String firstName, final String lastName, final String dob, final UUID userId, final boolean isCivil) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defendantId", notNullValue())
                );

        return pollDefenceClientWithMatcherForCivil(urn, firstName, lastName, dob, userId, isCivil, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithoutUrn(final String firstName, final String lastName, final String dob, String hearingDate, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defendantId", notNullValue())
                );
        return pollDefenceClientWithMatcherForPersonDefendant(firstName, lastName, dob, hearingDate, userId, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithoutUrnForCivil(final String firstName, final String lastName, final String dob, String hearingDate, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defendantId", notNullValue())
                );
        return pollDefenceClientWithMatcherForPersonDefendantForCivil(firstName, lastName, dob, hearingDate, userId, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithoutUrnWithOutDobForCivil(final String firstName, final String lastName, String hearingDate,  final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defendantId", notNullValue())
                );
        return pollDefenceClientWithMatcherForPersonDefendantWithOutDobForCivil(firstName, lastName, hearingDate, userId, payloadMatcher);
    }


    public static ResponseData pollDefenceClientForOrganisationWithoutUrn(final String organisationName, final String hearingDate, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defendantId", notNullValue())
                );
        return pollDefenceClientWithMatcherForOrganisationDefendant(organisationName, hearingDate, userId, payloadMatcher);
    }

    public static ResponseData pollDefenceClientForOrganisationWithoutUrnForCivil(final String organisationName, final String hearingDate, final UUID userId, final boolean isCivil) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defendantId", notNullValue())
                );

        return pollDefenceClientWithMatcherForOrganisationDefendantForCivil(organisationName, hearingDate, userId, payloadMatcher, isCivil);
    }

    public static ResponseData pollDefenceClientForOrganisationWithoutUrnFor404(final String organisationName, final String hearingDate, final UUID userId, final boolean isCivil) {

        return pollDefenceClientWithMatcherForOrganisationDefendantFor404(organisationName, hearingDate, userId, payload(), isCivil);
    }

    public static ResponseData pollDefenceClientForDefenceClientCountByPerson(final String firstName, final String lastName, final String dob, final String hearingDate, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defenceClientCount", notNullValue())
                );
        return pollDefenceClientWithMatcherForPersonDefendant(firstName, lastName, dob, hearingDate, userId, payloadMatcher);
    }

    public static ResponseData pollDefenceClientForDefenceClientCountByPersonNoDob(final String firstName, final String lastName, final String hearingDate, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defenceClientCount", notNullValue())
                );
        return pollDefenceClientWithMatcherForPersonDefendantWithOutDobForCivil(firstName, lastName, hearingDate, userId, payloadMatcher);
    }

    public static ResponseData pollDefenceClientForDefenceClientCountByOrganisation(final String organisationName, final String hearingDate, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defenceClientCount", notNullValue())
                );
        return pollDefenceClientWithMatcherForOrganisationDefendant(organisationName, hearingDate, userId, payloadMatcher);
    }

    public static ResponseData pollDefenceClientForLockedByRepOrder(final String urn, final String firstName, final String lastName, final String dob, final UUID userId) {
        return pollDefenceClientFor403(urn, firstName, lastName, dob, userId);
    }

    public static void pollProsecutionOrganizationAccess(final String caseId, final String organisationId, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        pollProsecutionOrganizationAccessFor200(caseId, organisationId, userId, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithMatcher(final String urn, final String firstName, final String lastName, final String dob, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_QUERY_URI_PARAMETERS, firstName, lastName, dob, now(), urn);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithMatcherForCivil(final String urn, final String firstName, final String lastName, final String dob, final UUID userId, final boolean isCivil, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_QUERY_URI_PARAMETERS_FOR_CIVIL, firstName, lastName, dob, now(), urn, isCivil);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithMatcherForPersonDefendant(final String firstName, final String lastName, final String dob, final String hearingDate, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN, firstName, lastName, dob, hearingDate);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithMatcherForPersonDefendantForCivil(final String firstName, final String lastName, final String dob, final String hearingDate, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN_FOR_CIVIL, firstName, lastName, dob, hearingDate, true);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithMatcherForPersonDefendantWithOutDobForCivil(final String firstName, final String lastName, final String hearingDate, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN_WITHOUT_DOB_FOR_CIVIL, firstName, lastName, hearingDate, true);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithMatcherForOrganisationDefendant(final String organisationName, final String hearingDate, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(ORGANISATION_DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN, organisationName, hearingDate);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollDefenceClientWithMatcherForOrganisationDefendantForCivil(final String organisationName, final String hearingDate, final UUID userId, final ResponsePayloadMatcher payloadMatcher, final boolean isCivil) {
        final String queryString = format(ORGANISATION_DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN_FOR_CIVIL, organisationName, hearingDate, isCivil);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(), isCivil ? responseMatcher : responseMatcherNotFound, isCivil ? payloadMatcher : payload());
    }

    public static ResponseData pollDefenceClientWithMatcherForOrganisationDefendantFor404(final String organisationName, final String hearingDate, final UUID userId, final ResponsePayloadMatcher payloadMatcher, final boolean isCivil) {
        final String queryString = format(ORGANISATION_DEFENCE_QUERY_URI_PARAMETERS_WITHOUT_URN_FOR_CIVIL, organisationName, hearingDate, isCivil);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(), responseMatcherNotFound, payloadMatcher);
    }

    public static ResponseData pollDefenceOrganisationWithMatcher(final UUID caseId, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_QUERY_DEFENDANT_ORGANISATIONS_BY_CASE_ID_URI_PARAMETERS, caseId);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_QUERY_DEFENDANT_ORGANISATIONS_BY_CASE_ID_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollProsecutionOrganizationAccessFor200(final String caseId, final String organisationId, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_CASE_QUERY_URI_PARAMETERS, caseId, organisationId);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CASE_QUERY_MEDIA_TYPE,
                userId.toString(),

                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollDefenceClientFor403(final String urn, final String firstName, final String lastName, final String dob, final UUID userId) {
        final String queryString = format(DEFENCE_QUERY_URI_PARAMETERS, firstName, lastName, dob, now(), urn);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(),
                forbiddenResponseMatcher);
    }

    public static ResponseData pollDefenceClientGrantee(final String defenceClientId, final UUID userId, final UUID granteeUserId) {
        final String queryString = format(DEFENCE_CLIENT_GRANTEE_PARAMETERS, defenceClientId);

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("$.grantees[0].userId", is(granteeUserId.toString()))
                );

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_GRANTEE_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollDefenceClientGranteeForRemove(final String defenceClientId, final UUID userId, final int expectedRecord) {
        final String queryString = format(DEFENCE_CLIENT_GRANTEE_PARAMETERS, defenceClientId);

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("$.grantees.size()", is(expectedRecord))
                );

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_GRANTEE_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollDefenceClient(final String urn, final String organisationName, final UUID userId) {
        final String queryString = format(ORGANISATION_DEFENCE_QUERY_URI_PARAMETERS, organisationName, now().toString(), urn);

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("defendantId", notNullValue())
                );

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_CLIENT_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }


    public static ResponseData pollIDPCMetadata(final String defenceClientId, final UUID userId) {
        final String queryString = format(IPDC_METADATA_QUERY_URI, defenceClientId);

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("idpcMetadata.documentName", notNullValue())
                );

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                IDPC_METADATA_QUERY_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);

    }

    public static ResponseData pollForMatchingResponse(final String url, final String contentType, final String userId, final Matcher<ResponseData>... matchers) {

        RequestParams requestParams = RequestParamsBuilder
                .requestParams(url, contentType)
                .withHeader("CJSCPPUID", userId)
                .build();

        return RestPoller
                .poll(requestParams)
                .pollInterval(POLL_INTERVAL_IN_SECONDS, TimeUnit.SECONDS)
                .timeout(POLL_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
                .until(matchers);
    }

    public static ResponseData pollCaseByPersonDefendant(final String firstName, final String lastName, final String dob, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("caseIds", notNullValue())
                );
        return pollForCaseByPersonDefendant(firstName, lastName, dob, userId, payloadMatcher);
    }

    public static ResponseData pollCaseByPersonDefendantForCivil(final String firstName, final String lastName, final String dob, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("caseIds", notNullValue())
                );
        return pollForCaseByPersonDefendantForCivil(firstName, lastName, dob, userId, payloadMatcher);
    }

    public static ResponseData pollCaseByPersonDefendantForCivilWithOutDob(final UUID caseId, final String firstName, final String lastName, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("$.caseIds[0]", is(caseId.toString()))
                );
        return pollForCaseByPersonDefendantForCivilWithoutDob(firstName, lastName, userId, payloadMatcher);
    }

    public static ResponseData pollCaseByOrganisationDefendant(final String organisationName, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("caseIds", notNullValue())
                );
        return pollForCaseByOrganisationDefendant(organisationName, userId, payloadMatcher);
    }

    public static ResponseData pollCaseByOrganisationDefendantForCivil(final String organisationName, final UUID userId) {

        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(
                        withJsonPath("caseIds", notNullValue())
                );
        return pollForCaseByOrganisationDefendantForCivil(organisationName, userId, payloadMatcher);
    }

    public static ResponseData pollForCaseByPersonDefendant(final String firstName, final String lastName, final String dob, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_CASE_QUERY_BY_PERSON_DEFENDANT, firstName, lastName, dob);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_QUERY_BY_PERSON_DEFENDANT_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollForCaseByPersonDefendantForCivil(final String firstName, final String lastName, final String dob, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_CASE_QUERY_BY_PERSON_DEFENDANT_FOR_CIVIL, firstName, lastName, dob, true, false);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_QUERY_BY_PERSON_DEFENDANT_MEDIA_TYPE,
                userId.toString(),
               responseMatcher, payloadMatcher);
    }

    public static ResponseData pollForCaseByPersonDefendantForCivilWithoutDob(final String firstName, final String lastName, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_CASE_QUERY_BY_PERSON_DEFENDANT_FOR_CIVIL_WITHOUT_DOB, firstName, lastName, true, false);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_QUERY_BY_PERSON_DEFENDANT_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollForCaseByOrganisationDefendant(final String organisationName, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_CASE_QUERY_BY_ORGANISATION_DEFENDANT, organisationName);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_QUERY_BY_ORGANISATION_DEFENDANT_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

    public static ResponseData pollForCaseByOrganisationDefendantForCivil(final String organisationName, final UUID userId, final ResponsePayloadMatcher payloadMatcher) {
        final String queryString = format(DEFENCE_CASE_QUERY_BY_ORGANISATION_DEFENDANT_FOR_CIVIL, organisationName, true, false);

        return pollForMatchingResponse(
                getBaseUri() + queryString,
                DEFENCE_QUERY_BY_ORGANISATION_DEFENDANT_MEDIA_TYPE,
                userId.toString(),
                responseMatcher, payloadMatcher);
    }

}
