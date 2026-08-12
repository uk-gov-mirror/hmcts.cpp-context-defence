package uk.gov.moj.cpp.defence.event.listener;

import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static uk.gov.moj.cpp.defence.event.listener.utils.ProsecutionCaseAccessTransformer.toOrganisationAccess;
import static uk.gov.moj.cpp.defence.event.listener.utils.ProsecutionCaseAccessTransformer.updateAssignorDetailsAndExpiryDate;

import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.event.listener.utils.ApplicationParameters;
import uk.gov.moj.cpp.defence.persistence.OrganisationAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProsecutionOrganisationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProsecutionOrganisationService.class);

    @Inject
    private OrganisationAccessRepository organisationAccessRepository;

    @Inject
    private ApplicationParameters applicationParameters;

    public ProsecutionOrganisationAccess updateOrSave(final UUID caseId, final PersonDetails assigneePersonDetails,
                                                      final Organisation assigneeOrganisation, final PersonDetails assignorPersonDetails,
                                                      final Organisation assignorOrganisation, final String representingOrganisation, final ZonedDateTime assignmentTimestamp) {

        final Optional<ProsecutionOrganisationAccess> existingProsecutionOrganisationAccess = getProsecutionOrganisationAccess(caseId, assigneeOrganisation.getOrgId());

        if (existingProsecutionOrganisationAccess.isPresent()) {
            final ProsecutionOrganisationAccess existingEntity = existingProsecutionOrganisationAccess.get();
            if (nonNull(existingEntity.getAssignmentExpiryDate())) {

                updateAssignorDetailsAndExpiryDate(existingEntity, assignorPersonDetails, assignorOrganisation, assignmentTimestamp,
                        applicationParameters.getAssignmentExpiryHours());
                organisationAccessRepository.saveAndFlush(existingEntity);
            }
            return existingEntity;
        } else {
            final ProsecutionOrganisationAccess organisationAccess = toOrganisationAccess(caseId, assigneeOrganisation, assigneePersonDetails,
                    assignorPersonDetails, assignorOrganisation, representingOrganisation, assignmentTimestamp);
            organisationAccess.setAssignmentExpiryDate(assignmentTimestamp.plusHours(applicationParameters.getAssignmentExpiryHours()));

            organisationAccessRepository.saveAndFlush(organisationAccess);
            return organisationAccess;
        }
    }

    public Optional<ProsecutionOrganisationAccess> getProsecutionOrganisationAccess(final UUID caseId, final UUID organisationId) {
        final ProsecutionOrganisationCaseKey prosecutionOrgCaseKey = new ProsecutionOrganisationCaseKey(caseId, organisationId);
        try {
            return ofNullable(organisationAccessRepository.findBy(prosecutionOrgCaseKey));
        } catch (NoResultException nre) {
            LOGGER.info("No existing assignment found for the organisation id: {}", organisationId, nre);
        }
        return empty();
    }
}
