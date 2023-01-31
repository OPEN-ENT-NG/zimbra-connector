package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.task.Task;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

import java.util.Date;

import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;

public class QueueServiceImpl {
    protected static final Logger log = LoggerFactory.getLogger(Renders.class);

    private final String actionTable;
    private final String taskTable;

    public QueueServiceImpl(String schema) {
        this.actionTable = schema + ".action";
        this.taskTable = schema + ".task";
    }

    public Future<Void> putRequestInQueue(UserInfos user, JsonObject info) {
        Promise promise = Promise.promise();
        String type = info.getString(Field.TYPE, null);

        if (type != null) {
            createActionInQueue(user, info.getString(Field.TYPE))
                    .onSuccess(result -> {
                        Task tasks = Task.requestObjectFactory(info);
                        tasks.addTaskToAction();
                        promise.complete(result);
                    })
                    .onFailure(error -> {
                        //todo
                    });
        } else {
            //todo
            promise.fail("");
        }

        return promise.future();
    }

    public Future<Integer> createActionInQueue(UserInfos user, String type) {
        Promise promise = Promise.promise();

        createActionInQueue(user, type, result -> {
            if (result.isRight()) {
                promise.complete(result.right().getValue().getInteger(Field.ID));
            } else {
                //todo
                promise.fail();
            }
        });

        return promise.future();
    }

    public void createActionInQueue(UserInfos user, String type, Handler<Either<String, JsonObject>> handler) {
        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ").append(actionTable)
                .append(" (user_id, date, type) ").append( "VALUES (?, ?, ?)")
                .append("RETURN id");

        JsonArray values = new JsonArray();
        values.add(user.getUserId()).add(new Date().getTime()).add(type);

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(handler));
    }

    public Future<Integer> createTask(Integer actionId) {
        Promise<Integer> promise = Promise.promise();

        createTask(actionId, result -> {
            if (result.isRight()) {
                promise.complete(result.right().getValue().getInteger(Field.ID));
            } else {
                //todo
                promise.fail();
            }
        });

        return promise.future();
    }

    public void createTask(Integer actionId, Handler<Either<String, JsonObject>> handler) {
        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ").append(taskTable)
                .append(" (action_id, status) ").append( "VALUES (?, ?)")
                .append("RETURN id");

        JsonArray values = new JsonArray();
        values.add(actionId).add("pending");

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(handler));
    }


}
