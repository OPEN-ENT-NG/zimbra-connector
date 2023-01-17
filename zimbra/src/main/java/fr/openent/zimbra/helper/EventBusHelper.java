package fr.openent.zimbra.helper;

import fr.openent.zimbra.controllers.ZimbraController;
import fr.openent.zimbra.core.constants.Field;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class EventBusHelper {

    private static final Logger log = LoggerFactory.getLogger(ZimbraController.class);

    public static void eventBusError(String logMessage, String replayErrorMessage, Message<JsonObject> message) {
        log.error(logMessage);
        message.reply(new JsonObject().put(Field.STATUS, Field.ERROR).put(Field.MESSAGE, replayErrorMessage));
    }


}
