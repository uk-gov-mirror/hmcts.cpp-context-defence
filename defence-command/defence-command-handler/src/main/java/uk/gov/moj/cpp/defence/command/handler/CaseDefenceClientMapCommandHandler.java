package uk.gov.moj.cpp.defence.command.handler;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.UUID.fromString;
import static uk.gov.justice.cps.defence.DefendantDetails.defendantDetails;
import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

import uk.gov.justice.core.courts.LegalEntityDefendant;
import uk.gov.justice.core.courts.PersonDefendant;
import uk.gov.justice.cps.defence.AddDefendant;
import uk.gov.justice.cps.defence.DefendantDetails;
import uk.gov.justice.cps.defence.Offence;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.CaseDetails;
import uk.gov.moj.cpp.defence.Defendant;
import uk.gov.moj.cpp.defence.aggregate.CaseDefenceClientMap;
import uk.gov.moj.cpp.defence.aggregate.DefenceClient;
import uk.gov.moj.cpp.defence.command.handler.commands.ProsecutionCaseReceiveDetails;
import uk.gov.moj.cpp.defence.commands.CaseDefendantChanged;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;

@SuppressWarnings("WeakerAccess")
@ServiceComponent(Component.COMMAND_HANDLER)
public class CaseDefenceClientMapCommandHandler {

    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;

    @Handles("defence.command.prosecution-case-receive-details")
    public void receiveProsecutionCaseDetails(final Envelope<ProsecutionCaseReceiveDetails> envelope) throws EventStreamException {
        final ProsecutionCaseReceiveDetails prosecutionCaseReceiveDetails = envelope.payload();
        final CaseDetails caseDetails = prosecutionCaseReceiveDetails.getCaseDetails();
        final EventStream eventStream = eventSource.getStreamById(caseDetails.getCaseId());
        final CaseDefenceClientMap caseDefenceClientMap = aggregateService.get(eventStream, CaseDefenceClientMap.class);
        final Stream<Object> events = caseDefenceClientMap.receiveDetails(caseDetails.getCaseId(), caseDetails.getProsecutorCaseReference(),
                caseDetails.getProsecutor().getProsecutingAuthority(), caseDetails.getIsCivil(), caseDetails.getIsGroupMember());
        appendEventsToStream(envelope, eventStream, events);

        final List<Defendant> defendantList = envelope.payload().getDefendants();
        for (final Defendant defendant : defendantList) {
            final UUID defendantId = fromString(defendant.getId());

            final String dateOfBirth = defendant.getDateOfBirth() != null ? defendant.getDateOfBirth().format((ofPattern("yyyy-MM-dd"))) : null;

            final DefendantDetails defendantDetails = defendantDetails()
                    .withId(defendantId)
                    .withCaseId(caseDetails.getCaseId())
                    .withFirstName(defendant.getFirstName())
                    .withLastName(defendant.getLastName())
                    .withDateOfBirth(dateOfBirth)
                    .withOrganisation(defendant.getOrganisation())
                    .build();

            final String policeDefendantId = defendant.getAsn();

            final List<Offence> offences = defendant.getOffences();

            final Stream<Object> addADefendantEvents = caseDefenceClientMap.addADefendant(defendantId, defendantDetails, policeDefendantId, offences);
            appendEventsToStream(envelope, eventStream, addADefendantEvents);
        }
    }


    @Handles("defence.command.add-defendant")
    public void addDefendant(final Envelope<AddDefendant> envelope) throws EventStreamException {
        final AddDefendant addDefendant = envelope.payload();
        final UUID caseId = addDefendant.getDefendantDetails().getCaseId();

        final EventStream eventStream = eventSource.getStreamById(caseId);
        final CaseDefenceClientMap caseDefenceClientMap = aggregateService.get(eventStream, CaseDefenceClientMap.class);

        final Stream<Object> events = caseDefenceClientMap.addADefendant(addDefendant.getDefendantId(),
                addDefendant.getDefendantDetails(),
                addDefendant.getPoliceDefendantId(),
                addDefendant.getOffences());
        appendEventsToStream(envelope, eventStream, events);

    }

    @Handles("defence.command.case-defendant-changed")
    public void receiveCaseDefendantChanged(final Envelope<CaseDefendantChanged> envelope) throws EventStreamException {

        final CaseDefendantChanged caseDefendantChanged = envelope.payload();
        final UUID defendantId = caseDefendantChanged.getDefendant().getId();
        final UUID caseId = caseDefendantChanged.getDefendant().getProsecutionCaseId();
        final PersonDefendant individualDefendant = caseDefendantChanged.getDefendant().getPersonDefendant();
        final LegalEntityDefendant corporateDefendant = caseDefendantChanged.getDefendant().getLegalEntityDefendant();

        final EventStream defendantEventStream = eventSource.getStreamById(defendantId);
        final DefenceClient defenceClientAggregate = aggregateService.get(defendantEventStream, DefenceClient.class);

        final DefendantDetails.Builder defendantDetailsBuilder = DefendantDetails.defendantDetails();
        defendantDetailsBuilder.withId(defendantId);
        defendantDetailsBuilder.withCaseId(caseId);

        if (individualDefendant != null) {
            defendantDetailsBuilder.withFirstName(individualDefendant.getPersonDetails().getFirstName());
            defendantDetailsBuilder.withLastName(individualDefendant.getPersonDetails().getLastName());
            defendantDetailsBuilder.withDateOfBirth(individualDefendant.getPersonDetails().getDateOfBirth());
        } else if (corporateDefendant != null) {
            defendantDetailsBuilder.withOrganisation(convertOrganization(corporateDefendant.getOrganisation()));
        }

        final Stream<Object> events = defenceClientAggregate.receiveUpdateClient(defendantDetailsBuilder.build(),defendantId);

        appendEventsToStream(envelope, defendantEventStream, events);

    }

    private uk.gov.moj.cpp.defence.Organisation convertOrganization(final uk.gov.justice.core.courts.Organisation organisationIn){
         return uk.gov.moj.cpp.defence.Organisation.organisation().withOrganisationName(organisationIn.getName()).build();
    }

}
