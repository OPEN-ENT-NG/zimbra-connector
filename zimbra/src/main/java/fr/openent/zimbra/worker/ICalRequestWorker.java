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
import fr.openent.zimbra.tasks.helpers.CalendarEventBusHelper;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
    public static final String ZIMBRA_ACTION_ICS = "zimbra-platform-ics";

    private CalendarService calendarService;

    @Override
    public void start() throws Exception {
        super.start();
        this.configManager = new ConfigManager(Vertx.currentContext().config());
        this.setMaxQueueSize(configManager.getZimbraICalWorkerMaxQueue());
        this.eb.localConsumer(this.getClass().getName(), this);
        this.calendarService = ServiceManager.getServiceManager().getCalendarService();
        this.queueService = serviceManager.getICalQueueService();
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

    private JsonObject icalDataAsJson(String ical, ICalTask task) {
        return new JsonObject()
                .put(Field.ICS, ical)
                .put(Field.PLATFORM, Field.ZIMBRAUC)
                .put(Field.USERID, task.getAction().getUserId().toString());
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
                    notifyCalendarModule(CalendarEventBusHelper.createSucceedAnswerAndSetAction(ZIMBRA_ACTION_ICS, icalDataAsJson(ical, task)))
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
                    notifyCalendarModule(CalendarEventBusHelper.generateFailureNotification(error.getMessage()))
                            .onFailure(err -> {
                                String errorMessage = String.format("[Zimbra@%s::retrieveIcalAndNotifyCalendar]: error notify calendar: %s",
                                        this.getClass().getSimpleName(), err.getMessage());
                                log.error(errorMessage);
                            });
                });
    }

    private Future<JsonObject> notifyCalendarModule(JsonObject message) {
        return EventBusHelper.requestJsonObject(eb, CALENDAR_MODULE_ADDRESS, message);
    }



}
