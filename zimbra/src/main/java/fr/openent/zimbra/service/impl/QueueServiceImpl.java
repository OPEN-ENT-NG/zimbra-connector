package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.action.Action;
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

    public Future<Void> putRequestInQueue(UserInfos user, JsonObject info) throws Exception {
        Promise promise = Promise.promise();
        String type = info.getString(Field.TYPE, null);
        Boolean approved = info.getBoolean(Field.APPROVED, null);

        if (type != null) {
            createActionInQueue(user, info.getString(Field.TYPE), approved)
                    .onSuccess(result -> {
                        info.put(Field.USER, user);
                        try {
                            Action action = new Action(result);
//                            Task tasks = Task.requestObjectFactory(user, info);
//                            tasks.addTaskToAction();
                            promise.complete(result);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .onFailure(error -> {
                        String errMessage = String.format("[Zimbra@%s::putRequestInQueue]:  " +
                                        "an error has occurred while putting request in queue: %s",
                                this.getClass().getSimpleName(), error.getMessage());
                        log.error(errMessage);
                        promise.fail("zimbra.error.queue");
                    });
        } else {
            String errMessage = String.format("[Zimbra@%s::putRequestInQueue]:  " +
                            "an error has occurred while putting request in queue: request type is not defined",
                    this.getClass().getSimpleName());
            log.error(errMessage);
            promise.fail("zimbra.error.queue.no.type");
        }

        return promise.future();
    }

    public Future<JsonObject> createActionInQueue(UserInfos user, String type, Boolean approved) {
        Promise promise = Promise.promise();

        createActionInQueue(user, type, approved, result -> {
            if (result.isRight()) {
                promise.complete(result.right().getValue().getInteger(Field.ID));
            } else {
                String errMessage = String.format("[Zimbra@%s::createActionInQueue]:  " +
                                "an error has occurred while creating action in queue: %s",
                        this.getClass().getSimpleName(), result.left().getValue());
                log.error(errMessage);
                promise.fail("zimbra.error.queue.action");
            }
        });

        return promise.future();
    }

    public void createActionInQueue(UserInfos user, String type, Boolean approved, Handler<Either<String, JsonObject>> handler) {
        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ").append(actionTable)
                .append(" (" + Field.USER_ID + "," + Field.USER_ID + "," + Field.TYPE + "," + Field.APPROVED + ") ").append("VALUES (?, ?, ?, ?)")
                .append("RETURN *;");

        JsonArray values = new JsonArray();
        values.add(Integer.parseInt(user.getUserId())).add(new Date().getTime()).add(type).add(approved);

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(handler));
    }

    public Future<Integer> createTask(Integer actionId) {
        Promise<Integer> promise = Promise.promise();

        createTask(actionId, result -> {
            if (result.isRight()) {
                promise.complete(result.right().getValue().getInteger(Field.ID));
            } else {
                String errMessage = String.format("[Zimbra@%s::createTask]:  " +
                                "an error has occurred while creating task for queue action: %s",
                        this.getClass().getSimpleName(), result.left().getValue());
                log.error(errMessage);
                promise.fail("zimbra.error.queue.action.task");
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

    public Future<Integer> createICalTask(UserInfos user, JsonObject requestInfo) {
        Promise<Integer> promise = Promise.promise();

        createICalTask(user, requestInfo, result -> {
            if (result.isRight()) {
                promise.complete(result.right().getValue().getInteger(Field.ID));
            } else {
                String errMessage = String.format("[Zimbra@%s::createICalTask]:  " +
                                "an error has occurred while creating ical task for queue action: %s",
                        this.getClass().getSimpleName(), result.left().getValue());
                log.error(errMessage);
                promise.fail("zimbra.error.queue.action.task.ical");
            }
        });

        return promise.future();
    }

    public void createICalTask(UserInfos user, JsonObject requestInfo, Handler<Either<String, JsonObject>> handler) {
        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ").append(taskTable)
                .append(" (id_user, body) ").append( "VALUES (?, ?)")
                .append("RETURN id");

        JsonArray values = new JsonArray();
        values.add(Integer.parseInt(user.getUserId())).add(requestInfo);

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(handler));
    }


}
