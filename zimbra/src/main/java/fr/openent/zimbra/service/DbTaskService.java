package fr.openent.zimbra.service;

import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.Task;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class DbTaskService<T extends Task<T>> {
    protected final String schema;
    protected final String actionTable;
    protected String taskTable;

    public DbTaskService(String schema) {
        this.schema = schema;
        this.actionTable = schema + ".actions";
        this.taskTable = schema + ".task";
    }

    public abstract Future<JsonArray> retrieveTasksDataFromDB(TaskStatus status);
    public abstract Future<JsonObject> createTask(Action<T> action, T task);
}
