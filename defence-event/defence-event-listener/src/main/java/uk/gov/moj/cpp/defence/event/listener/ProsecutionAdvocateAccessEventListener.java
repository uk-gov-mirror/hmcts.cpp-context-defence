package uk.gov.moj.cpp.defence.event.listener;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;
import static uk.gov.moj.cpp.defence.event.listener.utils.ProsecutionCaseAccessTransformer.toAdvocateAccess;
import static uk.gov.moj.cpp.defence.event.listener.utils.ProsecutionCaseAccessTransformer.toOrganisationAccess;

import uk.gov.justice.cps.defence.PersonDetails;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.Organisation;
import uk.gov.moj.cpp.defence.event.listener.utils.ApplicationParameters;
import uk.gov.moj.cpp.defence.events.CaseAssignedToAdvocate;
import uk.gov.moj.cpp.defence.events.CaseAssignmentToAdvocateRemoved;
import uk.gov.moj.cpp.defence.events.CaseHearingAssignments;
import uk.gov.moj.cpp.defence.events.CasesAssignedToAdvocate;
import uk.gov.moj.cpp.defence.persistence.AdvocateAccessRepository;
import uk.gov.moj.cpp.defence.persistence.OrganisationAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.AssignmentUserDetails;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionAdvocateAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(EVENT_LISTENER)
public class ProsecutionAdvocateAccessEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProsecutionAdvocateAccessEventListener.class);
    @Inject
    private AdvocateAccessRepository advocateAssignmentRepository;

    @Inject
    private OrganisationAccessRepository organisationAccessRepository;

    @Inject
    private ApplicationParameters applicationParameters;

    @Inject
    private ProsecutionOrganisationService prosecutionOrganisationService;

    @Inject
    private EntityManager entityManager;

    @Handles("defence.events.case-assigned-to-advocate")
    public void prosecutionCaseAssignmentToAdvocateReceived(final Envelope<CaseAssignedToAdvocate> envelope) {
        final CaseAssignedToAdvocate caseAssignedToAdvocate = envelope.payload();

        final ProsecutionOrganisationAccess assigneeOrganisationAccess = getAssigneeOrganisation(caseAssignedToAdvocate.getCaseId(), caseAssignedToAdvocate.getAssigneeOrganisation(), caseAssignedToAdvocate.getAssigneeDetails(),
                caseAssignedToAdvocate.getAssignorDetails(), caseAssignedToAdvocate.getAssignorOrganisation(), caseAssignedToAdvocate.getRepresentingOrganisation(), caseAssignedToAdvocate.getAssignmentTimestamp());

        final ProsecutionAdvocateAccess advocateAccess = toAdvocateAccess(caseAssignedToAdvocate.getAssigneeDetails(), caseAssignedToAdvocate.getAssignorDetails(),
                caseAssignedToAdvocate.getAssignorOrganisation(), assigneeOrganisationAccess, caseAssignedToAdvocate.getAssignmentTimestamp());
        advocateAssignmentRepository.save(advocateAccess);
    }

    @Handles("defence.events.case-assignment-to-advocate-removed")
    public void prosecutionRemoveCaseAssignmentToAdvocateReceived(final Envelope<CaseAssignmentToAdvocateRemoved> envelope) {
        final CaseAssignmentToAdvocateRemoved caseAssignmentToAdvocateRemoved = envelope.payload();

        final ProsecutionOrganisationCaseKey organisationCaseKey = new ProsecutionOrganisationCaseKey(caseAssignmentToAdvocateRemoved.getCaseId(), caseAssignmentToAdvocateRemoved.getAssigneeOrganisationId());
        final ProsecutionOrganisationAccess prosecutionOrganisationAccess = organisationAccessRepository.findBy(organisationCaseKey);
        if (!entityManager.contains(prosecutionOrganisationAccess)) {
            entityManager.merge(prosecutionOrganisationAccess);
        }
        final Optional<ProsecutionAdvocateAccess> prosecutionAdvocateAccessMatch = nonNull(prosecutionOrganisationAccess.getProsecutionAdvocatesWithAccess()) ? prosecutionOrganisationAccess.getProsecutionAdvocatesWithAccess().stream()
                .filter(paa -> paa.getAssigneeDetails().getUserId().equals(caseAssignmentToAdvocateRemoved.getAssigneeUserId()))
                .findFirst() : Optional.empty();
        prosecutionAdvocateAccessMatch.ifPresent(paa -> prosecutionOrganisationAccess.getProsecutionAdvocatesWithAccess().remove(paa));

        if (prosecutionOrganisationAccess.getProsecutionAdvocatesWithAccess().isEmpty()) {
            organisationAccessRepository.remove(prosecutionOrganisationAccess);
        } else {
            organisationAccessRepository.save(prosecutionOrganisationAccess);
        }
    }

    @Handles("defence.events.cases-assigned-to-advocate")
    public void prosecutionCasesAssignmentToAdvocateReceived(final Envelope<CasesAssignedToAdvocate> envelope) {

        final CasesAssignedToAdvocate casesAssignedToAdvocate = envelope.payload();
        LOGGER.info("Inside prosecutionCasesAssignmentToAdvocateReceived={}", casesAssignedToAdvocate);

        casesAssignedToAdvocate.getCaseHearingAssignments().forEach(caa -> {

            final ProsecutionOrganisationAccess assigneeOrganisationAccess = saveAndGetProsecutionOrganisationAccess(casesAssignedToAdvocate, caa);
            final Optional<ProsecutionAdvocateAccess> existingAdvocateAccess = advocateAssignmentRepository
                    .findByCaseIdAndAssigneeId(caa.getCaseId(), casesAssignedToAdvocate.getAssigneeDetails().getUserId())
                    .stream().findFirst();

            if (existingAdvocateAccess.isPresent()) {
                final ProsecutionAdvocateAccess existingEntity = existingAdvocateAccess.get();
                if (nonNull(existingEntity.getAssignmentExpiryDate())) {
                    updateAssignorDetailsAndExpiryDate(casesAssignedToAdvocate, existingEntity, assigneeOrganisationAccess);
                    advocateAssignmentRepository.save(existingEntity);
                }
            } else {

                final ProsecutionAdvocateAccess advocateAccess = toAdvocateAccess(casesAssignedToAdvocate.getAssigneeDetails(), casesAssignedToAdvocate.getAssignorDetails(),
                        casesAssignedToAdvocate.getAssignorOrganisation(), assigneeOrganisationAccess, casesAssignedToAdvocate.getAssignmentTimestamp());
                advocateAccess.setAssignmentExpiryDate(casesAssignedToAdvocate.getAssignmentTimestamp().plusHours(applicationParameters.getAssignmentExpiryHours()));

                advocateAssignmentRepository.save(advocateAccess);
            }
        });

    }

    private ProsecutionOrganisationAccess saveAndGetProsecutionOrganisationAccess(final CasesAssignedToAdvocate casesAssignedToAdvocate, final CaseHearingAssignments caa) {
        return prosecutionOrganisationService.updateOrSave(caa.getCaseId(), casesAssignedToAdvocate.getAssigneeDetails(),
                casesAssignedToAdvocate.getAssigneeOrganisation(), casesAssignedToAdvocate.getAssignorDetails(),
                casesAssignedToAdvocate.getAssignorOrganisation(), casesAssignedToAdvocate.getRepresentingOrganisation(),
                casesAssignedToAdvocate.getAssignmentTimestamp());
    }

    private void updateAssignorDetailsAndExpiryDate(final CasesAssignedToAdvocate casesAssignedToAdvocate, final ProsecutionAdvocateAccess existingEntity,
                                                    final ProsecutionOrganisationAccess assigneeOrganisationAccess) {
        if (!existingEntity.getAssignorDetails().getUserId().equals(casesAssignedToAdvocate.getAssignorDetails().getUserId())) {
            final AssignmentUserDetails assignorDetails = new AssignmentUserDetails(randomUUID(), casesAssignedToAdvocate.getAssignorDetails().getUserId(),
                    casesAssignedToAdvocate.getAssignorDetails().getFirstName(), casesAssignedToAdvocate.getAssignorDetails().getLastName());
            existingEntity.setAssignorDetails(assignorDetails);
            existingEntity.setAssignorOrganisationId(casesAssignedToAdvocate.getAssignorOrganisation().getOrgId());
            existingEntity.setAssignorOrganisationName(casesAssignedToAdvocate.getAssignorOrganisation().getOrganisationName());
            existingEntity.setProsecutionOrganisation(assigneeOrganisationAccess);
        }

        existingEntity.setAssignedDate(casesAssignedToAdvocate.getAssignmentTimestamp());
        existingEntity.setAssignmentExpiryDate(casesAssignedToAdvocate.getAssignmentTimestamp().plusHours(applicationParameters.getAssignmentExpiryHours()));
    }

    private ProsecutionOrganisationAccess getAssigneeOrganisation(final UUID caseId, final Organisation assigneeOrganisation,
                                                                  final PersonDetails assigneeUserDetails, final PersonDetails assignorUserDetails,
                                                                  final Organisation assignorOrganisation, final String representingOrganisation,
                                                                  final ZonedDateTime assignmentTimestamp) {

        final Optional<ProsecutionOrganisationAccess> existingAssigneeOrganisation = organisationAccessRepository.findByAssigneeOrganisationIdAndCaseId(assigneeOrganisation.getOrgId(), caseId);
        if (existingAssigneeOrganisation.isEmpty()) {
            final ProsecutionOrganisationAccess organisationAccess = toOrganisationAccess(caseId, assigneeOrganisation, assigneeUserDetails,
                    assignorUserDetails, assignorOrganisation, representingOrganisation, assignmentTimestamp);
            organisationAccessRepository.save(organisationAccess);
            return organisationAccess;
        }
        return existingAssigneeOrganisation.get();
    }

}
