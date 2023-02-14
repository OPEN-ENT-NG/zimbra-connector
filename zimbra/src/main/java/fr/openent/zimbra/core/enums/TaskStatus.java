package fr.openent.zimbra.core.enums;

import java.util.Objects;

public enum TaskStatus {
    ERROR("error"),
    PENDING("pending"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown"),
    IN_PROGRESS("inProgress"),
    FINISHED("finished");

    private final String actionStatus;

    TaskStatus(String actionStatus) {
        this.actionStatus = actionStatus;
    }

    public String method() {
        return this.actionStatus;
    }

    public static TaskStatus fromString (String type) {
        for (TaskStatus actionType : TaskStatus.values()) {
            if (Objects.equals(actionType.method(), type))
                return actionType;
        }
        return TaskStatus.UNKNOWN;
    }
}
