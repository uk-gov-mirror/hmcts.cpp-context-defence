package uk.gov.moj.defence.util;

import uk.gov.justice.services.common.http.HeaderConstants;

import jakarta.ws.rs.core.MultivaluedMap;

import org.jboss.resteasy.specimpl.MultivaluedMapImpl;

public class HttpHeaders {

    public static MultivaluedMap<String,Object> createHttpHeaders(final String userId) {
        MultivaluedMap<String,Object> headers  = new MultivaluedMapImpl<>();
        headers.add(HeaderConstants.USER_ID, userId);
        return headers;
    }
}
