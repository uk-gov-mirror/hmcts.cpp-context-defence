package uk.gov.moj.cpp.defence.event.listener;

import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.events.IdpcDetailsRecorded;
import uk.gov.moj.cpp.defence.persistence.DefenceCaseRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.IdpcDetailsRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceCase;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.IdpcDetails;

import java.util.Optional;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_LISTENER)
public class IdpcDetailsEventListener {

    @Inject
    private IdpcDetailsRepository idpcDetailsRepository;

    @Inject
    private DefenceClientRepository defenceClientRepository;

    @Inject
    private DefenceCaseRepository defenceCaseRepository;

    @Handles("defence.event.idpc-details-recorded")
    public void recordIdpcDetails(final Envelope<IdpcDetailsRecorded> envelope) {
        final IdpcDetailsRecorded idpcDetailsRecorded = envelope.payload();
        final Optional<DefenceClient> defenceClient = defenceClientRepository.findOptionalBy(idpcDetailsRecorded.getDefenceClientId());
        if(defenceClient.isEmpty()){
            return;
        }
        final uk.gov.moj.cpp.defence.IdpcDetails idpcDetailsVo = idpcDetailsRecorded.getIdpcDetails();
        final String documentName = createDocumentName(defenceClient.get());

        final IdpcDetails optionalByDefenceClientId = idpcDetailsRepository.findOptionalByDefenceClientId(idpcDetailsRecorded.getDefenceClientId());
        if(nonNull(optionalByDefenceClientId) && nonNull(optionalByDefenceClientId.getId())) {
            idpcDetailsRepository.remove(optionalByDefenceClientId);
        }
        final IdpcDetails idpcDetailsEntity = new IdpcDetails(randomUUID(), idpcDetailsRecorded.getDefenceClientId(),
                idpcDetailsVo, documentName);

        defenceClient.get().setIdpcDetailsId(idpcDetailsEntity.getId());
        idpcDetailsRepository.save(idpcDetailsEntity);
    }

    /*
     * Document name format : SURNAME firstname 11DD0304617 Initial Details Pros DefenceCase
     * where 11DD0304617 = URN
     * Refer to jira - CRC-6668 for details
     * Note: Middle Name is not stored withing C2I context and hence can't be included in file name.
     */
    private String createDocumentName(final DefenceClient defenceClient) {
        final DefenceCase defenceCase = defenceCaseRepository.findBy(defenceClient.getCaseId());

       if(isNotBlank(defenceClient.getOrganisationName())) {
           return new StringBuilder(defenceClient.getOrganisationName()).append(SPACE)
                   .append(defenceCase.getUrn()).append(SPACE)
                   .append("IDPC")
                   .toString();
       }
        return new StringBuilder(defenceClient.getLastName()).append(SPACE)
                .append(defenceClient.getFirstName()).append(SPACE)
                .append(defenceCase.getUrn()).append(SPACE)
                .append("IDPC")
                .toString();

    }
}
