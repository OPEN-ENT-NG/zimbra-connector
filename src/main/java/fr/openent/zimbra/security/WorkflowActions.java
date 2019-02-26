package fr.openent.zimbra.security;

public enum  WorkflowActions {
    EXPERT_ACCESS_RIGHT ("zimbra.expert");

    private final String actionName;

    WorkflowActions(String actionName) {
        this.actionName = actionName;
    }

    @Override
    public String toString () {
        return this.actionName;
    }
}
