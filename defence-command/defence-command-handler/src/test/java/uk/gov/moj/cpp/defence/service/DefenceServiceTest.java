package uk.gov.moj.cpp.defence.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.service.DefenceService.ASSIGNEES;
import static uk.gov.moj.cpp.defence.service.DefenceService.DEFENCE_GET_ADVOCATES_BY_CASE_ORGANISATION_QUERY;

import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView;

import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)

public class DefenceServiceTest {

    @Mock
    private JsonEnvelope responseEnvelop;

    @Mock
    private CpsCaseAccessQueryView cpsCaseAccessQueryView;

    @InjectMocks
    private DefenceService defenceService;

    @Mock
    private Envelope envelope;

    @Test
    public void shouldReturnTrueWhenAdvocatesAssignedToTheCaseAndOrganisation() {
        final String caseId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        when(cpsCaseAccessQueryView.getAssignedAdvocatesToTheCaseAndOrganisation(any())).thenReturn(responseEnvelop);
        when(responseEnvelop.asJsonObject()).thenReturn(createObjectBuilder().add(ASSIGNEES, createArrayBuilder().add(createObjectBuilder().add("assigneeId", randomUUID().toString()).build()).build()).build());

        final boolean hasAdvocatesAssignedToTheCase = defenceService.hasAdvocatesAssignedToTheCase(getRemoveCaseAssignmentJsonEnvelope(), caseId, organisationId);

        assertThat(hasAdvocatesAssignedToTheCase, is(true));
    }

    @Test
    public void shouldReturnFalseWhenNoAdvocatesAssignedToTheCaseAndOrganisation() {
        final String caseId = randomUUID().toString();
        final String organisationId = randomUUID().toString();
        when(cpsCaseAccessQueryView.getAssignedAdvocatesToTheCaseAndOrganisation(any())).thenReturn(responseEnvelop);
        when(responseEnvelop.asJsonObject()).thenReturn(createObjectBuilder().add(ASSIGNEES, createArrayBuilder().build()).build());

        final boolean hasAdvocatesAssignedToTheCase = defenceService.hasAdvocatesAssignedToTheCase(getRemoveCaseAssignmentJsonEnvelope(), caseId, organisationId);

        assertThat(hasAdvocatesAssignedToTheCase, is(false));
    }

    @Test
    public void shouldReturnFalseWhenInProsecutorRoleCalled() {
        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();

        Metadata metadata = JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName("defence.command.grant-defence-access").build();

        final JsonObject responseJsonObject = createObjectBuilder()
                .add("isAdvocateDefendingOrProsecuting", "dfdf")
                .build();

        when(cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(any())).thenReturn(responseEnvelop);
        when(responseEnvelop.payloadAsJsonObject()).thenReturn(responseJsonObject);

        final boolean isInProsecutorRole = defenceService.isInProsecutorRole(getRemoveCaseAssignmentJsonEnvelope(), caseId, userId);

        assertThat(isInProsecutorRole, is(false));
    }

    @Test
    public void shouldReturnTrueWhenInProsecutorRoleCalled() {
        final UUID caseId = randomUUID();
        final UUID userId = randomUUID();

        Metadata metadata = JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName("defence.command.grant-defence-access").build();

        final JsonObject responseJsonObject = createObjectBuilder()
                .add("isAdvocateDefendingOrProsecuting", "prosecuting")
                .build();

        when(cpsCaseAccessQueryView.findAdvocatesRoleInCaseByCaseId(any())).thenReturn(responseEnvelop);
        when(responseEnvelop.payloadAsJsonObject()).thenReturn(responseJsonObject);

        final boolean isInProsecutorRole = defenceService.isInProsecutorRole(getRemoveCaseAssignmentJsonEnvelope(), caseId, userId);

        assertThat(isInProsecutorRole, is(true));
    }

    private JsonEnvelope getRemoveCaseAssignmentJsonEnvelope() {
        return JsonEnvelope.envelopeFrom(
                JsonEnvelope.metadataBuilder()
                        .withId(randomUUID())
                        .withName(DEFENCE_GET_ADVOCATES_BY_CASE_ORGANISATION_QUERY).build(),
                createObjectBuilder()
                        .build());
    }

}