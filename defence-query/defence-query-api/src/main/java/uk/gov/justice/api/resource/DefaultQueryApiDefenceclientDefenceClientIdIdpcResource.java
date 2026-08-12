
package uk.gov.justice.api.resource;

import static java.util.UUID.randomUUID;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static jakarta.ws.rs.core.Response.Status.FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.fromStatusCode;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import uk.gov.justice.services.common.exception.ForbiddenRequestException;
import uk.gov.justice.services.core.annotation.Adapter;
import uk.gov.justice.services.core.annotation.Component;
import uk.gov.justice.services.core.interceptor.InterceptorChainProcessor;
import uk.gov.justice.services.core.interceptor.InterceptorContext;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.material.client.MaterialClient;
import uk.gov.moj.cpp.systemusers.ServiceContextSystemUserProvider;

import java.util.Optional;
import java.util.UUID;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
@Adapter(Component.QUERY_API)
public class DefaultQueryApiDefenceclientDefenceClientIdIdpcResource implements QueryApiDefenceclientDefenceClientIdIdpcResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultQueryApiDefenceclientDefenceClientIdIdpcResource.class);

    @SuppressWarnings("squid:S1075")
    private static final String IDPC_ERROR_PATH = "/defence/download-error";

    @SuppressWarnings("squid:S1075")
    private static final String IDPC_FINDACASE_PATH = "/defence/";

    private static final String JSON_MIME_TYPE = "application/json";
    private static final String DEFENCE_CLIENT_ID = "defenceClientId";
    private static final String USER_ID = "userId";
    private static final String ORGANISATION_ID = "organisationId";
    private static final String IDPC_DETAILS_ID = "idpcDetailsId";
    private static final String ACCESS_TIMESTAMP = "accessTimestamp";
    private static final String MATERIAL_ID = "materialId";

    @Inject
    MaterialClient materialClient;

    @Inject
    InterceptorChainProcessor interceptorChainProcessor;

    @Inject
    private ServiceContextSystemUserProvider serviceContextSystemUserProvider;

    @Override
    @SuppressWarnings("squid:S1181")
    public Response getDefenceclientByDefenceClientIdIdpc(final String defenceClientId, final String userId) {

        try {
            final UUID systemUserUUID = serviceContextSystemUserProvider.getContextSystemUserId().orElse(null);
            if (systemUserUUID == null) {
                return createRedirectToErrorPageResponse();
            }
            final String systemUserId = systemUserUUID.toString();

            final JsonEnvelope query = envelopeFrom(
                    metadataBuilder()
                            .withId(randomUUID())
                            .withName("defence.query.defence-client-idpc")
                            .withUserId(userId)
                            .build(),
                    createObjectBuilder()
                            .add(DEFENCE_CLIENT_ID, defenceClientId)
                            .add(USER_ID, userId)
                            .build()
            );

            final InterceptorContext interceptorContext = InterceptorContext.interceptorContextWithInput(query);
            //send the request to the interceptor chain to be handled by a handles method
            final Optional<JsonEnvelope> envelopeResponse = interceptorChainProcessor.process(interceptorContext);

            if (!envelopeResponse.isPresent()) {
                return createRedirectToErrorPageResponse();
            }

            final JsonObject responsePayload = envelopeResponse.get().payloadAsJsonObject();
            final String materialId = responsePayload.getString(MATERIAL_ID);

            //Invoke the Material context to retrieve the file
            final Response materialResponse = materialClient.getMaterialAsPdfAttachment(materialId, systemUserId);
            final Response.Status materialResponseStatus = fromStatusCode(materialResponse.getStatus());
            if (OK.equals(materialResponseStatus)) {
                //generate the response for this service
                final Response response = processedMaterialResponse(materialResponse);

                //issue command to record access to idpc
                recordIDPCAccess(query, responsePayload);

                return response;
            } else {
                return createRedirectToErrorPageResponse();
            }
        } catch (ForbiddenRequestException e) {
            LOGGER.error("Error retrieving IDPC due to failed authorization {}", e);
            return createRedirectToFindACasePageResponse();
        } catch (Throwable e) { // Need to redirect to error page for all possible errors other than unauthorized.
            LOGGER.error("Error retrieving IDPC due to a technical error {}", e);
            return createRedirectToErrorPageResponse();
        }
    }

    private void recordIDPCAccess(JsonEnvelope request, JsonObject ipdcAccess) {
        final JsonEnvelope query = envelopeFrom(
                JsonEnvelope.metadataFrom(request.metadata())
                        .withId(randomUUID())
                        .withName("defence.query.record-idpc-access")
                        .withUserId(ipdcAccess.getString(USER_ID))
                        .withCausation(request.metadata().id())
                        .build(),
                createObjectBuilder()
                        .add(DEFENCE_CLIENT_ID, ipdcAccess.getString(DEFENCE_CLIENT_ID))
                        .add(USER_ID, ipdcAccess.getString(USER_ID))
                        .add(ORGANISATION_ID, ipdcAccess.getString(ORGANISATION_ID))
                        .add(MATERIAL_ID, ipdcAccess.getString(MATERIAL_ID))
                        .add(IDPC_DETAILS_ID, ipdcAccess.getString(IDPC_DETAILS_ID))
                        .add(ACCESS_TIMESTAMP, ipdcAccess.getString(ACCESS_TIMESTAMP))
                        .build()
        );
        final InterceptorContext interceptorContext = InterceptorContext.interceptorContextWithInput(query);
        interceptorChainProcessor.process(interceptorContext);
    }

    private Response createRedirectToErrorPageResponse() {
        return Response.status(FOUND)
                .header(LOCATION, IDPC_ERROR_PATH)
                .entity("Please try again in a few minutes. Contact the help desk if this doesn’t work.")
                .build();
    }

    private Response createRedirectToFindACasePageResponse() {
        return Response.status(FOUND)
                .header(LOCATION, IDPC_FINDACASE_PATH)
                .entity("Unable to access IDPC due to failed authorization.")
                .build();
    }

    private Response processedMaterialResponse(final Response materialResponse) {

        final String url = materialResponse.readEntity(String.class);
        final JsonObject jsonObject = createObjectBuilder()
                .add("url", url)
                .build();

        return Response
                .status(OK)
                .entity(jsonObject)
                .header(CONTENT_TYPE, JSON_MIME_TYPE)
                .build();
    }
}
