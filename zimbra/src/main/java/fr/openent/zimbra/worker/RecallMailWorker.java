package fr.openent.zimbra.worker;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskType;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.model.task.Task;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Iterator;
import java.util.List;

public class RecallMailWorker extends QueueWorker implements Handler<Message<JsonObject>> {

    public static final String RECALL_MAIL_HANDLER_ADDRESS = "zimbra.handler";

    private static final Logger log = LoggerFactory.getLogger(RecallMailWorker.class);

    private final ConfigManager configManager = new ConfigManager(config());

    @Override
    public void start() throws Exception {
        super.start();
        this.setMaxQueueSize(configManager.getRecallWorkerMaxQueue());
        this.eb.localConsumer(RECALL_MAIL_HANDLER_ADDRESS, this);
    }

    @Override
    public void handle(Message<JsonObject> event) {
        QueueWorkerAction action = QueueWorkerAction.valueOf(event.body().getString(Field.ACTION));
        long taskId = event.body().getLong("taskId");
        List<Long> taskIds = event.body().getJsonArray("taskIds", new JsonArray()).getList();
        int maxQueueSize = event.body().getInteger("maxQueueSize", configManager.getRecallWorkerMaxQueue());

        switch (action) {
            case ADD_TASKS:
                this.addTasks(taskIds);
                break;
            case ADD_TASK:
                this.addTask(taskId);
                break;
            case REMOVE_TASK:
                this.removeTask(taskId);
                break;
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
                log.error("[ZimbraConnector@RecallMailWorker::handle] Unknown QueueWorkerAction: %s", action.getValue());
                break;
        }
    }

    @Override
    public void startQueue() {
        super.startQueue();
        Iterator<Task> itr = queue.iterator();
        while (this.running && itr.hasNext()) {
            Task currentTask = itr.next();
            // todo: execute task
            this.queue.poll();
            // or this.queue.remove(currentTask);
        }
    }

    @Override
    public void pauseQueue () {
        super.pauseQueue();
    }

    public void syncQueue() {
        this.queueService.getTasksInProgress(TaskType.RECALL)
                .onComplete(result -> {
                    if (result.succeeded()) {
                        this.queue.addAll(result.result().subList(0, this.remainingSize() - 1));
                        if (!this.running) this.startQueue();
                    } else {
                        log.error("[ZimbraConnector@RecallMailWorker:refillQueue] Cannot retrieve tasks in progress %s", result.cause().getMessage());
                    }
                });
    }

    @Override
    public void addTasks(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            log.error("[ZimbraConnector@RecallMailWorker:addTasks] taskIds cannot be null or empty");
            return;
        }
        if (this.queue.size() + taskIds.size() > this.maxQueueSize) {
            log.warn("[ZimbraConnector@RecallMailWorker:addTasks] Queue size limit is reached");
        }
        // todo: add only tasks size that can fit in the queue
        throw new NotImplementedException();
    }

    @Override
    public void addTask(long taskId) {
        if (this.queue.size() + 1 > this.maxQueueSize) {
            log.error("[ZimbraConnector@RecallMailWorker:addTask] Queue is full");
            return;
        }
        this.queueService.getTask(taskId).onComplete(result -> {
            if (result.succeeded()) {
                this.queue.add(result.result());
            } else {
                log.error("[ZimbraConnector@RecallMailWorker:addTask] Cannot retrieve task %s, %s", taskId, result.cause().getMessage());
            }
        });
    }

    @Override
    public void removeTask(long taskId) {
        for (Task task : this.queue) {
//            if (task.getId() == taskId) {
//                this.queue.remove(task);
//            }
        }
    }
}
