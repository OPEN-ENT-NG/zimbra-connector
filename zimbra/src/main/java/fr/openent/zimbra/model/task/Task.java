package fr.openent.zimbra.model.task;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.QueueService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public abstract class Task {
    public static final String ICAL = "ical";
    public static final String RECALL = "recall";
    protected static QueueService queueService;

    public static void init(ServiceManager serviceManager) {
        Task.queueService = serviceManager.getQueueService();
    }

    public static Task requestObjectFactory(JsonObject requestObject) {
        if (requestObject == null) {
            return null;
        }

        switch (requestObject.getString(Field.TYPE, "")) {
            case ICAL:
                return new TaskICal(requestObject);

            case RECALL:
                return null;

            default:
                throw new IllegalStateException("Unknown task");

        }
    }

    /**
     * Cretes action + task(s) in database for que queue
     * @return {@link Future<Void>}
     */
    public abstract Future<Void> addTaskToAction();

}
