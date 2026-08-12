package uk.gov.moj.cpp.defence.event.listener;

import static java.util.Objects.isNull;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;
import static uk.gov.moj.cpp.defence.persistence.entity.builder.AllegationBuilder.newAllegationBuilder;

import uk.gov.justice.cps.defence.AllegationsReceivedAgainstADefenceClient;
import uk.gov.justice.cps.defence.Offence;
import uk.gov.justice.cps.defence.OffenceCodeReferenceData;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.defence.event.listener.events.AddedOffences;
import uk.gov.moj.cpp.defence.event.listener.events.DefendantOffencesUpdated;
import uk.gov.moj.cpp.defence.event.listener.events.DeletedOffences;
import uk.gov.moj.cpp.defence.persistence.AllegationRepository;
import uk.gov.moj.cpp.defence.persistence.DefenceClientRepository;
import uk.gov.moj.cpp.defence.persistence.entity.Allegation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceClient;
import uk.gov.moj.cpp.defence.persistence.entity.builder.AllegationBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.inject.Inject;

@ServiceComponent(EVENT_LISTENER)
public class AllegationEventListener {

    @Inject
    private AllegationRepository allegationRepository;

    @Inject
    private DefenceClientRepository defenceClientRepository;

    @Handles("defence.event.allegations-received-against-a-defence-client")
    public void suspectIsCharged(final Envelope<AllegationsReceivedAgainstADefenceClient> envelope) {

        final AllegationsReceivedAgainstADefenceClient allegationsReceivedAgainstADefenceClient = envelope.payload();

        final UUID defenceClientId = allegationsReceivedAgainstADefenceClient.getDefenceClientId();

        final List<OffenceCodeReferenceData> offenceCodeReferenceDataList = allegationsReceivedAgainstADefenceClient.getOffenceCodeReferenceData();

        allegationsReceivedAgainstADefenceClient.getOffences().forEach(offence -> {

            final AllegationBuilder builder = newAllegationBuilder();

            builder.withId(randomUUID())
                    .withDefenceClientId(defenceClientId)
                    .withChargeDate(LocalDate.parse(offence.getStartDate()));

            // Add ReferenceData values
            offenceCodeReferenceDataList.stream()
                    .filter(rd -> rd.getCjsoffencecode().equals(offence.getCjsCode()))
                    .findFirst()
                    .ifPresent(rd -> {
                        builder.withLegislation(rd.getLegislation());
                        builder.withTitle(rd.getTitle());
                        builder.withOffenceId(offence.getId());
                    });


            allegationRepository.save(builder.build());
        });

    }


    @Handles("defence.event.defendant-offences-updated")
    @SuppressWarnings({"squid:S3776", "squid:S134"})
    public void defendantOffencesUpdated(final Envelope<DefendantOffencesUpdated> envelope) {

        final DefendantOffencesUpdated defendantOffencesUpdated = envelope.payload();

        final DefenceClient defenceClient = defenceClientRepository.findOptionalByDefendantId(envelope.metadata().streamId().orElseThrow(IllegalStateException::new));
        if(isNull(defenceClient)){
            return;
        }
        final List<AddedOffences> addedOffences = defendantOffencesUpdated.getAddedOffences();
        final List<DeletedOffences> deletedOffences = defendantOffencesUpdated.getDeletedOffences();

        // it seems it is not possible to delete offences in progression. Need to remove this code after verification. DD-5937
        if (deletedOffences != null) {
            deletedOffences.forEach(
                    deletedOffence -> {
                        final UUID defendantId = deletedOffence.getDefendantId();
                        final List<Allegation> allegationsToDelete = new ArrayList<>();
                        for (final Allegation allegation : defenceClient.getAllegationList()) {
                            for (final UUID offenceId : deletedOffence.getOffences()) {
                                if ((defendantId != null && defendantId.equals(allegation.getDefenceClientId()))
                                        && offenceId.equals(allegation.getOffenceId())) {
                                    allegationsToDelete.add(allegation);
                                }
                            }
                        }
                        allegationsToDelete.stream().forEach(e -> defenceClient.getAllegationList().remove(e));
                    }
            );
            defenceClientRepository.save(defenceClient);
        }

        if (addedOffences != null) {
            addedOffences.forEach(
                    addedOffence -> {
                        for (final Offence offence : addedOffence.getOffences()) {
                            final AllegationBuilder allegationBuilder = newAllegationBuilder();
                            allegationBuilder.withId(randomUUID())
                                    .withDefenceClientId(addedOffence.getDefenceClientId())
                                    .withChargeDate(LocalDate.parse(offence.getStartDate()))
                                    .withLegislation(offence.getOffenceCodeDetails().getLegislation())
                                    .withTitle(offence.getOffenceCodeDetails().getTitle())
                                    .withOffenceId(offence.getId());
                            final Allegation allegation = allegationBuilder.build();
                            allegationRepository.save(allegation);
                            defenceClient.getAllegationList().add(allegation);
                        }
                    }
            );
            defenceClientRepository.save(defenceClient);
        }
    }

}
