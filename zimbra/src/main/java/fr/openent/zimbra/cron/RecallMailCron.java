package fr.openent.zimbra.cron;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.EventBusHelper;
import fr.openent.zimbra.service.RecallMailService;
import fr.openent.zimbra.core.enums.QueueWorkerAction;
import fr.openent.zimbra.worker.RecallMailWorker;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class RecallMailCron implements Handler<Long> {
    private static final Logger log = LoggerFactory.getLogger(RecallMailCron.class);

    private final RecallMailService recallMailService;
    private final EventBus eb;

    public RecallMailCron(RecallMailService recallMailService, EventBus eb) {
        this.recallMailService = recallMailService;
        this.eb = eb;
    }

    @Override
    public void handle(Long event) {
        log.info("[ZimbraConnector@RecallCron] RecallCron started");
        final JsonObject message = new JsonObject();
        message.put(Field.ACTION, QueueWorkerAction.SYNC_QUEUE);
        EventBusHelper.requestJsonObject(eb, RecallMailWorker.RECALL_MAIL_HANDLER_ADDRESS, message)
                .onSuccess(res -> log.info("[ZimbraConnector@RecallCron] Sync recall queue"))
                .onFailure(err -> log.error("[ZimbraConnector@RecallCron] Failed to sync recall queue"));
    }
}
