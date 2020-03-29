package fr.openent.apizimbra.helper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface AsyncHandler<T> {
    void handle(T var1, Handler<AsyncResult<T>> handler);
}
