package fr.openent.zimbra.helper;

import fr.wseduc.webutils.Either;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.CompositeFutureImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

public class FutureHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(FutureHelper.class);

    private FutureHelper() {
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

    @Deprecated
    public static Handler<Either<String, JsonObject>> handlerJsonObject(Promise<JsonObject> future) {
        return handlerJsonObject(future, null);
    }

    @Deprecated
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

    public static <L, R> Handler<Either<L, R>> handlerEitherPromise(Promise<R> promise) {
        return handlerEitherPromise(promise, null);
    }

    public static <L, R> Handler<Either<L, R>> handlerEitherPromise(Promise<R> promise, String errorLogMessage) {
        return event -> {
            if (event.isRight()) {
                promise.complete(event.right().getValue());
            } else {
                if (errorLogMessage != null) {
                    String message = String.format("%s: %s", errorLogMessage, event.left().getValue());
                    LOGGER.error(message);
                }
                promise.fail(event.left().getValue() != null ? event.left().getValue().toString() : "null");
            }
        };
    }

    public static <T> CompositeFuture all(List<Future<T>> futures) {
        return CompositeFutureImpl.all(futures.toArray(new Future[0]));
    }

    public static <T> CompositeFuture join(List<Future<T>> futures) {
        return CompositeFutureImpl.join(futures.toArray(new Future[0]));
    }

    public static <T> CompositeFuture any(List<Future<T>> futures) {
        return CompositeFutureImpl.any(futures.toArray(new Future[0]));
    }
}

