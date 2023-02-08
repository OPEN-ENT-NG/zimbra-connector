package fr.openent.zimbra.model.task;

import fr.openent.zimbra.core.enums.TaskStatus;

public abstract class TaskInfo {
    protected int actionId;
    protected TaskStatus status;

    public TaskInfo(int actionId, TaskStatus status) {
        this.actionId = actionId;
        this.status = status;
    }

    public int getActionId() {
        return actionId;
    }

    public TaskStatus getStatus() {
        return status;
    }
}
