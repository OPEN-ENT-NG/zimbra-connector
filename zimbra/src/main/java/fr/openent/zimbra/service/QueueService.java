package fr.openent.zimbra.service;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.Task;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.time.Instant;
import java.util.*;

/**
 * Abstract queue service
 *
 * @param <T> Task
 */
public abstract class QueueService<T extends Task<T>> {
    protected static final Logger log = LoggerFactory.getLogger(QueueService.class);
    protected Set<Action<T>> actionList;

    protected final String schema;
    protected final String actionTable;
    protected String taskTable;
    protected ActionType actionType;
    protected ServiceManager serviceManager = ServiceManager.getServiceManager();
    protected DbTaskService<T> dbTaskService;

    public QueueService(String schema) {
        this.schema = schema;
        this.actionTable = schema + ".actions";
        this.taskTable = schema + ".task";
    }

    /**
     * Insert a list of Task in the worker queue
     *
     * @param tasks List of Task to insert
     * @return
     */
    public abstract Future<List<T>> insertTasksInQueue(List<T> tasks);

    protected Action<T> getActionById(int id) {
        return actionList.stream().filter(action -> action.getId() == id).findFirst().orElse(null);
    }

    public abstract Future<T> createTask(Action<T> action, T task);

    protected abstract List<T> createTasksFromData(JsonArray taskData) throws Exception;

    public abstract Future<List<T>> createTasks(Action<T> action, List<T> tasks);

    protected abstract T createTaskFromData(JsonObject taskData, Action<T> action) throws Exception;

    /**
     * Retrieve all the pending tasks from DB.
     * @return List of pending task
     */
    public Future<List<T>> getPendingTasks() {
        Promise<List<T>> promise = Promise.promise();

        dbTaskService.retrieveTasksDataFromDB(TaskStatus.PENDING)
                .onSuccess(taskData -> {
                    try {
                        promise.complete(createTasksAndActionFromData(taskData));
                    } catch (Exception e) {
                        String errMessage = String.format("[Zimbra@%s::getPendingTasks]: fail fetching model from data: %s",
                                this.getClass().getSimpleName(), e.getMessage());
                        log.error(errMessage);
                        promise.fail("zimbra.error.fetching.task");
                    }
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::getPendingTasks]:  " +
                                    "an error has occurred while retrieving tasks: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail("zimbra.error.retrieve.task");
                });

        return promise.future();
    }


    /**
     * Create an Action
     *
     * @param userId User id
     * @param actionType Action type
     * @param approved
     * @return
     */
    public Future<Action<T>> createAction(UUID userId, ActionType actionType, boolean approved) {
        Promise<Action<T>> promise = Promise.promise();

        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ")
                .append(actionTable)
                .append(" (" + Field.USER_ID + ", " + Field.TYPE + ", " + Field.APPROVED + ") ")
                .append("VALUES (?, ?, ?) ")
                .append("RETURNING " + Field.ID + ", " + Field.USER_ID + ", " + Field.CREATED_AT + ", " + Field.TYPE + ", " + Field.APPROVED);

        JsonArray values = new JsonArray();
        values.add(userId.toString()).add(actionType.method()).add(approved);

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(handler -> {
            if (handler.isRight()) {
                long id = handler.right().getValue().getLong(Field.ID);
                Date createdAt = Date.from(Instant.parse(handler.right().getValue().getString(Field.CREATED_AT)));
                Action<T> action = new Action<T>(id, userId, createdAt, actionType, approved);
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

    public Future<Action<T>> createAction(Action<T> action) {
        return createAction(action.getUserId(), action.getActionType(), action.getApproved());
    }

    /**
     * Create and insert tasks in the worker queue
     *
     * @param action Action
     * @return
     */
    public Future<List<T>> createAndInsertTasksInQueue(Action<T> action, List<T> tasks) {
        Promise<List<T>> promise = Promise.promise();

        this.createTasks(action, tasks)
                 .compose((t) -> {
                     action.addTasks(t);
                     return Future.succeededFuture(t);
                 })
                .compose(this::insertTasksInQueue)
                .onSuccess(promise::complete)
                .onFailure(promise::fail);

        return promise.future();
    }

    protected List<T> createTasksAndActionFromData(JsonArray tasksData) throws Exception {
        List<T> taskList = new ArrayList<>();

        for (Object o: tasksData) {
            if(!(o instanceof JsonObject)) {
                throw new IllegalArgumentException("JsonArray is not a JsonObject list");
            }
            JsonObject taskData = (JsonObject) o;
            Action<T> action;
            action = getActionById(taskData.getInteger(Field.ACTION_ID));
            if (action == null) {
                action = new Action<>(taskData);
            }
            T newTask = createTaskFromData(taskData, action);
            action.addTask(newTask);
            taskList.add(newTask);
        }
        return taskList;
    }

}
