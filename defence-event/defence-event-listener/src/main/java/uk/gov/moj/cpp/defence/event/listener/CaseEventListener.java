package uk.gov.moj.cpp.defence.event.listener;

import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.events.ProsecutionCaseReceived;
import uk.gov.moj.cpp.defence.persistence.DefenceCaseRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceCase;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_LISTENER)
public class CaseEventListener {

    @Inject
    DefenceCaseRepository defenceCaseRepository;

    @Handles("defence.events.prosecution-case-received")
    public void prosecutionCaseReceived(final Envelope<ProsecutionCaseReceived> envelope){
        final ProsecutionCaseReceived prosecutionCaseReceived = envelope.payload();
        final DefenceCase defenceCase = new DefenceCase(prosecutionCaseReceived.getCaseId(), prosecutionCaseReceived.getUrn(), prosecutionCaseReceived.getProsecutingAuthority(), prosecutionCaseReceived.getIsCivil(), prosecutionCaseReceived.getIsGroupMember());
        defenceCaseRepository.save(defenceCase);
    }
}
