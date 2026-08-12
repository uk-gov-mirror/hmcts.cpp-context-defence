package uk.gov.moj.cpp.defence.event.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.cps.defence.ExpiredProsecutorAssignments;
import uk.gov.justice.cps.defence.ExpiredProsecutorOrganisationAssignments;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil;
import uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView;

import java.io.IOException;
import java.util.List;

import jakarta.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)

public class AdvocateAccessScheduledServiceTest {

    private static final String PROSECUTOR_ASSIGNMENTS_JSON_STRING = "{\n" +
            "  \"prosecutorAssignments\": [\n" +
            "    {\n" +
            "      \"assigneeUserId\": \"edea0f41-69ac-4a92-b1db-035d0532184b\",\n" +
            "      \"assignmentExpiryDate\": \"2022-05-24T18:51:51.000Z\",\n" +
            "      \"assignorOrganisationId\": \"0f53e7f8-1ff0-4ccb-8c45-8bd83c1cfbb1\",\n" +
            "      \"assignorUserId\": \"441a3c98-bf23-4779-bcb7-986b0cc00a07\",\n" +
            "      \"caseId\": \"5bc84b03-0fdc-49bd-bb47-b365ade0bb42\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"assigneeUserId\": \"ea475b5c-5e34-4cb5-ad77-df7a0f405120\",\n" +
            "      \"assignmentExpiryDate\": \"2022-05-24T18:51:51.000Z\",\n" +
            "      \"assignorOrganisationId\": \"31328e25-2ba1-4faf-8670-b2fc5c4343a9\",\n" +
            "      \"assignorUserId\": \"755f9a55-24b7-41a4-95e3-84bcf4f6f056\",\n" +
            "      \"caseId\": \"870d5ebb-b652-437b-b15d-55db95f07821\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    private static final String ORGANISATION_ASSIGNMENTS_JSON_STRING = "{\n" +
            "  \"organisationAssignments\": [\n" +
            "    {\n" +
            "      \"assigneeOrganisationId\": \"815db89f-c2d9-42c8-927c-af7c41d14b98\",\n" +
            "      \"assigneeOrganisationName\": \"Bodgit and Scarper LLP\",\n" +
            "      \"assigneeUserId\": \"9cc46992-5e7d-4eab-a65f-75cd00feecd2\",\n" +
            "      \"assignmentExpiryDate\": \"2022-05-24T19:47:08.000Z\",\n" +
            "      \"assignorOrganisationId\": \"31328e25-2ba1-4faf-8670-b2fc5c4343a9\",\n" +
            "      \"assignorOrganisationName\": \"Assignor Co Ltd\",\n" +
            "      \"assignorUserId\": \"4c4f0036-d78b-4a17-ba82-35fe0385291d\",\n" +
            "      \"caseId\": \"8b3c08e3-99f4-487f-92e0-50d8fd24825c\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    public static final String DEFENCE_COMMAND_ADVOCATE_REMOVE_CASE_ASSIGNMENT = "defence.command.handler.advocate.remove-case-assignment";
    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Mock
    private CpsCaseAccessQueryView cpsCaseAccessQueryView;

    @Mock
    private Envelope<ExpiredProsecutorAssignments> expiredProsecutorAssignmentsEnvelope;

    @Mock
    private Envelope<ExpiredProsecutorOrganisationAssignments> expiredProsecutorOrganisationAssignmentsEnvelope;

    @Mock
    private Sender sender;

    @Captor
    ArgumentCaptor<Envelope<JsonObject>> envelopeArgumentCaptor;

    @InjectMocks
    private AdvocateAccessScheduledService advocateAccessScheduledService;

    @Spy
    ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());


    @Test
    public void shouldUnassignExpiredAssignments() throws IOException {

        ReflectionUtil.setField(advocateAccessScheduledService, "expiredProsecutorAssignmentsSelectCount", "50");

        final ExpiredProsecutorOrganisationAssignments expiredProsecutorOrganisationAssignments = objectMapper.readValue(ORGANISATION_ASSIGNMENTS_JSON_STRING, ExpiredProsecutorOrganisationAssignments.class);
        when(expiredProsecutorOrganisationAssignmentsEnvelope.payload()).thenReturn(expiredProsecutorOrganisationAssignments);

        final ExpiredProsecutorAssignments expiredProsecutorAssignments = objectMapper.readValue(PROSECUTOR_ASSIGNMENTS_JSON_STRING, ExpiredProsecutorAssignments.class);
        when(expiredProsecutorAssignmentsEnvelope.payload()).thenReturn(expiredProsecutorAssignments);

        when(cpsCaseAccessQueryView.queryExpiredProsecutorAssignments(any())).thenReturn(expiredProsecutorAssignmentsEnvelope);
        when(cpsCaseAccessQueryView.queryExpiredProsecutorOrganisationAssignments(any())).thenReturn(expiredProsecutorOrganisationAssignmentsEnvelope);

        advocateAccessScheduledService.unassignExpiredAssignments();

        verify(sender, times(3)).sendAsAdmin(envelopeArgumentCaptor.capture());

        final List<Envelope<JsonObject>> allValues = envelopeArgumentCaptor.getAllValues();

        assertThat(allValues.get(0).metadata().name(), is(DEFENCE_COMMAND_ADVOCATE_REMOVE_CASE_ASSIGNMENT));
        assertThat(allValues.get(0).payload().getString("caseId"), is("5bc84b03-0fdc-49bd-bb47-b365ade0bb42"));
        assertThat(allValues.get(0).payload().getString("assigneeUserId"), is("edea0f41-69ac-4a92-b1db-035d0532184b"));

        assertThat(allValues.get(1).metadata().name(), is(DEFENCE_COMMAND_ADVOCATE_REMOVE_CASE_ASSIGNMENT));
        assertThat(allValues.get(1).payload().getString("caseId"), is("870d5ebb-b652-437b-b15d-55db95f07821"));
        assertThat(allValues.get(1).payload().getString("assigneeUserId"), is("ea475b5c-5e34-4cb5-ad77-df7a0f405120"));

        assertThat(allValues.get(2).metadata().name(), is(DEFENCE_COMMAND_ADVOCATE_REMOVE_CASE_ASSIGNMENT));
        assertThat(allValues.get(2).payload().getString("caseId"), is("8b3c08e3-99f4-487f-92e0-50d8fd24825c"));
        assertThat(allValues.get(2).payload().getString("assigneeUserId"), is("9cc46992-5e7d-4eab-a65f-75cd00feecd2"));

    }
}