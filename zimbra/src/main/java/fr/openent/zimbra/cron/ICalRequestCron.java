package fr.openent.zimbra.cron;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.QueueWorkerAction;
import fr.openent.zimbra.helper.EventBusHelper;
import fr.openent.zimbra.worker.ICalRequestWorker;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;

public class ICalRequestCron extends ControllerHelper implements Handler<Long> {
    private static final Logger log = LoggerFactory.getLogger(ICalRequestCron.class);

    private final EventBus eb;

    public ICalRequestCron (EventBus eb) {
        this.eb = eb;
    }

    @Override
    public void handle(Long event) {
        log.info("[ZimbraConnector@ICalRequestCron] ICalRequestCron started");
        final JsonObject message = new JsonObject();
        message.put(Field.ACTION, QueueWorkerAction.SYNC_QUEUE);
        EventBusHelper.requestJsonObject(eb, ICalRequestWorker.class.getName(), message)
                .onFailure(err -> log.error("[ZimbraConnector@ICalRequestCron] Failed to sync ical queue"));
    }
}
