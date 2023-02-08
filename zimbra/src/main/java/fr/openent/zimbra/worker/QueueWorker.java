package fr.openent.zimbra.worker;

import fr.openent.zimbra.model.task.Task;
import fr.openent.zimbra.service.QueueService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.*;

abstract class QueueWorker extends AbstractVerticle {

    protected boolean running = false;
    protected QueueWorkerStatus workerStatus = QueueWorkerStatus.NOT_STARTED;

    protected QueueService queueService;
    protected EventBus eb;

    protected Queue<Task> queue = new PriorityQueue<>();
    protected int maxQueueSize = 1000;

    @Override
    public void start() throws Exception {
        this.eb = vertx.eventBus();
    }

    public void startQueue() {
        this.running = true;
        this.workerStatus = QueueWorkerStatus.RUNNING;
    }

    public void pauseQueue() {
        this.running = false;
        this.workerStatus = QueueWorkerStatus.PAUSED;
    }

    public void clearQueue() { this.queue.clear(); }

    public abstract void syncQueue();

    public abstract void addTasks(List<Long> taskIds);

    public abstract void addTask(long taskId);

    public abstract void removeTask(long taskId);

    public void setMaxQueueSize (int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public int remainingSize () {
        return this.queue.size() - this.maxQueueSize;
    }

    public void sendRemainingSize(Message<JsonObject> event) {
        JsonObject response = new JsonObject();
        response.put("remainingSize", remainingSize());
        response.put("status", 200);
        event.reply(response);
    }

    public void sendWorkerStatus(Message<JsonObject> event) {
        JsonObject response = new JsonObject();
        response.put("workerStatus", this.workerStatus);
        response.put("status", 200);
        event.reply(response);
    }
}
