package fr.openent.zimbra.tasks.helpers;

import fr.openent.zimbra.core.constants.Field;
import io.vertx.core.json.JsonObject;

public class CalendarEventBusHelper {
    public static JsonObject generateSuccessNotification(JsonObject result) {
        return new JsonObject()
                .put(Field.STATUS, Field.OK)
                .put(Field.RESULT, result);
    }

    public static JsonObject generateFailureNotification(String error) {
        return new JsonObject()
                .put(Field.STATUS, Field.KO)
                .put(Field.ERROR, error);
    }

    public static JsonObject createSucceedAnswerAndSetAction(String action, JsonObject result) {
        return generateSuccessNotification(result)
                .put(Field.ACTION, action);
    }
}
