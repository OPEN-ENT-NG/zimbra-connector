package fr.openent.zimbra.model.task;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.service.impl.QueueServiceImpl;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public abstract class Task {
    protected static QueueServiceImpl queueService;

    protected long id;
    protected TaskStatus status;
    Action action;

    public Task(long id, TaskStatus status, Action action) {
        this.id = id;
        this.status = status;
        this.action = action;
    }

    public Task (JsonObject dbData, Action action) throws Exception {
        try {
            this.id = dbData.getInteger(Field.TASK_ID);
            this.status = TaskStatus.fromString(dbData.getString(Field.TASK_STATUS));
            this.action = action;

        } catch (Exception e) {
            throw new Exception(String.format("[Zimbra@%s::Task] JSON does not match Task model", Task.class));
        }
    }

    public static void init(ServiceManager serviceManager) {
        Task.queueService = serviceManager.getQueueService();
    }

    public static Task requestObjectFactory(UserInfos user, JsonObject requestObject, Action action) throws Exception {
        if (requestObject == null) {
            return null;
        }
        switch (action.getActionType()) {
            case ICAL:
                return new TaskICal(user, requestObject, action);
            case RECALL:
                return null;
            default:
                return null;
        }
    }

    public abstract void addTaskToAction();

}
