package fr.openent.zimbra.tasks.service;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.FutureHelper;
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
import org.apache.commons.lang3.NotImplementedException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
     * Used to create tasks depending on data retrieved from the db.
     * @param action        Action linked to the task.
     * @param taskData      Data about the specific task.
     * @return              List of tasks instances.
     * @throws Exception    If data does not match the model.
     */
    protected List<T> createTasksFromData(Action<T> action, JsonArray taskData) throws Exception {
        throw new NotImplementedException();
    }

    /**
     * Insert a list of Task in the worker queue
     *
     * @param tasks List of Task to insert
     * @return
     */
    public Future<List<T>> insertTasksInQueue(List<T> tasks) {
        throw new NotImplementedException();
    }

    protected abstract T createTaskFromData(JsonObject taskData, Action<T> action) throws Exception;

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
                        promise.fail(ErrorEnum.ERROR_QUEUE_TASK.method());
                    }
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::createTask]:  " +
                                    "an error has occurred while creating task: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_QUEUE_TASK.method());
                });

        return promise.future();
    }

    public Future<List<T>> createTasksByBatch(Action<T> action, List<RecallTask> tasks, int batchSize) {
        Promise<List<T>> promise = Promise.promise();
        if (tasks.isEmpty())
            return Future.failedFuture(ErrorEnum.NO_MAIL_TO_RECALL.method());

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
                        promise.fail(ErrorEnum.ERROR_QUEUE_TASK.method());
                    }

                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::createTasksByBatch]:  " +
                                    "error retrieving data from database: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_TASK_RETRIEVE.method());
                });

        return promise.future();
    }

    public Future<List<T>> createTasks(Action<T> action, List<T> tasks) {
        Promise<List<T>> promise = Promise.promise();
        if (tasks.isEmpty())
            return Future.failedFuture(ErrorEnum.ERROR_QUEUE_TASK.method());

        List<Future<T>> futures = new ArrayList<>();
        for (T task : tasks) {
            futures.add(this.createTask(action, task));
        }
        FutureHelper.all(futures)
                .onSuccess(res -> promise.complete(res.list()))
                .onFailure(promise::fail);

        return promise.future();
    }

    /**
     * Set a new row of logs in the database with the detail of why task failed. Also, change the task status to failed
     * @param task  The failed Task
     * @return      Instance of task model with updated status.
     */
    public Future<T> logFailureOnTask(T task, String error) {
        Promise<T> promise = Promise.promise();

        dbTaskService.createLogsForTask(task, error)
                .compose(res -> editTaskStatus(task, TaskStatus.ERROR))
                .onSuccess(promise::complete)
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::logFailureOnTask]: error while saving logs: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_EDITING_TASK.method());
                });

        return promise.future();
    }

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
                            promise.fail(ErrorEnum.ERROR_EDITING_TASK.method());
                        }
                    } else {
                        String errMessage = String.format("[Zimbra@%s::editTaskStatus]: missing updated status",
                                this.getClass().getSimpleName());
                        log.error(errMessage);
                        promise.fail(ErrorEnum.ERROR_EDITING_TASK.method());
                    }
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::editTaskStatus]: fail to call db for task status update: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_UPDATING_TASK.method());
                });

        return promise.future();
    }

    /**
     * Retrieve all the pending tasks from DB.
     * @return List of pending task
     */
    public Future<List<T>> getPendingTasks() {
        Promise<List<T>> promise = Promise.promise();

        dbTaskService.retrieveTasksDataFromDB(TaskStatus.PENDING)
                .onSuccess(taskData -> {
                    try {
                        List<T> tasks = createTasksAndActionFromData(taskData);
                        promise.complete(tasks);
                    } catch (Exception e) {
                        String errMessage = String.format("[Zimbra@%s::getPendingTasks]: fail fetching model from data: %s",
                                this.getClass().getSimpleName(), e.getMessage());
                        log.error(errMessage);
                        promise.fail(ErrorEnum.ERROR_FETCHING_TASK.method());
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
                        promise.fail(ErrorEnum.ERROR_FETCHING_ACTION.method());
                    }
                })
                .onFailure(err -> {
                        String errMessage = String.format("[Zimbra@%s::createAction]:  " +
                                        "an error has occurred while creating action in queue: %s",
                                this.getClass().getSimpleName(), err.getMessage());
                        log.error(errMessage);
                        promise.fail(ErrorEnum.ERROR_RETRIEVING_ACTION.method());
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
                action = new Action<>(new JsonObject(taskData.getString(Field.ACTION)));
                this.actionList.add(action);
            }
            try {
                T newTask = createTaskFromData(taskData, action);
                action.addTask(newTask);
                taskList.add(newTask);
            } catch(Exception e) {
                String errMessage = String.format("[Zimbra@%s::createTasksAndActionFromData]:  " +
                                        "an error has occured while creating tasks: %s",
                                this.getClass().getSimpleName(), e.getMessage());
                log.error(errMessage);
            }
            
        }
        return taskList;
    }

}
