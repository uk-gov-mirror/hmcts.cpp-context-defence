package uk.gov.moj.cpp.defence.event.converter;

import static java.util.UUID.fromString;

import uk.gov.justice.core.courts.Defendant;
import uk.gov.justice.cps.defence.DefendantDetails;
import uk.gov.moj.cpp.defence.json.schema.event.DefendantAdded;

import jakarta.inject.Inject;

public class DefendantsAddedConverter {

    @Inject
    private ConverterUtil converterUtil;

    public DefendantAdded convert(Defendant defendant) {

        return DefendantAdded.defendantAdded()
                .withDefendantId(defendant.getId())
                .withPoliceDefendantId(defendant.getProsecutionAuthorityReference())
                .withDefendantDetails(transformDefendant(defendant))
                .withOffences(converterUtil.transformOffences(defendant.getOffences()))
                .build();

    }

    private DefendantDetails transformDefendant(final Defendant defendant) {
        final DefendantDetails.Builder builder = DefendantDetails.defendantDetails()
                .withCaseId(fromString(defendant.getProsecutionCaseId().toString()))
                .withId(fromString(defendant.getId().toString()));
        if (defendant.getPersonDefendant() != null && defendant.getPersonDefendant().getPersonDetails() != null) {
            builder.withLastName(defendant.getPersonDefendant().getPersonDetails().getLastName())
                    .withFirstName(defendant.getPersonDefendant().getPersonDetails().getFirstName())
                    .withDateOfBirth(defendant.getPersonDefendant().getPersonDetails().getDateOfBirth());
        }

        builder.withOrganisation(converterUtil.transformOrganisation(defendant.getLegalEntityDefendant(), defendant.getAliases()));

        return builder.build();

    }


}
