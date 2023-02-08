package fr.openent.zimbra.service;

import fr.openent.zimbra.core.enums.TaskType;
import fr.openent.zimbra.model.task.Task;
import fr.wseduc.webutils.Either;
import fr.openent.zimbra.model.task.Action;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;

public interface QueueService {
    Future<Void> putRequestInQueue(UserInfos user, JsonObject info);

    Future<Void> createActionInQueue(UserInfos user, String type);

    void createActionInQueue(UserInfos user, String type, Handler<Either<String, JsonObject>> handler);

    void createTask(Integer actionId, Boolean approved, Handler<Either<String, JsonObject>> handler);

    /**
     * Retrieve list of Tasks in progress
     * @param taskType
     * @return
     */
    Future<List<Task>> getTasksInProgress(TaskType taskType);

    /**
     * Retrieve Task from taskId
     * @param taskId
     * @return
     */
    Future<Task> getTask(long taskId);

    /**
     * Create a Task with specific action and
     * @param action
     * @param taskType
     * @return
     */
    Future<Task> createTask(Action action, TaskType taskType);
}
