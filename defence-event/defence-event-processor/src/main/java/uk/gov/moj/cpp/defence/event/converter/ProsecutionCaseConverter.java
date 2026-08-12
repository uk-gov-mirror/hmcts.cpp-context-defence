package uk.gov.moj.cpp.defence.event.converter;

import uk.gov.justice.core.courts.ProsecutionCase;
import uk.gov.moj.cpp.defence.CaseDetails;
import uk.gov.moj.cpp.defence.Defendant;
import uk.gov.moj.cpp.defence.Prosecutor;
import uk.gov.moj.cpp.defence.event.processor.commands.ProsecutionCaseReceiveDetails;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import static uk.gov.moj.cpp.defence.CaseDetails.caseDetails;
import static uk.gov.moj.cpp.defence.event.processor.commands.ProsecutionCaseReceiveDetails.prosecutionCaseReceiveDetails;

public class ProsecutionCaseConverter {

    @Inject
    private ConverterUtil converterUtil;

    public ProsecutionCaseReceiveDetails convertToProsecutionCaseReceiveDetails(final ProsecutionCase prosecutionCase) {

        final String urn;
        if (prosecutionCase.getProsecutionCaseIdentifier().getCaseURN() != null) {
            urn = prosecutionCase.getProsecutionCaseIdentifier().getCaseURN();
        } else {
            urn = prosecutionCase.getProsecutionCaseIdentifier().getProsecutionAuthorityReference();
        }
        final Prosecutor prosecutor = Prosecutor.prosecutor()
                .withProsecutingAuthority((prosecutionCase.getProsecutionCaseIdentifier().getProsecutionAuthorityCode()))
                .withProsecutionAuthorityId((prosecutionCase.getProsecutionCaseIdentifier().getProsecutionAuthorityId())).build();

        final CaseDetails caseDetails = caseDetails().withCaseId(prosecutionCase.getId())
                .withProsecutorCaseReference(urn)
                .withProsecutor(prosecutor)
                .withIsCivil(prosecutionCase.getIsCivil())
                .withIsGroupMember(prosecutionCase.getIsGroupMember())
                .build();
        return prosecutionCaseReceiveDetails()
                .withCaseDetails(caseDetails)
                .withDefendants(transformDefendants(prosecutionCase.getDefendants()))
                .build();

    }

    private List<Defendant> transformDefendants(List<uk.gov.justice.core.courts.Defendant> defendants) {
        final List<Defendant> transformedDefendants = new ArrayList<>();
        defendants.forEach(defendant -> {
            final Defendant.Builder builder = Defendant.defendant().withId(defendant.getId().toString())
                    .withOrganisation(converterUtil.transformOrganisation(defendant.getLegalEntityDefendant(), defendant.getAliases()))
                    .withAsn(defendant.getProsecutionAuthorityReference())
                    .withOffences(converterUtil.transformOffences(defendant.getOffences()))
                    .withProsecutorDefendantReference(defendant.getProsecutionAuthorityReference());

            if (defendant.getPersonDefendant() != null && defendant.getPersonDefendant().getPersonDetails() != null) {
                builder.withFirstName(defendant.getPersonDefendant().getPersonDetails().getFirstName())
                        .withLastName(defendant.getPersonDefendant().getPersonDetails().getLastName());
                if (defendant.getPersonDefendant().getPersonDetails().getDateOfBirth() != null) {
                    builder.withDateOfBirth(LocalDate.parse(defendant.getPersonDefendant().getPersonDetails().getDateOfBirth()));
                }
            }
            transformedDefendants.add(builder.build());
        });
        return transformedDefendants;
    }


}
