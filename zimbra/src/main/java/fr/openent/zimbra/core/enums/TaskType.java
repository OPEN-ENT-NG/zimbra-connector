package fr.openent.zimbra.core.enums;

public enum TaskType {
    ICAL("ical"),
    RECALL("recall");

    private final String taskType;

    TaskType(String taskType) {
        this.taskType = taskType;
    }

    public String method() {
        return this.taskType;
    }
}
