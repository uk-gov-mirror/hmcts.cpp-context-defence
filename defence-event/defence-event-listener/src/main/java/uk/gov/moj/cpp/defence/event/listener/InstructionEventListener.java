package uk.gov.moj.cpp.defence.event.listener;

import static java.util.Objects.isNull;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.events.InstructionDetailsRecorded;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.Instruction;

import java.util.Optional;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_LISTENER)
public class InstructionEventListener {

    @Inject
    private DefenceClientRepository defenceClientRepository;

    @Handles("defence.event.instruction-details-recorded")
    public void recordInstructionDetails(final Envelope<InstructionDetailsRecorded> envelope) {
        final InstructionDetailsRecorded instructionDetailsRecorded = envelope.payload();
        final Optional<DefenceClient> defenceClient = defenceClientRepository.findOptionalBy(instructionDetailsRecorded.getDefenceClientId());
        if(defenceClient.isEmpty()){
            return;
        }
        final Instruction instruction = new Instruction(instructionDetailsRecorded.getInstructionId(),
                instructionDetailsRecorded.getUserId(),
                instructionDetailsRecorded.getOrganisationId(),
                defenceClient.get(),
                instructionDetailsRecorded.getInstructionDate());

        defenceClient.get().getInstructionHistory().add(instruction);

    }
}
