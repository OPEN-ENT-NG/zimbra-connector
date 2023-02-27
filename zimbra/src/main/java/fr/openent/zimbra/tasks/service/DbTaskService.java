package fr.openent.zimbra.tasks.service;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.ICalTask;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.model.task.Task;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.List;

public abstract class DbTaskService<T extends Task<T>> {
    protected static final Logger log = LoggerFactory.getLogger(DbTaskService.class);
    protected final String schema;
    protected final String actionTable;
    private final String taskTable;


    public DbTaskService(String schema) {
        this.schema = schema;
        this.actionTable = schema + ".actions";
        this.taskTable = schema + ".tasks";
    }

    protected abstract Future<JsonArray> retrieveTasksDataFromDB(TaskStatus status);
    protected abstract Future<JsonObject> createTask(Action<T> action, T task);

    protected Future<JsonObject> editTaskStatus(T task, TaskStatus status) {
        Promise<JsonObject> promise = Promise.promise();
        String query = "UPDATE " + this.taskTable + " SET status = ? " + "WHERE id = ? RETURNING " + Field.STATUS + ";";

        JsonArray values = new JsonArray();
        values.add(status.method()).add(task.getId());

        Sql.getInstance().prepared(query, values, SqlResult.validUniqueResultHandler(handler -> {
            if (handler.isLeft()) {
                String errMessage = String.format("[Zimbra@%s::createTask]:  " +
                                "an error has occurred while creating task: %s",
                        this.getClass().getSimpleName(), handler.left().getValue());
                log.error(errMessage);
                promise.fail("zimbra.error.queue.task");
            } else {
                promise.complete(handler.right().getValue());
            }
        }));

        return promise.future();
    }

    protected abstract Future<JsonArray> createTasksByBatch(Action<T> action, List<RecallTask> tasks, int batchSize);
}
