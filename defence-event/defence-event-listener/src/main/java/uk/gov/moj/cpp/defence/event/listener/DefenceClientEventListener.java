package uk.gov.moj.cpp.defence.event.listener;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;

import uk.gov.justice.cps.defence.DefendantDetails;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.event.listener.events.DefendantUpdateReceived;
import uk.gov.moj.cpp.defence.events.DefenceClientReceived;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_LISTENER)
public class DefenceClientEventListener {


    @Inject
    private DefenceClientRepository defenceClientRepository;


    @Handles("defence.events.defence-client-received")
    public void defenceClientReceived(final Envelope<DefenceClientReceived> envelope) {
        final DefenceClientReceived defenceClientReceived = envelope.payload();
        final DefendantDetails defendantDetails = defenceClientReceived.getDefendantDetails();
        final UUID caseId = defenceClientReceived.getDefendantDetails().getCaseId();

        final String lastName = defendantDetails.getLastName();
        final Organisation organisation = defendantDetails.getOrganisation();
        final LocalDate dateOfBirth = isNotBlank(defendantDetails.getDateOfBirth()) ? LocalDate.parse(defendantDetails.getDateOfBirth()) : null;
        if (isNotBlank(lastName)) {
            final DefenceClient defenceClient = new DefenceClient(defenceClientReceived.getDefenceClientId(),
                    defendantDetails.getFirstName(), defendantDetails.getLastName(), caseId
                    , dateOfBirth, defenceClientReceived.getDefendantId());
            defenceClientRepository.save(defenceClient);
        } else if (nonNull(organisation) && isNotBlank(organisation.getOrganisationName())) {
            final DefenceClient defenceClient = new DefenceClient(defenceClientReceived.getDefenceClientId(),
                    organisation.getOrganisationName(), caseId, defenceClientReceived.getDefendantId());
            defenceClientRepository.save(defenceClient);
        }

    }

    @Handles("defence.event.defendant-update-received")
    public void defenceUpdateReceived(final Envelope<DefendantUpdateReceived> envelope) {
        final DefendantUpdateReceived defendantUpdateReceived = envelope.payload();
        final DefenceClient defenceClient = getDefenceClient(defendantUpdateReceived.getDefendantDetails(), defendantUpdateReceived.getDefendantId());
        if(isNull(defenceClient)){
            return;
        }
        defenceClientRepository.save(defenceClient);
    }

    private DefenceClient getDefenceClient(final DefendantDetails defendantDetails, final UUID defendantId) {
        final DefenceClient defenceClient = defenceClientRepository.findOptionalByDefendantIdAndCaseId(defendantId, defendantDetails.getCaseId());

        if(isNull(defenceClient)){
            return null;
        }

        if (!isEmpty(defendantDetails.getFirstName())) {
            defenceClient.setFirstName(defendantDetails.getFirstName());
        }

        if (!isEmpty(defendantDetails.getLastName())) {
            defenceClient.setLastName(defendantDetails.getLastName());
        }

        if (!isEmpty(defendantDetails.getDateOfBirth())) {
            defenceClient.setDateOfBirth(LocalDate.parse(defendantDetails.getDateOfBirth()));
        }

        if (defendantDetails.getOrganisation() != null) {
            defenceClient.setOrganisationName(defendantDetails.getOrganisation().getOrganisationName());
        }

        return defenceClient;

    }

}
