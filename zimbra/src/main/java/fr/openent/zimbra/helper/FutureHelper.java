package fr.openent.zimbra.helper;

import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class FutureHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(FutureHelper.class);

    private FutureHelper() {
    }

    public static Handler<Either<String, JsonArray>> handlerJsonArray(Future<JsonArray> future) {
        return event -> {
            if (event.isRight()) {
                future.complete(event.right().getValue());
            } else {
                LOGGER.error(event.left().getValue());
                future.fail(event.left().getValue());
            }
        };
    }

    public static Handler<Either<String, JsonArray>> handlerJsonArray(Promise<JsonArray> future) {
        return event -> {
            if (event.isRight()) {
                future.complete(event.right().getValue());
            } else {
                LOGGER.error(event.left().getValue());
                future.fail(event.left().getValue());
            }
        };
    }

    public static Handler<Either<String, JsonObject>> handlerJsonObject(Future<JsonObject> future) {
        return event -> {
            if (event.isRight()) {
                future.complete(event.right().getValue());
            } else {
                LOGGER.error(event.left().getValue());
                future.fail(event.left().getValue());
            }
        };
    }

    public static Handler<Either<String, JsonObject>> handlerJsonObject(Promise<JsonObject> future) {
        return handlerJsonObject(future, null);
    }

    public static Handler<Either<String, JsonObject>> handlerJsonObject(Promise<JsonObject> future, String errorMessage) {
        return event -> {
            if (event.isRight()) {
                future.complete(event.right().getValue());
                return;
            }
            LOGGER.error(String.format("%s %s", (errorMessage != null ? errorMessage : ""), event.left().getValue()));
            future.fail(errorMessage != null ? errorMessage : event.left().getValue());
        };
    }
}

