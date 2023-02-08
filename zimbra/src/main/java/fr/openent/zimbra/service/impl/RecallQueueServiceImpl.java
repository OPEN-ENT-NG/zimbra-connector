package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.FutureHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.message.RecallMail;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.model.task.RecallTaskInfo;
import fr.openent.zimbra.service.QueueService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.NotImplementedException;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecallQueueServiceImpl extends QueueService<RecallTask, RecallTaskInfo> {
    private final String recallTable = "recall_recipient_tasks";

    public RecallQueueServiceImpl(String schema) {
        super(schema);
        this.taskTable = this.schema + "." + recallTable;
        this.actionType = ActionType.RECALL;
    }

    @Override
    public Future<List<RecallTask>> insertTasksInQueue(List<RecallTask> tasks) {
        // todo: insert Task in the top of the queue of RecallWorker
        throw new NotImplementedException();
    }

    @Override
    protected Future<List<RecallTask>> createTasks(Action action, RecallTaskInfo taskInfo) {
        Promise<List<RecallTask>> promise = Promise.promise();

        int recallMailId = taskInfo.getRecallMailId();
        // todo: retrieve list of recipient from Zimbra
        List<JsonObject> fakeRecipients = new ArrayList<>();

        List<Future<RecallTask>> futures = new ArrayList<>();
        for (JsonObject fakeRecipient : fakeRecipients) {
            futures.add(this.createTask(action, fakeRecipient));
        }

        FutureHelper.all(futures)
                .onSuccess(res -> {
                    action.addTasks(res.list());
                    promise.complete(res.list());
                })
                .onFailure(promise::fail);

        return promise.future();
    }

    private Future<RecallTask> createTask(Action action, JsonObject task) {
        Promise<RecallTask> promise = Promise.promise();

        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ")
                .append(this.taskTable)
                .append(" (" + Field.ACTION_ID + "," + Field.STATUS + "," + Field.RECALL_MAIL_ID + "," + Field.RECEIVER_ID + ") ")
                .append("VALUES (?, ?, ?, ?)")
                .append("RETURNING *"); // todo: return only necessary fields

        JsonArray values = new JsonArray();
        values.add(action.getId()).add(TaskStatus.UNKNOWN).add(task.getString("recallMailId")).add(task.getString("receiverId"));

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(handler -> {
            if (handler.isRight()) {
                long id = handler.right().getValue().getLong(Field.ID);
                int retry = handler.right().getValue().getInteger(Field.RETRY);
                String taskStatus = handler.right().getValue().getString(Field.STATUS);
                UUID receiverId = UUID.fromString(handler.right().getValue().getString("receiverId"));

                RecallTask recallTask = new RecallTask(id, TaskStatus.fromString(taskStatus), action, null, receiverId, retry);
                promise.complete(recallTask);
            } else {
                String errMessage = String.format("[Zimbra@%s::createTask]:  " +
                                "an error has occurred while creating task: %s",
                        this.getClass().getSimpleName(), handler.left().getValue());
                log.error(errMessage);
                promise.fail("zimbra.error.queue.task");
            }
        }));

        return promise.future();
    }
}
