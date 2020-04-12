package fr.openent.zimbra.service.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

public class AccessLoggerService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    public void logAction(UserInfos user, String route, JsonObject data) {
        if(data == null) {
            data = new JsonObject();
        }
        data.put("userId", user.getUserId())
                .put("login", user.getLogin());
        log.debug(String.format("ROUTE : %s, DATA : %s", route, data.toString()));
    }
}
