package uk.gov.moj.cpp.defence.query.view;

import static java.time.ZonedDateTime.now;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.defence.query.view.CpsCaseAccessQueryView.EXPIRED_ASSIGNMENTS_SELECT_COUNT;

import uk.gov.justice.cps.defence.ExpiredProsecutorAssignments;
import uk.gov.justice.cps.defence.ExpiredProsecutorOrganisationAssignments;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.OrganisationAssignment;
import uk.gov.moj.cpp.defence.ProsecutorAssignment;
import uk.gov.moj.cpp.defence.persistence.AdvocateAccessRepository;
import uk.gov.moj.cpp.defence.persistence.OrganisationAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.AssignmentUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionAdvocateAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;
import uk.gov.moj.cpp.defence.persistence.entity.RepresentationType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExpiredAssignmentQueryViewTest {
    @Mock
    private JsonEnvelope envelope;

    @Mock
    private JsonObject jsonPayload;

    @Mock
    private AdvocateAccessRepository advocateAccessRepository;

    @Mock
    private OrganisationAccessRepository organisationAccessRepository;

    @InjectMocks
    private CpsCaseAccessQueryView advocateAccessQueryView;

    private UUID caseId = randomUUID();
    private UUID organisationId = randomUUID();
    private static final String ASSIGNEE_ORG_NAME = "ABC & Co.";

    @BeforeEach
    public void setup() {
        caseId = randomUUID();
        organisationId = randomUUID();
    }

    @Test
    public void shouldGetExpiredProsecutorAssignments() {

        final UUID advocateUserId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);

        final ProsecutionAdvocateAccess prosecutionAdvocateAccess = getProsecutionAdvocateAccess(advocateUserId);
        prosecutionAdvocateAccess.setProsecutionOrganisation(getProsecutionOrganisationAccessEntity(caseId, organisationId));
        when(advocateAccessRepository.findExpiredCaseAssignments()).thenReturn(singletonList(prosecutionAdvocateAccess));

        final Envelope<ExpiredProsecutorAssignments> expiredProsecutorAssignmentsEnvelope = advocateAccessQueryView.queryExpiredProsecutorAssignments(envelope);
        final List<ProsecutorAssignment> prosecutorAssignments = expiredProsecutorAssignmentsEnvelope.payload().getProsecutorAssignments();


        assertThat(prosecutorAssignments, hasSize(equalTo(1)));
        assertThat(prosecutorAssignments.get(0).getCaseId(), is(prosecutionAdvocateAccess.getCaseId()));
        assertThat(prosecutorAssignments.get(0).getAssignmentExpiryDate(), is(prosecutionAdvocateAccess.getAssignmentExpiryDate()));
        assertThat(prosecutorAssignments.get(0).getAssigneeUserId(), is(prosecutionAdvocateAccess.getAssigneeDetails().getUserId()));
        assertThat(prosecutorAssignments.get(0).getAssignorOrganisationId(), is(prosecutionAdvocateAccess.getAssignorOrganisationId()));
        assertThat(prosecutorAssignments.get(0).getAssignorUserId(), is(prosecutionAdvocateAccess.getAssignorDetails().getUserId()));

    }

    @Test
    public void shouldGetExpiredProsecutorAssignmentsWithLimit() {

        final UUID advocateUserId = randomUUID();

        final ProsecutionAdvocateAccess prosecutionAdvocateAccess = getProsecutionAdvocateAccess(advocateUserId);
        prosecutionAdvocateAccess.setProsecutionOrganisation(getProsecutionOrganisationAccessEntity(caseId, organisationId));
        when(advocateAccessRepository.findExpiredCaseAssignments(50)).thenReturn(singletonList(prosecutionAdvocateAccess));
        when(envelope.payloadAsJsonObject()).thenReturn(createObjectBuilder().add(EXPIRED_ASSIGNMENTS_SELECT_COUNT, "50").build());

        final Envelope<ExpiredProsecutorAssignments> expiredProsecutorAssignmentsEnvelope = advocateAccessQueryView.queryExpiredProsecutorAssignments(envelope);
        final List<ProsecutorAssignment> prosecutorAssignments = expiredProsecutorAssignmentsEnvelope.payload().getProsecutorAssignments();


        assertThat(prosecutorAssignments, hasSize(equalTo(1)));
        assertThat(prosecutorAssignments.get(0).getCaseId(), is(prosecutionAdvocateAccess.getCaseId()));
        assertThat(prosecutorAssignments.get(0).getAssignmentExpiryDate(), is(prosecutionAdvocateAccess.getAssignmentExpiryDate()));
        assertThat(prosecutorAssignments.get(0).getAssigneeUserId(), is(prosecutionAdvocateAccess.getAssigneeDetails().getUserId()));
        assertThat(prosecutorAssignments.get(0).getAssignorOrganisationId(), is(prosecutionAdvocateAccess.getAssignorOrganisationId()));
        assertThat(prosecutorAssignments.get(0).getAssignorUserId(), is(prosecutionAdvocateAccess.getAssignorDetails().getUserId()));

    }

    @Test
    public void shouldGetExpiredProsecutorOrganisationAssignments() {

        final UUID advocateUserId = randomUUID();
        when(envelope.payloadAsJsonObject()).thenReturn(jsonPayload);

        final ProsecutionOrganisationAccess prosecutionOrganisationAccess = getProsecutionOrganisationAccessEntity(caseId, organisationId);
        final Set<ProsecutionAdvocateAccess> prosecutionAdvocatesWithAccessSet = prosecutionOrganisationAccess.getProsecutionAdvocatesWithAccess();
        final ProsecutionAdvocateAccess prosecutionAdvocateAccess = getProsecutionAdvocateAccess(advocateUserId);
        prosecutionAdvocatesWithAccessSet.add(prosecutionAdvocateAccess);

        when(organisationAccessRepository.findExpiredCaseAssignments()).thenReturn(singletonList(prosecutionOrganisationAccess));

        final Envelope<ExpiredProsecutorOrganisationAssignments> expiredProsecutorAssignmentsEnvelope = advocateAccessQueryView.queryExpiredProsecutorOrganisationAssignments(envelope);
        final List<OrganisationAssignment> organisationAssignments = expiredProsecutorAssignmentsEnvelope.payload().getOrganisationAssignments();


        assertThat(organisationAssignments, hasSize(equalTo(1)));

        assertThat(organisationAssignments.get(0).getCaseId(), is(prosecutionOrganisationAccess.getCaseId()));
        assertThat(organisationAssignments.get(0).getAssignmentExpiryDate(), is(prosecutionOrganisationAccess.getAssignmentExpiryDate()));
        assertThat(organisationAssignments.get(0).getAssigneeUserId(), is(prosecutionOrganisationAccess.getAssigneeDetails().getUserId()));
        assertThat(organisationAssignments.get(0).getAssignorOrganisationId(), is(prosecutionOrganisationAccess.getAssignorOrganisationId()));
        assertThat(organisationAssignments.get(0).getAssignorOrganisationName(), is(prosecutionOrganisationAccess.getAssignorOrganisationName()));
        assertThat(organisationAssignments.get(0).getAssigneeOrganisationId(), is(prosecutionOrganisationAccess.getId().getAssigneeOrganisationId()));
        assertThat(organisationAssignments.get(0).getAssigneeOrganisationName(), is(ASSIGNEE_ORG_NAME));
        assertThat(organisationAssignments.get(0).getAssignorUserId(), is(prosecutionOrganisationAccess.getAssignorDetails().getUserId()));

    }

    @Test
    void shouldGetExpiredProsecutorOrganisationAssignmentsWithLimit() {
        final UUID advocateUserId = randomUUID();
        final ProsecutionOrganisationAccess prosecutionOrganisationAccess = getProsecutionOrganisationAccessEntity(caseId, organisationId);
        final Set<ProsecutionAdvocateAccess> prosecutionAdvocatesWithAccessSet = prosecutionOrganisationAccess.getProsecutionAdvocatesWithAccess();
        final ProsecutionAdvocateAccess prosecutionAdvocateAccess = getProsecutionAdvocateAccess(advocateUserId);
        prosecutionAdvocatesWithAccessSet.add(prosecutionAdvocateAccess);

        when(organisationAccessRepository.findExpiredCaseAssignments(50)).thenReturn(singletonList(prosecutionOrganisationAccess));
        when(envelope.payloadAsJsonObject()).thenReturn(createObjectBuilder().add(EXPIRED_ASSIGNMENTS_SELECT_COUNT, "50").build());

        final Envelope<ExpiredProsecutorOrganisationAssignments> expiredProsecutorAssignmentsEnvelope = advocateAccessQueryView.queryExpiredProsecutorOrganisationAssignments(envelope);
        final List<OrganisationAssignment> organisationAssignments = expiredProsecutorAssignmentsEnvelope.payload().getOrganisationAssignments();
        assertThat(organisationAssignments, hasSize(equalTo(1)));
    }

    private ProsecutionAdvocateAccess getProsecutionAdvocateAccess(UUID advocateUserId) {
        ProsecutionAdvocateAccess prosecutionAdvocateAccess = new ProsecutionAdvocateAccess();
        prosecutionAdvocateAccess.setAssigneeDetails(new AssignmentUserDetails(randomUUID(), advocateUserId, "Patesh", "Singh"));
        prosecutionAdvocateAccess.setAssignorDetails(new AssignmentUserDetails(randomUUID(), randomUUID(), "John", "Smith"));
        prosecutionAdvocateAccess.setAssignmentExpiryDate(now());
        prosecutionAdvocateAccess.setAssignedDate(now());
        return prosecutionAdvocateAccess;
    }

    private ProsecutionOrganisationAccess getProsecutionOrganisationAccessEntity(UUID caseId, UUID organisationId) {

        ProsecutionOrganisationAccess prosecutionOrganisationAccess = new ProsecutionOrganisationAccess();
        prosecutionOrganisationAccess.setAssigneeDetails(new AssignmentUserDetails(randomUUID(), randomUUID(), "Jhon", "Rambo"));
        prosecutionOrganisationAccess.setAssignorDetails(new AssignmentUserDetails(randomUUID(), randomUUID(), "Tom", "Harry"));
        prosecutionOrganisationAccess.setId(new ProsecutionOrganisationCaseKey(caseId, organisationId));
        prosecutionOrganisationAccess.setAssigneeOrganisationName(ASSIGNEE_ORG_NAME);
        prosecutionOrganisationAccess.setRepresentationType(RepresentationType.PROSECUTION);
        prosecutionOrganisationAccess.setAssignedDate(now());
        prosecutionOrganisationAccess.setAssignmentExpiryDate(now());
        return prosecutionOrganisationAccess;
    }
}
