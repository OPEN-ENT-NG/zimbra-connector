package fr.openent.zimbra.cron;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;

public class ICalRequestCron extends ControllerHelper implements Handler<Long> {
    private static final Logger log = LoggerFactory.getLogger(ICalRequestCron.class);

    public ICalRequestCron () {
    }

    @Override
    public void handle(Long event) {
        log.info("[ZimbraConnector@ICalRequestCron] ICalRequestCron started");
        //todo call worker
    }
}
