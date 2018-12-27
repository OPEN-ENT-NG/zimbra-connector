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


    public SynchroTask(EventBus eb) {
        this.eb = eb;
    }


    @Override
    public void handle(Long event) {
        log.info("Zimbra Synchro started");
        eb.send(SYNCHRO_BUSADDR,
                new JsonObject().put(BUS_ACTION, ACTION_STARTSYNCHRO),
                res -> {
                    if(res.succeeded()) {
                        log.info("Synchronization successful");
                    } else {
                        log.error("Synchronization failed");
                    }
                });
    }
}
