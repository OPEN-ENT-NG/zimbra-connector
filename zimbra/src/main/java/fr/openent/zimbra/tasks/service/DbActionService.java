package fr.openent.zimbra.tasks.service;
import fr.openent.zimbra.core.enums.ActionType;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


import java.util.UUID;

public abstract class DbActionService {
    protected String schema;
    protected String actionTable;
    protected DbActionService(String schema) {
        this.schema = schema;
        this.actionTable = schema + "." + "actions";
    }

    protected abstract Future<JsonObject> createAction(UUID userId, ActionType actionType, boolean approved);
}
