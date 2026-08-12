package uk.gov.moj.cpp.defence.command.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.cps.defence.AssignCase;
import uk.gov.justice.cps.defence.AssignCaseByHearing;
import uk.gov.justice.cps.defence.CaseHearings;
import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.moj.cpp.defence.command.error.OrganisationNotProsecutingAuthorityException;
import uk.gov.moj.cpp.defence.command.service.DefenceService;
import uk.gov.moj.cpp.defence.query.view.ProsecutionCaseAuthority;
import uk.gov.moj.cpp.defence.service.ProgressionService;
import uk.gov.moj.cpp.defence.service.ReferenceDataService;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.defence.service.UsersGroupQueryService;


import jakarta.json.JsonObject;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.collect.ImmutableList.of;
import static java.lang.Integer.parseInt;
import static java.time.ZonedDateTime.now;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.defence.command.api.CaseAccessCommandApi.DEFENCE_COMMAND_HANDLER_SYSTEM_SCHEDULE_ADVOCATE_ACCESS;
import static uk.gov.moj.cpp.defence.command.api.CaseAccessCommandApi.ERROR_CODE;
import static uk.gov.moj.cpp.defence.command.api.CaseAccessCommandApi.FAILURE_REASON;
import static uk.gov.moj.cpp.defence.common.util.ErrorType.CASE_NOT_FOUND;
import static uk.gov.moj.cpp.defence.common.util.ErrorType.ORGANISATION_NOT_PROSECUTING_AUTHORITY;

@ExtendWith(MockitoExtension.class)
public class CaseAccessCommandApiTest {

    private static final String EMAIL = "mail@hmcts.net";
    @Spy
    private final ObjectToJsonObjectConverter objectToJsonObjectConverter = new ObjectToJsonObjectConverter(new ObjectMapperProducer().objectMapper());
    @Spy
    private final ObjectMapper mapper = new ObjectMapperProducer().objectMapper();
    @Mock
    private Sender sender;
    @InjectMocks
    private CaseAccessCommandApi caseAccessCommandApi;
    @Captor
    private ArgumentCaptor<Envelope<JsonObject>> envelopeArgumentCaptor;
    @Mock
    private DefenceService defenceService;
    @Mock
    private Envelope<AssignCase> assignCaseEnvelope;

    @Mock
    private UserGroupService usersGroupService;

    @Mock
    private ProgressionService progressionService;

    @Mock
    private Requester requester;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private UsersGroupQueryService usersGroupQueryService;

    @BeforeEach
    public void setup() {
        setField(objectToJsonObjectConverter, "mapper", mapper);
    }

