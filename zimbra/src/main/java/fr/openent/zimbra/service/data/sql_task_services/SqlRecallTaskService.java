package fr.openent.zimbra.service.data.sql_task_services;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.PromiseHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.service.DbTaskService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.NotImplementedException;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

public class SqlRecallTaskService extends DbTaskService<RecallTask> {
    private final String recallMailTable = schema + "." + "recall_mails";
    public SqlRecallTaskService(String schema) {
        super(schema);
    }

    @Override
    public Future<JsonArray> retrieveTasksDataFromDB(TaskStatus status) {
        Promise<JsonArray> promise = Promise.promise();

        String query = "SELECT * " +
                this.taskTable + " as recall_tasks " +
                "JOIN " + this.actionTable + " as actions" + " on actions.id = recall_tasks.action_id" +
                "JOIN " + this.recallMailTable + " as recall_mails" + " on actions.id = recall_mails.action_id " +
                "WHERE recall_tasks.status = ?;";
        JsonArray params = new JsonArray().add(status.method());

        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(PromiseHelper.handlerJsonArray(promise)));

        return promise.future();
    }

    @Override
    public Future<JsonObject> createTask(Action<RecallTask> action, RecallTask task) {
        Promise<JsonObject> promise = Promise.promise();

        String query = "INSERT INTO " +
                this.taskTable +
                " (" + Field.ACTION_ID + "," + Field.STATUS + "," + Field.RECALL_MAIL_ID + "," + Field.RECEIVER_ID + ") " +
                "VALUES (?, ?, ?, ?)" +
                "RETURNING *";

        JsonArray values = new JsonArray();
        values.add(action.getId()).add(task.getStatus()).add(task.getRecallMessage().getRecallId()).add(task.getReceiverId());
        Sql.getInstance().prepared(query, values, SqlResult.validUniqueResultHandler(PromiseHelper.handlerJsonObject(promise)));

        return promise.future();
    }

    @Override
    public Future<Void> editTaskStatus(RecallTask task, TaskStatus status) {
        throw new NotImplementedException();
    }
}
