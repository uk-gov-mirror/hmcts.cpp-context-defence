package uk.gov.moj.cpp.defence.command.api;

import static uk.gov.justice.services.core.annotation.Component.COMMAND_API;
import static uk.gov.justice.services.core.enveloper.Enveloper.envelop;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.command.api.service.OrganisationService;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

@ServiceComponent(COMMAND_API)
public class DefencePleaCommandApi {

    @Inject
    private Sender sender;

    @Inject
    private Requester requester;

    @Inject
    private OrganisationService organisationService;

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    private boolean canCreatePleaAllocationForDefendants(final List<UUID> associatedDefendantIds, final UUID defendantId) {
        return associatedDefendantIds.contains(defendantId);
    }


    private void validateAndSend(final JsonEnvelope envelope, String commandName) {
        final List<UUID> associatedDefendantIds = organisationService.getAssociatedDefendants(envelope, requester);
        final UUID defendantId = UUID.fromString(envelope.payloadAsJsonObject()
                .getString("defendantId"));
        final boolean canCreateCotr = canCreatePleaAllocationForDefendants(associatedDefendantIds, defendantId);
        if (canCreateCotr) {
            sendEnvelopeWithName(envelope, commandName);
        } else {
            throw new ForbiddenRequestException("User is not associated to defendant to create/change plea");
        }
    }

    @Handles("defence.add-offence-pleas")
    public void createPleaForDefendantsOffence(final JsonEnvelope envelope) {
        validateAndSend(envelope, "defence.command.add-offence-pleas");
    }

    @Handles("defence.update-offence-pleas")
    public void updatePleaForDefendantsOffence(final JsonEnvelope envelope) {
        validateAndSend(envelope, "defence.command.update-offence-pleas");
    }

    private void sendEnvelopeWithName(final JsonEnvelope envelope, final String name) {
        sender.send(envelop(createObjectBuilder().add("pleasAllocation", envelope.payloadAsJsonObject()).build())
                .withName(name)
                .withMetadataFrom(envelope));
    }
}
