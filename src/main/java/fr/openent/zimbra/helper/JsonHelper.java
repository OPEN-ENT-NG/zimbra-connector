package fr.openent.zimbra.helper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class JsonHelper {

    public static List<String> getStringList(JsonArray jsonArray) throws IllegalArgumentException {
        List<String> finalList = new ArrayList<>();
        for(Object o : jsonArray) {
            if(!(o instanceof String)) {
                throw new IllegalArgumentException("JsonArray is not a String list");
            }
            finalList.add((String)o);
        }
        return finalList;
    }

    public static List<String> extractValueFromJsonObjects(JsonArray jsonArray, String key) throws IllegalArgumentException {
        List<String> finalList = new ArrayList<>();
        for(Object o : jsonArray) {
            if(!(o instanceof JsonObject)) {
                throw new IllegalArgumentException("JsonArray is not a JsonObject list");
            }
            JsonObject jo = (JsonObject)o;
            Object res = jo.getValue(key);
            if(res == null) {
                throw new IllegalArgumentException(key + " value of JsonObject is not a String or key does not exists");
            }
            finalList.add(res.toString());
        }
        return finalList;
    }
}
