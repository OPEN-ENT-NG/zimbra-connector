package fr.openent.zimbra.helper;

import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AsyncHelper {
    public static Handler<AsyncResult<JsonObject>> getJsonObjectAsyncHandler(Handler<Either<String, JsonObject>> handler) {
        return res -> {
            if(res.failed()) {
                handler.handle(new Either.Left<>(res.cause().getMessage()));
            } else {
                handler.handle(new Either.Right<>(res.result()));
            }
        };
    }

    public static Handler<Either<String, JsonObject>> getJsonObjectEitherHandler(Handler<AsyncResult<JsonObject>> handler) {
        return res -> {
            if(res.isLeft()) {
                handler.handle(Future.failedFuture(res.left().getValue()));
            } else {
                handler.handle(Future.succeededFuture(res.right().getValue()));
            }
        };
    }

    public static Handler<Either<String, JsonObject>> getVoidEitherHandler(Handler<AsyncResult<Void>> handler) {
        return res -> {
                handler.handle(eitherToVoidAsync(res));
        };
    }

    public static Future<JsonObject> getJsonObjectFinalFuture(Handler<Either<String, JsonObject>> handler) {
        Future<JsonObject> startFuture = Future.future();
        startFuture.setHandler(getJsonObjectAsyncHandler(handler));
        return startFuture;
    }

    public static AsyncResult<Void> eitherToVoidAsync(Either<String,JsonObject> either) {
        if(either.isLeft()) {
            return Future.failedFuture(either.left().getValue());
        } else {
            return Future.succeededFuture();
        }
    }

    public static Either<String, JsonObject> voidAsyncToJsonObjectEither(AsyncResult<Void> asyncResult) {
        if(asyncResult.failed()) {
            return new Either.Left<>(asyncResult.cause().getMessage());
        } else {
            return new Either.Right<>(new JsonObject());
        }
    }

    public static Either<String, JsonObject> jsonObjectAsyncToJsonObjectEither(AsyncResult<JsonObject> asyncResult) {
        if(asyncResult.failed()) {
            return new Either.Left<>(asyncResult.cause().getMessage());
        } else {
            return new Either.Right<>(asyncResult.result());
        }
    }



    public static Handler<Either<String, JsonArray>> getJsonArrayEitherHandler(Handler<AsyncResult<JsonArray>> handler) {
        return res -> {
            if(res.isLeft()) {
                handler.handle(Future.failedFuture(res.left().getValue()));
            } else {
                handler.handle(Future.succeededFuture(res.right().getValue()));
            }
        };
    }
}
