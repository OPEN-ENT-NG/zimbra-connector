package fr.openent.zimbra.service.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import java.time.Duration;

import static fr.openent.zimbra.model.constant.ZimbraErrors.IS_SUCCESSFUL;

public class AccessLoggerService {
    private static final Logger log = LoggerFactory.getLogger(AccessLoggerService.class);

    public void logAction(UserInfos user, String route, JsonObject data) {
        if(data == null) {
            data = new JsonObject();
        }
        data.put("userId", user.getUserId())
                .put("login", user.getLogin());
        log.debug(String.format("ROUTE : %s, DATA : %s", route, data.toString()));
    }

    public void logZimbraRequestStart(String requestId, String userId, String reqName, String namespace) {
        JsonObject data = new JsonObject().put("namespace", namespace).put("requestName", reqName);
        log.debug(String.format("REQSTART %s - UserId : %s %s", requestId, userId, data.toString()));
    }

    public void logZimbraRequestEnd(String requestId, String userId, Duration requestDuration,
                                    AsyncResult<JsonObject> result) {
        JsonObject data = new JsonObject()
                .put("requestCBSuccess", result.succeeded())
                .put("requestZSuccess", result.succeeded() && result.result().getBoolean(IS_SUCCESSFUL));
        log.debug(String.format("REQEND %s -  UserId : %s %s elapsed=%d",
                requestId, userId, data.toString(), requestDuration.toMillis()));
    }
}
