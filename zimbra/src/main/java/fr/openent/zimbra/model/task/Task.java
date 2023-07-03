package fr.openent.zimbra.model.task;

import java.util.Date;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.action.Action;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class Task<T extends Task<T>> {
    protected long id;
    protected TaskStatus status;
    public Action<T> action;
    public Date lastUpdated;

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Action<T> getAction() {
        return action;
    }

    protected static final Logger log = LoggerFactory.getLogger(Task.class);

    public Task(JsonObject jsonObject) {
        this.id = jsonObject.getLong(Field.ID, null);
        this.status = TaskStatus.fromString(jsonObject.getString(Field.STATUS, null));
    }

    public Task(TaskStatus status, Action<T> action) {
        this.status = status;
        this.action = action;
    }

    public Task(long id, Date lastUpdated, TaskStatus status, Action<T> action) {
        this.id = id;
        this.status = status;
        this.action = action;
        this.lastUpdated = lastUpdated;
    }

    public Task(JsonObject dbData, Action<T> action) throws Exception {
        try {
            this.id = dbData.getLong(Field.ID);
            this.status = TaskStatus.fromString(dbData.getString(Field.STATUS));
            this.action = action;
        } catch (Exception e) {
            throw new Exception(String.format("[Zimbra@%s::Task] JSON does not match Task model", Task.class));
        }
    }

    public Date getLastUpdated() {
        return this.lastUpdated;
    }
}
