package fr.openent.zimbra.tasks.service.impl.data;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.PromiseHelper;
import fr.openent.zimbra.helper.TransactionHelper;
import fr.openent.zimbra.model.TransactionElement;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.tasks.service.DbTaskService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.ArrayList;
import java.util.List;

public class SqlRecallTaskService extends DbTaskService<RecallTask> {
    private final String recallMailTable = schema + "." + "recall_mails";
    private final String taskTable = schema + "." + "recall_recipient_tasks";
    private static Logger log = LoggerFactory.getLogger(SqlRecallTaskService.class);

    public SqlRecallTaskService(String schema) {
        super(schema);
    }

    @Override
    protected Future<JsonArray> retrieveTasksDataFromDB(TaskStatus status) {
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
    protected Future<JsonObject> createTask(Action<RecallTask> action, RecallTask task) {
        Promise<JsonObject> promise = Promise.promise();

        String query = "INSERT INTO " +
                this.taskTable +
                " (" + Field.ACTION_ID + "," + Field.STATUS + "," + Field.RECALL_MAIL_ID + "," + Field.RECEIVER_ID + "," + Field.RETRY + ") " +
                "VALUES (?, ?, ?, ?, ?)" +
                "RETURNING *";

        JsonArray values = new JsonArray();
        values
                .add(action.getId())
                .add(task.getStatus())
                .add(task.getRecallMessage().getRecallId())
                .add(task.getReceiverId().toString())
                .add(0);
        Sql.getInstance().prepared(query, values, SqlResult.validUniqueResultHandler(PromiseHelper.handlerJsonObject(promise)));

        return promise.future();
    }


    private TransactionElement createTransactionForTasks(Action<RecallTask> action, List<RecallTask> tasks) {
        StringBuilder query = new StringBuilder();
        JsonArray params = new JsonArray();

        query
                .append("INSERT INTO ")
                .append(this.taskTable)
                .append(" (")
                .append(Field.ACTION_ID).append(",").append(Field.STATUS).append(",").append(Field.RECALL_MAIL_ID).append(",").append(Field.RECEIVER_ID).append(",").append(Field.RETRY).append(") ")
                .append("VALUES ");
        tasks.forEach(task -> {
            query.append("(?, ?, ?, ?, ?),");
            params
                    .add(action.getId())
                    .add(task.getStatus())
                    .add(task.getRecallMessage().getRecallId())
                    .add(task.getReceiverId().toString())
                    .add(0);
        });
        query.deleteCharAt(query.length() - 1);
        query.append(" RETURNING *;");

        return new TransactionElement(query.toString(), params);
    }

    @Override
    protected Future<JsonArray> createTasksByBatch(Action<RecallTask> action, List<RecallTask> tasks, int batchSize) {
        Promise<JsonArray> promise = Promise.promise();
        List<TransactionElement> tasksTransactions = new ArrayList<>();

        for (int i = 0; i < batchSize; i += batchSize) {
            tasksTransactions.add(createTransactionForTasks(action, tasks.subList(i, Math.min(i + batchSize, tasks.size()))));
        }

        TransactionHelper.executeTransaction(tasksTransactions)
                .onSuccess(transResult -> {
                    promise.complete(transResult.stream().map(TransactionElement::getResult).reduce(new JsonArray(), JsonArray::add));
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::createTasksByBatch]:  " +
                                    "an error has occurred during create tasks transaction: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail("zimbra.error.queue.task");
                });

        return promise.future();
    }

}
