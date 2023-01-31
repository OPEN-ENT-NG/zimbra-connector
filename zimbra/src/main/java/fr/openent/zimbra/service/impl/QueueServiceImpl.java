package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.requestQueue.RequestQueue;
import fr.openent.zimbra.service.QueueService;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;

import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;

public class QueueServiceImpl {
    protected static final Logger log = LoggerFactory.getLogger(Renders.class);

    private final String icalRequestTable;
    private final String icalBodyRequestTable;

    public QueueServiceImpl(String schema) {
        this.icalRequestTable = schema + ".ical_request";
        this.icalBodyRequestTable = schema + ".ical_body";
    }

    public Future<Void> putRequestInQueue(UserInfos user, JsonObject info) {
        Promise promise = Promise.promise();
        String type = info.getString(Field.TYPE, null);

        if (type != null) {
            //create action
            createActionInQueue(user, info.getString(Field.TYPE))
                    .onSuccess(result -> {
                        RequestQueue tasks = RequestQueue.requestObjectFactory(info);
                        tasks.addTaskToAction();
                    })
                    .onFailure();
        } else {
            //todo
            promise.fail("");
        }

        return promise.future();
    }




    public Future<Void> createActionInQueue(UserInfos user, String type) {
        StringBuilder query = new StringBuilder();

        //todo
        query.append("INSERT INTO ").append(icalRequestTable)
                .append(" (user_id, date, type) ").append( "VALUES (?, ?, ?)");

        JsonArray values = new JsonArray();
        values.add(user.getUserId()).add().add(type);

        Sql.getInstance().prepared(query.toString(), values, validUniqueResultHandler(null));
    }

}
