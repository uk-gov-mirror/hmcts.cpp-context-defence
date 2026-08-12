package uk.gov.moj.cpp.defence.event.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.cps.defence.PleasAllocationDetails;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.events.AllocationPleasAdded;
import uk.gov.moj.cpp.defence.events.AllocationPleasUpdated;
import uk.gov.moj.cpp.defence.persistence.DefendantAllocationRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocation;
import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocationPlea;

import jakarta.inject.Inject;
import java.util.List;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;

@ServiceComponent(EVENT_LISTENER)
public class DefencePleaEventsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefencePleaEventsListener.class);

    @Inject
    private DefendantAllocationRepository allocationRepository;


    @Handles("defence.event.allocation-pleas-added")
    public void saveAllocationPlea(final Envelope<AllocationPleasAdded> event) {

        final AllocationPleasAdded allocationPleasAdded = event.payload();
        final PleasAllocationDetails pleasAllocationDetails = allocationPleasAdded.getPleasAllocation();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Received event '{}' with defendanId: {}", "defence.event.allocation-pleas-added", allocationPleasAdded.getPleasAllocation().getDefendantId());
        }
        final DefendantAllocation defendantAllocation = new DefendantAllocation();
        defendantAllocation.setId(pleasAllocationDetails.getAllocationId());
        defendantAllocation.setDefendantAllocationPleas(pleasAllocationDetails.getOffencePleas().stream()
                .map(s -> new DefendantAllocationPlea(s.getOffenceId(), s.getPleaDate(), s.getIndicatedPlea(), defendantAllocation)).toList());
        defendantAllocation.setAcknowledgement(pleasAllocationDetails.getAcknowledgement());
        defendantAllocation.setGuardianConsentProvided(pleasAllocationDetails.getYouthAcknowledgement());
        defendantAllocation.setConsentToMagistrateTrail(pleasAllocationDetails.getConsentToMagistratesCourtTrial());
        defendantAllocation.setElectCrownCourtTrail(pleasAllocationDetails.getElectingCrownCourtTrial());
        defendantAllocation.setElectCrownCourtTrailDetails(pleasAllocationDetails.getElectingCrownCourtTrialDetails());
        defendantAllocation.setOffenceValueRepresentations(pleasAllocationDetails.getDisputeOffenceValueDetails());
        defendantAllocation.setOffenceValueDisputed(pleasAllocationDetails.getDisputeOffenceValue());
        defendantAllocation.setDefendantId(pleasAllocationDetails.getDefendantId());
        defendantAllocation.setRepresentationsOnGraveCrime(pleasAllocationDetails.getRepresentationsOnGraveCrime());
        defendantAllocation.setRepresentationsOnGraveCrimeDetails(pleasAllocationDetails.getRepresentationsOnGraveCrimeDetails());
        defendantAllocation.setDefendantNameDobConfirmation(pleasAllocationDetails.getDefendantNameDobConfirmation());
        if (nonNull(pleasAllocationDetails.getDefendantDetails())) {
            defendantAllocation.setDefendantFirstName(pleasAllocationDetails.getDefendantDetails().getFirstName());
            defendantAllocation.setDefendantMiddleName(pleasAllocationDetails.getDefendantDetails().getMiddleName());
            defendantAllocation.setDefendantSurname(pleasAllocationDetails.getDefendantDetails().getSurname());
            defendantAllocation.setDefendantDateOfBirth(pleasAllocationDetails.getDefendantDetails().getDob());
            defendantAllocation.setDefendantOrganisationName(pleasAllocationDetails.getDefendantDetails().getOrganisationName());
        }
        defendantAllocation.setOffenceType(ofNullable(pleasAllocationDetails.getOffenceType()).map(Object::toString).orElse(null));
        defendantAllocation.setAdditionalInformation(pleasAllocationDetails.getAdditionalInformation());
        defendantAllocation.setDefendantTurningEighteenDetails(pleasAllocationDetails.getDefendantTurningEighteenDetails());
        defendantAllocation.setTheftFromShop(ofNullable(pleasAllocationDetails.getTheftFromShop()).map(Object::toString).orElse(null));
        defendantAllocation.setTheftFromShopDetails(pleasAllocationDetails.getTheftFromShopDetails());

        if(nonNull(pleasAllocationDetails.getCrownCourtObjection())) {
            defendantAllocation.setCrownCourtObjection(pleasAllocationDetails.getCrownCourtObjection().toString());
        }

        if(nonNull(pleasAllocationDetails.getSentencingIndication())){
            defendantAllocation.setSentencingIndicationRequested(pleasAllocationDetails.getSentencingIndication().toString());
        }
        allocationRepository.save(defendantAllocation);
    }

    @Handles("defence.event.allocation-pleas-updated")
    public void updatePlea(final Envelope<AllocationPleasUpdated> event) {

        final AllocationPleasUpdated allocationPleasUpdated = event.payload();
        final PleasAllocationDetails pleasAllocationDetails = allocationPleasUpdated.getPleasAllocation();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Received event '{}' with defendanId: {}", "defence.event.allocation-pleas-updated", pleasAllocationDetails.getDefendantId());
        }

        final DefendantAllocation defendantAllocation = allocationRepository.findBy(pleasAllocationDetails.getAllocationId());

        final List<DefendantAllocationPlea> updatedDefendantAllocationPleas = pleasAllocationDetails.getOffencePleas().stream()
                .map(s -> new DefendantAllocationPlea(s.getOffenceId(), s.getPleaDate(), s.getIndicatedPlea(), defendantAllocation)).toList();
        defendantAllocation.getDefendantAllocationPleas().clear();
        defendantAllocation.getDefendantAllocationPleas().addAll(updatedDefendantAllocationPleas);

        defendantAllocation.setAcknowledgement(pleasAllocationDetails.getAcknowledgement());
        defendantAllocation.setGuardianConsentProvided(pleasAllocationDetails.getYouthAcknowledgement());
        defendantAllocation.setConsentToMagistrateTrail(pleasAllocationDetails.getConsentToMagistratesCourtTrial());
        defendantAllocation.setElectCrownCourtTrail(pleasAllocationDetails.getElectingCrownCourtTrial());
        defendantAllocation.setElectCrownCourtTrailDetails(pleasAllocationDetails.getElectingCrownCourtTrialDetails());
        defendantAllocation.setOffenceValueRepresentations(pleasAllocationDetails.getDisputeOffenceValueDetails());
        defendantAllocation.setDefendantId(pleasAllocationDetails.getDefendantId());
        defendantAllocation.setOffenceValueDisputed(pleasAllocationDetails.getDisputeOffenceValue());
        defendantAllocation.setSentencingIndicationRequested(ofNullable(pleasAllocationDetails.getSentencingIndication()).map(Object::toString).orElse(null));
        defendantAllocation.setRepresentationsOnGraveCrime(pleasAllocationDetails.getRepresentationsOnGraveCrime());
        defendantAllocation.setRepresentationsOnGraveCrimeDetails(pleasAllocationDetails.getRepresentationsOnGraveCrimeDetails());
        defendantAllocation.setDefendantNameDobConfirmation(pleasAllocationDetails.getDefendantNameDobConfirmation());
        if (nonNull(pleasAllocationDetails.getDefendantDetails())) {
            defendantAllocation.setDefendantFirstName(pleasAllocationDetails.getDefendantDetails().getFirstName());
            defendantAllocation.setDefendantMiddleName(pleasAllocationDetails.getDefendantDetails().getMiddleName());
            defendantAllocation.setDefendantSurname(pleasAllocationDetails.getDefendantDetails().getSurname());
            defendantAllocation.setDefendantDateOfBirth(pleasAllocationDetails.getDefendantDetails().getDob());
            defendantAllocation.setDefendantOrganisationName(pleasAllocationDetails.getDefendantDetails().getOrganisationName());
        } else {
            defendantAllocation.setDefendantFirstName(null);
            defendantAllocation.setDefendantMiddleName(null);
            defendantAllocation.setDefendantSurname(null);
            defendantAllocation.setDefendantDateOfBirth(null);
            defendantAllocation.setDefendantOrganisationName(null);
        }
        defendantAllocation.setOffenceType(ofNullable(pleasAllocationDetails.getOffenceType()).map(Object::toString).orElse(null));
        defendantAllocation.setAdditionalInformation(pleasAllocationDetails.getAdditionalInformation());
        defendantAllocation.setDefendantTurningEighteenDetails(pleasAllocationDetails.getDefendantTurningEighteenDetails());
        defendantAllocation.setTheftFromShop(ofNullable(pleasAllocationDetails.getTheftFromShop()).map(Object::toString).orElse(null));
        defendantAllocation.setTheftFromShopDetails(pleasAllocationDetails.getTheftFromShopDetails());

        if (isNull(pleasAllocationDetails.getCrownCourtObjection())) {
            defendantAllocation.setCrownCourtObjection(null);
        } else {
            defendantAllocation.setCrownCourtObjection(pleasAllocationDetails.getCrownCourtObjection().toString());
        }

        allocationRepository.save(defendantAllocation);
    }

}

