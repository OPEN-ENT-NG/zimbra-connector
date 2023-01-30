package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.requestQueue.RequestQueue;
import fr.openent.zimbra.service.QueueService;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

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

        //create action
        createActionInQueue(user)
                .onSuccess(result -> {
                    RequestQueue tasks = RequestQueue.requestObjectFactory(info);
                    tasks.addTaskToAction();
                })
                .onFailure();

        return promise.future();
    }




    public Future<Void> createActionInQueue(UserInfos user) {
        StringBuilder query = new StringBuilder();

        //todo
        query.append("INSERT INTO ").append(icalRequestTable)



                .append("( ")
                .append(ZIMBRA_NAME).append(", ")
                .append(NEO4J_UID).append(") ");
        query.append("SELECT d.name, d.alias ");
        query.append("FROM data d ");
        query.append("WHERE NOT EXISTS (SELECT 1 FROM ").append(userTable)
                .append(" u WHERE u.").append(ZIMBRA_NAME).append(" = d.name ")
                .append("AND u.").append(NEO4J_UID).append(" = d.alias ")
                .append(")");


    }

}
