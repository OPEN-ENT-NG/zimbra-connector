package fr.openent.zimbra.worker;

import fr.openent.zimbra.core.enums.QueueWorkerAction;
import fr.openent.zimbra.core.enums.QueueWorkerStatus;
import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.task.Task;
import fr.openent.zimbra.tasks.service.QueueService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;

import java.util.*;

abstract class QueueWorker<T extends Task<T>> extends AbstractVerticle implements Handler<Message<JsonObject>> {
    protected final ConfigManager configManager = new ConfigManager(Vertx.currentContext().config());
    protected final ServiceManager serviceManager = ServiceManager.getServiceManager();
    protected Logger log;
    protected boolean running = false;
    protected QueueWorkerStatus workerStatus = QueueWorkerStatus.NOT_STARTED;

    protected QueueService<T> queueService;
    protected EventBus eb;

    protected Queue<T> queue = new LinkedList<>();
    protected int maxQueueSize = 1000;

    protected abstract void execute(T task);

    @Override
    public void start() throws Exception {
        this.eb = vertx.eventBus();
    }

    public void startQueue() {
        this.running = true;
        this.workerStatus = QueueWorkerStatus.RUNNING;
        while (this.running && !queue.isEmpty()) {
            execute(this.queue.poll());
        }
    }

    @Override
    public void handle(Message<JsonObject> event) {
        QueueWorkerAction action = QueueWorkerAction.valueOf(event.body().getString(Field.ACTION));
        int maxQueueSize = event.body().getInteger(Field.MAXQUEUESIZE, configManager.getZimbraRecallWorkerMaxQueue());

        switch (action) {
            case REMOVE_TASK:
                try {
                    List<T> tasks = queueService.createTasksAndActionFromData(event.body().getJsonArray(Field.TASKS));
                    removeTasks(tasks);
                } catch (Exception e) {
                    String errMessage = String.format("[Zimbra@%s::handle]:  " +
                                    "an error has occurred while creating tasks: %s",
                            this.getClass().getSimpleName(), e.getMessage());
                    log.error(errMessage);
                }
            case SYNC_QUEUE:
                this.syncQueue();
                break;
            case CLEAR_QUEUE:
                this.clearQueue();
                break;
            case START:
                this.startQueue();
                break;
            case PAUSE:
                this.pauseQueue();
                break;
            case SET_MAX_QUEUE_SIZE:
                this.setMaxQueueSize(maxQueueSize);
                break;
            case GET_STATUS:
                this.sendWorkerStatus(event);
                break;
            case GET_REMAINING_SIZE:
                this.sendRemainingSize(event);
                break;
            case UNKNOWN:
            default:
                log.error("[ZimbraConnector@%s::handle] Unknown QueueWorkerAction: %s", this.getClass().getSimpleName(), action.getValue());
                break;
        }
    }
    public void addTasks(List<T> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            log.error("[ZimbraConnector@ICalRequestWorker:addTasks] taskIds cannot be null or empty");
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

    public void addTask(T task) {
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

    public void syncQueue() {
        queueService.getPendingTasks()
                .onSuccess(this::addTasks)
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::syncQueue]:  " +
                                    "an error has occurred while creating task: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                });
    };


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
        for (T task : tasks) {
            removeTask(task);
        }
    }

    public void removeTask(T task) {
        this.queue.removeIf(queuedTask -> queuedTask.getId() == task.getId());
    }
}
