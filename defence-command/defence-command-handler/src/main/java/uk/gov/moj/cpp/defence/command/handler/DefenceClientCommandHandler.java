package uk.gov.moj.cpp.defence.command.handler;

import static java.time.LocalDate.parse;
import static java.util.Objects.nonNull;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.justice.cps.defence.OffenceCode.offenceCode;
import static uk.gov.moj.cpp.defence.command.util.EventStreamAppender.appendEventsToStream;

import uk.gov.justice.core.courts.Offence;
import uk.gov.justice.cps.defence.OffenceCode;
import uk.gov.justice.cps.defence.OffenceCodeReferenceData;
import uk.gov.justice.cps.defence.ReceiveAllegationsAgainstADefenceClient;
import uk.gov.justice.cps.defence.RecordAccessToIdpc;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.aggregate.AggregateService;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.eventsourcing.source.core.EventStream;
import uk.gov.justice.services.eventsourcing.source.core.exception.EventStreamException;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.aggregate.DefenceClient;
import uk.gov.moj.cpp.defence.commands.ReceiveDefenceClient;
import uk.gov.moj.cpp.defence.commands.ReceiveUrnForDefenceClient;
import uk.gov.moj.cpp.defence.commands.RecordIdpcDetails;
import uk.gov.moj.cpp.defence.commands.RecordInstructionDetails;
import uk.gov.moj.cpp.defence.commands.UpdateDefendantOffences;
import uk.gov.moj.cpp.defence.event.listener.events.AddedOffences;
import uk.gov.moj.cpp.defence.event.listener.events.DeletedOffences;
import uk.gov.moj.cpp.defence.service.UserGroupService;
import uk.gov.moj.cpp.defence.service.referencedata.ReferenceDataService;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceComponent(Component.COMMAND_HANDLER)
public class DefenceClientCommandHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefenceClientCommandHandler.class);
    @Inject
    private EventSource eventSource;

    @Inject
    private AggregateService aggregateService;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private UserGroupService usersGroupQueryService;

    @Inject
    private Requester requester;
    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;


    @Handles("defence.command.update-defendant-offences")
    public void receiveDefendantOffencesChanged(final Envelope<UpdateDefendantOffences> envelope) throws EventStreamException {
        JsonObject jsonObject = objectToJsonObjectConverter.convert(envelope.payload());
        LOGGER.info("Inside receiveDefendantOffencesChanged ={}", jsonObject != null ? jsonObject.toString(): "");
        final UpdateDefendantOffences updateDefendantOffences = envelope.payload();


        final AtomicReference<UUID> defenceClientIdAtomicReference = new AtomicReference<>();
        setDefenceClientIdAtomicReference(defenceClientIdAtomicReference, updateDefendantOffences);

        final EventStream defendantEventStream = eventSource.getStreamById(defenceClientIdAtomicReference.get());
        final DefenceClient defenceClientAggregate = aggregateService.get(defendantEventStream, DefenceClient.class);

        List<uk.gov.moj.cpp.defence.event.listener.events.AddedOffences> addedOffenceList = null;
        List<uk.gov.moj.cpp.defence.event.listener.events.DeletedOffences> deletedOffenceList = null;

        addedOffenceList = getAddedOffences(envelope, updateDefendantOffences, defenceClientAggregate.getIsCivil(), defenceClientIdAtomicReference);
        LOGGER.info("Inside addedOffenceList={}", addedOffenceList);
        deletedOffenceList = getDeletedOffences(updateDefendantOffences, defenceClientAggregate.getIsCivil(), defenceClientIdAtomicReference);
        LOGGER.info("Inside deletedOffenceList={}", deletedOffenceList);
        if (updateDefendantOffences.getUpdatedOffences() != null) {
            final Boolean isCivil = defenceClientAggregate.getIsCivil();
            final List<uk.gov.moj.cpp.defence.event.listener.events.AddedOffences> addedOffenceUpdateList = updateDefendantOffences.getUpdatedOffences().stream()
                    .map(updatedOffences -> {

                        final List<uk.gov.justice.cps.defence.Offence> offenceList = getOffenceList(updatedOffences.getOffences(), envelope, isCivil);

                        defenceClientIdAtomicReference.set(updatedOffences.getDefendantId());
                        return AddedOffences.addedOffences()
                                .withOffences(offenceList)
                                .withDefenceClientId(updatedOffences.getDefendantId())
                                .withDefendantId(updatedOffences.getDefendantId())
                                .withProsecutionCaseId(updatedOffences.getProsecutionCaseId())
                                .withIsCivil(isCivil)
                                .build();

                    }).collect(Collectors.toList());

            addedOffenceList.addAll(addedOffenceUpdateList);


            final List<uk.gov.moj.cpp.defence.event.listener.events.DeletedOffences> deletedOffenceUpdateList = updateDefendantOffences.getUpdatedOffences().stream()
                    .map(deletedOffence -> {

                                final List<uk.gov.justice.cps.defence.Offence> offenceList = getOffenceList(deletedOffence.getOffences(), envelope, defenceClientAggregate.getIsCivil());

                                return uk.gov.moj.cpp.defence.event.listener.events.DeletedOffences.deletedOffences()
                                        .withDefenceClientId(deletedOffence.getDefendantId())
                                        .withDefendantId(deletedOffence.getDefendantId())
                                        .withOffences(offenceList.stream().map(uk.gov.justice.cps.defence.Offence::getId).collect(Collectors.toList()))
                                        .withProsecutionCaseId(deletedOffence.getProsecutionCaseId())
                                        .withIsCivil(isCivil)
                                        .build();
                            }
                    ).collect(Collectors.toList());

            deletedOffenceList.addAll(deletedOffenceUpdateList);
        }

        final Stream<Object> events = defenceClientAggregate.receiveUpdateOffences(parse(updateDefendantOffences.getModifiedDate()), addedOffenceList, deletedOffenceList);

        appendEventsToStream(envelope, defendantEventStream, events);

    }



    private void setDefenceClientIdAtomicReference(final AtomicReference<UUID> defenceClientIdAtomicReference, final UpdateDefendantOffences updateDefendantOffences) {

        setDefenceClientIdAtomicReferenceFromAddedOffences(defenceClientIdAtomicReference, updateDefendantOffences.getAddedOffences());

        if (nonNull(defenceClientIdAtomicReference.get())) {
            return;
        }

        setDefenceClientIdAtomicReferenceFromUpdatedOffences(defenceClientIdAtomicReference, updateDefendantOffences.getUpdatedOffences());

        if (nonNull(defenceClientIdAtomicReference.get())) {
            return;
        }
        setDefenceClientIdAtomicReferenceFromDeletedOffences(defenceClientIdAtomicReference, updateDefendantOffences.getDeletedOffences());
    }

    private static void setDefenceClientIdAtomicReferenceFromDeletedOffences(final AtomicReference<UUID> defenceClientIdAtomicReference, final List<uk.gov.moj.cpp.defence.commands.DeletedOffences> deletedOffencesList) {
        if (isNotEmpty(deletedOffencesList)) {
            for (uk.gov.moj.cpp.defence.commands.DeletedOffences deletedOffences : deletedOffencesList) {
                if (nonNull(defenceClientIdAtomicReference.get())) {
                    return;
                } else {
                    defenceClientIdAtomicReference.set(deletedOffences.getDefendantId());
                }
            }

        }
    }

    private static void setDefenceClientIdAtomicReferenceFromUpdatedOffences(final AtomicReference<UUID> defenceClientIdAtomicReference, final List<uk.gov.moj.cpp.defence.commands.UpdatedOffences> updatedOffencesList) {
        if (isNotEmpty(updatedOffencesList)) {
            for (uk.gov.moj.cpp.defence.commands.UpdatedOffences updatedOffences : updatedOffencesList) {
                if (nonNull(defenceClientIdAtomicReference.get())) {
                    return;
                } else {
                    defenceClientIdAtomicReference.set(updatedOffences.getDefendantId());
                }
            }
        }
    }

    private static void setDefenceClientIdAtomicReferenceFromAddedOffences(final AtomicReference<UUID> defenceClientIdAtomicReference, final List<uk.gov.moj.cpp.defence.commands.AddedOffences> addedOffencesList) {
        if (isNotEmpty(addedOffencesList)) {
            for (uk.gov.moj.cpp.defence.commands.AddedOffences addedOffences : addedOffencesList) {
                if (nonNull(defenceClientIdAtomicReference.get())) {
                    return;
                } else {
                    defenceClientIdAtomicReference.set(addedOffences.getDefendantId());
                }
            }
        }
    }

    private List<DeletedOffences> getDeletedOffences(final UpdateDefendantOffences updateDefendantOffences, final Boolean isCivil, final AtomicReference<UUID> defenceClientIdAtomicReference) {
        if (updateDefendantOffences.getDeletedOffences() != null) {

            return updateDefendantOffences.getDeletedOffences().stream()
                    .map(deletedOffence -> {
                        if (defenceClientIdAtomicReference.get() == null) {
                            defenceClientIdAtomicReference.set(deletedOffence.getDefendantId());

                        }
                        return DeletedOffences.deletedOffences()
                                .withDefenceClientId(deletedOffence.getDefendantId())
                                .withDefendantId(deletedOffence.getDefendantId())
                                .withOffences(deletedOffence.getOffences())
                                .withProsecutionCaseId(deletedOffence.getProsecutionCaseId())
                                .withIsCivil(isCivil)
                                .build();
                    }).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<AddedOffences> getAddedOffences(final Envelope<UpdateDefendantOffences> envelope, final UpdateDefendantOffences updateDefendantOffences, final Boolean isCivil, final AtomicReference<UUID> defenceClientIdAtomicReference) {
        if (updateDefendantOffences.getAddedOffences() == null) {
            return new ArrayList<>();
        } else {
            return updateDefendantOffences.getAddedOffences().stream()
                    .map(addedOffence -> {

                        final List<uk.gov.justice.cps.defence.Offence> offenceList = getOffenceList(addedOffence.getOffences(), envelope, isCivil);
                        defenceClientIdAtomicReference.set(addedOffence.getDefendantId());
                        return AddedOffences.addedOffences()
                                .withOffences(offenceList)
                                .withDefenceClientId(addedOffence.getDefendantId())
                                .withDefendantId(addedOffence.getDefendantId())
                                .withProsecutionCaseId(addedOffence.getProsecutionCaseId())
                                .withIsCivil(isCivil)
                                .build();

                    }).collect(Collectors.toList());
        }
    }

    private List<uk.gov.justice.cps.defence.Offence> getOffenceList(final List<Offence> offenceList, final Envelope<UpdateDefendantOffences> envelope, final Boolean isCivil) {

        return offenceList.stream()
                .map(offence -> {

                    final uk.gov.moj.cpp.referencedata.query.Offences refDataOffences = referenceDataService.getRefDataOffences(offence.getOffenceCode(), offence.getStartDate(), envelope.metadata(), isCivil);

                    return getOffence(offence, refDataOffences);
                }).collect(Collectors.toList());

    }

    private uk.gov.justice.cps.defence.Offence getOffence(final Offence offence, final uk.gov.moj.cpp.referencedata.query.Offences refDataOffences) {
        return uk.gov.justice.cps.defence.Offence.offence()
                .withStartDate(offence.getStartDate())
                .withId(offence.getId())
                .withWording(offence.getWording())
                .withOffenceCodeDetails(getOffenceCode(offence, refDataOffences))
                .withArrestDate(offence.getArrestDate())
                .withEndDate(offence.getEndDate())
                .withChargeDate(offence.getChargeDate())
                .withCjsCode(offence.getOffenceCode())
                .build();
    }

    private OffenceCode getOffenceCode(final Offence offence, final uk.gov.moj.cpp.referencedata.query.Offences refDataOffences) {

        OffenceCode.Builder builder = offenceCode()
                .withId(offence.getId().toString())
                .withCjsoffencecode(offence.getOffenceCode())
                .withStandardoffencewording(offence.getWording());

        return refDataOffences.getDetails() != null ?
                builder.withTitle(refDataOffences.getTitle()).withLegislation(refDataOffences.getLegislation()).build() :
                builder.withTitle(offence.getOffenceTitle()).withLegislation(offence.getOffenceLegislation()).build();
    }

    @Handles("defence.command.receive-allegations-against-a-defence-client")
    public void receiveAllegationsAgainstDefenceClient(final Envelope<ReceiveAllegationsAgainstADefenceClient> envelope) throws EventStreamException {
        final ReceiveAllegationsAgainstADefenceClient payload = envelope.payload();
        final UUID defenceClientId = payload.getDefenceClientId();
        final EventStream eventStream = eventSource.getStreamById(defenceClientId);
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStream, DefenceClient.class);

        final Boolean isCaseCivil = nonNull(defenceClientAggregate.getIsCivil()) ? defenceClientAggregate.getIsCivil() : payload.getDefendantDetails().getIsCivil();

        final List<OffenceCodeReferenceData> offenceCodeReferenceDataList =
                referenceDataService.retrieveReferenceDataForOffences(payload.getOffences(), envelope.metadata(), isCaseCivil);


        final Stream<Object> events = defenceClientAggregate.receiveAllegations(
                defenceClientId,
                payload.getDefendantId(),
                payload.getDefendantDetails(),
                payload.getPoliceDefendantId(),
                payload.getOffences(),
                offenceCodeReferenceDataList);

        appendEventsToStream(envelope, eventStream, events);

    }

    @Handles("defence.command.receive-defence-client")
    public void receiveDefenceClient(final Envelope<ReceiveDefenceClient> envelope) throws EventStreamException {

        final ReceiveDefenceClient receiveDefenceClient = envelope.payload();

        final UUID defenceClientId = receiveDefenceClient.getDefenceClientId();
        final EventStream eventStream = eventSource.getStreamById(defenceClientId);
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStream, DefenceClient.class);

        final Stream<Object> events = defenceClientAggregate.receiveADefenceClient(defenceClientId,
                receiveDefenceClient.getUrn(),
                receiveDefenceClient.getDefendantDetails(),
                receiveDefenceClient.getDefenceClientDetails(),
                receiveDefenceClient.getDefendantId());
        appendEventsToStream(envelope, eventStream, events);

    }

    @Handles("defence.command.record-instruction-details")
    public void recordInstructionDetails(final Envelope<RecordInstructionDetails> envelope) throws EventStreamException {
        final RecordInstructionDetails recordInstructionDetails = envelope.payload();
        final UUID defenceClientId = recordInstructionDetails.getDefenceClientId();
        final EventStream eventStream = eventSource.getStreamById(defenceClientId);
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStream, DefenceClient.class);
        final UUID userId = fromString(envelope.metadata().userId().orElse(null));
        final UUID organisationId = usersGroupQueryService.getOrganisationForUser(userId, envelope.metadata(), requester);

        final Stream<Object> events = defenceClientAggregate.recordInstructionDetails(recordInstructionDetails.getInstructionDate(), userId,
                organisationId, randomUUID(), defenceClientId);

        appendEventsToStream(envelope, eventStream, events);

    }


    @Handles("defence.command.receive-urn-for-defence-client")
    public void receiveUrnForDefenceClient(final Envelope<ReceiveUrnForDefenceClient> envelope) throws EventStreamException {
        final ReceiveUrnForDefenceClient receiveUrnForDefenceClient = envelope.payload();
        final UUID defenceClientId = receiveUrnForDefenceClient.getDefenceClientId();
        final String urn = receiveUrnForDefenceClient.getUrn();
        final EventStream eventStream = eventSource.getStreamById(defenceClientId);
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStream, DefenceClient.class);

        final Stream<Object> events = defenceClientAggregate.receiveUrn(defenceClientId, urn);

        appendEventsToStream(envelope, eventStream, events);
    }

    @Handles("defence.command.record-idpc-details")
    public void recordIdpcDetails(final Envelope<RecordIdpcDetails> envelope) throws EventStreamException {
        final RecordIdpcDetails recordIdpcDetails = envelope.payload();
        final UUID defenceClientId = recordIdpcDetails.getDefenceClientId();
        final EventStream eventStream = eventSource.getStreamById(defenceClientId);
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStream, DefenceClient.class);

        final Stream<Object> events = defenceClientAggregate.recordIdpcDetails(recordIdpcDetails.getIdpcDetails(), defenceClientId);

        appendEventsToStream(envelope, eventStream, events);

    }

    @Handles("defence.command.record-access-to-idpc")
    public void recordIDPCAccess(final Envelope<RecordAccessToIdpc> envelope) throws EventStreamException {
        final RecordAccessToIdpc recordAccessToIdpc = envelope.payload();
        final UUID defenceClientId = recordAccessToIdpc.getDefenceClientId();
        final UUID userId = recordAccessToIdpc.getUserId();
        final UUID organisationId = recordAccessToIdpc.getOrganisationId();
        final UUID idpcDetailsId = recordAccessToIdpc.getIdpcDetailsId();
        final UUID materialId = recordAccessToIdpc.getMaterialId();
        final ZonedDateTime accessTimestamp = recordAccessToIdpc.getAccessTimestamp();

        final EventStream eventStream = eventSource.getStreamById(defenceClientId);
        final DefenceClient defenceClientAggregate = aggregateService.get(eventStream, DefenceClient.class);

        final Stream<Object> events = defenceClientAggregate.recordIdpcAccess(accessTimestamp, userId, organisationId, idpcDetailsId, materialId);

        appendEventsToStream(envelope, eventStream, events);

    }
}

