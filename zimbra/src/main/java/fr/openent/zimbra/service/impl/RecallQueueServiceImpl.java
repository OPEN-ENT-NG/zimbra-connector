package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.FutureHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.message.RecallMail;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.service.QueueService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecallQueueServiceImpl extends QueueService<RecallTask> {
    public RecallQueueServiceImpl(String schema) {
        super(schema);
        this.dbTaskService = serviceManager.getSqlRecallTaskService();
        this.taskTable = this.schema + "." + "recall_recipient_tasks";
        this.actionType = ActionType.RECALL;
    }

    @Override
    public Future<List<RecallTask>> insertTasksInQueue(List<RecallTask> tasks) {
        // todo: insert Task in the top of the queue of RecallWorker
        throw new NotImplementedException();
    }

    @Override
    public Future<RecallTask> createTask(Action<RecallTask> action, RecallTask task) {
        Promise<RecallTask> promise = Promise.promise();

        dbTaskService.createTask(action, task)
                .onSuccess(taskData -> {
                    long id = taskData.getLong(Field.ID);
                    int retry = taskData.getInteger(Field.RETRY);
                    String taskStatus = taskData.getString(Field.STATUS);
                    UUID receiverId = UUID.fromString(taskData.getString(Field.RECEIVERID));

                    RecallTask recallTask = new RecallTask(id, TaskStatus.fromString(taskStatus), null, null, receiverId, retry);
                    promise.complete(recallTask);
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::createTask]:  " +
                                    "an error has occurred while creating task: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail("zimbra.error.queue.task");
                });

        return promise.future();
    }

    @Override
    protected List<RecallTask> createTasksFromData(JsonArray taskData) throws Exception {
        return null;
    }

    @Override
    public Future<List<RecallTask>> createTasks(Action<RecallTask> action, List<RecallTask> tasks) {
        Promise<List<RecallTask>> promise = Promise.promise();

        List<Future<RecallTask>> futures = new ArrayList<>();
        for (RecallTask task : tasks) {
            futures.add(this.createTask(action, task));
        }
        FutureHelper.all(futures)
                .onSuccess(res -> promise.complete(res.list()))
                .onFailure(promise::fail);

        return promise.future();
    }

    protected RecallTask createTaskFromData(JsonObject taskData, Action<RecallTask> action) throws Exception {
       return new RecallTask(taskData, action, new RecallMail(taskData.getInteger(Field.RECALL_MAIL_ID), taskData.getString(Field.MESSAGE_ID)));

    }

}
