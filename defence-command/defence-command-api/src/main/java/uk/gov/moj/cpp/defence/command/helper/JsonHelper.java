package uk.gov.moj.cpp.defence.command.helper;

import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;

import java.util.Map;

import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

public class JsonHelper {

    private JsonHelper() {

    }

    public static JsonObject removeProperty(final JsonObject origin, final String key) {
        final JsonObjectBuilder builder = createObjectBuilder();
        for (final Map.Entry<String, JsonValue> entry : origin.entrySet()) {
            if (!entry.getKey().equals(key)) {
                builder.add(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }
}
