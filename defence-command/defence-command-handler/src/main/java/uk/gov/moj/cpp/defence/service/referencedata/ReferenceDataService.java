package uk.gov.moj.cpp.defence.service.referencedata;

import static java.lang.Boolean.TRUE;

import uk.gov.justice.cps.defence.Offence;
import uk.gov.justice.cps.defence.OffenceCodeReferenceData;
import uk.gov.justice.cps.defence.ReferenceDataOffencesListRequest;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.defence.common.util.GenericEnveloper;
import uk.gov.moj.cpp.referencedata.query.Offences;
import uk.gov.moj.cpp.referencedata.query.OffencesList;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

public class ReferenceDataService {

    @ServiceComponent(Component.COMMAND_HANDLER)
    @Inject
    Requester requester;

    @Inject
    GenericEnveloper genericEnveloper;

    public List<OffenceCodeReferenceData> retrieveReferenceDataForOffences(final List<Offence> offenceList, final Metadata metadata, final Boolean isCaseCivil) {

        return offenceList.stream()
                .map(offence -> {
                    final Offences refDataOffences = getRefDataOffences(offence.getCjsCode(),offence.getStartDate(),metadata,  isCaseCivil);

                    final OffenceCodeReferenceData.Builder offenceCodeReferenceDataBuilder = OffenceCodeReferenceData.offenceCodeReferenceData();
                    offenceCodeReferenceDataBuilder.withTitle(refDataOffences.getTitle());
                    offenceCodeReferenceDataBuilder.withLegislation(refDataOffences.getLegislation());
                    return offenceCodeReferenceDataBuilder.withCjsoffencecode(offence.getCjsCode())
                            .build();

                }).collect(Collectors.toList());
    }

    public Offences getRefDataOffences(final String cjsCode, final String startDate, final Metadata metadata, final Boolean isCaseCivil){
        final ReferenceDataOffencesListRequest.Builder requestBuilder = ReferenceDataOffencesListRequest.referenceDataOffencesListRequest()
                .withCjsoffencecode(cjsCode)
                .withDate(startDate);

        if(TRUE.equals(isCaseCivil)){
            requestBuilder.withSowRef("moj");
        }

        final Envelope envelope = genericEnveloper.envelopeWithNewActionName(requestBuilder.build(), metadata, "referencedataoffences.query.offences-list");
        final Envelope<OffencesList> response = requester.request(envelope, OffencesList.class);
        final OffencesList refDataOffencesList = response.payload();

        // cjsoffencecode provided in query so there will only be one Offence in the response
        return refDataOffencesList.getOffences().get(0);
    }
}
