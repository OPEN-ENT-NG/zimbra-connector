package fr.openent.zimbra.helper;

import fr.openent.zimbra.core.constants.Field;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MessageResponseHandler {
    public static Handler<AsyncResult<Message<JsonObject>>> messageJsonObjectHandler(Handler<Either<String, JsonObject>> handler) {
        return event -> {
            if (event.succeeded() && Field.OK.equals(event.result().body().getString(Field.STATUS))) {
                if (!event.result().body().containsKey(Field.RESULT))
                    handler.handle(new Either.Right<>(event.result().body()));
                else
                    handler.handle(new Either.Right<>(event.result().body().getJsonObject(Field.RESULT)));
            } else {
                if (event.failed()) {
                    handler.handle(new Either.Left<>(event.cause().getMessage()));
                    return;
                }
                handler.handle(new Either.Left<>(event.result().body().getString(Field.MESSAGE)));
            }
        };
    }

    public static Handler<AsyncResult<Message<JsonObject>>> messageJsonArrayHandler(Handler<Either<String, JsonArray>> handler) {
        return event -> {
            if (event.succeeded() && Field.OK.equals(event.result().body().getString(Field.STATUS))) {
                handler.handle(new Either.Right<>(event.result().body().getJsonArray(Field.RESULT, event.result().body().getJsonArray(Field.RESULTS))));
            } else {
                if (event.failed()) {
                    handler.handle(new Either.Left<>(event.cause().getMessage()));
                    return;
                }
                handler.handle(new Either.Left<>(event.result().body().getString(Field.MESSAGE)));
            }
        };
    }
}
