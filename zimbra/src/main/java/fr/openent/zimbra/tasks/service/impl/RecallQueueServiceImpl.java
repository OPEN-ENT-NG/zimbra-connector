package fr.openent.zimbra.tasks.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.FutureHelper;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.message.Message;
import fr.openent.zimbra.model.message.RecallMail;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.tasks.service.DbActionService;
import fr.openent.zimbra.tasks.service.DbTaskService;
import fr.openent.zimbra.tasks.service.QueueService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecallQueueServiceImpl extends QueueService<RecallTask> {
    public RecallQueueServiceImpl(String schema, DbTaskService<RecallTask> dbTaskService, DbActionService dbActionService) {
        super(schema, dbTaskService, dbActionService);
        this.dbTaskService = dbTaskService;
        this.actionType = ActionType.RECALL;
    }

    @Override
    public Future<List<RecallTask>> insertTasksInQueue(List<RecallTask> tasks) {
        // todo: insert Task in the top of the queue of RecallWorker
        throw new NotImplementedException();
    }

    @Override
    protected List<RecallTask> createTasksFromData(Action<RecallTask> action, JsonArray taskData) throws Exception {
        return IModelHelper.toList(taskData, data -> new RecallTask(
                data.getInteger(Field.ID),
                TaskStatus.PENDING,
                action,
                null,
                UUID.fromString(data.getString(Field.RECEIVER_ID)),
                data.getInteger(Field.RETRY)));
    }

    @Override
    public Future<List<RecallTask>> createTasks(Action<RecallTask> action, List<RecallTask> tasks) {
        Promise<List<RecallTask>> promise = Promise.promise();
        if (tasks.isEmpty())
            return Future.failedFuture(ErrorEnum.NO_MAIL_TO_RECALL.method());

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
        Message message = Message.fromZimbra(new JsonObject().put(Field.MESSAGE_ID, taskData.getString(Field.MESSAGE_ID)));
        return new RecallTask(      taskData,
                                    action,
                                    new RecallMail(taskData.getInteger(Field.RECALL_MAIL_ID), message)
        );
    }

}