    @Test
    public void shouldHandleAssignCaseForNonCpsOrganisationMisMatch() {
        UUID userId = randomUUID();
        UUID prosecutingAuthorityId = randomUUID();
        final AssignCase assignCase = new AssignCase(EMAIL, randomUUID(), randomUUID(), of(randomUUID()));
        when(assignCaseEnvelope.payload()).thenReturn(assignCase);
        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName("defence.command.advocate.assign-case")
                .createdAt(now()).build();
        when(assignCaseEnvelope.metadata()).thenReturn(metadata);
        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.of("OrganisationMisMatch"));
        when(usersGroupService.getUserDetailsWithEmail(EMAIL, assignCaseEnvelope.metadata(), requester)).thenReturn(PersonDetails.personDetails().withUserId(userId).build());
        when(progressionService.getProsecutionCaseAuthority(assignCaseEnvelope.metadata(), assignCase.getCaseIds().get(0))).thenReturn(ProsecutionCaseAuthority.prosecutionCaseAuthority()
                .withProsecutionAuthorityId(prosecutingAuthorityId).build());
        when(referenceDataService.getProsecutor(assignCaseEnvelope.metadata(), prosecutingAuthorityId)).thenReturn(Optional.of(createObjectBuilder().add("cpsFlag", true).add("policeFlag", true).add("shortName", "DVLA").build()));
        assertThrows(OrganisationNotProsecutingAuthorityException.class, () -> caseAccessCommandApi.assignCase(assignCaseEnvelope));
    }

    @Test
    public void shouldHandleAssignCaseForOrganisationMisMatchCps() {
        final AssignCase assignCase = new AssignCase(EMAIL, randomUUID(), randomUUID(), of(randomUUID()));
        when(assignCaseEnvelope.payload()).thenReturn(assignCase);
        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName("defence.command.advocate.assign-case")
                .createdAt(now()).build();
        when(assignCaseEnvelope.metadata()).thenReturn(metadata);
        UUID userId = randomUUID();
        UUID prosecutingAuthorityId = randomUUID();
        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.empty());
        when(usersGroupService.getUserDetailsWithEmail(EMAIL, assignCaseEnvelope.metadata(), requester)).thenReturn(PersonDetails.personDetails().withUserId(userId).build());
        when(progressionService.getProsecutionCaseAuthority(assignCaseEnvelope.metadata(), assignCase.getCaseIds().get(0))).thenReturn(ProsecutionCaseAuthority.prosecutionCaseAuthority()
                .withProsecutionAuthorityId(prosecutingAuthorityId).build());
        when(referenceDataService.getProsecutor(assignCaseEnvelope.metadata(), prosecutingAuthorityId)).thenReturn(Optional.of(createObjectBuilder().add("shortName", "DVLA").build()));
        assertThrows(OrganisationNotProsecutingAuthorityException.class, () -> caseAccessCommandApi.assignCase(assignCaseEnvelope));
    }

    @Test
    public void shouldHandleRemoveCaseAssignment() {
        caseAccessCommandApi.removeCaseAssignment(getRemoveCaseAssignmentEnvelope());

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is("defence.command.handler.advocate.remove-case-assignment"));
    }

    @Test
    public void shouldHandleScheduleSystemAdvocateAccess() {
        final Metadata metadata = getMetadataWithName("defence.command.system-schedule-advocate-access");
        final JsonEnvelope jsonEnvelope =  new   DefaultJsonEnvelopeProvider().envelopeFrom(metadata, createObjectBuilder()
                .build());
        caseAccessCommandApi.scheduleSystemAdvocateAccess(jsonEnvelope);
        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is(DEFENCE_COMMAND_HANDLER_SYSTEM_SCHEDULE_ADVOCATE_ACCESS));
    }

    private JsonEnvelope getRemoveCaseAssignmentEnvelope() {
        final Metadata metadata = getMetadataWithName("defence.advocate.remove-case-assignment");
        return new DefaultJsonEnvelopeProvider().envelopeFrom(metadata, createObjectBuilder()
                .add("assigneeId", randomUUID().toString())
                .build());
    }

    private  Metadata  getMetadataWithName(final String name) {
        return  Envelope
                .metadataBuilder()
                .withName(name)
                .withId(randomUUID())
                .withUserId(randomUUID().toString())
                .build();
    }

    @Test
    public void shouldHandleAssignCaseByHearing() {
        final UUID userId = randomUUID();
        final AssignCaseByHearing assignCaseByHearing = new AssignCaseByHearing(EMAIL, randomUUID(), randomUUID(), of(new CaseHearings(randomUUID(), randomUUID())));
        final Envelope<AssignCaseByHearing> envelope = createEnvelopeForCaseAccess(EMAIL, assignCaseByHearing, userId);
        final UUID prosecutingAuthorityId = randomUUID();

        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.empty());
        when(usersGroupService.getUserDetailsWithEmail(EMAIL, envelope.metadata(), requester)).thenReturn(PersonDetails.personDetails().withUserId(userId).build());
        when(progressionService.getProsecutionCaseAuthority(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId())).thenReturn(ProsecutionCaseAuthority.prosecutionCaseAuthority()
                .withProsecutionAuthorityId(prosecutingAuthorityId).build());
        when(defenceService.isAssigneeDefendingTheCase(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId(), userId)).thenReturn(false);
        when(referenceDataService.getProsecutor(envelope.metadata(), prosecutingAuthorityId)).thenReturn(Optional.of(createObjectBuilder().add("cpsFlag", true).add("policeFlag", true).add("shortName", "DVLA").build()));

        caseAccessCommandApi.assignCaseByHearingListing(envelope);

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is("defence.command.handler.advocate.assign-case-by-hearing-listing"));
        assertThat(envelopeArgumentCaptorValue.payload().getString("assigneeEmailId"), is(EMAIL));
        JsonObject caseHearingAssignmentDetails = (JsonObject) envelopeArgumentCaptorValue.payload().getJsonArray("caseHearingAssignmentDetails").get(0);
        assertThat(caseHearingAssignmentDetails.getString("hearingId"), is(assignCaseByHearing.getCaseHearings().get(0).getHearingId().toString()));
        assertCaseAssignmentDetails(caseHearingAssignmentDetails, assignCaseByHearing.getCaseHearings().get(0).getCaseId(), prosecutingAuthorityId);
    }

    @Test
    public void shouldHandleAssignCaseByHearingNonCpsOrgMisMatch() {
        final UUID userId = randomUUID();
        final AssignCaseByHearing assignCaseByHearing = new AssignCaseByHearing(EMAIL, randomUUID(), randomUUID(), of(new CaseHearings(randomUUID(), randomUUID())));
        final Envelope<AssignCaseByHearing> envelope = createEnvelopeForCaseAccess(EMAIL, assignCaseByHearing, userId);
        final UUID prosecutingAuthorityId = randomUUID();

        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.of("OrganisationMisMatch"));
        when(usersGroupService.getUserDetailsWithEmail(EMAIL, envelope.metadata(), requester)).thenReturn(PersonDetails.personDetails().withUserId(userId).build());
        when(progressionService.getProsecutionCaseAuthority(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId())).thenReturn(ProsecutionCaseAuthority.prosecutionCaseAuthority()
                .withProsecutionAuthorityId(prosecutingAuthorityId).build());
        when(defenceService.isAssigneeDefendingTheCase(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId(), userId)).thenReturn(false);
        when(referenceDataService.getProsecutor(envelope.metadata(), prosecutingAuthorityId)).thenReturn(Optional.of(createObjectBuilder().add("cpsFlag", true).add("policeFlag", true).add("shortName", "DVLA").build()));

        caseAccessCommandApi.assignCaseByHearingListing(envelope);

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is("defence.command.handler.advocate.assign-case-by-hearing-listing"));
        assertThat(envelopeArgumentCaptorValue.payload().getString("assigneeEmailId"), is(EMAIL));
        JsonObject caseHearingAssignmentDetails = (JsonObject) envelopeArgumentCaptorValue.payload().getJsonArray("caseHearingAssignmentDetails").get(0);
        assertThat(caseHearingAssignmentDetails.getString("hearingId"), is(assignCaseByHearing.getCaseHearings().get(0).getHearingId().toString()));
        assertThat(caseHearingAssignmentDetails.getString(FAILURE_REASON), is(ORGANISATION_NOT_PROSECUTING_AUTHORITY.name()));
        assertThat(caseHearingAssignmentDetails.getInt(ERROR_CODE), is(parseInt(ORGANISATION_NOT_PROSECUTING_AUTHORITY.getCode())));
        assertCaseAssignmentDetails(caseHearingAssignmentDetails, assignCaseByHearing.getCaseHearings().get(0).getCaseId(), prosecutingAuthorityId);
    }

    @Test
    public void shouldHandleAssignCaseByHearingNonCpsOrgMatch() {
        final UUID userId = randomUUID();
        final AssignCaseByHearing assignCaseByHearing = new AssignCaseByHearing(EMAIL, randomUUID(), randomUUID(), of(new CaseHearings(randomUUID(), randomUUID())));
        final Envelope<AssignCaseByHearing> envelope = createEnvelopeForCaseAccess(EMAIL, assignCaseByHearing, userId);
        final UUID prosecutingAuthorityId = randomUUID();

        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.of("OrganisationMatch"));
        when(usersGroupService.getUserDetailsWithEmail(EMAIL, envelope.metadata(), requester)).thenReturn(PersonDetails.personDetails().withUserId(userId).build());
        when(progressionService.getProsecutionCaseAuthority(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId())).thenReturn(ProsecutionCaseAuthority.prosecutionCaseAuthority()
                .withProsecutionAuthorityId(prosecutingAuthorityId).build());
        when(defenceService.isAssigneeDefendingTheCase(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId(), userId)).thenReturn(false);
        when(referenceDataService.getProsecutor(envelope.metadata(), prosecutingAuthorityId)).thenReturn(Optional.of(createObjectBuilder().add("cpsFlag", true).add("policeFlag", true).add("shortName", "DVLA").build()));

        caseAccessCommandApi.assignCaseByHearingListing(envelope);

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is("defence.command.handler.advocate.assign-case-by-hearing-listing"));
        assertThat(envelopeArgumentCaptorValue.payload().getString("assigneeEmailId"), is(EMAIL));
        JsonObject caseHearingAssignmentDetails = (JsonObject) envelopeArgumentCaptorValue.payload().getJsonArray("caseHearingAssignmentDetails").get(0);
        assertThat(caseHearingAssignmentDetails.getString("hearingId"), is(assignCaseByHearing.getCaseHearings().get(0).getHearingId().toString()));
        assertCaseAssignmentDetailsNonCps(caseHearingAssignmentDetails, assignCaseByHearing.getCaseHearings().get(0).getCaseId(), prosecutingAuthorityId, "DVLA");
    }

    @Test
    public void shouldCaptureCaseNotFoundErrorWhenHandlingAssignCaseByHearing() {
        final UUID userId = randomUUID();
        final AssignCaseByHearing assignCaseByHearing = new AssignCaseByHearing(EMAIL, randomUUID(), randomUUID(), of(new CaseHearings(randomUUID(), randomUUID())));
        final Envelope<AssignCaseByHearing> envelope = createEnvelopeForCaseAccess(EMAIL, assignCaseByHearing, userId);
        when(usersGroupService.getUserDetailsWithEmail(EMAIL, envelope.metadata(), requester)).thenReturn(PersonDetails.personDetails().withUserId(userId).build());
        when(progressionService.getProsecutionCaseAuthority(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId())).thenReturn(null);

        caseAccessCommandApi.assignCaseByHearingListing(envelope);

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is("defence.command.handler.advocate.assign-case-by-hearing-listing"));
        assertThat(envelopeArgumentCaptorValue.payload().getString("assigneeEmailId"), is(EMAIL));
        JsonObject caseHearingAssignmentDetails = (JsonObject) envelopeArgumentCaptorValue.payload().getJsonArray("caseHearingAssignmentDetails").get(0);
        assertThat(caseHearingAssignmentDetails.getString("hearingId"), is(assignCaseByHearing.getCaseHearings().get(0).getHearingId().toString()));
        assertThat(caseHearingAssignmentDetails.get("prosecutionAuthorityId"), is(nullValue()));
        assertThat(caseHearingAssignmentDetails.getString(FAILURE_REASON), is(CASE_NOT_FOUND.name()));
        assertThat(caseHearingAssignmentDetails.getInt(ERROR_CODE), is(parseInt(CASE_NOT_FOUND.getCode())));
    }

    @Test
    public void shouldCaptureOrgNotProsecutingAuthorityErrorWhenHandlingAssignCaseByHearing() {
        final UUID userId = randomUUID();
        final AssignCaseByHearing assignCaseByHearing = new AssignCaseByHearing(EMAIL, randomUUID(), randomUUID(), of(new CaseHearings(randomUUID(), randomUUID())));
        final Envelope<AssignCaseByHearing> envelope = createEnvelopeForCaseAccess(EMAIL, assignCaseByHearing, userId);
        final UUID prosecutingAuthorityId = randomUUID();
        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.of("OrganisationMisMatch"));
        when(usersGroupService.getUserDetailsWithEmail(EMAIL, envelope.metadata(), requester)).thenReturn(PersonDetails.personDetails().withUserId(userId).build());
        when(progressionService.getProsecutionCaseAuthority(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId())).thenReturn(ProsecutionCaseAuthority.prosecutionCaseAuthority()
                .withProsecutionAuthorityId(prosecutingAuthorityId).build());
        when(defenceService.isAssigneeDefendingTheCase(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId(), userId)).thenReturn(false);
        when(referenceDataService.getProsecutor(envelope.metadata(), prosecutingAuthorityId)).thenReturn(Optional.of(createObjectBuilder().add("cpsFlag", false).add("policeFlag", false).add("shortName", "DVLA").build()));

        caseAccessCommandApi.assignCaseByHearingListing(envelope);

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is("defence.command.handler.advocate.assign-case-by-hearing-listing"));
        assertThat(envelopeArgumentCaptorValue.payload().getString("assigneeEmailId"), is(EMAIL));
        JsonObject caseHearingAssignmentDetails = (JsonObject) envelopeArgumentCaptorValue.payload().getJsonArray("caseHearingAssignmentDetails").get(0);
        assertThat(caseHearingAssignmentDetails.getString("hearingId"), is(assignCaseByHearing.getCaseHearings().get(0).getHearingId().toString()));
        assertThat(caseHearingAssignmentDetails.getString("prosecutionAuthorityId"), is(prosecutingAuthorityId.toString()));
        assertThat(caseHearingAssignmentDetails.getString(FAILURE_REASON), is(ORGANISATION_NOT_PROSECUTING_AUTHORITY.name()));
        assertThat(caseHearingAssignmentDetails.getInt(ERROR_CODE), is(parseInt(ORGANISATION_NOT_PROSECUTING_AUTHORITY.getCode())));
    }

    @Test
    public void shouldCaptureOrgNotProsecutingAuthorityErrorWhenHandlingAssignCaseByHearingNonCps() {
        final UUID userId = randomUUID();
        final AssignCaseByHearing assignCaseByHearing = new AssignCaseByHearing(EMAIL, randomUUID(), randomUUID(), of(new CaseHearings(randomUUID(), randomUUID())));
        final Envelope<AssignCaseByHearing> envelope = createEnvelopeForCaseAccess(EMAIL, assignCaseByHearing, userId);
        final UUID prosecutingAuthorityId = randomUUID();
        when(usersGroupQueryService.validateNonCPSUserOrg(any(),any(),any(),any())).thenReturn(Optional.of("OrganisationMisMatch"));
        when(usersGroupService.getUserDetailsWithEmail(EMAIL, envelope.metadata(), requester)).thenReturn(PersonDetails.personDetails().withUserId(userId).build());
        when(progressionService.getProsecutionCaseAuthority(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId())).thenReturn(ProsecutionCaseAuthority.prosecutionCaseAuthority()
                .withProsecutionAuthorityId(prosecutingAuthorityId).build());
        when(defenceService.isAssigneeDefendingTheCase(envelope.metadata(), assignCaseByHearing.getCaseHearings().get(0).getCaseId(), userId)).thenReturn(false);
        when(referenceDataService.getProsecutor(envelope.metadata(), prosecutingAuthorityId)).thenReturn(Optional.of(createObjectBuilder().add("cpsFlag", false).add("policeFlag", false).add("shortName", "DVLA").build()));

        caseAccessCommandApi.assignCaseByHearingListing(envelope);

        verify(sender).send(envelopeArgumentCaptor.capture());
        final Envelope<JsonObject> envelopeArgumentCaptorValue = envelopeArgumentCaptor.getValue();
        assertThat(envelopeArgumentCaptorValue.metadata().name(), is("defence.command.handler.advocate.assign-case-by-hearing-listing"));
        assertThat(envelopeArgumentCaptorValue.payload().getString("assigneeEmailId"), is(EMAIL));
        JsonObject caseHearingAssignmentDetails = (JsonObject) envelopeArgumentCaptorValue.payload().getJsonArray("caseHearingAssignmentDetails").get(0);
        assertThat(caseHearingAssignmentDetails.getString("hearingId"), is(assignCaseByHearing.getCaseHearings().get(0).getHearingId().toString()));
        assertThat(caseHearingAssignmentDetails.getString("prosecutionAuthorityId"), is(prosecutingAuthorityId.toString()));
        assertThat(caseHearingAssignmentDetails.getString(FAILURE_REASON), is(ORGANISATION_NOT_PROSECUTING_AUTHORITY.name()));
        assertThat(caseHearingAssignmentDetails.getInt(ERROR_CODE), is(parseInt(ORGANISATION_NOT_PROSECUTING_AUTHORITY.getCode())));
    }

    private void assertCaseAssignmentDetails(JsonObject caseAssignmentDetails, UUID caseId, UUID prosecutingAuthorityId) {
        assertThat(caseAssignmentDetails.getString("caseId"), is(caseId.toString()));
        assertThat(caseAssignmentDetails.getString("prosecutionAuthorityId"), is(prosecutingAuthorityId.toString()));
        assertThat(caseAssignmentDetails.getBoolean("isCps"), is(true));
        assertThat(caseAssignmentDetails.getBoolean("isPolice"), is(true));
    }

    private void assertCaseAssignmentDetailsNonCps(JsonObject caseAssignmentDetails, UUID caseId, UUID prosecutingAuthorityId, String representingOrganisation) {
        assertThat(caseAssignmentDetails.getString("caseId"), is(caseId.toString()));
        assertThat(caseAssignmentDetails.getString("prosecutionAuthorityId"), is(prosecutingAuthorityId.toString()));
        assertThat(caseAssignmentDetails.getString("representingOrganisation"), is(representingOrganisation));
        assertThat(caseAssignmentDetails.getBoolean("isCps"), is(true));
        assertThat(caseAssignmentDetails.getBoolean("isPolice"), is(true));
    }

    private Envelope<AssignCaseByHearing> createEnvelopeForCaseAccess(final String email, final AssignCaseByHearing assignCaseByHearing, final UUID userId) {

        final Metadata metadata = Envelope.metadataBuilder().withId(randomUUID())
                .withName("defence.command.advocate.assign-case-by-hearing")
                .withUserId(userId.toString())
                .createdAt(now()).build();
        return Envelope.envelopeFrom(metadata, assignCaseByHearing);
    }


}
