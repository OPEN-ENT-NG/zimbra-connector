package fr.openent.zimbra.model.task;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.action.Action;
import io.vertx.core.json.JsonObject;

public abstract class Task {
    protected long id;
    protected TaskStatus status;

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Action getAction() {
        return action;
    }

    public Action action;

    public Task(JsonObject jsonObject) {
        this.id = jsonObject.getLong(Field.ID, null);
        this.status = TaskStatus.fromString(jsonObject.getString(Field.STATUS, null));
    }

    public Task(TaskStatus status, Action action) {
        this.status = status;
        this.action = action;
    }

    public Task(long id, TaskStatus status, Action action) {
        this.id = id;
        this.status = status;
        this.action = action;
    }

    public Task(JsonObject dbData, Action action) throws Exception {
        try {
            this.id = dbData.getLong(Field.TASK_ID);
            this.status = TaskStatus.fromString(dbData.getString(Field.TASK_STATUS));
            this.action = action;

        } catch (Exception e) {
            throw new Exception(String.format("[Zimbra@%s::Task] JSON does not match Task model", Task.class));
        }
    }
}
