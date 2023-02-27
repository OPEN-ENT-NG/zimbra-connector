package fr.openent.zimbra.tasks.service.impl.data;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.helper.PromiseHelper;
import fr.openent.zimbra.tasks.service.DbActionService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import java.util.UUID;

public class SqlActionService extends DbActionService {

    public SqlActionService(String schema) {
        super(schema);
    }

    @Override
    protected Future<JsonObject> createAction(UUID userId, ActionType actionType, boolean approved) {
        Promise<JsonObject> promise = Promise.promise();

        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ")
                .append(actionTable)
                .append(" (" + Field.USER_ID + ", " + Field.TYPE + ", " + Field.APPROVED + ") ")
                .append("VALUES (?, ?, ?) ")
                .append("RETURNING " + Field.ID + ", " + Field.USER_ID + ", " + Field.CREATED_AT + ", " + Field.TYPE + ", " + Field.APPROVED);

        JsonArray values = new JsonArray();
        values.add(userId.toString()).add(actionType.method()).add(approved);

        Sql.getInstance().prepared(query.toString(), values, SqlResult.validUniqueResultHandler(PromiseHelper.handlerJsonObject(promise)));

        return promise.future();
    }
}
