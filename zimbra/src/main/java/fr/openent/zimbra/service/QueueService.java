package fr.openent.zimbra.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public interface QueueService {
    Future<Void> putRequestInQueue(UserInfos user, JsonObject info);

    Future<Void> createActionInQueue(UserInfos user, String type)
}
