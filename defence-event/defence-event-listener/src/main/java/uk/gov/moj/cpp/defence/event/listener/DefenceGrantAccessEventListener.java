package uk.gov.moj.cpp.defence.event.listener;

import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;

import uk.gov.justice.cps.defence.events.AccessGrantRemoved;
import uk.gov.justice.cps.defence.events.AccessGranted;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceGrantAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceGrantAccess;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.OrganisationDetails;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_LISTENER)
public class DefenceGrantAccessEventListener {


    @Inject
    private DefenceGrantAccessRepository defenceGrantAccessRepository;

    @Inject
    private DefenceClientRepository defenceClientRepository;


    @Handles("defence.event.access-granted")
    public void defenceEventAccessGranted(final Envelope<AccessGranted> envelope) {
        final AccessGranted accessGranted = envelope.payload();

        final Optional<DefenceClient> defenceClient = defenceClientRepository.findOptionalBy(accessGranted.getDefendantId());
        if(defenceClient.isEmpty()){
            return;
        }

        final uk.gov.justice.cps.defence.PersonDetails granteePersonDetails = accessGranted.getGranteeDetails();
        final uk.gov.justice.cps.defence.PersonDetails granterPersonDetails = accessGranted.getGranterDetails();

        final DefenceUserDetails granteeDetails = new DefenceUserDetails(randomUUID(), granteePersonDetails.getUserId(), granteePersonDetails.getFirstName(), granteePersonDetails.getLastName());
        final DefenceUserDetails granterDetails = new DefenceUserDetails(randomUUID(), granterPersonDetails.getUserId(), granterPersonDetails.getFirstName(), granterPersonDetails.getLastName());

        final Organisation granteeOrganisation = accessGranted.getGranteeOrganisation();

        final OrganisationDetails organisationDetails = new OrganisationDetails(randomUUID(), granteeOrganisation.getOrgId(), granteeOrganisation.getOrganisationName());

        final DefenceGrantAccess defenceGrantAccess = new DefenceGrantAccess();
        defenceGrantAccess.setDefenceClient(defenceClient.get());
        defenceGrantAccess.setId(randomUUID());
        defenceGrantAccess.setRemoved(false);
        defenceGrantAccess.setStartDate(ZonedDateTime.now());
        defenceGrantAccess.setGranteeDefenceUserDetails(granteeDetails);
        defenceGrantAccess.setGrantorDefenceUserDetails(granterDetails);
        defenceGrantAccess.setGranteeOrganisationDetails(organisationDetails);

        defenceGrantAccessRepository.save(defenceGrantAccess);

    }

    @Handles("defence.event.access-grant-removed")
    public void defenceEventAccessGrantRemoved(final Envelope<AccessGrantRemoved> envelope) {
        final AccessGrantRemoved accessGrantRemoved = envelope.payload();

        final DefenceGrantAccess permissionEntity = defenceGrantAccessRepository.findByDefenceClient(accessGrantRemoved.getDefendantId(),accessGrantRemoved.getGranteeUserId());
        if (nonNull(permissionEntity)) {
            permissionEntity.setRemoved(true);
            permissionEntity.setEndDate(ZonedDateTime.now());
            defenceGrantAccessRepository.save(permissionEntity);
        }

    }

}
