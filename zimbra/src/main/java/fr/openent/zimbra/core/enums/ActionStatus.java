package fr.openent.zimbra.core.enums;

public enum ActionStatus {
    ACCEPTED("accepted"),
    CANCELLED("cancelled");

    private final String actionStatus;

    ActionStatus(String actionStatus) {
        this.actionStatus = actionStatus;
    }

    public String method() {
        return this.actionStatus;
    }
}