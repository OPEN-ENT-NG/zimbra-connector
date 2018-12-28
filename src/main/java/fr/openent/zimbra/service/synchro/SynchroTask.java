package fr.openent.zimbra.service.synchro;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.openent.zimbra.model.constant.BusConstants.*;

public class SynchroTask implements Handler<Long> {

    private final EventBus eb;
    private final Logger log = LoggerFactory.getLogger(SynchroTask.class);

    private String action;


    public SynchroTask(EventBus eb, String action) {
        this.eb = eb;
        this.action = action;
    }


    @Override
    public void handle(Long event) {
        log.info("Zimbra cron started : " + action);
        eb.send(SYNCHRO_BUSADDR,
                new JsonObject().put(BUS_ACTION, action),
                res -> {
                    if(res.succeeded()) {
                        log.info("Cron launch successful with action " + action);
                    } else {
                        log.error("Cron launch failed with action " + action);
                    }
                });
    }
}
