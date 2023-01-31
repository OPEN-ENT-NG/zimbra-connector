package fr.openent.zimbra.model.requestQueue;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.impl.QueueServiceImpl;
import io.vertx.core.json.JsonObject;

public abstract class RequestQueue {
    public static final String ICAL = "ICal";
    public static final String RECALL = "Recall";
    protected static QueueServiceImpl queueService;

    public static void init(ServiceManager serviceManager) {
        RequestQueue.queueService = serviceManager.getQueueService();
    }

    public static RequestQueue requestObjectFactory(JsonObject requestObject) {
        if (requestObject == null) {
            return null;
        }

        switch (requestObject.getString(Field.TYPE, "")) {
            case ICAL:
                return new ICalRequest(requestObject);

            case RECALL:
                return null;

            default:
                return null;

        }
    }

    public abstract void addTaskToAction();

}
