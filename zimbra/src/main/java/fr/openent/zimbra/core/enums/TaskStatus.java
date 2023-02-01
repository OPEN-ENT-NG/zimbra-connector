package fr.openent.zimbra.core.enums;

public enum TaskStatus {
    ERROR("error"),
    CANCELLED("cancelled");

    private final String actionStatus;

    TaskStatus(String actionStatus) {
        this.actionStatus = actionStatus;
    }

    public String method() {
        return this.actionStatus;
    }
}
