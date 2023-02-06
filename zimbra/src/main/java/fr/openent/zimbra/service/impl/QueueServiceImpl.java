package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.task.Task;
import fr.openent.zimbra.service.QueueService;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

public class QueueServiceImpl implements QueueService {
    protected static final Logger log = LoggerFactory.getLogger(Renders.class);

    private final String actionTable;
    private final String taskTable;
    private final String icalTaskTable;

    public QueueServiceImpl(String schema) {
        this.actionTable = schema + ".actions";
        this.taskTable = schema + ".tasks";
        this.icalTaskTable = schema + ".ical_request_tasks";
    }

    public Future<Void> putRequestInQueue(UserInfos user, JsonObject info) {
        Promise<Void> promise = Promise.promise();
        String type = info.getString(Field.TYPE, null);
        Boolean approved = info.getBoolean(Field.APPROVED, null);

        if (type == null) {
            String errMessage = String.format("[Zimbra@%s::putRequestInQueue]:  " +
                            "an error has occurred while putting request in queue: request type is not defined",
                    this.getClass().getSimpleName());
            log.error(errMessage);
            promise.fail("zimbra.error.queue.no.type");
            return promise.future();
        }

        createActionInQueue(user, info.getString(Field.TYPE), approved)
                .compose(actionId -> {
                    info.put(Field.ACTIONID, actionId);
                    Task tasks = Task.requestObjectFactory(info);
                    return tasks.addTaskToAction();
                })
                .onSuccess(promise::complete)
                .onFailure(error -> {
                    String errMessage = String.format("[Zimbra@%s::putRequestInQueue]:  " +
                                    "an error has occurred while putting request in queue: %s",
                            this.getClass().getSimpleName(), error.getMessage());
                    log.error(errMessage);
                    promise.fail("zimbra.error.queue");
                });

        return promise.future();
    }

    public Future<Integer> createActionInQueue(UserInfos user, String type, Boolean approved) {
        Promise<Integer> promise = Promise.promise();
        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ").append(actionTable)
                .append(" (user_id, type, approved) ").append( "VALUES (?, ?, ?) ")
                .append("RETURNING id");

        JsonArray values = new JsonArray();
        values.add(user.getUserId()).add(type).add(approved);

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler( result -> {
            if (result.isRight()) {
                promise.complete(result.right().getValue().getInteger(Field.ID));
            } else {
                String errMessage = String.format("[Zimbra@%s::createActionInQueue]:  " +
                                "an error has occurred while creating action in queue: %s",
                        this.getClass().getSimpleName(), result.left().getValue());
                log.error(errMessage);
                promise.fail("zimbra.error.queue.action");
            }
        }));

        return promise.future();
    }

    public Future<Void> createICalTask(Integer actionId, JsonObject requestInfo) {
        Promise<Void> promise = Promise.promise();
        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ").append(icalTaskTable)
                .append(" (action_id, status, name, body) ").append( "VALUES (?, ?, ?, ?)");

        JsonArray values = new JsonArray();
        values.add(actionId)
                .add("pending")
                .add(requestInfo.getString(Field.NAME))
                .add(requestInfo.getJsonObject(Field.CONTENT));

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(result -> {
            if (result.isRight()) {
                promise.complete();
            } else {
                String errMessage = String.format("[Zimbra@%s::createICalTask]:  " +
                                "an error has occurred while creating ical task for queue action: %s",
                        this.getClass().getSimpleName(), result.left().getValue());
                log.error(errMessage);
                promise.fail("zimbra.error.queue.action.task.ical");
            }
        }));

        return promise.future();
    }


}
