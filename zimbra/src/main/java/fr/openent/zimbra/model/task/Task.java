package fr.openent.zimbra.model.task;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.impl.QueueServiceImpl;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public abstract class Task {
    public static final String ICAL = "ical";
    public static final String RECALL = "recall";
    protected int id;
    protected TaskStatus status;
    //todo rajouter action
    protected static QueueServiceImpl queueService;

    public static void init(ServiceManager serviceManager) {
        Task.queueService = serviceManager.getQueueService();
    }

    public static Task requestObjectFactory(UserInfos user, JsonObject requestObject) {
        if (requestObject == null) {
            return null;
        }

        switch (requestObject.getString(Field.TYPE, "")) {
            case ICAL:
                return new TaskICal(user, requestObject);

            case RECALL:
                return null;

            default:
                return null;

        }
    }

    public abstract void addTaskToAction();

}
