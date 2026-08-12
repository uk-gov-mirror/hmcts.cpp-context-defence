package uk.gov.moj.cpp.defence.event.listener;

import static java.util.Objects.nonNull;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;
import static uk.gov.moj.cpp.defence.event.listener.utils.ProsecutionCaseAccessTransformer.toOrganisationAccess;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.events.CaseAssigmentToOrganisationRemoved;
import uk.gov.moj.cpp.defence.events.CaseAssignedToOrganisation;
import uk.gov.moj.cpp.defence.events.CasesAssignedToOrganisation;
import uk.gov.moj.cpp.defence.persistence.OrganisationAccessRepository;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationAccess;
import uk.gov.moj.cpp.defence.persistence.entity.ProsecutionOrganisationCaseKey;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ServiceComponent(EVENT_LISTENER)
public class ProsecutionOrganisationAccessEventListener {

    @Inject
    private OrganisationAccessRepository organisationAccessRepository;

    @Inject
    private ProsecutionOrganisationService prosecutionOrganisationService;

    @Inject
    private EntityManager entityManager;

    @Handles("defence.events.case-assigned-to-organisation")
    public void prosecutionCaseAssignmentToOrganisationReceived(final Envelope<CaseAssignedToOrganisation> envelope) {
        final CaseAssignedToOrganisation caseAssignedToOrganisation = envelope.payload();
        final ProsecutionOrganisationAccess organisationAccess = toOrganisationAccess(caseAssignedToOrganisation.getCaseId(), caseAssignedToOrganisation.getAssigneeOrganisation(), caseAssignedToOrganisation.getAssigneeDetails(),
                caseAssignedToOrganisation.getAssignorDetails(), caseAssignedToOrganisation.getAssignorOrganisation(), caseAssignedToOrganisation.getRepresentingOrganisation(),
                caseAssignedToOrganisation.getAssignmentTimestamp());
        organisationAccessRepository.save(organisationAccess);
    }

    @Handles("defence.events.case-assignment-to-organisation-removed")
    public void prosecutionRemoveCaseAssignmentToOrganisationReceived(final Envelope<CaseAssigmentToOrganisationRemoved> envelope) {
        final CaseAssigmentToOrganisationRemoved caseAssigmentToOrganisationRemoved = envelope.payload();

        final ProsecutionOrganisationCaseKey organisationCaseKey = new ProsecutionOrganisationCaseKey(caseAssigmentToOrganisationRemoved.getCaseId(), caseAssigmentToOrganisationRemoved.getAssigneeOrganisationId());
        final ProsecutionOrganisationAccess prosecutionOrganisationAccess = organisationAccessRepository.findBy(organisationCaseKey);
        if(nonNull(prosecutionOrganisationAccess) ) {
            if (!entityManager.contains(prosecutionOrganisationAccess)) {
                entityManager.merge(prosecutionOrganisationAccess);
            }
            organisationAccessRepository.remove(prosecutionOrganisationAccess);
        }
    }

    @Handles("defence.events.cases-assigned-to-organisation")
    public void prosecutionCasesAssignmentToOrganisationReceived(final Envelope<CasesAssignedToOrganisation> envelope) {
        final CasesAssignedToOrganisation casesAssignedToOrganisation = envelope.payload();

        casesAssignedToOrganisation.getCaseHearingAssignments()
                .forEach(cha -> prosecutionOrganisationService.updateOrSave(cha.getCaseId(), casesAssignedToOrganisation.getAssigneeDetails(), casesAssignedToOrganisation.getAssigneeOrganisation(), casesAssignedToOrganisation.getAssignorDetails(),
                        casesAssignedToOrganisation.getAssignorOrganisation(), casesAssignedToOrganisation.getRepresentingOrganisation(),
                        casesAssignedToOrganisation.getAssignmentTimestamp()));
    }

}
