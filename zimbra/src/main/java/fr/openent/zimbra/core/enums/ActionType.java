package fr.openent.zimbra.core.enums;

import java.util.Objects;

public enum ActionType {
    ICAL("ical"),
    RECALL("recall"),
    UNKNOWN("unknown");

    private final String actionType;

    ActionType(String taskType) {
        this.actionType = taskType;
    }

    public String method() {
        return this.actionType;
    }

    public static ActionType fromString (String type) {
        for (ActionType actionType : ActionType.values()) {
            if (Objects.equals(actionType.method(), type))
                return actionType;
        }
        return ActionType.UNKNOWN;
    }
}
