package fr.openent.zimbra.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public interface QueueService {
    Future<Void> putRequestInQueue(UserInfos user, JsonObject info);

    Future<Integer> createActionInQueue(UserInfos user, String type);

    void createActionInQueue(UserInfos user, String type, Handler<Either<String, JsonArray>> handler);

    Future<Integer> createTask(Integer actionId);

    void createTask(Integer actionId, Handler<Either<String, JsonArray>> handler);
}
