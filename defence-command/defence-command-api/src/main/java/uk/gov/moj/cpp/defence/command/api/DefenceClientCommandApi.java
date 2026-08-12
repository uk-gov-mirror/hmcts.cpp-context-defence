package uk.gov.moj.cpp.defence.command.api;


import static java.lang.String.format;
import static java.time.LocalDate.now;
import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.messaging.Envelope.envelopeFrom;
import static uk.gov.justice.services.messaging.Envelope.metadataFrom;
import static uk.gov.moj.cpp.defence.common.util.DateValidator.validateDateString;

import uk.gov.justice.cps.defence.DefenceInstruction;
import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;

import java.time.LocalDate;

import jakarta.inject.Inject;

@ServiceComponent(COMMAND_API)
public class DefenceClientCommandApi {

    @Inject
    private Sender sender;

    @Handles("defence.record-instruction-details")
    public void recordInstructionDetails(final Envelope<DefenceInstruction> envelope) {

        final String instructionDateString = envelope.payload().getInstructionDate();
        final LocalDate instructionDate = validateDateString(instructionDateString);

        if(instructionDate.isAfter(now())){
            throw new BadRequestException(format("Date is in future. Date : %s", instructionDateString));
        }

        sender.send(envelopeFrom(metadataFrom(envelope.metadata()).withName("defence.command.record-instruction-details"),
                envelope.payload()));

    }

}
