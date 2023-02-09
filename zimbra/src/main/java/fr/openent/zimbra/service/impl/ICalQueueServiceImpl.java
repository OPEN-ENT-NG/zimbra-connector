package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.ICalTask;
import fr.openent.zimbra.service.QueueService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import org.apache.commons.lang3.NotImplementedException;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.List;

public class ICalQueueServiceImpl extends QueueService<ICalTask> {
    private final String icalTable = "ical_request_tasks";

    public ICalQueueServiceImpl(String schema) {
        super(schema);
        this.taskTable = this.schema + "." + icalTable;
        this.actionType = ActionType.RECALL;
    }

    @Override
    public Future<List<ICalTask>> insertTasksInQueue(List<ICalTask> tasks) {
        // todo: insert Task in the top of the queue of ICalWorker
        throw new NotImplementedException();
    }

    @Override
    public Future<ICalTask> createTask(Action action, ICalTask task) {
        Promise<ICalTask> promise = Promise.promise();

        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ")
                .append(this.taskTable)
                .append(" (" + Field.ACTION_ID + "," + Field.STATUS + "," + Field.JSNS + "," + Field.BODY + ") ")
                .append("VALUES (?, ?, ?, ?)")
                .append("RETURNING *");

        JsonArray values = new JsonArray();
        values.add(action.getId()).add(task.getStatus()).add(task.getJsns()).add(task.getBody());

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(handler -> {
            if (handler.isRight()) {
                long id = handler.right().getValue().getLong(Field.ID);

                task.setId(id);
                promise.complete(task);
            } else {
                String errMessage = String.format("[Zimbra@%s::createTask]:  " +
                                "an error has occurred while creating task: %s",
                        this.getClass().getSimpleName(), handler.left().getValue());
                log.error(errMessage);
                promise.fail("zimbra.error.queue.task");
            }
        }));

        return promise.future();
    }

    @Override
    public Future<List<ICalTask>> createTasks(Action action, List<ICalTask> tasks) {
        throw new NotImplementedException();
    }

}
