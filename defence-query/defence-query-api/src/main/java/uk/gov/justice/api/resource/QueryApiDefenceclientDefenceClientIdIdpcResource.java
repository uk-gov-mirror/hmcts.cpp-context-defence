
package uk.gov.justice.api.resource;

import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("defenceclient/{defenceClientId}/idpc")
public interface QueryApiDefenceclientDefenceClientIdIdpcResource {

    @GET
    @Produces({
            "application/vnd.defence.query.defence-client-idpc+json"
    })
    Response getDefenceclientByDefenceClientIdIdpc(
            @PathParam("defenceClientId") String defenceClientId,
            @HeaderParam(USER_ID) String userId);
}
