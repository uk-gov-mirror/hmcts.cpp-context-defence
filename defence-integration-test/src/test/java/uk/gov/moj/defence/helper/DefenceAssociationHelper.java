package uk.gov.moj.defence.helper;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withoutJsonPath;
import static java.text.MessageFormat.format;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.moj.defence.helper.CaseAssignmentHelper.assignCaseToAdvocate;
import static uk.gov.moj.defence.helper.PleasHelper.addPleas;
import static uk.gov.moj.defence.helper.PleasHelper.updatePleas;
import static uk.gov.moj.defence.util.RestHelper.pollForResponse;
import static uk.gov.moj.defence.util.RestHelper.postCommand;
import static uk.gov.moj.defence.util.RestQueryUtil.pollProsecutionOrganizationAccess;

import uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.ReadContext;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;

public class DefenceAssociationHelper implements AutoCloseable {

    private static final String DEFENCE_ASSOCIATION_MEDIA_TYPE = "application/vnd.defence.associate-defence-organisation+json";
    private static final String DEFENCE_ASSOCIATION_REQUEST_TEMPLATE_NAME = "stub-data/commands-payloads/defence.associate-defence-organisation.json";
    private static final String DEFENCE_ASSOCIATION_ADVOCATE_ASSIGN_CASE_REQUEST_TEMPLATE_NAME = "stub-data/commands-payloads/defence.associate-advocate-assign-case.json";

    private static final String ADD_PLEA_ALLOCATION_REQUEST_TEMPLATE_NAME = "stub-data/commands-payloads/defence.add-offence-pleas.json";
    private static final String ADD_PLEA_ALLOCATION_REQUEST_FOR_ORG_TEMPLATE_NAME = "stub-data/commands-payloads/defence.add-offence-pleas-for-org.json";

    private static final String DEFENCE_DISASSOCIATION_MEDIA_TYPE = "application/vnd.defence.disassociate-defence-organisation+json";
    private static final String DEFENCE_DISASSOCIATION_REQUEST_TEMPLATE_NAME = "stub-data/commands-payloads/defence.disassociate-defence-organisation.json";

    private static final String DEFENCE_ASSOCIATION_QUERY_ENDPOINT = "/defendants/{0}/associatedOrganisation";
    private static final String DEFENCE_ASSOCIATION_QUERY_MEDIA_TYPE = "application/vnd.defence.query.associated-organisation+json";
    private static final String DEFENCE_ASSOCIATIONS_QUERY_ENDPOINT = "/defendants/{0}/associatedOrganisations";
    private static final String DEFENCE_ASSOCIATIONS_QUERY_MEDIA_TYPE = "application/vnd.defence.query.associated-organisations+json";


    public static void associateOrganisation(final String defendantId,
                                             final String userId) throws IOException {
        String body = readFile(DEFENCE_ASSOCIATION_REQUEST_TEMPLATE_NAME);
        try (Response response = invokeCommand(defendantId, userId, body, DEFENCE_ASSOCIATION_MEDIA_TYPE)) {
            assertThat(response.getStatus(), equalTo(HttpStatus.SC_ACCEPTED));
        }
    }

    public static void associateAdvocateAssignCase(final String userId, final String caseId) throws IOException {
        String body = readFile(DEFENCE_ASSOCIATION_ADVOCATE_ASSIGN_CASE_REQUEST_TEMPLATE_NAME)
                .replaceAll("CASE_ID", caseId)
                .replaceAll("ASSIGNOOR_ID", userId);

        assignCaseToAdvocate(body, userId);
    }

    public static void addPleaAllocation(final String defendantId,
                                         final String userId, String allocationId) throws IOException {
        String body = readFile(ADD_PLEA_ALLOCATION_REQUEST_TEMPLATE_NAME);
        body = body.replace("OFFENCE_ID", randomUUID().toString());
        body = body.replace("ALLOCATION_ID", allocationId);
        addPleas(body, defendantId, userId);
    }

    public static void addPleaAllocationForOrganisation(final String defendantId,
                                                        final String userId, String allocationId) throws IOException {
        String body = readFile(ADD_PLEA_ALLOCATION_REQUEST_FOR_ORG_TEMPLATE_NAME);
        body = body.replace("OFFENCE_ID", randomUUID().toString());
        body = body.replace("ALLOCATION_ID", allocationId);
        addPleas(body, defendantId, userId);
    }

    public static void updatePleaAllocation(final String defendantId,
                                            final String userId, final String allocationId, final String fileName) throws IOException {
        String body = readFile(fileName);
        body = body.replace("OFFENCE_ID", randomUUID().toString());
        body = body.replace("ALLOCATION_ID", allocationId);
        updatePleas(body, defendantId, allocationId, userId);
    }

