package uk.gov.moj.cpp.defence.query.view;

import static com.google.common.collect.Maps.immutableEntry;
import static java.util.stream.Collectors.toList;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

@ServiceComponent(Component.QUERY_VIEW)
public class DefendantQueryView {

    @Inject
    private DefenceAssociationRepository defenceAssociationRepository;

    @Handles("defence.query.defendants-by-laacontractnumber")
    public JsonEnvelope getDefendantsByLAAContractNumber(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        final String laaContractNumberAsString = payload.getString("laaContractNumbers");
        final List<String> laaContractNumbers = Stream.of(laaContractNumberAsString.split(",")).collect(toList());
        final List<DefenceAssociation> defenceAssociations = defenceAssociationRepository.findByLAAContractNumber(laaContractNumbers);
        final JsonObject responsePayload = createObjectBuilder()
                .add("defendants", convertToJsonArray(defenceAssociations))
                .build();

        return JsonEnvelope.envelopeFrom(
                envelope.metadata(),
                responsePayload);
    }

    private JsonArray convertToJsonArray(final List<DefenceAssociation> defenceAssociations) {
        final List<Map.Entry<String, String>> defendantIdList = defenceAssociations.stream().filter(Objects::nonNull)
                .map(entry ->
                        immutableEntry(
                                entry.getDefenceAssociationDefendant().getDefendantId().toString(),
                                entry.getLaaContractNumber())
                ).collect(toList());

        final JsonArrayBuilder jsonArrayBuilder = createArrayBuilder();
        defendantIdList.forEach(entry -> jsonArrayBuilder
                .add(createObjectBuilder().add("id", entry.getKey()).add("laaContractNumber", entry.getValue())));

        return jsonArrayBuilder.build();
    }

}
