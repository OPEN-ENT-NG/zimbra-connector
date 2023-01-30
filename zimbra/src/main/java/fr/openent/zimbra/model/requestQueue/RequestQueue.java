package fr.openent.zimbra.model.requestQueue;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.IModel;
import io.vertx.core.json.JsonObject;

public abstract class RequestQueue {
    public static final String ICAL = "ICal";
    public static final String RECALL = "Recall";

    public static RequestQueue requestObjectFactory(JsonObject requestObject) {
        if (requestObject == null) {
            return null;
        }

        switch (requestObject.getString(Field.TYPE, "")) {
            case ICAL: {
                return new ICalRequest(requestObject.getJsonObject(Field.DATA, new JsonObject()));
            }
            case RECALL: {
                return null;
            }
            default: {
                return null;
            }
        }
    }

    public abstract void addTaskToAction();

}
