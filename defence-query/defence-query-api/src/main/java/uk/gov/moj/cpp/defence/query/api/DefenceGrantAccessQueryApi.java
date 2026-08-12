package uk.gov.moj.cpp.defence.query.api;

import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.query.view.DefenceGrantAccessQueryView;

import jakarta.inject.Inject;

@ServiceComponent(Component.QUERY_API)
public class DefenceGrantAccessQueryApi {

    @Inject
    private Requester requester;

    @Inject
    private DefenceGrantAccessQueryView defenceGrantAccessQueryView;

    @Handles("defence.query.grantees-organisation")
    public JsonEnvelope getDefenceClientGrantees(final JsonEnvelope envelope) {
        return defenceGrantAccessQueryView.getDefenceClientGrantees(envelope);
    }
    @Handles("defence.query.defence-access-for-case")
    public JsonEnvelope getDefenceAccessForCase(final JsonEnvelope envelope) {
        return defenceGrantAccessQueryView.getCaseGrantee(envelope);
    }
}
