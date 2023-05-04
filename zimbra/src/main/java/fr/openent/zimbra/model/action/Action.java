package fr.openent.zimbra.model.action;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.model.task.Task;
import fr.openent.zimbra.utils.DateUtils;
import io.vertx.core.json.JsonObject;
import java.util.*;

public class Action<T extends Task<T>> {
    protected long id;
    protected UUID userId;
    protected Date createdAt;
    protected ActionType actionType;
    protected boolean approved;

    protected Set<T> tasks;

    public Action(UUID userId, ActionType actionType, boolean approved) {
        this.userId = userId;
        this.actionType = actionType;
        this.approved = approved;
    }

    public Action(long id, UUID userId, Date createdAt, ActionType actionType, boolean approved) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.actionType = actionType;
        this.approved = approved;
        this.tasks = new HashSet<>();
    }
    private static boolean JSONContainsActionData (JsonObject actionData) {
        return  actionData.containsKey(Field.ID) &&
                actionData.containsKey(Field.USER_ID) &&
                actionData.containsKey(Field.CREATED_AT) &&
                actionData.containsKey(Field.TYPE) &&
                actionData.containsKey(Field.APPROVED);
    }

    public Action(JsonObject actionData) throws Exception {
        if (!JSONContainsActionData(actionData)) {
            throw new Exception(String.format("[Zimbra@%s::Action] Json does not match Action model", Action.class));
        }
        long id = actionData.getInteger(Field.ID);
        try {
            UUID userId = UUID.fromString(actionData.getString(Field.USER_ID));
            Date created_at = DateUtils.parseDate(actionData.getString(Field.CREATED_AT), DateUtils.DATE_FORMAT_SQL_WITHOUT_MILLI);
            ActionType actionType = ActionType.fromString(actionData.getString(Field.TYPE));
            boolean approved = actionData.getBoolean(Field.APPROVED);
            this.id = id;
            this.userId = userId;
            this.createdAt = created_at;
            this.actionType = actionType;
            this.approved = approved;
            this.tasks = new HashSet<>();
        } catch (Exception e) {
            throw new Exception(String.format("[Zimbra@%s::Action] Bad field format", Action.class));
        }
    }

    public Set<T> getTasks() {
        return tasks;
    }

    public long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public boolean getApproved() {
        return approved;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public void addTasks(List<T> tasks) {
        this.tasks.addAll(tasks);
    }

    public void addTask(T task) {
        this.tasks.add(task);
    }

    public void removeTask(T task) {
        this.tasks.remove(task);
    }
}
