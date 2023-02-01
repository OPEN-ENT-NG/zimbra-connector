package fr.openent.zimbra.model.action;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.model.task.Task;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.*;

public class Action {
    protected long id;
    protected UUID userId;
    protected Date createdAt;
    protected ActionType actionType;
    protected boolean approved;

    Set<Task> tasks;

    public Action(long id, UUID userId, Date createdAt, ActionType actionType, boolean approved) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.actionType = actionType;
        this.approved = approved;
        tasks = new HashSet<>();
    }
    private static boolean JSONContainsActionData (JsonObject actionData) {
        return  actionData.containsKey(Field.ACTION_ID) &&
                actionData.containsKey(Field.USER_ID) &&
                actionData.containsKey(Field.CREATED_AT) &&
                actionData.containsKey(Field.TYPE) &&
                actionData.containsKey(Field.APPROVED);
    }

    public Action(JsonObject actionData) throws Exception {
        if (!JSONContainsActionData(actionData)) {
            throw new Exception(String.format("[Zimbra@%s::createActionFromJson] Bad answer from database", Action.class));
        }
        long id = actionData.getInteger(Field.ACTION_ID);
        try {
            UUID userId = UUID.fromString(actionData.getString(Field.USER_ID));
            Date created_at = Date.from(Instant.parse(actionData.getString(Field.CREATED_AT)));
            ActionType actionType = ActionType.fromString(actionData.getString(Field.TYPE));
            boolean approved = actionData.getBoolean(Field.APPROVED);
            this.id = id;
            this.userId = userId;
            this.createdAt = created_at;
            this.actionType = actionType;
            this.approved = approved;
        } catch (Exception e) {
            throw new Exception(String.format("[Zimbra@%s::createActionFromJson] Bad field format", Action.class));
        }
    }

    public Set<Task> getTasks() {
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

    public void addTasks(List<Task> tasks) {
        this.tasks.addAll(tasks);
    }

    public void addSingleTask(Task task) {
        this.tasks.add(task);
    }

    public void removeTask(Task task) {
        this.tasks.remove(task);
    }
}
