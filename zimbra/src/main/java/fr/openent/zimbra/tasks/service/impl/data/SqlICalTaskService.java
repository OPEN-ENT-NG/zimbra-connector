package fr.openent.zimbra.tasks.service.impl.data;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.PromiseHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.ICalTask;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.tasks.service.DbTaskService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.List;

public class SqlICalTaskService extends DbTaskService<ICalTask> {
    protected static final Logger log = LoggerFactory.getLogger(SqlICalTaskService.class);
    private static final String I_CAL_TABLE = "ical_request_tasks";
    private final String taskTable = this.schema + "." + I_CAL_TABLE;
    private final ConfigManager configManager;

    public SqlICalTaskService(String schema) {
        super(schema);
        this.configManager  = new ConfigManager(Vertx.currentContext().config());
    }

    @Override
    protected Future<JsonArray> retrieveTasksDataFromDB(TaskStatus status) {
        Promise<JsonArray> promise = Promise.promise();
        String query = "SELECT ical_tasks.*, actions.user_id, actions.created_at, actions.type, actions.approved "
        + "FROM " + this.taskTable + " as ical_tasks "
        + "join " + this.actionTable + " as actions on actions.id = ical_tasks.action_id "
        + "WHERE " + "ical_tasks.status = ? "
        + "ORDER BY actions.created_at "
        + "LIMIT ?";
        JsonArray params = new JsonArray().add(status.method()).add(configManager.getZimbraICalWorkerMaxQueue());


        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(PromiseHelper.handlerJsonArray(promise)));

        return promise.future();
    }

    @Override
    public Future<JsonObject> createTask(Action<ICalTask> action, ICalTask task) {
        Promise<JsonObject> promise = Promise.promise();
        String query = "INSERT INTO " + this.taskTable +
                " (" + Field.ACTION_ID + ", " + Field.STATUS + ", " + Field.NAME + ", " + Field.BODY + ") " +
                "VALUES (?, ?, ?, ?) " +
                "RETURNING *";

        JsonArray values = new JsonArray();
        values.add(action.getId()).add(task.getStatus().method()).add(task.getName()).add(task.getBody());

        Sql.getInstance().prepared(query, values, SqlResult.validUniqueResultHandler(handler -> {
            if (handler.isRight()) {
                long id = handler.right().getValue().getLong(Field.ID);

                task.setId(id);
                promise.complete(task.toJson());
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

    @Override
    protected Future<JsonArray> createTasksByBatch(Action<ICalTask> action, List<RecallTask> tasks, int batchSize) {
        return null;
    }

}
