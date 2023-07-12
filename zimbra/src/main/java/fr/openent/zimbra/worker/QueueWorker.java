package fr.openent.zimbra.worker;

import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.QueueWorkerAction;
import fr.openent.zimbra.core.enums.QueueWorkerStatus;
import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.EventBusHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.task.Task;
import fr.openent.zimbra.tasks.service.QueueService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;

abstract class QueueWorker<T extends Task<T>> extends AbstractVerticle implements Handler<Message<JsonObject>> {
    protected final ConfigManager configManager = new ConfigManager(Vertx.currentContext().config());
    protected final ServiceManager serviceManager = ServiceManager.getServiceManager();
    protected static Logger log = LoggerFactory.getLogger(QueueWorker.class);;
    protected boolean running = false;
    protected QueueWorkerStatus workerStatus = QueueWorkerStatus.NOT_STARTED;

    protected QueueService<T> queueService;
    protected EventBus eb;

    protected Queue<T> queue = new LinkedList<>();
    protected int maxQueueSize = 1000;

    protected abstract Future<Void> execute(T task);

    @Override
    public void start() throws Exception {
        this.eb = vertx.eventBus();
    }

    public void startQueue() {
        this.running = true;
        this.workerStatus = QueueWorkerStatus.RUNNING;
        Future<Void> previousFuture = Future.succeededFuture();
        while (this.running && !queue.isEmpty()) {
            T task = this.queue.poll();
            try {
                previousFuture = previousFuture
                        .compose(res -> {
                            Promise<Void> waitingPromise = Promise.promise();
                            execute(task)
                                .onComplete(execRes -> waitingPromise.complete());
                            return waitingPromise.future();
                        });
            } catch (Exception e) {
                previousFuture = Future.succeededFuture();
                String errMessage = String.format("[Zimbra@%s::startQueue]:  " +
                        "an error has occurred while executing task: %s",
                        this.getClass().getSimpleName(), e.getMessage());
                queueService.logFailureOnTask(task, errMessage);
                log.error(errMessage);
            }
        }
    }

    @Override
    public void handle(Message<JsonObject> message) {
        QueueWorkerAction action = QueueWorkerAction.valueOf(message.body().getString(Field.ACTION));
        int newMaxQueueSize = message.body().getInteger(Field.MAXQUEUESIZE, configManager.getZimbraRecallWorkerMaxQueue());

        switch (action) {
            case REMOVE_TASK:
                try {
                    List<T> tasks = queueService.createTasksAndActionFromData(message.body().getJsonArray(Field.TASKS));
                    removeTasks(tasks);
                    message.reply(new JsonObject().put(Field.STATUS, Field.OK));
                } catch (Exception e) {
                    String errMessage = String.format("[Zimbra@%s::handle]:  " +
                                    "an error has occurred while creating tasks: %s",
                            this.getClass().getSimpleName(), e.getMessage());
                    EventBusHelper.eventBusError(errMessage, ErrorEnum.ERROR_CREATING_TASKS.method(), message);
                    log.error(errMessage);
                }
                break;
            case SYNC_QUEUE:
                this.syncQueue(message);
                break;
            case CLEAR_QUEUE:
                this.clearQueue();
                message.reply(new JsonObject().put(Field.STATUS, Field.OK));
                break;
            case START:
                this.startQueue();
                message.reply(new JsonObject().put(Field.STATUS, Field.OK));
                break;
            case PAUSE:
                this.pauseQueue();
                message.reply(new JsonObject().put(Field.STATUS, Field.OK));
                break;
            case SET_MAX_QUEUE_SIZE:
                this.setMaxQueueSize(newMaxQueueSize);
                message.reply(new JsonObject().put(Field.STATUS, Field.OK));
                break;
            case GET_STATUS:
                this.sendWorkerStatus(message);
                break;
            case GET_REMAINING_SIZE:
                this.sendRemainingSize(message);
                break;
            case UNKNOWN:
            default:
                String errMessage = String.format("[ZimbraConnector@%s::handle] Unknown QueueWorkerAction: %s",
                        this.getClass().getSimpleName(), action.getValue());
                log.error(errMessage);
                EventBusHelper.eventBusError(errMessage, ErrorEnum.ACTION_DOES_NOT_EXIST.method(), message);
                break;
        }
    }
    public void addTasks(List<T> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        if (this.queue.size() + tasks.size() > this.maxQueueSize) {
            log.warn("[ZimbraConnector@ICalRequestWorker:addTasks] Queue size limit is reached");
        }

        for(T taskIteration : tasks) {
            if (!this.queue.contains(taskIteration) && (this.queue.size() < this.maxQueueSize)) {
                this.addTask(taskIteration);
            }
        }
    }

    private void addTask(T task) {
        if (this.queue.size() + 1 > this.maxQueueSize) {
            log.error(String.format("[ZimbraConnector@%s:addTask] Queue is full", this.getClass().getSimpleName()));
            return;
        }

        this.queue.add(task);
    }

    public void pauseQueue() {
        this.running = false;
        this.workerStatus = QueueWorkerStatus.PAUSED;
    }

    public void clearQueue() { this.queue.clear(); }

    public void syncQueue(Message<JsonObject> message) {
        pauseQueue();
        queueService.getPendingTasks()
                .onSuccess(tasks -> {
                    this.addTasks(tasks);
                    message.reply(new JsonObject().put(Field.STATUS, Field.OK).put(Field.RESULT, new JsonObject()
                            .put(Field.MESSAGE, Field.OK)));
                    this.startQueue();
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::syncQueue]:  " +
                                    "an error has occurred while creating task: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    EventBusHelper.eventBusError(errMessage, ErrorEnum.TASKS_NOT_RETRIEVED.method(), message);
                    log.error(errMessage);
                    startQueue();
                });
    }


    public void setMaxQueueSize (int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public int remainingSize () {
        return this.queue.size() - this.maxQueueSize;
    }

    public void sendRemainingSize(Message<JsonObject> event) {
        JsonObject response = new JsonObject();
        response.put(Field.REMAININGSIZE, remainingSize());
        response.put(Field.STATUS, 200);
        event.reply(response);
    }

    public void sendWorkerStatus(Message<JsonObject> event) {
        JsonObject response = new JsonObject();
        response.put(Field.WORKERSTATUS, this.workerStatus);
        response.put(Field.STATUS, 200);
        event.reply(response);
    }

    public void removeTasks(List<T> tasks) {
        queue.removeAll(tasks);
    }

    public void removeTask(T task) {
        this.queue.removeIf(queuedTask -> queuedTask.getId() == task.getId());
    }
}
