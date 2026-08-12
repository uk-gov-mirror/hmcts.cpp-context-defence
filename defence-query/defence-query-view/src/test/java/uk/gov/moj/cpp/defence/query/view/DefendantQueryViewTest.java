package uk.gov.moj.cpp.defence.query.view;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.defence.persistence.DefenceAssociationRepository;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociation;
import uk.gov.moj.cpp.defence.persistence.entity.DefenceAssociationDefendant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefendantQueryViewTest {

    private static final String LAA_CONTRACT_NUMBER = "l1";

    @Mock
    private DefenceAssociationRepository defenceAssociationRepository;

    @InjectMocks
    private DefendantQueryView defendantQueryView;

    @Test
    public void shouldReturnDefenceAssociation() {
        UUID defendantId1 = UUID.randomUUID();
        UUID defendantId2 = UUID.randomUUID();
        when(defenceAssociationRepository.findByLAAContractNumber(ImmutableList.of(LAA_CONTRACT_NUMBER))).thenReturn(stubDefenceAssociationWithDefendantIds(defendantId1, defendantId2));

        final JsonEnvelope defenceAssociationResponse = defendantQueryView.getDefendantsByLAAContractNumber(stubbedQueryObject());

        final JsonArray defendantsArray = defenceAssociationResponse.payloadAsJsonObject().getJsonArray("defendants");
        assertThat(defendantsArray.size(), is(2));
        assertThat(defendantsArray.getJsonObject(0).getString("id"), is(defendantId1.toString()));
        assertThat(defendantsArray.getJsonObject(0).getString("laaContractNumber"), is(LAA_CONTRACT_NUMBER));

        assertThat(defendantsArray.getJsonObject(1).getString("id"), is(defendantId2.toString()));
        assertThat(defendantsArray.getJsonObject(1).getString("laaContractNumber"), is(LAA_CONTRACT_NUMBER));
    }

    @Test
    public void shouldReturnEmptyArrayWhenNoDefendantsFoundForLAA() {
        when(defenceAssociationRepository.findByLAAContractNumber(ImmutableList.of(LAA_CONTRACT_NUMBER))).thenReturn(Collections.emptyList());

        final JsonEnvelope defenceAssociationResponse = defendantQueryView.getDefendantsByLAAContractNumber(stubbedQueryObject());

        final JsonArray defendantsArray = defenceAssociationResponse.payloadAsJsonObject().getJsonArray("defendants");
        assertThat(defendantsArray.size(), is(0));
    }

    private List<DefenceAssociation> stubDefenceAssociationWithDefendantIds(final UUID... defendantIds) {

        List<DefenceAssociation> defenceAssociations = new ArrayList<>();
        for (UUID defendantId : defendantIds) {
            DefenceAssociationDefendant defenceAssociationDefendant = new DefenceAssociationDefendant();
            defenceAssociationDefendant.setDefendantId(defendantId);

            DefenceAssociation defenceAssociation = new DefenceAssociation();
            defenceAssociation.setId(UUID.randomUUID());
            defenceAssociation.setLaaContractNumber(LAA_CONTRACT_NUMBER);
            defenceAssociation.setDefenceAssociationDefendant(defenceAssociationDefendant);

            defenceAssociations.add(defenceAssociation);
        }

        return defenceAssociations;
    }

    private JsonEnvelope stubbedQueryObject() {
        return JsonEnvelope.envelopeFrom(
                stubbedMetadataBuilder(),
                stubbedQueryObjectPayload());
    }

    private JsonObject stubbedQueryObjectPayload() {
        return createObjectBuilder()
                .add("laaContractNumbers", LAA_CONTRACT_NUMBER)
                .build();
    }

    private MetadataBuilder stubbedMetadataBuilder() {
        return JsonEnvelope.metadataBuilder()
                .withId(randomUUID())
                .withName("defence.query.associated-organisation")
                .withCausation(randomUUID())
                .withClientCorrelationId(randomUUID().toString())
                .withStreamId(randomUUID())
                .withUserId(randomUUID().toString());
    }
}