    public static Response invokeDisassociateOrganisation(final String defendantId,
                                                          final String userId,
                                                          final String organisationId, final String caseId) throws IOException {
        String body = readFile(DEFENCE_DISASSOCIATION_REQUEST_TEMPLATE_NAME);
        body = body.replaceAll("%ORGANISATION_ID%", organisationId).replaceAll("%CASE_ID%", caseId);
        return invokeCommand(defendantId, userId, body, DEFENCE_DISASSOCIATION_MEDIA_TYPE);
    }

    private static Response invokeCommand(final String defendantId,
                                          final String userId,
                                          final String body,
                                          final String mediaType) {

        return postCommand(userId, body, mediaType, "/defendants/" + defendantId + "/defenceorganisation");

    }

    public static void verifyDefenceOrganisationAssociatedDataPersisted(final String defendantId,
                                                                        final String organisationId,
                                                                        final String userId) {

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.association.organisationId", is(organisationId)))
                .add(withJsonPath("$.association.status", IsEqual.equalTo("Active Barrister/Solicitor of record")))
                .add(withJsonPath("$.association.address.address1", IsEqual.equalTo("Legal House")))
                .add(withJsonPath("$.association.address.address4", IsEqual.equalTo("London")))
                .add(withJsonPath("$.association.address.addressPostcode", IsEqual.equalTo("SE14 2AB")))
                .add(withJsonPath("$.association.representationType", IsEqual.equalTo("REPRESENTATION_ORDER")))
                .build();

        pollForResponse(format(DEFENCE_ASSOCIATION_QUERY_ENDPOINT, defendantId),
                DEFENCE_ASSOCIATION_QUERY_MEDIA_TYPE,
                userId,
                matchers);
    }

    public static void verifyDefenceAssociationUsingGetAllAssociationsEndpoint(final String defendantId,
                                                                               final String organisationId,
                                                                               final String userId) {

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.associations.[0].organisationId", IsEqual.equalTo(organisationId)))
                .add(withJsonPath("$.associations.[0].status", IsEqual.equalTo("Active Barrister/Solicitor of record")))
                .add(withJsonPath("$.associations.[0].address.address1", IsEqual.equalTo("Legal House")))
                .add(withJsonPath("$.associations.[0].address.address4", IsEqual.equalTo("London")))
                .add(withJsonPath("$.associations.[0].address.addressPostcode", IsEqual.equalTo("SE14 2AB")))
                .add(withJsonPath("$.associations.[0].representationType", IsEqual.equalTo("REPRESENTATION_ORDER")))
                .build();

        pollForResponse(format(DEFENCE_ASSOCIATIONS_QUERY_ENDPOINT, defendantId),
                DEFENCE_ASSOCIATIONS_QUERY_MEDIA_TYPE,
                userId,
                matchers);
    }


    public static void verifyDefenceOrganisationDissociatedDataPersisted(final String defendantId,
                                                                         final String userId) {

        List<Matcher<? super ReadContext>> matchers = ImmutableList.<Matcher<? super ReadContext>>builder()
                .add(withJsonPath("$.association"))
                .add(withoutJsonPath("$.association.organisationId"))
                .build();

        pollForResponse(format(DEFENCE_ASSOCIATION_QUERY_ENDPOINT, defendantId),
                DEFENCE_ASSOCIATION_QUERY_MEDIA_TYPE,
                userId,
                matchers);
    }

    private static String readFile(final String filename) throws IOException {
        return IOUtils.toString(DefenceAssociationHelper.class.getClassLoader().getResourceAsStream(filename));
    }

    public static void verifyProsecutionOrganizationAndAdvocateAccessDataPersisted(final String caseId, final String organisationId, final String userId) {
        final ResponsePayloadMatcher payloadMatcher = payload()
                .isJson(Matchers.allOf(
                                withJsonPath("$.assignees[0].assigneeUserId", Matchers.equalTo(userId)),
                                withJsonPath("$.assignees[0].assigneeName", Matchers.equalTo("Richard Chapman")),
                                withJsonPath("$.assignees[0].assigneeOrganisationId", Matchers.equalTo(organisationId)),
                                withJsonPath("$.assignees[0].assigneeOrganisationName", Matchers.equalTo("Bodgit and Scarper LLP")),
                                withJsonPath("$.assignees[0].representation", Matchers.equalTo("PROSECUTION")),
                                withJsonPath("$.assignees[0].representing", Matchers.equalTo("CPS"))
                        )
                );

        pollProsecutionOrganizationAccess(caseId, organisationId, UUID.fromString(userId), payloadMatcher);
    }

    @Override
    public void close() {

    }
}
