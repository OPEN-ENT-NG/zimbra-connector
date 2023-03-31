package fr.openent.zimbra.helper;


import fr.openent.zimbra.core.constants.Field;
import io.vertx.core.json.JsonObject;

public class StructureHelper {
    public static boolean JSONContainsStructAndADMLData(JsonObject data) {
        return data.containsKey(Field.ADMLS) && data.containsKey(Field.STRUCTURE);
    }
}
