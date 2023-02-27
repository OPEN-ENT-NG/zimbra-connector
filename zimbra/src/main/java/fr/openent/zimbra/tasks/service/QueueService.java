package fr.openent.zimbra.tasks.service;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.model.task.Task;
import fr.openent.zimbra.utils.DateUtils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
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
    protected DbTaskService<T> dbTaskService;
    protected DbActionService dbActionService;

    public QueueService(String schema, DbTaskService<T> dbTaskService, DbActionService dbActionService) {
        this.schema = schema;
        this.actionTable = schema + ".actions";
        this.taskTable = schema + ".tasks";
        this.actionList = new HashSet<>();
        this.dbTaskService = dbTaskService;
        this.dbActionService = dbActionService;
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



    public Future<T> createTask(Action<T> action, T task) {
        Promise<T> promise = Promise.promise();

        dbTaskService.createTask(action, task)
                .onSuccess(taskData -> {
                    try {
                        promise.complete(createTaskFromData(taskData, action));
                    } catch (Exception e) {
                        String errMessage = String.format("[Zimbra@%s::createTask]:  " +
                                        "error while fetching model: %s",
                                this.getClass().getSimpleName(), e.getMessage());
                        log.error(errMessage);
                        promise.fail("zimbra.error.queue.task");
                    }
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

    protected abstract List<T> createTasksFromData(Action<T> action, JsonArray taskData) throws Exception;

    public Future<List<T>> createTasksByBatch(Action<T> action, List<RecallTask> tasks, int batchSize) {
        Promise<List<T>> promise = Promise.promise();
        if (tasks.isEmpty())
            return Future.failedFuture("no.mail.to.recall");

        dbTaskService.createTasksByBatch(action, tasks, batchSize)
                .onSuccess(tasksData -> {
                    List<T> recallList;
                    try {
                        recallList = createTasksFromData(action, tasksData);
                    } catch (Exception e) {
                        throw new IllegalArgumentException(e.getMessage());
                    }
                    if (recallList != null) {
                        promise.complete(recallList);;
                    } else {
                        String errMessage = String.format("[Zimbra@%s::createTasksByBatch]:  " +
                                        "error while fetching model",
                                this.getClass().getSimpleName());
                        log.error(errMessage);
                        promise.fail("zimbra.error.queue.task");
                    }

                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::createTasksByBatch]:  " +
                                    "error retrieving data from database: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail("zimbra.error.task.retrieve");
                });

        return promise.future();
    }

    public abstract Future<List<T>> createTasks(Action<T> action, List<T> tasks);

    public Future<T> editTaskStatus(T task, TaskStatus status) {
        Promise<T> promise = Promise.promise();

        dbTaskService.editTaskStatus(task, status)
                .onSuccess(taskStatus -> {
                    if (taskStatus.containsKey(Field.STATUS)) {
                        try {
                            task.setStatus(TaskStatus.fromString(taskStatus.getString(Field.STATUS)));
                            promise.complete(task);
                        } catch (Exception e){
                            String errMessage = String.format("[Zimbra@%s::editTaskStatus]: wrong status format: %s",
                                    this.getClass().getSimpleName(), e.getMessage());
                            log.error(errMessage);
                            promise.fail("zimbra.error.editing.task");
                        }
                    } else {
                        String errMessage = String.format("[Zimbra@%s::editTaskStatus]: missing updated status",
                                this.getClass().getSimpleName());
                        log.error(errMessage);
                        promise.fail("zimbra.error.editing.task");
                    }
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::editTaskStatus]: fail to call db for task status update: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail("zimbra.error.updating.task");
                });

        return promise.future();
    }

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
                    promise.fail(ErrorEnum.TASKS_NOT_RETRIEVED.method());
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

            dbActionService.createAction(userId, actionType, approved)
                .onSuccess(actionData -> {
                    try {
                        long id = actionData.getLong(Field.ID);
                        Date createdAt = new SimpleDateFormat(DateUtils.DATE_FORMAT_SQL).parse(actionData.getString(Field.CREATED_AT));
                        Action<T> action = new Action<>(id, userId, createdAt, actionType, approved);
                        promise.complete(action);
                    } catch (ParseException e) {
                        String errMessage = String.format("[Zimbra@%s::createAction]:  " +
                                        "an error has occurred while getting creation date: %s",
                                this.getClass().getSimpleName(), e.getMessage());
                        log.error(errMessage);
                        promise.fail("zimbra.error.queue.action.date");
                    }
                })
                .onFailure(err -> {
                        String errMessage = String.format("[Zimbra@%s::createAction]:  " +
                                        "an error has occurred while creating action in queue: %s",
                                this.getClass().getSimpleName(), err.getMessage());
                        log.error(errMessage);
                        promise.fail("zimbra.error.queue.action");
                    });
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

    public List<T> createTasksAndActionFromData(JsonArray tasksData) throws Exception {
        List<T> taskList = new ArrayList<>();

        for (Object o: tasksData) {
            if(!(o instanceof JsonObject)) {
                throw new IllegalArgumentException("JsonArray is not a JsonObject list");
            }
            JsonObject taskData = (JsonObject) o;
            Action<T> action;
            action = getActionById(taskData.getInteger(Field.ACTION_ID, null));
            if (action == null) {
                action = new Action<>(taskData);
                this.actionList.add(action);
            }
            T newTask = createTaskFromData(taskData, action);
            action.addTask(newTask);
            taskList.add(newTask);
        }
        return taskList;
    }

}
