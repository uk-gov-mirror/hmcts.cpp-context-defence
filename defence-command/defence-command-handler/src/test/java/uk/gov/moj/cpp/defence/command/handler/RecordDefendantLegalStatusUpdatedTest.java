package uk.gov.moj.cpp.defence.command.handler;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.OrganisationDetails;
import uk.gov.moj.cpp.defence.aggregate.DefenceAssociation;
import uk.gov.moj.cpp.defence.service.UserGroupService;

import java.util.UUID;
import java.util.stream.Stream;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecordDefendantLegalStatusUpdatedTest {

    private static final String DEFENDANT_ID = "defendantId";
    private static final String LAA_CONTRACT_NUMBER = "laaContractNumber";
    private static final String LEGAL_AID_STATUS = "legalAidStatus";

    @Mock
    private EventSource eventSource;

    @Mock
    private AggregateService aggregateService;

    @Mock
    private UserGroupService userGroupService;

    @Mock
    private Requester requester;

    @Mock
    private EventStream eventStream;

    @Mock
    private DefenceAssociation defenceAssociation;

    @Mock
    private JsonEnvelope envelope;

    @InjectMocks
    private RecordDefendantLegalStatusUpdated handler;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldRecordLegalStatusForDefendant() throws EventStreamException {
        // Given
        UUID defendantId = randomUUID();
        UUID organisationId = randomUUID();
        String legalStatusUpdated = "GRANTED";
        String laaContractNumber = "ABC123";

        JsonObject payload = createObjectBuilder()
                .add(DEFENDANT_ID, defendantId.toString())
                .add(LAA_CONTRACT_NUMBER, laaContractNumber)
                .add(LEGAL_AID_STATUS, legalStatusUpdated)
                .build();

        when(envelope.payloadAsJsonObject()).thenReturn(payload);
        when(eventSource.getStreamById(defendantId)).thenReturn(eventStream);
        when(aggregateService.get(eventStream, DefenceAssociation.class)).thenReturn(defenceAssociation);
        when(userGroupService.getOrganisationByLaaReference(envelope, requester, laaContractNumber))
                .thenReturn(createOrganisation(organisationId));
        when(defenceAssociation.recordLegalStatusForDefendant(defendantId, organisationId, legalStatusUpdated))
                .thenReturn(Stream.of("SomeEvent"));

        // When
        handler.recordDefendantLegalStatusUpdated(envelope);

        // Then
        verify(eventSource).getStreamById(defendantId);
        verify(aggregateService).get(eventStream, DefenceAssociation.class);
        verify(defenceAssociation).recordLegalStatusForDefendant(defendantId, organisationId, legalStatusUpdated);
        verify(userGroupService).getOrganisationByLaaReference(envelope, requester, laaContractNumber);
        verify(eventStream).append(any());
    }

    private OrganisationDetails createOrganisation(final UUID orgId) {
        return OrganisationDetails.organisationDetails().withId(orgId).withName("ORGANISATION_NAME").build();
    }


}
