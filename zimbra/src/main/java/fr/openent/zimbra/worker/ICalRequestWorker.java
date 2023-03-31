package fr.openent.zimbra.worker;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.EventBusHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.helper.StringHelper;
import fr.openent.zimbra.model.task.ICalTask;
import fr.openent.zimbra.service.CalendarService;
import fr.openent.zimbra.tasks.service.QueueService;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;


public class ICalRequestWorker extends QueueWorker<ICalTask> implements Handler<Message<JsonObject>> {
    protected static final Logger log = LoggerFactory.getLogger(ICalRequestWorker.class);

    private ConfigManager configManager;
    public static final String CALENDAR_MODULE_ADDRESS = "net.atos.entng.calendar";

    private CalendarService calendarService;
    private QueueService<ICalTask> queueService;

    @Override
    public void start() throws Exception {
        super.start();
        this.configManager = new ConfigManager(Vertx.currentContext().config());
        this.setMaxQueueSize(configManager.getZimbraICalWorkerMaxQueue());
        this.eb.localConsumer(this.getClass().getName(), this);
        this.calendarService = ServiceManager.getServiceManager().getCalendarService();
        this.queueService = ServiceManager.getServiceManager().getICalQueueService();
    }

    @Override
    public void execute(ICalTask task) {
        String userId = task.getAction().getUserId().toString();

        if (StringHelper.isNullOrEmpty(userId)) {
            String errMessage = String.format("[Zimbra@%s::execute]: user is not defined", this.getClass().getSimpleName());
            queueService.logFailureOnTask(task, ErrorEnum.USER_NOT_DEFINED.method())
                    .onFailure(err -> log.error(String.format("[Zimbra@%s::execute]: failed to create log in db: %s", this.getClass().getSimpleName(), err.getMessage())));
            log.error(errMessage);
            return;
        }

        UserUtils.getUserInfos(this.eb, userId, user -> retrieveIcalAndNotifyCalendar(user, task));
    }

    private void retrieveIcalAndNotifyCalendar(UserInfos user, ICalTask task) {
        calendarService.getICal(user)
                .onSuccess(ical -> {
                    queueService.editTaskStatus(task, TaskStatus.FINISHED)
                            .onFailure(err -> {
                                String errMessage = String.format("[Zimbra@%s::retrieveIcalAndNotifyCalendar]: error task status change: %s",
                                        this.getClass().getSimpleName(), err.getMessage());
                                log.error(errMessage);
                            });
                    sendICalToCalendarModule(ical, task)
                            .onFailure(err -> {
                                String errMessage = String.format("[Zimbra@%s::retrieveIcalAndNotifyCalendar]: error notify calendar: %s",
                                        this.getClass().getSimpleName(), err.getMessage());
                                log.error(errMessage);
                            });
                })
                .onFailure(error -> {
                    String errMessage = String.format("[Zimbra@%s::retrieveIcalAndNotifyCalendar]: error during ical retrieval: %s",
                            this.getClass().getSimpleName(), error.getMessage());
                    log.error(errMessage);
                    queueService.logFailureOnTask(task, ErrorEnum.ERROR_RETRIEVING_ICAL.method());
                    notifyFailToCalendarModule(ErrorEnum.ERROR_NOTIFY_CALENDAR.method())
                            .onFailure(err -> {
                                String errorMessage = String.format("[Zimbra@%s::retrieveIcalAndNotifyCalendar]: error notify calendar: %s",
                                        this.getClass().getSimpleName(), err.getMessage());
                                log.error(errorMessage);
                            });
                });
    }

    private Future<Void> notifyFailToCalendarModule(String error) {
        Promise<Void> promise = Promise.promise();

        JsonObject icalMessage = new JsonObject()
                .put(Field.ACTION, "zimbra-platform-ics")
                .put(Field.STATUS, Field.KO)
                .put(Field.RESULT, new JsonObject());

        EventBusHelper.requestJsonObject(eb, CALENDAR_MODULE_ADDRESS, icalMessage)
                .onSuccess(res -> promise.complete())
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::notifyFailToCalendarModule]:  " +
                                    "fail while sending error notification to calendar: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    promise.fail(ErrorEnum.ERROR_NOTIFY_CALENDAR.method());
                    log.error(errMessage);
                });

        return promise.future();
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
