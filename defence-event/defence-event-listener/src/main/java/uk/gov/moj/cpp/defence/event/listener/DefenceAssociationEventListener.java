package uk.gov.moj.cpp.defence.event.listener;

import static java.time.ZonedDateTime.now;
import static java.util.Objects.isNull;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;
import static uk.gov.moj.cpp.defence.events.RepresentationType.REPRESENTATION_ORDER;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociated;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociatedBdf;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationAssociationUnlockedBdf;
import uk.gov.moj.cpp.defence.events.DefenceOrganisationDisassociated;
import uk.gov.moj.cpp.defence.events.DefendantDefenceAssociationLockedForLaa;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationDefendantRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociationDefendant;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_LISTENER)
public class DefenceAssociationEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefenceAssociationEventListener.class);

    @Inject
    private DefenceAssociationDefendantRepository defenceAssociationDefendantRepository;

    @Inject
    private DefenceAssociationRepository defenceAssociationRepository;

    @Inject
    private DefenceClientRepository defenceClientRepository;


    @Handles("defence.event.defence-organisation-associated")
    public void processOrganisationAssociated(final Envelope<DefenceOrganisationAssociated> event) {
        final DefenceOrganisationAssociated payload = event.payload();
        organisationAssociated(payload);

    }

    @Handles("defence.event.defence-organisation-associated-bdf")
    public void processOrganisationAssociatedBdf(final Envelope<DefenceOrganisationAssociatedBdf> event) {
        final DefenceOrganisationAssociatedBdf payloadBdf = event.payload();
        final DefenceOrganisationAssociated payload = DefenceOrganisationAssociated.defenceOrganisationAssociated()
                .withDefendantId(payloadBdf.getDefendantId())
                .withUserId(payloadBdf.getUserId())
                .withOrganisationId(payloadBdf.getOrganisationId())
                .withStartDate(payloadBdf.getStartDate())
                .withRepresentationType(payloadBdf.getRepresentationType())
                .withLaaContractNumber(payloadBdf.getLaaContractNumber())
                .build();
        organisationAssociated(payload);

    }

    private void organisationAssociated(final DefenceOrganisationAssociated defenceOrganisationAssociated) {
        final UUID defendantId = defenceOrganisationAssociated.getDefendantId();
        final UUID userId = defenceOrganisationAssociated.getUserId();
        final UUID defenceOrganisationId = defenceOrganisationAssociated.getOrganisationId();
        final ZonedDateTime startDate = defenceOrganisationAssociated.getStartDate();
        final String representationType = defenceOrganisationAssociated.getRepresentationType().name();
        final String laaContractNumber = defenceOrganisationAssociated.getLaaContractNumber();

        if(!associateWithDefenceClient(defendantId, defenceOrganisationId)){
            return;
        }

        final DefenceAssociationDefendant defenceAssociationDefendant
                = prepareDefenceAssociationEntity(defendantId, userId, defenceOrganisationId, startDate, representationType, laaContractNumber);
        defenceAssociationDefendantRepository.save(defenceAssociationDefendant);
    }

    @Handles("defence.event.defence-organisation-disassociated")
    public void processOrganisationDisassociated(final Envelope<DefenceOrganisationDisassociated> event) {

        final DefenceOrganisationDisassociated defenceOrganisationDisassociated = event.payload();
        final UUID defendantId = defenceOrganisationDisassociated.getDefendantId();

        final UUID organisationId = defenceOrganisationDisassociated.getOrganisationId();

        final ZonedDateTime endDate = defenceOrganisationDisassociated.getEndDate();

        if(! disassociateFromDefenceClient(defendantId, organisationId)){
            return;
        }

        final DefenceAssociationDefendant defenceAssociationDefendant = defenceAssociationDefendantRepository.findOptionalByDefendantId(defendantId);
        if(! isNull(defenceAssociationDefendant)) {
            final DefenceAssociationDefendant updatedDefenceAssociationDefendant = disassociateOrganisation(defenceAssociationDefendant, organisationId, endDate);

            defenceAssociationDefendantRepository.save(updatedDefenceAssociationDefendant);
        }

    }

    @Handles("defence.event.defendant-defence-association-locked-for-laa")
    public void processDefenceAssociationLockedForLaa(final Envelope<DefendantDefenceAssociationLockedForLaa> envelope) {
        final DefendantDefenceAssociationLockedForLaa defendantDefenceAssociationLockedForLaaEvent = envelope.payload();
        final UUID defendantId = defendantDefenceAssociationLockedForLaaEvent.getDefendantId();
        final DefenceClient defenceClient = defenceClientRepository.findOptionalByDefendantId(defendantId);
        if(isNull(defenceClient)){
            return;
        }
        defenceClient.setLockedByRepOrder(true);
        defenceClientRepository.save(defenceClient);

        final String userId = envelope.metadata().userId().orElse(null);

        final DefenceAssociationDefendant defenceAssociationDefendant
                = prepareDefenceAssociationEntity(defendantId, fromString(userId), null, envelope.metadata().createdAt().orElse(now()), null, defendantDefenceAssociationLockedForLaaEvent.getLaaContractNumber());
        defenceAssociationDefendantRepository.save(defenceAssociationDefendant);

    }

    @Handles("defence.event.defence-organisation-association-unlocked-bdf")
    public void bdfProcessUnlockDefenceOrganisationAssociation(final Envelope<DefenceOrganisationAssociationUnlockedBdf> envelope) {
        final DefenceOrganisationAssociationUnlockedBdf defenceOrganisationAssociationUnlockedBdf = envelope.payload();
        final UUID defendantId = defenceOrganisationAssociationUnlockedBdf.getDefendantId();
        final DefenceAssociationDefendant defenceAssociationDefendant = defenceAssociationDefendantRepository.findOptionalByDefendantId(defendantId);
        if(isNull(defenceAssociationDefendant)){
            return;
        }
        final Optional<DefenceAssociation> defenceAssociationOptional = defenceAssociationDefendant.getDefenceAssociations().stream().filter(da -> isNull(da.getEndDate())).findFirst();
        defenceAssociationOptional.ifPresent(da -> {
            LOGGER.info("Found association for defendant {}. unlocking now", defendantId);
            da.setOrgId(defenceOrganisationAssociationUnlockedBdf.getOrganisationId());
            da.setRepresentationType(REPRESENTATION_ORDER.toString());
            defenceAssociationRepository.save(da);
        });
        associateWithDefenceClient(defendantId, defenceOrganisationAssociationUnlockedBdf.getOrganisationId());
    }

    private boolean associateWithDefenceClient(final UUID defendantId, final UUID organisationId) {

        final DefenceClient defenceClient = defenceClientRepository.findOptionalByDefendantId(defendantId);

        if (isNull(defenceClient )) {
            LOGGER.warn("No defenceClient found for defendantId {}", defendantId);
            return false;
        }

        final UUID existingAssociatedOrganisationId = defenceClient.getAssociatedOrganisation();

        if (existingAssociatedOrganisationId != null && !existingAssociatedOrganisationId.equals(organisationId)) {

            LOGGER.info("Overwriting existing associated organisation {} with new organisation {} for defendant {}",
                    existingAssociatedOrganisationId, organisationId, defendantId);

            defenceClient.setLastAssociatedOrganisation(existingAssociatedOrganisationId);

        }

        defenceClient.setAssociatedOrganisation(organisationId);
        defenceClient.setLockedByRepOrder(false);

        defenceClientRepository.save(defenceClient);
        return true;
    }

    private DefenceAssociationDefendant prepareDefenceAssociationEntity(final UUID defendantId,
                                                                        final UUID requesterUserId,
                                                                        final UUID defenceOrganisationId,
                                                                        final ZonedDateTime startDate,
                                                                        final String representationType,
                                                                        final String laaContractNumber) {
        DefenceAssociationDefendant defenceAssociationDefendant = findDefenceAssociationByDefendantId(defendantId);
        if (defenceAssociationDefendant == null) {
            defenceAssociationDefendant = new DefenceAssociationDefendant();
            defenceAssociationDefendant.setDefendantId(defendantId);
        }

        final List<DefenceAssociation> defenceAssociations = defenceAssociationDefendant.getDefenceAssociations();

        // start date of new association record is end date of old association record
        updateDefenceAssociationEndDate(defenceAssociations, startDate);

        final DefenceAssociation defenceAssociation = new DefenceAssociation();
        defenceAssociation.setId(randomUUID());
        defenceAssociation.setUserId(requesterUserId);
        defenceAssociation.setOrgId(defenceOrganisationId);
        defenceAssociation.setStartDate(startDate);
        defenceAssociation.setRepresentationType(representationType);
        defenceAssociation.setDefenceAssociationDefendant(defenceAssociationDefendant);
        defenceAssociation.setLaaContractNumber(laaContractNumber);

        defenceAssociations.add(defenceAssociation);
        return defenceAssociationDefendant;
    }

    private void updateDefenceAssociationEndDate(final List<DefenceAssociation> defenceAssociations, final ZonedDateTime endDate) {
        defenceAssociations.stream()
                .filter(defenceAssociation ->  isNull(defenceAssociation.getEndDate()))
                .forEach(defenceAssociation -> defenceAssociation.setEndDate(endDate));
    }

    private boolean disassociateFromDefenceClient(final UUID defendantId, final UUID organisationId) {

        final DefenceClient defenceClient = defenceClientRepository.findOptionalByDefendantId(defendantId);
        if(isNull(defenceClient)){
            return false;
        }

        final UUID associatedOrganisationId = defenceClient.getAssociatedOrganisation();

        if (associatedOrganisationId != null) {
            if (!associatedOrganisationId.equals(organisationId)) {
                LOGGER.warn("Organisation {} to disassociate from defendant {} " +
                                "does not match with currently-associated organisation {}, ignoring event.",
                        organisationId, defendantId, associatedOrganisationId);

                return true;
            }

            defenceClient.setLastAssociatedOrganisation(associatedOrganisationId);
        }

        defenceClient.setAssociatedOrganisation(null);

        defenceClientRepository.save(defenceClient);
        return true;
    }


    private DefenceAssociationDefendant findDefenceAssociationByDefendantId(final UUID defendantId) {

        return defenceAssociationDefendantRepository.findOptionalByDefendantId(defendantId);

    }

    private DefenceAssociationDefendant disassociateOrganisation(final DefenceAssociationDefendant defenceAssociationDefendant, final UUID organisationId, final ZonedDateTime endDate) {

        final List<DefenceAssociation> defenceAssociations = defenceAssociationDefendant
                .getDefenceAssociations();

        final DefenceAssociation currentDefenceAssociation = getCurrentDefenceAssociation(organisationId, defenceAssociations);
        currentDefenceAssociation.setEndDate(endDate);
        defenceAssociations.add(currentDefenceAssociation);
        defenceAssociationDefendant.setDefenceAssociations(defenceAssociations);

        return defenceAssociationDefendant;
    }

    private DefenceAssociation getCurrentDefenceAssociation(final UUID organisationId, final List<DefenceAssociation> defenceAssociations) {
        final List<DefenceAssociation> currentDefenceAssociations = defenceAssociations
                .stream()
                .filter(d -> d.getEndDate() == null)
                .collect(toList());

        final Optional<DefenceAssociation> optionalCurrentDefenceAssociation = currentDefenceAssociations.stream()
                .filter(cda -> organisationId.equals(cda.getOrgId()) || isNull(cda.getOrgId()))

                .findAny();

        if (!optionalCurrentDefenceAssociation.isPresent()) {
            throw new IllegalArgumentException("Mismatched Organisation Ids");
        }

        return optionalCurrentDefenceAssociation.get();
    }

}
