package fr.openent.zimbra.worker;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.QueueWorkerAction;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.EventBusHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.helper.StringHelper;
import fr.openent.zimbra.model.task.ICalTask;
import fr.openent.zimbra.service.CalendarService;
import fr.openent.zimbra.service.DbTaskService;
import fr.openent.zimbra.service.QueueService;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserUtils;

import java.util.Iterator;
import java.util.List;

public class ICalRequestWorker extends QueueWorker<ICalTask> implements Handler<Message<JsonObject>> {
    protected final Integer maxQueueSize = Zimbra.appConfig.getZimbraICalWorkerMaxQueue();

    protected static final Logger log = LoggerFactory.getLogger(ICalRequestWorker.class);

    private ConfigManager configManager;
    public static final String CALENDAR_MODULE_ADDRESS = "net.atos.entng.calendar";

    private CalendarService calendarService;
    private QueueService<ICalTask> queueService;
    private DbTaskService<ICalTask> dbTaskService;
    private Message<JsonObject> message;

    @Override
    public void start() throws Exception {
        super.start();
        this.configManager = new ConfigManager(Vertx.currentContext().config());
        this.setMaxQueueSize(configManager.getZimbraICalWorkerMaxQueue());
        this.eb.localConsumer(this.getClass().getName(), this);
        this.calendarService = ServiceManager.getServiceManager().getCalendarService();
        this.queueService = ServiceManager.getServiceManager().getICalQueueService();
        this.dbTaskService = ServiceManager.getServiceManager().getSqlICalTaskService();
    }

    @Override
    public void handle(Message<JsonObject> message) {
        this.message = message;
        QueueWorkerAction action = QueueWorkerAction.valueOf(message.body().getString(Field.ACTION));
        int maxQueueSize = message.body().getInteger(Field.MAXQUEUESIZE, configManager.getZimbraICalWorkerMaxQueue());

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
                this.sendWorkerStatus(message);
                break;
            case GET_REMAINING_SIZE:
                this.sendRemainingSize(message);
                break;
            case UNKNOWN:
            default:
                log.error("[ZimbraConnector@%s::handle] Unknown QueueWorkerAction: %s",
                        this.getClass().getSimpleName(), action.getValue());
                break;
        }
    }

    @Override
    public void startQueue() {
        super.startQueue();
        Iterator<ICalTask> itr = queue.iterator();
        while (this.running && itr.hasNext() && !this.queue.isEmpty()) {
            this.getICal(this.queue.poll());
        }
    }

    @Override
    public void pauseQueue () {
        super.pauseQueue();
    }

    @Override
    public void syncQueue() {
        this.pauseQueue();
        queueService.getPendingTasks()
                .onSuccess(pendingTasks -> {
                    this.addTasks(pendingTasks);
                    this.startQueue();
                })
                .onFailure(error -> {
                    String errMessage = String.format("[Zimbra@%s::syncQueue]: error during queue synchronization: %s",
                            this.getClass().getSimpleName(), error.getMessage());
                    log.error(errMessage);
                });

    }

    @Override
    public void addTasks(List<ICalTask> tasks) {
        if (this.queue.size() + tasks.size() > this.maxQueueSize) {
            log.warn("[ZimbraConnector@%s:addTasks] Queue size limit is reached", this.getClass().getSimpleName());
        }

        tasks.forEach(task -> {
            if (!this.queue.contains(task) && (this.queue.size() < this.maxQueueSize)) {
                this.addTask(task);
            }
        });
    }

    @Override
    public void addTask(ICalTask task) {
        if (this.queue.size() + 1 > this.maxQueueSize) {
            log.error("[ZimbraConnector@%s:addTask] Queue is full", this.getClass().getSimpleName());
            return;
        }

        this.queue.add(task);
    }

    @Override
    public void removeTask(ICalTask task) {
        if (task != null) {
            this.queue.remove(task);
        }
    }

    public void removeTasks(List<ICalTask> tasks) {
        this.queue.removeAll(tasks);
    }


    public void getICal(ICalTask task) {
        String userId = task.getAction().getUserId().toString();

        if (StringHelper.isNullOrEmpty(userId)) {
            String errMessage = String.format("[Zimbra@%s::getICal]: user is not defined", this.getClass().getSimpleName());
            EventBusHelper.eventBusError(errMessage, "zimbra.no.user", this.message);
        }

        UserUtils.getUserInfos(this.eb, userId, user -> {
            calendarService.getICal(user)
                .compose(ical -> {
                    ical = ical.replaceAll("\\\r\\\n", "\n");
//                    ical = ical.replaceAll(";TZID\\=.*\\/.*\\\"", "");
                    return sendICalToCalendarModule(ical, task);
                })
                .onSuccess(result -> {
                    this.dbTaskService.editTaskStatus(task, TaskStatus.FINISHED)
                            .onFailure(err -> {
                                String errMessage = String.format("[Zimbra@%s::getICal]: error task status change: %s",
                                        this.getClass().getSimpleName(), err.getMessage());
                                log.error(errMessage);
                            });
                })
                .onFailure(error -> {
                    String errMessage = String.format("[Zimbra@%s::getICal]: error during ical retrieval: %s",
                            this.getClass().getSimpleName(), error.getMessage());
                    EventBusHelper.eventBusError(errMessage, "zimbra.ics.retrieval.error", message);
                    this.dbTaskService.editTaskStatus(task, TaskStatus.ERROR)
                            .onSuccess(res -> {
                                log.info("[Zimbra@%s::getICal]: task status changed to error");
                            })
                            .onFailure(err -> {
                                String errorMessage = String.format("[Zimbra@%s::getICal]: error task status change: %s",
                                        this.getClass().getSimpleName(), err.getMessage());
                                log.error(errorMessage);
                            });
                });
        });
    }

    private Future<Void> sendICalToCalendarModule(String ical, ICalTask task) {
        Promise<Void> promise = Promise.promise();

        JsonObject icalMessage = new JsonObject()
                .put(Field.ACTION, "zimbra-platform-ics")
                .put(Field.STATUS, Field.OK)
                .put(Field.RESULT, new JsonObject()
                        .put(Field.ICS, ical)
                        .put(Field.PLATFORM, Field.ZIMBRAUC)
                        .put(Field.USERID, task.getAction().getUserId().toString()));

        eb.request(CALENDAR_MODULE_ADDRESS, icalMessage, event -> {
            if(event.failed()) {
                String errMessage = String.format("[Zimbra@%s::sendICalToCalendarModule]:  " +
                                "an error has occurred while sending ical: %s",
                        this.getClass().getSimpleName(), event.cause().getMessage());
                promise.fail("zimbra.ical.worker.eb.error");
                log.error(errMessage);
            } else {
                promise.complete();
            }
        });

        return promise.future();
    }


}
