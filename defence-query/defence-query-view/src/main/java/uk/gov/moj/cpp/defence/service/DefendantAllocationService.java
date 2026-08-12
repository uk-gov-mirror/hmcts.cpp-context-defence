package uk.gov.moj.cpp.defence.service;

import static java.util.Objects.nonNull;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.cps.defence.OffencePleaDetails;
import uk.gov.justice.cps.defence.OffenceType;
import uk.gov.justice.cps.defence.PleasAllocationDetails;
import uk.gov.justice.cps.defence.YesNoNa;
import uk.gov.justice.cps.defence.plea.PleaDefendantDetails;
import uk.gov.justice.services.common.converter.ListToJsonArrayConverter;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.persistence.DefendantAllocationRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefendantAllocation;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@SuppressWarnings({"squid:S1188"})
public class DefendantAllocationService {

    public static final String CASE_ID = "caseId";

    @Inject
    private DefendantAllocationRepository repository;
    @Inject
    private ListToJsonArrayConverter<PleasAllocationDetails> listToJsonArrayConverter;

    public JsonEnvelope getPleasByCaseId(final String caseId) {
        final List<DefendantAllocation> defendantAllocations = repository.findDefendantAllocationByCaseId(UUID.fromString(caseId));
        final Metadata metadata = Envelope.metadataBuilder().withId(UUID.randomUUID())
                .withName("defence.query.pleas-and-allocation")
                .createdAt(ZonedDateTime.now())
                .build();

        final List<PleasAllocationDetails> pleasAllocationDetails = new ArrayList<>();
        defendantAllocations.stream().forEach(defendantAllocation -> {
            final PleasAllocationDetails.Builder builder = PleasAllocationDetails.pleasAllocationDetails().withAllocationId(defendantAllocation.getId())
                    .withOffencePleas(getOffencePleas(defendantAllocation)).withYouthAcknowledgement(defendantAllocation.getGuardianConsentProvided())
                    .withElectingCrownCourtTrialDetails(defendantAllocation.getElectCrownCourtTrailDetails()).withElectingCrownCourtTrial(defendantAllocation.getElectCrownCourtTrail())
                    .withDisputeOffenceValueDetails(defendantAllocation.getOffenceValueRepresentations()).withDisputeOffenceValue(defendantAllocation.getOffenceValueDisputed())
                    .withConsentToMagistratesCourtTrial(defendantAllocation.getConsentToMagistrateTrail()).withAcknowledgement(defendantAllocation.getAcknowledgement())
                    .withDefendantId(defendantAllocation.getDefendantId()).withRepresentationsOnGraveCrime(defendantAllocation.getRepresentationsOnGraveCrime())
                    .withRepresentationsOnGraveCrimeDetails(defendantAllocation.getRepresentationsOnGraveCrimeDetails()).withDefendantNameDobConfirmation(defendantAllocation.getDefendantNameDobConfirmation())
                    .withOffenceType(OffenceType.valueFor(defendantAllocation.getOffenceType()).orElse(null)).withAdditionalInformation(defendantAllocation.getAdditionalInformation())
                    .withDefendantTurningEighteenDetails(defendantAllocation.getDefendantTurningEighteenDetails()).withTheftFromShopDetails(defendantAllocation.getTheftFromShopDetails());

            if (nonNull(defendantAllocation.getDefendantFirstName()) || nonNull(defendantAllocation.getDefendantMiddleName()) ||
                    nonNull(defendantAllocation.getDefendantSurname()) || nonNull(defendantAllocation.getDefendantDateOfBirth()) || nonNull(defendantAllocation.getDefendantOrganisationName())) {
                builder.withDefendantDetails(PleaDefendantDetails.pleaDefendantDetails()
                        .withFirstName(defendantAllocation.getDefendantFirstName()).withMiddleName(defendantAllocation.getDefendantMiddleName())
                        .withSurname(defendantAllocation.getDefendantSurname()).withDob(defendantAllocation.getDefendantDateOfBirth())
                        .withOrganisationName(defendantAllocation.getDefendantOrganisationName()).build());
            }

            if (nonNull(defendantAllocation.getSentencingIndicationRequested())) {
                builder.withSentencingIndication(YesNoNa.valueOf(defendantAllocation.getSentencingIndicationRequested()));
            }

            if (nonNull(defendantAllocation.getCrownCourtObjection())) {
                builder.withCrownCourtObjection(YesNoNa.valueOf(defendantAllocation.getCrownCourtObjection()));
            }

            if (nonNull(defendantAllocation.getTheftFromShop())) {
                builder.withTheftFromShop(YesNoNa.valueOf(defendantAllocation.getTheftFromShop()));
            }

            pleasAllocationDetails.add(builder.build());
        });

        final JsonObject responsePayload = createObjectBuilder()
                .add("pleasAllocation", listToJsonArrayConverter.convert(pleasAllocationDetails))
                .build();

        return JsonEnvelope.envelopeFrom(metadata, responsePayload);
    }

    private List<OffencePleaDetails> getOffencePleas(final DefendantAllocation defendantAllocation) {
        return defendantAllocation.getDefendantAllocationPleas().stream()
                .map(s -> OffencePleaDetails.offencePleaDetails()
                        .withOffenceId(s.getOffenceId())
                        .withIndicatedPlea(s.getIndicatedPlea())
                        .withPleaDate(s.getPleaDate())
                        .build())
                .toList();
    }
}
