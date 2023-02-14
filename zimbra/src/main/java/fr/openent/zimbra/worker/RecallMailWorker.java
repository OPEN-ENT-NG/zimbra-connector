package fr.openent.zimbra.worker;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.QueueWorkerAction;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.model.task.RecallTask;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Iterator;
import java.util.List;

public class RecallMailWorker extends QueueWorker<RecallTask> implements Handler<Message<JsonObject>> {

    public static final String RECALL_MAIL_HANDLER_ADDRESS = "zimbra.handler";

    private static final Logger log = LoggerFactory.getLogger(RecallMailWorker.class);

    private ConfigManager configManager;

    @Override
    public void start() throws Exception {
        super.start();
        this.configManager  = new ConfigManager(Vertx.currentContext().config());
        this.setMaxQueueSize(configManager.getZimbraRecallWorkerMaxQueue());
        this.eb.localConsumer(RECALL_MAIL_HANDLER_ADDRESS, this);
    }

    @Override
    public void handle(Message<JsonObject> event) {
        QueueWorkerAction action = QueueWorkerAction.valueOf(event.body().getString(Field.ACTION));
//        RecallTask taskId = event.body().getJsonObject("taskId");
        RecallTask taskId = null;
//        try {
//            taskId = new RecallTask(new JsonObject(), null, new RecallMail(0, ""));
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
        int maxQueueSize = event.body().getInteger(Field.MAXQUEUESIZE, configManager.getZimbraRecallWorkerMaxQueue());

        switch (action) {
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
        Iterator<RecallTask> itr = queue.iterator();
        while (this.running && itr.hasNext()) {
            RecallTask currentTask = itr.next();
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
//        this.queueService.getTasksInProgress(TaskType.RECALL)
//                .onComplete(result -> {
//                    if (result.succeeded()) {
//                        this.queue.addAll(result.result().subList(0, this.remainingSize() - 1));
//                        if (!this.running) this.startQueue();
//                    } else {
//                        log.error("[ZimbraConnector@RecallMailWorker:refillQueue] Cannot retrieve tasks in progress %s", result.cause().getMessage());
//                    }
//                });
    }

    @Override
    public void addTasks(List<RecallTask> tasks) {

    }

//    @Override
//    public void addTasks(List<Long> taskIds) {
//        if (taskIds == null || taskIds.isEmpty()) {
//            log.error("[ZimbraConnector@RecallMailWorker:addTasks] taskIds cannot be null or empty");
//            return;
//        }
//        if (this.queue.size() + taskIds.size() > this.maxQueueSize) {
//            log.warn("[ZimbraConnector@RecallMailWorker:addTasks] Queue size limit is reached");
//        }
//        // todo: add only tasks size that can fit in the queue
//        throw new NotImplementedException();
//    }


    public void addTask(RecallTask taskId) {
        if (this.queue.size() + 1 > this.maxQueueSize) {
            log.error("[ZimbraConnector@RecallMailWorker:addTask] Queue is full");
        }
//        this.queueService.getTask(taskId).onComplete(result -> {
//            if (result.succeeded()) {
//                this.queue.add(result.result());
//            } else {
//                log.error("[ZimbraConnector@RecallMailWorker:addTask] Cannot retrieve task %s, %s", taskId, result.cause().getMessage());
//            }
//        });
    }


    public void removeTask(RecallTask taskId) {
//        for (RecallTask task : this.queue) {
////            if (task.getId() == taskId) {
////                this.queue.remove(task);
////            }
//        }
    }
}
