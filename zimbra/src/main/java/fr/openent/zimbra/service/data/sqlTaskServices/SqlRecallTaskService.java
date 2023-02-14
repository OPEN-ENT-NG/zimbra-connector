package fr.openent.zimbra.service.data.sqlTaskServices;

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
        StringBuilder query = new StringBuilder();

        query.append("SELECT * ")
                .append(this.taskTable + " as recall_tasks ")
                .append("JOIN " + this.actionTable + " as actions" + " on actions.id = recall_tasks.action_id")
                .append("JOIN " + this.recallMailTable + " as recall_mails" + " on actions.id = recall_mails.action_id ")
                .append("WHERE recall_tasks.status = ?;");
        JsonArray params = new JsonArray().add(status.method());

        Sql.getInstance().prepared(query.toString(), params, SqlResult.validResultHandler(PromiseHelper.handlerJsonArray(promise)));

        return promise.future();
    }

    @Override
    public Future<JsonObject> createTask(Action<RecallTask> action, RecallTask task) {
        Promise<JsonObject> promise = Promise.promise();

        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ")
                .append(this.taskTable)
                .append(" (" + Field.ACTION_ID + "," + Field.STATUS + "," + Field.RECALL_MAIL_ID + "," + Field.RECEIVER_ID + ") ")
                .append("VALUES (?, ?, ?, ?)")
                .append("RETURNING *");

        JsonArray values = new JsonArray();
        values.add(action.getId()).add(task.getStatus()).add(task.getRecallMessage().getRecallId()).add(task.getReceiverId());
        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(PromiseHelper.handlerJsonObject(promise)));

        return promise.future();
    }
}
