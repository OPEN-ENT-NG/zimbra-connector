/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.helper;

import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

@SuppressWarnings("unused")
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

    public static Handler<AsyncResult<JsonArray>> getJsonArrayAsyncHandler(Handler<Either<String, JsonArray>> handler) {
        return res -> {
            if(res.failed()) {
                handler.handle(new Either.Left<>(res.cause().getMessage()));
            } else {
                handler.handle(new Either.Right<>(res.result()));
            }
        };
    }

    @SuppressWarnings("Duplicates")
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
        return res -> handler.handle(eitherToVoidAsync(res));
    }

    public static Future<JsonObject> getJsonObjectFinalFuture(Handler<AsyncResult<JsonObject>> handler) {
        Future<JsonObject> startFuture = Future.future();
        startFuture.setHandler(handler);
        return startFuture;
    }

    @SuppressWarnings("WeakerAccess")
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



    @SuppressWarnings("Duplicates")
    public static Handler<Either<String, JsonArray>> getJsonArrayEitherHandler(Handler<AsyncResult<JsonArray>> handler) {
        return res -> {
            if(res.isLeft()) {
                handler.handle(Future.failedFuture(res.left().getValue()));
            } else {
                handler.handle(Future.succeededFuture(res.right().getValue()));
            }
        };
    }

    public static <T> void processListSynchronously(List<T> origList, AsyncHandler<T> strHandler,
                                         Handler<AsyncResult<T>> finalHandler) {
        if(origList.isEmpty()) {
            finalHandler.handle(Future.failedFuture("Empty list"));
            return;
        }
        Future<T> init = Future.future();
        strHandler.handle(origList.get(0), init.completer());
        Future<T> current = init;
        for(T obj : origList.subList(1, origList.size())) {
            current = current.compose(v -> {
                Future<T> next = Future.future();
                strHandler.handle(obj, next.completer());
                return next;
            });

        }
        current.setHandler(finalHandler);
    }

    public static <T> Handler<Either<String,T>> getEitherFromFuture(Future<T> future) {
        return evt -> {
            if (evt.isLeft()) future.fail(evt.left().getValue());
            else future.complete(evt.right().getValue());
        };
    }

    public static <T> Future<T> getFutureFromEither(Handler<Either<String, T>> either) {
        Future<T> future = Future.future();
        future.setHandler(evt -> {
            if(evt.failed()) {
                either.handle(new Either.Left<>(evt.cause().getMessage()));
            } else {
                either.handle(new Either.Right<>(evt.result()));
            }
        });
        return future;
    }
}
