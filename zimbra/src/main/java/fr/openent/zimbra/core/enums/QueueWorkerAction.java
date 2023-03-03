package fr.openent.zimbra.core.enums;

public enum QueueWorkerAction {
    SYNC_QUEUE("syncQueue"),
    START("start"),
    PAUSE("pause"),
    GET_STATUS("getStatus"),
    ADD_TASKS("addTasks"),
    ADD_TASK("addTask"),
    REMOVE_TASK("removeTask"),
    REMOVE_TASKS("removeTasks"),
    CLEAR_QUEUE("clearQueue"),
    SET_MAX_QUEUE_SIZE("setMaxQueueSize"),
    GET_REMAINING_SIZE("getRemainingSize"),
    UNKNOWN("unknown");

    private final String action;

    QueueWorkerAction(String action) {
        this.action = action;
    }

    public String getValue() {
        return action;
    }
}
