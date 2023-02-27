package fr.openent.zimbra.helper;

import fr.openent.zimbra.core.constants.Field;
import io.vertx.core.json.JsonObject;

public class EntUserHelper {
    public static boolean JSONContainsADMLData(JsonObject ADMLData) {
        return ADMLData.containsKey(Field.ID) && ADMLData.containsKey(Field.STRUCTURES) && ADMLData.containsKey(Field.USERNAME);
    }
}
