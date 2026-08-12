package uk.gov.moj.cpp.defence.event.listener;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.events.IdpcAccessRecorded;
import uk.gov.moj.cpp.defence.persistence.IdpcAccessHistoryRepository;
import uk.gov.moj.cpp.defence.persistence.entity.IdpcAccess;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_LISTENER)
public class IdpcAccessEventListener {

    @Inject
    private IdpcAccessHistoryRepository idpcAccessHistoryRepository;

    @Handles("defence.event.idpc-access-recorded")
    public void recordIdpcAccess(final Envelope<IdpcAccessRecorded> envelope) {
        final IdpcAccessRecorded idpcAccessInfo = envelope.payload();

        final IdpcAccess idpcAccess = new IdpcAccess(randomUUID(), idpcAccessInfo.getDefenceClientId(),
                                                        idpcAccessInfo.getIdpcDetailsId(), idpcAccessInfo.getUserId(),
                                                        idpcAccessInfo.getOrganisationId(), idpcAccessInfo.getAccessTimestamp());

        idpcAccessHistoryRepository.save(idpcAccess);
    }
}
