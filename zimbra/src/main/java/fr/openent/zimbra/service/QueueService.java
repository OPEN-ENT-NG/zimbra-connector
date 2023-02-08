package fr.openent.zimbra.service;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.model.action.Action;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Abstract queue service
 *
 * @param <T> Task
 * @param <V> TaskInfo
 */
public abstract class QueueService<T, V> {
    protected static final Logger log = LoggerFactory.getLogger(QueueService.class);

    protected final String schema;
    protected final String actionTable;
    protected String taskTable;
    protected ActionType actionType;

    public QueueService(String schema) {
        this.schema = schema;
        this.actionTable = schema + ".action";
        this.taskTable = schema + ".task";
    }

    /**
     * Insert a list of Task in the worker queue
     *
     * @param tasks List of Task to insert
     * @return
     */
    public abstract Future<List<T>> insertTasksInQueue(List<T> tasks);

    /**
     * Method to create Tasks
     *
     * @param action Action
     * @param taskInfo Task info
     * @return
     */
    protected abstract Future<List<T>> createTasks(Action action, V taskInfo);

    /**
     * Create an Action
     *
     * @param userId User id
     * @param actionType Action type
     * @param approved
     * @return
     */
    protected Future<Action> createAction(UUID userId, ActionType actionType, boolean approved) {
        Promise<Action> promise = Promise.promise();

        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ")
                .append(actionTable)
                .append(" (" + Field.USER_ID + "," + Field.TYPE + "," + Field.APPROVED + ") ")
                .append("VALUES (?, ?, ?)")
                .append("RETURNING " + Field.ACTION_ID + "," + Field.USER_ID + "," + Field.CREATED_AT + "," + Field.TYPE + "," + Field.APPROVED);

        JsonArray values = new JsonArray();
        values.add(userId.toString()).add(actionType.method()).add(approved);

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(handler -> {
            if (handler.isRight()) {
                long id = handler.right().getValue().getLong(Field.ID);
                Date createdAt = Date.from(Instant.parse(handler.right().getValue().getString(Field.CREATED_AT)));
                Action action = new Action(id, userId, createdAt, actionType, approved);
                promise.complete(action);
            } else {
                String errMessage = String.format("[Zimbra@%s::createAction]:  " +
                                "an error has occurred while creating action in queue: %s",
                        this.getClass().getSimpleName(), handler.left().getValue());
                log.error(errMessage);
                promise.fail("zimbra.error.queue.action");
            }
        }));

        return promise.future();
    }

    /**
     * Create and insert tasks in the worker queue
     *
     * @param userId User id
     * @param taskInfo Task data
     * @return
     */
    public Future<List<T>> createAndInsertTasksInQueue(UUID userId, V taskInfo) {
        Promise<List<T>> promise = Promise.promise();

        this.createAction(userId, this.actionType, false)
                .compose(action -> this.createTasks(action, taskInfo))
                .compose(this::insertTasksInQueue)
                .onSuccess(promise::complete)
                .onFailure(promise::fail);

        return promise.future();
    }
}
