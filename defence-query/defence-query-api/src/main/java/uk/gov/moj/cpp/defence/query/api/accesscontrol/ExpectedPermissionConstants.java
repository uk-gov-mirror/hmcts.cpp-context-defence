package uk.gov.moj.cpp.defence.query.api.accesscontrol;

import static java.lang.String.format;
import static uk.gov.moj.cpp.defence.common.util.DefencePermission.VIEW_DEFENDANT_PERMISSION;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.accesscontrol.drools.Action;
import uk.gov.moj.cpp.accesscontrol.drools.ExpectedPermission;

import jakarta.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExpectedPermissionConstants {

    private static final String DEFENDENT_ID_KEY = "defendantId";
    private static final String DEFENDENT_CLIENT_ID_KEY = "defendantClientId";
    private static final String DEFENCE_CLIENT_ID = "defenceClientId";
    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    private ExpectedPermissionConstants() {
    }

    public static String[] defenceClientViewAction(final Action action) throws JsonProcessingException {
        final ExpectedPermission expectedPermission1 = ExpectedPermission.builder()
                .withAction(VIEW_DEFENDANT_PERMISSION.getActionType())
                .withObject(VIEW_DEFENDANT_PERMISSION.getObjectType())
                .build();

        final ExpectedPermission expectedPermission2 = ExpectedPermission.builder()
                .withAction(VIEW_DEFENDANT_PERMISSION.getActionType())
                .withObject(VIEW_DEFENDANT_PERMISSION.getObjectType())
                .withTarget(defenceClientIdFrom(action))
                .withSource(action.metadata().userId().orElse(null))
                .build();

        return new String[]{objectMapper.writeValueAsString(expectedPermission1), objectMapper.writeValueAsString(expectedPermission2)};

    }


    private static String defenceClientIdFrom(final Action action) {

        final JsonObject jsonObject = action.envelope().payloadAsJsonObject();

        String key;
       if (jsonObject.containsKey(DEFENDENT_ID_KEY)) {
            key = jsonObject.getString(DEFENDENT_ID_KEY);
        } else if (jsonObject.containsKey(DEFENDENT_CLIENT_ID_KEY)) {
            key = jsonObject.getString(DEFENDENT_CLIENT_ID_KEY);
        } else if (jsonObject.containsKey(DEFENCE_CLIENT_ID)) {
            key = jsonObject.getString(DEFENCE_CLIENT_ID);
        }
        else {
            throw new IllegalArgumentException(format("Action should contain %s or %s", VIEW_DEFENDANT_PERMISSION.getObjectType(), DEFENDENT_ID_KEY));
        }

        return key;

    }
}
