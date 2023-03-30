package fr.openent.zimbra.tasks.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.helper.FutureHelper;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.ICalTask;
import fr.openent.zimbra.tasks.service.DbActionService;
import fr.openent.zimbra.tasks.service.DbTaskService;
import fr.openent.zimbra.tasks.service.QueueService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.NotImplementedException;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.ArrayList;
import java.util.List;

public class ICalQueueServiceImpl extends QueueService<ICalTask> {
    private final String icalTable = "ical_request_tasks";

    public ICalQueueServiceImpl(String schema, DbTaskService<ICalTask> dbTaskService, DbActionService dbActionService) {
        super(schema, dbTaskService, dbActionService);
        this.taskTable = this.schema + "." + icalTable;
        this.actionType = ActionType.ICAL;
    }

    @Override
    public Future<List<ICalTask>> insertTasksInQueue(List<ICalTask> tasks) {
        // todo: insert Task in the top of the queue of ICalWorker
        throw new NotImplementedException();
    }

    @Override
    protected List<ICalTask> createTasksFromData(Action<ICalTask> action, JsonArray taskData) {
        return IModelHelper.toList(taskData, ICalTask.class);
    }

    @Override
    public Future<List<ICalTask>> createTasks(Action<ICalTask> action, List<ICalTask> tasks) {
        Promise<List<ICalTask>> promise = Promise.promise();

         this.createICalTasks(action.getId(), tasks)
                .onSuccess(promise::complete)
                .onFailure(error -> {
                    String errMessage = String.format("[Zimbra@%s::putRequestInQueue]:  " +
                                    "an error has occurred while putting request in queue: %s",
                            this.getClass().getSimpleName(), error.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ZIMBRA_ERROR_QUEUE.method());
                });

        return promise.future();
    }

    private Future<List<ICalTask>> createICalTasks(long actionId, List<ICalTask> tasks) {
        Promise<List<ICalTask>> promise = Promise.promise();

        List<Future<ICalTask>> icalTaskFutures = new ArrayList<>();
        tasks.forEach(task -> icalTaskFutures.add(this.createICalTask(actionId, task)));

        FutureHelper.all(icalTaskFutures)
                .onSuccess(result -> promise.complete(result.list()))
                .onFailure(error -> {
                    String errMessage = String.format("[Zimbra@%s::createICalTask]:  " +
                                    "an error has occurred while creating ical task for queue action: %s",
                            this.getClass().getSimpleName(), error.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ZIMBRA_ERROR_QUEUE.method());
                });

        return promise.future();
    }

    private Future<ICalTask> createICalTask(long actionId, ICalTask task) {
        Promise<ICalTask> promise = Promise.promise();
        String query = "INSERT INTO " + this.taskTable + " (action_id, status, name, body) VALUES (?, ?, ?, ?)";

        JsonArray values = new JsonArray();
        values.add(actionId)
                .add(Field.PENDING)
                .add(task.getName())
                .add(task.getBody());

        Sql.getInstance().prepared(query, values, SqlResult.validUniqueResultHandler(result -> {
            if (result.isRight()) {
                try {
                    promise.complete(new ICalTask(result.right().getValue()));
                } catch (Exception e) {
                    String errMessage = String.format("[Zimbra@%s::createICalTask]:  " +
                                    "an error has occurred while creating task model: %s",
                            this.getClass().getSimpleName(), e.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_TASK_MODEL.method());
                }
            } else {
                String errMessage = String.format("[Zimbra@%s::createICalTask]:  " +
                                "an error has occurred while creating ical task for queue action: %s",
                        this.getClass().getSimpleName(), result.left().getValue());
                log.error(errMessage);
                promise.fail(ErrorEnum.ZIMBRA_ERROR_QUEUE.method());
            }
        }));

        return promise.future();
    }

    @Override
    protected ICalTask createTaskFromData(JsonObject taskData, Action<ICalTask> action) throws Exception {
        return new ICalTask(action, taskData);
    }

}
